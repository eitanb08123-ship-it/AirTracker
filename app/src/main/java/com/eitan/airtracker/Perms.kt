package com.eitan.airtracker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object Perms {
    fun required(): Array<String> = buildList {
        add(Manifest.permission.BLUETOOTH_SCAN)
        add(Manifest.permission.BLUETOOTH_CONNECT)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    private fun granted(ctx: Context, p: String) =
        ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    fun hasBluetooth(ctx: Context) =
        granted(ctx, Manifest.permission.BLUETOOTH_SCAN) &&
            granted(ctx, Manifest.permission.BLUETOOTH_CONNECT)

    fun hasLocation(ctx: Context) =
        granted(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ||
            granted(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)

    fun hasNotifications(ctx: Context) =
        Build.VERSION.SDK_INT < 33 || granted(ctx, Manifest.permission.POST_NOTIFICATIONS)

    fun hasCore(ctx: Context) = hasBluetooth(ctx) && hasLocation(ctx)
}
