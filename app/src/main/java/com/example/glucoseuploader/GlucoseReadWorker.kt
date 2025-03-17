package com.example.glucoseuploader

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * Worker to read glucose data in the background
 */
class GlucoseReadWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val tag = "GlucoseReadWorker"
        private const val NOTIFICATION_CHANNEL_ID = "glucose_updates"
        private const val NOTIFICATION_ID = 1001
        private const val WORKER_NAME = "glucose_background_check"
        private const val PERIODIC_WORKER_NAME = "glucose_periodic_check"

        // Helper function to schedule this worker
        fun schedule(
            context: Context,
            delayMinutes: Long = 10,
            timeRangeHours: Long = 24,
            skipNotification: Boolean = false,
            detailedAnalysis: Boolean = true
        ) {
            val workRequest = OneTimeWorkRequestBuilder<GlucoseReadWorker>()
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)

            Log.d(tag, "Scheduled glucose read worker to run in $delayMinutes minutes")
        }

        // Schedule periodic background checks
        fun scheduleRepeating(
            context: Context,
            intervalHours: Long = 12,
            timeRangeHours: Long = 24,
            startDelayMinutes: Long = 60
        ) {
            val workRequest = PeriodicWorkRequestBuilder<GlucoseReadWorker>(
                intervalHours, TimeUnit.HOURS
            )
                .setInitialDelay(startDelayMinutes, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORKER_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )

            Log.d(tag, "Scheduled periodic glucose check every $intervalHours hours")
        }

        // Cancel specific work
        fun cancelWork(context: Context, periodic: Boolean = false) {
            val name = if (periodic) PERIODIC_WORKER_NAME else WORKER_NAME
            WorkManager.getInstance(context).cancelUniqueWork(name)
            Log.d(tag, "Canceled glucose work: $name")
        }

        // Cancel all scheduled work
        fun cancelAllWork(context: Context) {
            WorkManager.getInstance(context).cancelAllWork()
            Log.d(tag, "Canceled all scheduled work")
        }
    }

    override suspend fun doWork(): Result {
        Log.d(tag, "GlucoseReadWorker started")

        try {
            // Create Health Connect client
            val healthConnectClient = HealthConnectClient.getOrCreate(context)

            // Read glucose data from the past 24 hours
            val endTime = Instant.now()
            val startTime = endTime.minus(24, ChronoUnit.HOURS)

            // Create read request
            val request = ReadRecordsRequest(
                recordType = BloodGlucoseRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )

            // Execute the request
            val response = healthConnectClient.readRecords(request)
            val records = response.records

            // Calculate stats
            if (records.isNotEmpty()) {
                val avgGlucose = records.map { it.level.inMilligramsPerDeciliter }.average()
                val latestReading = records.maxByOrNull { it.time }

                // Log the results
                Log.d(tag, "Found ${records.size} glucose readings in the last 24 hours")
                Log.d(tag, "Average glucose: ${String.format("%.1f", avgGlucose)} mg/dL")
                latestReading?.let {
                    Log.d(tag, "Latest reading: ${it.level.inMilligramsPerDeciliter} mg/dL at ${it.time}")
                }

                // Show notification with the results
                showResultNotification(records.size, avgGlucose, latestReading)
            } else {
                Log.d(tag, "No glucose readings found in the last 24 hours")
                showNoDataNotification()
            }

            return Result.success()
        } catch (e: Exception) {
            Log.e(tag, "Error reading glucose data: ${e.message}", e)
            return Result.failure()
        }
    }

    /**
     * Show a notification with the glucose reading results
     */
    private fun showResultNotification(
        count: Int,
        average: Double,
        latestReading: BloodGlucoseRecord?
    ) {
        // Check for notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                Log.w(tag, "Notification permission not granted")
                return
            }
        }

        createNotificationChannel()

        val notificationBuilder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Use Android's built-in icon
            .setContentTitle("Glucose Summary")
            .setContentText("Found $count glucose readings in the last 24 hours")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        "Found $count glucose readings in the last 24 hours\n" +
                                "Average: ${String.format("%.1f", average)} mg/dL\n" +
                                if (latestReading != null) {
                                    "Latest: ${latestReading.level.inMilligramsPerDeciliter} mg/dL at " +
                                            "${formatTime(latestReading.time)}"
                                } else ""
                    )
            )
            .setAutoCancel(true)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
    }

    /**
     * Show a notification when no data is found
     */
    private fun showNoDataNotification() {
        // Check for notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                Log.w(tag, "Notification permission not granted")
                return
            }
        }

        createNotificationChannel()

        val notificationBuilder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Use Android's built-in icon
            .setContentTitle("Glucose Summary")
            .setContentText("No glucose readings found in the last 24 hours")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
    }

    /**
     * Create the notification channel for Android O and above
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Glucose Updates"
            val descriptionText = "Notifications about glucose readings"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Format a time instant for display
     */
    private fun formatTime(instant: Instant): String {
        val localTime = instant.atZone(ZoneId.systemDefault()).toLocalTime()
        val formatter = java.time.format.DateTimeFormatter.ofPattern("h:mm a")
        return localTime.format(formatter)
    }
}