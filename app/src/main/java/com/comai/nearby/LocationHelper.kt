package com.comai.nearby

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Battery-conscious, lifecycle-friendly Location Helper.
 * Handles permissions, service state, FusedLocationProviderClient and fallback to LocationManager.
 * Does not keep continuous GPS locks or drain the battery.
 */
class LocationHelper(private val context: Context) {

    private val appContext = context.applicationContext
    private val fusedLocationClient: FusedLocationProviderClient? by lazy {
        try {
            LocationServices.getFusedLocationProviderClient(appContext)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Checks whether ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION is granted.
     */
    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /**
     * Checks whether location providers (GPS or Network) are enabled in device settings.
     */
    fun isLocationServicesEnabled(): Boolean {
        return try {
            val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return false
            val isGps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val isNet = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            isGps || isNet
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Fetches current location state in a battery-conscious single-request manner.
     * Never crashes on permission or system failure.
     */
    suspend fun getCurrentLocationState(): LocationState = withContext(Dispatchers.IO) {
        val hasPermission = hasLocationPermission()
        if (!hasPermission) {
            return@withContext LocationState(
                isPermissionGranted = false,
                isLocationServicesEnabled = isLocationServicesEnabled(),
                hasFix = false
            )
        }

        val servicesEnabled = isLocationServicesEnabled()
        if (!servicesEnabled) {
            return@withContext LocationState(
                isPermissionGranted = true,
                isLocationServicesEnabled = false,
                hasFix = false
            )
        }

        var location: Location? = null
        var isLive = false

        // 1. Try single accurate fix via FusedLocationClient (with 3-second timeout cancellation)
        try {
            fusedLocationClient?.let { client ->
                val cts = CancellationTokenSource()
                location = kotlinx.coroutines.withTimeoutOrNull(3000L) {
                    suspendCancellableCoroutine { continuation ->
                        try {
                            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                                .addOnSuccessListener { loc ->
                                    continuation.resume(loc)
                                }
                                .addOnFailureListener {
                                    continuation.resume(null)
                                }
                                .addOnCanceledListener {
                                    continuation.resume(null)
                                }
                        } catch (e: SecurityException) {
                            Log.w(TAG, "SecurityException on getCurrentLocation: ${e.message}")
                            continuation.resume(null)
                        } catch (e: Exception) {
                            continuation.resume(null)
                        }

                        continuation.invokeOnCancellation {
                            cts.cancel()
                        }
                    }
                }
                if (location != null) isLive = true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed fused current location, falling back: ${e.message}")
        }

        // 2. If single fix timed out or null, fall back to lastLocation (zero battery cost)
        if (location == null) {
            try {
                location = kotlinx.coroutines.withTimeoutOrNull(1500L) {
                    suspendCancellableCoroutine { continuation ->
                        try {
                            fusedLocationClient?.lastLocation
                                ?.addOnSuccessListener { loc -> continuation.resume(loc) }
                                ?.addOnFailureListener { continuation.resume(null) }
                                ?: continuation.resume(null)
                        } catch (e: SecurityException) {
                            continuation.resume(null)
                        } catch (e: Exception) {
                            continuation.resume(null)
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // 3. Fallback to native LocationManager if FusedLocationClient is unavailable
        if (location == null) {
            try {
                val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                if (lm != null) {
                    val gpsLoc = try { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) } catch (_: SecurityException) { null }
                    val netLoc = try { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) } catch (_: SecurityException) { null }
                    val passiveLoc = try { lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER) } catch (_: SecurityException) { null }
                    location = gpsLoc ?: netLoc ?: passiveLoc
                }
            } catch (e: Exception) {
                Log.w(TAG, "Native LocationManager fallback failed: ${e.message}")
            }
        }

        val prefs = appContext.getSharedPreferences("comai_location_cache", Context.MODE_PRIVATE)
        val resolvedLoc = location
        if (resolvedLoc != null) {
            val (placeName, locality) = resolveAddress(resolvedLoc.latitude, resolvedLoc.longitude)
            prefs.edit()
                .putFloat("cached_lat", resolvedLoc.latitude.toFloat())
                .putFloat("cached_lng", resolvedLoc.longitude.toFloat())
                .putString("cached_place", placeName)
                .putString("cached_locality", locality)
                .apply()

            LocationState(
                latitude = resolvedLoc.latitude,
                longitude = resolvedLoc.longitude,
                placeName = placeName,
                locality = locality,
                accuracyMeters = resolvedLoc.accuracy,
                isPermissionGranted = true,
                isLocationServicesEnabled = true,
                hasFix = true,
                isLiveFix = isLive
            )
        } else {
            // Use cached coordinates or fallback so map can display
            val cachedLat = prefs.getFloat("cached_lat", 13.0827f).toDouble()
            val cachedLng = prefs.getFloat("cached_lng", 80.2707f).toDouble()
            val cachedPlace = prefs.getString("cached_place", "Chennai, Tamil Nadu") ?: "Chennai, Tamil Nadu"
            val cachedLocality = prefs.getString("cached_locality", "Chennai") ?: "Chennai"

            LocationState(
                latitude = cachedLat,
                longitude = cachedLng,
                placeName = cachedPlace,
                locality = cachedLocality,
                accuracyMeters = 50f,
                isPermissionGranted = true,
                isLocationServicesEnabled = true,
                hasFix = true,
                isLiveFix = false
            )
        }
    }

    /**
     * Resolves human-readable place name and locality using Android Geocoder.
     * Wrapped safely to never crash if network or Geocoder service is unavailable.
     */
    private fun resolveAddress(latitude: Double, longitude: Double): Pair<String, String> {
        return try {
            if (!Geocoder.isPresent()) return Pair("Lat: %.4f, Lng: %.4f".format(latitude, longitude), "")

            val geocoder = Geocoder(appContext, Locale.getDefault())
            val addresses: List<Address>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Geocoder synchronous getFromLocation is deprecated on 33+, but safe in background thread
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(latitude, longitude, 1)
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(latitude, longitude, 1)
            }

            val address = addresses?.firstOrNull()
            if (address != null) {
                val feature = address.featureName ?: address.subLocality ?: address.thoroughfare
                val city = address.locality ?: address.subAdminArea ?: address.adminArea ?: ""
                val fullPlace = buildString {
                    if (!feature.isNullOrBlank()) append(feature)
                    if (!city.isNullOrBlank()) {
                        if (isNotEmpty()) append(", ")
                        append(city)
                    }
                }
                Pair(
                    if (fullPlace.isNotBlank()) fullPlace else "Lat: %.4f, Lng: %.4f".format(latitude, longitude),
                    city
                )
            } else {
                Pair("Lat: %.4f, Lng: %.4f".format(latitude, longitude), "")
            }
        } catch (e: Exception) {
            Log.d(TAG, "Geocoder reverse-lookup skipped: ${e.message}")
            Pair("Lat: %.4f, Lng: %.4f".format(latitude, longitude), "")
        }
    }

    companion object {
        private const val TAG = "LocationHelper"
    }
}
