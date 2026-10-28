package com.eitan.airtracker.bt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.eitan.airtracker.Graph
import com.eitan.airtracker.Perms
import com.eitan.airtracker.beep.ProximityBeepController
import com.eitan.airtracker.data.RecordSource
import com.eitan.airtracker.data.SavedDevice
import com.eitan.airtracker.location.LocationRecorder
import com.eitan.airtracker.service.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class NearbyDevice(
    val address: String,
    val name: String?,
    val rssi: Int,
    val lastSeenMs: Long,
    val proximity: AppleProximity?,
)

/** Raw counters shown in the in-app "system check" card. */
data class ScanStats(
    val scanning: Boolean = false,
    val mode: String = "—",
    val scanStartedMs: Long = 0L,
    val restarts: Int = 0,
    val lastError: Int? = null,
    val totalPackets: Long = 0,
    val applePackets: Long = 0,
    val filteredOut: Long = 0,
    val lastAnyMs: Long = 0L,
    val lastAppleMs: Long = 0L,
    val lastAppleModel: Int? = null,
    val lastAppleRssi: Int? = null,
    val savedModel: Int? = null,
)

data class LiveState(
    val bluetoothOn: Boolean = true,
    val scanning: Boolean = false,
    val error: String? = null,
    val connected: Boolean = false,
    val connectedName: String? = null,
    /** Raw RSSI of the latest matching advertisement. */
    val rssi: Int? = null,
    /** Smoothed RSSI used for the meter and beeping. */
    val smoothedRssi: Double? = null,
    /** Positive = signal getting stronger, negative = weaker. */
    val trend: Double = 0.0,
    val lastSeenMs: Long = 0L,
    val proximity: AppleProximity? = null,
) {
    fun seenRecently(now: Long) = smoothedRssi != null && now - lastSeenMs < SEEN_WINDOW_MS

    companion object {
        const val SEEN_WINDOW_MS = 15_000L
    }
}

/**
 * Single source of truth for everything Bluetooth: BLE scanning for the AirPods'
 * public advertisements, classic connection state, and the detection → location
 * recording / beeping pipeline.
 *
 * Users "acquire" it: the UI in the foreground (fast, unfiltered scan so the devices
 * list works) and the background service (battery-friendly, filtered to Apple
 * proximity messages so Android keeps delivering results with the screen off).
 */
