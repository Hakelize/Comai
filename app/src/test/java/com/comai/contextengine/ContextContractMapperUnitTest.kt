package com.comai.contextengine

import com.comai.contextengine.contract.ContextContractMapper
import com.comai.contextengine.contract.ContextSignals
import com.comai.contextengine.contract.SharedContextContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextContractMapperUnitTest {

    @Test
    fun testSharedContextContractToContextInputMapping() {
        val contract = SharedContextContract(
            systemRole = "Comai Companion",
            userState = "awake",
            contextSignals = ContextSignals(
                time = "18:45",
                location = "Office",
                routineDeviation = true
            ),
            retrievedData = "Optional memory snippet",
            task = "OVERTIME_CHECKIN",
            constraints = "empathetic and concise"
        )

        val contextInput = ContextContractMapper.toContextInput(contract)

        assertNotNull("Mapped ContextInput must not be null", contextInput)
        assertEquals("Comai Companion", contextInput.systemRole)
        assertEquals("awake", contextInput.userState)
        assertEquals("18:45", contextInput.contextSignals.time)
        assertEquals("Office", contextInput.contextSignals.location)
        assertTrue(contextInput.contextSignals.routineDeviation)
        assertEquals("Optional memory snippet", contextInput.retrievedData)
        assertEquals("OVERTIME_CHECKIN", contextInput.task)
        assertEquals("empathetic and concise", contextInput.constraints)
    }

    @Test
    fun testNullRetrievedDataMappingHandledSafely() {
        val contract = SharedContextContract(
            systemRole = "Comai",
            userState = "awake",
            contextSignals = ContextSignals("08:00", "Home", false),
            retrievedData = null,
            task = "COMMUTE",
            constraints = "short"
        )

        val contextInput = ContextContractMapper.toContextInput(contract)

        assertNotNull(contextInput)
        assertEquals(null, contextInput.retrievedData)
        assertEquals("COMMUTE", contextInput.task)
    }
}
