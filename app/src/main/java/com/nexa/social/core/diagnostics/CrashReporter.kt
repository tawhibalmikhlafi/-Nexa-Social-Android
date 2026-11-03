package com.nexa.social.core.diagnostics

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Persists the last unexpected exception so the next launch can show a clear, actionable alert
 * instead of silently closing. The report is local-only (app private storage) and is deleted as
 * soon as it is read, so it is never uploaded anywhere.
 */
object CrashReporter {

    private const val FILE_NAME = "last_crash.txt"
    private const val MAX_REPORT_CHARS = 24_000

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(appContext, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** Returns the previous crash report (and removes it) or null when the last run was clean. */
    fun consumeLastCrash(context: Context): String? = runCatching {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return@runCatching null
        val report = file.readText()
        file.delete()
        report.takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun write(context: Context, thread: Thread, throwable: Throwable) {
        val writer = StringWriter()
        PrintWriter(writer).use { printer ->
            printer.println("time=${System.currentTimeMillis()}")
            printer.println("thread=${thread.name}")
            printer.println("android=${android.os.Build.VERSION.SDK_INT}")
            throwable.printStackTrace(printer)
        }
        File(context.filesDir, FILE_NAME)
            .writeText(writer.toString().take(MAX_REPORT_CHARS))
    }
}
