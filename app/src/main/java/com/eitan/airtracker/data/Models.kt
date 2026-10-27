package com.eitan.airtracker.data

import org.json.JSONObject

enum class ThemeMode { SYSTEM, DARK, LIGHT }

data class AppSettings(
    val beepEnabled: Boolean = true,
    /** Smoothed RSSI (dBm) at or above which the phone beeps. */
    val thresholdRssi: Int = -65,
    val cooldownSec: Int = 60,
    val beepWhenConnected: Boolean = false,
    val backgroundTracking: Boolean = false,
    val notifyOnDisconnect: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)

/**
 * The user's AirPods.
 * [address] is the stable classic-Bluetooth address of the paired (bonded) device.
 * [modelId] is the Apple model id seen in BLE advertisements (the BLE address rotates,
 * so only the model can be remembered for BLE matching).
 */
data class SavedDevice(
    val address: String?,
    val name: String?,
    val modelId: Int?,
) {
    val isEmpty: Boolean get() = address == null && modelId == null
}

enum class RecordSource(val label: String) {
    CONNECTED("מחוברות לטלפון"),
    NEARBY("זוהו בקרבת מקום (BLE)"),
    DISCONNECTED("התנתקו כאן"),
    MANUAL("נשמר ידנית"),
}

data class LocationRecord(
    val lat: Double,
    val lon: Double,
    /** Horizontal accuracy in meters. */
    val accuracy: Float,
    /** When the AirPods were detected. */
    val timeMs: Long,
    /** When the GPS fix itself was taken (can be older than [timeMs]). */
    val fixTimeMs: Long,
    val source: RecordSource,
    val rssi: Int? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("lat", lat)
        put("lon", lon)
        put("acc", accuracy.toDouble())
        put("t", timeMs)
        put("ft", fixTimeMs)
        put("src", source.name)
        if (rssi != null) put("rssi", rssi)
    }

    companion object {
        fun fromJson(o: JSONObject): LocationRecord? = try {
            LocationRecord(
                lat = o.getDouble("lat"),
                lon = o.getDouble("lon"),
                accuracy = o.optDouble("acc", 999.0).toFloat(),
                timeMs = o.getLong("t"),
                fixTimeMs = o.optLong("ft", o.getLong("t")),
                source = runCatching { RecordSource.valueOf(o.getString("src")) }
                    .getOrDefault(RecordSource.MANUAL),
                rssi = if (o.has("rssi")) o.getInt("rssi") else null,
            )
        } catch (e: Exception) {
            null
        }
    }
}
