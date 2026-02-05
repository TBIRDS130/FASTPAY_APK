package com.example.fast.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Singleton debug logger for capturing animation events, API calls, and errors.
 * - Logs are stored locally to file (persistent)
 * - On copy, old logs are cleared automatically
 * - Each log entry includes: absolute time, cumulative time, delta time
 */
object DebugLogger {
    private val logs = mutableListOf<LogEntry>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private var startTime: Long = 0
    private var appContext: Context? = null
    private const val LOG_FILE_NAME = "fastpay_debug_logs.txt"

    data class LogEntry(
        val timestamp: Long,
        val relativeMs: Long,
        val tag: String,
        val message: String,
        val type: LogType
    )

    enum class LogType { ANIMATION, API, ERROR, INFO }

    /** Initialize with app context for local file storage */
    fun init(context: Context) {
        appContext = context.applicationContext
        loadLogsFromFile()
    }

    fun startSession() {
        logs.clear()
        startTime = System.currentTimeMillis()
        log("INFO", "Logger session started", LogType.INFO)
    }

    fun logAnimation(step: String, detail: String = "") {
        val message = if (detail.isNotEmpty()) "$step - $detail" else step
        log("ANIM", message, LogType.ANIMATION)
    }

    fun logAnimationEnd(step: String, durationMs: Long) {
        log("ANIM", "$step completed (${durationMs}ms)", LogType.ANIMATION)
    }

    fun logApi(endpoint: String, status: String) {
        log("API", "$endpoint: $status", LogType.API)
    }

    fun logError(tag: String, error: String) {
        log("ERROR", "$tag: $error", LogType.ERROR)
    }

    fun logInfo(message: String) {
        log("INFO", message, LogType.INFO)
    }

    /**
     * Log activation check steps for diagnostics
     * @param step The check step (e.g., "Firebase", "Django", "Local")
     * @param result The result of the check
     */
    fun logActivationCheck(step: String, result: String) {
        log("ACTIVATION", "[$step] $result", LogType.INFO)
    }

    private fun log(tag: String, message: String, type: LogType) {
        val now = System.currentTimeMillis()
        val relative = if (startTime > 0) now - startTime else 0
        logs.add(LogEntry(now, relative, tag, message, type))
        saveLogsToFile() // Persist after each log
        
        // Also log to logcat for debugging
        LogHelper.d("DebugLogger", "[$tag] $message")
    }

    /** Save logs to local file */
    private fun saveLogsToFile() {
        appContext?.let { ctx ->
            try {
                val file = File(ctx.filesDir, LOG_FILE_NAME)
                file.writeText(getLogsAsText())
            } catch (e: Exception) {
                // Silent fail for logging
            }
        }
    }

    /** Load previous logs from file (if app was killed) */
    private fun loadLogsFromFile() {
        appContext?.let { ctx ->
            try {
                val file = File(ctx.filesDir, LOG_FILE_NAME)
                if (file.exists()) {
                    // Logs exist from previous session - keep them until copied
                    // We don't parse them back, just note they exist
                    LogHelper.d("DebugLogger", "Previous log file exists")
                }
            } catch (e: Exception) {
                // Silent fail
            }
        }
    }

    /** Clear all logs and delete local file */
    private fun clearLogs() {
        logs.clear()
        startTime = 0
        appContext?.let { ctx ->
            try {
                val file = File(ctx.filesDir, LOG_FILE_NAME)
                if (file.exists()) file.delete()
            } catch (e: Exception) {
                // Silent fail
            }
        }
    }

    fun getLogsAsText(): String {
        return buildString {
            appendLine("=== FASTPAY DEBUG LOG ===")
            appendLine("Generated: ${dateFormat.format(Date())}")
            appendLine("Total entries: ${logs.size}")
            appendLine("========================")
            appendLine()
            var prevTimestamp = if (startTime > 0) startTime else (logs.firstOrNull()?.timestamp ?: System.currentTimeMillis())
            logs.forEach { entry ->
                val time = dateFormat.format(Date(entry.timestamp))
                val total = "Total: ${entry.relativeMs}ms"
                val delta = "Δ: +${entry.timestamp - prevTimestamp}ms"
                appendLine("[$time] [$total] [$delta] [${entry.tag}] ${entry.message}")
                prevTimestamp = entry.timestamp
            }
        }
    }

    /** Get log count */
    fun getLogCount(): Int = logs.size

    /**
     * Copy logs to clipboard and CLEAR old logs automatically
     */
    fun copyToClipboardAndClear(context: Context) {
        val logText = getLogsAsText()
        val entryCount = logs.size

        // Copy to clipboard
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("FastPay Debug Logs", logText)
        clipboard.setPrimaryClip(clip)

        // Clear old logs after copy
        clearLogs()

        // Start fresh session
        startSession()

        Toast.makeText(context, "Logs copied ($entryCount entries) - Old logs cleared", Toast.LENGTH_SHORT).show()
    }
}
