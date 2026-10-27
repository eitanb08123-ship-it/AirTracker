package com.eitan.airtracker.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eitan.airtracker.Graph
import com.eitan.airtracker.bt.DirectionSweep
import com.eitan.airtracker.bt.SweepResult
import com.eitan.airtracker.sensors.Heading
import com.eitan.airtracker.ui.components.NavigationArrow
import com.eitan.airtracker.ui.components.SectionCard
import com.eitan.airtracker.ui.components.WarningRow
import com.eitan.airtracker.ui.theme.FindGreen
import kotlinx.coroutines.delay

private const val RESULT_VALID_MS = 90_000L
private const val SWEEP_TIMEOUT_MS = 30_000L

/**
 * "Turn around slowly" direction finder. Works only while the AirPods are broadcasting
 * (case open / out of the case) – the same condition as the proximity meter.
 */
@Composable
fun DirectionSweepCard(heading: Heading?, detected: Boolean, now: Long) {
    val headingState = rememberUpdatedState(heading)
    var sweeping by remember { mutableStateOf(false) }
    var sweep by remember { mutableStateOf<DirectionSweep?>(null) }
    var tick by remember { mutableIntStateOf(0) } // forces redraw of progress
    var result by remember { mutableStateOf<SweepResult?>(null) }
    var failed by remember { mutableStateOf(false) }

    // Collect (heading, rssi) pairs during a sweep.
    LaunchedEffect(sweeping) {
        if (!sweeping) return@LaunchedEffect
        val s = DirectionSweep()
        sweep = s
        failed = false
        val start = System.currentTimeMillis()
        var lastSeen = Graph.monitor.state.value.lastSeenMs
        while (true) {
            delay(40)
            val st = Graph.monitor.state.value
            val h = headingState.value
            if (st.lastSeenMs != lastSeen && st.rssi != null && h != null) {
                lastSeen = st.lastSeenMs
                s.add(h.azimuthDeg, st.rssi)
                tick++
            }
            val elapsed = System.currentTimeMillis() - start
            if (s.isComplete() || elapsed > SWEEP_TIMEOUT_MS) break
        }
        val r = s.result(System.currentTimeMillis())
        result = r
        failed = r == null
        sweeping = false
    }

    val res = result?.takeIf { now - it.timeMs < RESULT_VALID_MS }
    val title = "חץ לפי אות ה-Bluetooth"

    SectionCard(title = title) {
        when {
            sweeping -> {
                val s = sweep
                val redraw = tick // read to refresh progress
                if (redraw < 0) return@SectionCard
                Text(
                    "הצמד את הטלפון לחזה (מסך אליך) והסתובב לאט במקום סיבוב שלם.",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(
                    Modifier
                        .fillMaxWidth(0.7f)
                        .aspectRatio(1f)
                        .align(Alignment.CenterHorizontally),
                    contentAlignment = Alignment.Center,
                ) {
                    CoverageRing(s?.covered() ?: BooleanArray(DirectionSweep.BINS), heading?.azimuthDeg)
                    Text(
                        "${((s?.coverage() ?: 0f) * 100).toInt()}%",
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                LinearProgressIndicator(
                    progress = { s?.coverage() ?: 0f },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!detected) {
                    WarningRow("אין אות מהאוזניות כרגע – ודא שהקופסה פתוחה.", Icons.Filled.Warning)
                }
                OutlinedButton(onClick = { sweeping = false }, modifier = Modifier.fillMaxWidth()) {
                    Text("עצור")
                }
            }

            res != null -> {
                val h = heading
                val arrow = if (h != null && !h.unstable) res.bearingDeg - h.azimuthDeg else null
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    NavigationArrow(
                        arrowAngle = arrow,
                        northAngle = h?.let { -it.azimuthDeg },
                        pulsing = detected,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Text(
                    if (res.reliable) "לכיוון הזה האות הכי חזק" else "לא נמצא כיוון ברור",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "ביטחון: ${res.confidence} (הפרש ${res.spreadDb.toInt()} dB). " +
                        "לך כמה צעדים בכיוון החץ, ואז סרוק שוב – ככה מתקרבים.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!res.reliable) {
                    WarningRow(
                        "ייתכן שהאוזניות קרובות מאוד, או שהאות מוחזר מקירות. נסה שוב במקום אחר בחדר.",
                        Icons.Filled.Warning,
                    )
                }
                Button(onClick = { sweeping = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("סרוק שוב")
                }
            }

            else -> {
                Text(
                    "טלפון לא יכול לדעת כיוון מ-Bluetooth ישירות. אבל אם תצמיד את הטלפון לחזה ותסתובב לאט, " +
                        "הגוף שלך חוסם את האות מאחור – והאפליקציה תמצא לאיזה כיוון האות הכי חזק.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (failed) {
                    WarningRow("הסריקה לא קיבלה מספיק אות. ודא שהקופסה פתוחה ונסה שוב.", Icons.Filled.Warning)
                }
                if (!detected) {
                    WarningRow(
                        "זמין רק כשהאוזניות משדרות (הקופסה פתוחה או האוזניות מחוץ לקופסה).",
                        Icons.Filled.Warning,
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = { sweeping = true },
                    enabled = detected && heading != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Explore, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("מצא כיוון (סיבוב של כ-15 שניות)")
                }
            }
        }
    }
}

/** 12 segments lighting up as the user turns; a dot shows where the phone points now. */
@Composable
private fun CoverageRing(covered: BooleanArray, headingDeg: Float?) {
    val on = FindGreen
    val off = MaterialTheme.colorScheme.surfaceVariant
    val dot = MaterialTheme.colorScheme.primary
    val hd by animateFloatAsState(headingDeg ?: 0f, label = "hd")
    Canvas(Modifier.fillMaxSize()) {
        val stroke = 14.dp.toPx()
        val r = size.minDimension / 2f - stroke
        val tl = Offset(center.x - r, center.y - r)
        val n = covered.size
        val seg = 360f / n
        for (i in 0 until n) {
            // compass: 0° = up, clockwise. Canvas arcs: 0° = right, clockwise.
            drawArc(
                color = if (covered[i]) on else off,
                startAngle = i * seg - 90f + 2f,
                sweepAngle = seg - 4f,
                useCenter = false,
                topLeft = tl,
                size = Size(r * 2, r * 2),
                style = Stroke(width = stroke),
            )
        }
        if (headingDeg != null) {
            val a = Math.toRadians((hd - 90f).toDouble())
            drawCircle(
                dot,
                radius = stroke * 0.7f,
                center = Offset(center.x + r * kotlin.math.cos(a).toFloat(), center.y + r * kotlin.math.sin(a).toFloat()),
            )
        }
    }
}
