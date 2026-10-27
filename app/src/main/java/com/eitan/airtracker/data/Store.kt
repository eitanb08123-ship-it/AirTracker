package com.eitan.airtracker.data

import android.content.Context
import android.location.Location
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "airtracker")

/** Local persistent storage (settings, saved device, location history). */
class Store(private val context: Context) {

    private object K {
        val beep = booleanPreferencesKey("beep_enabled")
        val threshold = intPreferencesKey("threshold_rssi")
        val cooldown = intPreferencesKey("cooldown_sec")
        val beepConnected = booleanPreferencesKey("beep_when_connected")
        val background = booleanPreferencesKey("background_tracking")
        val notifyDisconnect = booleanPreferencesKey("notify_disconnect")
        val theme = stringPreferencesKey("theme_mode")
        val devAddress = stringPreferencesKey("device_address")
        val devName = stringPreferencesKey("device_name")
        val devModel = intPreferencesKey("device_model")
        val history = stringPreferencesKey("history_json")
    }

    private val data: Flow<Preferences> = context.dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    val settings: Flow<AppSettings> = data.map(::readSettings)

    val savedDevice: Flow<SavedDevice?> = data.map { p ->
        val d = SavedDevice(p[K.devAddress], p[K.devName], p[K.devModel])
        if (d.isEmpty) null else d
    }

    /** Newest first. */
    val history: Flow<List<LocationRecord>> = data.map { p -> parseHistory(p[K.history]) }

    private fun readSettings(p: Preferences): AppSettings {
        val d = AppSettings()
        return AppSettings(
            beepEnabled = p[K.beep] ?: d.beepEnabled,
            thresholdRssi = p[K.threshold] ?: d.thresholdRssi,
            cooldownSec = p[K.cooldown] ?: d.cooldownSec,
            beepWhenConnected = p[K.beepConnected] ?: d.beepWhenConnected,
            backgroundTracking = p[K.background] ?: d.backgroundTracking,
            notifyOnDisconnect = p[K.notifyDisconnect] ?: d.notifyOnDisconnect,
            themeMode = p[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: d.themeMode,
        )
    }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { p ->
            val n = transform(readSettings(p))
            p[K.beep] = n.beepEnabled
            p[K.threshold] = n.thresholdRssi
            p[K.cooldown] = n.cooldownSec
            p[K.beepConnected] = n.beepWhenConnected
            p[K.background] = n.backgroundTracking
            p[K.notifyDisconnect] = n.notifyOnDisconnect
            p[K.theme] = n.themeMode.name
        }
    }

    /** Pass null for a field to keep its current value; use [clearDevice] to forget. */
    suspend fun saveDevice(address: String? = null, name: String? = null, modelId: Int? = null) {
        context.dataStore.edit { p ->
            if (address != null) p[K.devAddress] = address
            if (name != null) p[K.devName] = name
            if (modelId != null) p[K.devModel] = modelId
        }
    }

    suspend fun clearDevice() {
        context.dataStore.edit { p ->
            p.remove(K.devAddress)
            p.remove(K.devName)
            p.remove(K.devModel)
        }
    }

    /**
     * Adds a record. Repeated sightings of the same kind at the same spot only refresh the
     * newest entry instead of flooding the history.
     */
    suspend fun addRecord(rec: LocationRecord) {
        context.dataStore.edit { p ->
            val list = parseHistory(p[K.history]).toMutableList()
            val last = list.firstOrNull()
            val merge = last != null &&
                rec.source != RecordSource.DISCONNECTED &&
                rec.source != RecordSource.MANUAL &&
                last.source == rec.source &&
                rec.timeMs - last.timeMs < 15 * 60_000L &&
                distance(last, rec) < 30f
            if (merge) {
                list[0] = rec
            } else {
                list.add(0, rec)
            }
            while (list.size > MAX_HISTORY) list.removeAt(list.lastIndex)
            p[K.history] = JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()
        }
    }

    suspend fun clearHistory() {
        context.dataStore.edit { it.remove(K.history) }
    }

    private fun parseHistory(json: String?): List<LocationRecord> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { LocationRecord.fromJson(arr.getJSONObject(it)) }
                .sortedByDescending { it.timeMs }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun distance(a: LocationRecord, b: LocationRecord): Float {
        val r = FloatArray(1)
        Location.distanceBetween(a.lat, a.lon, b.lat, b.lon, r)
        return r[0]
    }

    companion object {
        const val MAX_HISTORY = 300
    }
}
