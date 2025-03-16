package com.example.glucoseuploader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.records.BloodGlucoseRecord
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Screen that shows glucose history from Health Connect
 */

@Composable
fun GlucoseHistoryScreen(
    healthConnectUploader: HealthConnectUploader,
    requestPermissions: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var glucoseRecords by remember { mutableStateOf<List<BloodGlucoseRecord>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isHealthConnectAvailable by remember { mutableStateOf(false) }
    var hasPermissions by remember { mutableStateOf(false) }

    // Time range for the query - last 30 days
    val endTime = Instant.now()
    val startTime = endTime.minus(30, ChronoUnit.DAYS)

    // Check Health Connect availability and permissions
    LaunchedEffect(key1 = Unit) {
        try {
            isHealthConnectAvailable = healthConnectUploader.isHealthConnectAvailable()

            if (isHealthConnectAvailable) {
                hasPermissions = healthConnectUploader.hasPermissions()

                // If we have permissions, load the data
                if (hasPermissions) {
                    loadGlucoseData(
                        healthConnectUploader,
                        startTime,
                        endTime,
                        onLoadingChanged = { isLoading = it },
                        onDataLoaded = { glucoseRecords = it },
                        onError = { errorMessage = it }
                    )
                }
            }
        } catch (e: Exception) {
            errorMessage = "Error initializing: ${e.message}"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Glucose History",
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
                    text = "Last 30 Days",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Showing glucose readings from ${formatDate(startTime)} to ${formatDate(endTime)}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Handle different states
        when {
            !isHealthConnectAvailable -> {
                HealthConnectNotAvailableMessage {
                    coroutineScope.launch {
                        healthConnectUploader.openHealthConnectApp(healthConnectUploader.context)
                    }
                }
            }
            !hasPermissions -> {
                PermissionsRequiredMessage(requestPermissions)
            }
            isLoading -> {
                LoadingIndicator()
            }
            errorMessage != null -> {
                ErrorMessage(errorMessage!!) {
                    errorMessage = null
                    coroutineScope.launch {
                        loadGlucoseData(
                            healthConnectUploader,
                            startTime,
                            endTime,
                            onLoadingChanged = { isLoading = it },
                            onDataLoaded = { glucoseRecords = it },
                            onError = { errorMessage = it }
                        )
                    }
                }
            }
            glucoseRecords.isEmpty() -> {
                NoDataMessage()
            }
            else -> {
                GlucoseRecordsList(glucoseRecords, healthConnectUploader.context.packageName)
            }
        }
    }
}

/**
 * Load glucose data from Health Connect
 */
private suspend fun loadGlucoseData(
    healthConnectUploader: HealthConnectUploader,
    startTime: Instant,
    endTime: Instant,
    onLoadingChanged: (Boolean) -> Unit,
    onDataLoaded: (List<BloodGlucoseRecord>) -> Unit,
    onError: (String) -> Unit
) {
    onLoadingChanged(true)
    try {
        val records = healthConnectUploader.readBloodGlucoseRecords(startTime, endTime)
        // Sort by time descending (newest first)
        val sortedRecords = records.sortedByDescending { it.time }
        onDataLoaded(sortedRecords)
    } catch (e: Exception) {
        onError("Error loading data: ${e.message}")
    } finally {
        onLoadingChanged(false)
    }
}


@Composable
private fun GlucoseRecordsList(records: List<BloodGlucoseRecord>, packageName: String) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth()
    ) {
        items(records) { record ->
            GlucoseRecordItem(record, packageName)
            Divider()
        }
    }
}


@Composable
private fun GlucoseRecordItem(record: BloodGlucoseRecord, packageName: String) {
    val localDateTime = record.time
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()

    val dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")
    val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a")

    val date = localDateTime.format(dateFormatter)
    val time = localDateTime.format(timeFormatter)

    val glucoseValue = record.level.inMilligramsPerDeciliter

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.AccessTime,
                contentDescription = "Time",
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = "$glucoseValue mg/dL",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "$date at $time",
                    style = MaterialTheme.typography.bodyMedium
                )

                // Show data source
                val source = record.metadata.dataOrigin?.packageName ?: "Unknown"
                val isFromThisApp = source == packageName

                Text(
                    text = if (isFromThisApp) "Recorded by this app" else "From external app",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isFromThisApp) Color.Gray else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}


@Composable
private fun LoadingIndicator() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}


@Composable
private fun NoDataMessage() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "No glucose readings found for this time period",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}


@Composable
private fun ErrorMessage(message: String, onRetry: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEEEE))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Error",
                style = MaterialTheme.typography.titleLarge,
                color = Color.Red
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}


@Composable
private fun HealthConnectNotAvailableMessage(onInstallClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Health Connect Not Available",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Health Connect is required to view your glucose history",
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = onInstallClick) {
                Text("Install Health Connect")
            }
        }
    }
}


@Composable
private fun PermissionsRequiredMessage(onRequestPermissions: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Permissions Required",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Permission to read glucose data is required to view your history",
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = onRequestPermissions) {
                Text("Grant Permissions")
            }
        }
    }
}

private fun formatDate(instant: Instant): String {
    val date = instant.atZone(ZoneId.systemDefault()).toLocalDate()
    return date.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
}