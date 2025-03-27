package com.example.glucoseuploader

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application class for the Glucose Uploader app
 */
class GlucoseUploaderApplication : Application() {
    private val tag = "GlucoseUploaderApp"

    // Application coroutine scope for background operations
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Health Connect uploader instance
    private lateinit var healthConnectUploader: HealthConnectUploader

    override fun onCreate() {
        super.onCreate()

        // Initialize Health Connect uploader
        healthConnectUploader = HealthConnectUploader(this)

        // Check Health Connect availability at startup
        applicationScope.launch {
            try {
                val isAvailable = healthConnectUploader.isHealthConnectAvailable()
                Log.d(tag, "Health Connect available: $isAvailable")

                if (isAvailable) {
                    val hasPermissions = healthConnectUploader.hasPermissions()
                    Log.d(tag, "Has permissions: $hasPermissions")
                }
            } catch (e: Exception) {
                Log.e(tag, "Error checking Health Connect availability", e)
            }
        }
    }
}