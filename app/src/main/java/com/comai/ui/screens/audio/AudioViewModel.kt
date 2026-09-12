package com.comai.ui.screens.audio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comai.engine.AIEngine
import com.comai.engine.models.ContextInput
import com.comai.engine.models.ContextSignals
import com.comai.tts.TTSManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AudioState {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING
}

class AudioViewModel(
    private val aiEngine: AIEngine,
    private val ttsManager: TTSManager
) : ViewModel() {

    private val _state = MutableStateFlow(AudioState.IDLE)
    val state: StateFlow<AudioState> = _state.asStateFlow()

    private val _spokenText = MutableStateFlow("Tap the orb to start speaking with Comai")
    val spokenText: StateFlow<String> = _spokenText.asStateFlow()

    private val _aiResponseText = MutableStateFlow("")
    val aiResponseText: StateFlow<String> = _aiResponseText.asStateFlow()

    init {
        viewModelScope.launch {
            ttsManager.isSpeaking.collect { isSpeaking ->
                if (isSpeaking) {
                    _state.value = AudioState.SPEAKING
                } else if (_state.value == AudioState.SPEAKING) {
                    _state.value = AudioState.IDLE
                }
            }
        }
    }

    fun onOrbClicked() {
        when (_state.value) {
            AudioState.IDLE -> startListening()
            AudioState.LISTENING -> stopListeningAndProcess("Tell me what I should do right now")
            AudioState.SPEAKING -> {
                ttsManager.stop()
                _state.value = AudioState.IDLE
            }
            AudioState.PROCESSING -> Unit
        }
    }

    private fun startListening() {
        _state.value = AudioState.LISTENING
        _spokenText.value = "Listening... (Sprint 1 Simulation: tap again to finish)"
    }

    fun stopListeningAndProcess(simulatedSpeech: String) {
        _spokenText.value = "\"$simulatedSpeech\""
        _state.value = AudioState.PROCESSING

        viewModelScope.launch {
            val context = ContextInput(
                systemRole = "Comai Voice Companion",
                userState = "conversational",
                contextSignals = ContextSignals(time = "afternoon", location = "commute"),
                task = simulatedSpeech
            )

            val response = aiEngine.process(context)
            _aiResponseText.value = response.displayText
            ttsManager.speak(response.speech)
        }
    }
}
