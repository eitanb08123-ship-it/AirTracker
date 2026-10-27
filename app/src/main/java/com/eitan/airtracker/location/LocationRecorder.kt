package com.eitan.airtracker.location

import com.eitan.airtracker.data.LocationRecord
import com.eitan.airtracker.data.RecordSource
import com.eitan.airtracker.data.Store
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Saves "the phone was here when it detected the AirPods". */
class LocationRecorder(
    private val location: PhoneLocation,
    private val store: Store,
) {
    private val mutex = Mutex()

    suspend fun record(
        source: RecordSource,
        rssi: Int? = null,
        timeoutMs: Long = 12_000L,
    ): LocationRecord? = mutex.withLock {
        val loc = location.current(timeoutMs) ?: return null
        val rec = LocationRecord(
            lat = loc.latitude,
            lon = loc.longitude,
            accuracy = if (loc.hasAccuracy()) loc.accuracy else 999f,
            timeMs = System.currentTimeMillis(),
            fixTimeMs = loc.time,
            source = source,
            rssi = rssi,
        )
        store.addRecord(rec)
        rec
    }
}
