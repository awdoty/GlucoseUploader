package com.example.glucoseuploader

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.glucoseuploader.ui.theme.GlucoseUploaderTheme
import kotlinx.coroutines.launch

/**
 * Activity for handling CSV file importing and uploading to Health Connect
 */
class ImportActivity : ComponentActivity() {
    private val tag = "ImportActivity"
    private lateinit var healthConnectUploader: HealthConnectUploader
    private val glucoseCsvParser = GlucoseCsvParser()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Health Connect uploader
        healthConnectUploader = HealthConnectUploader(this)

        // Get URI from intent
        val fileUri = intent.data

        setContent {
            GlucoseUploaderTheme {
                ImportScreen(
                    fileUri = fileUri,
                    healthConnectUploader = healthConnectUploader,
                    glucoseCsvParser = glucoseCsvParser,
                    onClose = { finish() }
                )
            }
        }
    }
}

/**
 * Composable for the import screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    fileUri: Uri?,
    healthConnectUploader: HealthConnectUploader,
    glucoseCsvParser: GlucoseCsvParser,
    onClose: () -> Unit
) {
    val scrollState = rememberScrollState()

    // State for tracking import progress
    var isLoading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Ready to import") }
    var progress by remember { mutableStateOf(0f) }
    var totalReadings by remember { mutableStateOf(0) }
    var importedReadings by remember { mutableStateOf(0) }
    var filename by remember { mutableStateOf("") }
    var isComplete by remember { mutableStateOf(false) }
    var importSuccess by remember { mutableStateOf(false) }

    // Get filename from URI
    LaunchedEffect(fileUri) {
        fileUri?.let { uri ->
            // Try to get filename from content resolver
            val contentResolver = healthConnectUploader.context.contentResolver
            val cursor = contentResolver.query(uri, null, null, null, null)

            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val displayNameIndex = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (displayNameIndex != -1) {
                        filename = c.getString(displayNameIndex)
                    }
                }
            }

            // If we couldn't get the filename, use the last path segment
            if (filename.isEmpty()) {
                filename = uri.lastPathSegment ?: "Unknown file"
            }
        }
    }

    // Start import if file URI is provided
    LaunchedEffect(fileUri) {
        if (fileUri != null) {
            try {
                isLoading = true
                status = "Reading file..."

                // Parse the CSV file
                val readings = glucoseCsvParser.parseGlucoseReadings(
                    healthConnectUploader.context,
                    fileUri
                )

                totalReadings = readings.size

                if (readings.isEmpty()) {
                    status = "No glucose readings found in the file"
                    isLoading = false
                    return@LaunchedEffect
                }

                status = "Found ${readings.size} glucose readings. Uploading to Health Connect..."

                // Upload readings to Health Connect
                var successCount = 0
                readings.forEachIndexed { index, reading ->
                    try {
                        // Update progress
                        progress = (index + 1).toFloat() / totalReadings
                        importedReadings = index + 1

                        // Upload the reading
                        healthConnectUploader.uploadBloodGlucose(
                            value = reading.value,
                            time = reading.dateTime.toInstant(),
                            relationToMeal = reading.mealType
                        )

                        successCount++
                    } catch (e: Exception) {
                        Log.e(tag, "Error uploading reading: $reading", e)
                    }
                }

                // Update status with results
                if (successCount > 0) {
                    status = "Successfully uploaded $successCount out of $totalReadings readings"
                    importSuccess = true
                } else {
                    status = "Failed to upload any readings"
                }

            } catch (e: Exception) {
                Log.e(tag, "Error importing file", e)
                status = "Error: ${e.message}"
            } finally {
                isComplete = true
                isLoading = false
            }
        } else {
            status = "No file selected"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import Glucose Data") }
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
            // File info
            if (filename.isNotEmpty()) {
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
                            text = "File",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = filename,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Progress card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Import Status",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isLoading) {
                        CircularProgressIndicator()

                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodyLarge
                    )

                    if (totalReadings > 0) {
                        Spacer(modifier = Modifier.height(16.dp))

                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "$importedReadings of $totalReadings readings processed",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    if (isComplete) {
                        Spacer(modifier = Modifier.height(16.dp))

                        Icon(
                            imageVector = if (importSuccess)
                                Icons.Default.CheckCircle
                            else
                                Icons.Default.Error,
                            contentDescription = "Status",
                            tint = if (importSuccess)
                                Color.Green
                            else
                                Color.Red,
                            modifier = Modifier.size(48.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onClose,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isComplete) "Close" else "Cancel")
                    }
                }
            }

            // Additional info
            if (!isComplete && isLoading) {
                Spacer(modifier = Modifier.height(16.dp))

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
                            text = "Please Wait",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "The app is processing your glucose data and uploading it to Health Connect. This may take a moment depending on the file size.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}