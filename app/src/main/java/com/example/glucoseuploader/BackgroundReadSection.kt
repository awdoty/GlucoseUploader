package com.example.glucoseuploader

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun BackgroundReadSection(
    healthConnectUploader: HealthConnectUploader,
    requestPermissions: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    var isBackgroundReadAvailable by remember { mutableStateOf(false) }
    var hasBackgroundPermissions by remember { mutableStateOf(false) }
    var isHealthConnectAvailable by remember { mutableStateOf(false) }
    var isCheckingStatus by remember { mutableStateOf(true) }
    var statusMessage by remember { mutableStateOf("Checking background read availability...") }

    LaunchedEffect(Unit) {
        try {
            isHealthConnectAvailable = healthConnectUploader.isHealthConnectAvailable()

            if (isHealthConnectAvailable) {
                isBackgroundReadAvailable = healthConnectUploader.isBackgroundReadAvailable()
                hasBackgroundPermissions = healthConnectUploader.hasBackgroundReadPermission()

                statusMessage = when {
                    !isBackgroundReadAvailable -> "Background reading not available on this device"
                    !hasBackgroundPermissions -> "Background read permissions required"
                    else -> "Background reading is available and enabled"
                }
            } else {
                statusMessage = "Health Connect not available on this device"
            }
        } catch (e: Exception) {
            statusMessage = "Error: ${e.message}"
        } finally {
            isCheckingStatus = false
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
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = "Background Reading",
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "Background Reading",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Background reading allows the app to automatically sync your glucose readings from Health Connect.",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        isCheckingStatus -> MaterialTheme.colorScheme.surfaceVariant
                        isHealthConnectAvailable && isBackgroundReadAvailable && hasBackgroundPermissions ->
                            MaterialTheme.colorScheme.primaryContainer
                        !isHealthConnectAvailable -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.secondaryContainer
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Status Info",
                        tint = if (isCheckingStatus || !isHealthConnectAvailable)
                            MaterialTheme.colorScheme.error else
                            MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when {
                isCheckingStatus -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
                !isHealthConnectAvailable -> {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                healthConnectUploader.openHealthConnectApp()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Install Health Connect")
                    }
                }
                !isBackgroundReadAvailable -> {
                    Text(
                        text = "Background reading is not available on this device. This feature requires Android 13 or newer and a compatible version of Health Connect.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                !hasBackgroundPermissions -> {
                    Button(
                        onClick = requestPermissions,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Request Background Permissions")
                    }
                }
                else -> {
                    BackgroundReadSettings(healthConnectUploader)
                }
            }
        }
    }
}

private fun ColumnScope.BackgroundReadSettings(uploader: HealthConnectUploader) {
    TODO("Not yet implemented")
}

@Composable
private fun BackgroundReadSettings() {
    val syncFrequencyOptions = listOf("15 minutes", "30 minutes", "1 hour", "3 hours", "6 hours")
    var selectedSyncFrequency by remember { mutableStateOf(syncFrequencyOptions[2]) }
    var backgroundReadEnabled by remember { mutableStateOf(true) }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Enable background reading",
                style = MaterialTheme.typography.bodyLarge
            )

            Switch(
                checked = backgroundReadEnabled,
                onCheckedChange = { enabled ->
                    backgroundReadEnabled = enabled
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (backgroundReadEnabled) {
            Text(
                text = "Sync frequency",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(4.dp))

            OutlinedTextField(
                value = selectedSyncFrequency,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Frequency") }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    // TODO: Add real sync
                    //coroutineScope.launch {
                    //    healthConnectUploader.syncHealthConnectData()
                    //}
                    //println("Sync now clicked")
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = "Sync Now"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sync Now")
            }
        }
    }
}