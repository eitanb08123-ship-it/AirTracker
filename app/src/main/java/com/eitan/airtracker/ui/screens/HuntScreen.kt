package com.eitan.airtracker.ui.screens

import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eitan.airtracker.Graph
import com.eitan.airtracker.beep.GeigerTicker
import com.eitan.airtracker.ui.components.signalLabel
import com.eitan.airtracker.ui.components.signalLevel
import com.eitan.airtracker.ui.components.trendLabel
import com.eitan.airtracker.ui.rememberNow
import kotlinx.coroutines.delay
import kotlin.math.pow

private val Cold = Color(0xFF3A0D0D)
private val Warm = Color(0xFF6B4A00)
private val Hot = Color(0xFF0E5A22)

/** Time between ticks: slow when weak, rapid when strong. */
private fun tickIntervalMs(level: Float): Long =
    (1200.0 - 1110.0 * level.toDouble().pow(1.2)).toLong().coerceIn(80L, 1200L)

/**
 * Full-screen "hot / cold" search: huge 0–100 number, background from red to green,
 * Geiger-counter ticks from the phone speaker and vibration when very close.
 */
@Composable
fun HuntScreen(onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        HuntContent(onClose)
    }
}

@Composable
private fun HuntContent(onClose: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val live by Graph.monitor.state.collectAsStateWithLifecycle()
    val now by rememberNow(250L)
    val detected = live.seenRecently(now)
    val level = if (detected) signalLevel(live.smoothedRssi) else 0f
    val anim by animateFloatAsState(level, label = "lvl")
    var peak by remember { mutableFloatStateOf(0f) }
    if (level > peak) peak = level
    var sound by rememberSaveable { mutableStateOf(true) }
    val ticker = remember { GeigerTicker(context) }
    var muted by remember { mutableStateOf(com.eitan.airtracker.SystemHelpers.alarmMuted(context)) }

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        Graph.monitor.suppressAutoBeep = true
        onDispose {
            view.keepScreenOn = false
            Graph.monitor.suppressAutoBeep = false
            ticker.release()
        }
    }

    LaunchedEffect(sound) {
        if (!sound) {
            ticker.release()
            return@LaunchedEffect
        }
        ticker.prepare()
        val vib = context.getSystemService(Vibrator::class.java)
        while (true) {
            val st = Graph.monitor.state.value
            val t = System.currentTimeMillis()
            val lv = if (st.seenRecently(t)) signalLevel(st.smoothedRssi) else 0f
            if (lv > 0f) {
                ticker.tick()
                if (lv > 0.8f) runCatching {
                    vib?.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
                }
                delay(tickIntervalMs(lv))
            } else {
                delay(400)
            }
        }
    }

    val bg by animateColorAsState(
        when {
            !detected -> Color(0xFF151517)
            anim < 0.5f -> lerp(Cold, Warm, anim / 0.5f)
            else -> lerp(Warm, Hot, (anim - 0.5f) / 0.5f)
        },
        label = "bg",
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(bg)
            .systemBarsPadding()
            .padding(20.dp),
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "מצב חיפוש",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (detected) "${(anim * 100).toInt()}" else "…",
                    color = Color.White,
                    fontSize = 140.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (detected) signalLabel(live.smoothedRssi) else "מחפש אות מהאוזניות",
                    color = Color.White,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                if (detected) {
                    Text(
                        trendLabel(live.trend),
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 18.sp,
                    )
                }
                Spacer(Modifier.height(28.dp))
                // level bar with best-so-far marker
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Color.White.copy(alpha = 0.18f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(anim.coerceIn(0f, 1f))
                            .clip(RoundedCornerShape(9.dp))
                            .background(Color.White),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "השיא עד עכשיו: ${(peak * 100).toInt()}",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 15.sp,
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (detected) {
                        "לך לאט. ככל שהקליקים מהירים יותר והמסך ירוק יותר – אתה קרוב יותר. " +
                            "אם המספר יורד, חזור אחורה ונסה כיוון אחר."
                    } else {
                        "פתח את מכסה הקופסה או ודא שהאוזניות מחוץ לקופסה. בקופסה סגורה אין אות."
                    },
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                    fontSize = 15.sp,
                )
                if (sound && muted) {
                    Spacer(Modifier.height(10.dp))
                    FilledTonalButton(onClick = {
                        com.eitan.airtracker.SystemHelpers.raiseAlarmVolume(context)
                        muted = com.eitan.airtracker.SystemHelpers.alarmMuted(context)
                    }) { Text("עוצמת השעון המעורר על 0 – לחץ להגברה") }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(onClick = { sound = !sound }) {
                        Icon(
                            if (sound) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsOff,
                            null,
                            Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (sound) "השתק קליקים" else "הפעל קליקים")
                    }
                    FilledTonalButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("סגור")
                    }
                }
                androidx.compose.material3.TextButton(onClick = { peak = 0f }) {
                    Text("אפס שיא", color = Color.White)
                }
            }
        }
    }
}
