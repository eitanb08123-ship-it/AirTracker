package com.eitan.airtracker.ui.components

import android.location.Location
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.eitan.airtracker.R
import com.eitan.airtracker.data.LocationRecord
import com.eitan.airtracker.ui.Format
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.TilesOverlay

private class MapMemo {
    var centeredKey: Long? = null
}

/**
 * OpenStreetMap view (no API key needed).
 * @param records history, newest first
 * @param focus record to center on (defaults to newest)
 */
@Composable
fun OsmMap(
    records: List<LocationRecord>,
    phone: Location?,
    dark: Boolean,
    modifier: Modifier = Modifier,
    focus: LocationRecord? = null,
    showHistory: Boolean = true,
    interactive: Boolean = true,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val memo = remember { MapMemo() }
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(17.0)
            isTilesScaledToDpi = true
        }
    }

    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(obs)
            mapView.onDetach()
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier) { mv ->
        if (interactive) mv.setOnTouchListener(null) else mv.setOnTouchListener { _, _ -> true }
        mv.overlayManager.tilesOverlay.setColorFilter(if (dark) TilesOverlay.INVERT_COLORS else null)
        mv.overlays.clear()

        val target = focus ?: records.firstOrNull()

        if (showHistory) {
            records.drop(1).take(60).forEach { r ->
                mv.overlays.add(Marker(mv).apply {
                    position = GeoPoint(r.lat, r.lon)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = ContextCompat.getDrawable(context, R.drawable.marker_history)
                    title = "${r.source.label}\n${Format.dateTime(r.timeMs)}"
                })
            }
        }

        if (target != null) {
            val center = GeoPoint(target.lat, target.lon)
            mv.overlays.add(Polygon(mv).apply {
                points = Polygon.pointsAsCircle(center, target.accuracy.coerceAtMost(500f).toDouble())
                fillPaint.color = 0x330A84FF
                outlinePaint.color = 0x990A84FF.toInt()
                outlinePaint.strokeWidth = 2f
            })
            mv.overlays.add(Marker(mv).apply {
                position = center
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = ContextCompat.getDrawable(context, R.drawable.marker_latest)
                title = "${target.source.label}\n${Format.dateTime(target.timeMs)}\n±${target.accuracy.toInt()} מ׳"
            })
        }

        if (phone != null) {
            mv.overlays.add(Marker(mv).apply {
                position = GeoPoint(phone.latitude, phone.longitude)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = ContextCompat.getDrawable(context, R.drawable.marker_phone)
                title = "אתה כאן"
            })
        }

        val key = target?.timeMs ?: phone?.let { -1L }
        if (key != null && key != memo.centeredKey) {
            memo.centeredKey = key
            val p = target?.let { GeoPoint(it.lat, it.lon) }
                ?: GeoPoint(phone!!.latitude, phone.longitude)
            mv.controller.setZoom(17.5)
            mv.controller.setCenter(p)
        }
        mv.invalidate()
    }
}
