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
import java.util.Locale

/**
 * Specialized parser for AgaMatrix CSV format
 */
class AgaMatrixCsvParser(private val context: Context) {
    private val TAG = "AgaMatrixCsvParser"

    /**
     * Parse a CSV file from AgaMatrix
     */
    suspend fun parseAgaMatrixCsv(uri: Uri): List<GlucoseReading> = withContext(Dispatchers.IO) {
        val readings = mutableListOf<GlucoseReading>()

        try {
            // Read the file content
            val csvLines = readCsvFile(uri)

            // Find the header row (usually contains "Date", "Time", "Glucose")
            var headerIndex = -1
            for (i in csvLines.indices) {
                if (csvLines[i].contains("Date") &&
                    (csvLines[i].contains("Time") || csvLines[i].contains("Clock")) &&
                    csvLines[i].contains("Glucose")) {
                    headerIndex = i
                    break
                }
            }

            if (headerIndex == -1) {
                Log.w(TAG, "Could not find header row in AgaMatrix CSV")
                return@withContext readings
            }

            // Parse the header to identify column positions
            val headerRow = csvLines[headerIndex].split(",")
            val dateIndex = headerRow.indexOfFirst { it.trim().contains("Date") }
            val timeIndex = headerRow.indexOfFirst { it.trim().contains("Time") || it.trim().contains("Clock") }
            val glucoseIndex = headerRow.indexOfFirst { it.trim().contains("Glucose") }

            if (dateIndex == -1 || timeIndex == -1 || glucoseIndex == -1) {
                Log.w(TAG, "Missing required columns in AgaMatrix CSV")
                return@withContext readings
            }

            // Process data rows
            for (i in (headerIndex + 1) until csvLines.size) {
                try {
                    val row = csvLines[i].split(",")

                    if (row.size <= Math.max(Math.max(dateIndex, timeIndex), glucoseIndex)) {
                        continue // Skip rows with insufficient columns
                    }

                    val dateStr = row[dateIndex].trim().replace("\"", "")
                    val timeStr = row[timeIndex].trim().replace("\"", "")
                    val glucoseStr = row[glucoseIndex].trim().replace("\"", "")

                    // Extract glucose value (remove any units like "mg/dL")
                    val glucoseValue = glucoseStr.replace(Regex("[^0-9.]"), "").toDoubleOrNull()

                    if (glucoseValue != null && glucoseValue > 0) {
                        // Parse date/time
                        val dateTime = parseDateTime(dateStr, timeStr)
                        if (dateTime != null) {
                            readings.add(GlucoseReading(
                                value = glucoseValue,
                                timestamp = dateTime
                            ))
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing row ${csvLines[i]}", e)
                    // Continue with next row
                }
            }

            Log.d(TAG, "Successfully parsed ${readings.size} glucose readings")
            readings
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing AgaMatrix CSV", e)
            readings
        }
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
            Log.e(TAG, "Error reading CSV file", e)
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
            "yyyy-MM-dd"
        )

        // Common time formats in AgaMatrix exports
        val timeFormats = listOf(
            "HH:mm:ss",
            "HH:mm",
            "hh:mm:ss a",
            "hh:mm a"
        )

        for (dateFormat in dateFormats) {
            for (timeFormat in timeFormats) {
                try {
                    val combinedFormat = DateTimeFormatter.ofPattern("$dateFormat $timeFormat", Locale.US)
                    val localDateTime = LocalDateTime.parse("$dateStr $timeStr", combinedFormat)
                    return ZonedDateTime.of(localDateTime, ZoneId.systemDefault())
                } catch (e: Exception) {
                    // Try next format combination
                }
            }
        }

        return null
    }
}