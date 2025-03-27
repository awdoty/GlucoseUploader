package com.example.glucoseuploader

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.health.connect.client.records.BloodGlucoseRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Class for parsing CSV files with glucose readings
 */
class GlucoseCsvParser {
    private val tag = "GlucoseCsvParser"

    // Common date formats used in glucose tracking apps
    private val dateFormats = listOf(
        "MM/dd/yyyy", "MM-dd-yyyy",
        "dd/MM/yyyy", "dd-MM-yyyy",
        "yyyy/MM/dd", "yyyy-MM-dd"
    )

    // Common time formats
    private val timeFormats = listOf(
        "HH:mm:ss", "HH:mm",
        "hh:mm:ss a", "hh:mm a"
    )

    /**
     * Parse a CSV file containing glucose data
     */
    suspend fun parseGlucoseReadings(context: Context, fileUri: Uri): List<GlucoseReading> = withContext(Dispatchers.IO) {
        val readings = mutableListOf<GlucoseReading>()

        try {
            Log.d(tag, "Starting to parse file: $fileUri")

            context.contentResolver.openInputStream(fileUri)?.use { stream ->
                val reader = BufferedReader(InputStreamReader(stream))
                val csvContent = reader.readText()

                // First, check if it's an AgaMatrix format
                if (csvContent.contains("AgaMatrix", ignoreCase = true) ||
                    csvContent.contains("WaveSense", ignoreCase = true) ||
                    csvContent.contains("Jazz", ignoreCase = true)) {
                    Log.d(tag, "Detected AgaMatrix format")
                    readings.addAll(parseAgaMatrixFormat(csvContent))
                } else {
                    // Try parsing with Apache Commons CSV for standard formats
                    Log.d(tag, "Using generic CSV parser")
                    readings.addAll(parseGenericCsvFormat(csvContent))
                }
            }

            Log.d(tag, "Parsed ${readings.size} glucose readings")
        } catch (e: Exception) {
            Log.e(tag, "Error parsing CSV file", e)
        }

        readings
    }

    /**
     * Parse CSV in AgaMatrix format
     */
    private fun parseAgaMatrixFormat(csvContent: String): List<GlucoseReading> {
        val readings = mutableListOf<GlucoseReading>()
        val lines = csvContent.lines()

        // Find the header row - usually contains "Date", "Time", "Glucose"
        val headerIndex = lines.indexOfFirst { line ->
            line.contains("Date", ignoreCase = true) &&
                    (line.contains("Time", ignoreCase = true) || line.contains("Clock", ignoreCase = true)) &&
                    line.contains("Glucose", ignoreCase = true)
        }

        if (headerIndex == -1) {
            Log.w(tag, "Could not find header row in AgaMatrix CSV")
            return parseGenericCsvFormat(csvContent)
        }

        // Parse the header row
        val header = lines[headerIndex].split(",").map { it.trim() }
        val dateIndex = header.indexOfFirst { it.contains("Date", ignoreCase = true) }
        val timeIndex = header.indexOfFirst {
            it.contains("Time", ignoreCase = true) || it.contains("Clock", ignoreCase = true)
        }
        val glucoseIndex = header.indexOfFirst {
            it.contains("Glucose", ignoreCase = true) || it.contains("Reading", ignoreCase = true)
        }
        val mealIndex = header.indexOfFirst {
            it.contains("Meal", ignoreCase = true) || it.contains("Event", ignoreCase = true)
        }

        if (dateIndex == -1 || glucoseIndex == -1) {
            Log.w(tag, "Missing required columns in header")
            return parseGenericCsvFormat(csvContent)
        }

        // Process data rows
        for (i in (headerIndex + 1) until lines.size) {
            try {
                val line = lines[i]
                if (line.isBlank()) continue

                val values = line.split(",").map { it.trim().replace("\"", "") }
                if (values.size <= maxOf(dateIndex, timeIndex, glucoseIndex)) {
                    continue // Skip rows with insufficient columns
                }

                val dateStr = values[dateIndex]
                val timeStr = if (timeIndex != -1 && timeIndex < values.size) values[timeIndex] else "00:00"

                // Parse glucose value - remove any non-numeric characters except decimal point
                val glucoseStr = values[glucoseIndex].replace("[^0-9.]".toRegex(), "")
                val glucoseValue = glucoseStr.toDoubleOrNull()

                if (glucoseValue != null && glucoseValue > 0) {
                    // Parse date and time
                    val dateTime = parseDateTime(dateStr, timeStr)

                    if (dateTime != null) {
                        // Determine meal type if available
                        var mealType = BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN
                        if (mealIndex != -1 && mealIndex < values.size) {
                            mealType = parseMealType(values[mealIndex])
                        }

                        readings.add(GlucoseReading(
                            value = glucoseValue,
                            dateTime = dateTime,
                            mealType = mealType
                        ))
                    }
                }
            } catch (e: Exception) {
                // Skip problematic rows
                Log.e(tag, "Error parsing row ${lines[i]}", e)
            }
        }

        return readings
    }

