package com.comai.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.comai.data.models.PersonalPlan
import com.comai.data.repository.PersonalPlanRepository

class ScheduleAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        when (action) {
            ScheduleAlarmManager.ACTION_REMINDER_ALARM -> {
                val planId = intent.getStringExtra(ScheduleAlarmManager.EXTRA_PLAN_ID) ?: return
                val title = intent.getStringExtra(ScheduleAlarmManager.EXTRA_PLAN_TITLE) ?: "Reminder"
                val time = intent.getStringExtra(ScheduleAlarmManager.EXTRA_PLAN_TIME) ?: ""
                val repeat = intent.getStringExtra(ScheduleAlarmManager.EXTRA_PLAN_REPEAT) ?: "Daily"

                // 1. Post notification
                ScheduleNotificationManager.showPlanReminder(context, planId, title, time)

                // 2. If recurring, schedule the next occurrence
                if (!repeat.equals("Once", ignoreCase = true)) {
                    val plan = PersonalPlan(
                        id = planId,
                        title = title,
                        time = time,
                        repeatFrequency = repeat,
                        isEnabled = true
                    )
                    ScheduleAlarmManager(context).schedulePlan(plan)
                }
            }

            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                // Re-arm all enabled alarms after reboot or time change
                val repository = PersonalPlanRepository(context)
                val enabledPlans = repository.plans.value.filter { it.isEnabled }
                ScheduleAlarmManager(context).rescheduleAll(enabledPlans)
            }
        }
    }
}
