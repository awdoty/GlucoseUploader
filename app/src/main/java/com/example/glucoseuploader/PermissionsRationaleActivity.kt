package com.example.glucoseuploader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.glucoseuploader.ui.theme.GlucoseUploaderTheme

/**
 * Activity to explain Health Connect permissions rationale to the user
 */
class PermissionsRationaleActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            GlucoseUploaderTheme {
                PermissionsRationaleScreen()
            }
        }
    }

    @Composable
    fun PermissionsRationaleScreen() {
        val permissions = listOf(
            PermissionExplanation(
                "Read Blood Glucose",
                "This allows the app to read your blood glucose readings from Health Connect",
                "Used to view your existing glucose records and create visualizations"
            ),
            PermissionExplanation(
                "Write Blood Glucose",
                "This allows the app to write new blood glucose readings to Health Connect",
                "Used to upload your glucose readings from glucose meter CSV exports"
            )
        )

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Permissions Information",
                    style = MaterialTheme.typography.headlineMedium
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "This app requires the following Health Connect permissions:",
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn {
                    items(permissions) { permission ->
                        PermissionCard(permission)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { finish() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("I Understand")
                }
            }
        }
    }

    @Composable
    fun PermissionCard(permission: PermissionExplanation) {
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = permission.name,
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = permission.description,
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = permission.usage,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/**
 * Data class to represent a permission explanation
 */
data class PermissionExplanation(
    val name: String,
    val description: String,
    val usage: String
)