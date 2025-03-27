package com.example.glucoseuploader

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Composable screen for CSV import functionality.
 */
@Composable
fun CsvImportScreen(
    uploadProgress: Float,
    uploadStatus: String,
    isUploading: Boolean,
    totalRecords: Int,
    currentRecord: Int,
    onImportCsv: (Uri) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Import Blood Glucose Data",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // File picker launcher
        val filePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri ->
            uri?.let { onImportCsv(it) }
        }

        Button(
            onClick = { filePickerLauncher.launch("text/csv") },
            enabled = !isUploading,
            modifier = Modifier.padding(bottom = 24.dp)
        ) {
            Text("Select CSV File")
        }

        // Progress indicator and status
        if (isUploading) {
            Text(
                text = uploadStatus,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LinearProgressIndicator(
                progress = uploadProgress,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

            Text(
                text = "Uploading $currentRecord of $totalRecords records",
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // Show help text
        Card(
            modifier = Modifier.padding(top = 24.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "CSV Format Requirements:",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text("• Each row should represent one glucose reading")
                Text("• Required columns: Date, Time, Glucose (mg/dL)")
                Text("• Optional columns: Meal type, Notes")
                Text("• Date format: MM/DD/YYYY or YYYY-MM-DD")
                Text("• Time format: HH:MM or HH:MM:SS (24-hour)")
            }
        }
    }
}