    /**
     * Parse CSV in any standard format
     */
    private fun parseGenericCsvFormat(csvContent: String): List<GlucoseReading> {
        val readings = mutableListOf<GlucoseReading>()

        try {
            // First try the Apache Commons CSV parser
            val parser = CSVParser.parse(csvContent, CSVFormat.DEFAULT.withFirstRecordAsHeader())

            // Try to identify which columns contain date, time, and glucose values
            var dateColumn: String? = null
            var timeColumn: String? = null
            var dateTimeColumn: String? = null
            var glucoseColumn: String? = null
            var mealColumn: String? = null

            for (header in parser.headerMap.keys) {
                val lowerHeader = header.lowercase()
                when {
                    lowerHeader.contains("date") && !lowerHeader.contains("time") ->
                        dateColumn = header
                    lowerHeader.contains("time") && !lowerHeader.contains("date") ->
                        timeColumn = header
                    lowerHeader.contains("datetime") || (lowerHeader.contains("date") && lowerHeader.contains("time")) ->
                        dateTimeColumn = header
                    lowerHeader.contains("glucose") || lowerHeader.contains("reading") ||
                            lowerHeader.contains("value") || lowerHeader.contains("result") ->
                        glucoseColumn = header
                    lowerHeader.contains("meal") || lowerHeader.contains("event") || lowerHeader.contains("relation") ->
                        mealColumn = header
                }
            }

            // Process the records
            for (record in parser) {
                try {
                    // Get datetime either from date+time columns or combined datetime column
                    var dateTime: ZonedDateTime? = null

                    if (dateTimeColumn != null && record.isMapped(dateTimeColumn)) {
                        val dateTimeStr = record.get(dateTimeColumn)
                        dateTime = parseDateTimeString(dateTimeStr)
                    } else if (dateColumn != null && record.isMapped(dateColumn)) {
                        val dateStr = record.get(dateColumn)
                        val timeStr = if (timeColumn != null && record.isMapped(timeColumn))
                            record.get(timeColumn) else ""
                        dateTime = parseDateTime(dateStr, timeStr)
                    }

                    // Get glucose value
                    if (glucoseColumn != null && record.isMapped(glucoseColumn) && dateTime != null) {
                        val glucoseStr = record.get(glucoseColumn).replace("[^0-9.]".toRegex(), "")
                        val glucoseValue = glucoseStr.toDoubleOrNull()

                        if (glucoseValue != null && glucoseValue > 0) {
                            // Get meal type if available
                            var mealType = BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN
                            if (mealColumn != null && record.isMapped(mealColumn)) {
                                mealType = parseMealType(record.get(mealColumn))
                            }

                            readings.add(GlucoseReading(
                                value = glucoseValue,
                                dateTime = dateTime,
                                mealType = mealType
                            ))
                        }
                    }
                } catch (e: Exception) {
                    // Skip problematic records
                    continue
                }
            }

            // If Apache Commons CSV parsing failed, try a more basic approach
            if (readings.isEmpty()) {
                readings.addAll(parseCsvFallback(csvContent))
            }
        } catch (e: Exception) {
            Log.e(tag, "Error with Apache Commons CSV parser, trying fallback", e)
            readings.addAll(parseCsvFallback(csvContent))
        }

        return readings
    }

