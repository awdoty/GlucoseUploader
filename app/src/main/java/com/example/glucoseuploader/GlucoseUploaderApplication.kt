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

    private val TAG = "GlucoseUploaderApp"
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
                val isAvailable = healthConnectUploader.isHealthConnectAvailable()
                Log.d(TAG, "Health Connect available: $isAvailable")

                // Initialize any required background work
                if (isAvailable) {
                    val hasPermissions = healthConnectUploader.hasPermissions()
                    Log.d(TAG, "Health Connect permissions granted: $hasPermissions")

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
                            GlucoseReadWorker.scheduleRepeating(
                                this@GlucoseUploaderApplication,
                                intervalHours = interval.toLong()
                            )

                            Log.d(TAG, "Background checks scheduled every $interval hours")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during application initialization", e)
            }
        }
    }

    override fun getWorkManagerConfiguration(): Configuration {
        return Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
    }
}