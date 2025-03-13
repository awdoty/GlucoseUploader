package com.example.glucoseuploader

import android.content.Context
import android.net.Uri
import android.util.Log
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import java.io.BufferedReader/**
 * Parse a generic CSV format
 */
private fun parseGenericCsvFormat(lines: List<String>): List<GlucoseReading> {
    if (isAgaMatrixCsv(lines)) {
        // Use specialized AgaMatrix parsing logic
        return parseAgaMatrixFormat(lines)
    }

    // First try Apache Commons CSV for robust parsing
    val apacheReadings = parseWithApacheCommons(lines.joinToString("\n"))
    if (apacheReadings.isNotEmpty()) {
        return apacheReadings
    }

    // If Apache Commons failed, try manual parsing
    val readings = mutableListOf<GlucoseReading>()

    // Try to find header row
    var dataStartLine = 0
    for (i in lines.indices) {
        val parts = lines[i].split(',')
        if (parts.any {
                it.contains("date", ignoreCase = true) ||
                        it.contains("time", ignoreCase = true) ||
                        it.contains("glucose", ignoreCase = true)
            }) {
            dataStartLine = i + 1
            break
        }
    }

    // If no header found, start from line 1 (assuming line 0 is header)
    if (dataStartLine == 0 && lines.size > 1) {
        dataStartLine = 1
    }

    // Process data rows
    for (i in dataStartLine until lines.size) {
        val line = lines[i]
        val parts = splitCsvLine(line)

        if (parts.size < 2) continue

        try {
            // Try to identify date, time, and glucose parts
            var dateStr = ""
            var timeStr = ""
            var glucoseStr = ""

            // Look for date, time, and glucose values
            for (part in parts) {
                when {
                    dateStr.isEmpty() && looksLikeDate(part) -> dateStr = part
                    timeStr.isEmpty() && looksLikeTime(part) -> timeStr = part
                    glucoseStr.isEmpty() && part.matches(Regex("\\d+(\\.\\d+)?")) -> glucoseStr = part
                }
            }

            // Parse values
            val glucoseValue = extractNumericValue(glucoseStr)
            val dateTime = if (dateStr.isNotEmpty() && timeStr.isNotEmpty()) {
                parseDateTimeMultiFormat("$dateStr $timeStr")
            } else {
                parseDateTimeMultiFormat(glucoseStr)
            }

            if (glucoseValue > 0 && dateTime != null) {
                readings.add(GlucoseReading(glucoseValue, dateTime))
            }
        } catch (e: Exception) {
            // Skip problematic lines
            Log.w(TAG, "Error parsing generic CSV line: $line", e)
        }
    }

    return readings
}
import java.io.IOException
import java.io.InputStreamReader
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.regex.Pattern
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Comprehensive utilities for working with glucose CSV files from various glucose meters
 */
class GlucoseCsvUtilities {
    companion object {
        private const val TAG = "GlucoseCsvUtilities"

        // Known CSV formats
        enum class CsvFormat {
            AGAMATRIX,
            FREESTYLE_LIBRE,
            ONETOUCH,
            CONTOUR,
            DEXCOM,
            GENERIC,
            UNKNOWN
        }

        /**
         * Main entry point to read and parse a glucose CSV file
         */
        suspend fun parseGlucoseFile(
            context: Context,
            uri: Uri,
            progressCallback: ((String) -> Unit)? = null
        ): GlucoseFileResult = withContext(Dispatchers.IO) {
            try {
                progressCallback?.invoke("Reading file...")

                // Read the CSV content
                val csvContent = readCsvFile(context, uri)
                if (csvContent.isEmpty()) {
                    return@withContext GlucoseFileResult.Error("File is empty")
                }

                progressCallback?.invoke("Detecting file format...")

                // Detect what type of CSV file this is
                val format = detectCsvFormat(csvContent)

                progressCallback?.invoke("Parsing ${format.name} format...")

                // Parse the file based on its format
                val readings = when (format) {
                    CsvFormat.AGAMATRIX -> parseAgaMatrixFormat(csvContent)
                    CsvFormat.FREESTYLE_LIBRE -> parseFreestyleLibreFormat(csvContent)
                    CsvFormat.ONETOUCH -> parseOneTouchFormat(csvContent)
                    CsvFormat.DEXCOM -> parseDexcomFormat(csvContent)
                    CsvFormat.CONTOUR -> parseContourFormat(csvContent)
                    CsvFormat.GENERIC, CsvFormat.UNKNOWN -> parseGenericCsvFormat(csvContent)
                }

                if (readings.isEmpty()) {
                    return@withContext GlucoseFileResult.Error("No valid glucose readings found in file")
                }

                progressCallback?.invoke("Found ${readings.size} readings")
                GlucoseFileResult.Success(readings, format)

            } catch (e: Exception) {
                Log.e(TAG, "Error parsing glucose file: ${e.message}", e)
                GlucoseFileResult.Error("Error parsing file: ${e.message}")
            }
        }

        /**
         * Read a CSV file from a URI
         */
        private suspend fun readCsvFile(context: Context, uri: Uri): List<String> = withContext(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw IOException("Cannot open input stream for URI: $uri")

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

                reader.close()
                inputStream.close()

                lines
            } catch (e: Exception) {
                Log.e(TAG, "Error reading CSV file", e)
                throw e
            }
        }

