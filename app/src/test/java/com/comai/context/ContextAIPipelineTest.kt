package com.comai.context

import com.comai.contextengine.context.UserState
import com.comai.contextengine.contract.ContractAssembler
import com.comai.contextengine.contract.SharedContextContract
import com.comai.contextengine.rules.RuleEngine
import com.comai.contextengine.rules.RuleEvaluationInput
import com.comai.contextengine.rules.RuleTriggerType
import com.comai.engine.AIEngine
import com.comai.engine.MockEngine
import com.comai.engine.models.AIResponse
import com.comai.engine.models.ContextInput
import com.comai.engine.models.ContextSignals
import com.comai.tts.TTSManager
import com.comai.ui.screens.chat.ChatViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Deterministic tests covering all 12 required verifications from Step 14:
 * 1. SharedContextContract -> ContextInput
 * 2. Normal context conversion
 * 3. Overtime context conversion
 * 4. MockEngine OVERTIME_CHECKIN
 * 5. MockEngine COMMUTE
 * 6. MockEngine MEMORY_RECALL
 * 7. Proactive event publication
 * 8. Duplicate event suppression
 * 9. AI failure recovery
 * 10. TTS failure recovery
 * 11. Manual chat still works
 * 12. Contextual response reaches presentation layer
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContextAIPipelineTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var ruleEngine: RuleEngine
    private lateinit var mockEngine: MockEngine
    private lateinit var contextBridge: ContextBridge

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        ruleEngine = RuleEngine()
        ruleEngine.loadDefaultMvpRules()
        mockEngine = MockEngine()
        contextBridge = ContextBridge(mockEngine, TestScope(testDispatcher))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // 1. SharedContextContract -> ContextInput conversion
    @Test
    fun test1_SharedContextContractToContextInputMapping() {
        val contract = SharedContextContract(
            systemRole = "Comai Companion",
            userState = "awake",
            contextSignals = com.comai.contextengine.contract.ContextSignals(
                time = "18:43",
                location = "Office",
                routineDeviation = true
            ),
            retrievedData = null,
            task = "OVERTIME_CHECKIN",
            constraints = "warm tone"
        )

        val input = ContextInputMapper.toContextInput(contract)
        assertEquals("Comai Companion", input.systemRole)
        assertEquals("awake", input.userState)
        assertEquals("18:43", input.contextSignals.time)
        assertEquals("Office", input.contextSignals.location)
        assertTrue(input.contextSignals.routineDeviation)
        assertEquals("OVERTIME_CHECKIN", input.task)
        assertEquals("warm tone", input.constraints)
    }

    // 2. Normal context conversion: Normal routine produces no deviation
    @Test
    fun test2_NormalContextConversionProducesNoEscalation() {
        val normalInput = RuleEvaluationInput(
            currentTime = "16:45",
            currentLocation = "Office",
            historicalOfficeDepartureTime = "17:30",
            isWorkDay = true
        )

        val result = ruleEngine.evaluate(normalInput)
        assertNull("Normal time before departure must not trigger routine deviation", result)
    }

    // 3. Overtime context conversion: Time 18:43 at Office triggers OVERTIME_CHECKIN
    @Test
    fun test3_OvertimeContextConversion() {
        val overtimeInput = RuleEvaluationInput(
            currentTime = "18:43",
            currentLocation = "Office",
            historicalOfficeDepartureTime = "17:30",
            isWorkDay = true
        )

        val result = ruleEngine.evaluate(overtimeInput)
        assertNotNull("Overtime past 45m threshold must trigger RuleEngine", result)
        assertEquals(RuleTriggerType.OVERTIME_CHECKIN, result?.triggeredRule?.triggerType)
        assertEquals("OVERTIME_CHECKIN", result?.task)
        assertTrue(result?.contextSignals?.routineDeviation == true)

        val userState = UserState(activityType = "STILL")
        val (contract, _) = ContractAssembler.assemble(result!!, userState)
        val contextInput = ContextInputMapper.toContextInput(contract)

        assertEquals("18:43", contextInput.contextSignals.time)
        assertEquals("Office", contextInput.contextSignals.location)
        assertTrue(contextInput.contextSignals.routineDeviation)
        assertEquals("OVERTIME_CHECKIN", contextInput.task)
    }

    // 4. MockEngine OVERTIME_CHECKIN response content
    @Test
    fun test4_MockEngineOvertimeCheckin() = runTest {
        val input = ContextInput(
            task = "OVERTIME_CHECKIN",
            contextSignals = ContextSignals(time = "18:43", location = "Office", routineDeviation = true)
        )

        val response = mockEngine.process(input)
        assertEquals("check_in", response.action)
        assertEquals("You're leaving later than usual today. How was work?", response.speech)
        assertEquals("You're leaving later than usual today. How was work?", response.displayText)
        assertEquals("overtime_checkin", response.metadata?.get("scenario"))
    }

    // 5. MockEngine COMMUTE
    @Test
    fun test5_MockEngineCommute() = runTest {
        val input = ContextInput(
            task = "COMMUTE",
            contextSignals = ContextSignals(time = "08:00", location = "Home")
        )

        val response = mockEngine.process(input)
        assertEquals("commute", response.action)
        assertTrue(response.speech.contains("traffic", ignoreCase = true) || response.speech.contains("leaving", ignoreCase = true))
    }

    // 6. MockEngine MEMORY_RECALL
    @Test
    fun test6_MockEngineMemoryRecall() = runTest {
        val input = ContextInput(
            task = "MEMORY_RECALL",
            contextSignals = ContextSignals(time = "10:00", location = "Office")
        )

        val response = mockEngine.process(input)
        assertEquals("memory_recall", response.action)
        assertTrue(response.displayText.contains("remember", ignoreCase = true))
    }

    // 7. Proactive event publication
    @Test
    fun test7_ProactiveEventPublication() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val testBridge = ContextBridge(mockEngine, TestScope(testDispatcher))

        val received = mutableListOf<AIResponse>()
        val job = backgroundScope.launch(testDispatcher) {
            testBridge.proactiveEvents.collect { received.add(it) }
        }

        val contract = SharedContextContract(
            systemRole = "Comai",
            userState = "active",
            contextSignals = com.comai.contextengine.contract.ContextSignals("18:43", "Office", true),
            task = "OVERTIME_CHECKIN",
            constraints = "short"
        )

        testBridge.onContextEscalated("{}", contract)
        testScheduler.advanceTimeBy(1500)

        assertEquals(1, received.size)
        assertEquals("check_in", received[0].action)
        job.cancel()
    }

    // 8. Duplicate event suppression via cooldown
    @Test
    fun test8_DuplicateEventSuppression() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val testBridge = ContextBridge(mockEngine, TestScope(testDispatcher))

        val contract = SharedContextContract(
            systemRole = "Comai",
            userState = "active",
            contextSignals = com.comai.contextengine.contract.ContextSignals("18:43", "Office", true),
            task = "OVERTIME_CHECKIN",
            constraints = "short"
        )

        val now = 100000L
        val first = testBridge.dispatchDirect(contract, bypassCooldown = false, currentTimeMs = now)
        assertNotNull("First trigger must succeed", first)

        // Immediate duplicate at now + 1 min (within 60m cooldown)
        val duplicate = testBridge.dispatchDirect(contract, bypassCooldown = false, currentTimeMs = now + 60000L)
        assertNull("Duplicate trigger within cooldown must be suppressed", duplicate)

        // Trigger after 61 minutes
        val afterCooldown = testBridge.dispatchDirect(contract, bypassCooldown = false, currentTimeMs = now + (61 * 60 * 1000L))
        assertNotNull("Trigger after cooldown must succeed", afterCooldown)
    }

    // 9. AI failure recovery: try-catch-finally resets isTyping and posts fallback
    @Test
    fun test9_AIFailureRecovery() = runTest {
        val failingEngine = object : AIEngine {
            override suspend fun process(context: ContextInput): AIResponse {
                throw RuntimeException("Simulated hardware NPU inference failure")
            }
            override fun isReady(): Boolean = true
            override fun engineName(): String = "FailingEngine"
        }

        val testTTS = TTSManager(null)
        val viewModel = ChatViewModel(failingEngine, testTTS, contextBridge)

        viewModel.sendMessage("Test failure recovery")
        testScheduler.advanceUntilIdle()

        // Typing must be reset
        assertFalse("isTyping must reset to false on failure", viewModel.isTyping.value)

        // Messages must contain safe fallback
        val lastMessage = viewModel.messages.value.lastOrNull()
        assertNotNull(lastMessage)
        assertEquals("error", lastMessage?.action)
        assertEquals("Sorry, I couldn't process that right now.", lastMessage?.content)
    }

    // 10. TTS failure recovery: UI still displays response even if TTS throws
    @Test
    fun test10_TTSFailureRecovery() = runTest {
        val throwingTTS = object : TTSManager(null) {
            override fun speak(text: String): Boolean {
                throw RuntimeException("TTS audio track crashed")
            }
        }

        val viewModel = ChatViewModel(mockEngine, throwingTTS, contextBridge)

        viewModel.sendMessage("Hello Comai")
        testScheduler.advanceTimeBy(1500)

        // UI still receives the AIResponse
        val lastMessage = viewModel.messages.value.lastOrNull()
        assertNotNull(lastMessage)
        assertFalse(lastMessage?.isFromUser == true)
        assertTrue(lastMessage?.content?.isNotBlank() == true)
    }

    // 11. Manual chat still works independently
    @Test
    fun test11_ManualChatStillWorks() = runTest {
        val testTTS = TTSManager(null)
        val viewModel = ChatViewModel(mockEngine, testTTS, contextBridge)

        viewModel.sendMessage("Where is traffic heavy?")
        testScheduler.advanceTimeBy(1500)

        val messages = viewModel.messages.value
        assertTrue("Expected user message and AI response", messages.size >= 2)
        assertEquals("Where is traffic heavy?", messages[messages.size - 2].content)
        assertTrue(messages[messages.size - 2].isFromUser)

        val aiMessage = messages.last()
        assertFalse(aiMessage.isFromUser)
        assertEquals("commute", aiMessage.action)
    }

    // 12. Contextual response reaches presentation layer
    @Test
    fun test12_ContextualResponseReachesPresentationLayer() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val testBridge = ContextBridge(mockEngine, TestScope(testDispatcher))
        val testTTS = TTSManager(null)

        val viewModel = ChatViewModel(mockEngine, testTTS, testBridge)

        val overtimeContract = SharedContextContract(
            systemRole = "Comai Proactive Context Engine",
            userState = "awake",
            contextSignals = com.comai.contextengine.contract.ContextSignals("18:43", "Office", true),
            task = "OVERTIME_CHECKIN",
            constraints = "empathetic"
        )

        // Simulate context escalation from foreground service
        testBridge.onContextEscalated("{}", overtimeContract)
        testScheduler.advanceTimeBy(1500)

        val messages = viewModel.messages.value
        val proactiveMessage = messages.lastOrNull { !it.isFromUser && it.action == "check_in" }
        assertNotNull("Proactive check-in message must reach ChatViewModel messages list", proactiveMessage)
        assertEquals("You're leaving later than usual today. How was work?", proactiveMessage?.content)
    }
}
