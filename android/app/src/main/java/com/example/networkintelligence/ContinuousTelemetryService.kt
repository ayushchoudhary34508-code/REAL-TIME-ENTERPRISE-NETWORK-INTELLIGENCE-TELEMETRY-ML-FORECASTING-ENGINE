package com.example.networkintelligence

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.util.Collections

class ContinuousTelemetryService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false

    // Thread-safe memory buffer for 1-second telemetry readings
    private val telemetryBuffer = Collections.synchronizedList(mutableListOf<SpatialReading>())

    companion object {
        const val CHANNEL_ID = "signal_alerts_channel"
        const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            isRunning = true
            startForegroundServiceNotification()
            startContinuous1SecondPolling()
        }
        return START_STICKY
    }

    private fun startContinuous1SecondPolling() {
        serviceScope.launch {
            while (isActive) {
                // Read hardware telemetry every 1 second (updates StateFlow for Dashboard UI)
                val snapshot = NetworkTelemetry.getRealTelemetry(applicationContext)

                // Buffer snapshot locally (no database call made here)
                if (snapshot.networkType != "DISCONNECTED") {
                    telemetryBuffer.add(
                        SpatialReading(
                            latitude = 28.6139,
                            longitude = 77.2090,
                            download_speed = snapshot.downloadSpeedMbps.toDouble(),
                            latency = snapshot.latencyMs.toDouble(),
                            signal_strength = snapshot.signalDbm,
                            network_type = snapshot.networkType,
                            speed_category = snapshot.speedCategory
                        )
                    )
                }

                // Check for poor signal alert threshold
                if (snapshot.signalDbm < -105 || (snapshot.downloadSpeedMbps > 0f && snapshot.downloadSpeedMbps < 10f)) {
                    sendWeakSignalAlert(snapshot.signalDbm, snapshot.downloadSpeedMbps)
                }

                delay(1000)
            }
        }
    }

    // Flushes all accumulated 1-second readings to backend SQLite database on app closure
    private fun flushBufferToDatabase() {
        if (telemetryBuffer.isEmpty()) return

        val readingsToFlush = synchronized(telemetryBuffer) {
            val listCopy = ArrayList(telemetryBuffer)
            telemetryBuffer.clear()
            listCopy
        }

        try {
            val response = ApiClient.instance.postTelemetryBatch(readingsToFlush).execute()
            if (response.isSuccessful) {
                println("TelemetryBuffer: Flushed ${readingsToFlush.size} readings to SQLite.")
            }
        } catch (e: Exception) {
            println("TelemetryBuffer: Failed to flush readings: ${e.message}")
        }
    }

    // Called when the user swipes away/closes the app from Recent Apps
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        runBlocking(Dispatchers.IO) {
            flushBufferToDatabase()
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        runBlocking(Dispatchers.IO) {
            flushBufferToDatabase()
        }
        serviceScope.cancel()
        isRunning = false
    }

    private fun startForegroundServiceNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Network Intelligence Active")
            .setContentText("Buffering 1-second telemetry (Flushes to DB on app exit)")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun sendWeakSignalAlert(signalDbm: Int, speedMbps: Float) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to_map", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alertNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("⚠️ Poor Signal Spot Detected")
            .setContentText("Signal: $signalDbm dBm ($speedMbps Mbps). Tap to check coverage map.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1002, alertNotification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Network Telemetry Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Live background network diagnostic service."
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}