        /**
         * Detect the format of a CSV file based on its content
         */
        fun detectCsvFormat(lines: List<String>): CsvFormat {
            if (lines.isEmpty()) return CsvFormat.UNKNOWN

            // Check the first few lines for format-specific patterns
            val sampleText = lines.take(10).joinToString("\n").lowercase(Locale.getDefault())

            return when {
                // AgaMatrix detection
                sampleText.contains("agamatrix", ignoreCase = true) ||
                        sampleText.contains("wavesense", ignoreCase = true) ||
                        sampleText.contains("jazz wireless", ignoreCase = true) -> CsvFormat.AGAMATRIX

                // Freestyle Libre detection
                sampleText.contains("freestyle", ignoreCase = true) ||
                        sampleText.contains("libre", ignoreCase = true) ||
                        sampleText.contains("abbott", ignoreCase = true) ||
                        sampleText.contains("scan results", ignoreCase = true) -> CsvFormat.FREESTYLE_LIBRE

                // OneTouch detection
                sampleText.contains("onetouch", ignoreCase = true) ||
                        sampleText.contains("lifescan", ignoreCase = true) ||
                        sampleText.contains("verio", ignoreCase = true) ||
                        sampleText.contains("ultra", ignoreCase = true) -> CsvFormat.ONETOUCH

                // Dexcom detection
                sampleText.contains("dexcom", ignoreCase = true) ||
                        sampleText.contains("glucose reading", ignoreCase = true) ||
                        sampleText.contains("trend", ignoreCase = true) ||
                        sampleText.contains("cgm", ignoreCase = true) -> CsvFormat.DEXCOM

                // Contour detection
                sampleText.contains("contour", ignoreCase = true) ||
                        sampleText.contains("ascensia", ignoreCase = true) ||
                        sampleText.contains("bayer", ignoreCase = true) ||
                        sampleText.contains("glucofacts", ignoreCase = true) -> CsvFormat.CONTOUR

                // Generic format detection - check for common headers
                sampleText.contains("glucose") ||
                        sampleText.contains("date") && sampleText.contains("time") ||
                        sampleText.contains("mg/dl") ||
                        sampleText.contains("mmol") -> CsvFormat.GENERIC

                // Otherwise unknown
                else -> CsvFormat.UNKNOWN
            }
        }

        /**
         * Parse AgaMatrix CSV format
         */
        private fun parseAgaMatrixFormat(lines: List<String>): List<GlucoseReading> {
            val readings = mutableListOf<GlucoseReading>()

            // Find the header line (typically contains "Date,Time,Glucose")
            var dataStartLine = 0
            for (i in lines.indices) {
                if (lines[i].contains("Date") && lines[i].contains("Time") &&
                    (lines[i].contains("Glucose") || lines[i].contains("Reading"))) {
                    dataStartLine = i + 1
                    break
                }
            }

            // If no header found, try using Apache Commons CSV
            if (dataStartLine == 0) {
                return parseWithApacheCommons(lines.joinToString("\n"))
            }

            // Process each data row
            for (i in dataStartLine until lines.size) {
                val line = lines[i]
                val parts = line.split(",")

                // AgaMatrix format usually has Date in first column, Time in second, Glucose in third
                if (parts.size >= 3) {
                    try {
                        val dateStr = parts[0].trim()
                        val timeStr = parts[1].trim()

                        // Find the glucose value - might be in 3rd column or have specific label
                        val glucoseStr = parts.find {
                            it.trim().matches(Regex("\\d+(\\.\\d+)?"))
                        } ?: parts[2].trim()

                        // Parse glucose value
                        val glucoseValue = extractNumericValue(glucoseStr)

                        // Parse date/time
                        val dateTime = parseDateTimeAgaMatrix(dateStr, timeStr)

                        if (glucoseValue > 0 && dateTime != null) {
                            readings.add(GlucoseReading(glucoseValue, dateTime))
                        }
                    } catch (e: Exception) {
                        // Skip problematic lines
                        Log.w(TAG, "Error parsing AgaMatrix line: $line", e)
                    }
                }
            }

            return readings
        }

