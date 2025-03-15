package com.example.glucoseuploader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * UI section for background read functionality
 */
@Composable
fun BackgroundReadSection(
    healthConnectUploader: HealthConnectUploader,
    requestPermissions: () -> Unit
) {
    val context = LocalContext.current
    var isBackgroundReadAvailable by remember { mutableStateOf(false) }
    var hasBackgroundReadPermission by remember { mutableStateOf(false) }

    // Check if background read is available
    LaunchedEffect(key1 = Unit) {
        isBackgroundReadAvailable = healthConnectUploader.isBackgroundReadAvailable()

        // Check if we have background read permission
        if (isBackgroundReadAvailable) {
            val client = healthConnectUploader.healthConnectClient ?: return@LaunchedEffect
            val grantedPermissions = client.permissionController.getGrantedPermissions()
            // Check for the background read permission in the granted permissions
            hasBackgroundReadPermission = grantedPermissions.any {
                it.toString().contains("READ_HEALTH_DATA_IN_BACKGROUND")
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Background Reading",
                style = MaterialTheme.typography.h6,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (!isBackgroundReadAvailable) {
                Text(
                    text = "Background reading is not available on this device. Please update Health Connect.",
                    style = MaterialTheme.typography.body1
                )
            } else {
                if (!hasBackgroundReadPermission) {
                    Text(
                        text = "Background reading permission is required for automatic glucose monitoring.",
                        style = MaterialTheme.typography.body1
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            healthConnectUploader.requestBackgroundReadPermission(
                                context as androidx.activity.ComponentActivity
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Request Background Read Permission")
                    }
                } else {
                    Text(
                        text = "Background reading permission granted. You can schedule automatic glucose checks.",
                        style = MaterialTheme.typography.body1
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            // Schedule background read in 1 minute (for testing)
                            GlucoseReadWorker.schedule(context, delayMinutes = 1)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Schedule"
                        )
                        Text("Schedule Background Check (1 min)")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            // Schedule background read with default delay (10 minutes)
                            GlucoseReadWorker.schedule(context)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Schedule"
                        )
                        Text("Schedule Background Check (10 min)")
                    }
                }
            }
        }
    }
}