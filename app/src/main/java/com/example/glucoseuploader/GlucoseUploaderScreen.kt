package com.example.glucoseuploader

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Main screen for uploading glucose readings
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlucoseUploaderScreen(
    healthConnectUploader: HealthConnectUploader,
    requestPermissions: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // State variables
    var glucoseValue by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("Checking Health Connect...") }
    var isHealthConnectAvailable by remember { mutableStateOf(false) }
    var hasPermissions by remember { mutableStateOf(false) }
    var initAttempt by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var latestGlucoseReading by remember { mutableStateOf<Pair<Double, Instant>?>(null) }

    // Use LaunchedEffect to check status whenever initAttempt changes
    LaunchedEffect(key1 = initAttempt) {
        try {
            Log.d("GlucoseUploader", "Checking Health Connect availability (attempt: $initAttempt)")
            isHealthConnectAvailable = healthConnectUploader.isHealthConnectAvailable()
            Log.d("GlucoseUploader", "Health Connect available: $isHealthConnectAvailable")

            if (isHealthConnectAvailable) {
                hasPermissions = healthConnectUploader.hasPermissions()
                Log.d("GlucoseUploader", "Health Connect permissions: $hasPermissions")

                statusMessage = if (hasPermissions) {
                    "Ready to upload (permissions granted)"
                } else {
                    "Health Connect permissions needed"
                }

                // Fetch latest reading if we have permissions
                if (hasPermissions) {
                    isLoading = true
                    try {
                        val latestRecord = healthConnectUploader.readLatestBloodGlucoseRecord()
                        latestRecord?.let {
                            latestGlucoseReading = Pair(it.level.inMilligramsPerDeciliter, it.time)
                        }
                    } catch (e: Exception) {
                        Log.e("GlucoseUploader", "Error fetching latest reading: ${e.message}")
                    } finally {
                        isLoading = false
                    }
                }
            } else {
                statusMessage = "Health Connect not available - please install from Play Store"
            }
        } catch (e: Exception) {
            Log.e("GlucoseUploader", "Error during initialization: ${e.message}", e)
            statusMessage = "Error: ${e.message}"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "Glucose Uploader",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Status Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Status",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Status",
                        tint = if (isHealthConnectAvailable) Color.Green else Color.Red
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                // Show last reading if available
                latestGlucoseReading?.let { (value, time) ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Last Reading",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "$value mg/dL on ${formatDateTime(time)}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Input Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Input Glucose Reading",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = glucoseValue,
                    onValueChange = {
                        // Only allow numbers and decimal point
                        if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d*$"))) {
                            glucoseValue = it
                        }
                    },
                    label = { Text("Glucose value (mg/dL)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Try both methods
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Direct API upload (needs permissions)
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isLoading = true
                                try {
                                    val value = glucoseValue.toDoubleOrNull()
                                    if (value != null) {
                                        healthConnectUploader.uploadBloodGlucose(value, Instant.now())
                                        statusMessage = "Uploaded: $value mg/dL"

                                        // Refresh the latest reading
                                        val latestRecord = healthConnectUploader.readLatestBloodGlucoseRecord()
                                        latestRecord?.let {
                                            latestGlucoseReading = Pair(it.level.inMilligramsPerDeciliter, it.time)
                                        }
                                    } else {
                                        statusMessage = "Invalid glucose value"
                                    }
                                } catch (e: Exception) {
                                    statusMessage = "Error: ${e.message}"
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        enabled = isHealthConnectAvailable && hasPermissions && glucoseValue.isNotEmpty() && !isLoading
                    ) {
                        if (isLoading) {
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Upload with API")
                    }

                    // Intent-based upload (doesn't need permissions)
                    Button(
                        onClick = {
                            try {
                                val value = glucoseValue.toDoubleOrNull()
                                if (value != null) {
                                    if (healthConnectUploader.addGlucoseViaIntent(context, value)) {
                                        statusMessage = "Opened Health Connect for: $value mg/dL"
                                    } else {
                                        statusMessage = "Failed to open Health Connect"
                                    }
                                } else {
                                    statusMessage = "Invalid glucose value"
                                }
                            } catch (e: Exception) {
                                statusMessage = "Error: ${e.message}"
                            }
                        },
                        enabled = isHealthConnectAvailable && glucoseValue.isNotEmpty() && !isLoading
                    ) {
                        Text("Add via Health Connect")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Setup Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Health Connect Setup",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (!isHealthConnectAvailable) {
                    // Button to install Health Connect
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                try {
                                    healthConnectUploader.openHealthConnectApp(context)
                                    statusMessage = "Opening Health Connect..."
                                    // Increment to check status after returning
                                    initAttempt++
                                } catch (e: Exception) {
                                    statusMessage = "Error: ${e.message}"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Install/Open Health Connect")
                    }
                } else if (!hasPermissions) {
                    // Button to request permissions
                    Button(
                        onClick = {
                            try {
                                requestPermissions()
                                statusMessage = "Requesting Health Connect permissions..."
                                // Increment to check permission status after returning
                                initAttempt++
                            } catch (e: Exception) {
                                statusMessage = "Error: ${e.message}"
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Request Health Connect Permissions")
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Or open Health Connect directly:",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Add alternative button to open Health Connect directly
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                try {
                                    healthConnectUploader.openHealthConnectApp(context)
                                    statusMessage = "Opening Health Connect app..."
                                    // Increment to check status after returning
                                    initAttempt++
                                } catch (e: Exception) {
                                    statusMessage = "Error: ${e.message}"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open Health Connect Settings")
                    }
                } else {
                    // Permissions granted - refresh button
                    Button(
                        onClick = {
                            // Increment to trigger the LaunchedEffect again
                            initAttempt++
                            statusMessage = "Refreshing..."
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Refresh Status")
                    }
                }
            }
        }

        // Debug section
        if (statusMessage.startsWith("Error:")) {
            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEEEE))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Troubleshooting",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.Red
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            initAttempt++  // Increment to trigger the LaunchedEffect again
                            statusMessage = "Retrying..."
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Retry")
                    }
                }
            }
        }

        // Import CSV Section
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Import from CSV",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "You can import glucose readings from CSV files shared from other apps.",
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(16.dp))

                FilePickerButton(
                    text = "Select CSV File",
                    mimeType = "text/csv",
                    onFileSelected = { uri ->
                        // Launch CSV import activity
                        val intent = android.content.Intent(context, CsvImportActivity::class.java).apply {
                            action = android.content.Intent.ACTION_VIEW
                            data = uri
                        }
                        context.startActivity(intent)
                    }
                )
            }
        }

        // Add empty space at the bottom for better scrolling
        Spacer(modifier = Modifier.height(32.dp))
    }
}

private fun formatDateTime(instant: Instant): String {
    val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy hh:mm a")
    return formatter.format(instant.atZone(ZoneId.systemDefault()))
}