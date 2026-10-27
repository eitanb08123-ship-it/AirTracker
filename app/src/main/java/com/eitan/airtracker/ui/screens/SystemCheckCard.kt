package com.eitan.airtracker.ui.screens

import android.location.Location
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eitan.airtracker.Graph
import com.eitan.airtracker.Perms
import com.eitan.airtracker.bt.AppleProximityParser
import com.eitan.airtracker.sensors.Heading
import com.eitan.airtracker.ui.components.SectionCard
import com.eitan.airtracker.ui.components.WarningRow
import com.eitan.airtracker.ui.theme.FindGreen
import kotlinx.coroutines.launch

/** Shows what the phone actually receives, so problems can be diagnosed from one screenshot. */
@Composable
fun SystemCheckCard(heading: Heading?, phone: Location?, now: Long) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stats by Graph.monitor.stats.collectAsStateWithLifecycle()
    val live by Graph.monitor.state.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf(false) }

    fun ago(t: Long) = if (t == 0L) "אף פעם" else "לפני ${((now - t) / 1000).coerceAtLeast(0)} שנ׳"

    val scanAge = if (stats.scanStartedMs == 0L) 0L else now - stats.scanStartedMs
    val modelBlocked = stats.savedModel != null && stats.filteredOut > 0 &&
        stats.lastAppleModel != null && stats.lastAppleModel != stats.savedModel
    val verdict: Pair<Boolean, String> = when {
        !live.bluetoothOn -> false to "Bluetooth כבוי – הפעל אותו."
        !Perms.hasBluetooth(context) -> false to "חסרה הרשאת \"מכשירים בקרבת מקום\" – אשר בהגדרות האפליקציה."
        !Perms.hasPreciseLocation(context) ->
            false to "אושר רק מיקום מקורב – החץ לא יעבוד. הגדרות האפליקציה > הרשאות > מיקום > הפעל \"מיקום מדויק\"."
        !stats.scanning -> false to "הסריקה לא פועלת. היא תופעל מחדש אוטומטית תוך כחצי דקה."
        stats.totalPackets == 0L && scanAge > 8_000 ->
            false to "הטלפון לא מקבל שום אות Bluetooth. כבה והדלק Bluetooth, או הפעל מחדש את הטלפון."
        modelBlocked ->
            false to "האוזניות משדרות, אבל נשמר דגם אחר (${hex(stats.savedModel)}) ולכן הן מסוננות. לחץ \"בטל סינון דגם\"."
        stats.lastAppleMs == 0L || now - stats.lastAppleMs > 15_000 ->
            false to "הטלפון סורק, אבל האוזניות לא משדרות. פתח את המכסה של הקופסה או הוצא אוזנייה, ליד הטלפון."
        else -> true to "הכל תקין – האוזניות נקלטות."
    }

    SectionCard(
        title = "בדיקת מערכת",
        modifier = Modifier.clickable { expanded = !expanded },
    ) {
        WarningRow(
            verdict.second,
            if (verdict.first) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            if (verdict.first) FindGreen else MaterialTheme.colorScheme.tertiary,
        )
        if (!Perms.hasPreciseLocation(context)) {
            Button(onClick = {
                runCatching {
                    context.startActivity(
                        android.content.Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.parse("package:${context.packageName}"),
                        )
                    )
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("פתח הגדרות הרשאות") }
        }
        if (modelBlocked) {
            Button(onClick = {
                scope.launch {
                    Graph.store.clearDevice()
                    Graph.monitor.restart()
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("בטל סינון דגם") }
        }
        if (expanded) {
            Text(
                buildString {
                    append("סריקה: ${stats.mode} · הופעלה מחדש ${stats.restarts} פעמים")
                    stats.lastError?.let { append(" · שגיאה $it") }
                    append("\nכל חבילות ה-Bluetooth: ${stats.totalPackets} (אחרונה ${ago(stats.lastAnyMs)})")
                    append("\nשידורי AirPods: ${stats.applePackets} (אחרון ${ago(stats.lastAppleMs)})")
                    stats.lastAppleModel?.let {
                        append("\nדגם שנקלט: ${AppleProximityParser.modelName(it)} ${hex(it)} · ${stats.lastAppleRssi} dBm")
                    }
                    append("\nדגם שמור לסינון: ${stats.savedModel?.let { hex(it) } ?: "אין"} · סוננו: ${stats.filteredOut}")
                    append("\nמחוברות: ${if (live.connected) "כן" else "לא"}")
                    append("\nמצפן: ${heading?.let { "${it.azimuthDeg.toInt()}°" + if (it.needsCalibration) " (לא מכויל)" else "" } ?: "אין נתון"}")
                    append("\nGPS: ${phone?.let { "±${it.accuracy.toInt()} מ׳" } ?: "אין נתון"}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                "הקש לפרטים",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun hex(v: Int?) = v?.let { "0x%04X".format(it) } ?: "—"
