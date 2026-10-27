package com.eitan.airtracker.ui.screens

import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eitan.airtracker.Graph
import com.eitan.airtracker.bt.AppleProximityParser
import com.eitan.airtracker.ui.components.SectionCard
import com.eitan.airtracker.ui.theme.FindGreen
import kotlinx.coroutines.launch

@SuppressLint("MissingPermission")
@Composable
fun DevicesScreen(padding: PaddingValues) {
    val scope = rememberCoroutineScope()
    val saved by Graph.savedDevice.collectAsStateWithLifecycle()
    val live by Graph.monitor.state.collectAsStateWithLifecycle()
    val nearby by Graph.monitor.nearby.collectAsStateWithLifecycle()
    var refreshKey by remember { mutableIntStateOf(0) }
    val bonded = remember(refreshKey, live.bluetoothOn) {
        Graph.monitor.bondedDevices().sortedByDescending { isAudio(it) }
    }
    val appleNearby = nearby.filter { it.proximity != null }
    val others = nearby.filter { it.proximity == null }.take(40)

    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            SectionCard(title = "האוזניות שלי") {
                val s = saved
                if (s == null) {
                    Text(
                        "עדיין לא נבחרו. עד שתבחר, כל מכשיר מותאם ששמו מכיל \"AirPods\" ייחשב כשלך.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Headphones, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.name ?: "AirPods", style = MaterialTheme.typography.titleMedium)
                            Text(
                                listOfNotNull(
                                    s.address?.let { "כתובת: $it" },
                                    s.modelId?.let {
                                        "דגם BLE: ${AppleProximityParser.modelName(it)} (0x%04X)".format(it)
                                    },
                                ).joinToString("\n"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { scope.launch { Graph.store.clearDevice() } }) { Text("שכח") }
                    }
                }
                Text(
                    "שלב 1: בחר את ה-AirPods מרשימת המכשירים המותאמים (לזיהוי חיבור וניתוק).\n" +
                        "שלב 2: פתח את הקופסה ליד הטלפון ובחר אותן ברשימת \"אוזניות Apple בקרבת מקום\" " +
                        "(לזיהוי ב-BLE ולמד הקרבה).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("מכשירים מותאמים לטלפון", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { refreshKey++; Graph.monitor.restart() }) {
                    Icon(Icons.Filled.Refresh, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("רענן")
                }
            }
        }
        if (bonded.isEmpty()) {
            item {
                Text(
                    "אין מכשירים מותאמים. התאם את ה-AirPods בהגדרות ה-Bluetooth של הטלפון (לחיצה ארוכה על הכפתור בגב הקופסה).",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(bonded, key = { "b_" + it.address }) { d ->
            val name = Graph.monitor.safeName(d) ?: "מכשיר ללא שם"
            DeviceRow(
                icon = if (isAudio(d)) Icons.Filled.Headphones else Icons.Filled.Bluetooth,
                title = name,
                subtitle = d.address + if (live.connected && saved?.address == d.address) " · מחוברות" else "",
                selected = saved?.address == d.address,
                onClick = {
                    scope.launch {
                        Graph.store.saveDevice(address = d.address, name = name)
                        Graph.monitor.refreshConnection()
                    }
                },
            )
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("אוזניות Apple בקרבת מקום", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (live.scanning) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }
        item {
            Text(
                "AirPods משדרות רק כשהקופסה פתוחה או כשהן מחוץ לקופסה. כתובת ה-BLE שלהן מתחלפת כל כמה דקות, " +
                    "ולכן נשמר הדגם ולא הכתובת. אוזניות של אנשים אחרים מאותו דגם עשויות להופיע גם הן.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (appleNearby.isEmpty()) {
            item { Text("לא זוהו כרגע. פתח את הקופסה ליד הטלפון.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(appleNearby, key = { "a_" + it.address }) { n ->
            val strongest = n.address == appleNearby.firstOrNull()?.address
            val p = n.proximity!!
            val battery = listOfNotNull(
                p.leftPct?.let { "ש׳ $it%" },
                p.rightPct?.let { "י׳ $it%" },
                p.casePct?.let { "קופסה $it%" },
            ).joinToString(" · ")
            DeviceRow(
                icon = Icons.Filled.Headphones,
                title = "${p.modelName} (${p.modelHex})",
                subtitle = (if (strongest) "הכי חזק – כנראה שלך · " else "") +
                    "${n.rssi} dBm" + if (battery.isNotEmpty()) " · $battery" else "",
                selected = saved?.modelId == p.modelId,
                onClick = {
                    scope.launch {
                        Graph.store.saveDevice(
                            modelId = p.modelId,
                            name = if (saved?.name == null) p.modelName else null,
                        )
                    }
                },
            )
        }

        item { Text("כל מכשירי ה-Bluetooth בסביבה", style = MaterialTheme.typography.titleMedium) }
        if (others.isEmpty()) {
            item { Text("סורק…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(others, key = { "o_" + it.address }) { n ->
            DeviceRow(
                icon = Icons.Filled.Bluetooth,
                title = n.name ?: "מכשיר ללא שם",
                subtitle = "${n.address} · ${n.rssi} dBm",
                selected = false,
                onClick = null,
            )
        }
    }
}

private fun isAudio(d: BluetoothDevice): Boolean = try {
    d.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO
} catch (e: SecurityException) {
    false
}

@Composable
private fun DeviceRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: (() -> Unit)?,
) {
    SectionCard(
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) Icon(Icons.Filled.CheckCircle, "נבחר", tint = FindGreen)
        }
    }
}
