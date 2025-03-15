package com.example.glucoseuploader

import java.time.ZonedDateTime

/**
 * Data class to represent a parsed glucose reading with timestamp
 */
data class GlucoseReading(
    val value: Double,
    val timestamp: ZonedDateTime,
    val mealType: Int = androidx.health.connect.client.records.BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN
)