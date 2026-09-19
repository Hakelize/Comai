package com.comai.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.comai.ui.theme.ElectricTeal
import com.comai.ui.theme.TextSecondary
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.util.Locale

/**
 * Crash-resilient Compose wrapper for osmdroid MapView.
 *
 * Library: osmdroid 6.1.20 (Apache 2.0)
 * Map data: (c) OpenStreetMap contributors (ODbL)
 *
 * Resilient Architecture:
 * - Uses applicationContext to avoid ContextWrapper / Theme issues.
 * - Handles factory, update, and onRelease safely within AndroidView.
 * - Never re-uses a detached MapView (prevents NullPointerException in TileProvider).
 * - Comprehensive fallback UI if native mapping fails on specific hardware.
 */
@Composable
fun OsmOfflineMapView(
    latitude: Double?,
    longitude: Double?,
    modifier: Modifier = Modifier,
    zoom: Double = 15.0
) {
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    var hasMapFailed by remember { mutableStateOf(false) }

    if (hasMapFailed) {
        MapFallbackPlaceholder(latitude = latitude, longitude = longitude, modifier = modifier)
        return
    }

    AndroidView(
        factory = {
            try {
                createOsmMapView(appContext)
            } catch (t: Throwable) {
                android.util.Log.e("OsmOfflineMapView", "Failed to construct osmdroid MapView", t)
                hasMapFailed = true
                android.view.View(appContext)
            }
        },
        modifier = modifier,
        update = { view ->
            if (view is MapView) {
                try {
                    val validLat = latitude?.takeIf { !it.isNaN() && !it.isInfinite() } ?: 13.0827
                    val validLng = longitude?.takeIf { !it.isNaN() && !it.isInfinite() } ?: 80.2707
                    val geoPoint = GeoPoint(validLat, validLng)

                    view.controller.setZoom(zoom)
                    view.controller.setCenter(geoPoint)

                    if (latitude != null && longitude != null && !latitude.isNaN() && !longitude.isNaN()) {
                        view.overlays.removeAll { it is Marker }
                        val marker = Marker(view).apply {
                            position = geoPoint
                            title = "You Are Here"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        }
                        view.overlays.add(marker)
                    }
                    view.invalidate()
                } catch (t: Throwable) {
                    android.util.Log.w("OsmOfflineMapView", "Error updating MapView: ${t.message}")
                }
            }
        },
        onRelease = { view ->
            try {
                if (view is MapView) {
                    view.overlays.clear()
                    view.onDetach()
                }
            } catch (t: Throwable) {
                android.util.Log.w("OsmOfflineMapView", "Error releasing MapView: ${t.message}")
            }
        }
    )
}

/**
 * Builds and configures an osmdroid MapView instance safely using applicationContext.
 */
private fun createOsmMapView(appContext: Context): MapView {
    try {
        val osmBase = java.io.File(appContext.filesDir, "osmdroid").apply { if (!exists()) mkdirs() }
        val osmCache = java.io.File(appContext.cacheDir, "osmdroid_tiles").apply { if (!exists()) mkdirs() }

        Configuration.getInstance().apply {
            load(appContext, appContext.getSharedPreferences("osmdroid_prefs", Context.MODE_PRIVATE))
            osmdroidBasePath = osmBase
            osmdroidTileCache = osmCache
            userAgentValue = "ComaiApp/1.0 (Android; ${appContext.packageName})"
        }
    } catch (e: Throwable) {
        android.util.Log.w("OsmOfflineMapView", "Error configuring osmdroid: ${e.message}")
    }

    return MapView(appContext).apply {
        setTileSource(TileSourceFactory.MAPNIK)
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        setMultiTouchControls(true)
        minZoomLevel = 3.0
        maxZoomLevel = 19.0
        controller.setZoom(14.0)
        controller.setCenter(GeoPoint(13.0827, 80.2707))
        isTilesScaledToDpi = true
    }
}

/**
 * Clean visual fallback card shown if native OpenStreetMap rendering encounters any system issue.
 */
@Composable
private fun MapFallbackPlaceholder(
    latitude: Double?,
    longitude: Double?,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF131A26),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1F2B3E)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = ElectricTeal,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Location Coordinates",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (latitude != null && longitude != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = String.format(Locale.US, "%.4f° N, %.4f° E", latitude, longitude),
                    color = ElectricTeal,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Map view active in background",
                color = TextSecondary,
                fontSize = 11.sp
            )
        }
    }
}
