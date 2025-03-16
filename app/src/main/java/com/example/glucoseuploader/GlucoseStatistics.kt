package com.example.glucoseuploader

/**
 * Data class to represent statistics for glucose readings
 */
data class GlucoseStatistics(
    val averageGlucose: Double?,
    val minimumGlucose: Double?,
    val maximumGlucose: Double?,
    val readingCount: Long,
    val period: String
)