    /**
     * Fallback parser for when Apache Commons CSV fails
     */
    private fun parseCsvFallback(csvContent: String): List<GlucoseReading> {
        val readings = mutableListOf<GlucoseReading>()
        val lines = csvContent.lines()

        // Try to identify the header row
        val headerIndex = lines.indexOfFirst { line ->
            line.lowercase().let {
                it.contains("date") || it.contains("time") || it.contains("glucose")
            }
        }

        val dataStartIndex = if (headerIndex >= 0) headerIndex + 1 else 1

        // Process data rows
        for (i in dataStartIndex until lines.size) {
            try {
                val line = lines[i]
                if (line.isBlank()) continue

                val values = line.split(",").map { it.trim() }

                // Look for values that match expected patterns
                var dateStr = ""
                var timeStr = ""
                var glucoseValue = 0.0

                for (value in values) {
                    when {
                        // Date patterns like MM/DD/YYYY or YYYY-MM-DD
                        value.matches(Regex("""^\d{1,4}[-/]\d{1,2}[-/]\d{1,4}$""")) ->
                            dateStr = value

                        // Time patterns like HH:MM or HH:MM:SS
                        value.matches(Regex("""^\d{1,2}:\d{2}(:\d{2})?( ?[APap][Mm])?$""")) ->
                            timeStr = value

                        // Numeric values (potential glucose readings)
                        value.matches(Regex("""^\d+(\.\d+)?$""")) -> {
                            val numeric = value.toDoubleOrNull()
                            // Only consider reasonable glucose values (40-600 mg/dL)
                            if (numeric != null && numeric in 40.0..600.0) {
                                glucoseValue = numeric
                            }
                        }
                    }
                }

                // If we found date and glucose value, create reading
                if (dateStr.isNotEmpty() && glucoseValue > 0) {
                    val dateTime = parseDateTime(dateStr, timeStr)
                    if (dateTime != null) {
                        readings.add(GlucoseReading(
                            value = glucoseValue,
                            dateTime = dateTime,
                            mealType = BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN
                        ))
                    }
                }
            } catch (e: Exception) {
                // Skip problematic rows
                continue
            }
        }

        return readings
    }

    /**
     * Parse date and time strings into a ZonedDateTime
     */
    private fun parseDateTime(dateStr: String, timeStr: String): ZonedDateTime? {
        // Try each date format
        for (dateFormat in dateFormats) {
            try {
                val formatter = DateTimeFormatter.ofPattern(dateFormat)
                val localDate = LocalDate.parse(dateStr, formatter)

                // If no time provided, use midnight
                if (timeStr.isEmpty()) {
                    return ZonedDateTime.of(localDate, LocalTime.MIDNIGHT, ZoneId.systemDefault())
                }

                // Try each time format
                for (timeFormat in timeFormats) {
                    try {
                        val timeFormatter = DateTimeFormatter.ofPattern(timeFormat)
                        val localTime = LocalTime.parse(timeStr, timeFormatter)
                        return ZonedDateTime.of(localDate, localTime, ZoneId.systemDefault())
                    } catch (e: DateTimeParseException) {
                        // Try next time format
                    }
                }

                // If we parsed the date but not the time, use midnight
                return ZonedDateTime.of(localDate, LocalTime.MIDNIGHT, ZoneId.systemDefault())
            } catch (e: DateTimeParseException) {
                // Try next date format
            }
        }

        return null
    }

    /**
     * Parse a datetime string that combines date and time
     */
    private fun parseDateTimeString(dateTimeStr: String): ZonedDateTime? {
        // Common datetime formats
        val datetimeFormats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "MM/dd/yyyy HH:mm:ss",
            "dd/MM/yyyy HH:mm:ss"
        )

        for (format in datetimeFormats) {
            try {
                val formatter = DateTimeFormatter.ofPattern(format)
                val localDateTime = java.time.LocalDateTime.parse(dateTimeStr, formatter)
                return ZonedDateTime.of(localDateTime, ZoneId.systemDefault())
            } catch (e: DateTimeParseException) {
                // Try next format
            }
        }

        return null
    }

    /**
     * Parse meal type from string
     */
    private fun parseMealType(mealTypeStr: String): Int {
        return when {
            mealTypeStr.contains("before", ignoreCase = true) ->
                BloodGlucoseRecord.RELATION_TO_MEAL_BEFORE_MEAL
            mealTypeStr.contains("after", ignoreCase = true) ->
                BloodGlucoseRecord.RELATION_TO_MEAL_AFTER_MEAL
            mealTypeStr.contains("fasting", ignoreCase = true) ->
                BloodGlucoseRecord.RELATION_TO_MEAL_FASTING
            else ->
                BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN
        }
    }
}