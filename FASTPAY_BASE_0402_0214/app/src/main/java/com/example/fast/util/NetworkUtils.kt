package com.example.fast.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * NetworkUtils
 *
 * Utility class for checking network connectivity status
 *
 * Features:
 * - Check if device has active network connection
 * - Check if device has internet connectivity
 * - Determine network type (WiFi, Mobile, etc.)
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
}
