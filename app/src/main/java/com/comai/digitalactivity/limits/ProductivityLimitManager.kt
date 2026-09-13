package com.comai.digitalactivity.limits

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.comai.MainActivity
import com.comai.R
import com.comai.digitalactivity.data.AppLimitDao
import com.comai.digitalactivity.data.AppLimitEntity
import com.comai.digitalactivity.data.DigitalActivityPreferences
import com.comai.digitalactivity.model.DailyUsageSnapshot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ProductivityLimitManager: Evaluates active app limits against daily usage
 * and issues respectful, rate-limited notifications when a limit is reached.
 *
 * Principle: Monitor -> Understand -> Notify -> Let the user decide (no forcible blocking).
 */
class ProductivityLimitManager(
    private val context: Context,
    private val appLimitDao: AppLimitDao,
    private val preferences: DigitalActivityPreferences = DigitalActivityPreferences(context)
) {

    init {
        createNotificationChannel()
    }

    /**
     * Checks all enabled limits against the current daily usage snapshot.
     * Triggers notifications for any apps that reached their limit today,
     * rate-limited to once per day per app.
     */
    suspend fun checkLimitsAndNotify(snapshot: DailyUsageSnapshot) {
        if (!preferences.isMonitoringEnabled || !preferences.isNotificationsEnabled) {
            return
        }

        val limits = appLimitDao.getAllLimits().filter { it.isEnabled }
        if (limits.isEmpty()) return

        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val usageMap = snapshot.appUsageList.associate { it.packageName to it.usageMinutes }

        for (limit in limits) {
            val usageMinutes = usageMap[limit.packageName] ?: 0L
            if (usageMinutes >= limit.dailyLimitMinutes) {
                // Check if already notified today
                if (limit.lastNotifiedDate != todayDate) {
                    sendLimitNotification(limit)
                    appLimitDao.updateLastNotifiedDate(limit.packageName, todayDate)
                }
            }
        }
    }

    private fun sendLimitNotification(limit: AppLimitEntity) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                limit.packageName.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val text = "You've reached your ${limit.dailyLimitMinutes}-minute ${limit.appName} limit for today."

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Comai Limit Reminder")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setColor(ContextCompat.getColor(context, android.R.color.holo_blue_bright))
                .build()

            notificationManager.notify(NOTIFICATION_ID_OFFSET + Math.abs(limit.packageName.hashCode() % 10000), notification)
            Log.i(TAG, "Sent productivity limit reminder for ${limit.appName}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send limit notification: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "App Usage Limits",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Respectful reminders when configured daily app limits are reached."
                enableVibration(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "ProductivityLimitManager"
        const val CHANNEL_ID = "comai_app_limits"
        private const val NOTIFICATION_ID_OFFSET = 30000
    }
}
