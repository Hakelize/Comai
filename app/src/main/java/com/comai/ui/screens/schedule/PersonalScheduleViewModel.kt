package com.comai.ui.screens.schedule

import androidx.lifecycle.ViewModel
import com.comai.data.models.PersonalPlan
import com.comai.data.models.ReminderType
import com.comai.data.repository.PersonalPlanRepository
import kotlinx.coroutines.flow.StateFlow

class PersonalScheduleViewModel(
    private val repository: PersonalPlanRepository
) : ViewModel() {

    val plans: StateFlow<List<PersonalPlan>> = repository.plans

    fun addPlan(
        title: String,
        time: String,
        repeatFrequency: String = "Daily",
        reminderType: ReminderType = ReminderType.NOTIFICATION,
        date: String? = null
    ): PersonalPlan {
        return repository.addPlan(title, time, repeatFrequency, reminderType, date)
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
        return repository.updatePlan(id, title, time, repeatFrequency, reminderType, isEnabled, date)
    }

    fun deletePlan(id: String): Boolean {
        return repository.deletePlan(id)
    }

    fun togglePlan(id: String): Boolean {
        return repository.togglePlan(id)
    }
}
