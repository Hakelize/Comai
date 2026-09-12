package com.comai.contextengine

import com.comai.context.ContextBridge
import com.comai.contextengine.contract.ContextSignals
import com.comai.contextengine.contract.SharedContextContract
import com.comai.contextengine.rules.EventPriority
import com.comai.contextengine.rules.RuleEngine
import com.comai.contextengine.rules.RuleEvaluationInput
import com.comai.contextengine.rules.RuleTriggerType
import com.comai.engine.MockEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RuleEngineUnitTest {

    private lateinit var ruleEngine: RuleEngine

    @Before
    fun setUp() {
        ruleEngine = RuleEngine()
        ruleEngine.loadDefaultRules()
    }

    @Test
    fun testRuleConditionSatisfied() {
        val input = RuleEvaluationInput(
            currentTime = "18:45",
            currentLocation = "Office",
            historicalOfficeDepartureTime = "17:30",
            isWorkDay = true
        )

        val result = ruleEngine.evaluate(input)

        assertNotNull("Rule must trigger when staying late at office past threshold", result)
        assertEquals(RuleTriggerType.OVERTIME_CHECKIN, result?.triggeredRule?.triggerType)
        assertEquals("OVERTIME_CHECKIN", result?.task)
    }

    @Test
    fun testRuleConditionNotSatisfied() {
        val input = RuleEvaluationInput(
            currentTime = "16:45",
            currentLocation = "Office",
            historicalOfficeDepartureTime = "17:30",
            isWorkDay = true
        )

        val result = ruleEngine.evaluate(input)

        assertNull("Rule must not trigger before departure time threshold", result)
    }

    @Test
    fun testCorrectProactiveEvent() {
        val input = RuleEvaluationInput(
            currentTime = "09:00",
            wakeTime = "08:45",
            baselineWakeTime = "07:00",
            isWorkDay = true
        )

        val result = ruleEngine.evaluate(input)

        assertNotNull(result)
        assertEquals(RuleTriggerType.WAKE_TIME_DEVIATION, result?.triggeredRule?.triggerType)
        assertEquals("MORNING_ROUTINE_REMINDER", result?.task)
    }

    @Test
    fun testEventPriority() {
        val input = RuleEvaluationInput(
            currentTime = "18:45",
            currentLocation = "Office",
            historicalOfficeDepartureTime = "17:30",
            isWorkDay = true
        )

        val result = ruleEngine.evaluate(input)
        assertNotNull(result)
        assertEquals(EventPriority.HIGH, result?.proactiveEvent?.priority)
    }

    @Test
    fun testDuplicatePreventionWithinCooldown() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val mockEngine = MockEngine()
        val testBridge = ContextBridge(mockEngine, TestScope(testDispatcher))

        val contract = SharedContextContract(
            systemRole = "Comai Proactive Engine",
            userState = "awake",
            contextSignals = ContextSignals("18:45", "Office", true),
            task = "OVERTIME_CHECKIN",
            constraints = "concise"
        )

        val nowMs = 100000L
        val firstTrigger = testBridge.dispatchDirect(contract, bypassCooldown = false, currentTimeMs = nowMs)
        assertNotNull("First trigger must succeed", firstTrigger)

        val duplicateTrigger = testBridge.dispatchDirect(contract, bypassCooldown = false, currentTimeMs = nowMs + (10 * 60 * 1000L))
        assertNull("Duplicate trigger within 60-minute cooldown must be suppressed", duplicateTrigger)
    }

    @Test
    fun test60MinuteCooldownEnforcement() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val mockEngine = MockEngine()
        val testBridge = ContextBridge(mockEngine, TestScope(testDispatcher))

        val contract = SharedContextContract(
            systemRole = "Comai Proactive Engine",
            userState = "awake",
            contextSignals = ContextSignals("18:45", "Office", true),
            task = "OVERTIME_CHECKIN",
            constraints = "concise"
        )

        val nowMs = 100000L
        testBridge.dispatchDirect(contract, bypassCooldown = false, currentTimeMs = nowMs)

        val at59Min = testBridge.dispatchDirect(contract, bypassCooldown = false, currentTimeMs = nowMs + (59 * 60 * 1000L))
        assertNull("Trigger at 59 minutes must still be suppressed by 60-min cooldown", at59Min)
    }

    @Test
    fun testEventAllowedAfterCooldown() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val mockEngine = MockEngine()
        val testBridge = ContextBridge(mockEngine, TestScope(testDispatcher))

        val contract = SharedContextContract(
            systemRole = "Comai Proactive Engine",
            userState = "awake",
            contextSignals = ContextSignals("18:45", "Office", true),
            task = "OVERTIME_CHECKIN",
            constraints = "concise"
        )

        val nowMs = 100000L
        testBridge.dispatchDirect(contract, bypassCooldown = false, currentTimeMs = nowMs)

        val at61Min = testBridge.dispatchDirect(contract, bypassCooldown = false, currentTimeMs = nowMs + (61 * 60 * 1000L))
        assertNotNull("Trigger after 60-minute cooldown must be allowed", at61Min)
    }
}
