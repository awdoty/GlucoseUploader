package com.example.glucoseuploader

import android.content.Context
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Helper class for managing Health Connect permissions.
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

    // Optional extended permissions
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
                grantedPermissions.contains(historyReadPermission)
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
                grantedPermissions.contains(backgroundReadPermission)
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
            healthConnectClient != null
        }
    }

    /**
     * Create proper metadata for a glucose record
     */
    fun createMetadata(): Metadata {
        // Create device info for manual entry
        val device = Device(
            type = Device.TYPE_PHONE,
            manufacturer = android.os.Build.MANUFACTURER,
            model = android.os.Build.MODEL
        )

        // Use manual entry recording method since CSV imports are manual
        return Metadata.manualEntry(device)
    }

    /**
     * Request required permissions in given activity
     */
    fun requestPermissions(activity: ComponentActivity, launcher: ActivityResultLauncher<Set<String>>) {
        try {
            launcher.launch(requiredPermissions)
        } catch (e: Exception) {
            Log.e(tag, "Error launching permission request", e)
        }
    }

    /**
     * Request history read permission
     */
    fun requestHistoryReadPermission(activity: ComponentActivity) {
        try {
            val permissionController = healthConnectClient?.permissionController
            permissionController?.requestPermission(
                activity,
                setOf(historyReadPermission)
            )
        } catch (e: Exception) {
            Log.e(tag, "Error requesting history read permission", e)
        }
    }

    /**
     * Request background read permission
     */
    fun requestBackgroundReadPermission(activity: ComponentActivity) {
        try {
            val permissionController = healthConnectClient?.permissionController
            permissionController?.requestPermission(
                activity,
                setOf(backgroundReadPermission)
            )
        } catch (e: Exception) {
            Log.e(tag, "Error requesting background read permission", e)
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