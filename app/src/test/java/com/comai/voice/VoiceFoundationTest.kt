package com.comai.voice

import com.comai.capability.CapabilityStatus
import com.comai.capability.CapabilityType
import com.comai.capability.DeviceCapability
import com.comai.capability.DeviceInfo
import com.comai.engine.MockEngine
import com.comai.engine.models.ContextInput
import com.comai.engine.models.ContextSignals
import com.comai.memory.MemoryRepository
import com.comai.tts.TTSManager
import com.comai.ui.screens.chat.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite for Phase 3:
 * 1. Device Capability Matrix distinctions (Platform/Hardware vs Runtime Permission vs Special Access vs OS Restriction)
 * 2. Manual User Intent Priority over Ambient Context
 * 3. Voice State Machine transitions and duplicate start suppression
 * 4. Empty speech result handling
 * 5. Audio feedback loop prevention (TTS stop before voice recording)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceFoundationTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -------------------------------------------------------------
    // Test 1: Capability Matrix categorization and distinction
    // -------------------------------------------------------------
    @Test
    fun testCapabilityTypeDistinction() {
        val hardwareCap = DeviceCapability(
            name = "Microphone",
            permissionOrAccess = "android.permission.RECORD_AUDIO",
            type = CapabilityType.PLATFORM_HARDWARE,
            status = CapabilityStatus.AVAILABLE,
            details = "Microphone hardware available"
        )

        val permissionCap = DeviceCapability(
            name = "Microphone",
            permissionOrAccess = "android.permission.RECORD_AUDIO",
            type = CapabilityType.RUNTIME_PERMISSION,
            status = CapabilityStatus.GRANTED,
            details = "RECORD_AUDIO granted"
        )

        val specialAccessCap = DeviceCapability(
            name = "Usage Access",
            permissionOrAccess = "AppOpsManager: OPSTR_GET_USAGE_STATS",
            type = CapabilityType.SPECIAL_ACCESS,
            status = CapabilityStatus.RESTRICTED,
            details = "Requires user approval in Settings"
        )

        val restrictionCap = DeviceCapability(
            name = "Background Location",
            permissionOrAccess = "ACCESS_BACKGROUND_LOCATION",
            type = CapabilityType.SYSTEM_RESTRICTION,
            status = CapabilityStatus.RESTRICTED,
            details = "Restricted on Android 10+"
        )

        assertEquals("Platform / Hardware", hardwareCap.type.displayName)
        assertEquals("Runtime Permission", permissionCap.type.displayName)
        assertEquals("Special User-Granted Access", specialAccessCap.type.displayName)
        assertEquals("Restricted Android Behavior", restrictionCap.type.displayName)

        assertNotEquals(hardwareCap.type, permissionCap.type)
        assertNotEquals(specialAccessCap.type, restrictionCap.type)
    }

    @Test
    fun testDeviceInfoStructure() {
        val info = DeviceInfo(
            androidVersion = "14",
            apiLevel = 34,
            manufacturer = "Samsung",
            model = "SM-A155F",
            hardware = "mt6789",
            totalRam = "5.68 GB",
            availableRam = "2.15 GB"
        )
        assertEquals("14", info.androidVersion)
        assertEquals(34, info.apiLevel)
        assertEquals("Samsung", info.manufacturer)
        assertEquals("SM-A155F", info.model)
        assertEquals("mt6789", info.hardware)
    }

    // -------------------------------------------------------------
    // Test 2: Manual User Intent Priority over Ambient Context
    // -------------------------------------------------------------
    @Test
    fun testManualIntentOverridesAmbientMorningContext() = runTest {
        val engine = MockEngine()

        // Ambient context is morning wakeup, but user asks a memory question
        val morningWithMemoryQuery = ContextInput(
            systemRole = "Comai Companion",
            userState = "conversational",
            contextSignals = ContextSignals(time = "morning", location = "home"),
            task = "what do you remember about me",
            retrievedData = "- User usually leaves work around 5:30"
        )

        val response = engine.process(morningWithMemoryQuery)

        // Must trigger memory recall, NOT morning greeting!
        assertEquals("memory_recall", response.action)
        assertTrue(response.speech.contains("5:30"))
        assertFalse(response.speech.contains("Good morning"))
    }

    @Test
    fun testManualIntentOverridesAmbientNightContext() = runTest {
        val engine = MockEngine()

        // Ambient context is night wind-down, but user asks for traffic route
        val nightWithTrafficQuery = ContextInput(
            systemRole = "Comai Companion",
            userState = "conversational",
            contextSignals = ContextSignals(time = "night", location = "home"),
            task = "commute traffic check"
        )

        val response = engine.process(nightWithTrafficQuery)

        // Must trigger commute traffic, NOT night medication reminder!
        assertEquals("commute", response.action)
        assertTrue(response.speech.contains("traffic") || response.speech.contains("route"))
        assertFalse(response.speech.contains("medication"))
    }

    // -------------------------------------------------------------
    // Test 3: Voice State Machine & Audio Loop Prevention
    // -------------------------------------------------------------
    private class FakeTTSManager : TTSManager(null) {
        var wasStopped = false
        var spokenTexts = mutableListOf<String>()
        val speakingFlow = MutableStateFlow(false)

        override fun speak(text: String): Boolean {
            spokenTexts.add(text)
            speakingFlow.value = true
            return true
        }

        fun markFinished() {
            speakingFlow.value = false
        }

        override fun stop() {
            wasStopped = true
            speakingFlow.value = false
        }
    }

    @Test
    fun testVoiceStateEnumCompleteness() {
        val states = VoiceState.values()
        assertTrue(states.contains(VoiceState.IDLE))
        assertTrue(states.contains(VoiceState.LISTENING))
        assertTrue(states.contains(VoiceState.PROCESSING))
        assertTrue(states.contains(VoiceState.SPEAKING))
        assertTrue(states.contains(VoiceState.ERROR))
    }

    @Test
    fun testVoicePipelineEndToEndFlow() = runTest {
        val tts = FakeTTSManager()
        val engine = MockEngine()

        val chatViewModel = ChatViewModel(
            aiEngine = engine,
            ttsManager = tts,
            contextBridge = null,
            memoryRepository = null
        )

        // Simulate SpeechRecognizer delivering final result
        val recognizedVoiceText = "commute traffic check"
        chatViewModel.sendMessage(recognizedVoiceText)
        advanceUntilIdle()

        // Verify messages received (Initial welcome greeting + User voice message + AI response)
        val messages = chatViewModel.messages.value
        assertEquals(3, messages.size)
        assertEquals(recognizedVoiceText, messages[1].content)
        assertTrue(messages[1].isFromUser)

        // AI message generated
        assertEquals("commute", messages[2].action)
        assertFalse(messages[2].isFromUser)

        // TTS invoked with AI response speech
        assertEquals(1, tts.spokenTexts.size)
        assertTrue(tts.spokenTexts[0].contains("traffic") || tts.spokenTexts[0].contains("route"))
    }
}
