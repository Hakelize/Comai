package com.comai.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.comai.data.models.PersonalPlan
import com.comai.data.models.ReminderType
import com.comai.data.repository.PersonalPlanRepository

class ScheduleAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        when (action) {
            ScheduleAlarmManager.ACTION_REMINDER_ALARM -> {
                val planId = intent.getStringExtra(ScheduleAlarmManager.EXTRA_PLAN_ID) ?: return
                val title = intent.getStringExtra(ScheduleAlarmManager.EXTRA_PLAN_TITLE) ?: "Reminder"
                val time = intent.getStringExtra(ScheduleAlarmManager.EXTRA_PLAN_TIME) ?: ""
                val date = intent.getStringExtra(ScheduleAlarmManager.EXTRA_PLAN_DATE)
                val repeat = intent.getStringExtra(ScheduleAlarmManager.EXTRA_PLAN_REPEAT) ?: "Daily"
                val reminderTypeStr = intent.getStringExtra(ScheduleAlarmManager.EXTRA_REMINDER_TYPE)
                val reminderType = try {
                    ReminderType.valueOf(reminderTypeStr ?: ReminderType.NOTIFICATION.name)
                } catch (_: Exception) {
                    ReminderType.NOTIFICATION
                }

                // 1. Play audible sound if ALARM
                if (reminderType == ReminderType.ALARM) {
                    AlarmAudioPlayer.play(context)
                }

                // 2. Post notification with Dismiss & Snooze actions
                ScheduleNotificationManager.showPlanReminder(context, planId, title, time, reminderType)

                // 3. If recurring, schedule the next occurrence in the future
                if (!repeat.equals("Once", ignoreCase = true)) {
                    val plan = PersonalPlan(
                        id = planId,
                        title = title,
                        time = time,
                        date = date,
                        repeatFrequency = repeat,
                        reminderType = reminderType,
                        isEnabled = true
                    )
                    ScheduleAlarmManager(context).schedulePlan(plan)
                }
            }

            ScheduleAlarmManager.ACTION_DISMISS_ALARM -> {
                val planId = intent.getStringExtra(ScheduleAlarmManager.EXTRA_PLAN_ID)
                AlarmAudioPlayer.stop()
                if (planId != null) {
                    ScheduleNotificationManager.cancelNotification(context, planId)
                    ScheduleAlarmManager(context).cancelSnooze(planId)
                }
            }

            ScheduleAlarmManager.ACTION_SNOOZE_ALARM -> {
                val planId = intent.getStringExtra(ScheduleAlarmManager.EXTRA_PLAN_ID) ?: return
                val title = intent.getStringExtra(ScheduleAlarmManager.EXTRA_PLAN_TITLE) ?: "Reminder"
                val reminderTypeStr = intent.getStringExtra(ScheduleAlarmManager.EXTRA_REMINDER_TYPE)
                val reminderType = try {
                    ReminderType.valueOf(reminderTypeStr ?: ReminderType.ALARM.name)
                } catch (_: Exception) {
                    ReminderType.ALARM
                }

                AlarmAudioPlayer.stop()
                ScheduleNotificationManager.cancelNotification(context, planId)
                ScheduleAlarmManager(context).scheduleSnooze(planId, title, reminderType, 10)
            }

            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.TIME_SET",
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                // Re-arm all active upcoming alarms after phone reboot or system time change
                val repository = PersonalPlanRepository.getInstance(context)
                val enabledPlans = repository.plans.value.filter { it.isEnabled }
                ScheduleAlarmManager(context).rescheduleAll(enabledPlans)
            }
        }
    }
}
