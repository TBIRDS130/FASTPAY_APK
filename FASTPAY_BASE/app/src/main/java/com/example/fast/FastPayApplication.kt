package com.example.fast

import android.app.Application
import com.example.fast.util.FirebaseCallTracker
import com.example.fast.util.Logger
import com.google.firebase.Firebase
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.database.database
import dagger.hilt.android.HiltAndroidApp

/**
 * FastPay Application class
 * 
 * Initializes Firebase with offline persistence support
 * This enables the app to work offline and cache Firebase data locally
 * 
 * Note: Firebase persistence must be enabled before any database reference is used
 */
@HiltAndroidApp
class FastPayApplication : Application() {
    
    companion object {
        private var persistenceEnabled = false
        
        /**
         * Check if persistence has been enabled
         * Used to prevent multiple enable attempts
         */
        fun isPersistenceEnabled(): Boolean = persistenceEnabled
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Logger first (needed for other initialization logs)
        Logger.initialize()
        
        // Initialize Firebase Crashlytics
        initializeCrashlytics()
        
        // Initialize Firebase Call Tracker
        FirebaseCallTracker.initialize(this)
        
        // Enable Firebase Realtime Database offline persistence
        // This allows the app to work offline by caching data locally
        // IMPORTANT: This must be called before any Firebase database reference is used
        enableFirebasePersistence()
    }
    
    /**
     * Initialize Firebase Crashlytics for crash reporting
     * 
     * Crashlytics will automatically collect crashes and send them to Firebase.
     * In debug builds, crashes are only sent when explicitly enabled.
     */
    private fun initializeCrashlytics() {
        val crashlytics = FirebaseCrashlytics.getInstance()
        
        // Enable Crashlytics collection in debug builds (optional, for testing)
        // In production, Crashlytics is always enabled
        // Uncomment the line below to enable crash reporting in debug builds
        // if (BuildConfig.DEBUG) {
        //     crashlytics.setCrashlyticsCollectionEnabled(true)
        // }
        
        // Set user identifier (optional, for tracking)
        // crashlytics.setUserId("user-id-here")
        
        // Set custom keys (optional, for filtering crashes)
        // crashlytics.setCustomKey("app_version", "3.0")
        // crashlytics.setCustomKey("version_code", "30")
        
        Logger.d("FastPayApplication", "Firebase Crashlytics initialized")
    }
    
    private fun enableFirebasePersistence() {
        if (persistenceEnabled) {
            Logger.d("FastPayApplication", "Firebase persistence already enabled")
            return
        }
        
        try {
            // Enable persistence - must be called before any database references
            Firebase.database.setPersistenceEnabled(true)
            
            // Set cache size to 10MB (default is 10MB, but explicit is better)
            // This controls how much data can be cached locally
            Firebase.database.setPersistenceCacheSizeBytes(10 * 1024 * 1024) // 10MB
            
            persistenceEnabled = true
            Logger.d("FastPayApplication", "Firebase offline persistence enabled (10MB cache)")
        } catch (e: IllegalStateException) {
            // Persistence can only be enabled once, before any database reference is used
            // If we get here, persistence may already be enabled elsewhere (unlikely in our app)
            Logger.w("FastPayApplication", e, "Firebase persistence may already be enabled")
            persistenceEnabled = true // Assume it's enabled
        } catch (e: Exception) {
            // Other errors should be logged
            Logger.e("FastPayApplication", e, "Failed to enable Firebase persistence")
        }
    }
}
