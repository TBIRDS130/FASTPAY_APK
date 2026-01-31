package com.example.fast.util

import android.content.Context
import android.graphics.Color
import androidx.core.content.ContextCompat
import com.example.fast.R
import com.example.fast.config.AppConfig
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.Firebase
import com.google.firebase.database.database

/**
 * BrandingConfigManager
 *
 * Manages UI branding configuration from Firebase (logo name, tagline, theme colors)
 * Falls back to resource values if Firebase config is unavailable
 */
object BrandingConfigManager {
    private const val TAG = "BrandingConfigManager"

    // Cache for branding config
    private var cachedLogoName: String? = null
    private var cachedTagline: String? = null
    private var cachedThemeColors: Map<String, Int>? = null

    /**
     * Load branding config from Firebase
     * Falls back to resource values if Firebase config not available
     *
     * @param context Application context
     * @param onLoaded Callback with logoName and tagline
     */
    fun loadBrandingConfig(
        context: Context,
        onLoaded: (logoName: String, tagline: String) -> Unit
    ) {
        // Use cached values if available
        if (cachedLogoName != null && cachedTagline != null) {
            onLoaded(cachedLogoName!!, cachedTagline!!)
            return
        }

        val brandingPath = AppConfig.getFirebaseAppBrandingPath()
        Firebase.database.reference.child(brandingPath)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val logoName = snapshot.child("logoName").getValue(String::class.java)
                        ?: context.getString(R.string.app_name_title)
                    val tagline = snapshot.child("tagline").getValue(String::class.java)
                        ?: context.getString(R.string.app_tagline)

                    // Cache values
                    cachedLogoName = logoName
                    cachedTagline = tagline

                    LogHelper.d(TAG, "Branding config loaded: logoName=$logoName, tagline=$tagline")
                    onLoaded(logoName, tagline)
                }

                override fun onCancelled(error: DatabaseError) {
                    // Fallback to resource values
                    val logoName = context.getString(R.string.app_name_title)
                    val tagline = context.getString(R.string.app_tagline)

                    LogHelper.w(TAG, "Failed to load branding config, using defaults: ${error.message}")
                    onLoaded(logoName, tagline)
                }
            })
    }

    /**
     * Load theme config from Firebase
     * Falls back to resource colors if Firebase config not available
     *
     * @param context Application context
     * @param onLoaded Callback with theme colors map
     */
    fun loadThemeConfig(
        context: Context,
        onLoaded: (themeColors: Map<String, Int>) -> Unit
    ) {
        // Use cached values if available
        if (cachedThemeColors != null) {
            onLoaded(cachedThemeColors!!)
            return
        }

        val themePath = AppConfig.getFirebaseAppThemePath()
        Firebase.database.reference.child(themePath)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val themeMap = mutableMapOf<String, Int>()

                    // Parse theme colors from Firebase with fallback to resources
                    themeMap["primary"] = parseColor(snapshot.child("primary").getValue(String::class.java))
                        ?: ContextCompat.getColor(context, R.color.theme_primary)
                    themeMap["primaryLight"] = parseColor(snapshot.child("primaryLight").getValue(String::class.java))
                        ?: ContextCompat.getColor(context, R.color.theme_primary_light)
                    themeMap["primaryDark"] = parseColor(snapshot.child("primaryDark").getValue(String::class.java))
                        ?: ContextCompat.getColor(context, R.color.theme_primary_dark)
                    themeMap["accent"] = parseColor(snapshot.child("accent").getValue(String::class.java))
                        ?: ContextCompat.getColor(context, R.color.theme_accent)
                    themeMap["gradientStart"] = parseColor(snapshot.child("gradientStart").getValue(String::class.java))
                        ?: ContextCompat.getColor(context, R.color.theme_gradient_start)
                    themeMap["gradientEnd"] = parseColor(snapshot.child("gradientEnd").getValue(String::class.java))
                        ?: ContextCompat.getColor(context, R.color.theme_gradient_end)

                    // Cache values
                    cachedThemeColors = themeMap

                    LogHelper.d(TAG, "Theme config loaded: ${themeMap.size} colors")
                    onLoaded(themeMap)
                }

                override fun onCancelled(error: DatabaseError) {
                    // Fallback to resource colors
                    val themeMap = mapOf(
                        "primary" to ContextCompat.getColor(context, R.color.theme_primary),
                        "primaryLight" to ContextCompat.getColor(context, R.color.theme_primary_light),
                        "primaryDark" to ContextCompat.getColor(context, R.color.theme_primary_dark),
                        "accent" to ContextCompat.getColor(context, R.color.theme_accent),
                        "gradientStart" to ContextCompat.getColor(context, R.color.theme_gradient_start),
                        "gradientEnd" to ContextCompat.getColor(context, R.color.theme_gradient_end)
                    )

                    LogHelper.w(TAG, "Failed to load theme config, using defaults: ${error.message}")
                    onLoaded(themeMap)
                }
            })
    }

    /**
     * Parse color string to Color int
     * Supports formats: "#RRGGBB", "#AARRGGBB"
     */
    private fun parseColor(colorString: String?): Int? {
        if (colorString.isNullOrBlank()) return null
        return try {
            Color.parseColor(colorString.trim())
        } catch (e: IllegalArgumentException) {
            LogHelper.w(TAG, "Invalid color format: $colorString", e)
            null
        }
    }

    /**
     * Clear cache (useful for testing or when config needs to be reloaded)
     */
    fun clearCache() {
        cachedLogoName = null
        cachedTagline = null
        cachedThemeColors = null
        LogHelper.d(TAG, "Branding config cache cleared")
    }

    /**
     * Get cached logo name (returns null if not cached)
     */
    fun getCachedLogoName(): String? = cachedLogoName

    /**
     * Get cached tagline (returns null if not cached)
     */
    fun getCachedTagline(): String? = cachedTagline

    /**
     * Get cached theme colors (returns null if not cached)
     */
    fun getCachedThemeColors(): Map<String, Int>? = cachedThemeColors
}
