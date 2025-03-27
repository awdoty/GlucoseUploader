package com.example.glucoseuploader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Activity for importing CSV glucose data.
 */
class CsvImportActivity : ComponentActivity() {

    private val healthConnectUploader by lazy {
        HealthConnectUploader(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            // Create state variables for tracking upload progress
            val uploadProgress = remember { mutableStateOf(0f) }
            val uploadStatus = remember { mutableStateOf("") }
            val isUploading = remember { mutableStateOf(false) }
            val totalRecords = remember { mutableStateOf(0) }
            val currentRecord = remember { mutableStateOf(0) }

            // Explicitly specify types for the parameters to fix inference issues
            CsvImportScreen(
                uploadProgress = uploadProgress.value,
                uploadStatus = uploadStatus.value,
                isUploading = isUploading.value,
                totalRecords = totalRecords.value,
                currentRecord = currentRecord.value,
                onImportCsv = { uri ->
                    // Start the CSV import process
                    isUploading.value = true
                    uploadStatus.value = "Preparing to import..."

                    lifecycleScope.launch {
                        try {
                            val csvParser = CsvParser(contentResolver)
                            val records = csvParser.parseGlucoseData(uri)

                            totalRecords.value = records.size

                            records.forEachIndexed { index, record ->
                                currentRecord.value = index + 1
                                uploadProgress.value = (index + 1).toFloat() / records.size
                                uploadStatus.value = "Uploading record ${index + 1} of ${records.size}"

                                healthConnectUploader.uploadGlucoseRecord(record)
                            }

                            uploadStatus.value = "Import complete: ${records.size} records imported"
                        } catch (e: Exception) {
                            uploadStatus.value = "Error: ${e.message}"
                        } finally {
                            isUploading.value = false
                        }
                    }
                }
            )
        }
    }
}