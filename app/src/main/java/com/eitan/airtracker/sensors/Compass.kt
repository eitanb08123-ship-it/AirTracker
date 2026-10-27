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
import kotlin.math.hypot
import kotlin.math.sin

/**
 * @param azimuthDeg magnetic heading of the direction the phone points, 0..360 (0 = magnetic north)
 * @param needsCalibration true when the magnetometer reports low accuracy
 * @param unstable true when the phone is held at an angle where heading can't be trusted
 */
data class Heading(val azimuthDeg: Float, val needsCalibration: Boolean, val unstable: Boolean = false)

/**
 * Heading that works whether the phone is flat on the palm or held upright.
 *
 * Android's getOrientation() azimuth breaks down when the phone is tilted towards
 * vertical (it jumps or flips). Instead we take, from the rotation matrix, the
 * horizontal direction of the phone's TOP edge plus the direction out of its BACK.
 * Flat phone: the top edge is horizontal and the back points down → top wins.
 * Upright phone: the top points up and the back points forward → back wins.
 * In between both point forward, so their sum is always "where the phone points".
 */
fun headingFlow(context: Context): Flow<Heading?> = callbackFlow {
    val sm = context.getSystemService(SensorManager::class.java)
    val rotSensor = sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: sm?.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
    if (sm == null || rotSensor == null) {
        trySend(null)
        awaitClose { }
        return@callbackFlow
    }
    val magSensor = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    val r = FloatArray(9)
    var sx = 0f
    var sy = 0f
    var initialized = false
    var magAccuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH

    val rotListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            SensorManager.getRotationMatrixFromVector(r, e.values)
            // Column 1 = device Y (top edge) in world (East, North, Up).
            // Column 2 = device Z (out of the screen); back = -Z.
            val east = r[1] - r[2]
            val north = r[4] - r[5]
            val horizontal = hypot(east, north)
            if (horizontal < 0.15f) return // pointing straight up/down: no usable heading

            val az = atan2(east, north) // radians clockwise from north
            val alpha = 0.2f
            if (!initialized) {
                sx = cos(az); sy = sin(az); initialized = true
            } else {
                sx = sx * (1 - alpha) + cos(az) * alpha
                sy = sy * (1 - alpha) + sin(az) * alpha
            }
            var deg = Math.toDegrees(atan2(sy, sx).toDouble()).toFloat()
            if (deg < 0f) deg += 360f

            val headingAccRad = if (e.values.size > 4) e.values[4] else -1f
            val badHeading = headingAccRad > Math.toRadians(30.0).toFloat()
            val lowMag = magAccuracy == SensorManager.SENSOR_STATUS_UNRELIABLE ||
                magAccuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW
            trySend(Heading(deg, lowMag || badHeading, unstable = horizontal < 0.5f))
        }

        override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
    }
    // Only used to learn the magnetometer's calibration state.
    val magListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            magAccuracy = e.accuracy
        }

        override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {
            magAccuracy = accuracy
        }
    }
    sm.registerListener(rotListener, rotSensor, SensorManager.SENSOR_DELAY_GAME)
    if (magSensor != null) sm.registerListener(magListener, magSensor, SensorManager.SENSOR_DELAY_NORMAL)
    awaitClose {
        sm.unregisterListener(rotListener)
        sm.unregisterListener(magListener)
    }
}
