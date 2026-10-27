package com.eitan.airtracker.service

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.IntentCompat
import com.eitan.airtracker.Graph
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Receives classic Bluetooth connect/disconnect events (allowed for manifest receivers
 * on modern Android). On disconnect of the saved AirPods it stores the phone's location
 * and shows a notification.
 */
class BtEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val connected = when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> true
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> false
            else -> return
        }
        val device = IntentCompat.getParcelableExtra(
            intent,
            BluetoothDevice.EXTRA_DEVICE,
            BluetoothDevice::class.java,
        ) ?: return
        Graph.init(context.applicationContext as Application)
        val pending = goAsync()
        Graph.scope.launch {
            try {
                withTimeoutOrNull(9_000L) { Graph.monitor.handleAclEvent(device, connected) }
            } finally {
                pending.finish()
            }
        }
    }
}
