package com.example.glucoseuploader

/**
 * Data class to represent a parsed glucose reading with date and time
 */
data class GlucoseData(
    val date: String,
    val time: String,
    val glucose: String
)