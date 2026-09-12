package com.comai.engine.models

import com.google.gson.annotations.SerializedName

/**
 * Input to the AI engine — matches the shared JSON contract.
 * Produced by the Context Engine (Ram) or manually via Developer Dashboard overrides (Rakesh).
 */
data class ContextInput(
    @SerializedName("system_role")
    val systemRole: String = "Comai — a warm, proactive AI life companion",

    @SerializedName("user_state")
    val userState: String = "",

    @SerializedName("context_signals")
    val contextSignals: ContextSignals = ContextSignals(),

    @SerializedName("retrieved_data")
    val retrievedData: String? = null,

    val task: String = "",

    val constraints: String = "under 30 words, warm tone"
)

data class ContextSignals(
    val time: String = "",
    val location: String = "home",

    @SerializedName("routine_deviation")
    val routineDeviation: Boolean = false
)
