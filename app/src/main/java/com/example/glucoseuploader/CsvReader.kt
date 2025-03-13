package com.example.glucoseuploader

import android.content.Context
import android.net.Uri
import android.util.Log
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Utility class to read and parse CSV files
 */
class CsvReader(private val context: Context) {

    private val TAG = "CsvReader"

    /**
     * Read a CSV file from a URI
     */
    suspend fun readCsvFile(uri: Uri): List<String> {
        return try {
            Log.d(TAG, "Reading CSV file: $uri")

            val inputStream = context.contentResolver.openInputStream(uri)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val lines = mutableListOf<String>()

            var line: String?
            while (reader.readLine().also { line = it } != null) {
                line?.let {
                    if (it.isNotBlank()) {
                        lines.add(it)
                    }
                }
            }

            Log.d(TAG, "Found ${lines.size} non-empty lines in file")

            // Log first few lines for debugging
            lines.take(5).forEachIndexed { index, line ->
                Log.d(TAG, "Line $index: $line")
            }

            reader.close()
            inputStream?.close()

            lines
        } catch (e: Exception) {
            Log.e(TAG, "Error reading CSV file", e)
            throw e
        }
    }

    /**
     * Detect the type of CSV file based on its content
     */
    fun detectCsvType(lines: List<String>): String {
        if (lines.isEmpty()) return "Unknown"

        return when {
            lines.any { it.contains("Exported from AgaMatrix", ignoreCase = true) } -> "AgaMatrix CSV"
            lines.any { it.contains("Blood Glucose Data", ignoreCase = true) } -> "Glucose CSV"
            lines.any { it.contains("Glucose", ignoreCase = true) } -> "Glucose CSV"
            lines.any { it.contains("mg/dL", ignoreCase = true) } -> "Glucose CSV"
            lines.any { it.contains("date", ignoreCase = true) && it.contains("time", ignoreCase = true) } -> "Standard CSV"
            lines.any { it.split(',').size >= 3 } -> "Generic CSV"
            else -> "Unknown"
        }
    }

    /**
     * Parse a generic CSV format into glucose readings
     */
    fun parseGenericCsvFormat(lines: List<String>): List<GlucoseReading> {
        val readings = mutableListOf<GlucoseReading>()

        try {
            // First try to parse using Apache Commons CSV for more robust handling
            try {
                val result = parseWithApacheCommons(lines)
                if (result.isNotEmpty()) {
                    Log.d(TAG, "Successfully parsed ${result.size} readings using Apache Commons CSV")
                    return result
                }
            } catch (e: Exception) {
                Log.w(TAG, "Apache Commons CSV parsing failed: ${e.message}")
            }

            // Find start line (skip headers)
            var startLine = 0
            for (i in lines.indices) {
                val parts = lines[i].split(',')
                if (parts.any { it.contains("date", ignoreCase = true) } ||
                    parts.any { it.contains("time", ignoreCase = true) } ||
                    parts.any { it.contains("glucose", ignoreCase = true) }) {
                    startLine = i + 1
                    break
                }
            }

            // If we didn't find a header, assume the first line is data
            if (startLine == 0 && lines.isNotEmpty()) {
                startLine = 1
            }

            // Process data rows
            for (i in startLine until lines.size) {
                val line = lines[i]
                val parts = splitCsvLine(line)

                if (parts.size >= 2) {
                    try {
                        // Try to find date and time parts
                        var dateStr = ""
                        var timeStr = ""
                        var valueStr = ""

                        // Find date, time, and glucose values in the row
                        for (part in parts) {
                            when {
                                dateStr.isEmpty() && looksLikeDate(part) -> dateStr = part
                                timeStr.isEmpty() && looksLikeTime(part) -> timeStr = part
                                valueStr.isEmpty() && looksLikeGlucoseValue(part) -> valueStr = part
                            }
                        }

                        // If no date found but time found, check if first column is date
                        if (dateStr.isEmpty() && timeStr.isNotEmpty() && parts.isNotEmpty()) {
                            dateStr = parts[0]
                        }

                        // If no time found but date found, check if second column is time
                        if (dateStr.isNotEmpty() && timeStr.isEmpty() && parts.size > 1) {
                            timeStr = parts[1]
                        }

                        // If no glucose value found, check the third column
                        if (valueStr.isEmpty() && parts.size > 2) {
                            valueStr = parts[2]
                        }

                        // Parse the glucose value
                        val valueRaw = valueStr.replace(Regex("[^0-9.]"), "")
                        val value = valueRaw.toDoubleOrNull()

                        if (value != null) {
                            // Try various date formats
                            var timestamp: ZonedDateTime? = null
                            try {
                                // Try standard date + time format
                                val dateTime = "$dateStr $timeStr"
                                timestamp = parseDateTime(dateTime)
                            } catch (e: Exception) {
                                // If that fails, try iso format
                                try {
                                    timestamp = ZonedDateTime.parse(dateStr)
                                } catch (e2: Exception) {
                                    // If all parsing fails, use current time
                                    timestamp = ZonedDateTime.now()
                                }
                            }

                            readings.add(GlucoseReading(value, timestamp))
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing line: $line", e)
                        // Continue with next line
                    }
                }
            }

            Log.d(TAG, "Successfully parsed ${readings.size} readings using parseGenericCsvFormat")
            return readings
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing CSV", e)
            return emptyList()
        }
    }
    /**
     * Try to parse a date-time string with different common formats
     */
    fun tryParseDateTime(dateTimeStr: String): LocalDateTime {
        val formats = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "MM/dd/yyyy HH:mm:ss",
            "MM/dd/yyyy HH:mm",
            "dd/MM/yyyy HH:mm:ss",
            "dd/MM/yyyy HH:mm",
            "MM-dd-yyyy HH:mm:ss",
            "MM-dd-yyyy HH:mm"
        )

        for (format in formats) {
            try {
                return LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ofPattern(format))
            } catch (e: DateTimeParseException) {
                // Try next format
            }
        }

