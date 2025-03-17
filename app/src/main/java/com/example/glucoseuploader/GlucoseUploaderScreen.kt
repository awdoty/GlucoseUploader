package com.example.glucoseuploader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
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

        // Rest of the code...
    }
}

private fun formatDateTime(instant: Instant): String {
    val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy hh:mm a")
    return formatter.format(instant.atZone(ZoneId.systemDefault()))
}