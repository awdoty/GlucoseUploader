package com.example.glucoseuploader

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Data class to represent messages about glucose data changes.
 */
sealed class ChangesMessage {
    data class RecordsUploaded(val count: Int) : ChangesMessage()
    data class RecordsDeleted(val count: Int) : ChangesMessage()
    data class NewTokenReceived(val token: String) : ChangesMessage()
    data class Error(val errorMessage: String) : ChangesMessage()
}

/**
 * Composable screen for tracking glucose data changes.
 */
@Composable
fun GlucoseChangesTrackingScreen(
    healthConnectUploader: HealthConnectUploader,
    onNewToken: (String) -> Unit
) {
    val TAG = "GlucoseChangesTracking"

    // State to track change messages
    val changesList = remember { mutableStateListOf<ChangesMessage>() }

    // Function to handle incoming messages
    fun handleMessage(message: ChangesMessage) {
        when (message) {
            is ChangesMessage.RecordsUploaded -> {
                Log.d(TAG, "Records uploaded: ${message.count}")
                changesList.add(message)
            }
            is ChangesMessage.RecordsDeleted -> {
                Log.d(TAG, "Records deleted: ${message.count}")
                changesList.add(message)
            }
            is ChangesMessage.NewTokenReceived -> {
                Log.d(TAG, "New token received: ${message.token}")
                changesList.add(message)
                onNewToken(message.token)
            }
            is ChangesMessage.Error -> {
                Log.e(TAG, "Error occurred: ${message.errorMessage}")
                changesList.add(message)
            }
        }
    }

    // Start tracking changes
    LaunchedEffect(key1 = healthConnectUploader) {
        healthConnectUploader.startTrackingChanges { message ->
            handleMessage(message)
        }
    }

    // UI for displaying changes
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Glucose Data Changes",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (changesList.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No changes detected yet...")
            }
        } else {
            LazyColumn {
                items(changesList) { change ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            val (title, details) = when (change) {
                                is ChangesMessage.RecordsUploaded ->
                                    Pair("Records Uploaded", "${change.count} new records added")
                                is ChangesMessage.RecordsDeleted ->
                                    Pair("Records Deleted", "${change.count} records removed")
                                is ChangesMessage.NewTokenReceived ->
                                    Pair("New Token", "Token updated")
                                is ChangesMessage.Error ->
                                    Pair("Error", change.errorMessage)
                            }

                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = details,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}