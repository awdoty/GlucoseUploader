package com.example.glucoseuploader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Screen for displaying glucose statistics
 */

@Composable
fun GlucoseStatisticsScreen(
    healthConnectUploader: HealthConnectUploader,
    requestPermissions: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // State variables
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var statistics by remember { mutableStateOf<List<GlucoseStatistics>>(emptyList()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var dateStatistics by remember { mutableStateOf<GlucoseStatistics?>(null) }
    var isHealthConnectAvailable by remember { mutableStateOf(false) }
    var hasPermissions by remember { mutableStateOf(false) }

    // Check health connect status
    LaunchedEffect(key1 = Unit) {
        try {
            isHealthConnectAvailable = healthConnectUploader.isHealthConnectAvailable()

            if (isHealthConnectAvailable) {
                hasPermissions = healthConnectUploader.hasPermissions()

                // Load data if permissions are granted
                if (hasPermissions) {
                    loadStatistics(
                        healthConnectUploader,
                        onLoadingChanged = { isLoading = it },
                        onDataLoaded = { statistics = it },
                        onError = { errorMessage = it }
                    )
                }
            }
        } catch (e: Exception) {
            errorMessage = "Error: ${e.message}"
        }
    }

    // Function to load date-specific statistics
    fun loadDateStatistics() {
        coroutineScope.launch {
            try {
                isLoading = true
                errorMessage = null

                dateStatistics = healthConnectUploader.getDayGlucoseStatistics(selectedDate)
            } catch (e: Exception) {
                errorMessage = "Error loading date statistics: ${e.message}"
                dateStatistics = null
            } finally {
                isLoading = false
            }
        }
    }

    // Load the initial date statistics
    LaunchedEffect(key1 = selectedDate) {
        if (hasPermissions) {
            loadDateStatistics()
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
            text = "Glucose Statistics",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Health Connect status
        if (!isHealthConnectAvailable) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Health Connect Required",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color(0xFFE65100)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Health Connect is required to view glucose statistics.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                healthConnectUploader.openHealthConnectApp(healthConnectUploader.context)
                            }
                        }
                    ) {
                        Text("Install Health Connect")
                    }
                }
            }
        } else if (!hasPermissions) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8E9))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Permissions Required",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color(0xFF33691E)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Permission to access glucose data is needed to show statistics.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = requestPermissions
                    ) {
                        Text("Request Permissions")
                    }
                }
            }
        } else {
            // Refresh button
            Button(
                onClick = {
                    coroutineScope.launch {
                        loadStatistics(
                            healthConnectUploader,
                            onLoadingChanged = { isLoading = it },
                            onDataLoaded = { statistics = it },
                            onError = { errorMessage = it }
                        )
                        loadDateStatistics()
                    }
                },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Refresh Statistics")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Period statistics
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.BarChart,
                            contentDescription = "Statistics",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Period Statistics",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else if (statistics.isEmpty()) {
                        Text(
                            text = "No glucose statistics available.",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    } else {
                        statistics.forEach { stat ->
                            StatisticsCard(stat)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Date-specific statistics
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Date",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Date Statistics",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Date picker
                    DateSelector(
                        selectedDate = selectedDate,
                        onDateSelected = {
                            selectedDate = it
                            // Date statistics will be loaded automatically by LaunchedEffect
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else if (dateStatistics == null || dateStatistics?.readingCount == 0L) {
                        Text(
                            text = "No glucose readings for ${formatDate(selectedDate)}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    } else {
                        dateStatistics?.let { StatisticsCard(it) }
                    }
                }
            }
        }

        // Error message
        errorMessage?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Error",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // Add space at the bottom for better scrolling
        Spacer(modifier = Modifier.height(32.dp))
    }
}

/**
 * Card to display statistics
 */

@Composable
fun StatisticsCard(statistics: GlucoseStatistics) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = statistics.period,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (statistics.readingCount == 0L) {
                Text(
                    text = "No readings for this period",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text(
                    text = "Readings: ${statistics.readingCount}",
                    style = MaterialTheme.typography.bodyMedium
                )

                statistics.averageGlucose?.let {
                    Text(
                        text = "Average: ${String.format("%.1f", it)} mg/dL",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    statistics.minimumGlucose?.let {
                        Text(
                            text = "Min: ${String.format("%.1f", it)} mg/dL",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    statistics.maximumGlucose?.let {
                        Text(
                            text = "Max: ${String.format("%.1f", it)} mg/dL",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

/**
 * Simple date selector
 */

@Composable
fun DateSelector(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = {
                onDateSelected(selectedDate.minusDays(1))
            }
        ) {
            Text("◀")
        }

        Text(
            text = formatDate(selectedDate),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        IconButton(
            onClick = {
                val tomorrow = LocalDate.now().plusDays(1)
                val nextDate = selectedDate.plusDays(1)
                // Don't allow selecting future dates beyond tomorrow
                if (!nextDate.isAfter(tomorrow)) {
                    onDateSelected(nextDate)
                }
            }
        ) {
            Text("▶")
        }
    }
}

/**
 * Format a date for display
 */
private fun formatDate(date: LocalDate): String {
    val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")
    return date.format(formatter)
}

/**
 * Load all statistics
 */
private suspend fun loadStatistics(
    healthConnectUploader: HealthConnectUploader,
    onLoadingChanged: (Boolean) -> Unit,
    onDataLoaded: (List<GlucoseStatistics>) -> Unit,
    onError: (String) -> Unit
) {
    onLoadingChanged(true)
    try {
        val stats = healthConnectUploader.getAllGlucoseStatistics()
        onDataLoaded(stats)
    } catch (e: Exception) {
        onError("Error loading statistics: ${e.message}")
    } finally {
        onLoadingChanged(false)
    }
}