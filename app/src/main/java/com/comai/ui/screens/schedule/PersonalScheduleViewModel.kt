package com.comai.ui.screens.schedule

import androidx.lifecycle.ViewModel
import com.comai.data.models.PersonalPlan
import com.comai.data.repository.PersonalPlanRepository
import kotlinx.coroutines.flow.StateFlow

class PersonalScheduleViewModel(
    private val repository: PersonalPlanRepository
) : ViewModel() {

    val plans: StateFlow<List<PersonalPlan>> = repository.plans

    fun addPlan(title: String, time: String, repeatFrequency: String = "Daily"): PersonalPlan {
        return repository.addPlan(title, time, repeatFrequency)
    }

    fun updatePlan(id: String, title: String, time: String, repeatFrequency: String, isEnabled: Boolean): PersonalPlan? {
        return repository.updatePlan(id, title, time, repeatFrequency, isEnabled)
    }

    fun deletePlan(id: String): Boolean {
        return repository.deletePlan(id)
    }

    fun togglePlan(id: String): Boolean {
        return repository.togglePlan(id)
    }
}
