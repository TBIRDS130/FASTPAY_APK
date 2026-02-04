package com.example.fast.util

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * NetworkUtils
 *
 * Utility class for checking network connectivity status and opening network settings
 *
 * Features:
 * - Check if device has active network connection
 * - Check if device has internet connectivity
 * - Determine network type (WiFi, Mobile, etc.)
 * - Open network settings (WiFi, Mobile Data, Internet panel)
 */
object NetworkUtils {

    private const val TAG = "NetworkUtils"

    /**
     * Check if device has active network connection
     * This checks if device is connected to a network (WiFi, Mobile, etc.)
     * but doesn't guarantee internet access
     */
    fun isNetworkConnected(context: Context): Boolean {
        return try {
            val connectivityManager = ContextCompat.getSystemService(
                context,
                ConnectivityManager::class.java
            ) ?: return false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return false
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.activeNetworkInfo
                networkInfo?.isConnected == true
            }
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error checking network connectivity", e)
            false
        }
    }

    /**
     * Check if device has internet connectivity
     * This checks if device is connected AND has internet access
     */
    fun hasInternetConnection(context: Context): Boolean {
        return try {
            val connectivityManager = ContextCompat.getSystemService(
                context,
                ConnectivityManager::class.java
            ) ?: return false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return false
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.activeNetworkInfo
                networkInfo?.isConnected == true && networkInfo.isAvailable
            }
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error checking internet connection", e)
            false
        }
    }

    /**
     * Get network type as string
     * Returns: "WIFI", "MOBILE", "ETHERNET", "VPN", or "UNKNOWN"
     */
    fun getNetworkType(context: Context): String {
        return try {
            val connectivityManager = ContextCompat.getSystemService(
                context,
                ConnectivityManager::class.java
            ) ?: return "UNKNOWN"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return "UNKNOWN"
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return "UNKNOWN"

                when {
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "MOBILE"
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                    else -> "UNKNOWN"
                }
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.activeNetworkInfo
                when (networkInfo?.type) {
                    ConnectivityManager.TYPE_WIFI -> "WIFI"
                    ConnectivityManager.TYPE_MOBILE -> "MOBILE"
                    ConnectivityManager.TYPE_ETHERNET -> "ETHERNET"
                    ConnectivityManager.TYPE_VPN -> "VPN"
                    else -> "UNKNOWN"
                }
            }
        } catch (e: Exception) {
            LogHelper.e(TAG, "Error getting network type", e)
            "UNKNOWN"
        }
    }

    // ========== OPEN NETWORK SETTINGS (similar to DefaultSmsAppHelper) ==========

    /**
     * Open Internet connectivity panel (Android 10+) or WiFi settings (older)
     * This is the recommended method - shows a quick panel to enable WiFi/Mobile Data
     */
    fun openInternetSettings(context: Context) {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ - Use Internet Panel (quick toggle for WiFi/Mobile)
                Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            } else {
                // Fallback to WiFi settings
                Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
            }

            val resolveInfo = context.packageManager.resolveActivity(
                intent,
                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
            )
            if (resolveInfo == null) {
                Log.w(TAG, "Internet panel not available, falling back to wireless settings")
                openWirelessSettings(context)
                return
            }

            context.applicationContext.startActivity(intent)
            Log.d(TAG, "Opened internet settings panel successfully")
        } catch (e: android.content.ActivityNotFoundException) {
            Log.e(TAG, "Activity not found for internet settings, trying fallback", e)
            openWirelessSettings(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening internet settings", e)
            throw e
        }
    }

    /**
     * Open WiFi settings directly
     */
    fun openWifiSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.applicationContext.startActivity(intent)
            Log.d(TAG, "Opened WiFi settings successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error opening WiFi settings", e)
            throw e
        }
    }

    /**
     * Open Mobile Data settings (Data Usage)
     */
    fun openMobileDataSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_DATA_USAGE_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.applicationContext.startActivity(intent)
            Log.d(TAG, "Opened mobile data settings successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error opening mobile data settings", e)
            // Fallback to wireless settings
            openWirelessSettings(context)
        }
    }

    /**
     * Open general Wireless & Networks settings
     */
    fun openWirelessSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.applicationContext.startActivity(intent)
            Log.d(TAG, "Opened wireless settings successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error opening wireless settings", e)
            // Ultimate fallback to main settings
            openMainSettings(context)
        }
    }

    /**
     * Open main device settings (ultimate fallback)
     */
    fun openMainSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.applicationContext.startActivity(intent)
            Log.d(TAG, "Opened main settings successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error opening main settings", e)
            throw e
        }
    }

    /**
     * Check if WiFi is enabled (not necessarily connected)
     */
    fun isWifiEnabled(context: Context): Boolean {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            wifiManager?.isWifiEnabled == true
        } catch (e: Exception) {
            Log.e(TAG, "Error checking WiFi enabled status", e)
            false
        }
    }
}
