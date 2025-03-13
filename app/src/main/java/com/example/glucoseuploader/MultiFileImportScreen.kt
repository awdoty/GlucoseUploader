package com.example.glucoseuploader

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
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
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

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

    // Track file processing state
    data class FileImportState(
        val uri: Uri,
        val filename: String,
        var status: Status = Status.PENDING,
        var message: String = "",
        var glucoseData: List<GlucoseData> = emptyList()
    )

    enum class Status {
        PENDING, LOADING, SUCCESS, ERROR
    }

    var fileImportStates by remember { mutableStateOf<List<FileImportState>>(emptyList()) }
    var isUploading by remember { mutableStateOf(false) }

    // Initialize file states with filenames
    LaunchedEffect(uris) {
        val states = uris.map { uri ->
            val filename = getFilenameFromUri(context, uri)
            FileImportState(uri, filename)
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
            style = MaterialTheme.typography.h5,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Found ${fileImportStates.size} files to import",
            style = MaterialTheme.typography.subtitle1
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
                    backgroundColor = Color(0xFFFFEEEE),
                    elevation = 4.dp
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
                                style = MaterialTheme.typography.h6,
                                color = Color.Red
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = errorMessage ?: "Unknown error",
                            style = MaterialTheme.typography.body1
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
                    elevation = 4.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Files to Import:",
                            style = MaterialTheme.typography.h6
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
                                colors = ButtonDefaults.buttonColors(backgroundColor = Color.Gray)
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
                                            kotlinx.coroutines.delay(1500)

                                            onImportComplete(
                                                successCount,
                                                uris.size,
                                                "Successfully uploaded data from $successCount of ${uris.size} files"
                                            )
                                        } catch (e: Exception) {
                                            errorMessage = "Error during batch upload: ${e.message}"
                                            onImportComplete(
                                                successCount,
                                                uris.size,
                                                "Error: ${e.message}"
                                            )
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
                                        color = MaterialTheme.colors.onPrimary,
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
    fileState: MultiFileImportScreen.FileImportState,
    onPreview: () -> Unit
) {
    val statusColor = when(fileState.status) {
        MultiFileImportScreen.Status.PENDING -> Color.Gray
        MultiFileImportScreen.Status.LOADING -> MaterialTheme.colors.primary
        MultiFileImportScreen.Status.SUCCESS -> Color.Green
        MultiFileImportScreen.Status.ERROR -> Color.Red
    }

    val statusIcon = when(fileState.status) {
        MultiFileImportScreen.Status.PENDING -> Icons.Default.Info
        MultiFileImportScreen.Status.LOADING -> null // Show loader instead
        MultiFileImportScreen.Status.SUCCESS -> Icons.Default.CheckCircle
        MultiFileImportScreen.Status.ERROR -> Icons.Default.Error
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
            if (fileState.status == MultiFileImportScreen.Status.LOADING) {
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
                style = MaterialTheme.typography.subtitle1,
                fontWeight = FontWeight.Bold
            )

            if (fileState.message.isNotEmpty()) {
                Text(
                    text = fileState.message,
                    style = MaterialTheme.typography.caption,
                    color = statusColor
                )
            }

            if (fileState.glucoseData.isNotEmpty()) {
                Text(
                    text = "${fileState.glucoseData.size} readings found",
                    style = MaterialTheme.typography.caption
                )
            }
        }

        // Preview button - only for pending files
        if (fileState.status == MultiFileImportScreen.Status.PENDING) {
            Button(
                onClick = onPreview,
                modifier = Modifier.padding(start = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.primaryVariant
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
    onStateUpdate: (MultiFileImportScreen.FileImportState) -> Unit
) {
    try {
        // Update to loading state
        val filename = getFilenameFromUri(context, uri)
        onStateUpdate(
            MultiFileImportScreen.FileImportState(
                uri = uri,
                filename = filename,
                status = MultiFileImportScreen.Status.LOADING
            )
        )

        // Read the file
        val glucoseData = readCsvFile(context, uri)

        if (glucoseData.isNotEmpty()) {
            onStateUpdate(
                MultiFileImportScreen.FileImportState(
                    uri = uri,
                    filename = filename,
                    status = MultiFileImportScreen.Status.PENDING,
                    message = "Ready to import ${glucoseData.size} readings",
                    glucoseData = glucoseData
                )
            )
        } else {
            onStateUpdate(
                MultiFileImportScreen.FileImportState(
                    uri = uri,
                    filename = filename,
                    status = MultiFileImportScreen.Status.ERROR,
                    message = "No glucose readings found in file"
                )
            )
        }
    } catch (e: Exception) {
        val filename = getFilenameFromUri(context, uri)
        onStateUpdate(
            MultiFileImportScreen.FileImportState(
                uri = uri,
                filename = filename,
                status = MultiFileImportScreen.Status.ERROR,
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
            // Try different common date formats
            val dateTime = tryParseDateTime(dateTimeStr)

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