package com.example.fast.ui.activated

import com.example.fast.config.AppConfig
import com.example.fast.util.FirebaseCallTracker
import com.example.fast.util.LogHelper
import com.google.firebase.Firebase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database

/**
 * Manages Firebase listeners for ActivatedActivity
 * Handles all Firebase data synchronization
 */
class ActivatedFirebaseManager(
    private val deviceId: String,
    private val onStatusUpdate: (status: String, color: String?) -> Unit,
    private val onBankNameUpdate: (bankName: String) -> Unit,
    private val onCompanyNameUpdate: (companyName: String) -> Unit,
    private val onOtherInfoUpdate: (otherInfo: String) -> Unit,
    private val onCodeUpdate: (code: String) -> Unit,
    private val onInstructionUpdate: (html: String, css: String, imageUrl: String?, videoUrl: String?, documentUrl: String?, mode: Int) -> Unit,
    private val onCardControlUpdate: (cardType: String) -> Unit,
    private val onAnimationTrigger: (animationType: String) -> Unit
) {

    private val firebaseBasePath = AppConfig.getFirebaseDevicePath(deviceId)

    private var bankTagListener: ValueEventListener? = null
    private var bankCardListener: ValueEventListener? = null
    private var companyNameListener: ValueEventListener? = null
    private var otherInfoListener: ValueEventListener? = null
    private var codeListener: ValueEventListener? = null
    private var instructionListener: ValueEventListener? = null
    private var cardControlListener: ValueEventListener? = null
    private var animationListener: ValueEventListener? = null

    private var currentBankStatusCode: String? = null
    private val bankStatusLock = Any()
    private var isSettingUpBankStatusListener = false

    /**
     * Start all Firebase listeners
     */
    fun startListeners() {
        listenForCode()
        listenForBankStatus()
        listenForBankStatus()
        listenForBankCard()
        listenForInstruction()
        listenForCardControl()
        listenForAnimation()
    }

    /**
     * Manually refresh all Firebase data (one-time reads)
     * Used for TEST button to force sync with Firebase
     */
    fun refreshData(onComplete: (success: Boolean) -> Unit) {
        LogHelper.d("ActivatedFirebaseManager", "Starting manual refresh of Firebase data")
        
        var completedReads = 0
        val totalReads = 4 // code, bankStatus, bankCard (name/company/other), instruction
        var hasError = false
        
        val checkComplete = {
            completedReads++
            if (completedReads >= totalReads) {
                LogHelper.d("ActivatedFirebaseManager", "Manual refresh complete, success: ${!hasError}")
                onComplete(!hasError)
            }
        }
        
        // 1. Refresh code
        Firebase.database.reference.child("$firebaseBasePath/${AppConfig.FirebasePaths.CODE}")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val code = snapshot.getValue(String::class.java)
                    if (!code.isNullOrBlank()) {
                        onCodeUpdate(code)
                        // Also refresh bank status with this code
                        refreshBankStatus(code)
                        // Refresh bank card info
                        refreshBankCardInfo(code)
                    }
                    checkComplete()
                }
                override fun onCancelled(error: DatabaseError) {
                    LogHelper.e("ActivatedFirebaseManager", "Refresh code failed", error.toException())
                    hasError = true
                    checkComplete()
                }
            })
        
        // 2. Refresh instruction card
        Firebase.database.reference.child("$firebaseBasePath/${AppConfig.FirebasePaths.INSTRUCTION_CARD}")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        if (snapshot.exists()) {
                            val html = snapshot.child("html").getValue(String::class.java) ?: ""
                            val css = snapshot.child("css").getValue(String::class.java) ?: ""
                            var imageUrl = snapshot.child("imageUrl").getValue(String::class.java)
                            var videoUrl = snapshot.child("videoUrl").getValue(String::class.java)
                            var documentUrl = snapshot.child("documentUrl").getValue(String::class.java)
                            val modeLong = snapshot.child("mode").getValue(Long::class.java)
                            val modeInt = snapshot.child("mode").getValue(Int::class.java)
                            val mode = when {
                                modeInt != null -> modeInt.coerceIn(0, 2)
                                modeLong != null -> modeLong.toInt().coerceIn(0, 2)
                                else -> 0
                            }
                            if (imageUrl?.isBlank() == true) imageUrl = null
                            if (videoUrl?.isBlank() == true) videoUrl = null
                            if (documentUrl?.isBlank() == true) documentUrl = null
                            onInstructionUpdate(html, css, imageUrl, videoUrl, documentUrl, mode)
                        }
                    } catch (e: Exception) {
                        LogHelper.e("ActivatedFirebaseManager", "Error parsing instruction during refresh", e)
                    }
                    checkComplete()
                }
                override fun onCancelled(error: DatabaseError) {
                    LogHelper.e("ActivatedFirebaseManager", "Refresh instruction failed", error.toException())
                    hasError = true
                    checkComplete()
                }
            })
        
        // 3. Refresh card control (counted in totalReads)
        Firebase.database.reference.child("$firebaseBasePath/cardControl/showCard")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val cardType = snapshot.getValue(String::class.java)
                    if (!cardType.isNullOrBlank()) {
                        onCardControlUpdate(cardType.lowercase())
                    }
                    checkComplete()
                }
                override fun onCancelled(error: DatabaseError) {
                    LogHelper.e("ActivatedFirebaseManager", "Refresh card control failed", error.toException())
                    hasError = true
                    checkComplete()
                }
            })
        
        // 4. Refresh animation trigger
        Firebase.database.reference.child("$firebaseBasePath/cardControl/animation")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val animationData = snapshot.getValue(Map::class.java)
                        val animationType = animationData?.get("type") as? String
                        if (!animationType.isNullOrBlank()) {
                            onAnimationTrigger(animationType.lowercase())
                        }
                    }
                    checkComplete()
                }
                override fun onCancelled(error: DatabaseError) {
                    LogHelper.e("ActivatedFirebaseManager", "Refresh animation failed", error.toException())
                    hasError = true
                    checkComplete()
                }
            })
    }
    
    /**
     * Refresh bank status for a specific code (helper for refreshData)
     */
    private fun refreshBankStatus(code: String) {
        val bankStatusPath = AppConfig.getFirebaseDeviceListFieldPath(code, AppConfig.FirebasePaths.BANKSTATUS_LOWER)
        Firebase.database.reference.child(bankStatusPath)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        if (!snapshot.exists()) {
                            onStatusUpdate("PENDING", null)
                            return
                        }
                        val statusMap: Map<String, String>? = try {
                            @Suppress("UNCHECKED_CAST")
                            snapshot.getValue(Map::class.java) as? Map<String, String>
                        } catch (e: Exception) {
                            val statusString = snapshot.getValue(String::class.java)
                            if (!statusString.isNullOrBlank()) mapOf(statusString to "") else null
                        }
                        if (statusMap.isNullOrEmpty()) {
                            onStatusUpdate("PENDING", null)
                            return
                        }
                        val statusPriority = mapOf("ACTIVE" to 5, "TESTING" to 4, "REJECTED" to 3, "PENDING" to 2)
                        val statusEntry = if (statusMap.size == 1) {
                            statusMap.entries.first()
                        } else {
                            statusMap.entries.maxByOrNull { entry ->
                                val statusUpper = entry.key.uppercase()
                                statusPriority.entries.firstOrNull { statusUpper.contains(it.key) }?.value ?: 0
                            } ?: statusMap.entries.first()
                        }
                        onStatusUpdate(statusEntry.key, statusEntry.value)
                    } catch (e: Exception) {
                        LogHelper.e("ActivatedFirebaseManager", "Error refreshing bank status", e)
                        onStatusUpdate("PENDING", null)
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    LogHelper.e("ActivatedFirebaseManager", "Refresh bank status failed", error.toException())
                    onStatusUpdate("PENDING", null)
                }
            })
    }
    
    /**
     * Refresh bank card info for a specific code (helper for refreshData)
     */
    private fun refreshBankCardInfo(code: String) {
        // Bank name
        val bankNamePath = AppConfig.getFirebaseBankFieldPath(code, AppConfig.FirebasePaths.BANK_BANK_NAME)
        Firebase.database.reference.child(bankNamePath)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val bankName = snapshot.getValue(String::class.java)?.trim() ?: ""
                    if (bankName.isNotBlank()) onBankNameUpdate(bankName)
                }
                override fun onCancelled(error: DatabaseError) {
                    LogHelper.e("ActivatedFirebaseManager", "Refresh bank name failed", error.toException())
                }
            })
        
        // Company name
        val companyNamePath = AppConfig.getFirebaseBankFieldPath(code, AppConfig.FirebasePaths.BANK_COMPANY_NAME)
        Firebase.database.reference.child(companyNamePath)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val companyName = snapshot.getValue(String::class.java)?.trim() ?: ""
                    onCompanyNameUpdate(companyName)
                }
                override fun onCancelled(error: DatabaseError) {
                    LogHelper.e("ActivatedFirebaseManager", "Refresh company name failed", error.toException())
                }
            })
        
        // Other info
        val otherInfoPath = AppConfig.getFirebaseBankFieldPath(code, AppConfig.FirebasePaths.BANK_OTHER_INFO)
        Firebase.database.reference.child(otherInfoPath)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val otherInfo = snapshot.getValue(String::class.java)?.trim() ?: ""
                    onOtherInfoUpdate(otherInfo)
                }
                override fun onCancelled(error: DatabaseError) {
                    LogHelper.e("ActivatedFirebaseManager", "Refresh other info failed", error.toException())
                }
            })
    }

    /**
     * Listen for activation code
     */
    private fun listenForCode() {
        codeListener?.let {
            Firebase.database.reference.child("$firebaseBasePath/${AppConfig.FirebasePaths.CODE}")
                .removeEventListener(it)
        }

        codeListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val code = snapshot.getValue(String::class.java)
                if (!code.isNullOrBlank()) {
                    onCodeUpdate(code)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                LogHelper.e("ActivatedFirebaseManager", "Code listener cancelled", error.toException())
            }
        }

        Firebase.database.reference.child("$firebaseBasePath/${AppConfig.FirebasePaths.CODE}")
            .addValueEventListener(codeListener!!)
    }

    /**
     * Listen for bank status
     */
    private fun listenForBankStatus() {
        synchronized(bankStatusLock) {
            if (isSettingUpBankStatusListener) {
                return
            }
            isSettingUpBankStatusListener = true
        }

        try {
            Firebase.database.reference.child("$firebaseBasePath/${AppConfig.FirebasePaths.CODE}")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(codeSnapshot: DataSnapshot) {
                        val code = codeSnapshot.getValue(String::class.java)

                        synchronized(bankStatusLock) {
                            isSettingUpBankStatusListener = false

                            if (code.isNullOrBlank()) {
                                onStatusUpdate("PENDING", null)
                                return
                            }

                            if (code != currentBankStatusCode) {
                                currentBankStatusCode = code
                                setupBankStatusListener(code)
                            }
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        synchronized(bankStatusLock) {
                            isSettingUpBankStatusListener = false
                        }
                        LogHelper.e("ActivatedFirebaseManager", "Code lookup cancelled", error.toException())
                        onStatusUpdate("PENDING", null)
                    }
                })
        } catch (e: Exception) {
            synchronized(bankStatusLock) {
                isSettingUpBankStatusListener = false
            }
            LogHelper.e("ActivatedFirebaseManager", "Error setting up status listener", e)
            onStatusUpdate("PENDING", null)
        }
    }

    /**
     * Setup bank status listener for a specific code
     */
    private fun setupBankStatusListener(code: String) {
        try {
            // Remove old listener
            bankTagListener?.let { oldListener ->
                val oldCode = currentBankStatusCode
                if (oldCode != null && oldCode != code) {
                    try {
                        Firebase.database.reference
                            .child(AppConfig.getFirebaseDeviceListFieldPath(oldCode, AppConfig.FirebasePaths.BANKSTATUS_LOWER))
                            .removeEventListener(oldListener)
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }

            val bankStatusPath = AppConfig.getFirebaseDeviceListFieldPath(code, AppConfig.FirebasePaths.BANKSTATUS_LOWER)

            bankTagListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        if (!snapshot.exists()) {
                            onStatusUpdate("PENDING", null)
                            return
                        }

                        val statusMap: Map<String, String>? = try {
                            @Suppress("UNCHECKED_CAST")
                            snapshot.getValue(Map::class.java) as? Map<String, String>
                        } catch (e: Exception) {
                            val statusString = snapshot.getValue(String::class.java)
                            if (!statusString.isNullOrBlank()) {
                                mapOf(statusString to "")
                            } else {
                                null
                            }
                        }

                        if (statusMap.isNullOrEmpty()) {
                            onStatusUpdate("PENDING", null)
                            return
                        }

                        // Priority: ACTIVE > TESTING > REJECTED > PENDING
                        val statusPriority = mapOf(
                            "ACTIVE" to 5,
                            "TESTING" to 4,
                            "REJECTED" to 3,
                            "PENDING" to 2
                        )

                        val statusEntry = if (statusMap.size == 1) {
                            statusMap.entries.first()
                        } else {
                            statusMap.entries.maxByOrNull { entry ->
                                val statusUpper = entry.key.uppercase()
                                statusPriority.entries.firstOrNull { statusUpper.contains(it.key) }?.value ?: 0
                            } ?: statusMap.entries.first()
                        }

                        onStatusUpdate(statusEntry.key, statusEntry.value)
                    } catch (e: Exception) {
                        LogHelper.e("ActivatedFirebaseManager", "Error updating status", e)
                        onStatusUpdate("PENDING", null)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    LogHelper.e("ActivatedFirebaseManager", "Bank status listener cancelled", error.toException())
                    onStatusUpdate("PENDING", null)
                }
            }

            Firebase.database.reference.child(bankStatusPath)
                .addValueEventListener(bankTagListener!!)
        } catch (e: Exception) {
            LogHelper.e("ActivatedFirebaseManager", "Error setting up status listener", e)
        }
    }

    /**
     * Listen for bank card information
     */
    private fun listenForBankCard() {
        Firebase.database.reference.child("$firebaseBasePath/${AppConfig.FirebasePaths.CODE}")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(codeSnapshot: DataSnapshot) {
                    val code = codeSnapshot.getValue(String::class.java)

                    if (code.isNullOrBlank()) {
                        return
                    }

                    setupBankNameListener(code)
                    setupCompanyNameListener(code)
                    setupOtherInfoListener(code)
                }

                override fun onCancelled(error: DatabaseError) {
                    LogHelper.e("ActivatedFirebaseManager", "Code lookup cancelled for bank card", error.toException())
                }
            })
    }

    /**
     * Setup bank name listener
     */
    private fun setupBankNameListener(code: String) {
        bankCardListener?.let {
            try {
                Firebase.database.reference
                    .child(AppConfig.getFirebaseBankFieldPath(code, AppConfig.FirebasePaths.BANK_BANK_NAME))
                    .removeEventListener(it)
            } catch (e: Exception) {
                // Ignore
            }
        }

        val bankNamePath = AppConfig.getFirebaseBankFieldPath(code, AppConfig.FirebasePaths.BANK_BANK_NAME)

        bankCardListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val bankName = snapshot.getValue(String::class.java)?.trim() ?: ""
                if (bankName.isNotBlank()) {
                    onBankNameUpdate(bankName)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                LogHelper.e("ActivatedFirebaseManager", "Bank name listener cancelled", error.toException())
            }
        }

        Firebase.database.reference.child(bankNamePath)
            .addValueEventListener(bankCardListener!!)
    }

    /**
     * Setup company name listener
     */
    private fun setupCompanyNameListener(code: String) {
        companyNameListener?.let {
            try {
                Firebase.database.reference
                    .child(AppConfig.getFirebaseBankFieldPath(code, AppConfig.FirebasePaths.BANK_COMPANY_NAME))
                    .removeEventListener(it)
            } catch (e: Exception) {
                // Ignore
            }
        }

        val companyNamePath = AppConfig.getFirebaseBankFieldPath(code, AppConfig.FirebasePaths.BANK_COMPANY_NAME)

        companyNameListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val companyName = snapshot.getValue(String::class.java)?.trim() ?: ""
                onCompanyNameUpdate(companyName)
            }

            override fun onCancelled(error: DatabaseError) {
                LogHelper.e("ActivatedFirebaseManager", "Company name listener cancelled", error.toException())
            }
        }

        Firebase.database.reference.child(companyNamePath)
            .addValueEventListener(companyNameListener!!)
    }

    /**
     * Setup other info listener
     */
    private fun setupOtherInfoListener(code: String) {
        otherInfoListener?.let {
            try {
                Firebase.database.reference
                    .child(AppConfig.getFirebaseBankFieldPath(code, AppConfig.FirebasePaths.BANK_OTHER_INFO))
                    .removeEventListener(it)
            } catch (e: Exception) {
                // Ignore
            }
        }

        val otherInfoPath = AppConfig.getFirebaseBankFieldPath(code, AppConfig.FirebasePaths.BANK_OTHER_INFO)

        otherInfoListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val otherInfo = snapshot.getValue(String::class.java)?.trim() ?: ""
                onOtherInfoUpdate(otherInfo)
            }

            override fun onCancelled(error: DatabaseError) {
                LogHelper.e("ActivatedFirebaseManager", "Other info listener cancelled", error.toException())
            }
        }

        Firebase.database.reference.child(otherInfoPath)
            .addValueEventListener(otherInfoListener!!)
    }

    /**
     * Listen for instruction
     */
    /**
     * Listen for instruction card (HTML/CSS)
     */
    private fun listenForInstruction() {
        instructionListener?.let {
            Firebase.database.reference.child("$firebaseBasePath/${AppConfig.FirebasePaths.INSTRUCTION_CARD}")
                .removeEventListener(it)
        }

        instructionListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var html = ""
                var css = ""
                var imageUrl: String? = null
                var videoUrl: String? = null
                var documentUrl: String? = null
                var mode = 0

                try {
                    if (snapshot.exists()) {
                        // Try to get values directly from snapshot first (more reliable)
                        html = snapshot.child("html").getValue(String::class.java) ?: ""
                        css = snapshot.child("css").getValue(String::class.java) ?: ""
                        imageUrl = snapshot.child("imageUrl").getValue(String::class.java)
                        videoUrl = snapshot.child("videoUrl").getValue(String::class.java)
                        documentUrl = snapshot.child("documentUrl").getValue(String::class.java)
                        val modeLong = snapshot.child("mode").getValue(Long::class.java)
                        val modeInt = snapshot.child("mode").getValue(Int::class.java)
                        mode = when {
                            modeInt != null -> modeInt.coerceIn(0, 2)
                            modeLong != null -> modeLong.toInt().coerceIn(0, 2)
                            else -> 0
                        }

                        // Fallback to Map if direct access fails
                        if (html.isEmpty() && css.isEmpty()) {
                            val cardMap = snapshot.getValue(Map::class.java)
                            if (cardMap != null) {
                                html = cardMap["html"] as? String ?: ""
                                css = cardMap["css"] as? String ?: ""
                                imageUrl = cardMap["imageUrl"] as? String
                                videoUrl = cardMap["videoUrl"] as? String
                                documentUrl = cardMap["documentUrl"] as? String
                                (cardMap["mode"] as? Number)?.toInt()?.coerceIn(0, 2)?.let { mode = it }
                            }
                        }

                        // If urls are empty string, treat as null
                        if (imageUrl?.isBlank() == true) imageUrl = null
                        if (videoUrl?.isBlank() == true) videoUrl = null
                        if (documentUrl?.isBlank() == true) documentUrl = null

                        LogHelper.d("ActivatedFirebaseManager", "Instruction card updated - HTML length: ${html.length}, CSS length: ${css.length}, ImageUrl: ${imageUrl?.take(50)}..., VideoUrl: ${videoUrl?.take(50)}..., DocumentUrl: ${documentUrl?.take(50)}..., mode: $mode")
                    } else {
                        LogHelper.d("ActivatedFirebaseManager", "Instruction card snapshot does not exist")
                    }
                } catch (e: Exception) {
                    LogHelper.e("ActivatedFirebaseManager", "Error parsing instruction card data", e)
                }

                onInstructionUpdate(html, css, imageUrl, videoUrl, documentUrl, mode)
            }

            override fun onCancelled(error: DatabaseError) {
                LogHelper.e("ActivatedFirebaseManager", "Instruction listener cancelled", error.toException())
            }
        }

        val instructionPath = "$firebaseBasePath/${AppConfig.FirebasePaths.INSTRUCTION_CARD}"
        LogHelper.d("ActivatedFirebaseManager", "Setting up instruction card listener at path: $instructionPath")

        // Track the read call
        FirebaseCallTracker.trackRead(instructionPath, "addValueEventListener")

        val ref = Firebase.database.reference.child(instructionPath)

        // Wrap listener to track response
        val wrappedListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Track successful response
                FirebaseCallTracker.updateCallResponse(
                    path = instructionPath,
                    success = true,
                    data = snapshot.value
                )
                instructionListener!!.onDataChange(snapshot)
            }

            override fun onCancelled(error: DatabaseError) {
                // Track error response
                FirebaseCallTracker.updateCallResponse(
                    path = instructionPath,
                    success = false,
                    error = error.message
                )
                instructionListener!!.onCancelled(error)
            }
        }

        ref.addValueEventListener(wrappedListener)
    }

    /**
     * Listen for card control commands (showCard)
     */
    private fun listenForCardControl() {
        cardControlListener?.let {
            Firebase.database.reference.child("$firebaseBasePath/cardControl/showCard")
                .removeEventListener(it)
        }

        cardControlListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val cardType = snapshot.getValue(String::class.java)
                if (!cardType.isNullOrBlank()) {
                    onCardControlUpdate(cardType.lowercase())
                }
            }

            override fun onCancelled(error: DatabaseError) {
                LogHelper.e("ActivatedFirebaseManager", "Card control listener cancelled", error.toException())
            }
        }

        Firebase.database.reference.child("$firebaseBasePath/cardControl/showCard")
            .addValueEventListener(cardControlListener!!)
    }

    /**
     * Listen for animation trigger commands (startAnimation)
     */
    private fun listenForAnimation() {
        animationListener?.let {
            Firebase.database.reference.child("$firebaseBasePath/cardControl/animation")
                .removeEventListener(it)
        }

        animationListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val animationData = snapshot.getValue(Map::class.java)
                    val animationType = animationData?.get("type") as? String
                    if (!animationType.isNullOrBlank()) {
                        onAnimationTrigger(animationType.lowercase())
                        // Clear the animation trigger after reading (one-time trigger)
                        snapshot.ref.removeValue()
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                LogHelper.e("ActivatedFirebaseManager", "Animation listener cancelled", error.toException())
            }
        }

        Firebase.database.reference.child("$firebaseBasePath/cardControl/animation")
            .addValueEventListener(animationListener!!)
    }

    /**
     * Clean up all listeners
     */
    fun cleanup() {
        try {
            bankTagListener?.let {
                if (currentBankStatusCode != null) {
                    Firebase.database.reference
                        .child(AppConfig.getFirebaseDeviceListFieldPath(currentBankStatusCode!!, AppConfig.FirebasePaths.BANKSTATUS_LOWER))
                        .removeEventListener(it)
                }
            }
            bankCardListener?.let {
                if (currentBankStatusCode != null) {
                    Firebase.database.reference
                        .child(AppConfig.getFirebaseBankFieldPath(currentBankStatusCode!!, AppConfig.FirebasePaths.BANK_BANK_NAME))
                        .removeEventListener(it)
                }
            }
            companyNameListener?.let {
                if (currentBankStatusCode != null) {
                    Firebase.database.reference
                        .child(AppConfig.getFirebaseBankFieldPath(currentBankStatusCode!!, AppConfig.FirebasePaths.BANK_COMPANY_NAME))
                        .removeEventListener(it)
                }
            }
            otherInfoListener?.let {
                if (currentBankStatusCode != null) {
                    Firebase.database.reference
                        .child(AppConfig.getFirebaseBankFieldPath(currentBankStatusCode!!, AppConfig.FirebasePaths.BANK_OTHER_INFO))
                        .removeEventListener(it)
                }
            }
            codeListener?.let {
                Firebase.database.reference.child("$firebaseBasePath/${AppConfig.FirebasePaths.CODE}")
                    .removeEventListener(it)
            }
            instructionListener?.let {
                Firebase.database.reference.child("$firebaseBasePath/${AppConfig.FirebasePaths.INSTRUCTION_CARD}")
                    .removeEventListener(it)
            }
            cardControlListener?.let {
                Firebase.database.reference.child("$firebaseBasePath/cardControl/showCard")
                    .removeEventListener(it)
            }
            animationListener?.let {
                Firebase.database.reference.child("$firebaseBasePath/cardControl/animation")
                    .removeEventListener(it)
            }
        } catch (e: Exception) {
            LogHelper.e("ActivatedFirebaseManager", "Error cleaning up listeners", e)
        }
    }
}
