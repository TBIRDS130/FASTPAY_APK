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
    private val animationStartTimes = mutableMapOf<String, Long>()

    data class LogEntry(
        val timestamp: Long,
        val relativeMs: Long,
        val tag: String,
        val message: String,
        val type: LogType
    )

    enum class LogType { ANIMATION, API, ERROR, INFO, FLOW, SCREEN, PLACEMENT }

    /** Initialize with app context for local file storage */
    fun init(context: Context) {
        appContext = context.applicationContext
        loadLogsFromFile()
    }

    fun startSession() {
        logs.clear()
        animationStartTimes.clear()
        startTime = System.currentTimeMillis()
        log("INFO", "Logger session started", LogType.INFO)
    }

    fun logAnimation(step: String, detail: String = "") {
        val message = if (detail.isNotEmpty()) "$step - $detail" else step
        log("ANIM", message, LogType.ANIMATION)
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

    /**
     * Log flow markers (screen start/end, decisions)
     */
    fun logFlow(screen: String, step: String, detail: String = "") {
        val message = if (detail.isNotEmpty()) "[$screen] $step - $detail" else "[$screen] $step"
        log("FLOW", message, LogType.FLOW)
    }

    /**
     * Store animation start time; use with logAnimationEnd to log actual elapsed time
     */
    fun logAnimationStart(step: String, detail: String = "") {
        animationStartTimes[step] = System.currentTimeMillis()
        val message = if (detail.isNotEmpty()) "$step - $detail (start)" else "$step (start)"
        log("ANIM", message, LogType.ANIMATION)
    }

    /**
     * Log animation end with actual elapsed time (if start was logged) and expected duration
     */
    fun logAnimationEnd(step: String, expectedMs: Long) {
        val start = animationStartTimes.remove(step)
        val elapsed = if (start != null) System.currentTimeMillis() - start else expectedMs
        log("ANIM", "$step completed (elapsed=${elapsed}ms, expected=${expectedMs}ms)", LogType.ANIMATION)
    }

    /**
     * Log component visibility/state changes
     */
    fun logVisibility(component: String, state: String, extras: String = "") {
        val message = if (extras.isNotEmpty()) "$component=$state $extras" else "$component=$state"
        log("SCREEN", message, LogType.SCREEN)
    }

    /**
     * Log a full screen snapshot: one line with all element=value pairs.
     * Values: V (visible), G (gone), I (invisible); optional (alpha) e.g. V(1.0); smsSide: sms|instruction.
     */
    fun logScreenSnapshot(screen: String, elements: Map<String, String>) {
        val parts = elements.entries.joinToString(" | ") { "${it.key}=${it.value}" }
        log("SCREEN", "[$screen] $parts", LogType.SCREEN)
    }

    /**
     * Log component placement (x, y, width, height, alpha, source)
     */
    fun logPlacement(
        context: String,
        component: String,
        x: Float,
        y: Float,
        w: Int,
        h: Int,
        alpha: Float,
        source: String
    ) {
        log("PLACEMENT", "$context | $component at ($x,$y) size ($w,$h) alpha=$alpha source=$source", LogType.PLACEMENT)
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
        animationStartTimes.clear()
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
