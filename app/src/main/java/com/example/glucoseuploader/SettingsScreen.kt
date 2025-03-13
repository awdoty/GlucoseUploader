package com.example.glucoseuploader

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.records.BloodGlucoseRecord
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.time.ZoneId

/**
 * Settings screen for app configuration
 */
@Composable
fun SettingsScreen(
    healthConnectUploader: HealthConnectUploader,
    requestPermissions: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Get shared preferences
    val prefs = remember { context.getSharedPreferences("GlucoseUploaderPrefs", Context.MODE_PRIVATE) }

    // State variables for settings
    var backgroundCheckEnabled by remember { mutableStateOf(prefs.getBoolean(PREF_BACKGROUND_CHECK_ENABLED, false)) }
    var backgroundCheckInterval by remember { mutableIntStateOf(prefs.getInt(PREF_BACKGROUND_CHECK_INTERVAL, 12)) }
    var defaultMealType by remember { mutableIntStateOf(prefs.getInt(PREF_DEFAULT_MEAL_TYPE, BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN)) }
    var showNotifications by remember { mutableStateOf(prefs.getBoolean(PREF_SHOW_NOTIFICATIONS, true)) }
    var detailedAnalysis by remember { mutableStateOf(prefs.getBoolean(PREF_DETAILED_ANALYSIS, true)) }
    var isHealthConnectAvailable by remember { mutableStateOf(false) }
    var hasPermissions by remember { mutableStateOf(false) }
    var healthConnectVersion by remember { mutableStateOf<String?>(null) }
    var isChecking by remember { mutableStateOf(false) }

    // Check Health Connect availability
    LaunchedEffect(key1 = Unit) {
        isChecking = true
        try {
            isHealthConnectAvailable = healthConnectUploader.isHealthConnectAvailable()
            if (isHealthConnectAvailable) {
                hasPermissions = healthConnectUploader.hasPermissions()
                healthConnectVersion = healthConnectUploader.getHealthConnectVersion()
            }
        } catch (e: Exception) {
            // Handle error
        } finally {
            isChecking = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.h5,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Health Connect Status Section
        HealthConnectStatusCard(
            isHealthConnectAvailable = isHealthConnectAvailable,
            hasPermissions = hasPermissions,
            healthConnectVersion = healthConnectVersion,
            requestPermissions = requestPermissions,
            onOpenHealthConnect = {
                coroutineScope.launch {
                    healthConnectUploader.openHealthConnectApp(context)
                }
            },
            isChecking = isChecking
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Background Check Settings
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = 4.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Background Glucose Check",
                    style = MaterialTheme.typography.h6,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Enable background checking")
                    Switch(
                        checked = backgroundCheckEnabled,
                        onCheckedChange = {
                            backgroundCheckEnabled = it
                            savePreference(prefs, PREF_BACKGROUND_CHECK_ENABLED, it)

                            // Schedule or cancel background work
                            if (it) {
                                GlucoseReadWorker.scheduleRepeating(
                                    context,
                                    intervalHours = backgroundCheckInterval.toLong(),
                                    timeRangeHours = 24,
                                    startDelayMinutes = 1
                                )
                            } else {
                                GlucoseReadWorker.cancelWork(context, periodic = true)
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Only show interval selector if background checking is enabled
                if (backgroundCheckEnabled) {
                    Text("Check interval")

                    Spacer(modifier = Modifier.height(4.dp))

                    // Interval selector
                    Slider(
                        value = backgroundCheckInterval.toFloat(),
                        onValueChange = {
                            backgroundCheckInterval = it.toInt()
                            savePreference(prefs, PREF_BACKGROUND_CHECK_INTERVAL, it.toInt())
                        },
                        valueRange = 1f..24f,
                        steps = 5,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("1 hour", style = MaterialTheme.typography.caption)
                        Text("12 hours", style = MaterialTheme.typography.caption)
                        Text("24 hours", style = MaterialTheme.typography.caption)
                    }

                    Text(
                        text = "Currently set to check every $backgroundCheckInterval hours",
                        style = MaterialTheme.typography.body2
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Show notifications setting
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Show notifications")
                        Switch(
                            checked = showNotifications,
                            onCheckedChange = {
                                showNotifications = it
                                savePreference(prefs, PREF_SHOW_NOTIFICATIONS, it)
                            }
                        )
                    }

                    // Detailed analysis setting
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Detailed analysis")
                        Switch(
                            checked = detailedAnalysis,
                            onCheckedChange = {
                                detailedAnalysis = it
                                savePreference(prefs, PREF_DETAILED_ANALYSIS, it)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Test button
                    Button(
                        onClick = {
                            // Run a test background check
                            GlucoseReadWorker.schedule(
                                context,
                                delayMinutes = 1,
                                timeRangeHours = 24,
                                skipNotification = false,
                                detailedAnalysis = detailedAnalysis
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Test"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Test Background Check (1 min)")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Default Meal Type Settings
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = 4.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Default Meal Type",
                    style = MaterialTheme.typography.h6,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Select the default meal type to use when uploading glucose readings",
                    style = MaterialTheme.typography.body2
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Meal type selection
                val mealTypes = listOf(
                    Pair(BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN, "Unknown"),
                    Pair(BloodGlucoseRecord.RELATION_TO_MEAL_BEFORE_MEAL, "Before Meal"),
                    Pair(BloodGlucoseRecord.RELATION_TO_MEAL_AFTER_MEAL, "After Meal"),
                    Pair(BloodGlucoseRecord.RELATION_TO_MEAL_FASTING, "Fasting"),
                    Pair(BloodGlucoseRecord.RELATION_TO_MEAL_GENERAL, "General")
                )

                Column {
                    mealTypes.forEach { (type, name) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = defaultMealType == type,
                                onClick = {
                                    defaultMealType = type
                                    savePreference(prefs, PREF_DEFAULT_MEAL_TYPE, type)
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(name)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // About Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = 4.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "About",
                    style = MaterialTheme.typography.h6,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text("Glucose Uploader")
                Text("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "This app allows you to upload glucose readings from CSV files to Health Connect.",
                    style = MaterialTheme.typography.body2
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Support link
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/yourusername/glucose-uploader"))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Help,
                        contentDescription = "Support"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Support & Documentation")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Debug Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = 4.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Advanced Options",
                    style = MaterialTheme.typography.h6,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        // Clear all scheduled work
                        GlucoseReadWorker.cancelAllWork(context)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color.Red, contentColor = Color.White)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Cancel"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cancel All Scheduled Tasks")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Clear permissions button
                Button(
                    onClick = {
                        coroutineScope.launch {
                            try {
                                healthConnectUploader.revokeAllPermissions()
                                hasPermissions = false
                            } catch (e: Exception) {
                                // Handle error
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color.Gray)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = "Revoke"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Revoke Health Connect Permissions")
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

/**
 * Card showing Health Connect status
 */
@Composable
fun HealthConnectStatusCard(
    isHealthConnectAvailable: Boolean,
    hasPermissions: Boolean,
    healthConnectVersion: String?,
    requestPermissions: () -> Unit,
    onOpenHealthConnect: () -> Unit,
    isChecking: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 4.dp,
        backgroundColor = if (isHealthConnectAvailable && hasPermissions)
            Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Health Connect Status",
                style = MaterialTheme.typography.h6,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (isChecking) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.CenterHorizontally),
                    strokeWidth = 2.dp
                )
                Text(
                    text = "Checking Health Connect status...",
                    style = MaterialTheme.typography.body2
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isHealthConnectAvailable) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = if (isHealthConnectAvailable) "Available" else "Not Available",
                        tint = if (isHealthConnectAvailable) Color.Green else Color.Red
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isHealthConnectAvailable) "Health Connect Available" else "Health Connect Not Available",
                        style = MaterialTheme.typography.body1
                    )
                }

                if (isHealthConnectAvailable) {
                    Spacer(modifier = Modifier.height(4.dp))

                    // Show version if available
                    healthConnectVersion?.let {
                        Text(
                            text = "Version: $it",
                            style = MaterialTheme.typography.caption
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Show permissions status
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (hasPermissions) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = if (hasPermissions) "Granted" else "Not Granted",
                            tint = if (hasPermissions) Color.Green else Color.Red
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (hasPermissions) "Permissions Granted" else "Permissions Required",
                            style = MaterialTheme.typography.body1
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (!hasPermissions) {
                        Button(
                            onClick = requestPermissions,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Request Permissions")
                        }
                    }

                    Button(
                        onClick = onOpenHealthConnect,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open Health Connect")
                    }
                } else {
                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = onOpenHealthConnect,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Install Health Connect")
                    }
                }
            }
        }
    }
}

/**
 * Helper function to save boolean preference
 */
private fun savePreference(prefs: SharedPreferences, key: String, value: Boolean) {
    prefs.edit().putBoolean(key, value).apply()
}

/**
 * Helper function to save integer preference
 */
private fun savePreference(prefs: SharedPreferences, key: String, value: Int) {
    prefs.edit().putInt(key, value).apply()
}

/**
 * Helper function to save string preference
 */
private fun savePreference(prefs: SharedPreferences, key: String, value: String) {
    prefs.edit().putString(key, value).apply()
}

// Preference keys
private const val PREF_BACKGROUND_CHECK_ENABLED = "background_check_enabled"
private const val PREF_BACKGROUND_CHECK_INTERVAL = "background_check_interval"
private const val PREF_DEFAULT_MEAL_TYPE = "default_meal_type"
private const val PREF_SHOW_NOTIFICATIONS = "show_notifications"
private const val PREF_DETAILED_ANALYSIS = "detailed_analysis"