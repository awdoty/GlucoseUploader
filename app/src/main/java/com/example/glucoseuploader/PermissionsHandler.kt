package com.example.glucoseuploader

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Metadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID

/**
 * Helper class for managing Health Connect permissions.
 * Updated for Health Connect 1.1.0-alpha12
 */
class PermissionsHandler(private val context: Context) {
    private val tag = "PermissionsHandler"

    // Health Connect client - lazy initialization
    private val healthConnectClient by lazy {
        try {
            HealthConnectClient.getOrCreate(context)
        } catch (e: Exception) {
            Log.e(tag, "Error creating Health Connect client", e)
            null
        }
    }

    // Required permissions for this app
    private val requiredPermissions = setOf(
        HealthPermission.getReadPermission(BloodGlucoseRecord::class),
        HealthPermission.getWritePermission(BloodGlucoseRecord::class)
    )

    // Optional extended permissions - using strings since these permissions might not be
    // available as constants in older Health Connect versions
    private val historyReadPermission = "android.permission.health.READ_HEALTH_DATA_HISTORY"
    private val backgroundReadPermission = "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"

    /**
     * Check if all required Health Connect permissions are granted
     */
    suspend fun checkRequiredPermissions(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val client = healthConnectClient ?: return@withContext false
                val grantedPermissions = client.permissionController.getGrantedPermissions()
                grantedPermissions.containsAll(requiredPermissions)
            } catch (e: Exception) {
                Log.e(tag, "Error checking Health Connect permissions", e)
                false
            }
        }
    }

    /**
     * Get a list of missing required permissions
     */
    suspend fun getMissingRequiredPermissions(): Set<String> {
        return withContext(Dispatchers.IO) {
            try {
                val client = healthConnectClient ?: return@withContext requiredPermissions
                val grantedPermissions = client.permissionController.getGrantedPermissions()
                requiredPermissions.filter { !grantedPermissions.contains(it) }.toSet()
            } catch (e: Exception) {
                Log.e(tag, "Error checking missing permissions", e)
                requiredPermissions
            }
        }
    }

    /**
     * Check if history read permission is granted
     */
    suspend fun hasHistoryReadPermission(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val client = healthConnectClient ?: return@withContext false
                val grantedPermissions = client.permissionController.getGrantedPermissions()
                // Convert strings to the actual permission objects if needed
                grantedPermissions.any { it.toString().contains("READ_HEALTH_DATA_HISTORY") }
            } catch (e: Exception) {
                Log.e(tag, "Error checking history read permission", e)
                false
            }
        }
    }

    /**
     * Check if background read permission is granted
     */
    suspend fun hasBackgroundReadPermission(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val client = healthConnectClient ?: return@withContext false
                val grantedPermissions = client.permissionController.getGrantedPermissions()
                // Convert strings to the actual permission objects if needed
                grantedPermissions.any { it.toString().contains("READ_HEALTH_DATA_IN_BACKGROUND") }
            } catch (e: Exception) {
                Log.e(tag, "Error checking background read permission", e)
                false
            }
        }
    }

    /**
     * Check if Health Connect is available on the device
     */
    suspend fun isHealthConnectAvailable(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
            } catch (e: Exception) {
                Log.e(tag, "Error checking Health Connect availability", e)
                false
            }
        }
    }

    /**
     * Create proper metadata for a glucose record
     */
    fun createMetadata(): Metadata {
        // Create metadata using updated API for Health Connect alpha12
        return Metadata(
            clientRecordId = UUID.randomUUID().toString(),
            clientRecordVersion = 1,
            dataOrigin = DataOrigin.SAMSUNG_HEALTH_APP, // Adjust to appropriate source
            lastModifiedTime = Instant.now(),
            recordingMethod = Metadata.RECORDING_METHOD_MANUALLY_ENTERED
        )
    }

    /**
     * Request required permissions using direct intent
     */
    fun requestPermissions(activity: ComponentActivity) {
        try {
            // For Health Connect 1.1.0-alpha12, use direct intent approach
            val intent = Intent("androidx.health.ACTION_HEALTH_CONNECT_PERMISSIONS")
            intent.putExtra("androidx.health.EXTRA_PERMISSIONS", requiredPermissions.toList().toTypedArray())
            intent.putExtra("androidx.health.EXTRA_FROM_PERMISSIONS_REQUEST", true)
            activity.startActivity(intent)
        } catch (e: Exception) {
            Log.e(tag, "Error requesting permissions", e)
            // Fallback: open Health Connect directly
            openHealthConnectApp(activity)
        }
    }

    /**
     * Request history read permission
     */
    fun requestHistoryReadPermission(activity: ComponentActivity) {
        try {
            // Use the standard Health Connect intent approach
            val intent = Intent("androidx.health.ACTION_HEALTH_CONNECT_PERMISSIONS")
            intent.putExtra("androidx.health.EXTRA_PERMISSIONS", arrayOf(historyReadPermission))
            activity.startActivity(intent)
        } catch (e: Exception) {
            Log.e(tag, "Error requesting history read permission", e)
            // Fallback: open Health Connect app
            openHealthConnectApp(activity)
        }
    }

    /**
     * Open Health Connect app
     */
    fun openHealthConnectApp(activity: ComponentActivity) {
        try {
            // Standard intent for Health Connect settings
            val intent = Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS")
            activity.startActivity(intent)
        } catch (e: Exception) {
            try {
                // Fallback to older intent action
                val intent = Intent("android.health.connect.action.HEALTH_CONNECT_SETTINGS")
                activity.startActivity(intent)
            } catch (e2: Exception) {
                Log.e(tag, "Error opening Health Connect: ${e2.message}")
            }
        }
    }

    /**
     * Revoke all Health Connect permissions
     */
    suspend fun revokeAllPermissions() {
        withContext(Dispatchers.IO) {
            try {
                healthConnectClient?.permissionController?.revokeAllPermissions()
            } catch (e: Exception) {
                Log.e(tag, "Error revoking all permissions", e)
            }
        }
    }
}