        /**
         * Parse Freestyle Libre CSV format
         */
        private fun parseFreestyleLibreFormat(lines: List<String>): List<GlucoseReading> {
            val readings = mutableListOf<GlucoseReading>()

            // Freestyle Libre often has a multiline header
            // Look for the main data table that starts with "Device Timestamp" or similar
            var dataStartLine = 0
            for (i in lines.indices) {
                val line = lines[i].lowercase(Locale.getDefault())
                if (line.contains("device") && line.contains("timestamp") ||
                    line.contains("glucose") && line.contains("history") ||
                    line.contains("scan results") ||
                    line.contains("historic glucose")) {
                    dataStartLine = i + 1
                    break
                }
            }

            // If we didn't find the start, try generic parsing
            if (dataStartLine == 0) {
                return parseGenericCsvFormat(lines)
            }

            // Process data rows
            for (i in dataStartLine until lines.size) {
                val line = lines[i]
                val parts = line.split(",").map { it.trim() }

                // Skip lines that are too short
                if (parts.size < 3) continue

                try {
                    // In Freestyle Libre, timestamp is often first column, glucose value is in
                    // a column labeled "Historic Glucose mg/dL" or similar

                    // Find the date/time column (usually first)
                    val dateTimeStr = parts[0]

                    // Find glucose value column - look for numeric value that could be glucose
                    val glucoseStr = parts.firstOrNull {
                        it.matches(Regex("\\d+(\\.\\d+)?")) &&
                                it.toDoubleOrNull() ?: 0.0 in 20.0..600.0  // Reasonable glucose range
                    } ?: continue

                    // Parse values
                    val glucoseValue = extractNumericValue(glucoseStr)
                    val dateTime = parseDateTimeLibre(dateTimeStr)

                    if (glucoseValue > 0 && dateTime != null) {
                        readings.add(GlucoseReading(glucoseValue, dateTime))
                    }
                } catch (e: Exception) {
                    // Skip problematic lines
                    Log.w(TAG, "Error parsing Freestyle Libre line: $line", e)
                }
            }

            // If parsing failed, try more generic approach
            if (readings.isEmpty()) {
                return parseWithApacheCommons(lines.joinToString("\n"))
            }

            return readings
        }

        /**
         * Parse OneTouchFormat CSV format
         */
        private fun parseOneTouchFormat(lines: List<String>): List<GlucoseReading> {
            val readings = mutableListOf<GlucoseReading>()

            // OneTouch format typically has a multi-line header
            // Look for a line containing column headers
            var dataStartLine = 0
            for (i in lines.indices) {
                val line = lines[i].lowercase(Locale.getDefault())
                if ((line.contains("date") && line.contains("time") && line.contains("glucose")) ||
                    (line.contains("reading date") && line.contains("value")) ||
                    (line.contains("verio") && line.contains("result"))) {
                    dataStartLine = i + 1
                    break
                }
            }

            // If no header found, try generic approach
            if (dataStartLine == 0) {
                return parseGenericCsvFormat(lines)
            }

            // Process data rows
            for (i in dataStartLine until lines.size) {
                val line = lines[i]
                val parts = line.split(",").map { it.trim() }

                if (parts.size < 3) continue

                try {
                    // OneTouch typically has date in first column, time in second,
                    // glucose reading in third column
                    val dateStr = parts[0]
                    val timeStr = if (parts.size > 1) parts[1] else ""

                    // Find the glucose value column
                    val glucoseStr = parts.firstOrNull {
                        it.matches(Regex("\\d+(\\.\\d+)?")) &&
                                it.toDoubleOrNull() ?: 0.0 in 20.0..600.0
                    } ?: parts[2]

                    // Parse values
                    val glucoseValue = extractNumericValue(glucoseStr)

                    // Handle date and time which might be combined or separate
                    val dateTime = if (timeStr.isNotEmpty()) {
                        parseDateTimeMultiFormat("$dateStr $timeStr")
                    } else {
                        parseDateTimeMultiFormat(dateStr)
                    }

                    if (glucoseValue > 0 && dateTime != null) {
                        readings.add(GlucoseReading(glucoseValue, dateTime))
                    }
                } catch (e: Exception) {
                    // Skip problematic lines
                    Log.w(TAG, "Error parsing OneTouch line: $line", e)
                }
            }

            return readings
        }

