package com.example.glucoseuploader

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Handles interactions with Health Connect API
 * Supports uploading glucose readings and reading historical data
 */
class HealthConnectUploader(val context: Context) {
    private val tag = "HealthConnectUploader"

    // Get Health Connect client
    val healthConnectClient by lazy {
        HealthConnectClient.getOrCreate(context)
    }

    // Permissions required for the app
    private val permissions = setOf(
        HealthPermission.getReadPermission(BloodGlucoseRecord::class),
        HealthPermission.getWritePermission(BloodGlucoseRecord::class)
    )

    /**
     * Check if Health Connect is available on this device
     */
    suspend fun isHealthConnectAvailable(): Boolean {
        val sdkStatus = HealthConnectClient.getSdkStatus(context)
        Log.d(tag, "Health Connect SDK status: $sdkStatus")
        return sdkStatus == HealthConnectClient.SDK_AVAILABLE
    }

    /**
     * Get required permissions
     */
    fun getRequiredPermissions(): Set<String> {
        return permissions
    }

    /**
     * Check if required permissions are granted
     */
    suspend fun hasPermissions(): Boolean {
        return try {
            val grantedPermissions = healthConnectClient.permissionController.getGrantedPermissions()
            permissions.all { it in grantedPermissions }
        } catch (e: Exception) {
            Log.e(tag, "Error checking permissions: ${e.message}")
            false
        }
    }

    /**
     * Request permissions for a given activity
     */
    fun requestPermissions(activity: ComponentActivity) {
        val contract = PermissionController.createRequestPermissionResultContract()
        val intent = contract.createIntent(context, permissions)
        activity.startActivity(intent)
    }

    /**
     * Open Health Connect app or Play Store
     */
    suspend fun openHealthConnectApp(activity: Context = context) {
        try {
            // Try direct Health Connect deeplink first (Android 14+)
            val intent = Intent("android.health.connect.action.HEALTH_HOME_SETTINGS")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.d(tag, "Failed to open Health Connect directly, trying Play Store")

            // Fall back to Play Store if direct link fails
            val playStoreIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(playStoreIntent)
        }
    }

    /**
     * Upload a blood glucose record to Health Connect
     */
    suspend fun uploadBloodGlucose(
        value: Double,
        time: Instant = Instant.now(),
        mealType: Int = BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN
    ): String {
        try {
            // Create metadata
            val metadata = Metadata(
                DataOrigin(context.packageName),
                "manually_entered",
                clientRecordId = UUID.randomUUID().toString()
            )

            // Create record
            val record = BloodGlucoseRecord(
                time = time,
                zoneOffset = ZoneOffset.systemDefault().rules.getOffset(time),
                level = value,
                specimenSource = BloodGlucoseRecord.SPECIMEN_SOURCE_CAPILLARY_BLOOD,
                mealType = mealType,
                relationToMeal = mealType,
                metadata = metadata
            )

            // Insert record
            val response = healthConnectClient.insertRecords(listOf(record))
            return response.recordIdsList.firstOrNull() ?: ""
        } catch (e: Exception) {
            Log.e(tag, "Error uploading glucose: ${e.message}")
            throw e
        }
    }

    /**
     * Upload multiple glucose records from a list of values
     */
    suspend fun uploadGlucoseSeries(
        values: List<Double>,
        startTime: ZonedDateTime,
        endTime: ZonedDateTime
    ): List<String> {
        try {
            // Calculate time interval between readings
            val duration = java.time.Duration.between(startTime, endTime)
            val intervalMillis = duration.toMillis() / (values.size - 1).coerceAtLeast(1)

            // Create records
            val records = values.mapIndexed { index, value ->
                val recordTime = startTime.toInstant().plusMillis(intervalMillis * index)

                BloodGlucoseRecord(
                    time = recordTime,
                    zoneOffset = ZoneOffset.systemDefault().rules.getOffset(recordTime),
                    level = value,
                    specimenSource = BloodGlucoseRecord.SPECIMEN_SOURCE_CAPILLARY_BLOOD,
                    mealType = BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN,
                    relationToMeal = BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN,
                    metadata = Metadata(
                        DataOrigin(context.packageName),
                        "manually_entered",
                        clientRecordId = UUID.randomUUID().toString()
                    )
                )
            }

            // Insert records
            val response = healthConnectClient.insertRecords(records)
            return response.recordIdsList
        } catch (e: Exception) {
            Log.e(tag, "Error uploading glucose series: ${e.message}")
            throw e
        }
    }

    /**
     * Read blood glucose records for a given time range
     */
    suspend fun readBloodGlucoseRecords(
        startTime: Instant,
        endTime: Instant
    ): List<BloodGlucoseRecord> {
        try {
            val request = ReadRecordsRequest(
                recordType = BloodGlucoseRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )

            val response = healthConnectClient.readRecords(request)
            return response.records
        } catch (e: Exception) {
            Log.e(tag, "Error reading glucose records: ${e.message}")
            throw e
        }
    }

    /**
     * Read the latest blood glucose record
     */
    suspend fun readLatestBloodGlucoseRecord(): BloodGlucoseRecord? {
        try {
            // Read records from the last 7 days
            val endTime = Instant.now()
            val startTime = endTime.minus(7, ChronoUnit.DAYS)

            val records = readBloodGlucoseRecords(startTime, endTime)

            // Return the most recent record
            return records.maxByOrNull { it.time }
        } catch (e: Exception) {
            Log.e(tag, "Error reading latest glucose record: ${e.message}")
            return null
        }
    }

