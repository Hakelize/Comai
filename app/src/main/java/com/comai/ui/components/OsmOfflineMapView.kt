package com.comai.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * Compose wrapper for osmdroid MapView.
 *
 * Library: osmdroid 6.1.20 (Apache 2.0)
 * Map data: (c) OpenStreetMap contributors (ODbL) - https://www.openstreetmap.org/copyright
 *
 * Offline behavior:
 *   - Tiles downloaded when internet is available are cached in app-specific storage.
 *   - When offline, previously cached tiles render normally.
 *   - When no tiles are cached for the current area, gray tile placeholders appear.
 *
 * Lifecycle:
 *   - MapView.onResume() / onPause() follow the host Composable's lifecycle.
 *   - DisposableEffect ensures MapView.onDetach() is called when the Composable leaves
 *     composition, preventing memory leaks and duplicate map instances.
 *
 * @param latitude   Current latitude (null = no fix yet, shows world view)
 * @param longitude  Current longitude
 * @param modifier   Compose Modifier for sizing
 * @param zoom       Zoom level when a fix is present (15 = neighbourhood detail)
 */
@Composable
fun OsmOfflineMapView(
    latitude: Double?,
    longitude: Double?,
    modifier: Modifier = Modifier,
    zoom: Double = 15.0
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Single MapView instance - NOT recreated on recomposition
    val mapView = remember { createOsmMapView(context) }

    // Follow host lifecycle: pause/resume tile fetching and GPS overlay
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE  -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.overlays.clear()
            mapView.onDetach()
        }
    }

    // Re-center and update marker whenever the location fix changes
    LaunchedEffect(latitude, longitude) {
        if (latitude != null && longitude != null) {
            val geoPoint = GeoPoint(latitude, longitude)
            mapView.controller.setZoom(zoom)
            mapView.controller.animateTo(geoPoint)

            // Replace any existing "You" marker
            mapView.overlays.removeAll { it is Marker }
            val marker = Marker(mapView).apply {
                position = geoPoint
                title = "You"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            mapView.overlays.add(marker)
            mapView.invalidate()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { /* location updates driven by LaunchedEffect */ }
    )
}

/**
 * Builds and configures a fresh osmdroid MapView.
 * Called exactly once via [remember].
 */
private fun createOsmMapView(context: Context): MapView {
    // Safety net: ensure configuration exists even if Application.onCreate order varies
    Configuration.getInstance().apply {
        if (userAgentValue.isBlank()) userAgentValue = context.packageName
        if (osmdroidTileCache == null) {
            osmdroidTileCache = java.io.File(context.getExternalFilesDir(null), "osmdroid/tiles")
        }
    }

    return MapView(context).apply {
        // Standard OpenStreetMap Mapnik tiles
        // Attribution "(c) OpenStreetMap contributors" is embedded by osmdroid's tile renderer
        setTileSource(TileSourceFactory.MAPNIK)

        // Built-in zoom buttons hidden - multi-touch pinch/zoom enabled instead
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        setMultiTouchControls(true)

        // Zoom boundaries
        minZoomLevel = 4.0
        maxZoomLevel = 19.0

        // Default world view until a GPS fix arrives
        controller.setZoom(4.0)
        controller.setCenter(GeoPoint(20.0, 0.0))

        // Scale tiles to screen DPI for crisp rendering on high-density screens
        isTilesScaledToDpi = true
    }
}
