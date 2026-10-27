package com.eitan.airtracker.ui.screens

import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
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
import kotlin.math.cos
import kotlin.math.sin

/** Samples older than this are forgotten, so the arrow follows you as you walk. */
private const val WINDOW_MS = 25_000L
private const val MIN_BINS = 8

private data class Sample(val t: Long, val heading: Float, val rssi: Int)

private class SweepUi(
    val covered: BooleanArray = BooleanArray(DirectionSweep.BINS),
    val coverage: Float = 0f,
    val result: SweepResult? = null,
)

/**
 * Continuous signal-direction finder. While the AirPods broadcast, every received
 * advertisement is paired with the current compass heading. Holding the phone against
 * the chest, the body blocks the signal from behind, so the strongest heading points
 * toward the earbuds. Just turn slowly – the arrow appears and keeps updating.
 */
@Composable
fun DirectionSweepCard(heading: Heading?, detected: Boolean, withYou: Boolean) {
    val context = LocalContext.current
    val headingState = rememberUpdatedState(heading)
    val samples = remember { ArrayDeque<Sample>() }
    var ui by remember { mutableStateOf(SweepUi()) }
    var resetCount by remember { mutableStateOf(0) }

    LaunchedEffect(detected, resetCount) {
        samples.clear()
        ui = SweepUi()
        if (!detected) return@LaunchedEffect
        var lastSeen = Graph.monitor.state.value.lastSeenMs
        var lastCompute = 0L
        var hadResult = false
        while (true) {
            delay(40)
            val now = System.currentTimeMillis()
            val st = Graph.monitor.state.value
            val h = headingState.value
            if (st.lastSeenMs != lastSeen && st.rssi != null && h != null && !h.unstable) {
                lastSeen = st.lastSeenMs
                samples.addLast(Sample(now, h.azimuthDeg, st.rssi))
            }
            while (samples.isNotEmpty() && now - samples.first().t > WINDOW_MS) samples.removeFirst()
            if (now - lastCompute > 400) {
                lastCompute = now
                val s = DirectionSweep()
                samples.forEach { s.add(it.heading, it.rssi) }
                val covered = s.covered()
                val bins = covered.count { it }
                val r = if (bins >= MIN_BINS && s.samples >= 20) s.result(now) else null
                if (r != null && r.reliable && !hadResult) {
                    hadResult = true
                    vibrate(context)
                }
                if (r == null) hadResult = false
                ui = SweepUi(covered, bins.toFloat() / DirectionSweep.BINS, r)
            }
        }
    }

    if (!detected) {
        SectionCard(title = "חץ לפי אות Bluetooth") {
            WarningRow(
                if (withYou) "האוזניות מחוברות. כדי לחפש לפי אות, הוצא אותן מהאוזניים או פתח את הקופסה."
                else "יופעל אוטומטית כשהאוזניות ישדרו (קופסה פתוחה או אוזניות מחוץ לקופסה).",
                Icons.Filled.Info,
                MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val res = ui.result
    val h = heading
    val showArrow = res != null && res.reliable && h != null && !h.unstable
    SectionCard(title = "חץ לפי אות Bluetooth") {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .padding(4.dp),
        ) {
            CoverageRing(ui.covered, h?.azimuthDeg, ui.result?.bearingDeg)
            NavigationArrow(
                arrowAngle = if (showArrow) res!!.bearingDeg - h!!.azimuthDeg else null,
                northAngle = null,
                pulsing = true,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(26.dp),
            )
        }
        Text(
            when {
                showArrow -> "לך לכיוון החץ"
                res != null && !res.reliable -> "אין כיוון ברור עדיין"
                else -> "הסתובב לאט במקום"
            },
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            when {
                showArrow -> "ביטחון: ${res!!.confidence}. המשך להסתובב מדי פעם כדי לעדכן; " +
                    "כשהמד למטה בירוק מלא – הן ממש לידך."
                res != null && !res.reliable ->
                    "האות דומה בכל הכיוונים – כנראה הן קרובות מאוד או שהאות מוחזר מקירות. " +
                        "זוז כמה צעדים והסתובב שוב."
                else -> "הצמד את הטלפון לחזה (מסך אליך) והסתובב לאט סיבוב שלם. " +
                    "הגוף חוסם את האות מאחור, וכך נמצא מאיפה הוא הכי חזק. " +
                    "כוסה ${(ui.coverage * 100).toInt()}% מהסיבוב."
            },
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (h?.needsCalibration == true) {
            WarningRow("המצפן לא מכויל – הזז את הטלפון בתנועת שמונה באוויר.", Icons.Filled.Warning)
        }
        OutlinedButton(onClick = { resetCount++ }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("התחל מחדש (אחרי שזזת)")
        }
    }
}

private fun vibrate(context: android.content.Context) {
    runCatching {
        val v = context.getSystemService(Vibrator::class.java)
        v?.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}

/**
 * 12 segments (in phone-relative orientation, so they turn with you) light up as directions
 * get measured; the strongest one is highlighted.
 */
@Composable
private fun CoverageRing(covered: BooleanArray, headingDeg: Float?, bestDeg: Float?) {
    val on = FindGreen.copy(alpha = 0.55f)
    val off = MaterialTheme.colorScheme.surfaceVariant
    val best = FindGreen
    val rot = -(headingDeg ?: 0f)
    Canvas(Modifier.fillMaxSize()) {
        val stroke = 12.dp.toPx()
        val r = size.minDimension / 2f - stroke
        val tl = Offset(center.x - r, center.y - r)
        val n = covered.size
        val seg = 360f / n
        val bestBin = bestDeg?.let { ((it % 360f) / seg).toInt() }
        for (i in 0 until n) {
            drawArc(
                color = when {
                    i == bestBin -> best
                    covered[i] -> on
                    else -> off
                },
                startAngle = i * seg - 90f + 2f + rot,
                sweepAngle = seg - 4f,
                useCenter = false,
                topLeft = tl,
                size = Size(r * 2, r * 2),
                style = Stroke(width = stroke),
            )
        }
        // marker at the top = where the phone points now
        val a = Math.toRadians(-90.0)
        drawCircle(
            color = androidx.compose.ui.graphics.Color.White,
            radius = stroke * 0.45f,
            center = Offset(center.x + r * cos(a).toFloat(), center.y + r * sin(a).toFloat()),
        )
    }
}
