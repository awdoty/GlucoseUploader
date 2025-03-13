package com.example.glucoseuploader

/**
 * Data class to hold glucose statistics
 */
data class GlucoseStatistics(
    val averageGlucose: Double?,
    val minimumGlucose: Double?,
    val maximumGlucose: Double?,
    val readingCount: Long?,
    val period: String
)