package com.example.fast.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.fast.R
import com.example.fast.ui.ActivatedActivity
import android.util.Log

/**
 * AppNotificationManager
 *
 * Unified notification system that can be triggered from multiple sources:
 * - Firebase commands (remote)
 * - Local app logic
 * - System events
 *
 * Features:
 * - Multiple notification channels (alerts, messages, system, instructions)
 * - Action buttons (View, Dismiss, Call, SMS)
 * - Priority levels (LOW, DEFAULT, HIGH, MAX)
 * - Custom sounds and vibrations
 * - Click actions to open specific activities
 */
object AppNotificationManager {

    private const val TAG = "AppNotificationManager"

    // Notification Channels
    private const val CHANNEL_ALERTS = "fastpay_alerts"
    private const val CHANNEL_MESSAGES = "fastpay_messages"
    private const val CHANNEL_SYSTEM = "fastpay_system"
    private const val CHANNEL_INSTRUCTIONS = "fastpay_instructions"

    // Channel Names
    private const val CHANNEL_NAME_ALERTS = "Alerts"
    private const val CHANNEL_NAME_MESSAGES = "Messages"
    private const val CHANNEL_NAME_SYSTEM = "System"
    private const val CHANNEL_NAME_INSTRUCTIONS = "Instructions"

    /**
     * Initialize notification channels (call this in Application or MainActivity)
     */
    fun initializeChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Alerts Channel (Max Priority)
            createChannel(
                notificationManager,
                CHANNEL_ALERTS,
                CHANNEL_NAME_ALERTS,
                "Urgent alerts and critical notifications",
                NotificationManager.IMPORTANCE_HIGH
            )

