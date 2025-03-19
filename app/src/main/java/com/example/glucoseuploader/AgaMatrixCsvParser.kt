package com.example.glucoseuploader

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Specialized parser for AgaMatrix CSV format
 */
class AgaMatrixCsvParser(private val context: Context) {
    private val tag = "AgaMatrixCsvParser"

    /**
     * Parse a CSV file from AgaMatrix
     */
    suspend fun parseAgaMatrixCsv(uri: Uri): List<GlucoseReading> = withContext(Dispatchers.IO) {
        val readings = mutableListOf<GlucoseReading>()

        try {
            // Read the file content
            val csvLines = readCsvFile(uri)

            // Log details for debugging
            Log.d(tag, "Read ${csvLines.size} lines from CSV file")
            if (csvLines.isNotEmpty()) {
                Log.d(tag, "First line: ${csvLines.first()}")
            }

            // Find the header row (usually contains "Date", "Time", "Glucose")
            var headerIndex = findHeaderIndex(csvLines)
            if (headerIndex == -1) {
                Log.w(tag, "Could not find header row in AgaMatrix CSV, using first row as header")
                headerIndex = 0
            }

            if (headerIndex >= csvLines.size) {
                Log.e(tag, "Header index out of bounds")
                return@withContext readings
            }

            // Parse the header to identify column positions
            val headerRow = csvLines[headerIndex].split(",").map { it.trim() }
            Log.d(tag, "Header row: $headerRow")

            val dateIndex = headerRow.indexOfFirst {
                it.contains("Date", ignoreCase = true) && !it.contains("Time", ignoreCase = true)
            }
            val timeIndex = headerRow.indexOfFirst {
                it.contains("Time", ignoreCase = true) || it.contains("Clock", ignoreCase = true)
            }
            val glucoseIndex = headerRow.indexOfFirst {
                it.contains("Glucose", ignoreCase = true) || it.contains("Reading", ignoreCase = true)
            }

            Log.d(tag, "Column indices: date=$dateIndex, time=$timeIndex, glucose=$glucoseIndex")

            if (dateIndex == -1 || timeIndex == -1 || glucoseIndex == -1) {
                // Try a more flexible approach
                Log.w(tag, "Missing standard columns, trying alternative approach")
                return@withContext parseAlternativeFormat(csvLines)
            }

            // Process data rows
            for (i in (headerIndex + 1) until csvLines.size) {
                try {
                    val row = csvLines[i].split(",").map { it.trim().replace("\"", "") }

                    if (row.size <= maxOf(dateIndex, timeIndex, glucoseIndex)) {
                        continue // Skip rows with insufficient columns
                    }

                    val dateStr = row[dateIndex]
                    val timeStr = row[timeIndex]
                    val glucoseStr = row[glucoseIndex]

                    // Extract glucose value (remove any units like "mg/dL")
                    val glucoseValue = extractNumericValue(glucoseStr)

                    if (glucoseValue > 0) {
                        // Parse date/time
                        val dateTime = parseDateTime(dateStr, timeStr)
                        if (dateTime != null) {
                            readings.add(GlucoseReading(
                                value = glucoseValue,
                                dateTime = dateTime
                            ))
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error parsing row ${csvLines[i]}", e)
                    // Continue with next row
                }
            }

            Log.d(tag, "Successfully parsed ${readings.size} glucose readings")
            readings
        } catch (e: Exception) {
            Log.e(tag, "Error parsing AgaMatrix CSV", e)
            readings
        }
    }

    /**
     * Find the header row index in the CSV
     */
    private fun findHeaderIndex(csvLines: List<String>): Int {
        for (i in csvLines.indices) {
            val line = csvLines[i].lowercase(Locale.ROOT)
            if ((line.contains("date") && (line.contains("time") || line.contains("clock"))) ||
                line.contains("glucose")) {
                return i
            }
        }
        return -1
    }

    /**
     * Parse CSV in a more flexible way if standard format is not found
     */
    private fun parseAlternativeFormat(csvLines: List<String>): List<GlucoseReading> {
        val readings = mutableListOf<GlucoseReading>()

        // Try to identify data rows and extract values
        for (line in csvLines) {
            try {
                // Split by comma
                val parts = line.split(",").map { it.trim() }

                // Look for patterns in each part
                var dateStr = ""
                var timeStr = ""
                var glucoseValue = 0.0

                for (part in parts) {
                    when {
                        // Date patterns like MM/DD/YYYY or DD-MM-YYYY
                        part.matches(Regex("\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}")) -> {
                            dateStr = part
                        }
                        // Time patterns like HH:MM or HH:MM:SS
                        part.matches(Regex("\\d{1,2}:\\d{2}(:\\d{2})?( ?[APap][Mm])?")) -> {
                            timeStr = part
                        }
                        // Number that could be glucose value
                        part.matches(Regex("\\d+(\\.\\d+)?")) -> {
                            val value = part.toDoubleOrNull() ?: 0.0
                            // Only consider as glucose if in reasonable range (40-600 mg/dL)
                            if (value in 40.0..600.0) {
                                glucoseValue = value
                            }
                        }
                        // Value with mg/dL unit
                        part.contains("mg/dL", ignoreCase = true) -> {
                            val value = extractNumericValue(part)
                            if (value > 0) {
                                glucoseValue = value
                            }
                        }
                    }
                }

                // If we found both date and glucose value
                if (dateStr.isNotEmpty() && glucoseValue > 0) {
                    val dateTime = parseDateTime(dateStr, timeStr)
                    if (dateTime != null) {
                        readings.add(GlucoseReading(
                            value = glucoseValue,
                            dateTime = dateTime
                        ))
                    }
                }
            } catch (e: Exception) {
                // Skip problematic lines
                continue
            }
        }

        return readings
    }

    /**
     * Read raw CSV file content
     */
    private suspend fun readCsvFile(uri: Uri): List<String> = withContext(Dispatchers.IO) {
        val lines = mutableListOf<String>()

        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        line?.let {
                            if (it.isNotBlank()) {
                                lines.add(it)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error reading CSV file", e)
        }

        lines
    }

    /**
     * Parse date and time strings into ZonedDateTime
     */
    private fun parseDateTime(dateStr: String, timeStr: String): ZonedDateTime? {
        // Common date formats in AgaMatrix exports
        val dateFormats = listOf(
            "MM/dd/yyyy",
            "M/d/yyyy",
            "dd/MM/yyyy",
            "d/M/yyyy",
            "yyyy-MM-dd",
            "MM-dd-yyyy"
        )

        // Common time formats in AgaMatrix exports
        val timeFormats = listOf(
            "HH:mm:ss",
            "HH:mm",
            "h:mm:ss a",
            "h:mm a",
            "hh:mm:ss a",
            "hh:mm a"
        )

        // Try various date-time format combinations
        for (dateFormat in dateFormats) {
            // If no time provided, use date only
            if (timeStr.isEmpty()) {
                try {
                    val formatter = DateTimeFormatter.ofPattern(dateFormat, Locale.US)
                    val localDate = java.time.LocalDate.parse(dateStr, formatter)
                    return localDate.atStartOfDay(ZoneId.systemDefault())
                } catch (e: Exception) {
                    // Try next format
                }
            }

            // Try date + time combinations
            for (timeFormat in timeFormats) {
                try {
                    val formatter = DateTimeFormatter.ofPattern("$dateFormat $timeFormat", Locale.US)
                    val formattedDateTime = "$dateStr $timeStr"
                    val localDateTime = LocalDateTime.parse(formattedDateTime, formatter)
                    return ZonedDateTime.of(localDateTime, ZoneId.systemDefault())
                } catch (e: DateTimeParseException) {
                    // Try next format combination
                }
            }
        }

        // As a fallback, if only date is available and couldn't parse with standard formats
        if (timeStr.isEmpty()) {
            try {
                // Try to extract year, month, day from the string
                val parts = dateStr.split(Regex("[/.-]"))
                if (parts.size == 3) {
                    // Try different arrangements of year, month, day
                    val arrangements = listOf(
                        Triple(0, 1, 2), // yyyy-MM-dd
                        Triple(2, 0, 1), // MM/dd/yyyy
                        Triple(2, 1, 0)  // dd/MM/yyyy
                    )

                    for ((yearIdx, monthIdx, dayIdx) in arrangements) {
                        try {
                            val year = parts[yearIdx].toInt().let {
                                if (it < 100) it + 2000 else it // Convert 2-digit years to 4-digit
                            }
                            val month = parts[monthIdx].toInt()
                            val day = parts[dayIdx].toInt()

                            if (year in 2000..2100 && month in 1..12 && day in 1..31) {
                                return ZonedDateTime.of(
                                    year, month, day, 0, 0, 0, 0,
                                    ZoneId.systemDefault()
                                )
                            }
                        } catch (e: Exception) {
                            // Try next arrangement
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to parse date as fallback: $dateStr", e)
            }
        }

        // If all parsing attempts fail, return current time with a warning
        Log.w(tag, "Could not parse date/time: $dateStr $timeStr - using current time")
        return ZonedDateTime.now()
    }

    /**
     * Extract numeric value from a string that might include units
     */
    private fun extractNumericValue(str: String): Double {
        // Remove any non-numeric characters except decimal point
        val numericOnly = str.replace(Regex("[^0-9.]"), "")
        return numericOnly.toDoubleOrNull() ?: 0.0
    }
}