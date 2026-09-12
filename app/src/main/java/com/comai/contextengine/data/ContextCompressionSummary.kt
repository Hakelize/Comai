package com.comai.contextengine.data

/**
 * Compact summary of recent historical context signals for low-overhead storage/processing.
 */
data class ContextCompressionSummary(
    val summaryText: String,
    val timeSpanMinutes: Int,
    val dominantActivity: String,
    val primaryLocation: String,
    val totalDeviationsCount: Int,
    val confidenceAverage: Double
)
