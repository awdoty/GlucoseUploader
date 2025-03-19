package com.example.glucoseuploader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import androidx.health.connect.client.records.BloodGlucoseRecord

@Composable
fun CsvImportScreen(
    uri: Uri,
    healthConnectUploader: HealthConnectUploader,
    onImportComplete: (success: Boolean, message: String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var isLoading by remember { mutableStateOf(true) }
    var readings by remember { mutableStateOf<List<GlucoseReading>>(emptyList()) }
    var detectedFormat by remember { mutableStateOf("Detecting...") }
    var uploadStatus by remember { mutableStateOf("") }
    var uploadProgress by remember { mutableFloatStateOf(0f) }
    var isUploading by remember { mutableStateOf(false) }
    var selectedMealType by remember { mutableIntStateOf(BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isHealthConnectAvailable by remember { mutableStateOf(false) }
    var hasPermissions by remember { mutableStateOf(false) }

    // Load CSV data when the screen is composed
    LaunchedEffect(uri) {
        try {
            // Check Health Connect status first
            isHealthConnectAvailable = healthConnectUploader.isHealthConnectAvailable()
            if (isHealthConnectAvailable) {
                hasPermissions = healthConnectUploader.hasPermissions()
            }

            // Try to read the file content using the specialized parser
            try {
                val parser = AgaMatrixCsvParser(context)
                readings = parser.parseAgaMatrixCsv(uri)

                detectedFormat = when {
                    readings.isEmpty() -> "Unknown format (no data found)"
                    readings.size == 1 -> "CSV with 1 glucose reading"
                    else -> "CSV with ${readings.size} glucose readings"
                }

                if (readings.isEmpty()) {
                    errorMessage = "No valid glucose readings found in this file"
                }
            } catch (e: Exception) {
                Log.e("CsvImport", "Error parsing file: ${e.message}")
                errorMessage = "Error parsing CSV file: ${e.message}"
            }

            isLoading = false
        } catch (e: Exception) {
            errorMessage = "Error importing CSV: ${e.message}"
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Import Glucose Readings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // File information card
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "File Information",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Detected Format: $detectedFormat",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Health Connect Status Card
        if (!isHealthConnectAvailable || !hasPermissions) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (!isHealthConnectAvailable)
                        Color(0xFFFFF3E0) else Color(0xFFF1F8E9)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = if (!isHealthConnectAvailable)
                                Color(0xFFE65100) else Color(0xFF33691E)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = if (!isHealthConnectAvailable)
                                "Health Connect Required" else "Permissions Required",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (!isHealthConnectAvailable)
                                Color(0xFFE65100) else Color(0xFF33691E)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (!isHealthConnectAvailable)
                            "Health Connect is required to upload glucose readings."
                        else
                            "Permission to write glucose data is needed."
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            onImportComplete(false, "Please grant permissions and try again")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (!isHealthConnectAvailable)
                                "Install Health Connect" else "Request Permissions"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Main content
        if (isLoading) {
            // Show loading spinner
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Reading and processing CSV file...",
                style = MaterialTheme.typography.bodyLarge
            )
        } else if (errorMessage != null) {
            // Show error message
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEEEE)),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Error",
                            tint = Color.Red
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Error",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.Red
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = errorMessage ?: "Unknown error",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { onImportComplete(false, errorMessage!!) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Close")
            }
        } else {
            // Show parsing results
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    if (readings.isEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Warning",
                                tint = Color.Red
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = "No glucose readings found in the file",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.Red
                            )
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Success",
                                tint = Color.Green
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = "Found ${readings.size} glucose readings",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.Green
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Show a sample of readings
                        Text(
                            text = "Sample Readings:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                        readings.take(5).forEach { reading ->
                            Text(
                                text = "${reading.dateTime.format(formatter)}: ${reading.value} mg/dL",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        if (readings.size > 5) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "... and ${readings.size - 5} more",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Meal Type Selection
                        Column {
                            Text(
                                text = "Select Meal Type for Import",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Meal Type Radio Buttons
                            val mealTypes = listOf(
                                Pair(BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN, "Unknown"),
                                Pair(BloodGlucoseRecord.RELATION_TO_MEAL_BEFORE_MEAL, "Before Meal"),
                                Pair(BloodGlucoseRecord.RELATION_TO_MEAL_AFTER_MEAL, "After Meal"),
                                Pair(BloodGlucoseRecord.RELATION_TO_MEAL_FASTING, "Fasting"),
                                Pair(BloodGlucoseRecord.RELATION_TO_MEAL_GENERAL, "General")
                            )

                            Column {
                                mealTypes.forEach { (type, name) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = selectedMealType == type,
                                            onClick = { selectedMealType = type }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(name)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            if (readings.isNotEmpty() && isHealthConnectAvailable && hasPermissions) {
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
                                val startTime = readings.minByOrNull { it.dateTime }?.dateTime
                                    ?: ZonedDateTime.now()
                                val endTime = readings.maxByOrNull { it.dateTime }?.dateTime
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
                    if (isUploading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Upload ${readings.size} Readings")
                }

                // Show upload progress if uploading
                if (isUploading) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = uploadStatus)
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { uploadProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Cancel button
            OutlinedButton(
                onClick = { onImportComplete(false, "Import cancelled") },
                enabled = !isUploading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel")
            }
        }
    }
}