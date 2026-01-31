package com.example.fast.util

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import android.Manifest
import android.util.Log
import com.example.fast.service.ContactSmsSyncService

/**
 * PermissionSyncHelper
 * 
 * Automatically starts contact and SMS sync as soon as permissions are granted.
 * Works across all activities - triggers sync immediately when permissions available.
 */
object PermissionSyncHelper {
    private const val TAG = "PermissionSyncHelper"
    
    /**
     * Check if required permissions are granted and start sync if available
     * Call this whenever permissions might have changed
     * 
     * @param context Application context
     */
    fun checkAndStartSync(context: Context) {
        val hasContactsPermission = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        
        val hasSmsPermission = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
        
        when {
            hasContactsPermission && hasSmsPermission -> {
                // Both permissions granted - start full sync
                Log.d(TAG, "All permissions granted - starting full sync")
                ContactSmsSyncService.startSync(context, ContactSmsSyncService.SyncType.ALL)
            }
            hasContactsPermission -> {
                // Only contacts permission - sync contacts only
                Log.d(TAG, "Contacts permission granted - starting contact sync")
                ContactSmsSyncService.startSync(context, ContactSmsSyncService.SyncType.CONTACTS)
            }
            hasSmsPermission -> {
                // Only SMS permission - sync SMS only
                Log.d(TAG, "SMS permission granted - starting SMS sync")
                ContactSmsSyncService.startSync(context, ContactSmsSyncService.SyncType.SMS)
            }
            else -> {
                // No permissions - can't sync
                Log.d(TAG, "Permissions not granted - cannot sync")
            }
        }
    }
    
    /**
     * Check if all required permissions are granted
     */
    fun hasAllPermissions(context: Context): Boolean {
        val hasContacts = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        
        val hasSms = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
        
        return hasContacts && hasSms
    }
    
    /**
     * Check if contacts permission is granted
     */
    fun hasContactsPermission(context: Context): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Check if SMS permission is granted
     */
    fun hasSmsPermission(context: Context): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }
}

