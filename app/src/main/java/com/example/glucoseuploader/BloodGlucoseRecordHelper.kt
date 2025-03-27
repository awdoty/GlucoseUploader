package com.example.glucoseuploader

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Metadata
import java.time.Instant
import java.time.ZoneOffset

/**
 * Helper class for creating and managing BloodGlucose records.
 */
class BloodGlucoseRecordHelper(private val healthConnectClient: HealthConnectClient) {

    companion object {
        // Define constants that were missing in the original code
        const val SAMSUNG_HEALTH_APP = "com.samsung.health"
        const val RECORDING_METHOD_MANUALLY_ENTERED = "manually_entered"
    }

    /**
     * Creates a blood glucose record with the provided values.
     */
    fun createBloodGlucoseRecord(
        timestamp: Instant,
        glucoseLevel: Double,
        mealType: BloodGlucoseRecord.MealType = BloodGlucoseRecord.MealType.UNKNOWN,
        specimenSource: BloodGlucoseRecord.SpecimenSource = BloodGlucoseRecord.SpecimenSource.CAPILLARY_BLOOD
    ): BloodGlucoseRecord {
        // Create metadata with appropriate origin and recording method
        val metadata = Metadata(
            // Using constructor properties directly
            dataOrigin = DataOrigin(SAMSUNG_HEALTH_APP),
            recordingMethod = RECORDING_METHOD_MANUALLY_ENTERED,
            device = null,
            clientRecordId = null,
            clientRecordVersion = null,
            lastModifiedTime = null
        )

        // Create and return the BloodGlucoseRecord
        return BloodGlucoseRecord(
            time = timestamp,
            zoneOffset = ZoneOffset.UTC,
            level = glucoseLevel,
            mealType = mealType,
            specimenSource = specimenSource,
            relationToMeal = BloodGlucoseRecord.RelationToMeal.UNKNOWN,
            metadata = metadata
        )
    }
}