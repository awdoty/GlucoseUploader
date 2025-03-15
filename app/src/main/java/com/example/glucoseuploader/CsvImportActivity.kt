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
import com.example.glucoseuploader.ui.theme.GlucoseUploaderTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

        // Get file metadata for logging
        val fileName = getFileName(uri)
        val fileSize = getFileSize(uri)
        val fileType = contentResolver.getType(uri)

        Log.d(TAG, "Received action: $action with URI: $uri")
        Log.d(TAG, "File name: $fileName")
        Log.d(TAG, "File size: $fileSize bytes")
        Log.d(TAG, "File type: $fileType")

        // Process the CSV file using our composable
        setContent {
            GlucoseUploaderTheme {
                CsvImportScreen(
                    uri = uri,
                    healthConnectUploader = healthConnectUploader,
                    onImportComplete = { success, message ->
                        if (success) {
                            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                            // Add delay before finishing to show the toast
                            android.os.Handler(mainLooper).postDelayed({
                                finish()
                            }, 2000)
                        } else {
                            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                            if (message.contains("Error reading CSV")) {
                                finish()
                            }
                        }
                    }
                )
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