        /**
         * Parse Dexcom CSV format
         */
        private fun parseDexcomFormat(lines: List<String>): List<GlucoseReading> {
            val readings = mutableListOf<GlucoseReading>()

            // Dexcom usually has a header with "Timestamp" or "Device Timestamp" and "Glucose Value"
            var dataStartLine = 0
            for (i in lines.indices) {
                val line = lines[i].lowercase(Locale.getDefault())
                if (line.contains("timestamp") &&
                    (line.contains("glucose") || line.contains("value"))) {
                    dataStartLine = i + 1
                    break
                }
            }

            // If we didn't find the header, try generic approach
            if (dataStartLine == 0) {
                return parseGenericCsvFormat(lines)
            }

            // Process data rows
            for (i in dataStartLine until lines.size) {
                val line = lines[i]
                val parts = line.split(",").map { it.trim() }

                if (parts.size < 2) continue

                try {
                    // Dexcom typically has timestamp in first column and glucose value in second
                    val timestampStr = parts[0]

                    // Find glucose value - look for a numeric in the reasonable range
                    val glucoseStr = parts.firstOrNull {
                        it.matches(Regex("\\d+(\\.\\d+)?")) &&
                                it.toDoubleOrNull() ?: 0.0 in 20.0..600.0
                    } ?: continue

                    // Parse values
                    val glucoseValue = extractNumericValue(glucoseStr)
                    val dateTime = parseDateTimeMultiFormat(timestampStr)

                    if (glucoseValue > 0 && dateTime != null) {
                        readings.add(GlucoseReading(glucoseValue, dateTime))
                    }
                } catch (e: Exception) {
                    // Skip problematic lines
                    Log.w(TAG, "Error parsing Dexcom line: $line", e)
                }
            }

            return readings
        }

        /**
         * Parse Contour CSV format
         */
        private fun parseContourFormat(lines: List<String>): List<GlucoseReading> {
            val readings = mutableListOf<GlucoseReading>()

            // Contour meters often have a detailed header
            // Look for a line containing the column headers
            var dataStartLine = 0
            for (i in lines.indices) {
                val line = lines[i].lowercase(Locale.getDefault())
                if ((line.contains("date") || line.contains("datetime")) &&
                    (line.contains("glucose") || line.contains("reading") || line.contains("value"))) {
                    dataStartLine = i + 1
                    break
                }
            }

            // If no header found, try generic approach
            if (dataStartLine == 0) {
                return parseGenericCsvFormat(lines)
            }

            // Process data rows
            for (i in dataStartLine until lines.size) {
                val line = lines[i]
                val parts = line.split(",").map { it.trim() }

                if (parts.size < 2) continue

                try {
                    // Contour CSV typically has date/time in first column, glucose in another column
                    val dateTimeStr = parts[0]

                    // Find glucose value
                    val glucoseStr = parts.firstOrNull {
                        it.matches(Regex("\\d+(\\.\\d+)?")) &&
                                it.toDoubleOrNull() ?: 0.0 in 20.0..600.0
                    } ?: continue

                    // Parse values
                    val glucoseValue = extractNumericValue(glucoseStr)
                    val dateTime = parseDateTimeMultiFormat(dateTimeStr)

                    if (glucoseValue > 0 && dateTime != null) {
                        readings.add(GlucoseReading(glucoseValue, dateTime))
                    }
                } catch (e: Exception) {
                    // Skip problematic lines
                    Log.w(TAG, "Error parsing Contour line: $line", e)
                }
            }

            return readings
        }

        /**
         * Parse a generic CSV format
         */
        private fun parseGenericCsvFormat(lines: List<String>): List<GlucoseReading> {
            // First try Apache Commons CSV for robust parsing
            val apacheReadings = parseWithApacheCommons(lines.joinToString("\n"))
            if (apacheReadings.isNotEmpty()) {
                return apacheReadings
            }

            // If Apache Commons failed, try manual parsing
            val readings = mutableListOf<GlucoseReading>()

            // Try to find header row
            var dataStartLine = 0
            for (i in lines.indices) {
                val parts = lines[i].split(',')
                if (parts.any {
                        it.contains("date", ignoreCase = true) ||
                                it.contains("time", ignoreCase = true) ||
                                it.contains("glucose", ignoreCase = true)
                    }) {
                    dataStartLine = i + 1
                    break
                }
            }

            // If no header found, start from line 1 (assuming line 0 is header)
            if (dataStartLine == 0 && lines.size > 1) {
                dataStartLine = 1
            }

            // Process data rows
            for (i in dataStartLine until lines.size) {
                val line = lines[i]
                val parts = splitCsvLine(line)

                if (parts.size < 2) continue

                try {
                    // Try to identify date, time, and glucose parts
                    var dateStr = ""
                    var timeStr = ""
                    var glucoseStr = ""

                    // Look for date, time, and glucose values
                    for (part in parts) {
                        when {
                            dateStr.isEmpty() && looksLikeDate(part) -> dateStr = part
                            timeStr.isEmpty() && looksLikeTime(part) -> timeStr = part
                            glucoseStr.isEmpty() && looksLikeGlucoseValue(part) -> glucoseStr = part
                        }
                    }

                    // If we found date but not time, look for time in the next part
                    if (dateStr.isNotEmpty() && timeStr.isEmpty() && parts.size > 1) {
                        for (i in parts.indices) {
                            if (parts[i] == dateStr && i < parts.size - 1) {
                                timeStr = parts[i + 1]
                                break
                            }
                        }
                    }

                    // If we didn't find glucose value, look for a numeric in the reasonable range
                    if (glucoseStr.isEmpty()) {
                        glucoseStr = parts.firstOrNull { part ->
                            val numericValue = extractNumericValue(part)
                            numericValue in 20.0..600.0
                        } ?: continue
                    }

                    // Parse values
                    val glucoseValue = extractNumericValue(glucoseStr)

                    // Parse date/time
                    val dateTime = if (timeStr.isNotEmpty()) {
                        parseDateTimeMultiFormat("$dateStr $timeStr")
                    } else if (dateStr.isNotEmpty()) {
                        parseDateTimeMultiFormat(dateStr)
                    } else {
                        null
                    }

                    if (glucoseValue > 0 && dateTime != null) {
                        readings.add(GlucoseReading(glucoseValue, dateTime))
                    }
                } catch (e: Exception) {
                    // Skip problematic lines
                    Log.w(TAG, "Error parsing generic line: $line", e)
                }
            }

            // If still no readings, try a last resort approach: find any numbers that look like glucose values
            if (readings.isEmpty()) {
                var currentDate = ZonedDateTime.now()

                for (line in lines) {
                    val parts = splitCsvLine(line)
                    for (part in parts) {
                        try {
                            val value = extractNumericValue(part)
                            if (value in 40.0..400.0) {
                                // This looks like a glucose value - use it with the current date
                                // and space them out by 1 hour
                                readings.add(GlucoseReading(value, currentDate))
                                currentDate = currentDate.minusHours(1)
                            }
                        } catch (e: Exception) {
                            // Ignore parsing errors
                        }
                    }
                }
            }

            return readings
        }

