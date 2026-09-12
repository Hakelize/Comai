package com.comai.voice

import com.comai.engine.MockEngine
import com.comai.tts.TTSManager
import com.comai.ui.screens.chat.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class MultilingualVoiceTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeTTSManager : TTSManager(null) {
        var spokenTexts = mutableListOf<String>()
        var currentLocale: Locale = Locale.US

        override fun speak(text: String): Boolean {
            spokenTexts.add(text)
            return true
        }

        override fun setLanguage(locale: Locale): Boolean {
            currentLocale = locale
            return true
        }

        override fun stop() {}
    }

    // ─── Test 1: Language Enum & Locales ──────────────────────────────
    @Test
    fun testLanguageEnumSupport() {
        val languages = ComaiLanguage.entries
        assertEquals(6, languages.size)

        val english = ComaiLanguage.ENGLISH
        assertEquals("English", english.displayName)
        assertEquals("en-IN", english.sttTag)
        assertTrue(english.altLocales.isEmpty())

        val tamil = ComaiLanguage.TAMIL
        assertEquals("தமிழ்", tamil.displayName)
        assertEquals("ta-IN", tamil.sttTag)
        assertTrue(tamil.altLocales.contains("en-IN"))

        val telugu = ComaiLanguage.TELUGU
        assertEquals("తెలుగు", telugu.displayName)
        assertEquals("te-IN", telugu.sttTag)

        val hindi = ComaiLanguage.HINDI
        assertEquals("हिन्दी", hindi.displayName)
        assertEquals("hi-IN", hindi.sttTag)

        val malayalam = ComaiLanguage.MALAYALAM
        assertEquals("മലയാളം", malayalam.displayName)
        assertEquals("ml-IN", malayalam.sttTag)

        val tanglish = ComaiLanguage.TANGLISH
        assertEquals("Tanglish", tanglish.displayName)
        assertEquals("en-IN", tanglish.sttTag)
        assertTrue(tanglish.altLocales.contains("ta-IN"))
    }

    // ─── Test 2: TextNormalizer for Tanglish & Multilingual ────────────
    @Test
    fun testTextNormalizerEnglishPassThrough() {
        val normalizer = TextNormalizer()
        val input = "I usually leave office at six"
        val result = normalizer.normalize(input, ComaiLanguage.ENGLISH)
        assertEquals(input, result)
    }

    @Test
    fun testTextNormalizerTanglishTransliteration() {
        val normalizer = TextNormalizer()
        val tanglishInput = "Naan usually six manikku office-la irundhu kelambuven"
        val normalized = normalizer.normalize(tanglishInput, ComaiLanguage.TANGLISH)

        // Must preserve original text and append recognized keywords
        assertTrue(normalized.startsWith(tanglishInput))
        assertTrue(normalized.contains("office") || normalized.contains("work"))
        assertTrue(normalized.contains("leaving"))
    }

    @Test
    fun testTextNormalizerTamilScript() {
        val normalizer = TextNormalizer()
        val tamilInput = "காலை அலுவலகம் கிளம்புறேன்"
        val normalized = normalizer.normalize(tamilInput, ComaiLanguage.TAMIL)

        assertTrue(normalized.startsWith(tamilInput))
        assertTrue(normalized.contains("morning"))
        assertTrue(normalized.contains("office") || normalized.contains("work"))
        assertTrue(normalized.contains("leaving"))
    }

    @Test
    fun testTextNormalizerTamilFoodAndMedicine() {
        val normalizer = TextNormalizer()

        val foodInput = "saapadu time aachu"
        val foodNormalized = normalizer.normalize(foodInput, ComaiLanguage.TANGLISH)
        assertTrue(foodNormalized.contains("food") || foodNormalized.contains("lunch"))

        val medInput = "marundu eduthukanum"
        val medNormalized = normalizer.normalize(medInput, ComaiLanguage.TANGLISH)
        assertTrue(medNormalized.contains("medication") || medNormalized.contains("medicine"))
    }

    @Test
    fun testTextNormalizerHindiInput() {
        val normalizer = TextNormalizer()

        val hindiInput = "subah office nikalna hai"
        val normalized = normalizer.normalize(hindiInput, ComaiLanguage.HINDI)
        assertTrue(normalized.contains("morning"))
        assertTrue(normalized.contains("office") || normalized.contains("work"))
        assertTrue(normalized.contains("leaving"))
    }

    @Test
    fun testTextNormalizerNoKeywordFallback() {
        val normalizer = TextNormalizer()
        val unknownText = "xyzabc random text"
        val normalized = normalizer.normalize(unknownText, ComaiLanguage.TAMIL)
        // Returns original unchanged
        assertEquals(unknownText, normalized)
    }

    // ─── Test 3: End-to-End Multilingual Flow in ChatViewModel ────────
    @Test
    fun testChatViewModelTanglishFlow() = runTest {
        val tts = FakeTTSManager()
        val engine = MockEngine()
        val normalizer = TextNormalizer()

        val chatViewModel = ChatViewModel(
            aiEngine = engine,
            ttsManager = tts,
            contextBridge = null,
            memoryRepository = null,
            textNormalizer = normalizer
        )

        chatViewModel.setLanguage(ComaiLanguage.TANGLISH)

        val tanglishSpeech = "Naan office-la irundhu kelamburen"
        chatViewModel.sendMessage(tanglishSpeech)
        advanceUntilIdle()

        val messages = chatViewModel.messages.value
        assertEquals(3, messages.size)

        // 1. User chat bubble MUST contain original un-modified Tanglish speech
        assertEquals(tanglishSpeech, messages[1].content)
        assertTrue(messages[1].isFromUser)

        // 2. AI Engine response was triggered via normalized keywords matching leaving/evening intent
        val aiMessage = messages[2]
        assertFalse(aiMessage.isFromUser)
        assertEquals("check_in", aiMessage.action)

        // 3. TTS spoken response delivered
        assertEquals(1, tts.spokenTexts.size)
        assertTrue(tts.spokenTexts[0].isNotBlank())
    }

    @Test
    fun testChatViewModelTamilScriptFlow() = runTest {
        val tts = FakeTTSManager()
        val engine = MockEngine()
        val normalizer = TextNormalizer()

        val chatViewModel = ChatViewModel(
            aiEngine = engine,
            ttsManager = tts,
            contextBridge = null,
            memoryRepository = null,
            textNormalizer = normalizer
        )

        chatViewModel.setLanguage(ComaiLanguage.TAMIL)

        val tamilSpeech = "மருந்து எடுக்க வேண்டும்"
        chatViewModel.sendMessage(tamilSpeech)
        advanceUntilIdle()

        val messages = chatViewModel.messages.value
        assertEquals(3, messages.size)

        // Original script preserved in bubble
        assertEquals(tamilSpeech, messages[1].content)
        assertEquals("medication", messages[2].action)
        assertEquals(1, tts.spokenTexts.size)
    }
}
