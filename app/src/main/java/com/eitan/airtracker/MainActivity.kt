package com.eitan.airtracker

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eitan.airtracker.data.ThemeMode
import com.eitan.airtracker.service.TrackerService
import com.eitan.airtracker.ui.screens.DevicesScreen
import com.eitan.airtracker.ui.screens.FindScreen
import com.eitan.airtracker.ui.screens.MapScreen
import com.eitan.airtracker.ui.screens.PermissionsScreen
import com.eitan.airtracker.ui.screens.SettingsScreen
import com.eitan.airtracker.ui.theme.AirTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { AirTrackerRoot() }
    }
}

private data class Tab(val title: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("מצא", Icons.Filled.NearMe),
    Tab("מפה", Icons.Filled.Map),
    Tab("מכשירים", Icons.Filled.Headphones),
    Tab("הגדרות", Icons.Filled.Settings),
)

@Composable
fun AirTrackerRoot() {
    val settings by Graph.settings.collectAsStateWithLifecycle()
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    AirTrackerTheme(dark) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            val context = LocalContext.current
            var permsOk by remember { mutableStateOf(Perms.hasCore(context)) }
            OnResume { permsOk = Perms.hasCore(context) }
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(PaddingValues(0.dp))
            ) {
                if (!permsOk) {
                    Box(Modifier.fillMaxSize()) {
                        androidx.compose.material3.Surface(
                            Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background,
                        ) {
                            PermissionsScreen(onResult = { permsOk = Perms.hasCore(context) })
                        }
                    }
                } else {
                    MainScaffold(dark)
                }
            }
        }
    }
}

@Composable
private fun OnResume(block: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) block() }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
}

/** Keeps the fast foreground scan running only while the UI is visible. */
@Composable
private fun MonitorWhileVisible() {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        var acquired = false
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> if (!acquired) {
                    Graph.monitor.acquire(foreground = true); acquired = true
                }
                Lifecycle.Event.ON_STOP -> if (acquired) {
                    Graph.monitor.release(foreground = true); acquired = false
                }
                else -> {}
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose {
            owner.lifecycle.removeObserver(obs)
            if (acquired) Graph.monitor.release(foreground = true)
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun MainScaffold(dark: Boolean) {
    val context = LocalContext.current
    MonitorWhileVisible()
    val settings by Graph.settings.collectAsStateWithLifecycle()
    val live by Graph.monitor.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(settings.backgroundTracking) {
        if (settings.backgroundTracking) TrackerService.start(context)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(t.icon, contentDescription = t.title) },
                        label = { Text(t.title) },
                    )
                }
            }
        },
        snackbarHost = {
            if (!live.bluetoothOn) {
                Snackbar(
                    modifier = Modifier.padding(12.dp),
                    action = {
                        TextButton(onClick = {
                            runCatching {
                                context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                            }
                        }) { Text("הפעל") }
                    },
                ) {
                    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.BluetoothDisabled, null)
                        Text("  Bluetooth כבוי – אי אפשר לזהות את האוזניות")
                    }
                }
            }
        },
    ) { padding ->
        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "tabs",
        ) { current ->
            when (current) {
                0 -> FindScreen(dark, padding, onOpenMap = { tab = 1 })
                1 -> MapScreen(dark, padding)
                2 -> DevicesScreen(padding)
                else -> SettingsScreen(padding)
            }
        }
    }
}
