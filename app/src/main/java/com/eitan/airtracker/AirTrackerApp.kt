package com.eitan.airtracker

import android.app.Application
import android.content.Context
import com.eitan.airtracker.service.Notifications
import org.osmdroid.config.Configuration
import java.io.File

class AirTrackerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().apply {
            load(this@AirTrackerApp, getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            userAgentValue = packageName
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }
        Notifications.createChannels(this)
        Graph.init(this)
    }
}
