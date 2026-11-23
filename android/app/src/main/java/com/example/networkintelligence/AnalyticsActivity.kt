package com.example.networkintelligence

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.networkintelligence.ui.theme.NetworkIntelligenceTheme

class AnalyticsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NetworkIntelligenceTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AnalyticsScreen(onBack = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(onBack: () -> Unit) {
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Network Analytics", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Summary Metrics", fontWeight = FontWeight.Bold, fontSize = 18.sp)

            // Top Metric Tiles
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AnalyticsMetricTile("Avg Speed", "68.2 Mbps", Icons.Default.Speed, Color(0xFF1E88E5), Modifier.weight(1f))
                AnalyticsMetricTile("Avg Latency", "24 ms", Icons.Default.Timer, Color(0xFFFF9800), Modifier.weight(1f))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AnalyticsMetricTile("Signal Strength", "-72 dBm", Icons.Default.SignalCellularAlt, Color(0xFF4CAF50), Modifier.weight(1f))
                AnalyticsMetricTile("Spatial Clusters", "12 Zones", Icons.Default.LocationOn, Color(0xFF9C27B0), Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 1. Hourly Throughput Canvas Bar Chart
            Text("24-Hour Speed Trend (Mbps)", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ThroughputBarChart(
                        dataPoints = listOf(42f, 58f, 75f, 88f, 64f, 92f, 78f, 50f),
                        labels = listOf("00h", "03h", "06h", "09h", "12h", "15h", "18h", "21h"),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 2. Network Type Canvas Donut Ring Chart
            Text("Network Distribution", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    NetworkTypeDonutChart(
                        slices = listOf(
                            0.65f to Color(0xFF1E88E5), // 5G (65%)
                            0.25f to Color(0xFF009688), // Wi-Fi (25%)
                            0.10f to Color(0xFFFF9800)  // 4G (10%)
                        ),
                        modifier = Modifier.size(110.dp)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DonutLegendItem(label = "5G Network", percentage = "65%", color = Color(0xFF1E88E5))
                        DonutLegendItem(label = "Wi-Fi Hotspot", percentage = "25%", color = Color(0xFF009688))
                        DonutLegendItem(label = "4G LTE", percentage = "10%", color = Color(0xFFFF9800))
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 3. Speed Tier Bars
            Text("Speed Tier Distribution", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            SpeedTierBar("Green Tier (≥50 Mbps)", 0.72f, Color(0xFF4CAF50), "72% High-Speed Network")
            SpeedTierBar("Yellow Tier (20–50 Mbps)", 0.20f, Color(0xFFFFC107), "20% Moderate Network")
            SpeedTierBar("Red Tier (<20 Mbps)", 0.08f, Color(0xFFF44336), "8% Low-Speed Network")

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// Custom Jetpack Compose Canvas Bar Chart
@Composable
fun ThroughputBarChart(
    dataPoints: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier
) {
    val maxVal = (dataPoints.maxOrNull() ?: 100f).coerceAtLeast(100f)

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height - 30.dp.toPx()
        val barWidth = 20.dp.toPx()
        val spacing = (width - (dataPoints.size * barWidth)) / (dataPoints.size + 1)

        drawLine(
            color = Color.Gray.copy(alpha = 0.2f),
            start = Offset(0f, height / 2),
            end = Offset(width, height / 2),
            strokeWidth = 2f
        )

        dataPoints.forEachIndexed { index, value ->
            val barHeight = (value / maxVal) * height
            val x = spacing + index * (barWidth + spacing)
            val y = height - barHeight

            drawRoundRect(
                color = Color(0xFF1E88E5),
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
            )
        }
    }
}

// Custom Jetpack Compose Canvas Donut Ring Chart
@Composable
fun NetworkTypeDonutChart(
    slices: List<Pair<Float, Color>>,
    modifier: Modifier = Modifier
) {
    val animateProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 1000),
        label = "DonutAnimation"
    )

    Canvas(modifier = modifier) {
        val strokeWidth = 18.dp.toPx()
        var startAngle = -90f

        slices.forEach { (fraction, color) ->
            val sweepAngle = fraction * 360f * animateProgress
            drawArc(
                color = color,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            startAngle += sweepAngle
        }
    }
}

@Composable
fun DonutLegendItem(label: String, percentage: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, shape = RoundedCornerShape(3.dp))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.width(8.dp))
        Text(percentage, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun AnalyticsMetricTile(title: String, value: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(100.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(value, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
    }
}

@Composable
fun SpeedTierBar(label: String, progress: Float, color: Color, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("${(progress * 100).toInt()}%", fontWeight = FontWeight.Bold, color = color, fontSize = 14.sp)
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = color,
                trackColor = color.copy(alpha = 0.2f)
            )
            Text(description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}