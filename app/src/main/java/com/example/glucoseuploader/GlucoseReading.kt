package com.example.glucoseuploader

import androidx.health.connect.client.records.BloodGlucoseRecord
import java.time.ZonedDateTime

/**
 * Data class representing a glucose reading from a CSV file
 */
data class GlucoseReading(
    val value: Double,         // Glucose value in mg/dL
    val dateTime: ZonedDateTime, // When the reading was taken
    val mealType: Int = BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN  // Relation to meal, using BloodGlucoseRecord constants
)