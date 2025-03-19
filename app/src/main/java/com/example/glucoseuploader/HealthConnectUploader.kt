package com.example.glucoseuploader

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.changes.Change
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.BloodGlucose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

class HealthConnectUploader(val context: Context) {

    private val tag = "HealthConnectUploader"
    private val permissionsHandler = PermissionsHandler(context)

    // Health Connect client
    val healthConnectClient: HealthConnectClient? by lazy {
        try {
            HealthConnectClient.getOrCreate(context)
        } catch (e: Exception) {
            Log.e(tag, "Error creating Health Connect client", e)
            null
        }
    }

    /**
     * Check if Health Connect is available on this device
     */
    suspend fun isHealthConnectAvailable(): Boolean {
        return permissionsHandler.isHealthConnectAvailable()
    }

    /**
     * Get the Health Connect version if available
     */
    fun getHealthConnectVersion(): String? {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(
                "com.google.android.apps.healthdata", 0)
            packageInfo.versionName
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Check if we have the necessary permissions
     */
    suspend fun hasPermissions(): Boolean {
        return permissionsHandler.checkRequiredPermissions()
    }

    /**
     * Get required permissions for this app
     */
    fun getRequiredPermissions(): Set<String> {
        return setOf(
            HealthPermission.getReadPermission(BloodGlucoseRecord::class),
            HealthPermission.getWritePermission(BloodGlucoseRecord::class)
        )
    }

    /**
     * Check if we have history read permission
     */
    suspend fun hasHistoryReadPermission(): Boolean {
        return permissionsHandler.hasHistoryReadPermission()
    }

    /**
     * Check if background read is available
     */
    fun isBackgroundReadAvailable(): Boolean {
        return true  // Simplified check for Android 14+
    }

    /**
     * Check if we have background read permission
     */
    suspend fun hasBackgroundReadPermission(): Boolean {
        return permissionsHandler.hasBackgroundReadPermission()
    }

    /**
     * Request history read permission
     */
    fun requestHistoryReadPermission(activity: ComponentActivity) {
        permissionsHandler.requestHistoryReadPermission(activity)
    }

    /**
     * Get a token for tracking glucose changes
     */
    suspend fun getGlucoseChangesToken(): String {
        return withContext(Dispatchers.IO) {
            try {
                val client = healthConnectClient ?: throw Exception("Health Connect not available")

                // Request a token for tracking glucose changes
                val request = ChangesTokenRequest(
                    recordTypes = setOf(BloodGlucoseRecord::class)
                )

                client.getChangesToken(request)
            } catch (e: Exception) {
                throw RuntimeException("Failed to get changes token: ${e.message}")
            }
        }
    }

    /**
     * Upload a blood glucose reading
     */
    suspend fun uploadBloodGlucose(value: Double, time: Instant): String {
        try {
            val client = healthConnectClient ?: throw Exception("Health Connect not available")

            // Create a BloodGlucoseRecord using the helper
            val bloodGlucoseRecord = BloodGlucoseRecordHelper.createManualEntry(
                value = value,
                time = time
            )

            val response = client.insertRecords(listOf(bloodGlucoseRecord))
            return response.recordIdsList.firstOrNull() ?: "Unknown record ID"
        } catch (e: Exception) {
            Log.e(tag, "Error uploading glucose reading", e)
            throw e
        }
    }

    /**
     * Upload a series of blood glucose readings
     */
    suspend fun uploadGlucoseSeries(
        values: List<Double>,
        startTime: ZonedDateTime,
        endTime: ZonedDateTime,
        mealTypes: List<Int> = emptyList(),
        progressCallback: ((Int, Int) -> Unit)? = null
    ): Boolean {
        try {
            val client = healthConnectClient ?: throw Exception("Health Connect not available")

            // Verify permissions first
            if (!hasPermissions()) {
                Log.e(tag, "Missing required permissions")
                return false
            }

            // Use the helper to create all records with proper metadata
            val records = BloodGlucoseRecordHelper.createSeriesFromCsvData(
                values = values,
                startTime = startTime,
                endTime = endTime,
                mealTypes = mealTypes
            )

            val totalReadings = records.size

            // Insert records in batches to avoid overwhelming Health Connect
            val batchSize = 50
            for (i in records.indices step batchSize) {
                val batch = records.subList(i, minOf(i + batchSize, records.size))
                val response = client.insertRecords(batch)
                Log.d(tag, "Batch ${i/batchSize + 1}: Inserted ${response.recordIdsList.size} records")

                // Report progress after each batch
                progressCallback?.invoke(minOf(i + batchSize, records.size), totalReadings)
            }

            Log.d(tag, "Uploaded ${records.size} glucose readings")
            return true
        } catch (e: Exception) {
            Log.e(tag, "Error uploading glucose series: ${e.message}", e)
            throw e
        }
    }

    /**
     * Read recent blood glucose records
     */
    suspend fun readBloodGlucoseRecords(startTime: Instant, endTime: Instant): List<BloodGlucoseRecord> {
        try {
            val client = healthConnectClient ?: throw Exception("Health Connect not available")

            val request = ReadRecordsRequest(
                recordType = BloodGlucoseRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )

            val response = client.readRecords(request)
            return response.records
        } catch (e: Exception) {
            Log.e(tag, "Error reading glucose records", e)
            throw e
        }
    }

    /**
     * Read historical blood glucose records
     */
    suspend fun readHistoricalBloodGlucoseRecords(startTime: Instant, endTime: Instant): List<BloodGlucoseRecord> {
        return readBloodGlucoseRecords(startTime, endTime)
    }

    /**
     * Read the latest blood glucose record
     */
    suspend fun readLatestBloodGlucoseRecord(): BloodGlucoseRecord? {
        try {
            val endTime = Instant.now()
            val startTime = endTime.minus(30, ChronoUnit.DAYS)

            val records = readBloodGlucoseRecords(startTime, endTime)
            return records.maxByOrNull { it.time }
        } catch (e: Exception) {
            Log.e(tag, "Error reading latest glucose record", e)
            return null
        }
    }

    /**
     * Open Health Connect app
     */
    suspend fun openHealthConnectApp(activityContext: Context = context) {
        try {
            val intent = if (isHealthConnectAvailable()) {
                // Use the newer intent action for Android 14+
                Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS")
            } else {
                // Use the Play Store intent for installation
                Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("market://details?id=com.google.android.apps.healthdata")
                }
            }

            // Add flag if needed
            if (activityContext == context) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            activityContext.startActivity(intent)
        } catch (e: Exception) {
            Log.e(tag, "Error opening Health Connect app", e)
            openHealthConnectInPlayStore()
        }
    }

