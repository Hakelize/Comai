package com.comai.contextengine

import com.comai.contextengine.contract.ContractValidator
import com.comai.contextengine.contract.ContextSignals
import com.comai.contextengine.contract.SharedContextContract
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedContextContractUnitTest {

    @Test
    fun testGeneratedJsonFollowsExactContractStructure() {
        val contract = SharedContextContract(
            systemRole = "Comai Proactive Context Engine",
            userState = "awake",
            contextSignals = ContextSignals(
                time = "18:43",
                location = "Office",
                routineDeviation = true
            ),
            retrievedData = null,
            task = "OVERTIME_CHECKIN",
            constraints = "Keep response empathetic, concise (max 2 sentences)."
        )

        // Validate via ContractValidator
        ContractValidator.validate(contract)
        val jsonStr = ContractValidator.toJson(contract)

        // Parse JSON AST to verify exact key structure
        val root = JsonParser.parseString(jsonStr).asJsonObject

        assertTrue("Root must contain system_role", root.has("system_role"))
        assertTrue("Root must contain user_state", root.has("user_state"))
        assertTrue("Root must contain context_signals", root.has("context_signals"))
        assertTrue("Root must contain retrieved_data", root.has("retrieved_data"))
        assertTrue("Root must contain task", root.has("task"))
        assertTrue("Root must contain constraints", root.has("constraints"))

        val signals = root.getAsJsonObject("context_signals")
        assertTrue("context_signals must contain time", signals.has("time"))
        assertTrue("context_signals must contain location", signals.has("location"))
        assertTrue("context_signals must contain routine_deviation", signals.has("routine_deviation"))

        assertEquals("Comai Proactive Context Engine", root.get("system_role").asString)
        assertEquals("awake", root.get("user_state").asString)
        assertEquals("18:43", signals.get("time").asString)
        assertEquals("Office", signals.get("location").asString)
        assertEquals(true, signals.get("routine_deviation").asBoolean)
        assertEquals("OVERTIME_CHECKIN", root.get("task").asString)
    }

    @Test
    fun testSerializationAndDeserializationRoundtrip() {
        val original = SharedContextContract(
            systemRole = "Comai Companion",
            userState = "active",
            contextSignals = ContextSignals(time = "08:00", location = "Home", routineDeviation = false),
            retrievedData = "Sample data",
            task = "MORNING_ROUTINE_REMINDER",
            constraints = "concise tone"
        )
        val jsonStr = ContractValidator.toJson(original)

        val deserialized = ContractValidator.fromJson(jsonStr)

        assertNotNull(deserialized)
        assertEquals(original.systemRole, deserialized.systemRole)
        assertEquals(original.userState, deserialized.userState)
        assertEquals(original.contextSignals.time, deserialized.contextSignals.time)
        assertEquals(original.contextSignals.location, deserialized.contextSignals.location)
        assertEquals(original.contextSignals.routineDeviation, deserialized.contextSignals.routineDeviation)
        assertEquals(original.task, deserialized.task)
        assertEquals(original.constraints, deserialized.constraints)
    }
}
