package com.example.glucoseuploader

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.records.BloodGlucoseRecord
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Composable function for CSV import screen that can be used in any activity or composable
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsvImportScreen(
    uri: Uri,
    healthConnectUploader: HealthConnectUploader,
    onImportComplete: (success: Boolean, message: String) -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var csvContent by remember { mutableStateOf<List<String>>(emptyList()) }
    var detectedType by remember { mutableStateOf("Unknown") }
    var readings by remember { mutableStateOf<List<GlucoseReading>>(emptyList()) }
    var uploadStatus by remember { mutableStateOf("") }
    var uploadProgress by remember { mutableStateOf(0f) }
    var isUploading by remember { mutableStateOf(false) }
    var selectedMealType by remember { mutableStateOf(BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN) }
    val coroutineScope = rememberCoroutineScope()

    // Load CSV data when the screen is composed
    LaunchedEffect(uri) {
        try {
            // Create a CsvReader instance to read the file
            val reader = CsvReader(healthConnectUploader.context)
            val csvLines = reader.readCsvFile(uri)
            csvContent = csvLines

            // Parse the CSV content
            val parseResult = reader.parseGenericCsvFormat(csvLines)
            readings = parseResult
            detectedType = reader.detectCsvType(csvLines)

            isLoading = false
        } catch (e: Exception) {
            uploadStatus = "Error reading CSV: ${e.message}"
            onImportComplete(false, "Error reading CSV: ${e.message}")
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Import CSV",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(text = "File Type: $detectedType", style = MaterialTheme.typography.bodyLarge)

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                // Show loading spinner
                CircularProgressIndicator()
                Text(text = "Reading CSV...", style = MaterialTheme.typography.bodyLarge)
            } else {
                // Show parsing results
                if (readings.isEmpty()) {
                    Text(
                        text = "No glucose readings found in the file",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(
                        text = "Found ${readings.size} glucose readings",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Show a sample of readings
                    readings.take(3).forEach { reading ->
                        Text(
                            text = "${reading.timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}: ${reading.value} mg/dL",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }

                    if (readings.size > 3) {
                        Text(
                            text = "... and ${readings.size - 3} more",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Meal Type Selection
                    Text(
                        text = "Select Meal Type for Import",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Meal Type Dropdown
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        Button(onClick = { expanded = true }) {
                            Text(when (selectedMealType) {
                                BloodGlucoseRecord.RELATION_TO_MEAL_BEFORE_MEAL -> "Before Meal"
                                BloodGlucoseRecord.RELATION_TO_MEAL_AFTER_MEAL -> "After Meal"
                                BloodGlucoseRecord.RELATION_TO_MEAL_FASTING -> "Fasting"
                                BloodGlucoseRecord.RELATION_TO_MEAL_GENERAL -> "General"
                                else -> "Unknown Meal Type"
                            })
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Before Meal") },
                                onClick = {
                                    selectedMealType = BloodGlucoseRecord.RELATION_TO_MEAL_BEFORE_MEAL
                                    expanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("After Meal") },
                                onClick = {
                                    selectedMealType = BloodGlucoseRecord.RELATION_TO_MEAL_AFTER_MEAL
                                    expanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Fasting") },
                                onClick = {
                                    selectedMealType = BloodGlucoseRecord.RELATION_TO_MEAL_FASTING
                                    expanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("General") },
                                onClick = {
                                    selectedMealType = BloodGlucoseRecord.RELATION_TO_MEAL_GENERAL
                                    expanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Unknown") },
                                onClick = {
                                    selectedMealType = BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN
                                    expanded = false
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Upload button
                    Button(
                        onClick = {
                            isUploading = true
                            uploadProgress = 0f

                            coroutineScope.launch {
                                try {
                                    uploadStatus = "Uploading readings..."

                                    // Convert readings to doubles and get start/end times
                                    val values = readings.map { it.value }
                                    val startTime = readings.minByOrNull { it.timestamp }?.timestamp
                                        ?: ZonedDateTime.now()
                                    val endTime = readings.maxByOrNull { it.timestamp }?.timestamp
                                        ?: ZonedDateTime.now()

                                    // Create list of meal types for all readings
                                    val mealTypes = List(readings.size) { selectedMealType }

                                    // Upload the readings with progress tracking
                                    healthConnectUploader.uploadGlucoseSeries(
                                        values,
                                        startTime,
                                        endTime,
                                        mealTypes
                                    ) { current, total ->
                                        uploadProgress = current.toFloat() / total.toFloat()
                                        uploadStatus = "Uploaded $current of $total readings"
                                    }

                                    uploadStatus = "Successfully uploaded ${readings.size} readings"
                                    onImportComplete(true, "Successfully uploaded ${readings.size} readings")
                                } catch (e: Exception) {
                                    uploadStatus = "Error: ${e.message}"
                                    onImportComplete(false, "Error: ${e.message}")
                                } finally {
                                    isUploading = false
                                }
                            }
                        },
                        enabled = !isUploading && readings.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Upload ${readings.size} Readings")
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Cancel button
                    OutlinedButton(
                        onClick = { onImportComplete(false, "Import cancelled") },
                        enabled = !isUploading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel")
                    }

                    // Show upload progress
                    if (isUploading) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = uploadStatus)
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = uploadProgress,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

/**
 * Helper function to try parsing a date/time string with multiple formats
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