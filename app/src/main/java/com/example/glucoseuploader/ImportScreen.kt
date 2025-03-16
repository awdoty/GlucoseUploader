package com.example.glucoseuploader

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp


@Composable
fun ImportScreen(
    healthConnectUploader: HealthConnectUploader,
    onPickFile: () -> Unit
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Import Glucose Data",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // CSV File Upload Section
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
                    text = "CSV File Import",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Import your glucose readings from a CSV file exported from your glucose meter or app."
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = onPickFile,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = "Browse Files"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Select CSV File")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Instructions Card
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
                    text = "How to Import",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Method 1: Share directly from your glucose app"
                )
                Text(
                    text = "Many glucose apps like AgaMatrix allow you to share your data as a CSV file. When sharing, select this app from the share menu."
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Method 2: Download first, then import"
                )
                Text(
                    text = "Export your data from your glucose app, save the file, then use the 'Select CSV File' button above to import it."
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Supported formats:",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "• AgaMatrix CSV exports\n• Standard glucose meter CSV files\n• Generic CSV files with date, time, and glucose values"
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tips Card
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
                        imageVector = Icons.Default.Info,
                        contentDescription = "Info",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Tips",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "• Make sure your CSV file contains glucose readings in mg/dL units"
                )
                Text(
                    text = "• Files should include date and time information for each reading"
                )
                Text(
                    text = "• Enable permissions when prompted to allow Health Connect access"
                )
                Text(
                    text = "• Imported readings will be visible in the History screen"
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}