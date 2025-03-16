package com.example.glucoseuploader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.records.BloodGlucoseRecord
import kotlinx.coroutines.launch

/**
 * Enhanced screen that combines recent glucose history with historical data access
 */

@Composable
fun EnhancedGlucoseHistoryScreen(
    healthConnectUploader: HealthConnectUploader,
    requestPermissions: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Glucose Data",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Recent data (last 30 days)
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Recent Data (Last 30 Days)",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Include the regular glucose history screen as a composable
                GlucoseHistoryScreen(
                    healthConnectUploader = healthConnectUploader,
                    requestPermissions = requestPermissions
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Background reading capability
        BackgroundReadSection(
            healthConnectUploader = healthConnectUploader,
            requestPermissions = requestPermissions
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Historical data section
        HistoricalDataSection(
            healthConnectUploader = healthConnectUploader
        )

        // Add some space at the bottom for better scrolling
        Spacer(modifier = Modifier.height(32.dp))
    }
}