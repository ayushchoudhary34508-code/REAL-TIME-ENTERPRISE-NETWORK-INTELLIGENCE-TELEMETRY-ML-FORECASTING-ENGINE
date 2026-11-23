package com.example.networkintelligence

import android.annotation.SuppressLint
import android.os.Bundle
import android.preference.PreferenceManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.networkintelligence.ui.theme.NetworkIntelligenceTheme
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MapActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().userAgentValue = "NetworkIntelligenceApp/1.0 (Android; LocationNetworkAnalyzer)"
        Configuration.getInstance().load(applicationContext, PreferenceManager.getDefaultSharedPreferences(applicationContext))

        setContent {
            NetworkIntelligenceTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MapTelemetryScreen()
                }
            }
        }
    }
}

// Open-access tile server without API keys or watermarks
private val FREE_OPEN_TILE_SOURCE = XYTileSource(
    "OSM_De",
    0, 19, 256, ".png",
    arrayOf("https://tile.openstreetmap.de/")
)

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun MapTelemetryScreen() {
    val context = LocalContext.current
    val delhiCenter = GeoPoint(28.6139, 77.2090)

    var telemetryReadings by remember { mutableStateOf<List<SpatialReading>>(emptyList()) }
    var mlRecommendation by remember { mutableStateOf<RecommendationResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedReading by remember { mutableStateOf<SpatialReading?>(null) }

    var timeWindowHours by remember { mutableStateOf(24) }
    var highSpeedOnly by remember { mutableStateOf(false) }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(FREE_OPEN_TILE_SOURCE)
            setMultiTouchControls(true)
            controller.setZoom(16.0)
            controller.setCenter(delhiCenter)
        }
    }

    fun refreshMapData(lat: Double, lng: Double) {
        isLoading = true
        loadMapData(lat, lng, timeWindowHours) { readings, recommendation ->
            telemetryReadings = if (highSpeedOnly) {
                readings.filter { it.speed_category == "GREEN" }
            } else {
                readings
            }
            mlRecommendation = recommendation
            isLoading = false

            mapView.overlays.clear()

            // 1. Static Test Marker (Delhi)
            val testMarker = Marker(mapView).apply {
                position = delhiCenter
                title = "5G Hotspot (78.5 Mbps)"
                snippet = "Latency: 24 ms | Signal: -72 dBm"
                setOnMarkerClickListener { _, _ ->
                    selectedReading = SpatialReading(
                        network_type = "5G",
                        download_speed = 78.5,
                        latency = 24.0,
                        signal_strength = -72,
                        latitude = 28.6139,
                        longitude = 77.2090,
                        speed_category = "GREEN"
                    )
                    true
                }
            }
            mapView.overlays.add(testMarker)

            // 2. Dynamic DB Telemetry Markers
            telemetryReadings.forEach { reading ->
                val rLat = reading.latitude
                val rLng = reading.longitude
                if (rLat != null && rLng != null) {
                    val m = Marker(mapView).apply {
                        position = GeoPoint(rLat, rLng)
                        title = "${reading.network_type} (${reading.download_speed} Mbps)"
                        snippet = "Latency: ${reading.latency ?: 0.0}ms"
                        setOnMarkerClickListener { _, _ ->
                            selectedReading = reading
                            true
                        }
                    }
                    mapView.overlays.add(m)
                }
            }

            // 3. ML Recommended Spot
            mlRecommendation?.let { rec ->
                val rLat = rec.latitude ?: 28.6139
                val rLng = rec.longitude ?: 77.2090
                val recMarker = Marker(mapView).apply {
                    position = GeoPoint(rLat, rLng)
                    title = "⭐ Recommended Spot: ${rec.best_indoor_spot ?: "Optimal Spot"}"
                    snippet = "Expected Speed: ${rec.expected_download_speed}"
                }
                mapView.overlays.add(recMarker)
            }

            mapView.invalidate()
        }
    }

    LaunchedEffect(timeWindowHours, highSpeedOnly) {
        refreshMapData(delhiCenter.latitude, delhiCenter.longitude)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

        // Top Filter Controls Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, start = 12.dp, end = 12.dp)
                .horizontalScroll(rememberScrollState())
                .align(Alignment.TopStart),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = timeWindowHours == 24,
                onClick = { timeWindowHours = 24 },
                label = { Text("Past 24h", fontSize = 12.sp) }
            )
            FilterChip(
                selected = timeWindowHours == 1,
                onClick = { timeWindowHours = 1 },
                label = { Text("Real-Time", fontSize = 12.sp) }
            )
            FilterChip(
                selected = highSpeedOnly,
                onClick = { highSpeedOnly = !highSpeedOnly },
                label = { Text("High Speed Only", fontSize = 12.sp) }
            )
        }

        // Selected Marker Information Sheet
        AnimatedVisibility(
            visible = selectedReading != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            selectedReading?.let { reading ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(8.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = if (reading.speed_category == "GREEN") Color(0xFF4CAF50) else Color(0xFFFFC107)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${reading.network_type} Telemetry",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }
                            IconButton(onClick = { selectedReading = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        }

                        HorizontalDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Download Speed:", fontWeight = FontWeight.Medium)
                            Text("${reading.download_speed} Mbps", fontWeight = FontWeight.Bold)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Latency:", fontWeight = FontWeight.Medium)
                            Text("${reading.latency ?: "N/A"} ms")
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Signal Strength:", fontWeight = FontWeight.Medium)
                            Text("${reading.signal_strength ?: "N/A"} dBm")
                        }
                    }
                }
            }
        }

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            )
        }
    }
}

private fun loadMapData(
    lat: Double,
    lng: Double,
    hours: Int,
    onResult: (List<SpatialReading>, RecommendationResponse?) -> Unit
) {
    ApiClient.instance.getAnalytics(lat = lat, lng = lng, radius = 100.0, hours = hours)
        .enqueue(object : Callback<AnalyticsResponse> {
            override fun onResponse(call: Call<AnalyticsResponse>, response: Response<AnalyticsResponse>) {
                val readings = response.body()?.readings ?: emptyList()

                if (readings.isEmpty()) {
                    ApiClient.instance.getAnalytics(lat = null, lng = null, radius = 999999.0, hours = hours)
                        .enqueue(object : Callback<AnalyticsResponse> {
                            override fun onResponse(c2: Call<AnalyticsResponse>, r2: Response<AnalyticsResponse>) {
                                val allReadings = r2.body()?.readings ?: emptyList()
                                fetchRecommendation(lat, lng, allReadings, onResult)
                            }

                            override fun onFailure(c2: Call<AnalyticsResponse>, t2: Throwable) {
                                fetchRecommendation(lat, lng, emptyList(), onResult)
                            }
                        })
                } else {
                    fetchRecommendation(lat, lng, readings, onResult)
                }
            }

            override fun onFailure(call: Call<AnalyticsResponse>, t: Throwable) {
                onResult(emptyList(), null)
            }
        })
}

private fun fetchRecommendation(
    lat: Double,
    lng: Double,
    readings: List<SpatialReading>,
    onResult: (List<SpatialReading>, RecommendationResponse?) -> Unit
) {
    ApiClient.instance.getRecommendation(lat = lat, lng = lng, radius = 100.0)
        .enqueue(object : Callback<RecommendationResponse> {
            override fun onResponse(call: Call<RecommendationResponse>, response: Response<RecommendationResponse>) {
                onResult(readings, response.body())
            }

            override fun onFailure(call: Call<RecommendationResponse>, t: Throwable) {
                onResult(readings, null)
            }
        })
}