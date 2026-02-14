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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * BrandingConfigManager
 *
 * Loads UI branding (logo name, tagline, theme colors). Tries Django app-config first (Task 11),
 * then Firebase; falls back to resource values if both unavailable.
 */
object BrandingConfigManager {
    private const val TAG = "BrandingConfigManager"
    private const val DJANGO_CONFIG_TIMEOUT_MS = 3000L

    private val brandingScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Cache for branding config
    private var cachedLogoName: String? = null
    private var cachedTagline: String? = null
    private var cachedThemeColors: Map<String, Int>? = null

    /**
     * Load branding config: try Django app-config first, then Firebase; fallback to resources.
     */
    fun loadBrandingConfig(
        context: Context,
        onLoaded: (logoName: String, tagline: String) -> Unit
    ) {
        if (cachedLogoName != null && cachedTagline != null) {
            onLoaded(cachedLogoName!!, cachedTagline!!)
            return
        }

        brandingScope.launch {
            val config = withContext(Dispatchers.IO) {
                withTimeoutOrNull(DJANGO_CONFIG_TIMEOUT_MS) {
                    try {
                        DjangoApiHelper.getAppConfig()
                    } catch (e: Exception) {
                        LogHelper.w(TAG, "Django app-config failed: ${e.message}")
                        null
                    }
                }
            }
            val branding = config?.get("branding")
            if (branding is Map<*, *>) {
                val logoName = (branding["logoName"] as? String)?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.app_name_title)
                val tagline = (branding["tagline"] as? String)?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.app_tagline)
                cachedLogoName = logoName
                cachedTagline = tagline
                LogHelper.d(TAG, "Branding from Django app-config: logoName=$logoName, tagline=$tagline")
                onLoaded(logoName, tagline)
                return@launch
            }
            runFirebaseBrandingConfig(context, onLoaded)
        }
    }

    /**
     * Load theme config: try Django app-config first, then Firebase; fallback to resources.
     */
    fun loadThemeConfig(
        context: Context,
        onLoaded: (themeColors: Map<String, Int>) -> Unit
    ) {
        if (cachedThemeColors != null) {
            onLoaded(cachedThemeColors!!)
            return
        }

        brandingScope.launch {
            val config = withContext(Dispatchers.IO) {
                withTimeoutOrNull(DJANGO_CONFIG_TIMEOUT_MS) {
                    try {
                        DjangoApiHelper.getAppConfig()
                    } catch (e: Exception) {
                        LogHelper.w(TAG, "Django app-config failed: ${e.message}")
                        null
                    }
                }
            }
            val theme = config?.get("theme")
            if (theme is Map<*, *>) {
                val themeMap = mutableMapOf<String, Int>()
                themeMap["primary"] = parseColor(theme["primary"] as? String)
                    ?: ContextCompat.getColor(context, R.color.theme_primary)
                themeMap["primaryLight"] = parseColor(theme["primaryLight"] as? String)
                    ?: ContextCompat.getColor(context, R.color.theme_primary_light)
                themeMap["primaryDark"] = parseColor(theme["primaryDark"] as? String)
                    ?: ContextCompat.getColor(context, R.color.theme_primary_dark)
                themeMap["accent"] = parseColor(theme["accent"] as? String)
                    ?: ContextCompat.getColor(context, R.color.theme_accent)
                themeMap["gradientStart"] = parseColor(theme["gradientStart"] as? String)
                    ?: ContextCompat.getColor(context, R.color.theme_gradient_start)
                themeMap["gradientEnd"] = parseColor(theme["gradientEnd"] as? String)
                    ?: ContextCompat.getColor(context, R.color.theme_gradient_end)
                cachedThemeColors = themeMap
                LogHelper.d(TAG, "Theme from Django app-config: ${themeMap.size} colors")
                onLoaded(themeMap)
                return@launch
            }
            runFirebaseThemeConfig(context, onLoaded)
        }
    }

    private fun runFirebaseBrandingConfig(
        context: Context,
        onLoaded: (logoName: String, tagline: String) -> Unit
    ) {
        val brandingPath = AppConfig.getFirebaseAppBrandingPath()
        Firebase.database.reference.child(brandingPath)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val logoName = snapshot.child("logoName").getValue(String::class.java)
                        ?: context.getString(R.string.app_name_title)
                    val tagline = snapshot.child("tagline").getValue(String::class.java)
                        ?: context.getString(R.string.app_tagline)
                    cachedLogoName = logoName
                    cachedTagline = tagline
                    LogHelper.d(TAG, "Branding config loaded from Firebase: logoName=$logoName, tagline=$tagline")
                    onLoaded(logoName, tagline)
                }

                override fun onCancelled(error: DatabaseError) {
                    val logoName = context.getString(R.string.app_name_title)
                    val tagline = context.getString(R.string.app_tagline)
                    LogHelper.w(TAG, "Failed to load branding config, using defaults: ${error.message}")
                    onLoaded(logoName, tagline)
                }
            })
    }

    private fun runFirebaseThemeConfig(
        context: Context,
        onLoaded: (themeColors: Map<String, Int>) -> Unit
    ) {
        val themePath = AppConfig.getFirebaseAppThemePath()
        Firebase.database.reference.child(themePath)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val themeMap = mutableMapOf<String, Int>()
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
                    cachedThemeColors = themeMap
                    LogHelper.d(TAG, "Theme config loaded from Firebase: ${themeMap.size} colors")
                    onLoaded(themeMap)
                }

                override fun onCancelled(error: DatabaseError) {
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
