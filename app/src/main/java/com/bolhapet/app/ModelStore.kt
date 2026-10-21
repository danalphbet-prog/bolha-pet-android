package com.bolhapet.app

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Isolated private GGUF storage, unrelated to any other installed application. */
internal class ModelStore(private val context: Context) {
    data class Spec(val id: String, val label: String, val fileName: String,
                    val downloadUrl: String, val sha256: String, val size: Long)

    companion object {
        private val MODELS = listOf(
            Spec("1.5b", "Qwen2.5 1.5B · Q4_K_M",
                "qwen2.5-1.5b-instruct-q4_k_m.gguf",
                "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
                "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e",
                1_117_320_736L),
            Spec("3b", "Qwen2.5 3B · Q4_K_M",
                "qwen2.5-3b-instruct-q4_k_m.gguf",
                "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q4_k_m.gguf",
                "626b4a6678b86442240e33df819e00132d3ba7dddfe1cdc4fbb18e0a9615c62d",
                2_104_932_768L)
        )
    }

    private val prefs get() = context.getSharedPreferences("bolha_models_v2", Context.MODE_PRIVATE)
    private val dir get() = File(context.filesDir, "models").apply { mkdirs() }

    fun selected(): Spec = MODELS.firstOrNull { it.id == prefs.getString("selected", "1.5b") } ?: MODELS.first()
    fun select(id: String): Spec {
        val spec = MODELS.firstOrNull { it.id == id } ?: error("Modelo desconhecido")
        prefs.edit().putString("selected", id).apply()
        return spec
    }

    private fun finished(spec: Spec) = File(dir, spec.fileName)
    private fun partial(spec: Spec) = File(dir, "${spec.fileName}.part")
    private fun key(spec: Spec) = "verified_${spec.id}"

    suspend fun verified(spec: Spec = selected()): File? = withContext(Dispatchers.IO) {
        val file = finished(spec)
        if (!file.isFile || file.length() != spec.size) return@withContext null
        val identity = "${file.length()}:${file.lastModified()}:${spec.sha256}"
        if (prefs.getString(key(spec), null) == identity) return@withContext file
        if (!matchesSha(file, spec)) return@withContext null
        prefs.edit().putString(key(spec), identity).apply()
        file
    }

    suspend fun download(progress: (Int, String) -> Unit): File = withContext(Dispatchers.IO) {
        val spec = selected()
        verified(spec)?.let { return@withContext it }
        val part = partial(spec)
        var current = part.length().coerceAtMost(spec.size)
        if (current == spec.size && matchesSha(part, spec)) {
            val done = finished(spec)
            if (done.exists()) done.delete()
            check(part.renameTo(done)) { "Falha ao finalizar o GGUF" }
            return@withContext done
        }
        if (current == spec.size) { part.delete(); current = 0L }
        val minimumFree = (spec.size - current).coerceAtLeast(0) + 200_000_000L
        require(dir.usableSpace > minimumFree) { "Espaço insuficiente. Libere pelo menos ${minimumFree / 1_000_000L} MB." }
        val connection = (URL(spec.downloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 90_000
            instanceFollowRedirects = true
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("User-Agent", "BolhaPet-Android/0.2")
            if (current > 0) setRequestProperty("Range", "bytes=$current-")
        }
        try {
            val code = connection.responseCode
            require(code == 200 || code == 206) { "Download HTTP $code" }
            val append = code == 206 && current > 0 &&
                connection.getHeaderField("Content-Range").orEmpty().startsWith("bytes $current-")
            if (code == 206 && !append) error("Servidor não confirmou retomada")
            var count = if (append) current else 0L
            var last = 0L
            connection.inputStream.use { input ->
                FileOutputStream(part, append).buffered().use { output ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        val bytes = input.read(buffer)
                        if (bytes < 0) break
                        output.write(buffer, 0, bytes)
                        count += bytes
                        if (System.currentTimeMillis() - last > 250L) {
                            progress(((count * 100) / spec.size).toInt().coerceIn(0, 99),
                                "${spec.label}: ${count / 1_000_000} de ${spec.size / 1_000_000} MB")
                            last = System.currentTimeMillis()
                        }
                    }
                }
            }
            progress(99, "Verificando SHA-256...")
        } finally {
            connection.disconnect()
        }
        require(matchesSha(part, spec)) { "Download incompleto ou SHA-256 divergente; arquivo parcial preservado." }
        val done = finished(spec)
        if (done.exists()) require(done.delete()) { "Não foi possível substituir modelo anterior" }
        check(part.renameTo(done)) { "Falha ao finalizar GGUF" }
        prefs.edit().putString(key(spec), "${done.length()}:${done.lastModified()}:${spec.sha256}").apply()
        progress(100, "${spec.label} pronto")
        done
    }

    suspend fun import(uri: Uri, progress: (Int, String) -> Unit): File = withContext(Dispatchers.IO) {
        val spec = selected()
        val tmp = File(dir, "import-${spec.id}.tmp")
        tmp.delete()
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tmp).buffered().use { output ->
                    val buffer = ByteArray(128 * 1024)
                    var count = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        count += n
                        require(count <= spec.size + 1_024) { "Este arquivo excede o tamanho do modelo selecionado" }
                        output.write(buffer, 0, n)
                        if (count % (32L * 1024 * 1024) < buffer.size)
                            progress(((count * 100) / spec.size).toInt().coerceIn(0, 99), "Importando ${count / 1_000_000} MB")
                    }
                }
            } ?: error("Arquivo não pôde ser aberto")
            progress(99, "Verificando SHA-256...")
            require(matchesSha(tmp, spec)) { "GGUF diferente de ${spec.label}" }
            val done = finished(spec)
            if (done.exists()) require(done.delete()) { "Falha ao substituir GGUF" }
            check(tmp.renameTo(done)) { "Falha ao finalizar a importação" }
            prefs.edit().putString(key(spec), "${done.length()}:${done.lastModified()}:${spec.sha256}").apply()
            progress(100, "${spec.label} instalado")
            done
        } finally { tmp.delete() }
    }

    private fun matchesSha(file: File, spec: Spec): Boolean {
        if (!file.isFile || file.length() != spec.size) return false
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                md.update(buffer, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it.toInt() and 255) }.equals(spec.sha256, true)
    }
}
