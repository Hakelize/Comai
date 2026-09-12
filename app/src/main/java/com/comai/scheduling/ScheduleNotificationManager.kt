package com.comai.scheduling

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.comai.MainActivity

object ScheduleNotificationManager {

    const val CHANNEL_ID = "comai_personal_schedule_reminders"
    private const val CHANNEL_NAME = "Comai Reminders"
    private const val CHANNEL_DESC = "Notifications for personal schedules and daily plans"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showPlanReminder(context: Context, planId: String, title: String, time: String) {
        ensureChannel(context)

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

        val pendingIntent = PendingIntent.getActivity(
            context,
            planId.hashCode(),
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

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Comai Reminder")
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setColor(0xFF00E5FF.toInt())
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(planId.hashCode(), notification)
        } catch (e: SecurityException) {
            // Permission denied or revoked
        }
    }
}
