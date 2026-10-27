package com.eitan.airtracker.service

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.eitan.airtracker.Graph
import com.eitan.airtracker.Perms
import com.eitan.airtracker.R

/**
 * Foreground service for background tracking. Keeps a low-power, filtered BLE scan
 * running and lets the app take a location fix when the AirPods disconnect, even with
 * the screen off. Android requires a visible notification for this.
 */
class TrackerService : LifecycleService() {

    private var acquired = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Perms.hasBluetooth(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!goForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!acquired) {
            Graph.monitor.acquire(foreground = false)
            acquired = true
        }
        return START_STICKY
    }

    private fun goForeground(): Boolean {
        val notification = NotificationCompat.Builder(this, Notifications.CH_TRACKING)
            .setSmallIcon(R.drawable.ic_stat_tracker)
            .setContentTitle("AirTracker פעיל")
            .setContentText("שומר את המיקום האחרון שבו זוהו האוזניות")
            .setOngoing(true)
            .setContentIntent(Notifications.openAppIntent(this))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        val withLocation = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        val types = if (Perms.hasLocation(this)) {
            listOf(withLocation, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            listOf(ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        }
        for (t in types) {
            try {
                ServiceCompat.startForeground(this, Notifications.ID_TRACKING, notification, t)
                return true
            } catch (e: Exception) {
                Log.w(TAG, "startForeground failed for type $t", e)
            }
        }
        return false
    }

    override fun onDestroy() {
        if (acquired) {
            Graph.monitor.release(foreground = false)
            acquired = false
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "TrackerService"
        private const val ACTION_STOP = "com.eitan.airtracker.STOP"

        fun start(ctx: Context) {
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, TrackerService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "could not start service", e)
            }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, TrackerService::class.java))
        }
    }
}
