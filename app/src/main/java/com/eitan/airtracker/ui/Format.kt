package com.eitan.airtracker.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import com.eitan.airtracker.data.LocationRecord
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Format {
    private val dateTime = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    fun distance(m: Float): String = when {
        m < 1000f -> "${m.toInt()} מ׳"
        else -> String.format(Locale.getDefault(), "%.1f ק״מ", m / 1000f)
    }

    fun ago(timeMs: Long, now: Long): String {
        val s = ((now - timeMs) / 1000).coerceAtLeast(0)
        return when {
            s < 45 -> "עכשיו"
            s < 3600 -> "לפני ${(s / 60).coerceAtLeast(1)} דק׳"
            s < 86400 -> {
                val h = s / 3600
                if (h == 1L) "לפני שעה" else "לפני $h שעות"
            }
            else -> {
                val d = s / 86400
                if (d == 1L) "אתמול" else "לפני $d ימים"
            }
        }
    }

    fun dateTime(timeMs: Long): String = dateTime.format(Date(timeMs))
}

const val STALE_AFTER_MS = 30 * 60_000L
const val INACCURATE_ABOVE_M = 40f

/** Human-readable warnings for a saved location, or empty when it looks fine. */
fun LocationRecord.warnings(now: Long): List<String> = buildList {
    if (now - timeMs > STALE_AFTER_MS) add("המיקום ישן (${Format.ago(timeMs, now)})")
    if (accuracy > INACCURATE_ABOVE_M) add("דיוק נמוך (±${accuracy.toInt()} מ׳)")
    if (timeMs - fixTimeMs > 5 * 60_000L) add("נקודת ה-GPS ישנה מרגע הזיהוי")
}

@Composable
fun rememberNow(periodMs: Long = 1000L): State<Long> =
    produceState(System.currentTimeMillis()) {
        while (true) {
            delay(periodMs)
            value = System.currentTimeMillis()
        }
    }
