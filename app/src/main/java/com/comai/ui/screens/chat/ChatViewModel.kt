package com.comai.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comai.data.models.ChatMessage
import com.comai.engine.AIEngine
import com.comai.engine.models.ContextInput
import com.comai.engine.models.ContextSignals
import com.comai.tts.TTSManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatViewModel(
    private val aiEngine: AIEngine,
    private val ttsManager: TTSManager,
    private val contextBridge: com.comai.context.ContextBridge? = null,
    private val memoryRepository: com.comai.memory.MemoryRepository? = null
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    private val _currentContext = MutableStateFlow(
        ContextInput(
            systemRole = "Comai — a warm, proactive AI life companion",
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

        // Observe proactive context events emitted by the Context Engine / ContextBridge
        contextBridge?.let { bridge ->
            viewModelScope.launch {
                bridge.proactiveEvents.collect { response ->
                    val proactiveMessage = ChatMessage(
                        content = response.displayText,
                        isFromUser = false,
                        action = response.action
                    )
                    _messages.value = _messages.value + proactiveMessage
                    ttsManager.speak(response.speech)
                }
            }
        }
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
            try {
                // Deterministically extract and persist explicit personal memory if present
                memoryRepository?.extractAndStoreMemory(text)

                // Retrieve relevant memories for this task / context
                val retrievedData = memoryRepository?.retrieveRelevantMemories(
                    task = text,
                    signals = _currentContext.value.contextSignals
                )

                val input = _currentContext.value.copy(
                    task = text,
                    retrievedData = retrievedData
                )
                android.util.Log.i("ChatViewModel", "TEXT_SUBMITTED: length=${text.length}")
                android.util.Log.i("ChatViewModel", "AI_PROCESSING: task=${input.task}, hasRetrievedData=${retrievedData != null}")
                val response = aiEngine.process(input)
                android.util.Log.i("ChatViewModel", "AI_RESPONSE: action=${response.action}")

                val aiMessage = ChatMessage(
                    content = response.displayText,
                    isFromUser = false,
                    action = response.action
                )
                _messages.value = _messages.value + aiMessage

                // Speak response via Android TTS
                ttsManager.speak(response.speech)
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "AI processing failure", e)
                val errorBubble = ChatMessage(
                    content = "Sorry, I couldn't process that right now.",
                    isFromUser = false,
                    action = "error"
                )
                _messages.value = _messages.value + errorBubble
            } finally {
                _isTyping.value = false
            }
        }
    }

    fun triggerScenario(scenario: String, displayText: String) {
        viewModelScope.launch {
            _isTyping.value = true
            try {
                val retrievedData = memoryRepository?.retrieveRelevantMemories(
                    task = scenario,
                    signals = _currentContext.value.contextSignals
                )
                val input = _currentContext.value.copy(
                    task = scenario,
                    retrievedData = retrievedData
                )
                val response = aiEngine.process(input)

                val aiMessage = ChatMessage(
                    content = response.displayText,
                    isFromUser = false,
                    action = response.action
                )
                _messages.value = _messages.value + aiMessage
                ttsManager.speak(response.speech)
            } catch (e: Exception) {
                val errorBubble = ChatMessage(
                    content = "I'm having trouble triggering that scenario right now.",
                    isFromUser = false,
                    action = "error"
                )
                _messages.value = _messages.value + errorBubble
            } finally {
                _isTyping.value = false
            }
        }
    }

    fun clearMessages() {
        _messages.value = emptyList()
    }
}
