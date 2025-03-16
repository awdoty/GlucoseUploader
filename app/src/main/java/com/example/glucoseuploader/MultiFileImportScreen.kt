package com.example.glucoseuploader

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Define file status enum
 */
enum class Status {
    PENDING, LOADING, SUCCESS, ERROR
}

/**
 * Data class to track file processing state
 */
data class FileImportState(
    val uri: Uri,
    val filename: String,
    val status: Status = Status.PENDING,
    val message: String = "",
    val glucoseData: List<GlucoseData> = emptyList()
)

/**
 * Screen for importing glucose data from multiple CSV files
 */

@Composable
fun MultiFileImportScreen(
    uris: List<Uri>,
    healthConnectUploader: HealthConnectUploader,
    onImportComplete: (successCount: Int, totalCount: Int, message: String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var fileImportStates by remember { mutableStateOf<List<FileImportState>>(emptyList()) }
    var isUploading by remember { mutableStateOf(false) }

    // Initialize file states with filenames
    LaunchedEffect(uris) {
        val states = uris.map { uri ->
            val filename = getFilenameFromUri(context, uri)
            FileImportState(uri = uri, filename = filename)
        }
        fileImportStates = states
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Import Multiple Glucose Files",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Found ${fileImportStates.size} files to import",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        when {
            isLoading -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text("Analyzing files...")
            }
            errorMessage != null -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEEEE)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = "Error",
                                tint = Color.Red
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Error",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.Red
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = errorMessage ?: "Unknown error",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { onImportComplete(0, uris.size, errorMessage ?: "Unknown error") }) {
                    Text("Close")
                }
            }
            else -> {
                // Show list of files
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Files to Import:",
                            style = MaterialTheme.typography.titleLarge
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                        ) {
                            items(fileImportStates) { fileState ->
                                FileImportItem(
                                    fileState = fileState,
                                    onPreview = {
                                        coroutineScope.launch {
                                            previewFile(
                                                context,
                                                fileState.uri,
                                                onStateUpdate = { newState ->
                                                    fileImportStates = fileImportStates.map {
                                                        if (it.uri == fileState.uri) newState else it
                                                    }
                                                }
                                            )
                                        }
                                    }
                                )
                                Divider()
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Button(
                                onClick = { onImportComplete(0, uris.size, "Import cancelled") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                            ) {
                                Text("Cancel")
                            }

                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        isUploading = true
                                        var successCount = 0

                                        // Process each file
                                        try {
                                            for (i in fileImportStates.indices) {
                                                val fileState = fileImportStates[i]

                                                // Update status to loading
                                                fileImportStates = fileImportStates.toMutableList().apply {
                                                    this[i] = fileState.copy(status = Status.LOADING)
                                                }

                                                try {
                                                    // If not already previewed, load the data
                                                    val glucoseData = if (fileState.glucoseData.isEmpty()) {
                                                        readCsvFile(context, fileState.uri)
                                                    } else {
                                                        fileState.glucoseData
                                                    }

                                                    if (glucoseData.isNotEmpty()) {
                                                        // Upload data
                                                        uploadGlucoseReadings(context, healthConnectUploader, glucoseData)
                                                        successCount++

                                                        // Update status to success
                                                        fileImportStates = fileImportStates.toMutableList().apply {
                                                            this[i] = fileState.copy(
                                                                status = Status.SUCCESS,
                                                                message = "Uploaded ${glucoseData.size} readings",
                                                                glucoseData = glucoseData
                                                            )
                                                        }
                                                    } else {
                                                        // No data found
                                                        fileImportStates = fileImportStates.toMutableList().apply {
                                                            this[i] = fileState.copy(
                                                                status = Status.ERROR,
                                                                message = "No glucose data found in file"
                                                            )
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    // Error with this file
                                                    fileImportStates = fileImportStates.toMutableList().apply {
                                                        this[i] = fileState.copy(
                                                            status = Status.ERROR,
                                                            message = "Error: ${e.message}"
                                                        )
                                                    }
                                                }
                                            }

                                            // Wait a bit for user to see the results
                                            delay(1500)

                                            onImportComplete(
                                                successCount,
                                                uris.size,
                                                "Successfully uploaded data from $successCount of ${uris.size} files"
                                            )
                                        } catch (e: Exception) {
                                            errorMessage = "Error during batch upload: ${e.message}"
                                            onImportComplete(0, uris.size, "Error: ${e.message}")
                                        } finally {
                                            isUploading = false
                                        }
                                    }
                                },
                                enabled = !isUploading && fileImportStates.isNotEmpty()
                            ) {
                                if (isUploading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text("Import All Files")
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun FileImportItem(
    fileState: FileImportState,
    onPreview: () -> Unit
) {
    val statusColor = when(fileState.status) {
        Status.PENDING -> Color.Gray
        Status.LOADING -> MaterialTheme.colorScheme.primary
        Status.SUCCESS -> Color.Green
        Status.ERROR -> Color.Red
    }

    val statusIcon = when(fileState.status) {
        Status.PENDING -> Icons.Default.Info
        Status.LOADING -> null // Show loader instead
        Status.SUCCESS -> Icons.Default.CheckCircle
        Status.ERROR -> Icons.Default.Error
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status icon or progress indicator
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center
        ) {
            if (fileState.status == Status.LOADING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                statusIcon?.let {
                    Icon(
                        imageVector = it,
                        contentDescription = "Status",
                        tint = statusColor
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // File info
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = fileState.filename,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (fileState.message.isNotEmpty()) {
                Text(
                    text = fileState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor
                )
            }

            if (fileState.glucoseData.isNotEmpty()) {
                Text(
                    text = "${fileState.glucoseData.size} readings found",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Preview button - only for pending files
        if (fileState.status == Status.PENDING) {
            Button(
                onClick = onPreview,
                modifier = Modifier.padding(start = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("Preview")
            }
        }
    }
}

/**
 * Get a filename from a URI
 */
private fun getFilenameFromUri(context: Context, uri: Uri): String {
    return try {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val displayNameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (displayNameIndex != -1) {
                    it.getString(displayNameIndex)
                } else {
                    uri.lastPathSegment ?: "Unknown file"
                }
            } else {
                uri.lastPathSegment ?: "Unknown file"
            }
        } ?: uri.lastPathSegment ?: "Unknown file"
    } catch (e: Exception) {
        uri.lastPathSegment ?: "Unknown file"
    }
}

/**
 * Preview a file to analyze its contents
 */
private suspend fun previewFile(
    context: Context,
    uri: Uri,
    onStateUpdate: (FileImportState) -> Unit
) {
    try {
        // Update to loading state
        val filename = getFilenameFromUri(context, uri)
        onStateUpdate(
            FileImportState(
                uri = uri,
                filename = filename,
                status = Status.LOADING
            )
        )

        // Read the file
        val glucoseData = readCsvFile(context, uri)

        if (glucoseData.isNotEmpty()) {
            onStateUpdate(
                FileImportState(
                    uri = uri,
                    filename = filename,
                    status = Status.PENDING,
                    message = "Ready to import ${glucoseData.size} readings",
                    glucoseData = glucoseData
                )
            )
        } else {
            onStateUpdate(
                FileImportState(
                    uri = uri,
                    filename = filename,
                    status = Status.ERROR,
                    message = "No glucose readings found in file"
                )
            )
        }
    } catch (e: Exception) {
        val filename = getFilenameFromUri(context, uri)
        onStateUpdate(
            FileImportState(
                uri = uri,
                filename = filename,
                status = Status.ERROR,
                message = "Error reading file: ${e.message}"
            )
        )
    }
}

/**
 * Upload glucose readings to Health Connect
 */
private suspend fun uploadGlucoseReadings(
    context: Context,
    healthConnectUploader: HealthConnectUploader,
    glucoseData: List<GlucoseData>
) {
    // Convert to a list of time-value pairs
    val readings = glucoseData.mapNotNull { data ->
        try {
            // Parse date and time
            val dateTimeStr = "${data.date} ${data.time}"
            val dateTime = tryParseDateTime(dateTimeStr)  // Call the function to get dateTime

// Convert to ZonedDateTime
            val zonedDateTime = ZonedDateTime.of(
                dateTime,
                ZoneId.systemDefault()
            )

            Pair(zonedDateTime, data.glucose.toDouble())
        } catch (e: Exception) {
            // Skip unparseable entries
            null
        }
    }

    // Get min and max times for the series
    if (readings.isNotEmpty()) {
        val startTime = readings.minByOrNull { it.first.toInstant().toEpochMilli() }?.first ?: return
        val endTime = readings.maxByOrNull { it.first.toInstant().toEpochMilli() }?.first ?: return

        // Get just the glucose values
        val values = readings.map { it.second }

        // Upload as a series
        healthConnectUploader.uploadGlucoseSeries(values, startTime, endTime)
    }
}