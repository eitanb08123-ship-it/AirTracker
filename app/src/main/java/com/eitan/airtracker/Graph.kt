package com.eitan.airtracker

import android.app.Application
import com.eitan.airtracker.bt.AirPodsMonitor
import com.eitan.airtracker.data.AppSettings
import com.eitan.airtracker.data.LocationRecord
import com.eitan.airtracker.data.SavedDevice
import com.eitan.airtracker.data.Store
import com.eitan.airtracker.location.LocationRecorder
import com.eitan.airtracker.location.PhoneLocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Tiny manual dependency container (app-wide singletons). */
object Graph {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Set by the launcher shortcut / quick-settings tile to open search mode. */
    val huntRequest = kotlinx.coroutines.flow.MutableStateFlow(false)

    lateinit var store: Store
        private set
    lateinit var location: PhoneLocation
        private set
    lateinit var recorder: LocationRecorder
        private set
    lateinit var monitor: AirPodsMonitor
        private set
    lateinit var settings: StateFlow<AppSettings>
        private set
    lateinit var savedDevice: StateFlow<SavedDevice?>
        private set
    lateinit var history: StateFlow<List<LocationRecord>>
        private set

    @Synchronized
    fun init(app: Application) {
        if (::store.isInitialized) return
        store = Store(app)
        settings = store.settings.stateIn(scope, SharingStarted.Eagerly, AppSettings())
        savedDevice = store.savedDevice.stateIn(scope, SharingStarted.Eagerly, null)
        history = store.history.stateIn(scope, SharingStarted.Eagerly, emptyList())
        location = PhoneLocation(app)
        recorder = LocationRecorder(location, store)
        monitor = AirPodsMonitor(app, scope, recorder)
    }
}
