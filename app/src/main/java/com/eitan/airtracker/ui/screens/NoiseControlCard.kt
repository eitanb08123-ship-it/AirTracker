@file:OptIn(ExperimentalMaterial3Api::class)

package com.eitan.airtracker.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eitan.airtracker.Graph
import com.eitan.airtracker.bt.NoiseControl
import com.eitan.airtracker.bt.NoiseMode
import com.eitan.airtracker.bt.NoiseResult
import com.eitan.airtracker.ui.components.SectionCard
import com.eitan.airtracker.ui.components.WarningRow
import com.eitan.airtracker.ui.theme.FindGreen
import kotlinx.coroutines.launch

@Composable
fun NoiseControlCard() {
    val scope = rememberCoroutineScope()
    val live by Graph.monitor.state.collectAsStateWithLifecycle()
    val saved by Graph.savedDevice.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<NoiseMode?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    val modes = NoiseMode.entries

    SectionCard(title = "מצב רעשים") {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            modes.forEachIndexed { i, m ->
                SegmentedButton(
                    selected = selected == m,
                    enabled = !busy,
                    onClick = {
                        selected = m
                        val device = Graph.monitor.bondedDevices().firstOrNull {
                            Graph.monitor.matches(it, saved)
                        }
                        if (device == null) {
                            message = false to "לא נמצאו AirPods מותאמות. בחר אותן בלשונית \"מכשירים\"."
                            return@SegmentedButton
                        }
                        if (!live.connected) {
                            message = false to "חבר את האוזניות לטלפון (שים אותן באוזניים) ונסה שוב."
                            return@SegmentedButton
                        }
                        busy = true
                        message = null
                        scope.launch {
                            message = when (val r = NoiseControl.setMode(device, m)) {
                                NoiseResult.Sent -> true to
                                    "הפקודה \"${m.label}\" נשלחה לאוזניות. אם לא שמעת שינוי – הדגם שלך לא תומך במצב הזה."
                                is NoiseResult.Failed -> false to r.reason
                            }
                            busy = false
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                ) { Text(m.label, style = MaterialTheme.typography.labelMedium) }
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let { (ok, text) ->
            WarningRow(
                text,
                if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                if (ok) FindGreen else MaterialTheme.colorScheme.tertiary,
            )
        }
        WarningRow(
            "עובד רק באוזניות עם חסימת רעשים (AirPods 4 ANC, Pro, Max). ב-AirPods 4 הרגילות אין מצבים כאלה, " +
                "והאוזניות יתעלמו מהפקודה.",
            Icons.Filled.Info,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
