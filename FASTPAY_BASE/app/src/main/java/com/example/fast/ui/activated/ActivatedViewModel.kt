package com.example.fast.ui.activated

import androidx.lifecycle.ViewModel
import com.example.fast.util.LogHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * ViewModel for ActivatedActivity
 * Manages state and data for the activated device screen
 */
@HiltViewModel
class ActivatedViewModel @Inject constructor() : ViewModel() {
    
    // Device information
    var activationCode: String? = null
    var devicePhoneNumber: String? = null
    var cachedAndroidId: String? = ""
    
    // Bank information
    var bankName: String = ""
    var companyName: String = ""
    var otherInfo: String = ""
    var bankStatus: String = "PENDING"
    var bankStatusColor: String? = null
    
    // UI state
    var previousStatus: String = "PENDING"
    var previousStatusColor: Int? = null
    var isTransitioningFromSplash: Boolean = false
    var shouldAnimate: Boolean = false
    
    // Permission state
    var hasAllPermissions: Boolean = false
    
    // Service state
    var isServiceRunning: Boolean = false
    
    // Message state
    var totalMessageCount: Int = 0
    var displayedMessageCount: Int = 0
    
    // Instruction card state
    var hasInstructionCard: Boolean = false
    
    override fun onCleared() {
        super.onCleared()
        LogHelper.d("ActivatedViewModel", "ViewModel cleared")
    }
}
