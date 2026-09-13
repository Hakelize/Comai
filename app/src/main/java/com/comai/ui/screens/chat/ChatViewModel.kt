package com.comai.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comai.data.models.ChatMessage
import com.comai.engine.AIEngine
import com.comai.engine.models.ContextInput
import com.comai.engine.models.ContextSignals
import com.comai.tts.TTSManager
import com.comai.voice.ComaiLanguage
import com.comai.voice.TextNormalizer
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
    private val memoryRepository: com.comai.memory.MemoryRepository? = null,
    private val textNormalizer: TextNormalizer = TextNormalizer(),
    private val memoryContextProvider: com.comai.memory.MemoryContextProvider? = null
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    /** Current language for text normalization. Updated from VoiceHomeScreen. */
    private var currentLanguage: ComaiLanguage = ComaiLanguage.ENGLISH

    /** Tracks whether the user is actively viewing the Chat tab. AI responses in Chat are text-only (NO TTS). */
    private var isChatTabActive: Boolean = false

    fun setChatTabActive(active: Boolean) {
        isChatTabActive = active
        if (active) {
            ttsManager.stop()
        }
        android.util.Log.i("ChatViewModel", "CHAT_TAB_ACTIVE: $active (TTS disabled in chat)")
    }

    fun setLanguage(language: ComaiLanguage) {
        currentLanguage = language
        android.util.Log.i("ChatViewModel", "LANGUAGE_SET: ${language.name}")
    }

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
                    if (!isChatTabActive) {
                        ttsManager.speak(response.speech)
                    }
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

    fun sendMessage(
        text: String,
        mediaUri: String? = null,
        mediaType: String? = null,
        mediaName: String? = null,
        speakResponse: Boolean? = null
    ) {
        if (text.isBlank() && mediaUri == null) return

        val effectiveContent = if (text.isNotBlank()) text else (mediaName ?: "Sent an attachment")
        val userMessage = ChatMessage(
            content = effectiveContent,
            isFromUser = true,
            mediaUri = mediaUri,
            mediaType = mediaType,
            mediaName = mediaName
        )
        _messages.value = _messages.value + userMessage

        viewModelScope.launch {
            _isTyping.value = true
            try {
                // Deterministically extract and persist explicit personal memory if present
                memoryRepository?.extractAndStoreMemory(text)

                // Retrieve relevant memories for this task / context
                val provider = memoryContextProvider ?: memoryRepository?.let { com.comai.memory.MemoryContextProvider(it) }
                val retrievedData = provider?.getFormattedMemoryContext(
                    task = text,
                    signals = _currentContext.value.contextSignals
                )
                val memoryContextList = provider?.getMemoryContextList(
                    task = text,
                    signals = _currentContext.value.contextSignals
                )

                val input = _currentContext.value.copy(
                    task = textNormalizer.normalize(text, currentLanguage),
                    retrievedData = retrievedData,
                    memoryContext = memoryContextList
                )
                android.util.Log.i("ChatViewModel", "TEXT_SUBMITTED: length=${text.length}, lang=${currentLanguage.name}")
                android.util.Log.i("ChatViewModel", "AI_PROCESSING: task=${input.task}, hasRetrievedData=${retrievedData != null}, memCount=${memoryContextList?.size ?: 0}")
                val response = aiEngine.process(input)
                android.util.Log.i("ChatViewModel", "AI_RESPONSE: action=${response.action}")

                val aiMessage = ChatMessage(
                    content = response.displayText,
                    isFromUser = false,
                    action = response.action
                )
                _messages.value = _messages.value + aiMessage

                // Speak response via Android TTS ONLY when outside Chat tab (or explicitly requested)
                val shouldSpeak = if (isChatTabActive) false else (speakResponse ?: true)
                if (shouldSpeak) {
                    ttsManager.speak(response.speech)
                } else {
                    android.util.Log.i("ChatViewModel", "TTS_SUPPRESSED: Chat tab is active; text-only response delivered.")
                }
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
                if (!isChatTabActive) {
                    ttsManager.speak(response.speech)
                }
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
