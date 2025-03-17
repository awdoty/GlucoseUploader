package com.example.glucoseuploader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Screen for tracking changes to glucose readings in Health Connect
 */

@Composable
fun GlucoseChangesTrackingScreen(
    healthConnectUploader: HealthConnectUploader
) {
    val coroutineScope = rememberCoroutineScope()

    // State variables
    var isTrackingEnabled by remember { mutableStateOf(false) }
    var changesToken by remember { mutableStateOf<String?>(null) }
    var changes by remember { mutableStateOf<List<String>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // Save token when user enables tracking
    fun enableTracking() {
        coroutineScope.launch {
            try {
                isLoading = true
                errorMessage = null

                val token = healthConnectUploader.getGlucoseChangesToken()
                changesToken = token
                isTrackingEnabled = true

                changes = listOf("Started tracking changes at ${formatTimestamp(Instant.now())}")
            } catch (e: Exception) {
                errorMessage = "Error enabling tracking: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    // Get changes since last token
    fun getChanges() {
        val token = changesToken ?: return

        coroutineScope.launch {
            try {
                isLoading = true
                errorMessage = null

                val changesList = mutableListOf<String>()
                var newToken = token

                healthConnectUploader.getGlucoseChangesSinceToken(token).collect { message ->
                    when (message) {
                        is ChangesMessage.ChangeList -> {
                            val changeDetails = healthConnectUploader.processGlucoseChanges(message.changes)
                            changesList.addAll(changeDetails)
                        }
                        is ChangesMessage.NoMoreChanges -> {
                            newToken = message.nextChangesToken
                        }
                    }
                }

                changesToken = newToken

                if (changesList.isEmpty()) {
                    changes = listOf("No changes detected since last check")
                } else {
                    changes = changesList
                }
            } catch (e: Exception) {
                if (e.message?.contains("expired") == true) {
                    errorMessage = "Changes token expired. Please enable tracking again."
                    isTrackingEnabled = false
                    changesToken = null
                } else {
                    errorMessage = "Error getting changes: ${e.message}"
                }
            } finally {
                isLoading = false
            }
        }
    }

                changesToken = newToken

                if (changesList.isEmpty()) {
                    changes = listOf("No changes detected since last check")
                } else {
                    changes = changesList
                }

             catch (e: Exception) {
                if (e.message?.contains("expired") == true) {
                    errorMessage = "Changes token expired. Please enable tracking again."
                    isTrackingEnabled = false
                    changesToken = null
                } else {
                    errorMessage = "Error getting changes: ${e.message}"
                }
            } finally {
                isLoading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Track Glucose Reading Changes",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Tracking controls
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
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Change Tracking",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Switch(
                        checked = isTrackingEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                enableTracking()
                            } else {
                                isTrackingEnabled = false
                                changesToken = null
                                changes = emptyList()
                            }
                        },
                        enabled = !isLoading
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (isTrackingEnabled) {
                        "Tracking is enabled. Make changes to your glucose readings, then click 'Get Changes' to see them."
                    } else {
                        "Enable tracking to monitor changes to your glucose readings."
                    },
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { getChanges() },
                    enabled = isTrackingEnabled && !isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Get Changes"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Get Changes")
                }
            }
        }

        // Loading indicator
        if (isLoading) {
            Spacer(modifier = Modifier.height(16.dp))
            CircularProgressIndicator()
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

        // Changes list
        if (changes.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Changes",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn {
                        items(changes) { change ->
                            Text(
                                text = change,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            Divider()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Format a timestamp for display
 */
private fun formatTimestamp(instant: Instant): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    return formatter.format(instant.atZone(ZoneId.systemDefault()))
}