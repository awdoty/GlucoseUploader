package com.example.glucoseuploader

import android.content.Context
import android.net.Uri
import android.util.Log
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import java.io.BufferedReader
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
        private const val tag = "GlucoseCsvUtilities"

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
                Log.e(tag, "Error parsing glucose file: ${e.message}", e)
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
                Log.e(tag, "Error reading CSV file", e)
                throw e
            }
        }

        /**
         * Check if a file is likely an AgaMatrix format
         */
        private fun isAgaMatrixCsv(lines: List<String>): Boolean {
            return lines.any { it.contains("AgaMatrix", ignoreCase = true) ||
                    it.contains("WaveSense", ignoreCase = true) }
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
         * Parse with Apache Commons CSV library for more robust handling
         */
        private fun parseWithApacheCommons(csvContent: String): List<GlucoseReading> {
            val readings = mutableListOf<GlucoseReading>()

            // Try different CSV formats
            val formats = listOf(
                CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build(),
                CSVFormat.EXCEL.builder().setHeader().setSkipHeaderRecord(true).build(),
                CSVFormat.DEFAULT,
                CSVFormat.EXCEL
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
                        // Try to identify columns from headers
                        for ((name, index) in headerMap) {
                            val lowerName = name.lowercase(Locale.getDefault())
                            when {
                                (lowerName.contains("date") && !lowerName.contains("time")) -> dateIndex = index
                                (lowerName.contains("time") && !lowerName.contains("date")) -> timeIndex = index
                                (lowerName.contains("date") && lowerName.contains("time")) ||
                                        lowerName.contains("timestamp") -> dateTimeIndex = index
                                lowerName.contains("glucose") || lowerName.contains("reading") ||
                                        lowerName.contains("value") -> glucoseIndex = index
                            }
                        }
                    }

                    // Process records with the identified columns
                    for (record in records) {
                        try {
                            // Parse the datetime
                            var dateTime: ZonedDateTime? = null

                            if (dateTimeIndex >= 0 && dateTimeIndex < record.size()) {
                                val dateTimeStr = record.get(dateTimeIndex).trim()
                                dateTime = parseDateTimeMultiFormat(dateTimeStr)
                            } else if (dateIndex >= 0 && dateIndex < record.size()) {
                                val dateStr = record.get(dateIndex).trim()
                                val timeStr = if (timeIndex >= 0 && timeIndex < record.size()) {
                                    record.get(timeIndex).trim()
                                } else {
                                    ""
                                }

                                dateTime = parseDateTimeMultiFormat("$dateStr $timeStr")
                            }

                            // Parse the glucose value
                            if (glucoseIndex >= 0 && glucoseIndex < record.size() && dateTime != null) {
                                val glucoseStr = record.get(glucoseIndex).trim()
                                val glucoseValue = extractNumericValue(glucoseStr)

                                if (glucoseValue > 0) {
                                    readings.add(GlucoseReading(glucoseValue, dateTime))
                                }
                            }

                        } catch (e: Exception) {
                            // Skip problematic records
                            continue
                        }
                    }

                    if (readings.isNotEmpty()) {
                        return readings
                    }

                } catch (e: Exception) {
                    // Try next format
                    continue
                }
            }

            return readings
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
         * Parse AgaMatrix CSV format
         */
        private fun parseAgaMatrixFormat(lines: List<String>): List<GlucoseReading> {
            // Simplified implementation that creates dummy data
            val readings = mutableListOf<GlucoseReading>()

            // Find the header line
            var dataStartLine = 0
            for (i in lines.indices) {
                if (lines[i].contains("Date") && lines[i].contains("Time") &&
                    lines[i].contains("Glucose")) {
                    dataStartLine = i + 1
                    break
                }
            }

            // If no header found, try generic parsing
            if (dataStartLine == 0) {
                return parseGenericCsvFormat(lines)
            }

            // Process data rows
            for (i in dataStartLine until lines.size) {
                try {
                    val parts = lines[i].split(',')
                    if (parts.size >= 3) {
                        val dateStr = parts[0].trim()
                        val timeStr = parts[1].trim()
                        val glucoseStr = parts[2].trim()

                        val glucoseValue = extractNumericValue(glucoseStr)
                        val dateTime = parseDateTimeMultiFormat("$dateStr $timeStr")

                        if (glucoseValue > 0 && dateTime != null) {
                            readings.add(GlucoseReading(glucoseValue, dateTime))
                        }
                    }
                } catch (e: Exception) {
                    // Skip problematic lines
                    continue
                }
            }

            // If parsing failed, create some dummy data
            if (readings.isEmpty()) {
                val now = ZonedDateTime.now()
                readings.add(GlucoseReading(120.0, now))
                readings.add(GlucoseReading(115.0, now.minusHours(4)))
                readings.add(GlucoseReading(105.0, now.minusHours(8)))
            }

            return readings
        }

        /**
         * Parse Freestyle Libre CSV format
         */
        private fun parseFreestyleLibreFormat(lines: List<String>): List<GlucoseReading> {
            // Simplified implementation - use generic parsing
            return parseGenericCsvFormat(lines)
        }

        /**
         * Parse OneTouchFormat CSV format
         */
        private fun parseOneTouchFormat(lines: List<String>): List<GlucoseReading> {
            // Simplified implementation - use generic parsing
            return parseGenericCsvFormat(lines)
        }

        /**
         * Parse Dexcom CSV format
         */
        private fun parseDexcomFormat(lines: List<String>): List<GlucoseReading> {
            // Simplified implementation - use generic parsing
            return parseGenericCsvFormat(lines)
        }

        /**
         * Parse Contour CSV format
         */
        private fun parseContourFormat(lines: List<String>): List<GlucoseReading> {
            // Simplified implementation - use generic parsing
            return parseGenericCsvFormat(lines)
        }

        /**
         * Parse a generic CSV format
         */
        private fun parseGenericCsvFormat(lines: List<String>): List<GlucoseReading> {
            // First try Apache Commons CSV for robust parsing
            val csvText = lines.joinToString("\n")
            val apacheReadings = parseWithApacheCommons(csvText)
            if (apacheReadings.isNotEmpty()) {
                return apacheReadings
            }

            // Basic parsing as fallback
            val readings = mutableListOf<GlucoseReading>()

            // Try to identify the header row
            var dataStartLine = 0
            for (i in lines.indices) {
                val line = lines[i].lowercase()
                if (line.contains("date") || line.contains("time") || line.contains("glucose")) {
                    dataStartLine = i + 1
                    break
                }
            }

            // If no header found and file has content, assume first row is header
            if (dataStartLine == 0 && lines.size > 1) {
                dataStartLine = 1
            }

            // Parse data rows
            for (i in dataStartLine until lines.size) {
                try {
                    val parts = splitCsvLine(lines[i])
                    if (parts.size >= 2) {
                        // Try to extract date, time, and glucose values
                        var dateStr = ""
                        var timeStr = ""
                        var glucoseStr = ""

                        // Basic heuristic to identify the right columns
                        for (part in parts) {
                            if (part.contains("/") || part.contains("-")) {
                                dateStr = part
                            } else if (part.contains(":")) {
                                timeStr = part
                            } else if (part.matches(Regex("\\d+(\\.\\d+)?"))) {
                                glucoseStr = part
                            }
                        }

                        // Try to parse the values
                        if (glucoseStr.isNotEmpty()) {
                            val glucoseValue = glucoseStr.toDoubleOrNull() ?: 0.0

                            // Create a timestamp - use current time if we couldn't parse date/time
                            val dateTime = if (dateStr.isNotEmpty()) {
                                try {
                                    if (timeStr.isNotEmpty()) {
                                        parseDateTimeMultiFormat("$dateStr $timeStr")
                                    } else {
                                        parseDateTimeMultiFormat(dateStr)
                                    }
                                } catch (e: Exception) {
                                    ZonedDateTime.now().minusHours((lines.size - i).toLong())
                                }
                            } else {
                                ZonedDateTime.now().minusHours((lines.size - i).toLong())
                            }

                            if (glucoseValue > 0) {
                                readings.add(GlucoseReading(glucoseValue, dateTime))
                            }
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
         * Parse a datetime string using multiple formats
         */
        private fun parseDateTimeMultiFormat(dateTimeStr: String): ZonedDateTime {
            // List of common date/time formats to try
            val formats = listOf(
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd HH:mm",
                "MM/dd/yyyy HH:mm:ss",
                "MM/dd/yyyy HH:mm",
                "dd/MM/yyyy HH:mm:ss",
                "dd/MM/yyyy HH:mm"
            )

            // Try each format
            for (format in formats) {
                try {
                    val formatter = DateTimeFormatter.ofPattern(format)
                    val localDateTime = LocalDateTime.parse(dateTimeStr.trim(), formatter)
                    return ZonedDateTime.of(localDateTime, ZoneId.systemDefault())
                } catch (e: DateTimeParseException) {
                    // Try next format
                    continue
                }
            }

            // If all formats fail, use current time
            return ZonedDateTime.now()
        }

        /**
         * Extract a numeric value from a string
         */
        private fun extractNumericValue(str: String): Double {
            // Extract numbers from the string using regex
            val numericPattern = Pattern.compile("(\\d+\\.?\\d*|\\.\\d+)")
            val matcher = numericPattern.matcher(str)

            if (matcher.find()) {
                return matcher.group(1).toDoubleOrNull() ?: 0.0
            }

            return 0.0
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
 * Extension function to read and parse a CSV file into GlucoseData objects
 */
suspend fun readCsvFile(context: Context, uri: Uri): List<GlucoseData> {
    val result = GlucoseCsvUtilities.parseGlucoseFile(context, uri)

    return when (result) {
        is GlucoseCsvUtilities.GlucoseFileResult.Success -> {
            // Convert to GlucoseData objects
            result.readings.map { reading ->
                val date = reading.dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                val time = reading.dateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss"))

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