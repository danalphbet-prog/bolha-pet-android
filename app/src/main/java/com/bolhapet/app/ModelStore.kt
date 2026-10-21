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

/** No model weights are bundled in the APK. GGUF stays in the private app directory. */
internal class ModelStore(private val context: Context) {
    companion object {
        const val MODEL_NAME = "Qwen2.5-3B-Instruct Q4_K_M"
        private const val FILE_NAME = "qwen2.5-3b-instruct-q4_k_m.gguf"
        private const val URL_GGUF = "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q4_k_m.gguf"
        private const val SHA256 = "626b4a6678b86442240e33df819e00132d3ba7dddfe1cdc4fbb18e0a9615c62d"
        private const val MIN_BYTES = 1_900_000_000L
        private const val EST_BYTES = 2_100_000_000L
    }

    private val dir get() = File(context.filesDir, "models").apply { mkdirs() }
    private val finished get() = File(dir, FILE_NAME)
    private val partial get() = File(dir, "$FILE_NAME.part")
    private val prefs get() = context.getSharedPreferences("bolha_model_v1", Context.MODE_PRIVATE)

    suspend fun verified(): File? = withContext(Dispatchers.IO) {
        val file = finished
        if (!file.isFile || file.length() < MIN_BYTES) return@withContext null
        val identity = "${file.length()}:${file.lastModified()}:$SHA256"
        if (prefs.getString("verified_identity", null) == identity) return@withContext file
        if (!matchesSha(file)) return@withContext null
        prefs.edit().putString("verified_identity", identity).apply()
        file
    }

    suspend fun download(progress: (Int, String) -> Unit): File = withContext(Dispatchers.IO) {
        verified()?.let { return@withContext it }
        val current = partial.length().takeIf { it > 0L } ?: 0L
        require(dir.usableSpace > ((EST_BYTES - current).coerceAtLeast(0L) + 240_000_000L)) {
            "Espaço insuficiente. Libere aproximadamente 2,5 GB para o Qwen 3B."
        }
        val conn = (URL(URL_GGUF).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 90_000
            instanceFollowRedirects = true
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("User-Agent", "BolhaPet-Android/0.1")
            if (current > 0) setRequestProperty("Range", "bytes=$current-")
        }
        try {
            val status = conn.responseCode
            require(status == 200 || status == 206) { "Servidor retornou HTTP $status" }
            val append = status == 206 && current > 0 &&
                conn.getHeaderField("Content-Range").orEmpty().startsWith("bytes $current-")
            if (status == 206 && !append) error("Servidor não confirmou o ponto de retomada")
            val expected = conn.contentLengthLong.takeIf { it > 0 } ?: (EST_BYTES - current.coerceAtMost(EST_BYTES))
            val total = expected + (if (append) current else 0L)
            var copied = if (append) current else 0L
            var lastUpdate = 0L
            progress(((copied * 100L) / total).toInt().coerceIn(0, 99), "Baixando Qwen 3B...")
            conn.inputStream.use { input ->
                FileOutputStream(partial, append).buffered().use { output ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        val size = input.read(buffer)
                        if (size < 0) break
                        output.write(buffer, 0, size)
                        copied += size
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 240L) {
                            progress(((copied * 100L) / total).toInt().coerceIn(0, 99),
                                "Baixado: ${copied / 1_000_000} de ${total / 1_000_000} MB")
                            lastUpdate = now
                        }
                    }
                    output.flush()
                }
            }
            progress(99, "Verificando SHA-256 do modelo...")
        } finally {
            conn.disconnect()
        }
        if (!matchesSha(partial)) {
            partial.delete()
            error("Integridade SHA-256 inválida. O arquivo foi descartado para sua segurança.")
        }
        if (finished.exists()) require(finished.delete()) { "Não foi possível substituir um GGUF inválido." }
        require(partial.renameTo(finished)) { "Não foi possível finalizar o arquivo do modelo." }
        val identity = "${finished.length()}:${finished.lastModified()}:$SHA256"
        prefs.edit().putString("verified_identity", identity).apply()
        progress(100, "Modelo instalado e verificado")
        finished
    }

    suspend fun import(uri: Uri, progress: (Int, String) -> Unit): File = withContext(Dispatchers.IO) {
        val destination = partial
        val temp = File(dir, "importing.gguf.tmp")
        temp.delete()
        try {
            progress(0, "Importando GGUF...")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(temp).buffered().use { output ->
                    val buf = ByteArray(128 * 1024)
                    var count = 0L
                    while (true) {
                        val size = input.read(buf)
                        if (size < 0) break
                        output.write(buf, 0, size)
                        count += size
                        if (count % (32L * 1024L * 1024L) < buf.size)
                            progress(0, "Importados ${count / 1_000_000} MB...")
                    }
                }
            } ?: error("Não foi possível abrir o arquivo selecionado")
            progress(99, "Conferindo SHA-256...")
            require(matchesSha(temp)) { "GGUF não corresponde ao Qwen2.5-3B Q4_K_M oficial." }
            if (finished.exists()) require(finished.delete()) { "Não foi possível substituir o GGUF" }
            require(temp.renameTo(finished)) { "Não foi possível salvar o GGUF" }
            destination.delete()
            val identity = "${finished.length()}:${finished.lastModified()}:$SHA256"
            prefs.edit().putString("verified_identity", identity).apply()
            progress(100, "Modelo importado com sucesso")
            finished
        } finally {
            temp.delete()
        }
    }

    private fun matchesSha(file: File): Boolean {
        if (!file.isFile || file.length() < MIN_BYTES) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val bytes = ByteArray(256 * 1024)
            while (true) {
                val count = input.read(bytes)
                if (count < 0) break
                digest.update(bytes, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }.equals(SHA256, true)
    }
}
