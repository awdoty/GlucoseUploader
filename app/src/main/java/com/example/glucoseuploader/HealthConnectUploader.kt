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
            Log.e(TAG, "Missing required permissions: ${requiredPermissions - permissions}")
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

        // Insert records in batches to avoid overwhelming Health Connect
        val batchSize = 50
        for (i in records.indices step batchSize) {
            val batch = records.subList(i, minOf(i + batchSize, records.size))
            val response = client.insertRecords(batch)
            Log.d(TAG, "Batch ${i/batchSize + 1}: Inserted ${response.recordIdsList.size} records")
        }

        Log.d(TAG, "Uploaded ${records.size} glucose readings")
        return true
    } catch (e: Exception) {
        Log.e(TAG, "Error uploading glucose series: ${e.message}", e)
        throw e
    }
}