        /**
         * Parse CSV using Apache Commons CSV library
         */
        private fun parseWithApacheCommons(csvContent: String): List<GlucoseReading> {
            val readings = mutableListOf<GlucoseReading>()

            // Try multiple CSV formats since different glucose meters use different standards
            val formats = listOf(
                CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build(),
                CSVFormat.EXCEL.builder().setHeader().setSkipHeaderRecord(true).build(),
                CSVFormat.DEFAULT,
                CSVFormat.EXCEL,
                CSVFormat.RFC4180
            )

            for (format in formats) {
                try {
                    val parser = CSVParser.parse(csvContent, format)
                    val records = parser.records

                    if (records.isEmpty()) continue

                    // Try to identify columns
                    var dateIndex = -1
                    var timeIndex = -1
                    var dateTimeIndex = -1
                    var glucoseIndex = -1

                    // Check if we have headers
                    val headerMap = parser.headerMap
                    if (headerMap.isNotEmpty()) {
                        // Look for date/time/glucose columns in the header
                        for ((name, index) in headerMap) {
                            val lowerName = name.lowercase(Locale.getDefault())
                            when {
                                (lowerName.contains("date") && !lowerName.contains("time")) ||
                                        lowerName == "day" -> dateIndex = index
                                (lowerName.contains("time") && !lowerName.contains("date")) ||
                                        lowerName == "hour" -> timeIndex = index
                                (lowerName.contains("date") && lowerName.contains("time")) ||
                                        lowerName.contains("timestamp") ||
                                        lowerName.contains("recorded") -> dateTimeIndex = index
                                lowerName.contains("glucose") ||
                                        lowerName.contains("reading") ||
                                        lowerName.contains("value") ||
                                        lowerName.contains("result") ||
                                        lowerName.contains("mg/dl") ||
                                        lowerName.contains("mmol") -> glucoseIndex = index
                            }
                        }
                    }

                    // If columns not identified, try to guess from first row
                    if ((dateIndex == -1 && dateTimeIndex == -1) || glucoseIndex == -1) {
                        val firstRecord = records.firstOrNull() ?: continue

                        // Try to identify columns by content
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

                    // If still not identified, use defaults (common in glucose meter exports)
                    if (dateTimeIndex == -1 && dateIndex == -1) dateIndex = 0
                    if (dateTimeIndex == -1 && timeIndex == -1 && dateIndex > -1) timeIndex = minOf(dateIndex + 1, records.firstOrNull()?.size() ?: 1)
                    if (glucoseIndex == -1) {
                        glucoseIndex = if (timeIndex > -1) {
                            minOf(timeIndex + 1, records.firstOrNull()?.size() ?: 2)
                        } else {
                            minOf(2, records.firstOrNull()?.size() ?: 2)
                        }
                    }

                    // Process records
                    val successfulReads = processCsvRecords(
                        records, dateIndex, timeIndex, dateTimeIndex, glucoseIndex, readings
                    )

                    // If we successfully processed records, return them
                    if (successfulReads > 0) {
                        return readings
                    }

                } catch (e: Exception) {
                    // Try next format
                    Log.w(TAG, "Error with Apache Commons format: ${e.message}")
                }
            }

            return readings
        }

        /**
         * Process CSV records to extract glucose readings
         */
        private fun processCsvRecords(
            records: List<CSVRecord>,
            dateIndex: Int,
            timeIndex: Int,
            dateTimeIndex: Int,
            glucoseIndex: Int,
            results: MutableList<GlucoseReading>
        ): Int {
            var successCount = 0

            for (record in records) {
                try {
                    // Skip records that are too short
                    if (record.size() < 2) continue

                    // Parse date and time
                    var dateTime: ZonedDateTime? = null

                    if (dateTimeIndex >= 0 && dateTimeIndex < record.size()) {
                        // Combined date/time field
                        val dateTimeStr = record.get(dateTimeIndex).trim()
                        try {
                            dateTime = parseDateTimeMultiFormat(dateTimeStr)
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
                            dateTime = if (timeStr.isNotEmpty()) {
                                parseDateTimeMultiFormat("$dateStr $timeStr")
                            } else {
                                parseDateTimeMultiFormat(dateStr)
                            }
                        } catch (e: Exception) {
                            // Skip this record if can't parse date/time
                            continue
                        }
                    } else {
                        // Try to find date/time in any column
                        for (i in 0 until record.size()) {
                            val value = record.get(i).trim()
                            if (looksLikeDateAndTime(value)) {
                                try {
                                    dateTime = parseDateTimeMultiFormat(value)
                                    break
                                } catch (e: Exception) {
                                    // Continue checking other columns
                                }
                            }
                        }

                        // If still no datetime, try combinations of columns
                        if (dateTime == null) {
                            for (i in 0 until record.size() - 1) {
                                val value1 = record.get(i).trim()
                                val value2 = record.get(i + 1).trim()
                                if (looksLikeDate(value1) && looksLikeTime(value2)) {
                                    try {
                                        dateTime = parseDateTimeMultiFormat("$value1 $value2")
                                        break
                                    } catch (e: Exception) {
                                        // Continue checking
                                    }
                                }
                            }
                        }

                        // If still no datetime, skip this record
                        if (dateTime == null) continue
                    }

                    // Parse glucose value
                    var glucoseValue = 0.0

                    if (glucoseIndex >= 0 && glucoseIndex < record.size()) {
                        // Use the specified glucose column
                        val glucoseStr = record.get(glucoseIndex).trim()
                        glucoseValue = extractNumericValue(glucoseStr)
                    } else {
                        // Try to find a glucose value in any column
                        for (i in 0 until record.size()) {
                            val value = record.get(i).trim()
                            if (looksLikeGlucoseValue(value)) {
                                glucoseValue = extractNumericValue(value)
                                if (glucoseValue > 0) break
                            }
                        }
                    }

                    // Add valid reading to results
                    if (glucoseValue > 0 && dateTime != null) {
                        // Check for mmol/L values and convert to mg/dL if needed
                        if (glucoseValue < 25.0) {
                            // This is likely in mmol/L - convert to mg/dL
                            glucoseValue *= 18.0
                        }

                        results.add(GlucoseReading(glucoseValue, dateTime))
                        successCount++
                    }
                } catch (e: Exception) {
                    // Skip records with errors
                    Log.w(TAG, "Error processing CSV record: ${e.message}")
                }
            }

            return successCount
        }

        /**
         * Parse datetime string for AgaMatrix format
         */
        private fun parseDateTimeAgaMatrix(dateStr: String, timeStr: String): ZonedDateTime? {
            // AgaMatrix typically uses MM/DD/YYYY format
            val dateFormats = listOf(
                "MM/dd/yyyy",
                "M/d/yyyy",
                "MM-dd-yyyy",
                "yyyy/MM/dd",
                "yyyy-MM-dd"
            )

            val timeFormats = listOf(
                "HH:mm:ss",
                "HH:mm",
                "hh:mm:ss a",
                "hh:mm a",
                "h:mm a"
            )

            for (dateFormat in dateFormats) {
                try {
                    val localDate = java.time.LocalDate.parse(
                        dateStr,
                        DateTimeFormatter.ofPattern(dateFormat)
                    )

                    for (timeFormat in timeFormats) {
                        try {
                            val localTime = java.time.LocalTime.parse(
                                timeStr,
                                DateTimeFormatter.ofPattern(timeFormat)
                            )

                            return ZonedDateTime.of(
                                localDate,
                                localTime,
                                ZoneId.systemDefault()
                            )
                        } catch (e: DateTimeParseException) {
                            // Try next time format
                        }
                    }

                    // If we parsed the date but not the time, use noon
                    return ZonedDateTime.of(
                        localDate,
                        java.time.LocalTime.NOON,
                        ZoneId.systemDefault()
                    )
                } catch (e: DateTimeParseException) {
                    // Try next date format
                }
            }

            // If all else fails, try parsing as full datetime
            return parseDateTimeMultiFormat("$dateStr $timeStr")
        }

        /**
         * Parse datetime string for Freestyle Libre format
         */
        private fun parseDateTimeLibre(timestampStr: String): ZonedDateTime? {
            // Freestyle Libre uses several datetime formats
            val patterns = listOf(
                "MM-dd-yyyy HH:mm",
                "MM/dd/yyyy HH:mm",
                "yyyy-MM-dd HH:mm",
                "yyyy-MM-dd'T'HH:mm:ss",
                "dd-MM-yyyy HH:mm",
                "dd/MM/yyyy HH:mm"
            )

            for (pattern in patterns) {
                try {
                    val formatter = DateTimeFormatter.ofPattern(pattern)
                    val localDateTime = LocalDateTime.parse(timestampStr, formatter)
                    return ZonedDateTime.of(localDateTime, ZoneId.systemDefault())
                } catch (e: DateTimeParseException) {
                    // Try next pattern
                }
            }

            // If all patterns fail, try the multi-format parser
            return parseDateTimeMultiFormat(timestampStr)
        }

        /**
         * Parse a datetime string using multiple formats
         */
        private fun parseDateTimeMultiFormat(dateTimeStr: String): ZonedDateTime? {
            // Remove any extra whitespace
            val cleanedStr = dateTimeStr.trim().replace(Regex("\\s+"), " ")

            // List of date/time formats to try
            val formats = listOf(
                // ISO formats
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss",

                // Common formats with date and time
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd HH:mm",
                "MM/dd/yyyy HH:mm:ss",
                "MM/dd/yyyy HH:mm",
                "dd/MM/yyyy HH:mm:ss",
                "dd/MM/yyyy HH:mm",
                "MM-dd-yyyy HH:mm:ss",
                "MM-dd-yyyy HH:mm",
                "dd-MM-yyyy HH:mm:ss",
                "dd-MM-yyyy HH:mm",
                "yyyy.MM.dd HH:mm:ss",

                // Common formats with AM/PM
                "yyyy-MM-dd hh:mm:ss a",
                "yyyy-MM-dd hh:mm a",
                "MM/dd/yyyy hh:mm:ss a",
                "MM/dd/yyyy hh:mm a",
                "dd/MM/yyyy hh:mm:ss a",
                "dd/MM/yyyy hh:mm a",
                "MM-dd-yyyy hh:mm a",
                "yyyy-MM-dd h:mm a",
                "MM/dd/yyyy h:mm a",

                // Date-only formats
                "yyyy-MM-dd",
                "MM/dd/yyyy",
                "dd/MM/yyyy",
                "MM-dd-yyyy",
                "dd-MM-yyyy",
                "yyyy/MM/dd",
                "dd.MM.yyyy",
                "yyyy.MM.dd"
            )

            // Try each format
            for (format in formats) {
                try {
                    val formatter = DateTimeFormatter.ofPattern(format)

                    // Handle formats with and without time zone
                    if (format.contains("XXX")) {
                        // Parse with time zone
                        return ZonedDateTime.parse(cleanedStr, formatter)
                    } else if (!format.contains("HH:mm") && !format.contains("hh:mm") && !format.contains("h:mm")) {
                        // Date-only format
                        val localDate = java.time.LocalDate.parse(cleanedStr, formatter)
                        return ZonedDateTime.of(
                            localDate,
                            java.time.LocalTime.NOON,
                            ZoneId.systemDefault()
                        )
                    } else {
                        // Date and time without time zone
                        val localDateTime = LocalDateTime.parse(cleanedStr, formatter)
                        return ZonedDateTime.of(localDateTime, ZoneId.systemDefault())
                    }
                } catch (e: DateTimeParseException) {
                    // Try next format
                }
            }

            // Try non-standard formats - extract parts and reconstruct
            try {
                // Try to extract date parts using regex
                val datePattern = Pattern.compile("(\\d{1,4})[/.-](\\d{1,2})[/.-](\\d{1,4})")
                val matcher = datePattern.matcher(cleanedStr)

                if (matcher.find()) {
                    var year = matcher.group(1).toInt()
                    val month = matcher.group(2).toInt()
                    var day = matcher.group(3).toInt()

                    // Handle ambiguous year/day positions
                    if (year in 1..31 && day > 31) {
                        // Swap year and day
                        val temp = year
                        year = day
                        day = temp
                    }

                    // Handle 2-digit years
                    if (year < 100) {
                        year += if (year < 50) 2000 else 1900
                    }

                    // Create date
                    val localDate = java.time.LocalDate.of(year, month, day)

                    // Try to extract time if present
                    val timePattern = Pattern.compile("(\\d{1,2}):(\\d{2})(?::(\\d{2}))?\\s*(am|pm|AM|PM)?")
                    val timeMatcher = timePattern.matcher(cleanedStr)

                    if (timeMatcher.find()) {
                        var hour = timeMatcher.group(1).toInt()
                        val minute = timeMatcher.group(2).toInt()
                        val second = timeMatcher.group(3)?.toInt() ?: 0
                        val ampm = timeMatcher.group(4)

                        // Handle AM/PM
                        if (ampm != null) {
                            if ((ampm.equals("pm", ignoreCase = true)) && hour < 12) {
                                hour += 12
                            } else if ((ampm.equals("am", ignoreCase = true)) && hour == 12) {
                                hour = 0
                            }
                        }

                        val localTime = java.time.LocalTime.of(hour, minute, second)
                        val localDateTime = LocalDateTime.of(localDate, localTime)
                        return ZonedDateTime.of(localDateTime, ZoneId.systemDefault())
                    } else {
                        // No time found, use noon
                        return ZonedDateTime.of(
                            localDate,
                            java.time.LocalTime.NOON,
                            ZoneId.systemDefault()
                        )
                    }
                }
            } catch (e: Exception) {
                // Ignore regex extraction errors
            }

            // As a last resort, try using the system's default date/time parser
            try {
                val localDate = java.time.LocalDate.parse(cleanedStr)
                return ZonedDateTime.of(
                    localDate,
                    java.time.LocalTime.NOON,
                    ZoneId.systemDefault()
                )
            } catch (e: DateTimeParseException) {
                // Failed to parse as date
            }

            // If all parsing attempts failed, return null
            return null
        }

        /**
         * Extract a numeric value from a string, ignoring non-numeric characters
         */
        private fun extractNumericValue(str: String): Double {
            // Regular expression to extract numeric part (handles both integers and decimals)
            val numericPattern = Pattern.compile("(\\d+\\.?\\d*|\\.\\d+)")
            val matcher = numericPattern.matcher(str)

            if (matcher.find()) {
                val numericStr = matcher.group(1)
                return numericStr.toDoubleOrNull() ?: 0.0
            }

            return 0.0
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
                Regex("\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{2,4}\\s+\\d{1,2}:\\d{2}(:\\d{2})?(\\s*[AaPp][Mm])?"),
                // ISO8601-like
                Regex("\\d{4}[/.-]\\d{1,2}[/.-]\\d{1,2}[T\\s]\\d{1,2}:\\d{2}(:\\d{2})?.*")
            )

            return dateTimePatterns.any { it.matches(str.trim()) }
        }

