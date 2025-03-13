package com.example.glucoseuploader

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.lifecycle.lifecycleScope
import com.example.glucoseuploader.ui.theme.GlucoseUploaderTheme
import kotlinx.coroutines.launch

/**
 * Activity to handle Health Connect onboarding process
 */
class HealthConnectOnboardingActivity : ComponentActivity() {

    private val TAG = "HC_Onboarding"
    private lateinit var healthConnectUploader: HealthConnectUploader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        healthConnectUploader = HealthConnectUploader(this)

        setContent {
            GlucoseUploaderTheme {
                OnboardingScreen()
            }
        }
    }

    @Composable
    fun OnboardingScreen() {
        var isHealthConnectAvailable by remember { mutableStateOf(false) }
        var isLoading by remember { mutableStateOf(true) }
        var currentStep by remember { mutableStateOf(1) }

        // Check Health Connect availability when the screen is composed
        LaunchedEffect(Unit) {
            isHealthConnectAvailable = healthConnectUploader.isHealthConnectAvailable()
            isLoading = false
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colors.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Welcome to Glucose Uploader",
                    style = MaterialTheme.typography.h4
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "This app helps you upload your blood glucose readings to Health Connect.",
                    style = MaterialTheme.typography.body1
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (isLoading) {
                    CircularProgressIndicator()
                } else {
                    if (isHealthConnectAvailable) {
                        // Health Connect is available, show permissions information
                        Text(
                            text = "The app requires permissions to read and write blood glucose data.",
                            style = MaterialTheme.typography.body1
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                requestPermissions()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Grant Permissions")
                        }
                    } else {
                        // Health Connect is not available, show installation information
                        Text(
                            text = "Health Connect is required but not available on this device.",
                            style = MaterialTheme.typography.body1,
                            color = MaterialTheme.colors.error
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                lifecycleScope.launch {
                                    healthConnectUploader.openHealthConnectApp(this@HealthConnectOnboardingActivity)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Install Health Connect")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = {
                            // Return to MainActivity
                            val intent = Intent(this@HealthConnectOnboardingActivity, MainActivity::class.java)
                            startActivity(intent)
                            finish()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Continue")
                    }
                }
            }
        }
    }

    /**
     * Request Health Connect permissions
     */
    private fun requestPermissions() {
        lifecycleScope.launch {
            try {
                val client = healthConnectUploader.healthConnectClient
                if (client != null) {
                    // Define required permissions
                    val permissions = setOf(
                        HealthPermission.getReadPermission(BloodGlucoseRecord::class),
                        HealthPermission.getWritePermission(BloodGlucoseRecord::class)
                    )

                    // Request permissions
                    val contract = client.permissionController.createRequestPermissionResultContract()
                    val permissionLauncher = registerForActivityResult(contract) { granted ->
                        if (granted.containsAll(permissions)) {
                            Log.d(TAG, "All permissions granted")
                        } else {
                            Log.d(TAG, "Some permissions denied")
                        }
                    }

                    permissionLauncher.launch(permissions)
                } else {
                    Log.e(TAG, "Health Connect client is null")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error requesting permissions", e)
            }
        }
    }
}