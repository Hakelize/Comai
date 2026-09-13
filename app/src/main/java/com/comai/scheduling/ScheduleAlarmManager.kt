package com.comai.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.comai.MainActivity
import com.comai.data.models.PersonalPlan
import com.comai.data.models.ReminderType
import com.comai.util.TimeUtils
import java.util.Calendar

class ScheduleAlarmManager(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    companion object {
        const val ACTION_REMINDER_ALARM = "com.comai.action.REMINDER_ALARM"
        const val ACTION_DISMISS_ALARM = "com.comai.action.DISMISS_ALARM"
        const val ACTION_SNOOZE_ALARM = "com.comai.action.SNOOZE_ALARM"
        const val EXTRA_PLAN_ID = "extra_plan_id"
        const val EXTRA_PLAN_TITLE = "extra_plan_title"
        const val EXTRA_PLAN_TIME = "extra_plan_time"
        const val EXTRA_PLAN_DATE = "extra_plan_date"
        const val EXTRA_PLAN_REPEAT = "extra_plan_repeat"
        const val EXTRA_REMINDER_TYPE = "extra_reminder_type"

        /**
         * Computes the next epoch trigger timestamp in milliseconds for a plan.
         * If the plan frequency is "Once" and the target date/time has already passed,
         * returns -1L (past schedules should NOT be scheduled).
         */
        fun calculateNextTriggerTime(
            timeString: String,
            repeatFrequency: String,
            nowCalendar: Calendar = Calendar.getInstance(),
            scheduledDate: String? = null
        ): Long {
            val parsedTime = TimeUtils.parseTime(timeString)
            val hourOfDay = when {
                parsedTime.isAm && parsedTime.hour == 12 -> 0
                parsedTime.isAm -> parsedTime.hour
                !parsedTime.isAm && parsedTime.hour == 12 -> 12
                else -> parsedTime.hour + 12
            }

            val target = (nowCalendar.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, hourOfDay)
                set(Calendar.MINUTE, parsedTime.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            if (!scheduledDate.isNullOrBlank()) {
                try {
                    val parts = scheduledDate.split("-")
                    if (parts.size == 3) {
                        target.set(Calendar.YEAR, parts[0].toInt())
                        target.set(Calendar.MONTH, parts[1].toInt() - 1)
                        target.set(Calendar.DAY_OF_MONTH, parts[2].toInt())
                    }
                } catch (_: Exception) {}
            }

            val isOnce = repeatFrequency.equals("Once", ignoreCase = true)

            if (isOnce) {
                // If scheduled time has already passed, do NOT schedule past alarms!
                if (target.timeInMillis <= nowCalendar.timeInMillis) {
                    return -1L
                }
                return target.timeInMillis
            }

            // If target time has already passed today, advance by at least one day
            if (target.timeInMillis <= nowCalendar.timeInMillis) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }

            // Adjust based on repeat frequency
            when {
                repeatFrequency.equals("Weekdays", ignoreCase = true) -> {
                    while (target.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
                        target.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
                    ) {
                        target.add(Calendar.DAY_OF_YEAR, 1)
                    }
                }
                repeatFrequency.equals("Weekends", ignoreCase = true) -> {
                    while (target.get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY &&
                        target.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY
                    ) {
                        target.add(Calendar.DAY_OF_YEAR, 1)
                    }
                }
                repeatFrequency.contains("Mon", ignoreCase = true) &&
                repeatFrequency.contains("Wed", ignoreCase = true) -> {
                    while (target.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY &&
                        target.get(Calendar.DAY_OF_WEEK) != Calendar.WEDNESDAY &&
                        target.get(Calendar.DAY_OF_WEEK) != Calendar.FRIDAY
                    ) {
                        target.add(Calendar.DAY_OF_YEAR, 1)
                    }
                }
            }

            return target.timeInMillis
        }
    }

    /**
     * Schedules a personal plan reminder alarm or notification.
     * Prevents scheduling if the plan is disabled or already in the past.
     */
    fun schedulePlan(plan: PersonalPlan) {
        if (!plan.isEnabled || alarmManager == null) return

        val triggerAtMs = calculateNextTriggerTime(
            timeString = plan.time,
            repeatFrequency = plan.repeatFrequency,
            scheduledDate = plan.date
        )

        // Do not schedule past alarms
        if (triggerAtMs <= 0L || triggerAtMs <= System.currentTimeMillis()) {
            return
        }

        val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
            action = ACTION_REMINDER_ALARM
            putExtra(EXTRA_PLAN_ID, plan.id)
            putExtra(EXTRA_PLAN_TITLE, plan.title)
            putExtra(EXTRA_PLAN_TIME, plan.time)
            putExtra(EXTRA_PLAN_DATE, plan.date)
            putExtra(EXTRA_PLAN_REPEAT, plan.repeatFrequency)
            putExtra(EXTRA_REMINDER_TYPE, plan.reminderType.name)
        }

        val requestCode = plan.id.hashCode() and 0x7FFFFFFF
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val canExact = AlarmPermissionUtils.canScheduleExactAlarms(context)

        try {
            if (plan.reminderType == ReminderType.ALARM) {
                // Show intent when user clicks clock info
                val showIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("extra_opened_from_reminder", true)
                    putExtra("extra_plan_id", plan.id)
                }
                val showPendingIntent = PendingIntent.getActivity(
                    context,
                    requestCode,
                    showIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                if (canExact) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        alarmManager.setAlarmClock(
                            AlarmManager.AlarmClockInfo(triggerAtMs, showPendingIntent),
                            pendingIntent
                        )
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerAtMs,
                            pendingIntent
                        )
                    } else {
                        alarmManager.setExact(
                            AlarmManager.RTC_WAKEUP,
                            triggerAtMs,
                            pendingIntent
                        )
                    }
                } else {
                    // Fallback to inexact idle wakeup without exact permission
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerAtMs,
                            pendingIntent
                        )
                    } else {
                        alarmManager.set(
                            AlarmManager.RTC_WAKEUP,
                            triggerAtMs,
                            pendingIntent
                        )
                    }
                }
            } else {
                // ReminderType.NOTIFICATION
                if (canExact) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerAtMs,
                            pendingIntent
                        )
                    } else {
                        alarmManager.setExact(
                            AlarmManager.RTC_WAKEUP,
                            triggerAtMs,
                            pendingIntent
                        )
                    }
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerAtMs,
                            pendingIntent
                        )
                    } else {
                        alarmManager.set(
                            AlarmManager.RTC_WAKEUP,
                            triggerAtMs,
                            pendingIntent
                        )
                    }
                }
            }
        } catch (_: SecurityException) {
            // Security fallback if exact alarm permission revoked at runtime
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMs,
                        pendingIntent
                    )
                }
            } catch (_: Exception) {}
        } catch (_: Exception) {}
    }

    /**
     * Cancels any scheduled alarm for the given plan.
     */
    fun cancelPlan(planId: String) {
        if (alarmManager == null) return

        val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
            action = ACTION_REMINDER_ALARM
        }

        val requestCode = planId.hashCode() and 0x7FFFFFFF
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }

        cancelSnooze(planId)
    }

    /**
     * Schedules a Snooze alarm for the specified number of minutes (defaults to 10 minutes).
     */
    fun scheduleSnooze(
        planId: String,
        title: String,
        reminderType: ReminderType,
        minutes: Int = 10
    ) {
        if (alarmManager == null) return

        val triggerAtMs = System.currentTimeMillis() + (minutes * 60 * 1000L)

        val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
            action = ACTION_REMINDER_ALARM
            putExtra(EXTRA_PLAN_ID, planId)
            putExtra(EXTRA_PLAN_TITLE, title)
            putExtra(EXTRA_REMINDER_TYPE, reminderType.name)
            putExtra(EXTRA_PLAN_REPEAT, "Once")
        }

        val requestCode = ("snooze_$planId").hashCode() and 0x7FFFFFFF
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (AlarmPermissionUtils.canScheduleExactAlarms(context)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMs,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMs,
                        pendingIntent
                    )
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMs,
                        pendingIntent
                    )
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Cancels any active Snooze alarm for the plan.
     */
    fun cancelSnooze(planId: String) {
        if (alarmManager == null) return

        val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
            action = ACTION_REMINDER_ALARM
        }

        val requestCode = ("snooze_$planId").hashCode() and 0x7FFFFFFF
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    /**
     * Reschedules all enabled plans in batch.
     */
    fun rescheduleAll(plans: List<PersonalPlan>) {
        plans.forEach { plan ->
            if (plan.isEnabled) {
                schedulePlan(plan)
            } else {
                cancelPlan(plan.id)
            }
        }
    }
}