    /**
     * Read historical blood glucose records for a given time range
     */
    suspend fun readHistoricalBloodGlucoseRecords(
        startTime: Instant,
        endTime: Instant
    ): List<BloodGlucoseRecord> {
        return readBloodGlucoseRecords(startTime, endTime)
    }

    /**
     * Check if background read permission is available
     */
    suspend fun isBackgroundReadAvailable(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Background read requires Android 13+
            true
        } else {
            false
        }
    }

    /**
     * Check if background read permission is granted
     */
    suspend fun hasBackgroundReadPermission(): Boolean {
        if (!isBackgroundReadAvailable()) return false

        return try {
            val permissionString = "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"
            val grantedPermissions = healthConnectClient.permissionController.getGrantedPermissions()
            permissionString in grantedPermissions
        } catch (e: Exception) {
            Log.e(tag, "Error checking background permissions: ${e.message}")
            false
        }
    }

    /**
     * Request permission to access historical data
     */
    suspend fun hasHistoryReadPermission(): Boolean {
        return hasPermissions() // For now, this is the same as regular permissions
    }

    /**
     * Request permission to access historical data
     */
    fun requestHistoryReadPermission(activity: ComponentActivity) {
        requestPermissions(activity)
    }

    /**
     * Get all glucose statistics
     */
    suspend fun getAllGlucoseStatistics(): List<GlucoseStatistics> {
        try {
            // Get statistics for different time periods
            val now = Instant.now()

            val todayStart = ZonedDateTime.now().truncatedTo(ChronoUnit.DAYS).toInstant()
            val yesterdayStart = todayStart.minus(1, ChronoUnit.DAYS)
            val weekStart = todayStart.minus(7, ChronoUnit.DAYS)
            val monthStart = todayStart.minus(30, ChronoUnit.DAYS)

            // Read records for each period
            val todayRecords = readBloodGlucoseRecords(todayStart, now)
            val yesterdayRecords = readBloodGlucoseRecords(yesterdayStart, todayStart)
            val weekRecords = readBloodGlucoseRecords(weekStart, now)
            val monthRecords = readBloodGlucoseRecords(monthStart, now)

            // Calculate statistics
            val stats = mutableListOf<GlucoseStatistics>()

            if (todayRecords.isNotEmpty()) {
                stats.add(calculateStatistics(todayRecords, "Today"))
            }

            if (yesterdayRecords.isNotEmpty()) {
                stats.add(calculateStatistics(yesterdayRecords, "Yesterday"))
            }

            if (weekRecords.isNotEmpty()) {
                stats.add(calculateStatistics(weekRecords, "Last 7 Days"))
            }

            if (monthRecords.isNotEmpty()) {
                stats.add(calculateStatistics(monthRecords, "Last 30 Days"))
            }

            return stats
        } catch (e: Exception) {
            Log.e(tag, "Error getting glucose statistics: ${e.message}")
            return emptyList()
        }
    }

    /**
     * Get glucose statistics for a specific day
     */
    suspend fun getDayGlucoseStatistics(date: java.time.LocalDate): GlucoseStatistics? {
        try {
            val startTime = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
            val endTime = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()

            val records = readBloodGlucoseRecords(startTime, endTime)
            if (records.isEmpty()) {
                return GlucoseStatistics(
                    averageGlucose = null,
                    minimumGlucose = null,
                    maximumGlucose = null,
                    readingCount = 0,
                    period = date.toString()
                )
            }

            return calculateStatistics(records, date.toString())
        } catch (e: Exception) {
            Log.e(tag, "Error getting day statistics: ${e.message}")
            return null
        }
    }

    /**
     * Calculate statistics for a list of glucose records
     */
    private fun calculateStatistics(records: List<BloodGlucoseRecord>, period: String): GlucoseStatistics {
        val glucoseValues = records.map { it.level.inMilligramsPerDeciliter }

        return GlucoseStatistics(
            averageGlucose = glucoseValues.average(),
            minimumGlucose = glucoseValues.minOrNull(),
            maximumGlucose = glucoseValues.maxOrNull(),
            readingCount = records.size.toLong(),
            period = period
        )
    }

    /**
     * Revoke all Health Connect permissions
     */
    suspend fun revokeAllPermissions() {
        try {
            healthConnectClient.permissionController.revokeAllPermissions()
        } catch (e: Exception) {
            Log.e(tag, "Error revoking permissions: ${e.message}")
        }
    }

    /**
     * Get Health Connect version
     */
    suspend fun getHealthConnectVersion(): String? {
        return try {
            val packageManager = context.packageManager
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo("com.google.android.apps.healthdata", 0)
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo("com.google.android.apps.healthdata", 0)
            }

            return packageInfo.versionName
        } catch (e: Exception) {
            Log.d(tag, "Health Connect package not found: ${e.message}")
            null
        }
    }

    /**
     * Log Health Connect information for debugging
     */
    suspend fun logHealthConnectInfo() {
        try {
            val sdkStatus = HealthConnectClient.getSdkStatus(context)
            Log.d(tag, "Health Connect SDK status: $sdkStatus")

            if (sdkStatus == HealthConnectClient.SDK_AVAILABLE) {
                val hasPermissions = hasPermissions()
                Log.d(tag, "Has permissions: $hasPermissions")

                val version = getHealthConnectVersion()
                Log.d(tag, "Health Connect version: $version")
            }
        } catch (e: Exception) {
            Log.e(tag, "Error logging Health Connect info: ${e.message}")
        }
    }
}