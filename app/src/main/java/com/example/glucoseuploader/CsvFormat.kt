package com.example.glucoseuploader

/**
 * Enum class representing different CSV formats
 */
enum class CsvFormat {
    UNKNOWN,
    DEXCOM,
    LIBRE,
    CONTOUR,
    ONETOUCH,
    GENERIC,
    CUSTOM
}