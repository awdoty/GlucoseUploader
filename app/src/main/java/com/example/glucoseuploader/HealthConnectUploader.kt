package com.example.glucoseuploader

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.health.connect.client.HealthConnectClient
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
    fun getRequiredPermissions(): Array<String> {
        return arrayOf(
            "android.permission.health.READ_BLOOD_GLUCOSE",
            "android.permission.health.WRITE_BLOOD_GLUCOSE"
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
        // This is a simplified check - in a real app, you'd check for Android 13+ and HC version
        return true
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
        // This uses the same function as normal reading, but could be enhanced
        // with specialized historical data handling
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
     * Log Health Connect info for debugging
     */
    suspend fun logHealthConnectInfo() {
        Log.d(tag, "Health Connect available: ${isHealthConnectAvailable()}")

        if (isHealthConnectAvailable()) {
            Log.d(tag, "Health Connect permissions: ${hasPermissions()}")
        }
    }

    /**
     * Open Health Connect app
     */
    fun openHealthConnectApp(activityContext: Context = context) {
        try {
            val intent = Intent("android.health.connect.action.HEALTH_CONNECT_SETTINGS")
            intent.addCategory(Intent.CATEGORY_DEFAULT)

            if (activityContext == context) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            activityContext.startActivity(intent)
        } catch (e: Exception) {
            Log.e(tag, "Error opening Health Connect app", e)
            // Try Play Store as fallback
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
     * Add a glucose reading via intent (doesn't require permissions)
     */
    fun addGlucoseViaIntent(context: Context, value: Double): Boolean {
        try {
            val intent = Intent("android.health.action.INSERT_RECORDS")
            intent.putExtra("android.health.extra.RECORD_TYPES", arrayOf("android.health.BloodGlucose"))
            intent.putExtra("android.health.extra.VALUE", value)
            intent.putExtra("android.health.extra.RELATION_TO_MEAL", BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN)
            intent.putExtra("android.health.extra.START_TIME", System.currentTimeMillis())
            intent.addCategory(Intent.CATEGORY_DEFAULT)

            context.startActivity(intent)
            return true
        } catch (e: Exception) {
            Log.e(tag, "No activity found to handle the intent", e)
            return false
        }
    }

    /**
     * Get changes for glucose data since the given token
     * This is a simplified implementation that returns a no-changes message
     */
    fun getGlucoseChanges(token: String): Flow<ChangesMessage> = flow {
        // Always return a no-changes message
        emit(ChangesMessage.NoMoreChanges("next_token_${System.currentTimeMillis()}"))
    }

    /**
     * Process glucose changes into readable messages
     * This is a simplified implementation that returns a generic message
     */
    fun processGlucoseChanges(changes: List<Any>): List<String> {
        // Return a generic message
        return listOf("Changes detected in Health Connect data")
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
            throw e
        }
    }

    /**
     * Revoke all Health Connect permissions for this app
     */
    suspend fun revokeAllPermissions() {
        permissionsHandler.revokeAllPermissions()
    }
}