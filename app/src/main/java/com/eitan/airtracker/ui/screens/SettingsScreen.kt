@file:OptIn(ExperimentalMaterial3Api::class)

package com.eitan.airtracker.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eitan.airtracker.BuildConfig
import com.eitan.airtracker.Graph
import com.eitan.airtracker.beep.Beeper
import com.eitan.airtracker.data.AppSettings
import com.eitan.airtracker.data.ThemeMode
import com.eitan.airtracker.service.TrackerService
import com.eitan.airtracker.ui.components.SectionCard
import kotlinx.coroutines.launch

private val thresholds = listOf(-55 to "צמוד", -65 to "קרוב", -78 to "בטווח")
private val cooldowns = listOf(30 to "30 שנ׳", 60 to "דקה", 180 to "3 דק׳")
private val themes = listOf(ThemeMode.SYSTEM to "מערכת", ThemeMode.DARK to "כהה", ThemeMode.LIGHT to "בהיר")

@Composable
fun SettingsScreen(padding: PaddingValues) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by Graph.settings.collectAsStateWithLifecycle()
    fun update(t: (AppSettings) -> AppSettings) = scope.launch { Graph.store.updateSettings(t) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(padding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("הגדרות", style = MaterialTheme.typography.headlineMedium)

        NoiseControlCard()

        SectionCard(title = "צפצוף כשהאוזניות קרובות") {
            SwitchRow("צפצוף אוטומטי", "הצפצוף יוצא מרמקול הטלפון, לא מהאוזניות", s.beepEnabled) { v ->
                update { it.copy(beepEnabled = v) }
            }
            Text("סף קרבה", style = MaterialTheme.typography.bodyMedium)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                thresholds.forEachIndexed { i, (value, label) ->
                    SegmentedButton(
                        selected = s.thresholdRssi == value,
                        onClick = { update { it.copy(thresholdRssi = value) } },
                        shape = SegmentedButtonDefaults.itemShape(i, thresholds.size),
                        enabled = s.beepEnabled,
                    ) { Text(label) }
                }
            }
            Text(
                "\"צמוד\" = אות חזק מאוד (${thresholds[0].first} dBm), \"בטווח\" = גם אות חלש יחסית. " +
                    "זהו סף עוצמת אות, לא מרחק.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("זמן מינימלי בין צפצופים", style = MaterialTheme.typography.bodyMedium)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                cooldowns.forEachIndexed { i, (value, label) ->
                    SegmentedButton(
                        selected = s.cooldownSec == value,
                        onClick = { update { it.copy(cooldownSec = value) } },
                        shape = SegmentedButtonDefaults.itemShape(i, cooldowns.size),
                        enabled = s.beepEnabled,
                    ) { Text(label) }
                }
            }
            SwitchRow(
                "לצפצף גם כשהאוזניות מחוברות",
                "כבוי = לא יצפצף בזמן שאתה מקשיב איתן",
                s.beepWhenConnected,
                enabled = s.beepEnabled,
            ) { v -> update { it.copy(beepWhenConnected = v) } }
            if (com.eitan.airtracker.SystemHelpers.alarmMuted(context)) {
                Text(
                    "עוצמת השעון המעורר בטלפון על 0 – לא תשמע צפצופים.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                FilledTonalButton(onClick = { com.eitan.airtracker.SystemHelpers.raiseAlarmVolume(context) }) {
                    Text("הגבר עוצמה")
                }
            }
            OutlinedButton(onClick = { Beeper.play(context) }) {
                Icon(Icons.Filled.NotificationsActive, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("בדיקת צפצוף")
            }
            Text(
                "עוצמת הצפצוף נקבעת לפי עוצמת השעון המעורר בטלפון.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = "פעילות ברקע") {
            SwitchRow(
                "מעקב ברקע",
                "שומר מיקום בניתוק ומזהה את האוזניות גם כשהאפליקציה סגורה. מציג הודעה קבועה.",
                s.backgroundTracking,
            ) { v ->
                update { it.copy(backgroundTracking = v) }
                if (v) TrackerService.start(context) else TrackerService.stop(context)
            }
            SwitchRow("התראה בניתוק", "הודעה כשהאוזניות מתנתקות מהטלפון", s.notifyOnDisconnect) { v ->
                update { it.copy(notifyOnDisconnect = v) }
            }
            Text(
                "ב-Samsung מומלץ להגדיר את AirTracker כ\"ללא הגבלה\" בשימוש בסוללה, אחרת המערכת עלולה לעצור את המעקב.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (com.eitan.airtracker.SystemHelpers.ignoringBatteryOptimizations(context)) {
                Text("✓ פעילות ברקע ללא הגבלת סוללה", style = MaterialTheme.typography.bodyMedium)
            } else {
                FilledTonalButton(onClick = {
                    com.eitan.airtracker.SystemHelpers.requestUnrestrictedBattery(context)
                }) { Text("אפשר פעילות ברקע ללא הגבלה") }
            }
        }

        SectionCard(title = "מראה") {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                themes.forEachIndexed { i, (value, label) ->
                    SegmentedButton(
                        selected = s.themeMode == value,
                        onClick = { update { it.copy(themeMode = value) } },
                        shape = SegmentedButtonDefaults.itemShape(i, themes.size),
                    ) { Text(label) }
                }
            }
        }

        SectionCard(title = "מה האפליקציה יכולה ולא יכולה לעשות") {
            val lines = listOf(
                "✓ שומרת את מיקום הטלפון ברגע שהוא מזהה את האוזניות, כשהן מתחברות וכשהן מתנתקות.",
                "✓ חץ ומרחק אל המיקום האחרון הידוע, לפי GPS ומצפן.",
                "✓ מד קרבה לפי עוצמת אות ה-Bluetooth כשהאוזניות משדרות.",
                "✗ אין גישה לרשת Find My של Apple – באנדרואיד אין לכך API רשמי.",
                "✗ אי אפשר להשמיע צליל מתוך האוזניות עצמן מאנדרואיד.",
                "✗ כשהאוזניות בתוך קופסה סגורה הן לא משדרות – מוצג רק המיקום האחרון הידוע.",
                "✗ מד הקרבה אינו מרחק מדויק ואינו כיוון; קירות, גוף וכיסים מחלישים את האות.",
            )
            lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }

        Text(
            "AirTracker ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
