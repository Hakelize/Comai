package com.lifeloop.comai.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import com.lifeloop.comai.engine.AIEngine
import com.lifeloop.comai.engine.models.ContextSignals
import com.lifeloop.comai.tts.TTSManager
import com.lifeloop.comai.ui.screens.chat.ChatViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DashboardViewModel(
    private val aiEngine: AIEngine,
    private val ttsManager: TTSManager,
    private val chatViewModel: ChatViewModel
) : ViewModel() {

    private val _selectedTime = MutableStateFlow("morning")
    val selectedTime: StateFlow<String> = _selectedTime.asStateFlow()

    private val _selectedLocation = MutableStateFlow("home")
    val selectedLocation: StateFlow<String> = _selectedLocation.asStateFlow()

    private val _routineDeviation = MutableStateFlow(false)
    val routineDeviation: StateFlow<Boolean> = _routineDeviation.asStateFlow()

    fun setTime(time: String) {
        _selectedTime.value = time
        pushContext()
    }

    fun setLocation(loc: String) {
        _selectedLocation.value = loc
        pushContext()
    }

    fun setDeviation(dev: Boolean) {
        _routineDeviation.value = dev
        pushContext()
    }

    private fun pushContext() {
        chatViewModel.updateContextOverride(
            signals = ContextSignals(
                time = _selectedTime.value,
                location = _selectedLocation.value,
                routineDeviation = _routineDeviation.value
            ),
            userState = "demo_override"
        )
    }

    fun triggerStoryBeat(beatName: String, prompt: String) {
        chatViewModel.triggerScenario(beatName, prompt)
    }

    fun getEngineName(): String = aiEngine.engineName()
}
