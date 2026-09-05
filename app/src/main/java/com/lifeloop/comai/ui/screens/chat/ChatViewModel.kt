package com.lifeloop.comai.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifeloop.comai.data.models.ChatMessage
import com.lifeloop.comai.engine.AIEngine
import com.lifeloop.comai.engine.models.ContextInput
import com.lifeloop.comai.engine.models.ContextSignals
import com.lifeloop.comai.tts.TTSManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatViewModel(
    private val aiEngine: AIEngine,
    private val ttsManager: TTSManager
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    private val _currentContext = MutableStateFlow(
        ContextInput(
            systemRole = "Camoi — warm, proactive AI life companion",
            userState = "active",
            contextSignals = ContextSignals(time = "morning", location = "home", routineDeviation = false),
            task = "greeting"
        )
    )
    val currentContext: StateFlow<ContextInput> = _currentContext.asStateFlow()

    init {
        // Prepopulate with a welcoming initial message
        val initialTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        _messages.value = listOf(
            ChatMessage(
                content = "Good morning! I noticed you're up a bit earlier than usual. Would you like me to adjust your morning routine?",
                isFromUser = false,
                action = "greeting"
            )
        )
    }

    fun updateContextOverride(signals: ContextSignals, userState: String) {
        _currentContext.value = _currentContext.value.copy(
            contextSignals = signals,
            userState = userState
        )
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val userMessage = ChatMessage(
            content = text,
            isFromUser = true
        )
        _messages.value = _messages.value + userMessage

        viewModelScope.launch {
            _isTyping.value = true

            val input = _currentContext.value.copy(task = text)
            val response = aiEngine.process(input)

            _isTyping.value = false

            val aiMessage = ChatMessage(
                content = response.displayText,
                isFromUser = false,
                action = response.action
            )
            _messages.value = _messages.value + aiMessage

            // Speak response via Android TTS
            ttsManager.speak(response.speech)
        }
    }

    fun triggerScenario(scenario: String, displayText: String) {
        viewModelScope.launch {
            _isTyping.value = true
            val input = _currentContext.value.copy(task = scenario)
            val response = aiEngine.process(input)
            _isTyping.value = false

            val aiMessage = ChatMessage(
                content = response.displayText,
                isFromUser = false,
                action = response.action
            )
            _messages.value = _messages.value + aiMessage
            ttsManager.speak(response.speech)
        }
    }

    fun clearMessages() {
        _messages.value = emptyList()
    }
}
