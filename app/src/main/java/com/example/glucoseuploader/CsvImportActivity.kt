package com.example.glucoseuploader

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.lifecycle.lifecycleScope
import com.example.glucoseuploader.ui.theme.GlucoseUploaderTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.*

class CsvImportActivity : ComponentActivity() {

    private val TAG = "CsvImportActivity"
    private lateinit var healthConnectUploader: HealthConnectUploader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        healthConnectUploader = HealthConnectUploader(this)

        // Get the URI of the CSV file from the intent
        val action = intent?.action

        val uri = when {
            // Handle VIEW action (opening file directly)
            action == Intent.ACTION_VIEW -> intent.data

            // Handle SEND action (sharing from another app)
            action == Intent.ACTION_SEND -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
            }

            // Handle other cases
            else -> null
        }

        if (uri == null) {
            Toast.makeText(this, "No CSV file provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Get file metadata
        val fileName = getFileName(uri)
        val fileSize = getFileSize(uri)
        val fileType = contentResolver.getType(uri)

        Log.d(TAG, "Received action: $action with URI: $uri")
        Log.d(TAG, "File name: $fileName")
        Log.d(TAG, "File size: $fileSize bytes")
        Log.d(TAG, "File type: $fileType")

        // Process the CSV file
        setContent {
            GlucoseUploaderTheme {
                CsvImportScreen(uri, fileName)
            }
        }
    }

    @Composable
    fun CsvImportScreen(uri: Uri, fileName: String?) {
        var isLoading by remember { mutableStateOf(true) }
        var csvContent by remember { mutableStateOf<List<String>>(emptyList()) }
        var detectedType by remember { mutableStateOf("Unknown") }
        var readings by remember { mutableStateOf<List<GlucoseReading>>(emptyList()) }
        var uploadStatus by remember { mutableStateOf("") }
        var uploadProgress by remember { mutableStateOf(0f) }
        var isUploading by remember { mutableStateOf(false) }
        var selectedMealType by remember { mutableIntStateOf(BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN) }

        // Load CSV data when the screen is composed
        LaunchedEffect(uri) {
            try {
                val reader = CsvReader(this@CsvImportActivity)
                val csvLines = reader.readCsvFile(uri)
                csvContent = csvLines

                // Parse the CSV content
                val parseResult = reader.parseGenericCsvFormat(csvLines)
                readings = parseResult
                detectedType = reader.detectCsvType(csvLines)

                isLoading = false
            } catch (e: Exception) {
                Log.e(TAG, "Error reading CSV", e)
                Toast.makeText(this@CsvImportActivity, "Error reading CSV: ${e.message}", Toast.LENGTH_SHORT).show()
                withContext(Dispatchers.Main) {
                    finish()
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colors.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Import CSV",
                    style = MaterialTheme.typography.h5
                )

                Spacer(modifier = Modifier.height(16.dp))

                // File info
                fileName?.let {
                    Text(text = "File: $it", style = MaterialTheme.typography.body1)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Text(text = "File Type: $detectedType", style = MaterialTheme.typography.body1)

                Spacer(modifier = Modifier.height(16.dp))

                if (isLoading) {
                    // Show loading spinner
                    CircularProgressIndicator()
                    Text(text = "Reading CSV...", style = MaterialTheme.typography.body1)
                } else {
                    // Show parsing results
                    if (readings.isEmpty()) {
                        Text(
                            text = "No glucose readings found in the file",
                            style = MaterialTheme.typography.h6,
                            color = MaterialTheme.colors.error
                        )
                    } else {
                        Text(
                            text = "Found ${readings.size} glucose readings",
                            style = MaterialTheme.typography.h6,
                            color = MaterialTheme.colors.primary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Show a sample of readings
                        readings.take(3).forEach { reading ->
                            Text(
                                text = "${reading.timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}: ${reading.value} mg/dL",
                                style = MaterialTheme.typography.body1
                            )
                        }

                        if (readings.size > 3) {
                            Text(
                                text = "... and ${readings.size - 3} more",
                                style = MaterialTheme.typography.caption
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Meal Type Selection
                        Text(
                            text = "Select Meal Type for Import",
                            style = MaterialTheme.typography.subtitle1
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Meal Type Dropdown
                        var expanded by remember { mutableStateOf(false) }
                        Box {
                            Button(onClick = { expanded = true }) {
                                Text(when (selectedMealType) {
                                    BloodGlucoseRecord.RELATION_TO_MEAL_BEFORE_MEAL -> "Before Meal"
                                    BloodGlucoseRecord.RELATION_TO_MEAL_AFTER_MEAL -> "After Meal"
                                    BloodGlucoseRecord.RELATION_TO_MEAL_FASTING -> "Fasting"
                                    BloodGlucoseRecord.RELATION_TO_MEAL_GENERAL -> "General"
                                    else -> "Unknown Meal Type"
                                })
                            }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                DropdownMenuItem(onClick = {
                                    selectedMealType = BloodGlucoseRecord.RELATION_TO_MEAL_BEFORE_MEAL
                                    expanded = false
                                }) {
                                    Text("Before Meal")
                                }
                                DropdownMenuItem(onClick = {
                                    selectedMealType = BloodGlucoseRecord.RELATION_TO_MEAL_AFTER_MEAL
                                    expanded = false
                                }) {
                                    Text("After Meal")
                                }
                                DropdownMenuItem(onClick = {
                                    selectedMealType = BloodGlucoseRecord.RELATION_TO_MEAL_FASTING
                                    expanded = false
                                }) {
                                    Text("Fasting")
                                }
                                DropdownMenuItem(onClick = {
                                    selectedMealType = BloodGlucoseRecord.RELATION_TO_MEAL_GENERAL
                                    expanded = false
                                }) {
                                    Text("General")
                                }
                                DropdownMenuItem(onClick = {
                                    selectedMealType = BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN
                                    expanded = false
                                }) {
                                    Text("Unknown")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Upload button
                        Button(
                            onClick = {
                                isUploading = true
                                uploadProgress = 0f

                                lifecycleScope.launch {
                                    try {
                                        uploadStatus = "Uploading readings..."

                                        // Convert readings to doubles and get start/end times
                                        val values = readings.map { it.value }
                                        val startTime = readings.minByOrNull { it.timestamp }?.timestamp
                                            ?: ZonedDateTime.now()
                                        val endTime = readings.maxByOrNull { it.timestamp }?.timestamp
                                            ?: ZonedDateTime.now()

                                        // Create list of meal types for all readings
                                        val mealTypes = List(readings.size) { selectedMealType }

                                        // Upload the readings with progress tracking
                                        healthConnectUploader.uploadGlucoseSeries(
                                            values,
                                            startTime,
                                            endTime,
                                            mealTypes
                                        ) { current, total ->
                                            uploadProgress = current.toFloat() / total.toFloat()
                                            uploadStatus = "Uploaded $current of $total readings"
                                        }

                                        uploadStatus = "Successfully uploaded ${readings.size} readings"

                                        // Auto-close after short delay
                                        withContext(Dispatchers.Main) {
                                            kotlinx.coroutines.delay(2000)
                                            finish()
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error uploading readings", e)
                                        uploadStatus = "Error: ${e.message}"
                                    } finally {
                                        isUploading = false
                                    }
                                }
                            },
                            enabled = !isUploading && readings.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Upload ${readings.size} Readings")
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Cancel button
                        OutlinedButton(
                            onClick = { finish() },
                            enabled = !isUploading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cancel")
                        }

                        // Show upload progress
                        if (isUploading) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(text = uploadStatus)
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = uploadProgress,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Get the filename from a URI
     */
    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            try {
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            result = cursor.getString(nameIndex)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting filename", e)
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != -1) {
                result = result?.substring(cut!! + 1)
            }
        }
        return result
    }

    /**
     * Get the file size from a URI
     */
    private fun getFileSize(uri: Uri): Long {
        var size: Long = 0
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            try {
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIndex != -1) {
                            size = cursor.getLong(sizeIndex)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting file size", e)
            }
        }
        return size
    }
}