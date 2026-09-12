package com.comai.contextengine.contract

import com.comai.engine.MockEngine
import com.comai.engine.models.MockContextInputFactory
import kotlinx.coroutines.runBlocking

/**
 * Integration & Serialization Unit Tests for Context API Boundary.
 * Verifies:
 * 1. Serialization of SharedContextContract to exact JSON schema.
 * 2. Deserialization of valid JSON string into SharedContextContract.
 * 3. Safe rejection of malformed JSON strings throwing InvalidContractException.
 * 4. End-to-end pipeline flow: Providers -> ContextEngine -> SharedContextContract -> ContextInput -> AIEngine.
 */
object ContextApiBoundaryTest {

    fun runAllTests(): Boolean = runBlocking {
        var allPassed = true

        // Test 1: Serialization & Deserialization round-trip
        val originalContract = SharedContextContract(
            systemRole = "Comai Proactive Context Engine",
            userState = "BroadContext: OFFICE_LATE_DEPARTURE | Place: Office | Activity: STATIONARY | Audio: SPEAKER",
            contextSignals = ContextSignals(time = "19:30", location = "Office", routineDeviation = true),
            retrievedData = null,
            task = "OVERTIME_CHECKIN",
            constraints = "Keep response empathetic, concise (max 2 sentences)."
        )

        val jsonStr = ContractValidator.toJson(originalContract)
        val deserializedContract = ContractValidator.fromJson(jsonStr)

        val t1 = deserializedContract == originalContract
        println("Test 1 - Serialization/Deserialization Roundtrip: $t1")

        // Test 2: Safe rejection of malformed JSON (missing required field)
        var t2 = false
        try {
            val malformedJson = """
                {
                  "system_role": "",
                  "user_state": "Test",
                  "context_signals": { "time": "12:00", "location": "Home", "routine_deviation": false },
                  "task": "TEST",
                  "constraints": "Test"
                }
            """.trimIndent()
            ContractValidator.fromJson(malformedJson)
        } catch (e: InvalidContractException) {
            t2 = true
        }
        println("Test 2 - Safe Rejection of Malformed Contract: $t2")

        // Test 3: ContextContract -> ContextInput -> AIEngine pipeline test
        val contextInput = ContextContractMapper.toContextInput(originalContract)
        val mockAiEngine = MockEngine()
        val aiResponse = mockAiEngine.process(contextInput)

        val t3 = mockAiEngine.isReady() && aiResponse.displayText.isNotBlank()
        println("Test 3 - Pipeline Flow (ContextInput -> AIEngine): $t3")

        // Test 4: MockContextInputFactory validation
        val mockOvertime = MockContextInputFactory.createMockOvertimeInput()
        ContractValidator.validateContextInput(mockOvertime)
        val t4 = mockOvertime.task == "OVERTIME_CHECKIN"
        println("Test 4 - MockContextInputFactory Validation: $t4")

        allPassed = t1 && t2 && t3 && t4
        println("=== CONTEXT API BOUNDARY TEST RESULT: $allPassed ===")

        allPassed
    }
}