@SuppressLint("MissingPermission")
class AirPodsMonitor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val recorder: LocationRecorder,
) {
    private val main = Handler(Looper.getMainLooper())
    private val btManager = context.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter? get() = btManager?.adapter

    private val _state = MutableStateFlow(LiveState())
    val state: StateFlow<LiveState> = _state.asStateFlow()

    private val _stats = MutableStateFlow(ScanStats())
    val stats: StateFlow<ScanStats> = _stats.asStateFlow()
    private var cTotal = 0L
    private var cApple = 0L
    private var cFiltered = 0L
    private var lastAnyMs = 0L
    private var lastAppleMs = 0L
    private var lastAppleModel: Int? = null
    private var lastAppleRssi: Int? = null
    private var scanStartedMs = 0L
    private var lastRestartMs = 0L
    private var restarts = 0
    private var lastError: Int? = null

    private val _nearby = MutableStateFlow<List<NearbyDevice>>(emptyList())
    val nearby: StateFlow<List<NearbyDevice>> = _nearby.asStateFlow()
    private val nearbyMap = HashMap<String, NearbyDevice>()
    private var lastNearbyPush = 0L

    private var fgUsers = 0
    private var bgUsers = 0
    private var currentMode: Int? = null
    private var periodicJob: Job? = null

    private var fastEma: Double? = null
    private var slowEma: Double? = null
    private var lastNearbyRecord = 0L
    private var lastConnectedRecord = 0L

    private val beep = ProximityBeepController(context)

    init {
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        ContextCompat.registerReceiver(
            context,
            object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    val st = i.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF)
                    main.post { onAdapterState(st) }
                }
            },
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        _state.update { it.copy(bluetoothOn = adapter?.isEnabled == true) }
    }

    // ---------------------------------------------------------------- lifecycle

    fun acquire(foreground: Boolean) = main.post {
        if (foreground) fgUsers++ else bgUsers++
        reconfigure()
        refreshConnection()
        ensurePeriodic()
    }

    fun release(foreground: Boolean) = main.post {
        if (foreground) fgUsers = (fgUsers - 1).coerceAtLeast(0) else bgUsers = (bgUsers - 1).coerceAtLeast(0)
        reconfigure()
        if (fgUsers + bgUsers == 0) {
            periodicJob?.cancel()
            periodicJob = null
        }
    }

    /** Re-try scanning (e.g. after permissions were granted or Bluetooth turned on). */
    fun restart() = main.post {
        stopScan()
        reconfigure()
        refreshConnection()
    }

    private fun onAdapterState(st: Int) {
        when (st) {
            BluetoothAdapter.STATE_ON -> {
                _state.update { it.copy(bluetoothOn = true, error = null) }
                currentMode = null
                reconfigure()
                refreshConnection()
            }
            BluetoothAdapter.STATE_OFF, BluetoothAdapter.STATE_TURNING_OFF -> {
                currentMode = null
                _state.update {
                    it.copy(bluetoothOn = false, scanning = false, connected = false, smoothedRssi = null)
                }
            }
        }
    }

    private fun reconfigure() {
        val desired = when {
            fgUsers > 0 -> MODE_FOREGROUND
            bgUsers > 0 -> MODE_BACKGROUND
            else -> null
        }
        if (desired == currentMode) return
        stopScan()
        if (desired != null) startScan(desired)
    }

    // ---------------------------------------------------------------- scanning

    private fun startScan(mode: Int) {
        val a = adapter
        if (a == null) {
            _state.update { it.copy(scanning = false, error = "אין Bluetooth במכשיר") }
            return
        }
        if (!a.isEnabled) {
            _state.update { it.copy(scanning = false, bluetoothOn = false) }
            return
        }
        if (!Perms.hasBluetooth(context)) {
            _state.update { it.copy(scanning = false, error = "חסרות הרשאות Bluetooth") }
            return
        }
        val scanner = a.bluetoothLeScanner ?: return
        val settings = ScanSettings.Builder()
            .setScanMode(
                if (mode == MODE_FOREGROUND) ScanSettings.SCAN_MODE_LOW_LATENCY
                else ScanSettings.SCAN_MODE_LOW_POWER
            )
            .build()
        val filters: List<ScanFilter>? = if (mode == MODE_FOREGROUND) {
            null
        } else {
            listOf(
                ScanFilter.Builder()
                    .setManufacturerData(
                        AppleProximityParser.APPLE_ID,
                        byteArrayOf(0x07),
                        byteArrayOf(0xFF.toByte()),
                    )
                    .build()
            )
        }
        try {
            scanner.startScan(filters, settings, scanCallback)
            currentMode = mode
            scanStartedMs = System.currentTimeMillis()
            _state.update { it.copy(scanning = true, error = null, bluetoothOn = true) }
        } catch (e: Exception) {
            Log.w(TAG, "startScan failed", e)
            currentMode = null
            _state.update { it.copy(scanning = false, error = "לא ניתן להתחיל סריקה") }
        }
    }

    private fun stopScan() {
        if (currentMode == null) return
        try {
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.w(TAG, "stopScan failed", e)
        }
        currentMode = null
        _state.update { it.copy(scanning = false) }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) = handleResult(result)

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach(::handleResult)
        }

        override fun onScanFailed(errorCode: Int) {
            lastError = errorCode
            main.post {
                currentMode = null
                _state.update {
                    it.copy(scanning = false, error = "סריקת Bluetooth נכשלה (קוד $errorCode)")
                }
            }
        }
    }

    private fun handleResult(r: ScanResult) {
        val now = System.currentTimeMillis()
        cTotal++
        lastAnyMs = now
        val record = r.scanRecord
        val prox = AppleProximityParser.parse(
            record?.getManufacturerSpecificData(AppleProximityParser.APPLE_ID)
        )
        val name = record?.deviceName ?: safeName(r.device)
        val address = r.device.address
        synchronized(nearbyMap) {
            nearbyMap[address] = NearbyDevice(address, name, r.rssi, now, prox)
            if (now - lastNearbyPush > 1000 || _nearby.value.isEmpty()) {
                lastNearbyPush = now
                nearbyMap.entries.removeAll { now - it.value.lastSeenMs > 20_000 }
                _nearby.value = nearbyMap.values.sortedByDescending { it.rssi }
            }
        }
        if (prox != null) onProximity(prox, r.rssi, now)
    }

    /** Set by the full-screen search mode so the one-shot alarm doesn't talk over its ticks. */
    @Volatile
    var suppressAutoBeep = false

    private fun onProximity(p: AppleProximity, rssi: Int, now: Long) {
        val saved = Graph.savedDevice.value
        cApple++
        lastAppleMs = now
        lastAppleModel = p.modelId
        lastAppleRssi = rssi
        if (saved?.modelId != null && saved.modelId != p.modelId) {
            cFiltered++
            return
        }

        val f = fastEma?.let { it * 0.6 + rssi * 0.4 } ?: rssi.toDouble()
        val s = slowEma?.let { it * 0.92 + rssi * 0.08 } ?: rssi.toDouble()
        fastEma = f
        slowEma = s
        val wasSeen = _state.value.seenRecently(now)
        _state.update {
            it.copy(rssi = rssi, smoothedRssi = f, trend = f - s, lastSeenMs = now, proximity = p)
        }
        if (!suppressAutoBeep) beep.onSignal(f, _state.value.connected, now)

        if (!wasSeen && now - lastNearbyRecord > 60_000L) {
            lastNearbyRecord = now
            scope.launch { recorder.record(RecordSource.NEARBY, rssi) }
        }
    }

    // ---------------------------------------------------------------- periodic

    private fun ensurePeriodic() {
        if (periodicJob?.isActive == true) return
        periodicJob = scope.launch {
            while (isActive) {
                delay(2_000L)
                main.post { watchdog() }
                publishStats()
                tickCounter++
                if (tickCounter % 5 == 0) tick()
            }
        }
    }

    private var tickCounter = 0

    /**
     * Android sometimes silently stops delivering scan results (Samsung battery
     * optimisations, "scanning too frequently" throttling, a stuck BT stack).
     * If a foreground scan received nothing for 12 s, restart it – at most every 35 s,
     * because more than 5 starts in 30 s get the app blocked from scanning.
     */
    private fun watchdog() {
        val now = System.currentTimeMillis()
        val a = adapter ?: return
        val desired = when {
            fgUsers > 0 -> MODE_FOREGROUND
            bgUsers > 0 -> MODE_BACKGROUND
            else -> null
        } ?: return
        if (!a.isEnabled || !Perms.hasBluetooth(context)) return
        val silentSince = maxOf(lastAnyMs, scanStartedMs)
        val stuck = currentMode == null || (desired == MODE_FOREGROUND && now - silentSince > 12_000L)
        if (stuck && now - lastRestartMs > 35_000L) {
            lastRestartMs = now
            restarts++
            stopScan()
            startScan(desired)
        }
    }

    private fun publishStats() {
        _stats.value = ScanStats(
            scanning = currentMode != null,
            mode = when (currentMode) {
                MODE_FOREGROUND -> "מהיר (אפליקציה פתוחה)"
                MODE_BACKGROUND -> "חסכוני (רקע)"
                else -> "כבוי"
            },
            scanStartedMs = scanStartedMs,
            restarts = restarts,
            lastError = lastError,
            totalPackets = cTotal,
            applePackets = cApple,
            filteredOut = cFiltered,
            lastAnyMs = lastAnyMs,
            lastAppleMs = lastAppleMs,
            lastAppleModel = lastAppleModel,
            lastAppleRssi = lastAppleRssi,
            savedModel = Graph.savedDevice.value?.modelId,
        )
    }

    private suspend fun tick() {
        val now = System.currentTimeMillis()
        val s = _state.value
        if (s.smoothedRssi != null && !s.seenRecently(now)) {
            fastEma = null
            slowEma = null
            _state.update { it.copy(smoothedRssi = null, rssi = null, trend = 0.0) }
            beep.reset()
        }
        when {
            s.connected && now - lastConnectedRecord > RECORD_INTERVAL_MS -> {
                lastConnectedRecord = now
                recorder.record(RecordSource.CONNECTED)
            }
            !s.connected && s.seenRecently(now) && now - lastNearbyRecord > RECORD_INTERVAL_MS -> {
                lastNearbyRecord = now
                recorder.record(RecordSource.NEARBY, s.rssi)
            }
        }
    }

    // ---------------------------------------------------------------- connection

    fun refreshConnection() {
        val a = adapter ?: return
        if (!Perms.hasBluetooth(context) || !a.isEnabled) {
            _state.update { it.copy(connected = false) }
            return
        }
        try {
            a.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    val devices = try {
                        proxy.connectedDevices
                    } catch (e: SecurityException) {
                        emptyList()
                    }
                    val match = devices.firstOrNull { matches(it, Graph.savedDevice.value) }
                    _state.update {
                        it.copy(
                            connected = match != null,
                            connectedName = match?.let { d -> safeName(d) } ?: it.connectedName,
                        )
                    }
                    runCatching { a.closeProfileProxy(profile, proxy) }
                }

                override fun onServiceDisconnected(profile: Int) {}
            }, BluetoothProfile.A2DP)
        } catch (e: Exception) {
            Log.w(TAG, "getProfileProxy failed", e)
        }
    }

    /** Called from [com.eitan.airtracker.service.BtEventReceiver]; may run in a cold process. */
    suspend fun handleAclEvent(device: BluetoothDevice, connected: Boolean) {
        val saved = Graph.store.savedDevice.first()
        if (!matches(device, saved)) return
        val name = safeName(device)
        _state.update {
            it.copy(connected = connected, connectedName = name ?: it.connectedName)
        }
        if (connected) {
            lastConnectedRecord = System.currentTimeMillis()
            recorder.record(RecordSource.CONNECTED, timeoutMs = 7_000L)
        } else {
            val rec = recorder.record(RecordSource.DISCONNECTED, timeoutMs = 7_000L)
            if (Graph.store.settings.first().notifyOnDisconnect) {
                Notifications.disconnected(context, name, rec)
            }
        }
    }

    fun matches(device: BluetoothDevice, saved: SavedDevice?): Boolean {
        if (saved?.address != null) return device.address.equals(saved.address, ignoreCase = true)
        val n = safeName(device) ?: return false
        return n.contains("AirPods", ignoreCase = true)
    }

    fun bondedDevices(): List<BluetoothDevice> = try {
        adapter?.bondedDevices?.toList().orEmpty()
    } catch (e: SecurityException) {
        emptyList()
    }

    fun safeName(d: BluetoothDevice): String? = try {
        d.name
    } catch (e: SecurityException) {
        null
    }

    companion object {
        private const val TAG = "AirPodsMonitor"
        private const val MODE_FOREGROUND = 1
        private const val MODE_BACKGROUND = 2
        private const val RECORD_INTERVAL_MS = 2 * 60_000L
    }
}
