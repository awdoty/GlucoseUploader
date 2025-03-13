package com.example.glucoseuploader

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.changes.Change
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.BloodGlucose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Main class for interacting with Health Connect
 */
class HealthConnectUploader(val context: Context) {

    companion object {
        private const val TAG = "HealthConnectUploader"

        // Define permissions
        private val PERMISSIONS = setOf(
            HealthPermission.getReadPermission(BloodGlucoseRecord::class),
            HealthPermission.getWritePermission(BloodGlucoseRecord::class)
            // Note: Background and history permissions are only available in newer Health Connect versions
            // If you need these, check the Health Connect version first
        )
    }

    // Lazy initialization of Health Connect client
    val healthConnectClient by lazy {
        try {
            HealthConnectClient.getOrCreate(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating Health Connect client: ${e.message}")
            null
        }
    }

    /**
     * Check if Health Connect is available on the device
     */
    suspend fun isHealthConnectAvailable(): Boolean {
        return try {
            val available = HealthConnectClient.isProviderAvailable(context)
            Log.d(TAG, "Health Connect available: $available")
            available
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Health Connect availability: ${e.message}")
            false
        }
    }

    /**
     * Get Health Connect version (if available)
     */
    suspend fun getHealthConnectVersion(): String? {
        return try {
            if (!isHealthConnectAvailable()) return null
            val client = healthConnectClient ?: return null

            // This is a placeholder - the actual way to get version depends on the Health Connect API
            "1.0.0" // Replace with actual version detection
        } catch (e: Exception) {
            Log.e(TAG, "Error getting Health Connect version: ${e.message}")
            null
        }
    }

    /**
     * Log detailed Health Connect information for debugging
     */
    suspend fun logHealthConnectInfo() {
        try {
            val available = isHealthConnectAvailable()
            Log.d(TAG, "Health Connect available: $available")

            if (available) {
                val permissions = getGrantedPermissions()
                Log.d(TAG, "Granted permissions: $permissions")

                // Output availability of specific permissions
                Log.d(TAG, "Read blood glucose: ${hasSpecificPermission(
                    HealthPermission.getReadPermission(BloodGlucoseRecord::class)
                )}")
                Log.d(TAG, "Write blood glucose: ${hasSpecificPermission(
                    HealthPermission.getWritePermission(BloodGlucoseRecord::class)
                )}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error logging Health Connect info: ${e.message}")
        }
    }

    /**
     * Check if the app has all the required permissions
     */
    suspend fun hasPermissions(): Boolean {
        return try {
            val client = healthConnectClient ?: return false
            val grantedPermissions = client.permissionController.getGrantedPermissions()

            // Check if all required permissions are granted
            PERMISSIONS.all { it in grantedPermissions }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking permissions: ${e.message}")
            false
        }
    }

    /**
     * Get the current set of granted Health Connect permissions
     */
    suspend fun getGrantedPermissions(): Set<String> {
        return try {
            val client = healthConnectClient ?: return emptySet()
            client.permissionController.getGrantedPermissions()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting granted permissions: ${e.message}")
            emptySet()
        }
    }

    /**
     * Get the list of required permissions
     */
    fun getRequiredPermissions(): Array<String> {
        return PERMISSIONS.map { it.toString() }.toTypedArray()
    }

    /**
     * Revoke all Health Connect permissions for this app
     */
    suspend fun revokeAllPermissions() {
        try {
            val client = healthConnectClient ?: return
            client.permissionController.revokeAllPermissions()
            Log.d(TAG, "All Health Connect permissions revoked")
        } catch (e: Exception) {
            Log.e(TAG, "Error revoking permissions: ${e.message}")
        }
    }

    /**
     * Check specific permission status
     */
    suspend fun hasSpecificPermission(permission: String): Boolean {
        return try {
            val client = healthConnectClient ?: return false
            val grantedPermissions = client.permissionController.getGrantedPermissions()
            permission in grantedPermissions
        } catch (e: Exception) {
            Log.e(TAG, "Error checking specific permission: ${e.message}")
            false
        }
    }

    /**
     * Check if the app has permission to read data in the background
     */
    suspend fun hasBackgroundReadPermission(): Boolean {
        // In newer Health Connect versions, you would check for a background permission
        // For compatibility, we'll just return a placeholder value
        return false
    }

    /**
     * Check if the app has permission to read historical data
     */
    suspend fun hasHistoryReadPermission(): Boolean {
        // In newer Health Connect versions, you would check for a history permission
        // For compatibility, we'll just return a placeholder value
        return false
    }

    /**
     * Request background read permission
     */
    fun requestBackgroundReadPermission(activity: ComponentActivity) {
        try {
            // This is a placeholder for requesting background permission
            // The actual implementation depends on the Health Connect version
            requestPermissions(activity)
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting background read permission: ${e.message}")
        }
    }

    /**
     * Request historical data read permission
     */
    fun requestHistoryReadPermission(activity: ComponentActivity) {
        try {
            // This is a placeholder for requesting history permission
            // The actual implementation depends on the Health Connect version
            requestPermissions(activity)
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting history read permission: ${e.message}")
        }
    }

