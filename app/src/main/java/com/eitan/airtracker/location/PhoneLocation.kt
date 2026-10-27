package com.eitan.airtracker.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import com.eitan.airtracker.Perms
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** The phone's own location (GPS / fused provider). */
@SuppressLint("MissingPermission")
class PhoneLocation(private val context: Context) {

    private val client = LocationServices.getFusedLocationProviderClient(context)

    fun isLocationEnabled(): Boolean {
        val lm = context.getSystemService(LocationManager::class.java) ?: return false
        return lm.isLocationEnabled
    }

    /** Continuous high-accuracy updates while collected (used by the on-screen arrow). */
    fun updates(): Flow<Location?> = callbackFlow {
        if (!Perms.hasLocation(context)) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
            .setMinUpdateIntervalMillis(1000L)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it) }
            }
        }
        try {
            client.lastLocation.addOnSuccessListener { loc -> if (loc != null) trySend(loc) }
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            trySend(null)
        }
        awaitClose { client.removeLocationUpdates(callback) }
    }

    /** One fresh fix, falling back to the last known fix. */
    suspend fun current(timeoutMs: Long = 12_000L): Location? {
        if (!Perms.hasLocation(context)) return null
        val fresh = try {
            withTimeoutOrNull(timeoutMs) {
                val cts = CancellationTokenSource()
                try {
                    client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token).await()
                } finally {
                    cts.cancel()
                }
            }
        } catch (e: Exception) {
            null
        }
        if (fresh != null) return fresh
        return try {
            client.lastLocation.await()
        } catch (e: Exception) {
            null
        }
    }
}
