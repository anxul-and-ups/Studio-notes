package com.example.ui.util

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Saves the stack trace of an uncaught crash to a private file so the next
 * launch can show it on screen (no logcat / PC needed). No permissions used.
 */
object CrashReporter {
    private const val FILE_NAME = "last_crash.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                File(app.filesDir, FILE_NAME).writeText(
                    "Thread: ${thread.name}\n" + Log.getStackTraceString(throwable)
                )
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** Returns the saved crash text (and deletes it), or null if there was none. */
    fun consume(context: Context): String? {
        return try {
            val f = File(context.applicationContext.filesDir, FILE_NAME)
            if (f.exists()) {
                val text = f.readText()
                f.delete()
                text
            } else {
                // No Java stack trace saved: the process may have died from a
                // native crash / ANR, which a Java handler cannot see.
                exitInfoReport(context.applicationContext)
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun exitInfoReport(context: Context): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val last = am.getHistoricalProcessExitReasons(context.packageName, 0, 1)
                .firstOrNull() ?: return null
            val interesting = last.reason == ApplicationExitInfo.REASON_CRASH ||
                last.reason == ApplicationExitInfo.REASON_CRASH_NATIVE ||
                last.reason == ApplicationExitInfo.REASON_ANR
            if (!interesting) return null

            val prefs = context.getSharedPreferences("crash_reporter", Context.MODE_PRIVATE)
            if (prefs.getLong("last_shown_ts", 0L) >= last.timestamp) return null
            prefs.edit().putLong("last_shown_ts", last.timestamp).apply()

            "No Java stack trace saved (likely native/GPU crash or ANR)\n" +
                "Android: ${Build.VERSION.SDK_INT}  Device: ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                "Exit reason code: ${last.reason}\n" +
                "Description: ${last.description}\n" +
                "Status: ${last.status}"
        } catch (_: Throwable) {
            null
        }
    }
}
