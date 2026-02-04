package com.example.fast.util.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log

/**
 * AdaptiveBatchConfig
 *
 * Provides dynamic batch configuration based on network conditions.
 * Optimizes sync behavior for different network types and quality levels.
 *
 * Network-Aware Strategy:
 * - WiFi (strong): 10 seconds timeout, immediate upload enabled
 * - WiFi (weak): 20 seconds timeout
 * - Mobile (strong): 30 seconds timeout
 * - Mobile (weak/metered): 60 seconds timeout
 * - No network: Queue only, no upload attempts
 *
 * Usage:
 * ```kotlin
 * val config = AdaptiveBatchConfig.getConfig(context)
 * if (config.shouldUploadImmediately) {
 *     // Bypass batching, upload directly
 * } else {
 *     // Use config.batchTimeoutMs for scheduling
 * }
 * ```
 */
object AdaptiveBatchConfig {

    private const val TAG = "AdaptiveBatchConfig"

    // Timeout constants (in milliseconds)
    private const val WIFI_STRONG_TIMEOUT_MS = 10_000L      // 10 seconds
    private const val WIFI_WEAK_TIMEOUT_MS = 20_000L        // 20 seconds
    private const val MOBILE_STRONG_TIMEOUT_MS = 30_000L    // 30 seconds
    private const val MOBILE_WEAK_TIMEOUT_MS = 60_000L      // 60 seconds
    private const val OFFLINE_TIMEOUT_MS = 300_000L         // 5 minutes (queue only)

    // Default timeout when network state is unknown
    private const val DEFAULT_TIMEOUT_MS = 30_000L          // 30 seconds

    /**
     * Network quality levels
     */
    enum class NetworkQuality {
        EXCELLENT,  // Fast, unmetered connection
        GOOD,       // Decent speed, may be metered
        POOR,       // Slow or unstable connection
        OFFLINE     // No network available
    }

    /**
     * Network type classification
     */
    enum class NetworkType {
        WIFI,
        MOBILE,
        ETHERNET,
        UNKNOWN,
        NONE
    }

    /**
     * Batch configuration based on current network conditions
     */
    data class BatchConfig(
        val networkType: NetworkType,
        val networkQuality: NetworkQuality,
        val batchTimeoutMs: Long,
        val shouldUploadImmediately: Boolean,
        val isMetered: Boolean,
        val maxBatchSize: Int
    ) {
        companion object {
            /**
             * Default offline configuration
             */
            val OFFLINE = BatchConfig(
                networkType = NetworkType.NONE,
                networkQuality = NetworkQuality.OFFLINE,
                batchTimeoutMs = OFFLINE_TIMEOUT_MS,
                shouldUploadImmediately = false,
                isMetered = false,
                maxBatchSize = 100
            )
        }
    }

    /**
     * Get current batch configuration based on network conditions
     *
     * @param context Application context
     * @return BatchConfig with optimal settings for current network
     */
    fun getConfig(context: Context): BatchConfig {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return BatchConfig.OFFLINE

        val network = connectivityManager.activeNetwork
            ?: return BatchConfig.OFFLINE

        val capabilities = connectivityManager.getNetworkCapabilities(network)
            ?: return BatchConfig.OFFLINE

        val networkType = determineNetworkType(capabilities)
        val networkQuality = determineNetworkQuality(capabilities)
        val isMetered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)

        val (timeoutMs, immediateUpload, batchSize) = when (networkType) {
            NetworkType.WIFI -> when (networkQuality) {
                NetworkQuality.EXCELLENT -> Triple(WIFI_STRONG_TIMEOUT_MS, true, 50)
                NetworkQuality.GOOD -> Triple(WIFI_WEAK_TIMEOUT_MS, true, 75)
                NetworkQuality.POOR -> Triple(MOBILE_WEAK_TIMEOUT_MS, false, 100)
                NetworkQuality.OFFLINE -> Triple(OFFLINE_TIMEOUT_MS, false, 100)
            }
            NetworkType.ETHERNET -> Triple(WIFI_STRONG_TIMEOUT_MS, true, 50)
            NetworkType.MOBILE -> when (networkQuality) {
                NetworkQuality.EXCELLENT -> Triple(MOBILE_STRONG_TIMEOUT_MS, false, 75)
                NetworkQuality.GOOD -> Triple(MOBILE_STRONG_TIMEOUT_MS, false, 100)
                NetworkQuality.POOR -> Triple(MOBILE_WEAK_TIMEOUT_MS, false, 100)
                NetworkQuality.OFFLINE -> Triple(OFFLINE_TIMEOUT_MS, false, 100)
            }
            NetworkType.UNKNOWN -> Triple(DEFAULT_TIMEOUT_MS, false, 100)
            NetworkType.NONE -> Triple(OFFLINE_TIMEOUT_MS, false, 100)
        }

        val config = BatchConfig(
            networkType = networkType,
            networkQuality = networkQuality,
            batchTimeoutMs = timeoutMs,
            shouldUploadImmediately = immediateUpload,
            isMetered = isMetered,
            maxBatchSize = batchSize
        )

        Log.d(TAG, "Network config: type=$networkType, quality=$networkQuality, " +
                "timeout=${timeoutMs}ms, immediate=$immediateUpload, metered=$isMetered")

        return config
    }

    /**
     * Determine network type from capabilities
     */
    private fun determineNetworkType(capabilities: NetworkCapabilities): NetworkType {
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.MOBILE
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> {
                // VPN - check underlying transport
                when {
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.MOBILE
                    else -> NetworkType.UNKNOWN
                }
            }
            else -> NetworkType.UNKNOWN
        }
    }

    /**
     * Determine network quality from capabilities
     */
    private fun determineNetworkQuality(capabilities: NetworkCapabilities): NetworkQuality {
        // Check if network is validated (has internet connectivity)
        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return NetworkQuality.OFFLINE
        }

        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
            return NetworkQuality.POOR
        }

        // Check bandwidth (API 21+)
        val downstreamBandwidth = capabilities.linkDownstreamBandwidthKbps
        val upstreamBandwidth = capabilities.linkUpstreamBandwidthKbps

        return when {
            // Excellent: > 10 Mbps downstream, > 2 Mbps upstream
            downstreamBandwidth > 10_000 && upstreamBandwidth > 2_000 -> NetworkQuality.EXCELLENT
            // Good: > 2 Mbps downstream, > 500 Kbps upstream
            downstreamBandwidth > 2_000 && upstreamBandwidth > 500 -> NetworkQuality.GOOD
            // Poor: anything else
            else -> NetworkQuality.POOR
        }
    }

    /**
     * Check if network is available for upload
     */
    fun isNetworkAvailable(context: Context): Boolean {
        val config = getConfig(context)
        return config.networkType != NetworkType.NONE && config.networkQuality != NetworkQuality.OFFLINE
    }

    /**
     * Check if current network is WiFi
     */
    fun isWifiConnected(context: Context): Boolean {
        val config = getConfig(context)
        return config.networkType == NetworkType.WIFI || config.networkType == NetworkType.ETHERNET
    }

    /**
     * Check if immediate upload should be used (fast, unmetered connection)
     */
    fun shouldUploadImmediately(context: Context): Boolean {
        val config = getConfig(context)
        return config.shouldUploadImmediately
    }

    /**
     * Get recommended batch timeout for current network
     */
    fun getBatchTimeoutMs(context: Context): Long {
        val config = getConfig(context)
        return config.batchTimeoutMs
    }

    /**
     * Get recommended batch size for current network
     */
    fun getMaxBatchSize(context: Context): Int {
        val config = getConfig(context)
        return config.maxBatchSize
    }
}
