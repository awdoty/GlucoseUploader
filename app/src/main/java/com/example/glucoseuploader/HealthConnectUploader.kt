package com.example.glucoseuploader

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.launch
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import androidx.health.connect.client.units.BloodGlucose

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
        return try {
            // Just try to create a client
            healthConnectClient != null
        } catch (e: Exception) {
            Log.e(tag, "Error checking Health Connect availability", e)
            false
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
    // In HealthConnectUploader.kt - Modify the hasPermissions() method
    suspend fun hasPermissions(): Boolean {
        try {
            val client = healthConnectClient ?: return false

            // Force refresh permissions cache before checking
            try {
                client.permissionController.revokeAllPermissions()
                client.permissionController.getGrantedPermissions()
            } catch (e: Exception) {
                // Ignore any errors from this attempt - it's just to refresh the cache
            }

            val granted = client.permissionController.getGrantedPermissions()

            val requiredPermissions = setOf(
                HealthPermission.getReadPermission(BloodGlucoseRecord::class),
                HealthPermission.getWritePermission(BloodGlucoseRecord::class)
            )

            val hasAll = granted.containsAll(requiredPermissions)
            Log.d(tag, "Read blood glucose: ${granted.contains(HealthPermission.getReadPermission(BloodGlucoseRecord::class))}")
            Log.d(tag, "Write blood glucose: ${granted.contains(HealthPermission.getWritePermission(BloodGlucoseRecord::class))}")

            return hasAll
        } catch (e: Exception) {
            Log.e(tag, "Error checking permissions", e)
            return false
        }
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
        try {
            val client = healthConnectClient ?: return false
            val granted = client.permissionController.getGrantedPermissions()

            return granted.contains("android.permission.health.READ_HEALTH_DATA_HISTORY")
        } catch (e: Exception) {
            Log.e(tag, "Error checking history permissions", e)
            return false
        }
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
        try {
            val client = healthConnectClient ?: return false
            val granted = client.permissionController.getGrantedPermissions()

            return granted.contains("android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND")
        } catch (e: Exception) {
            Log.e(tag, "Error checking background permissions", e)
            return false
        }
    }

    /**
     * Request history read permission
     */
    fun requestHistoryReadPermission(activity: ComponentActivity) {
        try {
            val intent = Intent("android.health.connect.action.REQUEST_PERMISSION")
            intent.putExtra(
                "android.health.connect.extra.REQUEST_PERMISSIONS",
                arrayOf("android.permission.health.READ_HEALTH_DATA_HISTORY")
            )
            activity.startActivity(intent)
        } catch (e: Exception) {
            Log.e(tag, "Error requesting history permission", e)
            openHealthConnectApp(activity)
        }
    }

    suspend fun getGlucoseChangesToken(): String {
        return withContext(Dispatchers.IO) {
            try {
                val healthConnectClient = HealthConnectClient.getOrCreate(context)

                // Request a token for tracking glucose changes
                val request = ChangesTokenRequest(
                    recordTypes = setOf(BloodGlucoseRecord::class)
                )

                healthConnectClient.getChangesToken(request)
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

            // Create a BloodGlucoseRecord
            val bloodGlucoseRecord = BloodGlucoseRecord(
                time = time,
                zoneOffset = null,
                relationToMeal = BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN,
                level = androidx.health.connect.client.units.BloodGlucose.milligramsPerDeciliter(value),
                specimenSource = BloodGlucoseRecord.SPECIMEN_SOURCE_CAPILLARY_BLOOD
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
            val permissions = client.permissionController.getGrantedPermissions()
            val requiredPermissions = setOf(
                HealthPermission.getWritePermission(BloodGlucoseRecord::class),
                HealthPermission.getReadPermission(BloodGlucoseRecord::class)
            )

            if (!permissions.containsAll(requiredPermissions)) {
                Log.e(tag, "Missing required permissions: ${requiredPermissions - permissions}")
                return false
            }

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

                try {
                    // Create BloodGlucoseRecord
                    val bloodGlucoseRecord = BloodGlucoseRecord(
                        time = timestamp,
                        zoneOffset = null,
                        relationToMeal = mealType,
                        level = androidx.health.connect.client.units.BloodGlucose.milligramsPerDeciliter(values[i]),
                        specimenSource = BloodGlucoseRecord.SPECIMEN_SOURCE_CAPILLARY_BLOOD
                    )

                    records.add(bloodGlucoseRecord)
                } catch (e: Exception) {
                    Log.e(tag, "Error creating record: ${e.message}")
                    continue
                }

                // Report progress
                progressCallback?.invoke(i + 1, totalReadings)
            }

            // Insert records in batches to avoid overwhelming Health Connect
            val batchSize = 50
            for (i in records.indices step batchSize) {
                val batch = records.subList(i, minOf(i + batchSize, records.size))
                val response = client.insertRecords(batch)
                Log.d(tag, "Batch ${i/batchSize + 1}: Inserted ${response.recordIdsList.size} records")
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


// ... other imports ...
    import androidx.activity.result.ActivityResultLauncher // Added import
// ... other imports ...

    class HealthConnectUploader(val context: Context) {

        // ... other code ...

        /**
         * Open Health Connect app
         */
        fun openHealthConnectApp(activityContext: Context? = null, launcher: ActivityResultLauncher<Intent>? = null) { // Added type for launcher
            try {
                val intent = Intent("android.health.connect.action.HEALTH_CONNECT_SETTINGS")
                intent.addCategory(Intent.CATEGORY_DEFAULT)

                if (launcher != null) {
                    // Use launcher to expect a result
                    launcher.launch(intent)
                } else if (activityContext != null) {
                    activityContext.startActivity(intent)
                } else {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            } catch (e: Exception) {
                Log.e(tag, "Error opening Health Connect app", e)
                // Try Play Store as fallback
                openHealthConnectInPlayStore()
            }
        }

        // ... rest of the code ...
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

    fun openHealthConnectApp(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.healthdata")
        if (intent != null) {
            context.startActivity(intent)
        } else {
            Toast.makeText(context, "Health Connect app not installed", Toast.LENGTH_LONG).show()
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
        try {
            val client = healthConnectClient ?: throw Exception("Health Connect not available")
            client.permissionController.revokeAllPermissions()
        } catch (e: Exception) {
            Log.e(tag, "Error revoking permissions: ${e.message}", e)
            throw e
        }
    }
}