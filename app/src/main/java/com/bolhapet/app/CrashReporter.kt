package com.bolhapet.app

import android.app.Application
import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/** Keeps a diagnostic report on disk so a launcher crash can be inspected next time. */
class BolhaPetApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            CrashReporter.record(this, error)
            if (previous != null) previous.uncaughtException(thread, error)
            else android.os.Process.killProcess(android.os.Process.myPid())
        }
    }
}

internal object CrashReporter {
    private const val TAG = "BolhaPetCrash"
    private fun reportFile(context: Context) = File(context.filesDir, "bolha-crash-report.txt")

    fun record(context: Context, error: Throwable) {
        Log.e(TAG, "Falha da Bolha Pet", error)
        try {
            val trace = StringWriter().also { writer ->
                error.printStackTrace(PrintWriter(writer))
            }.toString().take(12000)
            reportFile(context).writeText("Bolha Pet · erro de execução\n\n$trace")
        } catch (_: Throwable) {
            // Never replace the original crash with a diagnostic failure.
        }
    }

    fun read(context: Context): String? = runCatching {
        reportFile(context).takeIf { it.isFile }?.readText()?.take(12000)
    }.getOrNull()

    fun clear(context: Context) { runCatching { reportFile(context).delete() } }
}
