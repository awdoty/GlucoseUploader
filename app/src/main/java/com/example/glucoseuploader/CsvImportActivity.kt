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
import androidx.lifecycle.lifecycleScope
import com.example.glucoseuploader.ui.theme.GlucoseUploaderTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Activity that handles CSV file imports from share intents
 */
class CsvImportActivity : ComponentActivity() {

    private val tag = "CsvImportActivity"
    private lateinit var healthConnectUploader: HealthConnectUploader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        healthConnectUploader = HealthConnectUploader(this)

        // Get the URI of the CSV file from the intent
        val uri = getFileUriFromIntent(intent)

        if (uri == null) {
            Toast.makeText(this, "No CSV file provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Log file details for debugging
        logFileDetails(uri)

        // Set up the UI
        setContent {
            GlucoseUploaderTheme {
                CsvImportScreen(
                    uri = uri,
                    healthConnectUploader = healthConnectUploader,
                    onImportComplete = { success, message ->
                        handleImportResult(success, message)
                    }
                )
            }
        }

        // Check Health Connect availability and permissions
        checkHealthConnectStatus()
    }

    /**
     * Handle the result of the import process
     */
    private fun handleImportResult(success: Boolean, message: String) {
        // Show toast with the result
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

        // If we need to request permissions, we shouldn't finish the activity yet
        if (message.contains("grant permissions", ignoreCase = true)) {
            requestHealthConnectPermissions()
            return
        }

        // Add delay before finishing to show the toast
        lifecycleScope.launch {
            delay(if (success) 1500 else 2500)
            finish()
        }
    }

    /**
     * Extract file URI from Intent, supporting different intent actions
     */
    private fun getFileUriFromIntent(intent: Intent?): Uri? {
        if (intent == null) return null

        return when (intent.action) {
            // Direct file viewing
            Intent.ACTION_VIEW -> intent.data

            // File sharing from another app
            Intent.ACTION_SEND -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
            }

            // Multiple files shared (we take only the first one)
            Intent.ACTION_SEND_MULTIPLE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)?.firstOrNull()
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.firstOrNull()
                }
            }

            // Unknown action
            else -> null
        }
    }

    /**
     * Log file metadata for debugging
     */
    private fun logFileDetails(uri: Uri) {
        try {
            val fileName = getFileName(uri)
            val fileSize = getFileSize(uri)
            val fileType = contentResolver.getType(uri)

            Log.d(tag, "Received URI: $uri")
            Log.d(tag, "File name: $fileName")
            Log.d(tag, "File size: $fileSize bytes")
            Log.d(tag, "File type: $fileType")

            // Try to read first few lines for debugging
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = inputStream.bufferedReader()
                val firstLine = reader.readLine()
                Log.d(tag, "First line: $firstLine")
            }
        } catch (e: Exception) {
            Log.e(tag, "Error logging file details", e)
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
                Log.e(tag, "Error getting filename", e)
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
                Log.e(tag, "Error getting file size", e)
            }
        }
        return size
    }

    /**
     * Check if Health Connect is available and we have necessary permissions
     */
    private fun checkHealthConnectStatus() {
        lifecycleScope.launch {
            try {
                val isAvailable = healthConnectUploader.isHealthConnectAvailable()
                Log.d(tag, "Health Connect available: $isAvailable")

                if (isAvailable) {
                    val hasPermissions = healthConnectUploader.hasPermissions()
                    Log.d(tag, "Health Connect permissions: $hasPermissions")
                }
            } catch (e: Exception) {
                Log.e(tag, "Error checking Health Connect status", e)
            }
        }
    }

    /**
     * Request Health Connect permissions
     */
    private fun requestHealthConnectPermissions() {
        lifecycleScope.launch {
            try {
                val intent = Intent("androidx.health.ACTION_HEALTH_CONNECT_PERMISSIONS")
                val permissions = healthConnectUploader.getRequiredPermissions().toList().toTypedArray()
                intent.putExtra("androidx.health.EXTRA_PERMISSIONS", permissions)
                startActivity(intent)
            } catch (e: Exception) {
                Log.e(tag, "Error requesting permissions", e)
                try {
                    healthConnectUploader.openHealthConnectApp(this@CsvImportActivity)
                } catch (e2: Exception) {
                    Log.e(tag, "Error opening Health Connect app", e2)
                    Toast.makeText(
                        this@CsvImportActivity,
                        "Error requesting permissions. Please install Health Connect from Play Store.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /**
     * Handle new intents when activity is already running
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        val newUri = getFileUriFromIntent(intent)

        if (newUri != null) {
            // Restart the activity with the new URI
            val restartIntent = Intent(this, CsvImportActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = newUri
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(restartIntent)
            finish()
        }
    }
}