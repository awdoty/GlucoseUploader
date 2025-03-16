package com.example.glucoseuploader

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Reusable error card component
 */
@Composable
fun ErrorCard(errorMessage: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEEEE)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Error",
                    tint = Color.Red
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Error",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.Red
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}