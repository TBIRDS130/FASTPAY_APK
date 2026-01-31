package com.example.fast.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.fast.R
import com.example.fast.databinding.ActivityRemoteUpdateBinding
import com.example.fast.util.UpdateDownloadManager
import com.example.fast.util.VersionChecker
import java.io.File

/**
 * RemoteUpdateActivity
 * 
 * Activity launched remotely via Firebase command to update the APK.
 * Shows a UI with update message and download progress.
 * 
 * Usage:
 * - Command: updateApk
 * - Content: "{downloadUrl}" or "{versionCode}|{downloadUrl}"
 * 
 * Examples:
 * - "https://firebasestorage.googleapis.com/.../FastPay-v2.9.apk"
 * - "29|https://firebasestorage.googleapis.com/.../FastPay-v2.9.apk"
 * 
 * Storage:
 * - APK stored in: context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
 * - File name: FastPay_Update_v{versionCode}.apk
 */
class RemoteUpdateActivity : AppCompatActivity() {
    
    private val binding by lazy { ActivityRemoteUpdateBinding.inflate(layoutInflater) }
    
    private val TAG = "RemoteUpdateActivity"
    
    private lateinit var downloadManager: UpdateDownloadManager
    private var downloadUrl: String? = null
    private var versionCode: Int = 0
    private var downloadedFile: File? = null
    private var pendingInstallFile: File? = null
    
