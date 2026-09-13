package com.comai.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.comai.data.models.PersonalPlan
import com.comai.data.models.ReminderType
import com.comai.scheduling.ScheduleAlarmManager
import com.comai.util.TimeUtils
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Local repository for persisting and managing user's Personal Plans,
 * integrated with real-time Android AlarmManager scheduling.
 * Acts as the SINGLE SOURCE OF TRUTH for both Personal Schedule and Calendar.
 */
class PersonalPlanRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val alarmManager = ScheduleAlarmManager(appContext)

    private val _plans = MutableStateFlow<List<PersonalPlan>>(emptyList())
    val plans: StateFlow<List<PersonalPlan>> = _plans.asStateFlow()

    init {
        loadPlans()
    }

    private fun loadPlans() {
        val json = prefs.getString(KEY_PLANS, null)
        if (json.isNullOrBlank()) {
            // Sensible initial starter plans
            val defaultPlans = listOf(
                PersonalPlan(
                    title = "Drink water",
                    time = "10:00 AM",
                    repeatFrequency = "Daily",
                    reminderType = ReminderType.NOTIFICATION
                ),
                PersonalPlan(
                    title = "Take a break",
                    time = "3:30 PM",
                    repeatFrequency = "Daily",
                    reminderType = ReminderType.NOTIFICATION
                ),
                PersonalPlan(
                    title = "Call Mom",
                    time = "6:00 PM",
                    repeatFrequency = "Daily",
                    reminderType = ReminderType.NOTIFICATION
                ),
                PersonalPlan(
                    title = "Go to gym",
                    time = "7:00 PM",
                    repeatFrequency = "Daily",
                    reminderType = ReminderType.NOTIFICATION
                )
            )
            saveInternal(defaultPlans)
            alarmManager.rescheduleAll(defaultPlans)
        } else {
            try {
                val type = object : TypeToken<List<PersonalPlan>>() {}.type
                val list: List<PersonalPlan> = gson.fromJson(json, type) ?: emptyList()
                @Suppress("SENSELESS_COMPARISON")
                val safeList = list.map { plan ->
                    if (plan.reminderType == null) plan.copy(reminderType = ReminderType.NOTIFICATION) else plan
                }
                val sorted = sortPlans(safeList)
                _plans.value = sorted
                // Arm all active enabled plans (past ones will be cleanly skipped by ScheduleAlarmManager)
                alarmManager.rescheduleAll(sorted.filter { it.isEnabled })
            } catch (_: Exception) {
                _plans.value = emptyList()
            }
        }
    }

    private fun sortPlans(list: List<PersonalPlan>): List<PersonalPlan> {
        return list.sortedWith(
            compareBy(
                { it.date ?: "" },
                { TimeUtils.parseTime(it.time).toMinutesOfDay() }
            )
        )
    }

    private fun saveInternal(list: List<PersonalPlan>) {
        val sorted = sortPlans(list)
        _plans.value = sorted
        val json = gson.toJson(sorted)
        prefs.edit().putString(KEY_PLANS, json).apply()
    }

    fun addPlan(
        title: String,
        time: String,
        repeatFrequency: String = "Daily",
        reminderType: ReminderType = ReminderType.NOTIFICATION,
        date: String? = null
    ): PersonalPlan {
        val newPlan = PersonalPlan(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            time = TimeUtils.normalizeTo12Hour(time),
            date = date?.trim()?.ifBlank { null },
            repeatFrequency = repeatFrequency,
            reminderType = reminderType,
            isEnabled = true
        )
        val current = _plans.value.toMutableList()
        current.add(newPlan)
        saveInternal(current)

        // Schedule Android alarm
        alarmManager.schedulePlan(newPlan)
        return newPlan
    }

    fun updatePlan(
        id: String,
        title: String,
        time: String,
        repeatFrequency: String,
        reminderType: ReminderType = ReminderType.NOTIFICATION,
        isEnabled: Boolean,
        date: String? = null
    ): PersonalPlan? {
        val current = _plans.value.toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index == -1) return null

        // Cancel previous alarm
        alarmManager.cancelPlan(id)

        val updated = current[index].copy(
            title = title.trim(),
            time = TimeUtils.normalizeTo12Hour(time),
            date = date?.trim()?.ifBlank { null } ?: current[index].date,
            repeatFrequency = repeatFrequency,
            reminderType = reminderType,
            isEnabled = isEnabled
        )
        current[index] = updated
        saveInternal(current)

        // Re-schedule alarm if enabled
        if (isEnabled) {
            alarmManager.schedulePlan(updated)
        }
        return updated
    }

    fun deletePlan(id: String): Boolean {
        // Cancel alarm
        alarmManager.cancelPlan(id)

        val current = _plans.value.toMutableList()
        val removed = current.removeAll { it.id == id }
        if (removed) {
            saveInternal(current)
        }
        return removed
    }

    fun togglePlan(id: String): Boolean {
        val current = _plans.value.toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index == -1) return false

        val updated = current[index].copy(isEnabled = !current[index].isEnabled)
        current[index] = updated
        saveInternal(current)

        if (updated.isEnabled) {
            alarmManager.schedulePlan(updated)
        } else {
            alarmManager.cancelPlan(id)
        }
        return true
    }

    companion object {
        private const val PREFS_NAME = "comai_personal_plans_prefs"
        private const val KEY_PLANS = "key_personal_plans_json"

        @Volatile
        private var INSTANCE: PersonalPlanRepository? = null

        fun getInstance(context: Context): PersonalPlanRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PersonalPlanRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
