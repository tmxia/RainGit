package me.rain.git

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashHandler {
    private const val TAG = "RainGitCrash"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val text = buildReport(thread, throwable)
                val file = writeReport(app, text)
                Log.e(TAG, "崩溃已记录: ${file?.absolutePath}", throwable)
            } catch (_: Throwable) {
            } finally {
                previous?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun buildReport(thread: Thread, throwable: Throwable): String {
        val sw = StringWriter()
        PrintWriter(sw).use { throwable.printStackTrace(it) }
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        return buildString {
            appendLine("========== RainGit Crash ==========")
            appendLine("时间: $time")
            appendLine("线程: ${thread.name}")
            appendLine("机型: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("App 版本: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine()
            appendLine("---------- Stacktrace ----------")
            append(sw.toString())
        }
    }

    private fun writeReport(app: Context, text: String): File? {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val name = "crash-$stamp.log"
        val ext = File("/sdcard/RainGit/crash")
        if (ext.isDirectory || ext.mkdirs()) {
            val f = File(ext, name)
            runCatching { f.writeText(text); return f }
        }
        val priv = File(app.filesDir, "crash").apply { mkdirs() }
        val f = File(priv, name)
        return runCatching { f.writeText(text); f }.getOrNull()
    }
}
