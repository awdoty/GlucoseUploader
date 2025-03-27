package com.example.glucoseuploader

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.reflect.KClass

/**
 * Handles uploading and retrieving glucose data from Health Connect.
 */
class HealthConnectUploader(private val context: Context) {

    companion object {
        // Define constants that were missing in the original code
        const val SAMSUNG_HEALTH_APP = "com.samsung.health"
        const val RECORDING_METHOD_MANUALLY_ENTERED = "manually_entered"
    }

    private val client: HealthConnectClient by lazy {
        HealthConnectClient.getOrCreate(context)
    }

    private val bloodGlucoseRecordHelper by lazy {
        BloodGlucoseRecordHelper(client)
    }

    // Token for tracking changes
    private var changeToken: String? = null

    /**
     * Upload a glucose reading to Health Connect.
     */
    suspend fun uploadGlucoseRecord(
        timestamp: Instant,
        glucoseLevel: Double,
        mealType: BloodGlucoseRecord.MealType = BloodGlucoseRecord.MealType.UNKNOWN
    ): String {
        val record = bloodGlucoseRecordHelper.createBloodGlucoseRecord(
            timestamp = timestamp,
            glucoseLevel = glucoseLevel,
            mealType = mealType
        )

        return client.insertRecords(listOf(record)).recordIdsList.firstOrNull() ?: ""
    }

    /**
     * Upload a pre-created glucose record.
     */
    suspend fun uploadGlucoseRecord(record: BloodGlucoseRecord): String {
        return client.insertRecords(listOf(record)).recordIdsList.firstOrNull() ?: ""
    }

    /**
     * Retrieve glucose readings from a time range.
     */
    suspend fun getGlucoseReadings(
        startTime: Instant,
        endTime: Instant = Instant.now()
    ): List<BloodGlucoseRecord> {
        // Create a time range filter
        val timeRangeFilter = TimeRangeFilter.between(startTime, endTime)

        // Create a request to read blood glucose records
        val request = ReadRecordsRequest(
            recordType = BloodGlucoseRecord::class,
            timeRangeFilter = timeRangeFilter
        )

        // Execute the request and return the records
        return client.readRecords(request).records
    }

    /**
     * Delete a glucose record by ID.
     */
    suspend fun deleteGlucoseRecord(id: String) {
        client.deleteRecords(
            recordType = BloodGlucoseRecord::class,
            recordIdsList = listOf(id)
        )
    }

    /**
     * Start tracking changes to glucose data.
     */
    suspend fun startTrackingChanges(onMessage: (ChangesMessage) -> Unit) {
        try {
            // Get or create a change token
            if (changeToken == null) {
                changeToken = client.getChangesToken(setOf(BloodGlucoseRecord::class))
                onMessage(ChangesMessage.NewTokenReceived(changeToken!!))
            }

            // Use the token to get changes
            val changes = client.getChanges(changeToken!!)

            // Update the token
            changeToken = changes.nextChangesToken
            onMessage(ChangesMessage.NewTokenReceived(changeToken!!))

            // Process the changes
            val recordIds = changes.changes

            if (recordIds.isNotEmpty()) {
                // Create a request to read the changed records
                val request = ReadRecordsRequest(
                    recordType = BloodGlucoseRecord::class,
                    timeRangeFilter = TimeRangeFilter.after(
                        Instant.now().minus(30, ChronoUnit.DAYS)
                    )
                )

                // Read the records
                val records = client.readRecords(request)

                // Inform about the changes
                onMessage(ChangesMessage.RecordsUploaded(records.records.size))
            }
        } catch (e: Exception) {
            onMessage(ChangesMessage.Error(e.message ?: "Unknown error tracking changes"))
        }
    }

    /**
     * Read records by IDs with proper type handling.
     */
    private suspend fun <T : Record> readRecordsByIds(
        recordType: KClass<T>,
        ids: List<String>
    ): List<T> {
        val request = ReadRecordsRequest(
            recordType = recordType,
            recordIdsList = ids
        )

        return client.readRecords(request).records
    }
}