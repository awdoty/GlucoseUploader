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
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
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
import java.util.UUID

class HealthConnectUploader(val context: Context) {

    private val tag = "HealthConnectUploader"

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
        return withContext(Dispatchers.IO) {
            try {
                HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
            } catch (e: Exception) {
                Log.e(tag, "Error checking Health Connect availability", e)
                false
            }
        }
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
        return try {
            val client = healthConnectClient ?: return false
            val grantedPermissions = client.permissionController.getGrantedPermissions()
            grantedPermissions.containsAll(getRequiredPermissions())
        } catch (e: Exception) {
            Log.e(tag, "Error checking permissions", e)
            false
        }
    }

    /**
     * Get required permissions for blood glucose data
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
        return try {
            val client = healthConnectClient ?: return false
            val grantedPermissions = client.permissionController.getGrantedPermissions()
            grantedPermissions.any { it.toString().contains("READ_HEALTH_DATA_HISTORY") }
        } catch (e: Exception) {
            Log.e(tag, "Error checking history read permission", e)
            false
        }
    }

    /**
     * Check if background read is available
     */
    fun isBackgroundReadAvailable(): Boolean {
        return true  // Simplified for Android 14+
    }

    /**
     * Check if we have background read permission
     */
    suspend fun hasBackgroundReadPermission(): Boolean {
        return try {
            val client = healthConnectClient ?: return false
            val grantedPermissions = client.permissionController.getGrantedPermissions()
            grantedPermissions.any { it.toString().contains("READ_HEALTH_DATA_IN_BACKGROUND") }
        } catch (e: Exception) {
            Log.e(tag, "Error checking background read permission", e)
            false
        }
    }

    /**
     * Request history read permission
     */
    fun requestHistoryReadPermission(activity: ComponentActivity) {
        try {
            val intent = Intent("androidx.health.ACTION_HEALTH_CONNECT_PERMISSIONS")
            val historyReadPermission = "android.permission.health.READ_HEALTH_DATA_HISTORY"
            intent.putExtra("androidx.health.EXTRA_PERMISSIONS", arrayOf(historyReadPermission))
            activity.startActivity(intent)
        } catch (e: Exception) {
            Log.e(tag, "Error requesting history read permission", e)
            openHealthConnectApp(activity)
        }
    }

    /**
     * Log various information about Health Connect for debugging
     */
    suspend fun logHealthConnectInfo() {
        try {
            Log.d(tag, "Health Connect availability: ${isHealthConnectAvailable()}")
            Log.d(tag, "Health Connect version: ${getHealthConnectVersion()}")
            if (isHealthConnectAvailable()) {
                Log.d(tag, "Has permissions: ${hasPermissions()}")
                Log.d(tag, "Has history read permission: ${hasHistoryReadPermission()}")
                Log.d(tag, "Has background read permission: ${hasBackgroundReadPermission()}")
            }
        } catch (e: Exception) {
            Log.e(tag, "Error logging Health Connect info", e)
        }
    }

    /**
     * Upload a blood glucose reading
     */
    suspend fun uploadBloodGlucose(
        value: Double,
        time: Instant,
        relationToMeal: Int = BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN
    ): String {
        try {
            val client = healthConnectClient ?: throw Exception("Health Connect not available")

            // Create a device info for manual entry
            val device = Device(
                manufacturer = android.os.Build.MANUFACTURER,
                model = android.os.Build.MODEL,
                type = Device.TYPE_PHONE
            )

            // Create metadata using builder pattern for Android 14+ compatibility
            val metadata = Metadata.Builder()
                .setDevice(device)
                .setRecordingMethod(Metadata.RECORDING_METHOD_MANUALLY_ENTERED)
                .build()

            // Create the record with the specified value and time
            val bloodGlucoseRecord = BloodGlucoseRecord(
                metadata = metadata,
                time = time,
                zoneOffset = ZoneId.systemDefault().rules.getOffset(time),
                level = BloodGlucose.milligramsPerDeciliter(value),
                relationToMeal = relationToMeal,
                specimenSource = BloodGlucoseRecord.SPECIMEN_SOURCE_CAPILLARY_BLOOD
            )

            // Insert the record
            val response = client.insertRecords(listOf(bloodGlucoseRecord))
            return response.recordIdsList.firstOrNull() ?: "Unknown record ID"
        } catch (e: Exception) {
            Log.e(tag, "Error uploading glucose reading", e)
            throw e
        }
    }

    /**
     * Upload a series of blood glucose readings, such as from CSV data
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

            if (!hasPermissions()) {
                Log.e(tag, "Missing required permissions")
                return false
            }

            // Create a list of records to insert
            val records = mutableListOf<BloodGlucoseRecord>()

            // Calculate time interval between readings
            val totalReadings = values.size
            val durationMillis = endTime.toInstant().toEpochMilli() - startTime.toInstant().toEpochMilli()
            val intervalMillis = if (totalReadings > 1) durationMillis / (totalReadings - 1) else 0

            // Create device info for manual entry
            val device = Device(
                manufacturer = android.os.Build.MANUFACTURER,
                model = android.os.Build.MODEL,
                type = Device.TYPE_PHONE
            )

            // Create metadata using builder pattern for Android 14+ compatibility
            val metadata = Metadata.Builder()
                .setDevice(device)
                .setRecordingMethod(Metadata.RECORDING_METHOD_MANUALLY_ENTERED)
                .build()

            // Create records for each value
            for (i in values.indices) {
                // Calculate timestamp for this reading
                val timestamp = if (totalReadings > 1 && i > 0) {
                    Instant.ofEpochMilli(startTime.toInstant().toEpochMilli() + (i * intervalMillis))
                } else {
                    startTime.toInstant()
                }

                // Get meal type if available, otherwise use unknown
                val mealType = if (i < mealTypes.size) mealTypes[i]
                else BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN

                // Create the record
                val record = BloodGlucoseRecord(
                    metadata = metadata,
                    time = timestamp,
                    zoneOffset = ZoneId.systemDefault().rules.getOffset(timestamp),
                    level = BloodGlucose.milligramsPerDeciliter(values[i]),
                    relationToMeal = mealType,
                    specimenSource = BloodGlucoseRecord.SPECIMEN_SOURCE_CAPILLARY_BLOOD
                )
                records.add(record)
            }

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
            Log.e(tag, "Error uploading glucose series", e)
            throw e
        }
    }

    /**
     * Read blood glucose records
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
     * Get statistics for glucose readings for the specified day
     */
    suspend fun getDayGlucoseStatistics(date: LocalDate): GlucoseStatistics {
        try {
            val zoneId = ZoneId.systemDefault()
            val startTime = date.atStartOfDay(zoneId).toInstant()
            val endTime = date.plusDays(1).atStartOfDay(zoneId).toInstant()

            val records = readBloodGlucoseRecords(startTime, endTime)
            val values = records.map { it.level.inMilligramsPerDeciliter }

            return GlucoseStatistics(
                averageGlucose = values.average().takeIf { !it.isNaN() },
                minimumGlucose = values.minOrNull(),
                maximumGlucose = values.maxOrNull(),
                readingCount = records.size.toLong(),
                period = date.format(java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy"))
            )
        } catch (e: Exception) {
            Log.e(tag, "Error getting day statistics", e)
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
            val weekValues = weekRecords.map { it.level.inMilligramsPerDeciliter }
            val week1Stats = GlucoseStatistics(
                averageGlucose = weekValues.average().takeIf { !it.isNaN() },
                minimumGlucose = weekValues.minOrNull(),
                maximumGlucose = weekValues.maxOrNull(),
                readingCount = weekRecords.size.toLong(),
                period = "Last 7 Days"
            )

            // Last 30 days
            val month1Start = endTime.minus(30, ChronoUnit.DAYS)
            val monthRecords = readBloodGlucoseRecords(month1Start, endTime)
            val monthValues = monthRecords.map { it.level.inMilligramsPerDeciliter }
            val month1Stats = GlucoseStatistics(
                averageGlucose = monthValues.average().takeIf { !it.isNaN() },
                minimumGlucose = monthValues.minOrNull(),
                maximumGlucose = monthValues.maxOrNull(),
                readingCount = monthRecords.size.toLong(),
                period = "Last 30 Days"
            )

            return listOf(day1Stats, week1Stats, month1Stats)
        } catch (e: Exception) {
            Log.e(tag, "Error getting glucose statistics", e)
            throw e
        }
    }

    /**
     * Get a token for tracking changes to blood glucose data
     */
    suspend fun getGlucoseChangesToken(): String {
        return try {
            val client = healthConnectClient ?: throw Exception("Health Connect not available")

            // Request a token for tracking glucose changes
            val request = ChangesTokenRequest(
                recordTypes = setOf(BloodGlucoseRecord::class)
            )

            client.getChangesToken(request)
        } catch (e: Exception) {
            Log.e(tag, "Failed to get changes token", e)
            throw e
        }
    }

    /**
     * Get changes for glucose data since the given token
     */
    fun getGlucoseChanges(token: String): Flow<ChangesMessage> = flow {
        try {
            val client = healthConnectClient ?: throw Exception("Health Connect not available")

            var nextChangesToken = token
            do {
                val response = client.getChanges(nextChangesToken)
                if (response.changesTokenExpired) {
                    throw Exception("Changes token has expired")
                }
                emit(ChangesMessage.ChangeList(response.changes))
                nextChangesToken = response.nextChangesToken
            } while (response.hasMore)
            emit(ChangesMessage.NoMoreChanges(nextChangesToken))
        } catch (e: Exception) {
            Log.e(tag, "Error getting changes", e)
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
            listOf("No changes detected in glucose data")
        } else {
            messages
        }
    }

    /**
     * Open Health Connect app or send to Play Store if not installed
     */
    suspend fun openHealthConnectApp(activityContext: Context = context) {
        try {
            val isAvailable = isHealthConnectAvailable()
            val intent = if (isAvailable) {
                // Use the newer intent action for Android 14+
                Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS")
            } else {
                // Use the Play Store intent for installation
                Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("market://details?id=com.google.android.apps.healthdata")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }

            activityContext.startActivity(intent)
        } catch (e: Exception) {
            Log.e(tag, "Error opening Health Connect", e)
            try {
                val playStoreIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(playStoreIntent)
            } catch (e2: Exception) {
                Log.e(tag, "Failed to open Play Store", e2)
            }
        }
    }

    /**
     * Revoke all Health Connect permissions
     */
    suspend fun revokeAllPermissions() {
        try {
            healthConnectClient?.permissionController?.revokeAllPermissions()
        } catch (e: Exception) {
            Log.e(tag, "Error revoking all permissions", e)
        }
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

    /**
     * Message types for changes API
     */
    sealed class ChangesMessage {
        data class ChangeList(val changes: List<Change>) : ChangesMessage()
        data class NoMoreChanges(val nextChangesToken: String) : ChangesMessage()
    }
}