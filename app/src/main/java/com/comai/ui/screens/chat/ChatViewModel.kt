package com.comai.ui.screens.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comai.capability.AppLaunchHandler
import com.comai.data.models.ChatMessage
import com.comai.data.repository.ChatHistoryRepository
import com.comai.engine.AIEngine
import com.comai.engine.models.ContextInput
import com.comai.engine.models.ContextSignals
import com.comai.nearby.LocationHelper
import com.comai.search.SearchResult
import com.comai.search.WebSearchService
import com.comai.tts.TTSManager
import com.comai.ui.screens.onboarding.OnboardingPreferences
import com.comai.voice.ComaiLanguage
import com.comai.voice.TextNormalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class ChatViewModel(
    private val aiEngine: AIEngine,
    private val ttsManager: TTSManager,
    private val contextBridge: com.comai.context.ContextBridge? = null,
    private val memoryRepository: com.comai.memory.MemoryRepository? = null,
    private val textNormalizer: TextNormalizer = TextNormalizer(),
    private val memoryContextProvider: com.comai.memory.MemoryContextProvider? = null,
    private val appContext: Context? = null
) : ViewModel() {

    private val chatHistoryRepository = appContext?.let { ChatHistoryRepository(it) }
    private val onboardingPrefs = appContext?.let { OnboardingPreferences(it) }
    private val locationHelper = appContext?.let { LocationHelper(it) }

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

    private fun appendMessage(message: ChatMessage) {
        val updated = _messages.value + message
        _messages.value = updated
        chatHistoryRepository?.saveMessages(updated)
    }

    private fun getLiveContextSignals(): ContextSignals {
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val period = when (hour) {
            in 5..11 -> "morning"
            in 12..16 -> "afternoon"
            in 17..20 -> "evening"
            else -> "night"
        }
        val exactTime = SimpleDateFormat("h:mm a, EEEE, MMM d, yyyy", Locale.getDefault()).format(cal.time)
        return ContextSignals(
            time = "$period ($exactTime)",
            location = "Current Location",
            routineDeviation = false
        )
    }

    private fun isTimeOrDateQuery(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT).trim()
        if (lower.contains("what time do i") || lower.contains("when do i") || lower.contains("my time")) return false
        return lower.contains("what time") || lower.contains("current time") ||
                lower.contains("what is the time") || lower.contains("what's the time") ||
                lower.contains("tell me the time") || lower.contains("time now") ||
                lower == "time" || lower == "what is time" ||
                lower.contains("today's date") || lower.contains("what date") ||
                lower.contains("what is the date") || lower.contains("what day is it") ||
                lower.contains("what day is today") || lower.contains("current date") ||
                lower == "date"
    }

    private fun isLocationQuery(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT).trim().removeSuffix("?").removeSuffix(".").trim()
        return lower == "where am i" ||
                lower == "where am i now" ||
                lower == "what is my location" ||
                lower == "what's my location" ||
                lower == "what is my current location" ||
                lower == "what's my current location" ||
                lower == "tell me my location" ||
                lower == "tell me where am i" ||
                lower == "tell me where i am" ||
                lower == "where i am" ||
                lower == "my location" ||
                lower == "current location" ||
                lower == "location" ||
                lower == "where are we"
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
        // Restore saved messages from persistent storage or initialize with welcoming greeting
        val saved = chatHistoryRepository?.loadMessages() ?: emptyList()
        if (saved.isNotEmpty()) {
            _messages.value = saved
            android.util.Log.i("ChatViewModel", "RESTORED_CHAT_HISTORY: ${saved.size} messages restored from disk.")
        } else {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val initialGreeting = when (hour) {
                in 5..11 -> "Good morning! Hope you have a wonderful start to your day. How can I help you today?"
                in 12..16 -> "Good afternoon! Hope your day is going smoothly. How can I assist you right now?"
                in 17..20 -> "Good evening! Ready to wind down or check tomorrow's schedule? How can I help?"
                else -> "Hello! It's getting late. Let me know if you need anything before resting."
            }
            val initialMsg = ChatMessage(
                content = initialGreeting,
                isFromUser = false,
                action = "greeting"
            )
            _messages.value = listOf(initialMsg)
            chatHistoryRepository?.saveMessages(_messages.value)
        }

        // Observe proactive context events emitted by the Context Engine / ContextBridge
        contextBridge?.let { bridge ->
            viewModelScope.launch {
                bridge.proactiveEvents.collect { response ->
                    val proactiveMessage = ChatMessage(
                        content = response.displayText,
                        isFromUser = false,
                        action = response.action
                    )
                    appendMessage(proactiveMessage)
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
        appendMessage(userMessage)

        viewModelScope.launch {
            _isTyping.value = true
            try {
                // ── Priority 0.1: Instant System Time / Date Query ──
                if (isTimeOrDateQuery(text)) {
                    val now = Calendar.getInstance()
                    val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(now.time)
                    val dateStr = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(now.time)
                    val tz = TimeZone.getDefault().getDisplayName(false, TimeZone.SHORT)
                    val responseText = "🕒 It is currently **$timeStr** ($tz) on **$dateStr**."
                    val speechText = "It is $timeStr on $dateStr."
                    val aiMessage = ChatMessage(
                        content = responseText,
                        isFromUser = false,
                        action = "time_query"
                    )
                    appendMessage(aiMessage)
                    val shouldSpeak = if (isChatTabActive) false else (speakResponse ?: true)
                    if (shouldSpeak) {
                        ttsManager.speak(speechText)
                    }
                    return@launch
                }

                // ── Priority 0.2: Instant Location Query ("Where am I?", "location") ──
                if (isLocationQuery(text)) {
                    val loc = locationHelper?.getCurrentLocationState()
                    val (displayText, speechText) = if (loc != null && loc.isPermissionGranted) {
                        val place = loc.placeName.ifBlank { "Current Coordinates" }
                        val coords = String.format(Locale.US, "%.4f, %.4f", loc.latitude, loc.longitude)
                        val accuracy = if (loc.accuracyMeters != null && loc.accuracyMeters > 0f) " (±${loc.accuracyMeters.toInt()}m accuracy)" else ""
                        Pair(
                            "📍 You are currently at **$place**\nCoordinates: `$coords`$accuracy.",
                            "You are currently at ${loc.locality.ifBlank { loc.placeName }}."
                        )
                    } else {
                        Pair(
                            "📍 Location permission is not granted yet. You can grant location permission in the Memory tab or app settings to view your exact location.",
                            "Please enable location permission in app settings or the Memory tab to view your location."
                        )
                    }
                    val aiMessage = ChatMessage(
                        content = displayText,
                        isFromUser = false,
                        action = "location_query"
                    )
                    appendMessage(aiMessage)
                    val shouldSpeak = if (isChatTabActive) false else (speakResponse ?: true)
                    if (shouldSpeak) {
                        ttsManager.speak(speechText)
                    }
                    return@launch
                }

                // ── Priority 0.3: Explicit Personalization / Memory Modification Command ──
                val personalizationResponse = handlePersonalizationOrMemoryCommand(text)
                if (personalizationResponse != null) {
                    val aiMessage = ChatMessage(
                        content = personalizationResponse.first,
                        isFromUser = false,
                        action = "personalization_update"
                    )
                    appendMessage(aiMessage)
                    val shouldSpeak = if (isChatTabActive) false else (speakResponse ?: true)
                    if (shouldSpeak) {
                        ttsManager.speak(personalizationResponse.second)
                    }
                    return@launch
                }

                // ── Priority 1: App Launch Commands ──
                val appName = AppLaunchHandler.detectAppLaunchIntent(text)
                if (appName != null && appContext != null) {
                    val result = AppLaunchHandler.launchApp(appContext, appName)
                    val aiMessage = ChatMessage(
                        content = result,
                        isFromUser = false,
                        action = "app_launch"
                    )
                    appendMessage(aiMessage)
                    android.util.Log.i("ChatViewModel", "APP_LAUNCH: $appName → $result")
                    return@launch
                }

                // ── Priority 2: Web Search with Citations ──
                var searchResults: List<SearchResult>? = null
                var searchContext: String? = null
                if (WebSearchService.hasSearchIntent(text)) {
                    val query = WebSearchService.extractQuery(text)
                    android.util.Log.i("ChatViewModel", "WEB_SEARCH: query='$query'")
                    try {
                        searchResults = WebSearchService.search(query)
                        if (searchResults.isNotEmpty()) {
                            searchContext = WebSearchService.formatAsContext(searchResults)
                            android.util.Log.i("ChatViewModel", "WEB_SEARCH_RESULTS: ${searchResults.size} results")
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("ChatViewModel", "Web search failed: ${e.message}")
                    }
                }

                // Deterministically extract and persist explicit personal memory if present
                memoryRepository?.extractAndStoreMemory(text)

                // Compute live signals (current time and period of day)
                val liveSignals = getLiveContextSignals()
                _currentContext.value = _currentContext.value.copy(contextSignals = liveSignals)

                // Retrieve relevant memories for this task / context
                val provider = memoryContextProvider ?: memoryRepository?.let { com.comai.memory.MemoryContextProvider(it) }
                val retrievedData = provider?.getFormattedMemoryContext(
                    task = text,
                    signals = liveSignals
                )
                val memoryContextList = provider?.getMemoryContextList(
                    task = text,
                    signals = liveSignals
                )

                // Build recent multi-turn conversation turns for transformer back-and-forth context
                val historyTurns = _messages.value
                    .filter { it.content.isNotBlank() && it.action != "error" && it.action != "time_query" && it.action != "location_query" }
                    .dropLast(1) // exclude the current message just added
                    .takeLast(8)
                    .map { msg ->
                        com.comai.engine.models.ChatTurn(
                            role = if (msg.isFromUser) "user" else "model",
                            content = msg.content
                        )
                    }

                // Merge memory context + search context
                val combinedRetrievedData = buildString {
                    if (!retrievedData.isNullOrBlank()) {
                        append(retrievedData)
                    }
                    if (!searchContext.isNullOrBlank()) {
                        if (isNotEmpty()) append("\n\n")
                        append(searchContext)
                    }
                }.ifBlank { null }

                val input = _currentContext.value.copy(
                    task = textNormalizer.normalize(text, currentLanguage),
                    contextSignals = liveSignals,
                    retrievedData = combinedRetrievedData,
                    memoryContext = memoryContextList,
                    conversationHistory = historyTurns
                )
                android.util.Log.i("ChatViewModel", "TEXT_SUBMITTED: length=${text.length}, lang=${currentLanguage.name}")
                android.util.Log.i("ChatViewModel", "AI_PROCESSING: task=${input.task}, hasRetrievedData=${combinedRetrievedData != null}, memCount=${memoryContextList?.size ?: 0}, hasSearch=${searchResults != null}")
                val response = aiEngine.process(input)
                android.util.Log.i("ChatViewModel", "AI_RESPONSE: action=${response.action}")

                val aiMessage = ChatMessage(
                    content = response.displayText,
                    isFromUser = false,
                    action = response.action,
                    citations = searchResults?.takeIf { it.isNotEmpty() }
                )
                appendMessage(aiMessage)

                // Store the exchange as an episodic memory for conversation continuity
                if (response.action != "error" && text.length > 4) {
                    storeEpisodicMemory(text)
                }

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
                appendMessage(errorBubble)
            } finally {
                _isTyping.value = false
            }
        }
    }

    /**
     * Handles explicit personalization or memory commands (e.g. "I am female", "Change my name to Sarah",
     * "Save that I love jazz", "My office is at Downtown Hub").
     * Immediately persists changes to OnboardingPreferences and Room MemoryRepository.
     */
    private suspend fun handlePersonalizationOrMemoryCommand(text: String): Pair<String, String>? {
        val lower = text.lowercase(Locale.ROOT).trim().removeSuffix(".").trim()

        // 1. Gender update (e.g. "I am female", "I'm female", "I am a woman", "my gender is female", "save that I am female")
        val isFemaleStmt = lower == "i am female" || lower == "i'm female" || lower == "i am a woman" ||
                lower == "i'm a woman" || lower == "my gender is female" || lower == "set gender to female" ||
                lower == "change gender to female" || lower == "save that i am female" || lower == "save that i'm female" ||
                lower == "remember that i am female" || lower == "remember i am female" || lower == "i am girl" ||
                lower == "i'm a girl"
        if (isFemaleStmt) {
            onboardingPrefs?.let { prefs ->
                val current = prefs.getProfile()
                prefs.saveProfile(current.copy(gender = "Female"))
            }
            memoryRepository?.saveMemory(
                com.comai.contextengine.db.Memory(
                    type = "PERSONAL_KNOWLEDGE",
                    key = "user_gender",
                    value = "User's gender is Female",
                    timestampMs = System.currentTimeMillis(),
                    source = "USER_EXPLICIT",
                    confidence = 1.0f,
                    isUserDeletable = true
                )
            )
            return Pair(
                "🌸 Got it! I've updated your profile — your gender is now set to **Female**, and **Menstrual Cycle Tracking** is now active in your Schedule and Memory tabs.",
                "Got it! I've updated your profile to Female, and cycle tracking is now active."
            )
        }

        val isMaleStmt = lower == "i am male" || lower == "i'm male" || lower == "i am a man" ||
                lower == "i'm a man" || lower == "my gender is male" || lower == "set gender to male" ||
                lower == "change gender to male" || lower == "save that i am male" || lower == "save that i'm male" ||
                lower == "remember that i am male" || lower == "remember i am male"
        if (isMaleStmt) {
            onboardingPrefs?.let { prefs ->
                val current = prefs.getProfile()
                prefs.saveProfile(current.copy(gender = "Male"))
            }
            memoryRepository?.saveMemory(
                com.comai.contextengine.db.Memory(
                    type = "PERSONAL_KNOWLEDGE",
                    key = "user_gender",
                    value = "User's gender is Male",
                    timestampMs = System.currentTimeMillis(),
                    source = "USER_EXPLICIT",
                    confidence = 1.0f,
                    isUserDeletable = true
                )
            )
            return Pair(
                "Got it! I've updated your profile — your gender is now set to **Male**.",
                "Got it! I've updated your profile to Male."
            )
        }

        // 2. Name update (e.g. "change my name to Sarah", "my name is Sarah", "call me Sarah", "set name to Sarah")
        val nameRegex = """(?i)\b(?:change\s+my\s+name\s+to|update\s+my\s+name\s+to|set\s+my\s+name\s+to|my\s+name\s+is|call\s+me|i\s+go\s+by)\s+([A-Za-z][a-zA-Z\s]{1,30})""".toRegex()
        val nameMatch = nameRegex.find(text)
        if (nameMatch != null && !lower.contains("what is") && !lower.contains("what's")) {
            val name = nameMatch.groupValues[1].trim()
            if (name.isNotBlank() && !name.equals("female", true) && !name.equals("male", true)) {
                onboardingPrefs?.let { prefs ->
                    val current = prefs.getProfile()
                    prefs.saveProfile(current.copy(name = name))
                }
                memoryRepository?.saveMemory(
                    com.comai.contextengine.db.Memory(
                        type = "PERSONAL_KNOWLEDGE",
                        key = "user_name",
                        value = "User's name is $name",
                        timestampMs = System.currentTimeMillis(),
                        source = "USER_EXPLICIT",
                        confidence = 1.0f,
                        isUserDeletable = true
                    )
                )
                return Pair(
                    "✨ Wonderful, **$name**! I've updated your profile and will remember your name.",
                    "Wonderful $name, I've updated your profile and will remember your name."
                )
            }
        }

        // 3. Workplace / Office update (e.g. "I work at Google", "my office is at Tech Park", "save that my workplace is ...")
        val workRegex = """(?i)\b(?:i\s+work\s+at|my\s+office\s+is\s+at|my\s+workplace\s+is|change\s+my\s+workplace\s+to|set\s+workplace\s+to)\s+([^.!?\n]+)""".toRegex()
        val workMatch = workRegex.find(text)
        if (workMatch != null && !lower.contains("where")) {
            val place = workMatch.groupValues[1].trim()
            if (place.isNotBlank()) {
                onboardingPrefs?.let { prefs ->
                    val current = prefs.getProfile()
                    prefs.saveProfile(current.copy(workplace = place, placeName = place, weekdayType = "Work"))
                }
                memoryRepository?.saveMemory(
                    com.comai.contextengine.db.Memory(
                        type = "PERSONAL_KNOWLEDGE",
                        key = "user_workplace",
                        value = "User works at $place",
                        timestampMs = System.currentTimeMillis(),
                        source = "USER_EXPLICIT",
                        confidence = 1.0f,
                        isUserDeletable = true
                    )
                )
                return Pair(
                    "💼 Noted! I've updated your workplace to **$place**.",
                    "Noted! I've updated your workplace to $place."
                )
            }
        }

        // 4. College update (e.g. "I study at IIT", "my college is ...")
        val collegeRegex = """(?i)\b(?:i\s+study\s+at|my\s+college\s+is|change\s+my\s+college\s+to|set\s+college\s+to)\s+([^.!?\n]+)""".toRegex()
        val collegeMatch = collegeRegex.find(text)
        if (collegeMatch != null && !lower.contains("where")) {
            val place = collegeMatch.groupValues[1].trim()
            if (place.isNotBlank()) {
                onboardingPrefs?.let { prefs ->
                    val current = prefs.getProfile()
                    prefs.saveProfile(current.copy(college = place, placeName = place, weekdayType = "College"))
                }
                memoryRepository?.saveMemory(
                    com.comai.contextengine.db.Memory(
                        type = "PERSONAL_KNOWLEDGE",
                        key = "user_college",
                        value = "User studies at $place",
                        timestampMs = System.currentTimeMillis(),
                        source = "USER_EXPLICIT",
                        confidence = 1.0f,
                        isUserDeletable = true
                    )
                )
                return Pair(
                    "🎓 Noted! I've updated your college to **$place**.",
                    "Noted! I've updated your college to $place."
                )
            }
        }

        // 5. Explicit Save / Remember directive (e.g. "Save that I love pizza", "Save this: ...", "Remember that ...")
        val saveDirectiveRegex = """(?i)\b(?:please\s+)?(?:save(?:\s+that|\s+this)?(?:\s+memory)?|remember(?:\s+that)?|note(?:\s+that)?)\s*[:\s]\s*([^.!?\n]+)""".toRegex()
        val saveMatch = saveDirectiveRegex.find(text)
        if (saveMatch != null || lower.startsWith("save ") || lower.startsWith("remember ")) {
            val fact = saveMatch?.groupValues?.getOrNull(1)?.trim()
                ?: text.replaceFirst("""(?i)^(?:please\s+)?(?:save|remember|note)\s+(?:that\s+|this\s*:\s*)?""".toRegex(), "").trim()
            if (fact.isNotBlank() && fact.length >= 3) {
                val words = fact.lowercase(Locale.ROOT).split("\\s+".toRegex()).take(3).joinToString("_")
                memoryRepository?.saveMemory(
                    com.comai.contextengine.db.Memory(
                        type = "PERSONAL_KNOWLEDGE",
                        key = "note_$words",
                        value = "User noted: $fact",
                        timestampMs = System.currentTimeMillis(),
                        source = "USER_EXPLICIT",
                        confidence = 1.0f,
                        isUserDeletable = true
                    )
                )
                return Pair(
                    "💾 Saved! I've stored that in your personal memory:\n> \"$fact\"",
                    "Saved to your personal memory: $fact."
                )
            }
        }

        return null
    }

    /**
     * Stores a short episodic memory of the user's message for conversation continuity.
     * Only saves if the user message provides meaningful context (not just greetings).
     */
    private fun storeEpisodicMemory(userText: String) {
        val lower = userText.lowercase(Locale.ROOT)
        // Skip trivial messages
        if (lower.length < 5 || lower in setOf("hi", "hello", "hey", "ok", "okay", "thanks", "bye")) return
        // Skip if it's a memory query (we don't want to re-store memory queries as memories)
        if (lower.contains("what do you know") || lower.contains("what do you remember") || lower.contains("recall")) return

        viewModelScope.launch {
            try {
                val key = "session_note_" + userText.take(20).lowercase(Locale.ROOT).replace("\\W+".toRegex(), "_")
                val value = "User said: \"${userText.take(80)}\""
                memoryRepository?.saveMemory(
                    com.comai.contextengine.db.Memory(
                        type = "EPISODIC",
                        key = key,
                        value = value,
                        timestampMs = System.currentTimeMillis(),
                        source = "CONVERSATION",
                        confidence = 0.6f,
                        isUserDeletable = true
                    )
                )
            } catch (_: Exception) { /* Non-critical — don't crash on episodic storage */ }
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
                appendMessage(aiMessage)
                if (!isChatTabActive) {
                    ttsManager.speak(response.speech)
                }
            } catch (e: Exception) {
                val errorBubble = ChatMessage(
                    content = "I'm having trouble triggering that scenario right now.",
                    isFromUser = false,
                    action = "error"
                )
                appendMessage(errorBubble)
            } finally {
                _isTyping.value = false
            }
        }
    }

    fun clearMessages() {
        _messages.value = emptyList()
        chatHistoryRepository?.clearHistory()
    }
}