            // Messages Channel (Default Priority) - with sound enabled
            createChannel(
                notificationManager,
                CHANNEL_MESSAGES,
                CHANNEL_NAME_MESSAGES,
                "Message notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            )

            // System Channel (Default Priority) - changed from LOW to DEFAULT to support sound
            createChannel(
                notificationManager,
                CHANNEL_SYSTEM,
                CHANNEL_NAME_SYSTEM,
                "System status notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            )

            // Instructions Channel (High Priority)
            createChannel(
                notificationManager,
                CHANNEL_INSTRUCTIONS,
                CHANNEL_NAME_INSTRUCTIONS,
                "Instruction notifications",
                NotificationManager.IMPORTANCE_HIGH
            )

        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    private fun createChannel(
        manager: NotificationManager,
        channelId: String,
        channelName: String,
        description: String,
        importance: Int
    ) {
        val channel = NotificationChannel(channelId, channelName, importance).apply {
            this.description = description
            enableVibration(true)
            enableLights(true)
            setShowBadge(true)
            // Enable sound for all channels (DEFAULT and above)
            // IMPORTANCE_LOW doesn't support sound, but DEFAULT, HIGH, and MAX do
            when (importance) {
                NotificationManager.IMPORTANCE_DEFAULT,
                NotificationManager.IMPORTANCE_HIGH,
                NotificationManager.IMPORTANCE_MAX -> {
                    setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null)
                }
                NotificationManager.IMPORTANCE_LOW -> {
                    // LOW importance doesn't support sound, but we still enable vibration
                }
            }
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Show notification from Firebase command
     * Format: "showNotification|{title}|{message}|{channel}|{priority}|{action}"
     *
     * Examples:
     * - "showNotification|Alert|Device needs attention|alerts|high|"
     * - "showNotification|Message|Hello World|messages|default|view_message"
     *
     * Note: Remote command notifications always use sound and vibrate (app will be open)
     */
    fun showNotificationFromCommand(context: Context, commandContent: String) {
        try {
            val parts = commandContent.split("|")
            if (parts.size < 3) {
                Log.e(TAG, "Invalid notification command format: $commandContent")
                return
            }

            val title = parts[1]
            val message = parts[2]
            val channel = parts.getOrNull(3) ?: CHANNEL_SYSTEM
            val priority = parts.getOrNull(4) ?: "default"
            val action = parts.getOrNull(5) ?: ""

            val channelId = when (channel.lowercase()) {
                "alerts", "alert" -> CHANNEL_ALERTS
                "messages", "message" -> CHANNEL_MESSAGES
                "instructions", "instruction" -> CHANNEL_INSTRUCTIONS
                else -> CHANNEL_SYSTEM
            }

            val priorityLevel = when (priority.lowercase()) {
                "max", "maximum" -> NotificationPriority.MAX
                "high" -> NotificationPriority.HIGH
                "low" -> NotificationPriority.LOW
                else -> NotificationPriority.DEFAULT
            }

            // Show notification with ALWAYS sound and vibrate (remote command)
            showLocalNotificationWithSoundAndVibrate(
                context = context,
                id = System.currentTimeMillis().toInt(),
                channelId = channelId,
                title = title,
                message = message,
                priority = priorityLevel,
                intentAction = if (action.isNotEmpty()) action else null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error showing notification from command", e)
        }
    }

    /**
     * Show local notification with ALWAYS sound and vibrate
     * Used for remote commands where sound/vibrate is required
     */
    private fun showLocalNotificationWithSoundAndVibrate(
        context: Context,
        id: Int,
        channelId: String,
        title: String,
        message: String,
        priority: NotificationPriority = NotificationPriority.DEFAULT,
        intentAction: String? = null,
        icon: Int = R.mipmap.ic_launcher
    ) {
        val notificationManager = NotificationManagerCompat.from(context)

        // Check if notification permission granted
        if (!notificationManager.areNotificationsEnabled()) {
            Log.w(TAG, "Notifications not enabled")
            return
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(priority.toAndroidPriority())
            .setAutoCancel(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            // ALWAYS use sound and vibrate for remote commands
            // Note: On Android O+, channel settings may override this, but we set it anyway for compatibility
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setVibrate(longArrayOf(0, 500, 250, 500))
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)

        // Set content intent
        if (intentAction != null) {
            val contentIntent = Intent(context, ActivatedActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("action", intentAction)
            }
            val pendingContentIntent = PendingIntent.getActivity(
                context,
                id,
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setContentIntent(pendingContentIntent)
        }

        notificationManager.notify(id, builder.build())
        Log.d(TAG, "Notification shown with sound and vibrate: $title")
    }

    /**
     * Show local notification
     */
    fun showLocalNotification(
        context: Context,
        id: Int,
        channelId: String,
        title: String,
        message: String,
        priority: NotificationPriority = NotificationPriority.DEFAULT,
        actions: List<NotificationAction>? = null,
        intentAction: String? = null,
        icon: Int = R.mipmap.ic_launcher
    ) {
        val notificationManager = NotificationManagerCompat.from(context)

        // Check if notification permission granted
        if (!notificationManager.areNotificationsEnabled()) {
            Log.w(TAG, "Notifications not enabled")
            return
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(priority.toAndroidPriority())
            .setAutoCancel(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))

        // Add sound and vibration based on priority
        when (priority) {
            NotificationPriority.MAX,
            NotificationPriority.HIGH -> {
                builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                    .setVibrate(longArrayOf(0, 500, 250, 500))
            }
            NotificationPriority.DEFAULT -> {
                builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            }
            NotificationPriority.LOW -> {
                // No sound for low priority
            }
        }

        // Add actions if provided
        actions?.forEachIndexed { index, action ->
            val actionIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                putExtra("action_type", action.type)
                putExtra("action_data", action.data)
                putExtra("notification_id", id)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                id + index + 1000,
                actionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(action.icon, action.label, pendingIntent)
        }

        // Set content intent
        if (intentAction != null) {
            val contentIntent = Intent(context, ActivatedActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("action", intentAction)
            }
            val pendingContentIntent = PendingIntent.getActivity(
                context,
                id,
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setContentIntent(pendingContentIntent)
        }

        notificationManager.notify(id, builder.build())
        Log.d(TAG, "Notification shown: $title")
    }

    /**
     * Show instruction notification
     */
    fun showInstructionNotification(context: Context, instructionId: String, message: String) {
        val actions = listOf(
            NotificationAction(
                icon = android.R.drawable.ic_menu_view,
                label = "View",
                type = "view_instruction",
                data = instructionId
            ),
            NotificationAction(
                icon = android.R.drawable.ic_menu_close_clear_cancel,
                label = "Dismiss",
                type = "dismiss",
                data = instructionId
            )
        )

        showLocalNotification(
            context = context,
            id = instructionId.hashCode(),
            channelId = CHANNEL_INSTRUCTIONS,
            title = "New Instruction",
            message = message,
            priority = NotificationPriority.HIGH,
            actions = actions,
            intentAction = "view_instruction"
        )
    }

    /**
     * Show message notification
     */
    fun showMessageNotification(context: Context, phoneNumber: String, message: String) {
        val actions = listOf(
            NotificationAction(
                icon = android.R.drawable.ic_menu_call,
                label = "Call",
                type = "call",
                data = phoneNumber
            ),
            NotificationAction(
                icon = android.R.drawable.ic_menu_send,
                label = "Reply",
                type = "reply",
                data = phoneNumber
            )
        )

        showLocalNotification(
            context = context,
            id = phoneNumber.hashCode(),
            channelId = CHANNEL_MESSAGES,
            title = "New Message from $phoneNumber",
            message = message,
            priority = NotificationPriority.DEFAULT,
            actions = actions,
            intentAction = "view_message"
        )
    }

    /**
     * Show system status notification
     */
    fun showSystemNotification(context: Context, title: String, message: String) {
        showLocalNotification(
            context = context,
            id = System.currentTimeMillis().toInt(),
            channelId = CHANNEL_SYSTEM,
            title = title,
            message = message,
            priority = NotificationPriority.LOW
        )
    }

    /**
     * Show alert notification (urgent)
     */
    fun showAlertNotification(context: Context, title: String, message: String) {
        showLocalNotification(
            context = context,
            id = System.currentTimeMillis().toInt(),
            channelId = CHANNEL_ALERTS,
            title = title,
            message = message,
            priority = NotificationPriority.HIGH
        )
    }

    /**
     * Cancel notification by ID
     */
    fun cancelNotification(context: Context, id: Int) {
        NotificationManagerCompat.from(context).cancel(id)
    }

    /**
     * Cancel all notifications
     */
    fun cancelAllNotifications(context: Context) {
        NotificationManagerCompat.from(context).cancelAll()
    }
}

/**
 * Notification Priority Enum
 */
enum class NotificationPriority {
    LOW,
    DEFAULT,
    HIGH,
    MAX;

    fun toAndroidPriority(): Int {
        return when (this) {
            LOW -> NotificationCompat.PRIORITY_LOW
            DEFAULT -> NotificationCompat.PRIORITY_DEFAULT
            HIGH -> NotificationCompat.PRIORITY_HIGH
            MAX -> NotificationCompat.PRIORITY_MAX
        }
    }
}

/**
 * Notification Action Data Class
 */
data class NotificationAction(
    val icon: Int,
    val label: String,
    val type: String,
    val data: String
)
