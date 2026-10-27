package com.eitan.airtracker.ui.screens

import android.content.Intent
import android.hardware.GeomagneticField
import android.location.Location
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eitan.airtracker.Graph
import com.eitan.airtracker.bt.AppleProximity
import com.eitan.airtracker.sensors.headingFlow
import com.eitan.airtracker.ui.Format
import com.eitan.airtracker.ui.components.NavigationArrow
import com.eitan.airtracker.ui.components.OsmMap
import com.eitan.airtracker.ui.components.ProximityMeter
import com.eitan.airtracker.ui.components.SectionCard
import com.eitan.airtracker.ui.components.SmallBar
import com.eitan.airtracker.ui.components.StatusPill
import com.eitan.airtracker.ui.components.WarningRow
import com.eitan.airtracker.ui.rememberNow
import com.eitan.airtracker.ui.theme.FindGreen
import com.eitan.airtracker.ui.warnings

@Composable
fun FindScreen(dark: Boolean, padding: PaddingValues, onOpenMap: () -> Unit) {
    val context = LocalContext.current
    val live by Graph.monitor.state.collectAsStateWithLifecycle()
    val history by Graph.history.collectAsStateWithLifecycle()
    val saved by Graph.savedDevice.collectAsStateWithLifecycle()
    val phoneFlow = remember { Graph.location.updates() }
    val phone: Location? by phoneFlow.collectAsStateWithLifecycle(initialValue = null)
    val headingF = remember { headingFlow(context) }
    val heading by headingF.collectAsStateWithLifecycle(initialValue = null)
    val now by rememberNow()

    val last = history.firstOrNull()
    val detected = live.seenRecently(now)

    val p = phone
    val h = heading
    val nav = computeNav(p, h?.azimuthDeg, last?.lat, last?.lon)
    val distance: Float? = nav.distance
    val arrowAngle: Float? = nav.arrow
    val northAngle: Float? = nav.north
    // Inside the combined GPS uncertainty the bearing is pure noise.
    val uncertainty = (last?.accuracy ?: 0f) + (p?.accuracy ?: 0f)
    val arrived = distance != null && last != null && distance <= maxOf(15f, uncertainty)
    val gpsTooWeak = p != null && p.accuracy > 60f
    val withYou = live.connected
    // Arrow only when it can actually be trusted.
    val shownArrow = when {
        withYou || arrived || gpsTooWeak -> null
        h == null || h.unstable -> null
        else -> arrowAngle
    }
    var showDiag by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(padding)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // ---------- header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    saved?.name ?: live.connectedName ?: "ה-AirPods שלי",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    when {
                        last == null -> "עדיין לא נשמר מיקום"
                        else -> "נראו לאחרונה ${Format.ago(last.timeMs, now)}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                !live.bluetoothOn -> StatusPill("Bluetooth כבוי", MaterialTheme.colorScheme.error, Icons.Filled.BluetoothDisabled)
                live.connected -> StatusPill("מחוברות", FindGreen, Icons.Filled.BluetoothConnected)
                detected -> StatusPill("בקרבת מקום", MaterialTheme.colorScheme.primary, Icons.Filled.Bluetooth)
                else -> StatusPill("לא בטווח", MaterialTheme.colorScheme.onSurfaceVariant, Icons.Filled.Bluetooth)
            }
        }

        if (!Graph.location.isLocationEnabled()) {
            SectionCard {
                WarningRow("שירותי המיקום כבויים – הפעל מיקום (GPS) בהגדרות המכשיר.", Icons.Filled.LocationOff)
            }
        }
        live.error?.let { err ->
            SectionCard { WarningRow(err, Icons.Filled.Warning, MaterialTheme.colorScheme.error) }
        }

        SystemCheckCard(heading = h, phone = p, now = now)

        // ---------- signal-direction arrow (works when the AirPods are near)
        DirectionSweepCard(heading = h, detected = detected, withYou = withYou)

        // ---------- GPS arrow (to the last known location)
        val compactGps = (detected || withYou) && (arrived || withYou || last == null)
        if (compactGps) {
            SectionCard(title = "מיקום אחרון (GPS)") {
                WarningRow(
                    if (withYou) "האוזניות מחוברות לטלפון – הן בטווח של כמה מטרים ממך."
                    else "אתה כבר במקום שבו הן זוהו לאחרונה. ה-GPS לא מדויק מספיק לכיוון כאן – השתמש בחץ האות שלמעלה.",
                    Icons.Filled.Info,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else SectionCard(title = "חץ למיקום האחרון (GPS)", modifier = Modifier.clickable { showDiag = !showDiag }) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                NavigationArrow(
                    arrowAngle = shownArrow,
                    northAngle = northAngle,
                    pulsing = detected || live.connected,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Text(
                text = when {
                    withYou -> "מחוברות אליך"
                    last == null -> "—"
                    arrived -> "הגעת לאזור"
                    p == null -> "ממתין ל-GPS…"
                    distance != null -> Format.distance(distance)
                    else -> "ממתין ל-GPS…"
                },
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = when {
                    withYou -> "האוזניות מחוברות לטלפון כרגע, כלומר הן בטווח של כמה מטרים ממך. אין לאן לנווט."
                    last == null -> "פתח את קופסת האוזניות ליד הטלפון כדי שיזוהו ויישמר מיקום."
                    arrived -> "אתה בתוך טווח הדיוק של ה-GPS מהמיקום השמור (±${uncertainty.toInt()} מ׳). " +
                        "כאן החץ כבר לא אמין – חפש לפי מד הקרבה ולפי הצפצוף."
                    gpsTooWeak -> "ה-GPS חלש מדי לכיוון אמין (±${p?.accuracy?.toInt()} מ׳). צא לשטח פתוח או חכה כמה שניות."
                    h?.unstable == true -> "החזק את הטלפון ישר מולך (מסך כלפי מעלה או מולך) כדי לראות כיוון."
                    else -> "אל המיקום האחרון שבו הטלפון זיהה את האוזניות · ${last.source.label}"
                },
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (h?.needsCalibration == true) {
                WarningRow("המצפן לא מדויק – הזז את הטלפון בתנועת שמונה באוויר.", Icons.Filled.Explore)
            } else if (h == null && p != null) {
                WarningRow("אין חיישן מצפן זמין – מוצג רק מרחק.", Icons.Filled.Info)
            }
            last?.warnings(now)?.forEach { WarningRow(it, Icons.Filled.Warning) }
            if (p != null && p.accuracy > 30f && !gpsTooWeak) {
                WarningRow("ה-GPS של הטלפון לא מדויק כרגע (±${p.accuracy.toInt()} מ׳).", Icons.Filled.Warning)
            }
            if (showDiag) {
                val hdg = h?.azimuthDeg
                val bearing = if (arrowAngle != null && northAngle != null) {
                    ((arrowAngle - northAngle) % 360f + 360f) % 360f
                } else null
                Text(
                    buildString {
                        append("אבחון (הקש שוב להסתרה)\n")
                        append("כיוון הטלפון (מגנטי): ${hdg?.let { "${it.toInt()}°" } ?: "—"}\n")
                        append("כיוון ליעד (מצפון אמיתי): ${bearing?.let { "${it.toInt()}°" } ?: "—"}\n")
                        append("דיוק GPS: ${p?.let { "±${it.accuracy.toInt()} מ׳" } ?: "—"} · ")
                        append("דיוק יעד: ${last?.let { "±${it.accuracy.toInt()} מ׳" } ?: "—"}\n")
                        append("השווה את \"כיוון הטלפון\" לאפליקציית מצפן – אם יש פער גדול, כייל בתנועת שמונה.")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "הקש על הכרטיס להצגת נתוני אבחון",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }

        // ---------- proximity meter
        AnimatedVisibility(
            visible = detected,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            SectionCard(title = "מד קרבה (עוצמת אות)") {
                ProximityMeter(live.smoothedRssi, live.trend, live.rssi)
            }
        }
        if (!detected && !live.connected) {
            SectionCard {
                WarningRow(
                    "האוזניות לא משדרות כרגע. אם הן בתוך קופסה סגורה הן לא נראות ב-Bluetooth – " +
                        "לכן מוצג המיקום האחרון הידוע.",
                    Icons.Filled.Info,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ---------- battery
        SectionCard(title = "סוללה") {
            BatteryRow(live.proximity?.takeIf { detected })
        }

        // ---------- map
        if (last != null) {
            SectionCard(title = "מפה") {
                OsmMap(
                    records = history,
                    phone = p,
                    dark = dark,
                    showHistory = false,
                    interactive = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(16.dp)),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onOpenMap) { Text("מפה והיסטוריה") }
                    Button(onClick = {
                        val uri = Uri.parse("geo:${last.lat},${last.lon}?q=${last.lat},${last.lon}(AirPods)")
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                    }) {
                        Icon(Icons.Filled.NearMe, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("ניווט באפליקציית מפות")
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private data class Nav(val distance: Float?, val arrow: Float?, val north: Float?)

/** Bearing math: true-north heading of the phone vs. bearing to the saved point. */
private fun computeNav(p: Location?, azimuth: Float?, lat: Double?, lon: Double?): Nav {
    if (p == null) return Nav(null, null, null)
    var dist: Float? = null
    var bearing: Float? = null
    if (lat != null && lon != null) {
        val res = FloatArray(2)
        Location.distanceBetween(p.latitude, p.longitude, lat, lon, res)
        dist = res[0]
        bearing = res[1]
    }
    if (azimuth == null) return Nav(dist, null, null)
    val decl = GeomagneticField(
        p.latitude.toFloat(), p.longitude.toFloat(), p.altitude.toFloat(), System.currentTimeMillis()
    ).declination
    val trueHeading = azimuth + decl
    return Nav(dist, bearing?.let { it - trueHeading }, -trueHeading)
}

@Composable
private fun BatteryRow(p: AppleProximity?) {
    if (p == null || !p.hasBattery) {
        Text(
            "לא זמין. אחוזי סוללה מוצגים רק כשהאוזניות משדרות (קופסה פתוחה ליד הטלפון).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        BatteryItem("שמאל", p.leftPct, p.leftCharging)
        BatteryItem("ימין", p.rightPct, p.rightCharging)
        BatteryItem("קופסה", p.casePct, p.caseCharging)
    }
    Text(
        "ערכים משוערים מתוך שידור ה-Bluetooth (בקפיצות של 10%).",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun BatteryItem(label: String, pct: Int?, charging: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(pct?.let { "$it%" } ?: "—", style = MaterialTheme.typography.titleLarge)
            if (charging && pct != null) {
                Icon(Icons.Filled.BatteryChargingFull, null, tint = FindGreen, modifier = Modifier.size(18.dp))
            }
        }
        SmallBar((pct ?: 0) / 100f)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
