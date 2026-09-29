package com.yashmaurya.roadbrowser.data

import android.content.Context
import android.os.Build
import com.yashmaurya.roadbrowser.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the last few crashes in the app's private storage so a user can send one along with a
 * bug report. Nothing leaves the phone unless the user shares it from Settings; there is no
 * automatic upload of any kind.
 */
object CrashLog {

    private const val DIR = "crash"
    private const val FILE = "crashes.txt"
    private const val MAX_BYTES = 48 * 1024
    private const val SEPARATOR = "\n\n----------------------------------------\n\n"

    /** Records uncaught exceptions, then lets the previous handler end the process as usual. */
    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { record(appContext, thread.name, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun record(context: Context, threadName: String, error: Throwable) {
        val stack = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val entry = buildString {
            append("RoadBrowser ").append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE).append(")\n")
            append("Time: ").append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())).append('\n')
            append("Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
            append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
            append("Thread: ").append(threadName).append("\n\n")
            append(stack)
        }
        val file = file(context)
        file.parentFile?.mkdirs()
        // Newest first, trimmed so the file never grows past what a share sheet can carry.
        val combined = (entry + (read(context)?.let { SEPARATOR + it } ?: "")).take(MAX_BYTES)
        file.writeText(combined)
    }

    fun read(context: Context): String? {
        val file = file(context)
        return if (file.exists()) file.readText().takeIf { it.isNotBlank() } else null
    }

    fun clear(context: Context) {
        file(context).delete()
    }

    private fun file(context: Context) = File(File(context.filesDir, DIR), FILE)
}
