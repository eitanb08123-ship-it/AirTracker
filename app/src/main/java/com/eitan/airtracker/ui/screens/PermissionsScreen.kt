package com.eitan.airtracker.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eitan.airtracker.Perms
import com.eitan.airtracker.ui.components.SectionCard
import com.eitan.airtracker.ui.components.WarningRow

@Composable
fun PermissionsScreen(onResult: () -> Unit) {
    val context = LocalContext.current
    var attempts by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        attempts++
        onResult()
    }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Icon(Icons.Filled.NearMe, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(72.dp))
        Text("ברוך הבא ל-AirTracker", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(
            "כדי למצוא את ה-AirPods האפליקציה צריכה כמה הרשאות:",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SectionCard {
            WarningRow("מכשירים בקרבת מקום – לזיהוי האוזניות ב-Bluetooth", Icons.Filled.Bluetooth, MaterialTheme.colorScheme.primary)
            WarningRow("מיקום – לשמירת המקום שבו זוהו ולחץ הניווט", Icons.Filled.LocationOn, MaterialTheme.colorScheme.primary)
            WarningRow("התראות – כדי להודיע כשהאוזניות מתנתקות", Icons.Filled.Notifications, MaterialTheme.colorScheme.primary)
            Text(
                "בבקשת המיקום בחר \"מיקום מדויק\" ו\"בזמן השימוש באפליקציה\". הנתונים נשמרים רק בטלפון שלך.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(
            onClick = { launcher.launch(Perms.required()) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("אשר הרשאות") }
        if (attempts > 0) {
            Text(
                "חלק מההרשאות לא אושרו. אם החלון לא נפתח שוב, אשר אותן ידנית בהגדרות האפליקציה.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = {
                val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                runCatching { context.startActivity(i) }
            }) { Text("פתח הגדרות אפליקציה") }
        }
    }
}