        // If all formats fail, throw exception
        throw DateTimeParseException("Could not parse date-time: $dateTimeStr", dateTimeStr, 0)
    }

    /**
     * Parse using Apache Commons CSV library for more robust handling
     */
    private fun parseWithApacheCommons(lines: List<String>): List<GlucoseReading> {
        val joinedContent = lines.joinToString("\n")

        // Try different CSV formats
        val formats = listOf(
            CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build(),
            CSVFormat.DEFAULT,
            CSVFormat.EXCEL.builder().setHeader().setSkipHeaderRecord(true).build(),
            CSVFormat.EXCEL
        )

        for (format in formats) {
            try {
                val parser = CSVParser.parse(joinedContent, format)
                val records = parser.records

                if (records.isEmpty()) continue

                // Try to identify columns
                var dateIndex = -1
                var timeIndex = -1
                var glucoseIndex = -1
                var dateTimeIndex = -1

                // Check if we have headers
                val header = parser.headerMap
                if (header.isNotEmpty()) {
                    for ((name, index) in header) {
                        val lowerName = name.lowercase(Locale.getDefault())
                        when {
                            lowerName.contains("date") && !lowerName.contains("time") -> dateIndex = index
                            lowerName.contains("time") && !lowerName.contains("date") -> timeIndex = index
                            (lowerName.contains("date") && lowerName.contains("time")) ||
                                    lowerName.contains("timestamp") -> dateTimeIndex = index
                            lowerName.contains("glucose") || lowerName.contains("reading") ||
                                    lowerName.contains("value") || lowerName.contains("level") ||
                                    lowerName.contains("mg/dl") -> glucoseIndex = index
                        }
                    }
                }

                // If columns not identified, try to infer from first row
                if ((dateIndex == -1 && dateTimeIndex == -1) || glucoseIndex == -1) {
                    val firstRecord = records.firstOrNull() ?: continue

                    for (i in 0 until firstRecord.size()) {
                        val value = firstRecord.get(i)
                        when {
                            dateTimeIndex == -1 && looksLikeDateAndTime(value) -> dateTimeIndex = i
                            dateIndex == -1 && looksLikeDate(value) -> dateIndex = i
                            timeIndex == -1 && looksLikeTime(value) -> timeIndex = i
                            glucoseIndex == -1 && looksLikeGlucoseValue(value) -> glucoseIndex = i
                        }
                    }
                }

                // If still not identified, use defaults
                if (dateTimeIndex == -1) {
                    if (dateIndex == -1) dateIndex = 0
                    if (timeIndex == -1) timeIndex = 1
                }
                if (glucoseIndex == -1) glucoseIndex = 2

                // Process records
                val result = mutableListOf<GlucoseReading>()

                for (record in records) {
                    try {
                        // Parse date and time
                        var timestamp: ZonedDateTime? = null

                        if (dateTimeIndex >= 0 && dateTimeIndex < record.size()) {
                            // Combined date/time field
                            val dateTimeStr = record.get(dateTimeIndex).trim()
                            try {
                                timestamp = parseDateTime(dateTimeStr)
                            } catch (e: Exception) {
                                // Skip this record if can't parse date/time
                                continue
                            }
                        } else if (dateIndex >= 0 && dateIndex < record.size()) {
                            // Separate date and time fields
                            val dateStr = record.get(dateIndex).trim()
                            val timeStr = if (timeIndex >= 0 && timeIndex < record.size()) {
                                record.get(timeIndex).trim()
                            } else {
                                ""
                            }

                            try {
                                timestamp = parseDateTime("$dateStr $timeStr")
                            } catch (e: Exception) {
                                // Skip this record if can't parse date/time
                                continue
                            }
                        } else {
                            // Skip records without date information
                            continue
                        }

                        // Parse glucose value
                        if (glucoseIndex >= 0 && glucoseIndex < record.size()) {
                            val glucoseStr = record.get(glucoseIndex).replace(Regex("[^0-9.]"), "").trim()
                            val glucose = glucoseStr.toDoubleOrNull()

                            if (glucose != null && timestamp != null) {
                                result.add(GlucoseReading(glucose, timestamp))
                            }
                        }
                    } catch (e: Exception) {
                        // Skip records with errors
                        Log.w(TAG, "Error processing record: ${e.message}")
                    }
                }

                if (result.isNotEmpty()) {
                    return result
                }
            } catch (e: Exception) {
                // Try next format
                Log.w(TAG, "Error with format ${format}: ${e.message}")
            }
        }

        return emptyList()
    }

    /**
     * Parse a datetime string using various formats
     */
    private fun parseDateTime(dateTimeStr: String): ZonedDateTime {
        // List of date formats to try
        val formats = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "MM/dd/yyyy HH:mm:ss",
            "MM/dd/yyyy HH:mm",
            "dd/MM/yyyy HH:mm:ss",
            "dd/MM/yyyy HH:mm",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy/MM/dd HH:mm",
            "MM-dd-yyyy HH:mm:ss",
            "MM-dd-yyyy HH:mm",
            "dd-MM-yyyy HH:mm:ss",
            "dd-MM-yyyy HH:mm",
            "yyyy.MM.dd HH:mm:ss",
            "yyyy.MM.dd HH:mm"
        )

        // Date without time formats
        val dateOnlyFormats = listOf(
            "yyyy-MM-dd",
            "MM/dd/yyyy",
            "dd/MM/yyyy",
            "yyyy/MM/dd",
            "MM-dd-yyyy",
            "dd-MM-yyyy",
            "yyyy.MM.dd"
        )

        // Time formats
        val timeFormats = listOf(
            "HH:mm:ss",
            "HH:mm",
            "hh:mm:ss a",
            "hh:mm a"
        )

        // Try combined formats first
        for (format in formats) {
            try {
                val formatter = DateTimeFormatter.ofPattern(format)
                val localDateTime = LocalDateTime.parse(dateTimeStr.trim(), formatter)
                return ZonedDateTime.of(localDateTime, ZoneId.systemDefault())
            } catch (e: Exception) {
                // Try next format
            }
        }

        // Try to extract date and time separately
        val dateTimeParts = dateTimeStr.trim().split(Regex("\\s+"), 2)
        if (dateTimeParts.size == 2) {
            val dateStr = dateTimeParts[0]
            val timeStr = dateTimeParts[1]

            for (dateFormat in dateOnlyFormats) {
                try {
                    val dateFormatter = DateTimeFormatter.ofPattern(dateFormat)
                    val localDate = java.time.LocalDate.parse(dateStr, dateFormatter)

                    for (timeFormat in timeFormats) {
                        try {
                            val timeFormatter = DateTimeFormatter.ofPattern(timeFormat)
                            val localTime = java.time.LocalTime.parse(timeStr, timeFormatter)

                            return ZonedDateTime.of(localDate, localTime, ZoneId.systemDefault())
                        } catch (e: Exception) {
                            // Try next time format
                        }
                    }
                } catch (e: Exception) {
                    // Try next date format
                }
            }
        }

        // If all formats fail, try date only
        for (format in dateOnlyFormats) {
            try {
                val formatter = DateTimeFormatter.ofPattern(format)
                val localDate = java.time.LocalDate.parse(dateTimeStr.trim(), formatter)
                return ZonedDateTime.of(localDate, java.time.LocalTime.NOON, ZoneId.systemDefault())
            } catch (e: Exception) {
                // Try next format
            }
        }

        // As a last resort, use current time
        throw DateTimeParseException("Could not parse date-time: $dateTimeStr", dateTimeStr, 0)
    }

    /**
     * Check if a string looks like a date
     */
    private fun looksLikeDate(str: String): Boolean {
        // Check for common date patterns
        val datePatterns = listOf(
            // MM/DD/YYYY or DD/MM/YYYY
            Regex("\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{2,4}"),
            // YYYY-MM-DD
            Regex("\\d{4}[/.-]\\d{1,2}[/.-]\\d{1,2}")
        )

        return datePatterns.any { it.matches(str.trim()) }
    }

    /**
     * Check if a string looks like a time
     */
    private fun looksLikeTime(str: String): Boolean {
        // Check for common time patterns (HH:MM or HH:MM:SS, with optional AM/PM)
        val timePattern = Regex("\\d{1,2}:\\d{2}(:\\d{2})?(\\s*[AaPp][Mm])?")
        return timePattern.matches(str.trim())
    }

    /**
     * Check if a string looks like a combined date and time
     */
    private fun looksLikeDateAndTime(str: String): Boolean {
        // Check for common date+time patterns
        val dateTimePatterns = listOf(
            // YYYY-MM-DD HH:MM:SS
            Regex("\\d{4}[/.-]\\d{1,2}[/.-]\\d{1,2}\\s+\\d{1,2}:\\d{2}(:\\d{2})?"),
            // MM/DD/YYYY HH:MM:SS
            Regex("\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{2,4}\\s+\\d{1,2}:\\d{2}(:\\d{2})?(\\s*[AaPp][Mm])?")
        )

        return dateTimePatterns.any { it.matches(str.trim()) }
    }

    /**
     * Check if a string might be a glucose value
     */
    private fun looksLikeGlucoseValue(str: String): Boolean {
        // A glucose value should be a number possibly followed by units
        val normalizedStr = str.trim().lowercase()

        // Check if it has numbers
        val hasNumbers = normalizedStr.any { it.isDigit() }

        // Check for common glucose units or just a plain number
        val hasUnits = normalizedStr.contains("mg") ||
                normalizedStr.contains("dl") ||
                normalizedStr.contains("mmol")

        // Most glucose values are between 40 and 400 mg/dL
        val numericPart = normalizedStr.replace(Regex("[^0-9.]"), "")
        val inRange = numericPart.toDoubleOrNull()?.let { it in 40.0..400.0 } ?: false

        return hasNumbers && (hasUnits || inRange || numericPart.isNotEmpty())
    }

    /**
     * Split a CSV line properly handling quoted values
     */
    private fun splitCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false

        for (char in line) {
            when {
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current = StringBuilder()
                }
                else -> current.append(char)
            }
        }

        // Add the last token
        result.add(current.toString())

        return result.map { it.trim('"', ' ') }
    }
}