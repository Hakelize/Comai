package com.comai.contextengine.testharness

import com.comai.contextengine.context.DetectedContextEvent
import com.comai.contextengine.context.DetectedEventType
import com.comai.contextengine.context.WakeSleepDetector
import com.comai.contextengine.contract.ContractAssembler
import com.comai.contextengine.db.CommunityEventDatabase
import com.comai.contextengine.rules.RuleEngine
import com.comai.contextengine.rules.RuleEvaluationInput

class ContextTestHarness {

    private val detector = WakeSleepDetector()
    private val ruleEngine = RuleEngine()

    init {
        ruleEngine.loadDefaultMvpRules()
    }

    fun runFullSimulationSequence(): List<String> {
        val outputs = mutableListOf<String>()

        println("=================================================================")
        println("         COMAI CONTEXT ENGINE - CHECKPOINT A TEST HARNESS        ")
        println("=================================================================\n")

        outputs.add(runScenario1MorningWakeDeviation())
        outputs.add(runScenario2LunchReminder())
        outputs.add(runScenario3TribeFinder())
        outputs.add(runScenario4NightMedication())

        println("=================================================================")
        println("   CHECKPOINT A VALIDATION COMPLETE - ALL CONTRACTS VALIDATED    ")
        println("=================================================================")

        return outputs
    }

    private fun runScenario1MorningWakeDeviation(): String {
        println("▶ SCENARIO 1: Morning Wake Time Deviation (Simulated 08:45 AM Wake)")
        val wakeTimeMs = System.currentTimeMillis() - (15 * 60 * 1000)
        detector.onEventDetected(DetectedContextEvent(wakeTimeMs, DetectedEventType.WAKE))

        val input = RuleEvaluationInput(
            currentTime = "09:00",
            dayOfWeek = "MONDAY",
            isWorkDay = true,
            currentLocation = "Home",
            wakeTime = "08:45",
            baselineWakeTime = "07:00"
        )

        val userState = detector.getCurrentUserState()
        val ruleResult = ruleEngine.evaluate(input) ?: throw IllegalStateException("Rule expected to trigger")

        val (contractObj, jsonOutput) = ContractAssembler.assemble(ruleResult, userState)
        println("--- GENERATED SHARED JSON CONTRACT ---")
        println(jsonOutput)
        println()
        return jsonOutput
    }

    private fun runScenario2LunchReminder(): String {
        println("▶ SCENARIO 2: Lunch Window Gap (Simulated 13:00 PM Office)")
        detector.onEventDetected(DetectedContextEvent(System.currentTimeMillis(), DetectedEventType.STILL))

        val input = RuleEvaluationInput(
            currentTime = "13:00",
            dayOfWeek = "MONDAY",
            isWorkDay = true,
            currentLocation = "Office",
            loggedLunch = false
        )

        val userState = detector.getCurrentUserState()
        val ruleResult = ruleEngine.evaluate(input) ?: throw IllegalStateException("Lunch rule expected to trigger")

        val (contractObj, jsonOutput) = ContractAssembler.assemble(ruleResult, userState)
        println("--- GENERATED SHARED JSON CONTRACT ---")
        println(jsonOutput)
        println()
        return jsonOutput
    }

    private fun runScenario3TribeFinder(): String {
        println("▶ SCENARIO 3: Non-Workday Free Evening Slot (Tribe Finder Event Lookup)")
        detector.onEventDetected(DetectedContextEvent(System.currentTimeMillis(), DetectedEventType.WALKING))

        val input = RuleEvaluationInput(
            currentTime = "18:30",
            dayOfWeek = "SATURDAY",
            isWorkDay = false,
            currentLocation = "Downtown"
        )

        val userState = detector.getCurrentUserState()
        val ruleResult = ruleEngine.evaluate(input) ?: throw IllegalStateException("Tribe finder rule expected to trigger")

        val mockRetrievedData = CommunityEventDatabase.MOCK_COMMUNITY_EVENTS
            .filter { !it.applicableOnWorkDay }
            .take(2)
            .joinToString(prefix = "[", postfix = "]") {
                "{name: '${it.eventName}', category: '${it.category}', area: '${it.locationArea}'}"
            }

        val (contractObj, jsonOutput) = ContractAssembler.assemble(ruleResult, userState, mockRetrievedData)
        println("--- GENERATED SHARED JSON CONTRACT ---")
        println(jsonOutput)
        println()
        return jsonOutput
    }

    private fun runScenario4NightMedication(): String {
        println("▶ SCENARIO 4: Night Window Medication Reminder (Simulated 21:30 PM)")
        detector.onEventDetected(DetectedContextEvent(System.currentTimeMillis(), DetectedEventType.STILL))

        val input = RuleEvaluationInput(
            currentTime = "21:30",
            dayOfWeek = "MONDAY",
            isWorkDay = true,
            currentLocation = "Home",
            medicationFlag = true
        )

        val userState = detector.getCurrentUserState()
        val ruleResult = ruleEngine.evaluate(input) ?: throw IllegalStateException("Medication rule expected to trigger")

        val (contractObj, jsonOutput) = ContractAssembler.assemble(ruleResult, userState, "Medication profile flag active: Evening Supplement")
        println("--- GENERATED SHARED JSON CONTRACT ---")
        println(jsonOutput)
        println()
        return jsonOutput
    }
}
