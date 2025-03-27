package com.example.glucoseuploader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.lifecycle.lifecycleScope
import com.example.glucoseuploader.ui.theme.GlucoseUploaderTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val tag = "MainActivity"
    private lateinit var healthConnectUploader: HealthConnectUploader
    private val glucoseCsvParser = GlucoseCsvParser()

    // State to track if we're coming back from a permission request
    private var pendingPermissionCheck = false

    // Permission request launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Check permission status when we return
        pendingPermissionCheck = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Health Connect uploader
        healthConnectUploader = HealthConnectUploader(this)

        // Set up the UI
        setContent {
            GlucoseUploaderTheme {
                GlucoseUploaderApp(
                    healthConnectUploader = healthConnectUploader,
                    onSelectFile = { selectCsvFile() },
                    onRequestPermissions = { requestHealthConnectPermissions() }
                )
            }
        }

        // Check Health Connect status
        checkHealthConnectStatus()

        // Handle intent (for when files are shared to this app)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()

        // If we're coming back from a permission request, check status
        if (pendingPermissionCheck) {
            pendingPermissionCheck = false
            checkHealthConnectStatus()
        }
    }

    /**
     * Handle incoming intent (file shared to app)
     */
    private fun handleIntent(intent: Intent?) {
        intent?.let {
            when (intent.action) {
                Intent.ACTION_SEND -> {
                    // Single file shared
                    if (intent.type?.startsWith("text/") == true ||
                        intent.type?.contains("csv") == true) {

                        val uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(Intent.EXTRA_STREAM)
                        }

                        uri?.let { fileUri ->
                            processFile(fileUri)
                        }
                    }
                }
                Intent.ACTION_VIEW -> {
                    // File opened with this app
                    val uri = intent.data
                    if (uri != null) {
                        processFile(uri)
                    }
                }
            }
        }
    }

    /**
     * Process a CSV file and upload data to Health Connect
     */
    private fun processFile(uri: Uri) {
        // Launch import activity with this URI
        val intent = Intent(this, ImportActivity::class.java).apply {
            data = uri
        }
        startActivity(intent)
    }

    /**
     * Request Health Connect permissions
     */
    private fun requestHealthConnectPermissions() {
        lifecycleScope.launch {
            try {
                // Check if Health Connect is available
                val isAvailable = healthConnectUploader.isHealthConnectAvailable()

                if (isAvailable) {
                    // Check if we already have permissions
                    val hasPermissions = healthConnectUploader.hasPermissions()

                    if (!hasPermissions) {
                        // For Android 14+, use the proper permissions flow
                        val intent = Intent("androidx.health.ACTION_HEALTH_CONNECT_PERMISSIONS").apply {
                            putExtra("androidx.health.EXTRA_PERMISSIONS", healthConnectUploader.getRequiredPermissions().toTypedArray())
                        }

                        try {
                            requestPermissionLauncher.launch(intent)
                        } catch (e: Exception) {
                            Log.e(tag, "Error launching permission request", e)
                            lifecycleScope.launch {
                                healthConnectUploader.openHealthConnectApp()
                            }
                        }
                    } else {
                        Toast.makeText(this@MainActivity, "Already have permissions", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // Health Connect not available, try to install it
                    healthConnectUploader.openHealthConnectApp()
                }
            } catch (e: Exception) {
                Log.e(tag, "Error requesting permissions", e)
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Launch file picker to select a CSV file
     */
    private fun selectCsvFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "text/csv",
                "text/comma-separated-values",
                "application/csv",
                "text/plain"
            ))
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Error opening file picker: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Check Health Connect status and update UI state
     */
    private fun checkHealthConnectStatus() {
        lifecycleScope.launch {
            try {
                val isAvailable = healthConnectUploader.isHealthConnectAvailable()
                Log.d(tag, "Health Connect available: $isAvailable")

                if (isAvailable) {
                    val hasPermissions = healthConnectUploader.hasPermissions()
                    Log.d(tag, "Has permissions: $hasPermissions")
                }
            } catch (e: Exception) {
                Log.e(tag, "Error checking Health Connect status", e)
            }
        }
    }
}

/**
 * Main app composable
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlucoseUploaderApp(
    healthConnectUploader: HealthConnectUploader,
    onSelectFile: () -> Unit,
    onRequestPermissions: () -> Unit
) {
    var isHealthConnectAvailable by remember { mutableStateOf(false) }
    var hasPermissions by remember { mutableStateOf(false) }
    var latestReading by remember { mutableStateOf<BloodGlucoseRecord?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // Check Health Connect status and permissions
    LaunchedEffect(key1 = Unit) {
        isLoading = true
        try {
            isHealthConnectAvailable = healthConnectUploader.isHealthConnectAvailable()

            if (isHealthConnectAvailable) {
                hasPermissions = healthConnectUploader.hasPermissions()

                if (hasPermissions) {
                    latestReading = healthConnectUploader.readLatestBloodGlucoseRecord()
                }
            }
        } catch (e: Exception) {
            Log.e("GlucoseUploaderApp", "Error checking status", e)
        } finally {
            isLoading = false
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                Text("Checking Health Connect status...")
            } else {
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

                        if (!isHealthConnectAvailable) {
                            Text(
                                text = "Health Connect is not available on this device.",
                                style = MaterialTheme.typography.bodyLarge
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = {
                                    healthConnectUploader.openHealthConnectApp()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Install Health Connect")
                            }
                        } else if (!hasPermissions) {
                            Text(
                                text = "Permissions needed to access glucose data.",
                                style = MaterialTheme.typography.bodyLarge
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = onRequestPermissions,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Grant Permissions")
                            }
                        } else {
                            Text(
                                text = "✓ Health Connect is available and permissions granted",
                                style = MaterialTheme.typography.bodyLarge
                            )

                            // Show latest reading if available
                            latestReading?.let { reading ->
                                Spacer(modifier = Modifier.height(16.dp))
                                Divider()
                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = "Latest Reading",
                                    style = MaterialTheme.typography.titleMedium
                                )

                                Text(
                                    text = "${reading.level.inMilligramsPerDeciliter} mg/dL at ${healthConnectUploader.formatTimestamp(reading.time)}",
                                    style = MaterialTheme.typography.bodyLarge
                                )
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
                            text = "Import CSV Data",
                            style = MaterialTheme.typography.titleLarge
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = onSelectFile,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = isHealthConnectAvailable && hasPermissions
                        ) {
                            Text("Select CSV File")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "You can also share CSV files directly from your glucose app to this app",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Instructions Card
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

                        Text(text = "1. Export CSV from your glucose meter app (like AgaMatrix)")
                        Text(text = "2. Share the file directly to this app or select it using the button above")
                        Text(text = "3. The app will upload the readings to Health Connect")
                        Text(text = "4. View your glucose history in Health Connect or other compatible apps")
                    }
                }
            }
        }
    }
}