package com.alauncher.app.service

import android.content.Context
import android.content.Intent
import android.os.Process
import timber.log.Timber
import kotlin.system.exitProcess

class GlobalExceptionHandler(
    context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {
    private val applicationContext = context.applicationContext

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            Timber.e(throwable, "Uncaught exception in thread ${thread.name}")
            
            // Start our custom error activity safely bounded for IPC Binder buffer
            val safeStackTrace = formatStackTrace(throwable.stackTraceToString())
            val intent = Intent(applicationContext, ErrorActivity::class.java).apply {
                putExtra("error_message", throwable.message ?: "Unknown error")
                putExtra("stack_trace", safeStackTrace)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            applicationContext.startActivity(intent)
            
            // Kill the current process
            Process.killProcess(Process.myPid())
            exitProcess(10)
        } catch (e: Exception) {
            // Fallback to default handler if something goes wrong here
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        internal const val MAX_STACK_TRACE_LENGTH = 20_000
        internal const val TRUNCATION_DISCLAIMER = "\n\n... [Stack trace truncated to prevent IPC TransactionTooLargeException]"

        internal fun formatStackTrace(rawStackTrace: String): String {
            return if (rawStackTrace.length > MAX_STACK_TRACE_LENGTH) {
                rawStackTrace.take(MAX_STACK_TRACE_LENGTH) + TRUNCATION_DISCLAIMER
            } else {
                rawStackTrace
            }
        }

        fun initialize(context: Context) {
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler(GlobalExceptionHandler(context, defaultHandler))
        }
    }
}
