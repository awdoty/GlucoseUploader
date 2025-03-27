package com.example.glucoseuploader

import android.content.ContentResolver
import android.net.Uri
import androidx.health.connect.client.records.BloodGlucoseRecord
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.UUID

/**
 * Parser for glucose CSV data files.
 */
class CsvParser(private val contentResolver: ContentResolver) {

    // Common date formats used in glucose tracking apps
    private val dateFormatters = listOf(
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy")
    )

    // Common time formats used in glucose tracking apps
    private val timeFormatters = listOf(
        DateTimeFormatter.ofPattern("HH:mm"),
        DateTimeFormatter.ofPattern("HH:mm:ss"),
        DateTimeFormatter.ofPattern("hh:mm a")
    )

    /**
     * Parse a CSV file containing glucose data.
     *
     * @param fileUri The URI of the CSV file
     * @return List of BloodGlucoseRecord objects
     */
    fun parseGlucoseData(fileUri: Uri): List<BloodGlucoseRecord> {
        val records = mutableListOf<BloodGlucoseRecord>()

        contentResolver.openInputStream(fileUri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                // Read header line to identify columns
                val headerLine = reader.readLine() ?: return emptyList()
                val headers = parseCsvLine(headerLine)

                // Find indices of required columns
                val dateIndex = headers.indexOfFirst { it.contains("date", ignoreCase = true) }
                val timeIndex = headers.indexOfFirst { it.contains("time", ignoreCase = true) }
                val glucoseIndex = headers.indexOfFirst {
                    it.contains("glucose", ignoreCase = true) ||
                            it.contains("value", ignoreCase = true) ||
                            it.contains("reading", ignoreCase = true)
                }
                val mealTypeIndex = headers.indexOfFirst {
                    it.contains("meal", ignoreCase = true) ||
                            it.contains("event", ignoreCase = true)
                }

                // Validate that required columns are present
                if (dateIndex == -1 || glucoseIndex == -1) {
                    throw IllegalArgumentException("CSV file must contain date and glucose columns")
                }

                // Process data rows
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    line?.let { dataLine ->
                        try {
                            val values = parseCsvLine(dataLine)
                            if (values.size <= maxOf(dateIndex, timeIndex, glucoseIndex)) {
                                // Skip rows with insufficient columns
                                continue
                            }

                            // Parse date and time
                            val dateStr = values[dateIndex]
                            val timeStr = if (timeIndex != -1 && timeIndex < values.size) values[timeIndex] else "00:00"
                            val timestamp = parseDateTime(dateStr, timeStr)

                            // Parse glucose level
                            val glucoseStr = values[glucoseIndex].replace(",", ".")
                            val glucoseLevel = glucoseStr.toDoubleOrNull()
                                ?: throw NumberFormatException("Invalid glucose value: $glucoseStr")

                            // Parse meal type if available
                            val mealType = if (mealTypeIndex != -1 && mealTypeIndex < values.size) {
                                parseMealType(values[mealTypeIndex])
                            } else {
                                BloodGlucoseRecord.MealType.UNKNOWN
                            }

                            // Create and add the record
                            val record = BloodGlucoseRecord(
                                time = timestamp,
                                zoneOffset = ZoneOffset.UTC,
                                level = glucoseLevel,
                                specimenSource = BloodGlucoseRecord.SpecimenSource.CAPILLARY_BLOOD,
                                mealType = mealType,
                                relationToMeal = BloodGlucoseRecord.RelationToMeal.UNKNOWN,
                                metadata = createMetadata()
                            )

                            records.add(record)
                        } catch (e: Exception) {
                            // Log and skip problematic rows
                            println("Error parsing row: $dataLine - ${e.message}")
                        }
                    }
                }
            }
        }

        return records
    }

    /**
     * Parse a CSV line into a list of values.
     */
    private fun parseCsvLine(line: String): List<String> {
        val values = mutableListOf<String>()
        var inQuotes = false
        var currentValue = StringBuilder()

        for (c in line) {
            when {
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    values.add(currentValue.toString().trim())
                    currentValue = StringBuilder()
                }
                else -> currentValue.append(c)
            }
        }

        // Add the last value
        values.add(currentValue.toString().trim())

        return values
    }

    /**
     * Parse date and time strings into an Instant.
     */
    private fun parseDateTime(dateStr: String, timeStr: String): Instant {
        // Try each date format until one works
        val date = dateFormatters.asSequence()
            .mapNotNull { formatter ->
                try {
                    LocalDate.parse(dateStr.trim(), formatter)
                } catch (e: DateTimeParseException) {
                    null
                }
            }
            .firstOrNull() ?: throw IllegalArgumentException("Invalid date format: $dateStr")

        // Try each time format until one works
        val time = timeFormatters.asSequence()
            .mapNotNull { formatter ->
                try {
                    LocalTime.parse(timeStr.trim(), formatter)
                } catch (e: DateTimeParseException) {
                    null
                }
            }
            .firstOrNull() ?: LocalTime.MIDNIGHT

        // Combine date and time and convert to Instant
        return LocalDateTime.of(date, time).toInstant(ZoneOffset.UTC)
    }

    /**
     * Parse meal type from string.
     */
    private fun parseMealType(mealTypeStr: String): BloodGlucoseRecord.MealType {
        return when {
            mealTypeStr.contains("before", ignoreCase = true) &&
                    mealTypeStr.contains("breakfast", ignoreCase = true) ->
                BloodGlucoseRecord.MealType.BEFORE_BREAKFAST

            mealTypeStr.contains("after", ignoreCase = true) &&
                    mealTypeStr.contains("breakfast", ignoreCase = true) ->
                BloodGlucoseRecord.MealType.AFTER_BREAKFAST

            mealTypeStr.contains("before", ignoreCase = true) &&
                    mealTypeStr.contains("lunch", ignoreCase = true) ->
                BloodGlucoseRecord.MealType.BEFORE_LUNCH

            mealTypeStr.contains("after", ignoreCase = true) &&
                    mealTypeStr.contains("lunch", ignoreCase = true) ->
                BloodGlucoseRecord.MealType.AFTER_LUNCH

            mealTypeStr.contains("before", ignoreCase = true) &&
                    mealTypeStr.contains("dinner", ignoreCase = true) ->
                BloodGlucoseRecord.MealType.BEFORE_DINNER

            mealTypeStr.contains("after", ignoreCase = true) &&
                    mealTypeStr.contains("dinner", ignoreCase = true) ->
                BloodGlucoseRecord.MealType.AFTER_DINNER

            mealTypeStr.contains("fasting", ignoreCase = true) ->
                BloodGlucoseRecord.MealType.FASTING

            else -> BloodGlucoseRecord.MealType.UNKNOWN
        }
    }

    /**
     * Create metadata for a record.
     */
    private fun createMetadata(): androidx.health.connect.client.records.metadata.Metadata {
        return androidx.health.connect.client.records.metadata.Metadata(
            dataOrigin = androidx.health.connect.client.records.metadata.DataOrigin(
                BloodGlucoseRecordHelper.SAMSUNG_HEALTH_APP
            ),
            recordingMethod = BloodGlucoseRecordHelper.RECORDING_METHOD_MANUALLY_ENTERED,
            clientRecordId = UUID.randomUUID().toString()
        )
    }
}