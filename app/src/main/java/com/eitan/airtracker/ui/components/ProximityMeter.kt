package com.eitan.airtracker.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.eitan.airtracker.ui.theme.FindGreen
import com.eitan.airtracker.ui.theme.FindOrange

/** 0..1 "how strong is the signal" – deliberately NOT a distance. */
fun signalLevel(rssi: Double?): Float =
    rssi?.let { ((it + 95.0) / 45.0).coerceIn(0.0, 1.0).toFloat() } ?: 0f

fun signalLabel(rssi: Double?): String = when {
    rssi == null -> "אין אות"
    rssi < -85 -> "אות חלש – רחוקות או מוסתרות"
    rssi < -72 -> "אות בינוני"
    rssi < -60 -> "אות חזק – קרובות"
    else -> "אות חזק מאוד – ממש בסביבה"
}

fun trendLabel(trend: Double): String = when {
    trend > 2.5 -> "האות מתחזק – אתה מתקרב"
    trend < -2.5 -> "האות נחלש – אתה מתרחק"
    else -> "האות יציב"
}

@Composable
fun ProximityMeter(smoothedRssi: Double?, trend: Double, rawRssi: Int?, modifier: Modifier = Modifier) {
    val level by animateFloatAsState(signalLevel(smoothedRssi), label = "level")
    val bars = 10
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            for (i in 0 until bars) {
                val threshold = (i + 1f) / bars
                val on = level >= threshold - 0.05f
                val color by animateColorAsState(
                    if (on) lerpColor(i.toFloat() / (bars - 1)) else MaterialTheme.colorScheme.surfaceVariant,
                    label = "bar$i",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(0.25f + 0.75f * threshold)
                        .clip(RoundedCornerShape(4.dp))
                        .background(color)
                )
            }
        }
        Text(signalLabel(smoothedRssi), style = MaterialTheme.typography.titleMedium)
        Text(
            trendLabel(trend) + (rawRssi?.let { "  ·  $it dBm" } ?: ""),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "מד הקרבה מבוסס על עוצמת אות ה-Bluetooth בלבד. הוא אינו מודד מרחק מדויק ואינו מראה כיוון – " +
                "התהלך לאט והסתובב, וראה מתי האות מתחזק.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun lerpColor(t: Float) = androidx.compose.ui.graphics.lerp(FindOrange, FindGreen, t)

@Composable
fun SmallBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .width(36.dp)
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(3.dp))
                .background(if (fraction < 0.2f) MaterialTheme.colorScheme.error else FindGreen)
        )
    }
}
