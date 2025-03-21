package com.example.glucoseuploader

import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.BloodGlucose
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Helper class to create BloodGlucoseRecord objects with proper metadata
 * Updated for Android 14+ compatibility
 */
object BloodGlucoseRecordHelper {

    /**
     * Create a BloodGlucoseRecord for manual entry (from CSV import)
     */
    fun createManualEntry(
        value: Double,
        time: Instant,
        relationToMeal: Int = BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN,
        specimenSource: Int = BloodGlucoseRecord.SPECIMEN_SOURCE_CAPILLARY_BLOOD
    ): BloodGlucoseRecord {
        // Create device info
        val device = Device(
            manufacturer = android.os.Build.MANUFACTURER,
            model = android.os.Build.MODEL,
            type = Device.TYPE_PHONE
        )

        // Create metadata using Builder pattern for Android 14+ compatibility
        val metadata = Metadata.Builder()
            .setDevice(device)
            .setRecordingMethod(Metadata.RECORDING_METHOD_MANUALLY_ENTERED)
            .build()

        // Create the record
        return BloodGlucoseRecord(
            time = time,
            zoneOffset = ZoneId.systemDefault().rules.getOffset(time),
            level = BloodGlucose.milligramsPerDeciliter(value),
            relationToMeal = relationToMeal,
            specimenSource = specimenSource,
            metadata = metadata
        )
    }

    /**
     * Create a series of BloodGlucoseRecords from a list of readings
     */
    fun createSeriesFromReadings(
        readings: List<GlucoseReading>
    ): List<BloodGlucoseRecord> {
        return readings.map { reading ->
            createManualEntry(
                value = reading.value,
                time = reading.dateTime.toInstant(),
                relationToMeal = reading.mealType
            )
        }
    }

    /**
     * Create a series of BloodGlucoseRecords from CSV data
     */
    fun createSeriesFromCsvData(
        values: List<Double>,
        startTime: ZonedDateTime,
        endTime: ZonedDateTime,
        mealTypes: List<Int> = emptyList()
    ): List<BloodGlucoseRecord> {
        val records = mutableListOf<BloodGlucoseRecord>()

        // Calculate time interval between readings
        val totalReadings = values.size
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
            val mealType = if (i < mealTypes.size) mealTypes[i]
            else BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN

            // Create the record with proper metadata
            records.add(createManualEntry(
                value = values[i],
                time = timestamp,
                relationToMeal = mealType
            ))
        }

        return records
    }
}