package com.example.glucoseuploader

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import android.util.Log
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
    var selectedFile by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("No file selected") }

    // File picker launcher
    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            selectedFile = it
            // Get file name from URI
            context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        selectedFileName = cursor.getString(nameIndex)
                    }
                }
            }
        }
    }

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
                        tint = if (isHealthConnectAvailable && hasPermissions) Color.Green
                        else if (isHealthConnectAvailable) Color(0xFFFFA000)
                        else Color.Red
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

        // If Health Connect is not available, show install button
        if (!isHealthConnectAvailable) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Health Connect Required",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color(0xFFE65100)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "This app requires Health Connect to store and manage your glucose readings.",
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                healthConnectUploader.openHealthConnectApp(context)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.GetApp,
                            contentDescription = "Install"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Install Health Connect")
                    }
                }
            }
        }
        // If Health Connect is available but permissions are not granted
        else if (!hasPermissions) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8E9)),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Permissions Required",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color(0xFF33691E)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Permission to read and write glucose data is required.",
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = requestPermissions,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Permissions"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Grant Permissions")
                    }
                }
            }
        }
        // If Health Connect is available and permissions are granted
        else {
            // CSV Upload Card
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
                        text = "Upload CSV File",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Import glucose readings from a CSV file exported from your glucose meter (like AgaMatrix).",
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Selected file information
                    if (selectedFile != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = "File",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = selectedFileName,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                fileLauncher.launch(
                                    arrayOf(
                                        "text/csv",
                                        "text/comma-separated-values",
                                        "application/csv",
                                        "application/vnd.ms-excel",
                                        "text/plain",
                                        "*/*"
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = "Select File"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Select CSV File")
                        }

                        Button(
                            onClick = {
                                selectedFile?.let { uri ->
                                    val importIntent = Intent(context, CsvImportActivity::class.java).apply {
                                        action = Intent.ACTION_VIEW
                                        data = uri
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(importIntent)
                                }
                            },
                            enabled = selectedFile != null,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Upload,
                                contentDescription = "Upload"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Upload File")
                        }
                    }
                }
            }

            // Manual Entry Card
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
                        text = "Manual Entry",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Enter a single glucose reading manually.",
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = glucoseValue,
                        onValueChange = { newValue ->
                            // Only allow numeric input with decimal point
                            if (newValue.isEmpty() || newValue.matches(Regex("^\\d*\\.?\\d*$"))) {
                                glucoseValue = newValue
                            }
                        },
                        label = { Text("Glucose Value (mg/dL)") },
                        placeholder = { Text("e.g., 120") },
                        // Using simple keyboard type without KeyboardOptions
                        // to avoid compatibility issues
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            val value = glucoseValue.toDoubleOrNull()
                            if (value != null && value > 0) {
                                coroutineScope.launch {
                                    try {
                                        isLoading = true
                                        val recordId = healthConnectUploader.uploadBloodGlucose(
                                            value = value,
                                            time = Instant.now()
                                        )
                                        statusMessage = "Successfully uploaded reading: $value mg/dL (ID: $recordId)"

                                        // Add this reading to latestGlucoseReading
                                        latestGlucoseReading = Pair(value, Instant.now())

                                        // Clear the field
                                        glucoseValue = ""
                                    } catch (e: Exception) {
                                        statusMessage = "Error uploading: ${e.message}"
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            } else {
                                statusMessage = "Please enter a valid glucose value"
                            }
                        },
                        enabled = !isLoading && glucoseValue.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Uploading...")
                        } else {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Reading"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add Reading")
                        }
                    }
                }
            }

            // Instructions Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "How to Import CSV Files",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "To import readings from AgaMatrix or other glucose meters:",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "1. Export data from your meter app as a CSV file\n" +
                                "2. Share the file directly to this app or select it using the 'Select CSV File' button\n" +
                                "3. Review and upload the readings\n\n" +
                                "This app works with AgaMatrix, OneTouch, FreeStyle Libre, and other standard CSV formats.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // Refresh button
        Button(
            onClick = {
                initAttempt++  // This triggers the LaunchedEffect to recheck permissions
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Refresh"
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Refresh Status")
        }

        // Add some bottom padding for better scrolling
        Spacer(modifier = Modifier.height(16.dp))
    }
}

private fun formatDateTime(instant: Instant): String {
    val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy hh:mm a")
    return formatter.format(instant.atZone(ZoneId.systemDefault()))
}