        /**
         * Check if a string might be a glucose value
         */
        private fun looksLikeGlucoseValue(str: String): Boolean {
            // A glucose value should be a number possibly followed by units
            val normalizedStr = str.trim().lowercase(Locale.getDefault())

            // Check if it has numbers
            val hasNumbers = normalizedStr.any { it.isDigit() }

            // Check for common glucose units or just a plain number
            val hasUnits = normalizedStr.contains("mg") ||
                    normalizedStr.contains("dl") ||
                    normalizedStr.contains("mmol")

            // Try to extract a numeric value and check if it's in typical glucose range
            val numericValue = extractNumericValue(normalizedStr)

            // Could be mg/dL or mmol/L, so check both ranges
            val inRange = numericValue in 20.0..600.0 || numericValue in 1.0..25.0

            return hasNumbers && (hasUnits || inRange)
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

        /**
         * Convert mmol/L to mg/dL
         */
        fun mmolLToMgDl(mmol: Double): Double {
            return mmol * 18.0
        }

        /**
         * Convert mg/dL to mmol/L
         */
        fun mgDlToMmolL(mgDl: Double): Double {
            return mgDl / 18.0
        }
    }

    /**
     * Sealed class for glucose file parsing results
     */
    sealed class GlucoseFileResult {
        data class Success(
            val readings: List<GlucoseReading>,
            val format: CsvFormat
        ) : GlucoseFileResult()

        data class Error(val message: String) : GlucoseFileResult()
    }
}

/**
 * Data class representing a glucose reading
 */
data class GlucoseReading(
    val value: Double,
    val timestamp: ZonedDateTime,
    val mealType: Int = androidx.health.connect.client.records.BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN
)

/**
 * Extension function to read and parse a CSV file into GlucoseData objects
 */
suspend fun readCsvFile(context: Context, uri: Uri): List<GlucoseData> {
    val result = GlucoseCsvUtilities.parseGlucoseFile(context, uri)

    return when (result) {
        is GlucoseCsvUtilities.GlucoseFileResult.Success -> {
            // Convert to GlucoseData objects
            result.readings.map { reading ->
                val date = reading.timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                val time = reading.timestamp.format(DateTimeFormatter.ofPattern("HH:mm:ss"))

                GlucoseData(
                    glucose = reading.value.toString(),
                    date = date,
                    time = time
                )
            }
        }
        is GlucoseCsvUtilities.GlucoseFileResult.Error -> {
            Log.e("CSV", "Error reading CSV: ${result.message}")
            emptyList()
        }
    }
}

/**
 * Data class for glucose data used in the app UI
 */
data class GlucoseData(
    val glucose: String,
    val date: String,
    val time: String
)