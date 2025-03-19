package com.example.glucoseuploader

import java.time.ZonedDateTime

/**
 * Data class representing a glucose reading with date and time
 */
data class GlucoseReading(
    val value: Double,
    val dateTime: ZonedDateTime,
    val mealType: Int = androidx.health.connect.client.records.BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN
)