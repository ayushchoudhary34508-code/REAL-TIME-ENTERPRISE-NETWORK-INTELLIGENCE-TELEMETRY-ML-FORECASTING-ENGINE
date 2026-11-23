package com.example.networkintelligence

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class TelemetryWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val snapshot = NetworkTelemetry.getRealTelemetry(context)

        val networkType = snapshot.networkType
        val downloadSpeed = snapshot.downloadSpeedMbps.toDouble()
        val latencyDbl = snapshot.latencyMs.toDouble() // Converted to Double?
        val signalInt = snapshot.signalDbm             // Kept as Int?
        val category = snapshot.speedCategory

        val latitude = 28.6139
        val longitude = 77.2090

        val request = SpatialReading(
            latitude = latitude,
            longitude = longitude,
            download_speed = downloadSpeed,
            latency = latencyDbl,
            signal_strength = signalInt,
            network_type = networkType,
            speed_category = category
        )

        return try {
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}