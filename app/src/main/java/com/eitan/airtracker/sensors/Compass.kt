package com.eitan.airtracker.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * @param azimuthDeg magnetic heading of the top of the phone, 0..360 (0 = magnetic north)
 * @param needsCalibration true when the sensor reports low accuracy
 */
data class Heading(val azimuthDeg: Float, val needsCalibration: Boolean)

/**
 * Fuses magnetometer + gyroscope + accelerometer through Android's rotation-vector sensor
 * and low-pass filters the result so the arrow does not jitter.
 */
fun headingFlow(context: Context): Flow<Heading?> = callbackFlow {
    val sm = context.getSystemService(SensorManager::class.java)
    val sensor = sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: sm?.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
    if (sm == null || sensor == null) {
        trySend(null)
        awaitClose { }
        return@callbackFlow
    }
    val rot = FloatArray(9)
    val orient = FloatArray(3)
    var sx = 0f
    var sy = 0f
    var initialized = false
    var lowAccuracy = false

    val listener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            SensorManager.getRotationMatrixFromVector(rot, e.values)
            SensorManager.getOrientation(rot, orient)
            val az = orient[0]
            if (!initialized) {
                sx = cos(az); sy = sin(az); initialized = true
            } else {
                sx = sx * 0.82f + cos(az) * 0.18f
                sy = sy * 0.82f + sin(az) * 0.18f
            }
            var deg = Math.toDegrees(atan2(sy, sx).toDouble()).toFloat()
            if (deg < 0f) deg += 360f
            // values[4] = estimated heading accuracy in radians (-1 when unavailable)
            val headingAccRad = if (e.values.size > 4) e.values[4] else -1f
            val badHeading = headingAccRad > Math.toRadians(35.0).toFloat()
            trySend(Heading(deg, lowAccuracy || badHeading))
        }

        override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {
            lowAccuracy = accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE ||
                accuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW
        }
    }
    sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
    awaitClose { sm.unregisterListener(listener) }
}
