package com.comai.contextengine.contract

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ContextSignals(
    @SerialName("time") @SerializedName("time") val time: String,
    @SerialName("location") @SerializedName("location") val location: String,
    @SerialName("routine_deviation") @SerializedName("routine_deviation") val routineDeviation: Boolean
)

@Serializable
data class SharedContextContract(
    @SerialName("system_role") @SerializedName("system_role") val systemRole: String,
    @SerialName("user_state") @SerializedName("user_state") val userState: String,
    @SerialName("context_signals") @SerializedName("context_signals") val contextSignals: ContextSignals,
    @SerialName("retrieved_data") @SerializedName("retrieved_data") val retrievedData: String? = null,
    @SerialName("task") @SerializedName("task") val task: String,
    @SerialName("constraints") @SerializedName("constraints") val constraints: String
)
