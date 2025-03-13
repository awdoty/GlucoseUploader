package com.example.glucoseuploader

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.records.BloodGlucoseRecord
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Screen for importing glucose data from CSV files shared from AgaMatrix
 */
@Composable
fun CsvImportScreen(
    uri: Uri,
    healthConnectUploader: HealthConnectUploader,
    onImportComplete: (success: Boolean, message: String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var glucoseData by remember { mutableStateOf<List<GlucoseData>>(emptyList()) }
    var isUploading by remember { mutableStateOf(false) }

    // Load CSV data when screen is shown
    LaunchedEffect(uri) {
        try {
            // Read CSV file
            val data = readCsvFile(context, uri)
            glucoseData = data
            error = if (data.isEmpty()) "No glucose readings found in file" else null
        } catch (e: Exception) {
            error = "Error reading file: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Import Glucose Readings",
            style = MaterialTheme.typography.h5,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        when {
            isLoading -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text("Reading file...")
            }
            error != null -> {
                ErrorCard(error!!)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { onImportComplete(false, error!!) }) {
                    Text("Close")
                }
            }
            else -> {
                // Show preview of data
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = 4.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Found ${glucoseData.size} glucose readings",
                            style = MaterialTheme.typography.h6
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text("Preview:")

                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        ) {
                            items(glucoseData.take(10)) { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("${item.date} ${item.time}")
                                    Text("${item.glucose} mg/dL")
                                }
                                Divider()
                            }

                            if (glucoseData.size > 10) {
                                item {
                                    Text(
                                        text = "... and ${glucoseData.size - 10} more readings",
                                        style = MaterialTheme.typography.caption,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Meal Type Selection
                        var selectedMealType by remember { mutableIntStateOf(BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN) }

                        Text(
                            text = "Select Meal Type for Import",
                            style = MaterialTheme.typography.subtitle1,
                            fontWeight = FontWeight.Bold
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
                                DropdownMenuItem(onClick = {
                                    selectedMealType = BloodGlucoseRecord.RELATION_TO_MEAL_BEFORE_MEAL
                                    expanded = false
                                }) {
                                    Text("Before Meal")
                                }
                                DropdownMenuItem(onClick = {
                                    selectedMealType = BloodGlucoseRecord.RELATION_TO_MEAL_AFTER_MEAL
                                    expanded = false
                                }) {
                                    Text("After Meal")
                                }
                                DropdownMenuItem(onClick = {
                                    selectedMealType = BloodGlucoseRecord.RELATION_TO_MEAL_FASTING
                                    expanded = false
                                }) {
                                    Text("Fasting")
                                }
                                DropdownMenuItem(onClick = {
                                    selectedMealType = BloodGlucoseRecord.RELATION_TO_MEAL_GENERAL
                                    expanded = false
                                }) {
                                    Text("General")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Button(
                                onClick = { onImportComplete(false, "Import cancelled") },
                                colors = ButtonDefaults.buttonColors(backgroundColor = Color.Gray)
                            ) {
                                Text("Cancel")
                            }

                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        isUploading = true
                                        try {
                                            // Pass selected meal type during upload
                                            uploadGlucoseReadings(
                                                context,
                                                healthConnectUploader,
                                                glucoseData,
                                                selectedMealType
                                            )
                                            onImportComplete(true, "Successfully imported ${glucoseData.size} glucose readings")
                                        } catch (e: Exception) {
                                            onImportComplete(false, "Error uploading data: ${e.message}")
                                        } finally {
                                            isUploading = false
                                        }
                                    }
                                },
                                enabled = !isUploading && glucoseData.isNotEmpty()
                            ) {
                                if (isUploading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = MaterialTheme.colors.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text("Import All Readings")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Upload glucose readings to Health Connect
 */
private suspend fun uploadGlucoseReadings(
    context: Context,
    healthConnectUploader: HealthConnectUploader,
    glucoseData: List<GlucoseData>,
    mealType: Int
) {
    // Convert to a list of time-value pairs with meal type
    val readings = glucoseData.mapNotNull { data ->
        try {
            // Parse date and time
            val dateTimeStr = "${data.date} ${data.time}"
            // Try different common date formats
            val dateTime = tryParseDateTime(dateTimeStr)

            // Convert to ZonedDateTime
            val zonedDateTime = ZonedDateTime.of(
                dateTime,
                ZoneId.systemDefault()
            )

            // Include meal type in the upload
            Triple(zonedDateTime, data.glucose.toDouble(), mealType)
        } catch (e: Exception) {
            // Skip unparseable entries
            null
        }
    }

    // Get min and max times for the series
    if (readings.isNotEmpty()) {
        val startTime = readings.minByOrNull { it.first.toInstant().toEpochMilli() }?.first ?: return
        val endTime = readings.maxByOrNull { it.first.toInstant().toEpochMilli() }?.first ?: return

        // Get just the glucose values and meal types
        val values = readings.map { it.second }
        val types = readings.map { it.third }

        // Upload as a series with meal type
        healthConnectUploader.uploadGlucoseSeries(
            values = values,
            startTime = startTime,
            endTime = endTime,
            mealTypes = types
        )
    }
}

// ... rest of the file remains the same

/**
 * Try to parse a date-time string with different common formats
 */
private fun tryParseDateTime(dateTimeStr: String): LocalDateTime {
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