package com.example.glucoseuploader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
 * UI section for historical data access
 */
@Composable
fun HistoricalDataSection(
    healthConnectUploader: HealthConnectUploader
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // State variables
    var isHistoryReadAvailable by remember { mutableStateOf(false) }
    var hasHistoryReadPermission by remember { mutableStateOf(false) }
    var historicalRecords by remember { mutableStateOf<List<BloodGlucoseRecord>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showHistoricalData by remember { mutableStateOf(false) }

    // Check feature availability and permission status
    LaunchedEffect(key1 = Unit) {
        try {
            isHistoryReadAvailable = true // Simplified approach - always assume available
            hasHistoryReadPermission = healthConnectUploader.hasHistoryReadPermission()
        } catch (e: Exception) {
            errorMessage = "Error checking permissions: ${e.message}"
        }
    }

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
                text = "Historical Glucose Data",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (!isHistoryReadAvailable) {
                Text(
                    text = "Historical data reading is not available on this device. Please update Health Connect.",
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                if (!hasHistoryReadPermission) {
                    Text(
                        text = "Permission to read historical data (older than 30 days) is required for long-term trend analysis.",
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            try {
                                healthConnectUploader.requestHistoryReadPermission(
                                    context as androidx.activity.ComponentActivity
                                )
                            } catch (e: Exception) {
                                errorMessage = "Error requesting permission: ${e.message}"
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "History"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Request Historical Data Permission")
                    }
                } else {
                    // Permission is granted, show historical data button
                    Text(
                        text = "You have permission to access historical glucose data. Load data from the past 90 days.",
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                try {
                                    loadHistoricalData(
                                        healthConnectUploader,
                                        onLoadingChanged = { isLoading = it },
                                        onDataLoaded = {
                                            historicalRecords = it
                                            showHistoricalData = true
                                        },
                                        onError = { errorMessage = it }
                                    )
                                } catch (e: Exception) {
                                    errorMessage = "Error loading data: ${e.message}"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colors.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        } else {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "Load"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Load Historical Data (90 Days)")
                    }

                    // Show error message if any
                    errorMessage?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    // Show historical data if available
                    if (showHistoricalData) {
                        Spacer(modifier = Modifier.height(16.dp))

                        // Group records by month
                        val groupedRecords = historicalRecords.groupBy { record ->
                            val date = record.time.atZone(ZoneId.systemDefault()).toLocalDate()
                            YearMonth(date.year, date.monthValue)
                        }

                        if (groupedRecords.isEmpty()) {
                            Text(
                                text = "No historical glucose data found.",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        } else {
                            Text(
                                text = "Historical Data Summary",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Show summary by month
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                            ) {
                                items(groupedRecords.entries.sortedByDescending { it.key.year * 100 + it.key.month }) { (yearMonth, records) ->
                                    val avgGlucose = records.map { it.level.inMilligramsPerDeciliter }.average()
                                    val minGlucose = records.minOfOrNull { it.level.inMilligramsPerDeciliter }
                                    val maxGlucose = records.maxOfOrNull { it.level.inMilligramsPerDeciliter }

                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        elevation = 2.dp
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(8.dp)
                                        ) {
                                            Text(
                                                text = "${yearMonth.month}/${yearMonth.year}",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )

                                            Spacer(modifier = Modifier.height(4.dp))

                                            Text(
                                                text = "Readings: ${records.size}",
                                                style = MaterialTheme.typography.bodyMedium
                                            )

                                            Text(
                                                text = "Avg: ${String.format("%.1f", avgGlucose)} mg/dL",
                                                style = MaterialTheme.typography.bodyMedium
                                            )

                                            Text(
                                                text = "Range: ${String.format("%.1f", minGlucose)} - ${String.format("%.1f", maxGlucose)} mg/dL",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Load historical glucose data
 */
private suspend fun loadHistoricalData(
    healthConnectUploader: HealthConnectUploader,
    onLoadingChanged: (Boolean) -> Unit,
    onDataLoaded: (List<BloodGlucoseRecord>) -> Unit,
    onError: (String) -> Unit
) {
    onLoadingChanged(true)
    try {
        // Read data from the past 90 days
        val endTime = Instant.now()
        val startTime = endTime.minus(90, ChronoUnit.DAYS)

        val records = healthConnectUploader.readHistoricalBloodGlucoseRecords(startTime, endTime)
        onDataLoaded(records)
    } catch (e: Exception) {
        onError("Error loading historical data: ${e.message}")
    } finally {
        onLoadingChanged(false)
    }
}

/**
 * Simple data class to hold year and month
 */
data class YearMonth(val year: Int, val month: Int)