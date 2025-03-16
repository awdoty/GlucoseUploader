package com.example.glucoseuploader

import java.time.ZonedDateTime

data class GlucoseReading(
    val value: Double,
    val dateTime: ZonedDateTime,
    val mealType: Int = androidx.health.connect.client.records.BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN
)