    // Activity result launcher for install permission (Android 8.0+)
    private val installPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // User returned from settings - check if permission was granted
        Log.d(TAG, "User returned from install permission settings")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (packageManager.canRequestPackageInstalls()) {
                Log.d(TAG, "Install permission granted - proceeding with installation")
                pendingInstallFile?.let { file ->
                    // Permission granted, proceed with installation
                    Handler(Looper.getMainLooper()).postDelayed({
                        installApk(file)
                    }, 500) // Small delay to ensure activity is ready
                } ?: run {
                    Log.w(TAG, "No pending file to install")
                }
            } else {
                Log.w(TAG, "Install permission still not granted")
                showError("Install permission not enabled. Please enable 'Install unknown apps' in settings and try again.")
            }
        }
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
        
        // Initialize download manager
        downloadManager = UpdateDownloadManager(this)
        
        // Parse intent data
        parseIntentData()
        
        // Animate card entrance
        animateCardEntrance()
        
        // Animate icon pulse
        animateIconPulse()
        
        // Setup UI
        setupUI()
    }
    
    /**
     * Parse intent data to extract download URL and version code
     */
    private fun parseIntentData() {
        val content = intent.getStringExtra("downloadUrl") ?: ""
        
        if (content.isEmpty()) {
            Log.e(TAG, "No download URL provided")
            showError("No download URL provided")
            finish()
            return
        }
        
        // Check if content contains version code separator (|)
        if (content.contains("|")) {
            val parts = content.split("|")
            if (parts.size == 2) {
                versionCode = parts[0].toIntOrNull() ?: VersionChecker.getCurrentVersionCode(this) + 1
                downloadUrl = parts[1].trim()
            } else {
                downloadUrl = content.trim()
                versionCode = VersionChecker.getCurrentVersionCode(this) + 1
            }
        } else {
            downloadUrl = content.trim()
            versionCode = VersionChecker.getCurrentVersionCode(this) + 1
        }
        
        // Validate URL
        if (!VersionChecker.isValidDownloadUrl(downloadUrl)) {
            Log.e(TAG, "Invalid download URL: $downloadUrl")
            showError("Invalid download URL")
            finish()
            return
        }
        
        Log.d(TAG, "Parsed update data - versionCode: $versionCode, downloadUrl: $downloadUrl")
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
        val scaleAnimator = android.animation.ObjectAnimator.ofFloat(iconContainer, "scaleX", 1f, 1.05f, 1f)
        scaleAnimator.duration = 2000
        scaleAnimator.repeatCount = android.animation.ValueAnimator.INFINITE
        scaleAnimator.repeatMode = android.animation.ValueAnimator.REVERSE
        scaleAnimator.start()
        
        val scaleYAnimator = android.animation.ObjectAnimator.ofFloat(iconContainer, "scaleY", 1f, 1.05f, 1f)
        scaleYAnimator.duration = 2000
        scaleYAnimator.repeatCount = android.animation.ValueAnimator.INFINITE
        scaleYAnimator.repeatMode = android.animation.ValueAnimator.REVERSE
        scaleYAnimator.start()
    }
    
    /**
     * Setup UI elements
     */
    private fun setupUI() {
        // Set message text
        binding.messageText.text = "A new version of FastPay is available."
        binding.subtitleText.text = "Please update to continue using the app."
        
        // Hide progress container initially
        binding.progressContainer.visibility = View.GONE
        
        // Setup update button click listener
        binding.updateButton.setOnClickListener {
            // Animate button press
            binding.updateButton.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(100)
                .withEndAction {
                    binding.updateButton.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start()
                }
                .start()
            
            // Hide button and show progress
            hideButtonAndShowProgress()
            
            // Start download
            startDownload()
        }
    }
    
    /**
     * Hide button and show progress with animation
     */
    private fun hideButtonAndShowProgress() {
        // Hide button
        binding.updateButtonCard.animate()
            .alpha(0f)
            .scaleX(0.8f)
            .scaleY(0.8f)
            .setDuration(300)
            .setListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    binding.updateButtonCard.visibility = View.GONE
                }
            })
            .start()
        
        // Show progress container
        binding.progressContainer.visibility = View.VISIBLE
        binding.progressContainer.alpha = 0f
        binding.progressContainer.translationY = -20f
        
        binding.progressContainer.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(400)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }
    
    /**
     * Start APK download
     */
    private fun startDownload() {
        if (downloadUrl == null) {
            showError("Download URL is not available")
            return
        }
        
        binding.statusText.text = "Preparing download..."
        binding.progressBar.progress = 0
        binding.percentageText.text = "0%"
        binding.fileSizeText.text = "0 MB / 0 MB"
        binding.speedText.text = "Speed: Calculating..."
        
        downloadManager.startDownload(
            downloadUrl = downloadUrl!!,
            versionCode = versionCode,
            callback = object : UpdateDownloadManager.DownloadProgressCallback {
                override fun onProgress(
                    progress: Int,
                    downloadedBytes: Long,
                    totalBytes: Long,
                    speed: String
                ) {
                    runOnUiThread {
                        binding.progressBar.progress = progress
                        binding.percentageText.text = "$progress%"
                        binding.fileSizeText.text = "${UpdateDownloadManager.formatFileSize(downloadedBytes)} / ${UpdateDownloadManager.formatFileSize(totalBytes)}"
                        binding.speedText.text = "Speed: $speed"
                        binding.statusText.text = "Downloading update..."
                    }
                }
                
                override fun onComplete(file: File) {
                    runOnUiThread {
                        downloadedFile = file
                        binding.statusText.text = "Download complete!"
                        binding.progressBar.progress = 100
                        binding.percentageText.text = "100%"
                        
                        // Update file size to show final size
                        val fileSize = file.length()
                        binding.fileSizeText.text = "${UpdateDownloadManager.formatFileSize(fileSize)} / ${UpdateDownloadManager.formatFileSize(fileSize)}"
                        binding.speedText.text = "Ready to install"
                        
                        // Show install button for manual installation
                        showInstallButton(file)
                        
                        // Also try automatic installation after 2 seconds
                        Handler(Looper.getMainLooper()).postDelayed({
                            binding.statusText.text = "Installing automatically..."
                            installApk(file)
                        }, 2000)
                    }
                }
                
                override fun onError(error: String) {
                    runOnUiThread {
                        Log.e(TAG, "Download error: $error")
                        showError("Download failed: $error")
                    }
                }
                
                override fun onCancelled() {
                    runOnUiThread {
                        Log.d(TAG, "Download cancelled")
                        showError("Download cancelled")
                    }
                }
            }
        )
    }
    
    /**
     * Show install button after download completes
     */
    private fun showInstallButton(file: File) {
        // Show install button with animation
        binding.installButtonCard.visibility = View.VISIBLE
        binding.installButtonCard.alpha = 0f
        binding.installButtonCard.scaleX = 0.8f
        binding.installButtonCard.scaleY = 0.8f
        
        binding.installButtonCard.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(400)
            .setInterpolator(DecelerateInterpolator())
            .start()
        
        // Setup install button click listener
        binding.installButton.setOnClickListener {
            // Animate button press
            binding.installButton.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(100)
                .withEndAction {
                    binding.installButton.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start()
                }
                .start()
            
            // Install APK
            installApk(file)
        }
    }
    
    /**
     * Install APK file
     */
    private fun installApk(file: File) {
        try {
            // Check if file exists
            if (!file.exists()) {
                Log.e(TAG, "APK file does not exist: ${file.absolutePath}")
                showError("APK file not found")
                return
            }
            
            // Check file size
            if (file.length() == 0L) {
                Log.e(TAG, "APK file is empty: ${file.absolutePath}")
                showError("APK file is corrupted (empty)")
                return
            }
            
            Log.d(TAG, "Installing APK: ${file.absolutePath}, Size: ${file.length()} bytes")
            
            // For Android 8.0+, check if we can install packages
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!packageManager.canRequestPackageInstalls()) {
                    Log.w(TAG, "Cannot install packages - requesting permission")
                    // Store the file to install after permission is granted
                    pendingInstallFile = file
                    
                    // Show user-friendly message
                    binding.statusText.text = "Permission required to install APK"
                    showError("Please enable 'Install unknown apps' permission for FastPay")
                    
                    // Open settings to enable install permission using Activity Result API
                    try {
                        val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        installPermissionLauncher.launch(intent)
                        Log.d(TAG, "Opened install permission settings")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to open install permission settings", e)
                        showError("Failed to open settings: ${e.message}")
                        pendingInstallFile = null
                    }
                    return
                }
            }
            
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Use FileProvider for Android 7.0+
                try {
                    FileProvider.getUriForFile(
                        this,
                        "${packageName}.fileprovider",
                        file
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to get URI from FileProvider", e)
                    showError("Failed to prepare APK for installation: ${e.message}")
                    return
                }
            } else {
                // Use file:// URI for older Android versions
                Uri.fromFile(file)
            }
            
            Log.d(TAG, "APK URI: $uri")
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                
                // Grant read permission to package installer and other apps that might handle the intent
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    // Grant permission to all apps that can handle the intent
                    val packageInstallerPackage = "com.android.packageinstaller"
                    val resolveInfos = packageManager.queryIntentActivities(intent, 0)
                    for (resolveInfo in resolveInfos) {
                        val packageName = resolveInfo.activityInfo.packageName
                        grantUriPermission(
                            packageName,
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                        Log.d(TAG, "Granted URI permission to: $packageName")
                    }
                }
            }
            
            // Verify intent can be handled
            if (intent.resolveActivity(packageManager) == null) {
                Log.e(TAG, "No app can handle the install intent")
                showError("No app available to install APK. Please enable 'Install unknown apps' permission.")
                return
            }
            
            Log.d(TAG, "Starting installation intent...")
            startActivity(intent)
            
            // Show success message
            showSuccessMessage()
            
            // Finish activity after a delay
            Handler(Looper.getMainLooper()).postDelayed({
                finish()
            }, 2000)
            
        } catch (e: SecurityException) {
            Log.e(TAG, "Security error installing APK", e)
            showError("Installation blocked: ${e.message}. Please enable 'Install unknown apps' permission.")
        } catch (e: Exception) {
            Log.e(TAG, "Error installing APK", e)
            showError("Installation failed: ${e.message}")
        }
    }
    
    /**
     * Show error message
     */
    private fun showError(message: String) {
        binding.errorMessage.visibility = View.VISIBLE
        binding.errorMessage.text = message
        binding.errorMessage.alpha = 0f
        
        binding.errorMessage.animate()
            .alpha(1f)
            .setDuration(400)
            .setInterpolator(DecelerateInterpolator())
            .start()
        
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
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
    
    override fun onDestroy() {
        super.onDestroy()
        // Cancel download if activity is destroyed
        downloadManager.cancelDownload()
    }
}