    /**
     * Open Health Connect in Play Store
     */
    private fun openHealthConnectInPlayStore() {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("market://details?id=com.google.android.apps.healthdata")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(tag, "Error opening Play Store", e)
        }
    }

    /**
     * Get changes for glucose data since the given token
     */
    fun getGlucoseChanges(token: String): Flow<ChangesMessage> = flow {
        try {
            val client = healthConnectClient ?: throw Exception("Health Connect not available")

            val changes = client.getChanges(token)

            if (changes.changes.isNotEmpty()) {
                emit(ChangesMessage.ChangeList(changes.changes))
            }

            emit(ChangesMessage.NoMoreChanges(changes.nextChangesToken))
        } catch (e: Exception) {
            Log.e(tag, "Error getting changes: ${e.message}", e)
            throw e
        }
    }

    /**
     * Process glucose changes into readable messages
     */
    fun processGlucoseChanges(changes: List<Change>): List<String> {
        val messages = mutableListOf<String>()

        for (change in changes) {
            when (change) {
                is Change.Insertion -> {
                    messages.add("New glucose reading inserted at ${formatTimestamp(change.metadata.clientRecordId ?: "unknown")}")
                }
                is Change.Deletion -> {
                    messages.add("Glucose reading deleted: ${change.deletedUuid}")
                }
                is Change.Update -> {
                    messages.add("Glucose reading updated: ${change.metadata.clientRecordId ?: "unknown"}")
                }
                else -> {
                    messages.add("Unknown change type: ${change::class.java.simpleName}")
                }
            }
        }

        return if (messages.isEmpty()) {
            listOf("No changes detected in Health Connect data")
        } else {
            messages
        }
    }

    /**
     * Get glucose statistics for different periods
     */
    suspend fun getAllGlucoseStatistics(): List<GlucoseStatistics> {
        try {
            val endTime = Instant.now()

            // Last 24 hours
            val day1Stats = getDayGlucoseStatistics(
                LocalDate.now(ZoneId.systemDefault())
            )

            // Last 7 days
            val week1Start = endTime.minus(7, ChronoUnit.DAYS)
            val weekRecords = readBloodGlucoseRecords(week1Start, endTime)
            val week1Stats = GlucoseStatistics(
                averageGlucose = weekRecords.map { it.level.inMilligramsPerDeciliter }.average().takeIf { !it.isNaN() },
                minimumGlucose = weekRecords.minOfOrNull { it.level.inMilligramsPerDeciliter },
                maximumGlucose = weekRecords.maxOfOrNull { it.level.inMilligramsPerDeciliter },
                readingCount = weekRecords.size.toLong(),
                period = "Last 7 Days"
            )

            // Last 30 days
            val month1Start = endTime.minus(30, ChronoUnit.DAYS)
            val monthRecords = readBloodGlucoseRecords(month1Start, endTime)
            val month1Stats = GlucoseStatistics(
                averageGlucose = monthRecords.map { it.level.inMilligramsPerDeciliter }.average().takeIf { !it.isNaN() },
                minimumGlucose = monthRecords.minOfOrNull { it.level.inMilligramsPerDeciliter },
                maximumGlucose = monthRecords.maxOfOrNull { it.level.inMilligramsPerDeciliter },
                readingCount = monthRecords.size.toLong(),
                period = "Last 30 Days"
            )

            return listOf(day1Stats, week1Stats, month1Stats)
        } catch (e: Exception) {
            Log.e(tag, "Error getting glucose statistics: ${e.message}", e)
            throw e
        }
    }

    /**
     * Get glucose statistics for a specific day
     */
    suspend fun getDayGlucoseStatistics(date: LocalDate): GlucoseStatistics {
        try {
            val zoneId = ZoneId.systemDefault()
            val startTime = date.atStartOfDay(zoneId).toInstant()
            val endTime = date.plusDays(1).atStartOfDay(zoneId).toInstant()

            val records = readBloodGlucoseRecords(startTime, endTime)

            return GlucoseStatistics(
                averageGlucose = records.map { it.level.inMilligramsPerDeciliter }.average().takeIf { !it.isNaN() },
                minimumGlucose = records.minOfOrNull { it.level.inMilligramsPerDeciliter },
                maximumGlucose = records.maxOfOrNull { it.level.inMilligramsPerDeciliter },
                readingCount = records.size.toLong(),
                period = date.format(java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy"))
            )
        } catch (e: Exception) {
            Log.e(tag, "Error getting day statistics: ${e.message}", e)
            return GlucoseStatistics(
                averageGlucose = null,
                minimumGlucose = null,
                maximumGlucose = null,
                readingCount = 0L,
                period = date.format(java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy"))
            )
        }
    }

    /**
     * Revoke all Health Connect permissions
     */
    suspend fun revokeAllPermissions() {
        permissionsHandler.revokeAllPermissions()
    }

    /**
     * Format a timestamp as a readable string
     */
    private fun formatTimestamp(timestamp: String): String {
        return try {
            // Try to parse as ISO timestamp if possible
            Instant.parse(timestamp).atZone(ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        } catch (e: Exception) {
            timestamp
        }
    }
    }
}