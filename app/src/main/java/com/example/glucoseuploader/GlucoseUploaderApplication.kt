package com.example.glucoseuploader

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GlucoseUploaderApplication : Application(), Configuration.Provider {

    private val tag = "GlucoseUploaderApp"
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        // Initialize WorkManager
        WorkManager.initialize(this, workManagerConfiguration)

        // Initialize application components
        initializeApp()
    }

    private fun initializeApp() {
        applicationScope.launch {
            try {
                // Check if Health Connect is available on first launch
                val healthConnectUploader = HealthConnectUploader(this@GlucoseUploaderApplication)

                // Safe call for suspend function
                val isAvailable = try {
                    healthConnectUploader.isHealthConnectAvailable()
                } catch (e: Exception) {
                    Log.e(tag, "Error checking HC availability", e)
                    false
                }

                Log.d(tag, "Health Connect available: $isAvailable")

                // Initialize any required background work
                if (isAvailable) {
                    val hasPermissions = try {
                        healthConnectUploader.hasPermissions()
                    } catch (e: Exception) {
                        Log.e(tag, "Error checking permissions", e)
                        false
                    }

                    Log.d(tag, "Health Connect permissions granted: $hasPermissions")

                    if (hasPermissions) {
                        // Check if background work is enabled in preferences
                        val prefs = getSharedPreferences(
                            PreferenceConstants.PREFERENCES_NAME,
                            MODE_PRIVATE
                        )
                        val backgroundCheckEnabled = prefs.getBoolean(
                            PreferenceConstants.PREF_BACKGROUND_CHECK_ENABLED,
                            false
                        )

                        if (backgroundCheckEnabled) {
                            val interval = prefs.getInt(
                                PreferenceConstants.PREF_BACKGROUND_CHECK_INTERVAL,
                                12
                            )

                            // Schedule background checks
                            try {
                                GlucoseReadWorker.scheduleRepeating(
                                    this@GlucoseUploaderApplication,
                                    intervalHours = interval.toLong()
                                )

                                Log.d(tag, "Background checks scheduled every $interval hours")
                            } catch (e: Exception) {
                                Log.e(tag, "Error scheduling background checks", e)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Error during application initialization", e)
            }
        }
    }

    // Fix for Configuration.Provider implementation
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}