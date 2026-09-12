package com.comai.contextengine.models

/**
 * Comparison output contrasting current context against historical context patterns.
 */
data class HistoricalComparisonResult(
    val currentBroadTag: String,
    val historicalFrequencyPercentage: Float = 90.0f,
    val isConsistentWithHistory: Boolean = true,
    val historicalAverageDepartureMinutes: Int = 1050, // 17:30 PM
    val historicalWakeMinutes: Int = 420, // 07:00 AM
    val comparisonSummary: String = "Current context aligns with historical patterns."
)
