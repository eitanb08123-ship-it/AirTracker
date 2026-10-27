package com.eitan.airtracker

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

object SystemHelpers {
    /** True when the alarm stream (used for all beeps/ticks) is muted. */
    fun alarmMuted(ctx: Context): Boolean {
        val am = ctx.getSystemService(AudioManager::class.java) ?: return false
        return am.getStreamVolume(AudioManager.STREAM_ALARM) == 0
    }

    fun raiseAlarmVolume(ctx: Context) {
        val am = ctx.getSystemService(AudioManager::class.java) ?: return
        runCatching {
            val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            am.setStreamVolume(AudioManager.STREAM_ALARM, (max * 0.7).toInt().coerceAtLeast(1), AudioManager.FLAG_SHOW_UI)
        }
    }

    fun ignoringBatteryOptimizations(ctx: Context): Boolean {
        val pm = ctx.getSystemService(PowerManager::class.java) ?: return true
        return pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    @SuppressLint("BatteryLife")
    fun requestUnrestrictedBattery(ctx: Context) {
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
        if (runCatching { ctx.startActivity(direct) }.isFailure) runCatching { ctx.startActivity(fallback) }
    }
}
