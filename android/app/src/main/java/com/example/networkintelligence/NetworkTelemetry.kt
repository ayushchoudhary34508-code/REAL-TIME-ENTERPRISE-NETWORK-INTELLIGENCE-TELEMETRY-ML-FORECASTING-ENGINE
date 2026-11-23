package com.example.networkintelligence

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.round
import kotlin.random.Random

fun Double.roundTo1Decimal(): Double = round(this * 10.0) / 10.0
fun Float.roundTo1Decimal(): Float = round(this * 10.0f) / 10.0f

data class TelemetrySnapshot(
    val networkType: String,
    val downloadSpeedMbps: Float,
    val uploadSpeedMbps: Float,
    val latencyMs: Int,
    val signalDbm: Int,
    val speedCategory: String,
    val rsrpDbm: Int = -85,
    val rsrqDb: Int = -10,
    val sinrDb: Int = 18,
    val cellId: String = "41029-A7",
    val zoomReadiness: String = "1080p HD Ready",
    val gamingStability: String = "Low Jitter (3ms)",
    val SLAStatus: String = "SLA Compliant (99.4%)"
)

object NetworkTelemetry {

    // Shared Flow that emits live 1-second updates to the Dashboard UI
    private val _currentTelemetry = MutableStateFlow(
        TelemetrySnapshot(
            networkType = "CELLULAR",
            downloadSpeedMbps = 0.0f,
            uploadSpeedMbps = 0.0f,
            latencyMs = 0,
            signalDbm = -120,
            speedCategory = "RED"
        )
    )
    val currentTelemetry: StateFlow<TelemetrySnapshot> = _currentTelemetry

    // Shared Flow of live in-memory session readings for instant Radar Map display
    private val _liveSessionReadings = MutableStateFlow<List<SpatialReading>>(emptyList())
    val liveSessionReadings: StateFlow<List<SpatialReading>> = _liveSessionReadings

    fun isNetworkConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun getRealTelemetry(context: Context): TelemetrySnapshot {
        val connected = isNetworkConnected(context)

        if (!connected) {
            val offlineSnapshot = TelemetrySnapshot(
                networkType = "DISCONNECTED",
                downloadSpeedMbps = 0.0f,
                uploadSpeedMbps = 0.0f,
                latencyMs = 0,
                signalDbm = -120,
                speedCategory = "RED",
                rsrpDbm = -120,
                rsrqDb = -20,
                sinrDb = -10,
                cellId = "N/A",
                zoomReadiness = "No Internet Connection",
                gamingStability = "Offline",
                SLAStatus = "SLA Breach (Offline)"
            )
            _currentTelemetry.value = offlineSnapshot
            return offlineSnapshot
        }

        val type = getNetworkType(context)

        // 1-second live telemetry variance (simulation for active link dynamics)
        val speed = (72.0f + Random.nextFloat() * 18.0f).roundTo1Decimal()
        val upload = (20.0f + Random.nextFloat() * 8.0f).roundTo1Decimal()
        val latency = Random.nextInt(18, 32)
        val signal = Random.nextInt(-78, -62)
        val category = classifySpeedTier(speed)

        val liveSnapshot = TelemetrySnapshot(
            networkType = type,
            downloadSpeedMbps = speed,
            uploadSpeedMbps = upload,
            latencyMs = latency,
            signalDbm = signal,
            speedCategory = category,
            rsrpDbm = signal - 15,
            rsrqDb = Random.nextInt(-12, -7),
            sinrDb = Random.nextInt(14, 22),
            cellId = "41029-A7",
            zoomReadiness = if (speed >= 25f) "1080p HD Ready" else "720p SD Ready",
            gamingStability = "Low Jitter (${Random.nextInt(2, 6)}ms)",
            SLAStatus = "SLA Compliant (${(99.0 + Random.nextDouble(0.1, 0.9)).roundTo1Decimal()}%)"
        )

        _currentTelemetry.value = liveSnapshot

        // Append snapshot to local live stream buffer for immediate Radar Map Canvas rendering
        val newPoint = SpatialReading(
            latitude = 28.6139 + Random.nextDouble(-0.002, 0.002),
            longitude = 77.2090 + Random.nextDouble(-0.002, 0.002),
            download_speed = speed.toDouble(),
            latency = latency.toDouble(),
            signal_strength = signal,
            network_type = type,
            speed_category = category
        )

        val updatedList = (_liveSessionReadings.value + newPoint).takeLast(30)
        _liveSessionReadings.value = updatedList

        return liveSnapshot
    }

    fun getNetworkType(context: Context): String {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork ?: return "DISCONNECTED"
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return "DISCONNECTED"

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                getMobileNetworkType(telephonyManager)
            }
            else -> "CELLULAR"
        }
    }

    private fun getMobileNetworkType(telephonyManager: TelephonyManager): String {
        return try {
            when (telephonyManager.dataNetworkType) {
                TelephonyManager.NETWORK_TYPE_NR -> "5G"
                TelephonyManager.NETWORK_TYPE_LTE -> "4G"
                else -> "CELLULAR"
            }
        } catch (e: SecurityException) {
            "CELLULAR"
        }
    }

    fun classifySpeedTier(speedMbps: Float): String {
        return if (speedMbps >= 50.0f) "GREEN" else if (speedMbps >= 20.0f) "YELLOW" else "RED"
    }
}