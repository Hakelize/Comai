package com.comai.contextengine.contract

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ContextSignals(
    @SerialName("time") val time: String,
    @SerialName("location") val location: String,
    @SerialName("routine_deviation") val routineDeviation: Boolean
)

@Serializable
data class SharedContextContract(
    @SerialName("system_role") val systemRole: String,
    @SerialName("user_state") val userState: String,
    @SerialName("context_signals") val contextSignals: ContextSignals,
    @SerialName("retrieved_data") val retrievedData: String? = null,
    @SerialName("task") val task: String,
    @SerialName("constraints") val constraints: String
)
