package com.eitan.airtracker.ui.screens

import android.location.Location
import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eitan.airtracker.Graph
import com.eitan.airtracker.data.LocationRecord
import com.eitan.airtracker.data.RecordSource
import com.eitan.airtracker.ui.Format
import com.eitan.airtracker.ui.components.OsmMap
import com.eitan.airtracker.ui.rememberNow
import com.eitan.airtracker.ui.warnings
import kotlinx.coroutines.launch

@Composable
fun MapScreen(dark: Boolean, padding: PaddingValues) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val history by Graph.history.collectAsStateWithLifecycle()
    val phoneFlow = remember { Graph.location.updates() }
    val phone: Location? by phoneFlow.collectAsStateWithLifecycle(initialValue = null)
    val now by rememberNow(15_000L)
    var selected by remember { mutableStateOf<LocationRecord?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(padding),
    ) {
        OsmMap(
            records = history,
            phone = phone,
            dark = dark,
            focus = selected,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(12.dp)
                .clip(RoundedCornerShape(22.dp)),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "היסטוריית מיקומים (${history.size})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            FilledTonalButton(
                enabled = !saving,
                onClick = {
                    saving = true
                    scope.launch {
                        val r = Graph.recorder.record(RecordSource.MANUAL)
                        saving = false
                        Toast.makeText(
                            context,
                            if (r != null) "המיקום הנוכחי נשמר" else "לא התקבל מיקום",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
            ) {
                Icon(Icons.Filled.PushPin, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("סמן כאן")
            }
            if (history.isNotEmpty()) {
                TextButton(onClick = { confirmClear = true }) {
                    Icon(Icons.Filled.Delete, null, Modifier.size(16.dp))
                    Text("נקה")
                }
            }
        }
        LazyColumn(
            Modifier
                .fillMaxWidth()
                .weight(0.8f),
        ) {
            if (history.isEmpty()) {
                item {
                    Text(
                        "אין עדיין מיקומים שמורים. מיקום נשמר אוטומטית כשהטלפון מזהה את האוזניות, " +
                            "כשהן מתחברות וכשהן מתנתקות.",
                        Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(history, key = { it.timeMs }) { r ->
                HistoryItem(r, now, r == (selected ?: history.firstOrNull())) { selected = r }
                HorizontalDivider(Modifier.padding(start = 64.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("למחוק את ההיסטוריה?") },
            text = { Text("כל המיקומים השמורים יימחקו, כולל המיקום האחרון הידוע.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    selected = null
                    scope.launch { Graph.store.clearHistory() }
                }) { Text("מחק") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("ביטול") } },
        )
    }
}

@Composable
private fun HistoryItem(r: LocationRecord, now: Long, highlighted: Boolean, onClick: () -> Unit) {
    val icon = when (r.source) {
        RecordSource.CONNECTED -> Icons.Filled.BluetoothConnected
        RecordSource.NEARBY -> Icons.Filled.Bluetooth
        RecordSource.DISCONNECTED -> Icons.Filled.BluetoothDisabled
        RecordSource.MANUAL -> Icons.Filled.MyLocation
    }
    val warnings = r.warnings(now)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (highlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        ) {
            Icon(icon, null, Modifier.padding(8.dp).size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(r.source.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                "${Format.dateTime(r.timeMs)} · ${Format.ago(r.timeMs, now)} · ±${r.accuracy.toInt()} מ׳",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (warnings.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Warning, null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        warnings.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
    }
}
