package com.spartantech.polarwear

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.wear.compose.material.*
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

// DEPENDENCIES NEEDED IN build.gradle (Module: app):
// implementation("androidx.wear.compose:compose-material:1.2.0")
// implementation("androidx.wear.compose:compose-foundation:1.2.0")
// implementation("com.google.android.gms:play-services-location:21.0.1")
// implementation("androidx.activity:activity-compose:1.7.2")

class MainActivity : ComponentActivity() {

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Handle permissions
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        locationPermissionRequest.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ))

        setContent {
            MaterialTheme {
                WatchApp(
                    toggleTorch = { on -> setTorchMode(on) }
                )
            }
        }
    }

    private fun setTorchMode(on: Boolean) {
        val layoutParams = window.attributes
        layoutParams.screenBrightness = if (on) 1.0f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = layoutParams

        if (on) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun WatchApp(toggleTorch: (Boolean) -> Unit) {
    // --- STATE ---
    var time by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var latitude by remember { mutableDoubleStateOf(0.0) }
    var longitude by remember { mutableDoubleStateOf(0.0) }
    var altitude by remember { mutableDoubleStateOf(0.0) }
    var isTorchOn by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // Timer Loop
    LaunchedEffect(Unit) {
        while(true) {
            time = System.currentTimeMillis()
            delay(1000)
        }
    }

    // GPS Loop
    LaunchedEffect(Unit) {
        while(true) {
            val hasPermission = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (hasPermission) {
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                    .addOnSuccessListener { loc ->
                        if (loc != null) {
                            latitude = loc.latitude
                            longitude = loc.longitude
                            altitude = loc.altitude
                        }
                    }
            }
            delay(60000)
        }
    }

    // Sync Torch
    LaunchedEffect(isTorchOn) {
        toggleTorch(isTorchOn)
    }

    // --- MATH CALCULATIONS ---
    val lstValue = calculateLSTValue(time, longitude)
    val polarisHA = calculatePolarisHAFromLST(lstValue)

    // Convert HA to Screen Angle
    // -90 (Top) - (HourAngle * 15) + 180 (Inverted View)
    val polarisAngle = -90f - (polarisHA.toFloat() * 15f) + 180f

    val timeString = remember(time) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(time))
    }

    // Format Helper
    fun formatHms(value: Double): String {
        val totalSeconds = (value * 3600).toInt()
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    }

    val haString = remember(polarisHA) { "HA ${formatHms(polarisHA)}" }
    val lstString = remember(lstValue) { "LST ${formatHms(lstValue)}" }

    // Helper to format coordinates
    val coordString = remember(latitude, longitude) {
        if (latitude == 0.0 && longitude == 0.0) ""
        else {
            val latDir = if (latitude >= 0) "N" else "S"
            val lonDir = if (longitude >= 0) "E" else "W"
            "${String.format("%.3f", abs(latitude))}°$latDir  ${String.format("%.3f", abs(longitude))}°$lonDir"
        }
    }

    val altString = remember(altitude) {
        if (altitude == 0.0) "" else "${altitude.toInt()}m Elev"
    }

    // --- UI LAYOUT ---
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (isTorchOn) {
            // --- TORCH MODE ---
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { isTorchOn = false }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("DOUBLE TAP OFF", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        } else {
            // --- CLOCK/SCOPE MODE ---

            // Interaction Layer (Torch Only)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { isTorchOn = true }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2, size.height / 2)
                    val screenRadius = size.minDimension / 2

                    // --- GEOMETRY ---
                    val outerTickLength = 25f
                    val outerRingRadius = screenRadius - outerTickLength - 2f

                    val gapSize = 12f
                    val innerRingRadius = outerRingRadius - gapSize

                    // The Gap where Polaris sits
                    val gapCenterRadius = (outerRingRadius + innerRingRadius) / 2

                    // --- LAYER 1: The Fixed Reticle (White Clock) ---
                    // No rotation block here anymore
                    val clockColor = Color.White

                    // 1. Draw the Two Rings
                    drawCircle(clockColor, radius = outerRingRadius, style = Stroke(width = 3f))
                    drawCircle(clockColor, radius = innerRingRadius, style = Stroke(width = 3f))

                    // 2. Draw Ticks (60 minutes, 12 hours)
                    for (i in 0 until 60) {
                        val angleDeg = (i * 6f) - 90 // 6 degrees per minute
                        val isHour = i % 5 == 0

                        // Outer Ticks: Go from Ring OUT to Edge
                        val oLen = if (isHour) outerTickLength else 12f
                        val stroke = if (isHour) 3f else 1f

                        val angleRad = Math.toRadians(angleDeg.toDouble())
                        val cosA = cos(angleRad).toFloat()
                        val sinA = sin(angleRad).toFloat()

                        // A. Outer Ring Ticks (Pointing OUTWARDS towards Edge)
                        val startXOuter = center.x + outerRingRadius * cosA
                        val startYOuter = center.y + outerRingRadius * sinA
                        val endXOuter = center.x + (outerRingRadius + oLen) * cosA
                        val endYOuter = center.y + (outerRingRadius + oLen) * sinA

                        drawLine(
                            color = clockColor,
                            start = Offset(startXOuter, startYOuter),
                            end = Offset(endXOuter, endYOuter),
                            strokeWidth = stroke
                        )

                        // B. Inner Ring Ticks (Pointing INWARDS towards Center)
                        val startXInner = center.x + innerRingRadius * cosA
                        val startYInner = center.y + innerRingRadius * sinA
                        val endXInner = center.x + (innerRingRadius - oLen) * cosA
                        val endYInner = center.y + (innerRingRadius - oLen) * sinA

                        drawLine(
                            color = clockColor,
                            start = Offset(startXInner, startYInner),
                            end = Offset(endXInner, endYInner),
                            strokeWidth = stroke
                        )
                    }

                    // 3. Draw Numbers 0, 3, 6, 9
                    drawIntoCanvas { canvas ->
                        val paint = Paint().apply {
                            color = android.graphics.Color.WHITE
                            textAlign = Paint.Align.CENTER
                            textSize = 40f
                            typeface = Typeface.DEFAULT_BOLD
                        }

                        fun drawLabel(text: String, angleDeg: Float) {
                            val r = innerRingRadius - 45f
                            val rad = Math.toRadians(angleDeg.toDouble())
                            val x = center.x + r * cos(rad).toFloat()
                            val y = center.y + r * sin(rad).toFloat() - (paint.descent() + paint.ascent()) / 2
                            canvas.nativeCanvas.drawText(text, x, y, paint)
                        }

                        drawLabel("0", -90f)  // Top
                        drawLabel("3", 0f)    // Right
                        drawLabel("6", 90f)   // Bottom
                        drawLabel("9", 180f)  // Left
                    }

                    // --- LAYER 2: Polaris Indicator (Fixed in the Sky, sits in Gap) ---

                    val polarisRad = Math.toRadians(polarisAngle.toDouble())
                    val polarisPos = Offset(
                        center.x + gapCenterRadius * cos(polarisRad).toFloat(),
                        center.y + gapCenterRadius * sin(polarisRad).toFloat()
                    )

                    val indicatorColor = Color(0xFFFF6D00)

                    drawLine(indicatorColor, Offset(polarisPos.x - 12f, polarisPos.y), Offset(polarisPos.x + 12f, polarisPos.y), strokeWidth = 3f)
                    drawLine(indicatorColor, Offset(polarisPos.x, polarisPos.y - 12f), Offset(polarisPos.x, polarisPos.y + 12f), strokeWidth = 3f)

                    drawCircle(indicatorColor, radius = 8f, center = polarisPos)
                }
            }

            // Top Info (HA and LST)
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = haString,
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = lstString,
                    color = Color.DarkGray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Info Panel (Aligned Bottom) - No Reset Button
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                if (latitude != 0.0) {
                    Text(
                        text = "GPS OK",
                        fontSize = 10.sp,
                        color = Color(0xFF00CC00),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = coordString,
                        fontSize = 10.sp,
                        color = Color.LightGray
                    )
                    if (altitude != 0.0) {
                        Text(
                            text = altString,
                            fontSize = 10.sp,
                            color = Color.LightGray
                        )
                    }
                } else {
                    Text(
                        text = "NO GPS",
                        fontSize = 10.sp,
                        color = Color.Red,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = timeString,
                    fontSize = 14.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}


// --- MATH: LST (0.0 - 24.0) ---
fun calculateLSTValue(timeMillis: Long, longitude: Double): Double {
    val jd = (timeMillis / 86400000.0) + 2440587.5
    val d = jd - 2451545.0
    val gmst = 18.697374558 + 24.06570982441908 * d
    val gmstNormalized = (gmst % 24 + 24) % 24
    val longHours = longitude / 15.0
    return (gmstNormalized + longHours + 24) % 24
}

// --- MATH: Polaris Hour Angle from LST (0.0 - 24.0) ---
fun calculatePolarisHAFromLST(lst: Double): Double {
    val polarisRA = 2.98
    return (lst - polarisRA + 24) % 24
}