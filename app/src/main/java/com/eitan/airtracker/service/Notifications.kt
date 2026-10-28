package com.eitan.airtracker.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.eitan.airtracker.MainActivity
import com.eitan.airtracker.Perms
import com.eitan.airtracker.R
import com.eitan.airtracker.data.LocationRecord

object Notifications {
    const val CH_TRACKING = "tracking"
    const val CH_ALERTS = "alerts"
    const val ID_TRACKING = 1
    private const val ID_DISCONNECT = 2

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH_TRACKING, "מעקב ברקע", NotificationManager.IMPORTANCE_LOW).apply {
                description = "הודעה קבועה בזמן שהאפליקציה עוקבת אחרי האוזניות ברקע"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERTS, "התראות ניתוק", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "התראה כשהאוזניות מתנתקות מהטלפון"
            }
        )
    }

    fun openAppIntent(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx,
        0,
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun huntIntent(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx,
        2,
        Intent(ctx, MainActivity::class.java)
            .setAction(MainActivity.ACTION_HUNT)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    @SuppressLint("MissingPermission")
    fun disconnected(ctx: Context, name: String?, rec: LocationRecord?) {
        if (!Perms.hasNotifications(ctx)) return
        val text = if (rec != null) {
            "המיקום נשמר (דיוק ±${rec.accuracy.toInt()} מ׳). הקש כדי לנווט אליו."
        } else {
            "לא התקבל מיקום עדכני. יוצג המיקום האחרון הידוע."
        }
        val n = NotificationCompat.Builder(ctx, CH_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_tracker)
            .setContentTitle("${name ?: "האוזניות"} התנתקו")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openAppIntent(ctx))
            .addAction(0, "חפש עכשיו", huntIntent(ctx))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(ID_DISCONNECT, n)
        } catch (e: SecurityException) {
            // permission revoked meanwhile
        }
    }
}
