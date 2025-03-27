package com.example.glucoseuploader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    healthConnectUploader: HealthConnectUploader,
    appState: AppState,
    requestPermissions: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // File picker for CSV files
    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val intent = Intent(context, CsvImportActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = it
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Glucose Uploader") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Welcome Card
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
                        text = "Welcome to Glucose Uploader",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "This app helps you upload glucose readings from CSV files to Health Connect.",
                        style = MaterialTheme.typography.bodyLarge
                    )

                    // Show last reading if available
                    appState.latestReading?.let { (value, time) ->
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Last Reading",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "${value.toInt()} mg/dL at ${formatTime(time)}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Health Connect Status Card
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
                        text = "Health Connect Status",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (appState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    } else {
                        if (!appState.isHealthConnectAvailable) {
                            Text("Health Connect is not available on this device.")

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        healthConnectUploader.openHealthConnectApp(context)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Install Health Connect")
                            }
                        } else if (!appState.hasPermissions) {
                            Text("Permission to access glucose data is needed.")

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = requestPermissions,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Grant Permissions")
                            }
                        } else {
                            Text("Health Connect is available and permissions are granted.")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Import Options Card
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
                        text = "Import Options",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Option 1: Select CSV File
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
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = "Select File"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Select CSV File")
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Option 2: Share From App
                    Text(
                        text = "You can also share CSV files directly from your glucose app to this app.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Help/Instructions Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Instructions",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "1. Export CSV from your glucose meter app (like AgaMatrix)\n" +
                                "2. Share the file directly to this app or select it using the button above\n" +
                                "3. Review the readings and upload them to Health Connect",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

// Helper function to format time for display
private fun formatTime(time: java.time.Instant): String {
    val formatter = java.time.format.DateTimeFormatter
        .ofPattern("MMM dd, yyyy hh:mm a")
        .withZone(java.time.ZoneId.systemDefault())
    return formatter.format(time)
}