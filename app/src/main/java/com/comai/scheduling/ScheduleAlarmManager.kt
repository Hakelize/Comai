package com.comai.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.comai.data.models.PersonalPlan
import com.comai.util.TimeUtils
import java.util.Calendar

class ScheduleAlarmManager(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    companion object {
        const val ACTION_REMINDER_ALARM = "com.comai.action.REMINDER_ALARM"
        const val EXTRA_PLAN_ID = "extra_plan_id"
        const val EXTRA_PLAN_TITLE = "extra_plan_title"
        const val EXTRA_PLAN_TIME = "extra_plan_time"
        const val EXTRA_PLAN_REPEAT = "extra_plan_repeat"

        /**
         * Computes the next epoch trigger timestamp in milliseconds for a plan.
         */
        fun calculateNextTriggerTime(
            timeString: String,
            repeatFrequency: String,
            nowCalendar: Calendar = Calendar.getInstance()
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
     * Schedules a personal plan reminder alarm.
     */
    fun schedulePlan(plan: PersonalPlan) {
        if (!plan.isEnabled || alarmManager == null) return

        val triggerAtMs = calculateNextTriggerTime(plan.time, plan.repeatFrequency)

        val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
            action = ACTION_REMINDER_ALARM
            putExtra(EXTRA_PLAN_ID, plan.id)
            putExtra(EXTRA_PLAN_TITLE, plan.title)
            putExtra(EXTRA_PLAN_TIME, plan.time)
            putExtra(EXTRA_PLAN_REPEAT, plan.repeatFrequency)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            plan.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMs,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMs,
                        pendingIntent
                    )
                }
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
        } catch (e: SecurityException) {
            // Fallback for strict security contexts
            try {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMs,
                    pendingIntent
                )
            } catch (ignored: Exception) {}
        }
    }

    /**
     * Cancels any scheduled alarm for the given plan.
     */
    fun cancelPlan(planId: String) {
        if (alarmManager == null) return

        val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
            action = ACTION_REMINDER_ALARM
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            planId.hashCode(),
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
