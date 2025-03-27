package com.example.glucoseuploader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.glucoseuploader.ui.theme.GlucoseUploaderTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val tag = "MainActivity"
    private lateinit var healthConnectUploader: HealthConnectUploader

    // Flag to track if we've requested permissions and should check in onResume
    private var hasPendingPermissionRequest = false

    // Track app state
    private val appState = mutableStateOf(AppState())

    // For handling shared files
    private var sharedFileUri: Uri? = null
    private var lastFileHandlingTime: Long = 0

    // Request notification permissions for Android 13+
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Log.d(tag, "Notification permission ${if (isGranted) "granted" else "denied"}")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Health Connect uploader
        healthConnectUploader = HealthConnectUploader(this)

        // Request notification permission on Android 13+
        requestNotificationPermissionIfNeeded()

        // Log Health Connect info
        lifecycleScope.launch {
            try {
                healthConnectUploader.logHealthConnectInfo()
            } catch (e: Exception) {
                Log.e(tag, "Error logging Health Connect info: ${e.message}")
            }
        }

        // Check Health Connect availability and permissions
        checkHealthConnectStatus()

        // Handle intent if this activity was started from a share action
        handleIntent(intent)

        // Set the UI
        setContent {
            GlucoseUploaderTheme {
                MainScreen(
                    healthConnectUploader = healthConnectUploader,
                    appState = appState.value,
                    requestPermissions = { requestHealthConnectPermissions() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // If we have a pending permission request, check permissions again
        if (hasPendingPermissionRequest) {
            hasPendingPermissionRequest = false

            lifecycleScope.launch {
                try {
                    // Check if Health Connect is available
                    val isAvailable = healthConnectUploader.isHealthConnectAvailable()

                    if (isAvailable) {
                        // Re-check permissions after returning from Health Connect
                        val hasPermissions = healthConnectUploader.hasPermissions()

                        Log.d(tag, "Health Connect permissions after return: $hasPermissions")

                        // Update app state
                        appState.value = appState.value.copy(
                            isHealthConnectAvailable = isAvailable,
                            hasPermissions = hasPermissions
                        )

                        // Get latest reading if we have permissions
                        if (hasPermissions) {
                            try {
                                val latestRecord = healthConnectUploader.readLatestBloodGlucoseRecord()
                                latestRecord?.let {
                                    appState.value = appState.value.copy(
                                        latestReading = Pair(it.level.inMilligramsPerDeciliter, it.time)
                                    )
                                }
                            } catch (e: Exception) {
                                Log.e(tag, "Error fetching latest reading: ${e.message}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error checking permissions in onResume: ${e.message}")
                }
            }
        }
    }

    /**
     * Check Health Connect availability and permissions status
     */
    private fun checkHealthConnectStatus() {
        lifecycleScope.launch {
            try {
                // Check if Health Connect is available
                val isAvailable = healthConnectUploader.isHealthConnectAvailable()

                if (isAvailable) {
                    // Check if we have necessary permissions
                    val hasPermissions = healthConnectUploader.hasPermissions()

                    // Update app state
                    appState.value = appState.value.copy(
                        isHealthConnectAvailable = isAvailable,
                        hasPermissions = hasPermissions
                    )

                    // Get latest reading if we have permissions
                    if (hasPermissions) {
                        try {
                            val latestRecord = healthConnectUploader.readLatestBloodGlucoseRecord()
                            latestRecord?.let {
                                appState.value = appState.value.copy(
                                    latestReading = Pair(it.level.inMilligramsPerDeciliter, it.time)
                                )
                            }
                        } catch (e: Exception) {
                            Log.e(tag, "Error fetching latest reading: ${e.message}")
                        }
                    }
                } else {
                    // Health Connect not available
                    appState.value = appState.value.copy(
                        isHealthConnectAvailable = false,
                        hasPermissions = false
                    )
                }
            } catch (e: Exception) {
                Log.e(tag, "Error checking Health Connect status: ${e.message}")
            } finally {
                appState.value = appState.value.copy(isLoading = false)
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    /**
     * Request Health Connect permissions
     */
    fun requestHealthConnectPermissions() {
        lifecycleScope.launch {
            try {
                // First check if Health Connect is available
                val isAvailable = healthConnectUploader.isHealthConnectAvailable()

                if (isAvailable) {
                    // Check if we already have permissions
                    val hasPermissions = healthConnectUploader.hasPermissions()

                    if (!hasPermissions) {
                        // Set flag to check permissions when we return
                        hasPendingPermissionRequest = true

                        // Request permissions using modern approach for Android 14+
                        val intent = Intent("androidx.health.ACTION_HEALTH_CONNECT_PERMISSIONS")
                        val permissions = healthConnectUploader.getRequiredPermissions().toTypedArray()
                        intent.putExtra("androidx.health.EXTRA_PERMISSIONS", permissions)
                        intent.putExtra("androidx.health.EXTRA_FROM_PERMISSIONS_REQUEST", true)

                        try {
                            startActivity(intent)
                        } catch (e: Exception) {
                            Log.e(tag, "Failed to launch Health Connect permissions request", e)
                            openHealthConnectDirectly()
                        }
                    }
                } else {
                    // Health Connect is not available, attempt to install it
                    openHealthConnectDirectly()
                }
            } catch (e: Exception) {
                Log.e(tag, "Error requesting Health Connect permissions", e)
                Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Opens Health Connect app directly
     */
    private fun openHealthConnectDirectly() {
        lifecycleScope.launch {
            try {
                healthConnectUploader.openHealthConnectApp(this@MainActivity)
            } catch (e: Exception) {
                Log.e(tag, "Error opening Health Connect", e)
                Toast.makeText(this@MainActivity, "Please install Health Connect from Play Store", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Handle incoming intents (for shared files)
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        // Prevent duplicate handling of the same intent
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastFileHandlingTime < 1000) {
            Log.d(tag, "Skipping intent handling - too soon after previous")
            return
        }
        lastFileHandlingTime = currentTime

        Log.d(tag, "Handling intent: ${intent.action}, type: ${intent.type}")

        when (intent.action) {
            Intent.ACTION_SEND -> {
                handleSendIntent(intent)
            }
            Intent.ACTION_VIEW -> {
                handleViewIntent(intent)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                handleSendMultipleIntent(intent)
            }
        }
    }

    private fun handleSendIntent(intent: Intent) {
        // Check if the intent has a file that matches our supported types
        if (isSupportedFileType(intent.type)) {
            // Get the URI of the shared file
            val uri = getUriFromIntent(intent)

            Log.d(tag, "File URI from intent: $uri")

            uri?.let {
                try {
                    // Validate the URI by trying to read it
                    contentResolver.openInputStream(it)?.use { stream ->
                        // File is readable
                        sharedFileUri = it
                        Log.d(tag, "Received shared file: $it")

                        // Launch CSV import activity
                        launchCsvImportActivity(it)
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error handling shared file: $it", e)
                    Toast.makeText(this, "Error handling the shared file: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun handleSendMultipleIntent(intent: Intent) {
        // Check if the intent has files that match our supported types
        if (isSupportedFileType(intent.type)) {
            // Get the URIs of the shared files
            val uriList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            }

            if (uriList != null && uriList.isNotEmpty()) {
                // For now, just handle the first file
                val uri = uriList[0]
                try {
                    contentResolver.openInputStream(uri)?.use {
                        sharedFileUri = uri
                        Log.d(tag, "Received shared file from multiple: $uri")

                        // Launch CSV import activity
                        launchCsvImportActivity(uri)
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error handling multiple shared files", e)
                    Toast.makeText(this, "Error handling shared files: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun handleViewIntent(intent: Intent) {
        val uri = intent.data
        if (uri != null && isSupportedFileType(intent.type)) {
            Log.d(tag, "Received view intent with URI: $uri")

            try {
                launchCsvImportActivity(uri)
            } catch (e: Exception) {
                Log.e(tag, "Error handling VIEW intent: ${e.message}")
                Toast.makeText(this, "Error opening file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getUriFromIntent(intent: Intent): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        } ?: intent.data
    }

    private fun isSupportedFileType(type: String?): Boolean {
        return type?.let {
            it.startsWith("text/") ||
                    it == "application/vnd.ms-excel" ||
                    it == "application/octet-stream" ||
                    it.contains("spreadsheet") ||
                    it.contains("csv")
        } == true
    }

    private fun launchCsvImportActivity(uri: Uri) {
        val importIntent = Intent(this, CsvImportActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = uri
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(importIntent)
    }

    // Handle new intents when app is already running
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
        setIntent(intent)
    }
}

/**
 * Data class representing the app state for UI updates
 */
data class AppState(
    val isLoading: Boolean = true,
    val isHealthConnectAvailable: Boolean = false,
    val hasPermissions: Boolean = false,
    val latestReading: Pair<Double, java.time.Instant>? = null
)