    /**
     * Request permissions with activity result API
     */
    fun requestPermissions(activity: ComponentActivity) {
        try {
            val client = healthConnectClient ?: return
            val contract = ActivityResultContracts.RequestMultiplePermissions()

            val launcher = activity.registerForActivityResult(contract) { permissions ->
                val allGranted = permissions.all { it.value }
                Log.d(TAG, "Permissions granted: $allGranted")
            }

            launcher.launch(getRequiredPermissions())
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting permissions: ${e.message}")
        }
    }

    /**
     * Open the Health Connect app
     */
    suspend fun openHealthConnectApp(activity: Context = context) {
        try {
            val intent = Intent("android.health.connect.action.HEALTH_CONNECT_SETTINGS")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            activity.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening Health Connect app: ${e.message}")

            // Try alternate intent
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("market://details?id=com.google.android.apps.healthdata")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                activity.startActivity(intent)
            } catch (e2: Exception) {
                Log.e(TAG, "Error opening Play Store for Health Connect: ${e2.message}")

                // Last resort - open Play Store website
                try {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    activity.startActivity(intent)
                } catch (e3: Exception) {
                    Log.e(TAG, "Error opening Play Store website: ${e3.message}")
                }
            }
        }
    }

    /**
     * Upload a blood glucose reading to Health Connect
     */
    suspend fun uploadBloodGlucose(glucoseValue: Double, timestamp: Instant) {
        try {
            val client = healthConnectClient ?: throw Exception("Health Connect not available")

            val bloodGlucoseRecord = BloodGlucoseRecord(
                time = timestamp,
                level = BloodGlucose.milligramsPerDeciliter(glucoseValue),
                relationToMeal = BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN,
                specimenSource = BloodGlucoseRecord.SPECIMEN_SOURCE_CAPILLARY_BLOOD,
                zoneOffset = ZoneId.systemDefault().rules.getOffset(timestamp)
            )

            val records = listOf(bloodGlucoseRecord)
            val response = client.insertRecords(records)

            Log.d(TAG, "Uploaded glucose reading: $glucoseValue mg/dL at $timestamp")
            Log.d(TAG, "Upserted records: ${response.recordIdsList}")
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading glucose reading: ${e.message}")
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
    ) {
        try {
            val client = healthConnectClient ?: throw Exception("Health Connect not available")

            val totalReadings = values.size
            val records = mutableListOf<BloodGlucoseRecord>()

            // Calculate time interval between readings
            val durationMillis = endTime.toInstant().toEpochMilli() - startTime.toInstant().toEpochMilli()
            val intervalMillis = if (totalReadings > 1) durationMillis / (totalReadings - 1) else 0

            for (i in values.indices) {
                // Calculate timestamp for this reading
                val timestamp = if (totalReadings > 1 && i > 0) {
                    Instant.ofEpochMilli(startTime.toInstant().toEpochMilli() + (i * intervalMillis))
                } else {
                    startTime.toInstant()
                }

                // Get meal type if available, otherwise use unknown
                val mealType = if (i < mealTypes.size) mealTypes[i] else BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN

                val bloodGlucoseRecord = BloodGlucoseRecord(
                    time = timestamp,
                    level = BloodGlucose.milligramsPerDeciliter(values[i]),
                    relationToMeal = mealType,
                    specimenSource = BloodGlucoseRecord.SPECIMEN_SOURCE_CAPILLARY_BLOOD,
                    zoneOffset = ZoneId.systemDefault().rules.getOffset(timestamp)
                )

                records.add(bloodGlucoseRecord)

                // Report progress
                progressCallback?.invoke(i + 1, totalReadings)
            }

            // Insert all records
            val response = client.insertRecords(records)

            Log.d(TAG, "Uploaded ${records.size} glucose readings")
            Log.d(TAG, "Upserted records count: ${response.recordIdsList.size}")

        } catch (e: Exception) {
            Log.e(TAG, "Error uploading glucose series: ${e.message}")
            throw e
        }
    }

    /**
     * Add glucose reading via intent (doesn't require permissions)
     */
    fun addGlucoseViaIntent(context: Context, glucoseValue: Double): Boolean {
        return try {
            val intent = Intent("android.health.connect.action.CREATE_RECORDS").apply {
                addCategory("android.health.connect.category.BLOOD_GLUCOSE")
                putExtra("android.health.connect.extra.VALUE", glucoseValue)
                putExtra("android.health.connect.extra.UNIT", "mg/dL")
            }

            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                Log.d(TAG, "Opened Health Connect for adding glucose: $glucoseValue mg/dL")
                true
            } else {
                Log.e(TAG, "No activity found to handle the intent")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adding glucose via intent: ${e.message}")
            false
        }
    }

    /**
     * Read blood glucose records from Health Connect
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
            Log.e(TAG, "Error reading blood glucose records: ${e.message}")
            throw e
        }
    }

    /**
     * Read historical blood glucose records from Health Connect
     */
    suspend fun readHistoricalBloodGlucoseRecords(startTime: Instant, endTime: Instant): List<BloodGlucoseRecord> {
        return readBloodGlucoseRecords(startTime, endTime) // Same implementation, different purpose
    }

    /**
     * Read the latest blood glucose record
     */
    suspend fun readLatestBloodGlucoseRecord(): BloodGlucoseRecord? {
        try {
            val client = healthConnectClient ?: throw Exception("Health Connect not available")

            // Read records from past 30 days
            val endTime = Instant.now()
            val startTime = endTime.minus(30, ChronoUnit.DAYS)

            val request = ReadRecordsRequest(
                recordType = BloodGlucoseRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )

            val response = client.readRecords(request)

            // Return the most recent record
            return response.records.maxByOrNull { it.time }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading latest blood glucose record: ${e.message}")
            return null
        }
    }

    /**
     * Get a token for tracking changes to glucose data
     */
    suspend fun getGlucoseChangesToken(): String {
        try {
            val client = healthConnectClient ?: throw Exception("Health Connect not available")

            val request = ChangesTokenRequest(
                recordTypes = setOf(BloodGlucoseRecord::class)
            )

            return client.getChangesToken(request)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting changes token: ${e.message}")
            throw e
        }
    }

    /**
     * Get changes since a token was issued
     */
    suspend fun getGlucoseChanges(token: String): Flow<ChangesMessage> = flow {
        try {
            val client = healthConnectClient ?: throw Exception("Health Connect not available")

            var nextChangesToken = token
            var changes: List<Change>

            do {
                val changesResponse = client.getChanges(nextChangesToken)
                changes = changesResponse.changes
                nextChangesToken = changesResponse.nextChangesToken

                if (changes.isNotEmpty()) {
                    emit(ChangesMessage.ChangeList(changes))
                }
            } while (changes.isNotEmpty())

            emit(ChangesMessage.NoMoreChanges(nextChangesToken))
        } catch (e: Exception) {
            Log.e(TAG, "Error getting changes: ${e.message}")
            throw e
        }
    }

    /**
     * Process glucose changes to get readable information
     */
    suspend fun processGlucoseChanges(changes: List<Change>): List<String> {
        val result = mutableListOf<String>()

        for (change in changes) {
            when (change) {
                is UpsertionChange -> {
                    val record = change.record
                    if (record is BloodGlucoseRecord) {
                        val time = record.time.atZone(ZoneId.systemDefault())
                        val value = record.level.inMilligramsPerDeciliter
                        result.add("Added/Updated: $value mg/dL at $time")
                    }
                }
                else -> {
                    result.add("Change: ${change.javaClass.simpleName} - ${change.uuid}")
                }
            }
        }

        return result
    }

    /**
     * Get all glucose statistics for different time periods
     */
    suspend fun getAllGlucoseStatistics(): List<GlucoseStatistics> {
        val now = Instant.now()
        val result = mutableListOf<GlucoseStatistics>()

        // Today
        result.add(getDayGlucoseStatistics(LocalDate.now()) ?:
        GlucoseStatistics(null, null, null, 0, "Today"))

        // Last 7 days
        val sevenDaysAgo = now.minus(7, ChronoUnit.DAYS)
        val weekStats = getGlucoseStatisticsForRange(sevenDaysAgo, now)
        result.add(weekStats ?: GlucoseStatistics(null, null, null, 0, "Last 7 Days"))

        // Last 30 days
        val thirtyDaysAgo = now.minus(30, ChronoUnit.DAYS)
        val monthStats = getGlucoseStatisticsForRange(thirtyDaysAgo, now)
        result.add(monthStats ?: GlucoseStatistics(null, null, null, 0, "Last 30 Days"))

        return result
    }

    /**
     * Get glucose statistics for a specific day
     */
    suspend fun getDayGlucoseStatistics(date: LocalDate): GlucoseStatistics? {
        // Convert local date to start and end time
        val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val endOfDay = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().minusMillis(1)

        return getGlucoseStatisticsForRange(startOfDay, endOfDay, date.toString())
    }

    /**
     * Get glucose statistics for a date range
     */
    private suspend fun getGlucoseStatisticsForRange(
        startTime: Instant,
        endTime: Instant,
        periodName: String = "Custom Range"
    ): GlucoseStatistics? {
        try {
            val records = readBloodGlucoseRecords(startTime, endTime)

            if (records.isEmpty()) {
                return GlucoseStatistics(null, null, null, 0, periodName)
            }

            val values = records.map { it.level.inMilligramsPerDeciliter }
            val average = values.average()
            val min = values.minOrNull()
            val max = values.maxOrNull()

            return GlucoseStatistics(average, min, max, records.size.toLong(), periodName)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting statistics for range: ${e.message}")
            return null
        }
    }

    /**
     * Check if background read is available
     */
    suspend fun isBackgroundReadAvailable(): Boolean {
        return true // Simplified implementation - always return true
    }
}