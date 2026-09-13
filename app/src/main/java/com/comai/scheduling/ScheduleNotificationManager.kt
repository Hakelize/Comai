package com.comai.scheduling

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.comai.MainActivity
import com.comai.R
import com.comai.data.models.ReminderType

object ScheduleNotificationManager {

    const val CHANNEL_ID = "comai_personal_schedule_reminders"
    const val ALARM_CHANNEL_ID = "comai_personal_schedule_alarms"
    private const val CHANNEL_NAME = "Comai Reminders"
    private const val CHANNEL_DESC = "Notifications for personal schedules and daily plans"
    private const val ALARM_CHANNEL_NAME = "Comai Alarms"
    private const val ALARM_CHANNEL_DESC = "Strong alarm-style alerts for scheduled plans"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            // 1. Standard Notification Channel
            val standardChannel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = CHANNEL_DESC
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(standardChannel)

            // 2. High-urgency Alarm Channel
            val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            val alarmChannel = NotificationChannel(
                ALARM_CHANNEL_ID,
                ALARM_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = ALARM_CHANNEL_DESC
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 1000)
                setSound(alarmSound, audioAttributes)
                enableLights(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(alarmChannel)
        }
    }

    fun showPlanReminder(
        context: Context,
        planId: String,
        title: String,
        time: String,
        reminderType: ReminderType = ReminderType.NOTIFICATION
    ) {
        ensureChannels(context)

        // Android 13+ POST_NOTIFICATIONS permission check
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!permissionGranted) {
                return
            }
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("extra_opened_from_reminder", true)
            putExtra("extra_plan_id", planId)
        }

        val requestCode = planId.hashCode() and 0x7FFFFFFF
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cleanTitle = title.trim()
        val bodyText = when {
            cleanTitle.startsWith("drink", ignoreCase = true) ||
            cleanTitle.startsWith("take", ignoreCase = true) -> "Time to ${cleanTitle.lowercase()}."
            cleanTitle.startsWith("call", ignoreCase = true) -> "It's time to $cleanTitle."
            cleanTitle.startsWith("go", ignoreCase = true) -> "Time to $cleanTitle."
            cleanTitle.startsWith("study", ignoreCase = true) -> "Time to study."
            else -> "It's time for $cleanTitle."
        }

        val isAlarm = reminderType == ReminderType.ALARM
        val channelToUse = if (isAlarm) ALARM_CHANNEL_ID else CHANNEL_ID

        val notificationBuilder = NotificationCompat.Builder(context, channelToUse)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(if (isAlarm) "Alarm: $cleanTitle" else "COMAI Reminder: $cleanTitle")
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setAutoCancel(true)
            .setColor(if (isAlarm) 0xFFFF5252.toInt() else 0xFF00E5FF.toInt())
            .setContentIntent(pendingIntent)

        // Dismiss action button
        val dismissIntent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
            action = ScheduleAlarmManager.ACTION_DISMISS_ALARM
            putExtra(ScheduleAlarmManager.EXTRA_PLAN_ID, planId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            ("dismiss_$planId").hashCode() and 0x7FFFFFFF,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val dismissLabel = try { context.getString(R.string.alarm_dismiss) } catch (_: Exception) { "Dismiss" }
        notificationBuilder.addAction(
            android.R.drawable.ic_menu_close_clear_cancel,
            dismissLabel,
            dismissPendingIntent
        )

        if (isAlarm) {
            // Snooze action button
            val snoozeIntent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
                action = ScheduleAlarmManager.ACTION_SNOOZE_ALARM
                putExtra(ScheduleAlarmManager.EXTRA_PLAN_ID, planId)
                putExtra(ScheduleAlarmManager.EXTRA_PLAN_TITLE, title)
                putExtra(ScheduleAlarmManager.EXTRA_REMINDER_TYPE, reminderType.name)
            }
            val snoozePendingIntent = PendingIntent.getBroadcast(
                context,
                ("snooze_$planId").hashCode() and 0x7FFFFFFF,
                snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val snoozeLabel = try { context.getString(R.string.alarm_snooze) } catch (_: Exception) { "Snooze (10m)" }
            notificationBuilder.addAction(
                android.R.drawable.ic_popup_reminder,
                snoozeLabel,
                snoozePendingIntent
            )

            val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            notificationBuilder
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setSound(alarmSound)
                .setVibrate(longArrayOf(0, 500, 200, 500, 200, 1000))
                .setFullScreenIntent(pendingIntent, true)
        } else {
            notificationBuilder
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
        }

        try {
            NotificationManagerCompat.from(context).notify(requestCode, notificationBuilder.build())
        } catch (_: SecurityException) {
            // Permission denied or revoked
        }
    }

    fun cancelNotification(context: Context, planId: String) {
        try {
            val requestCode = planId.hashCode() and 0x7FFFFFFF
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.cancel(requestCode)
        } catch (_: Exception) {}
    }
}
