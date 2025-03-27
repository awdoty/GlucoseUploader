package com.example.glucoseuploader

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodGlucoseRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Handles Health Connect permissions.
 */
class PermissionsHandler(private val context: Context) {

    companion object {
        // Define constants that were missing in the original code
        const val SAMSUNG_HEALTH_APP = "com.samsung.health"
        const val RECORDING_METHOD_MANUALLY_ENTERED = "manually_entered"
    }

    private val healthConnectClient by lazy {
        HealthConnectClient.getOrCreate(context)
    }

    // Permissions required for the app
    private val permissions = setOf(
        HealthPermission.getReadPermission(BloodGlucoseRecord::class),
        HealthPermission.getWritePermission(BloodGlucoseRecord::class)
    )

    // Permission state flow
    private val _permissionState = MutableStateFlow(false)
    val permissionState: StateFlow<Boolean> = _permissionState.asStateFlow()

    /**
     * Check if Health Connect is available on this device.
     */
    suspend fun isHealthConnectAvailable(): Boolean {
        return HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }

    /**
     * Check if all required permissions are granted.
     */
    suspend fun checkPermissions(): Boolean {
        val grantedPermissions = healthConnectClient.permissionController
            .getGrantedPermissions(permissions)

        val allGranted = grantedPermissions.containsAll(permissions)
        _permissionState.value = allGranted

        return allGranted
    }

    /**
     * Request permissions using the permission controller.
     */
    fun requestPermissions(activity: AppCompatActivity): ActivityResultLauncher<Intent> {
        // Create the permission launcher
        val requestPermissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            // When the result comes back, check permissions again
            activity.lifecycleScope.launch {
                checkPermissions()
            }
        }

        return requestPermissionLauncher
    }

    /**
     * Launch the permission request flow.
     */
    suspend fun launchPermissionRequest(launcher: ActivityResultLauncher<Intent>) {
        // Create the intent for the permission request
        val intent = PermissionController.createRequestPermissionResultContract()
            .createIntent(context, permissions)

        // Launch the permission request
        launcher.launch(intent)
    }

    /**
     * Open Health Connect settings if available.
     */
    fun openHealthConnectSettings() {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setData(Uri.parse("healthconnect://settings"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            // If Health Connect settings aren't available, try to install the app
            val playStoreIntent = Intent(Intent.ACTION_VIEW).apply {
                setData(Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata"))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(playStoreIntent)
        }
    }
}