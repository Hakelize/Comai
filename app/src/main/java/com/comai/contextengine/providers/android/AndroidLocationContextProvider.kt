package com.comai.contextengine.providers.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.comai.contextengine.context.RoutinePlaceManager
import com.comai.contextengine.providers.interfaces.LocationContextProvider
import com.comai.contextengine.providers.models.LocationAvailabilityState
import com.comai.contextengine.providers.models.LocationContextData

/**
 * Native Android implementation for Location / GPS Context.
 * Features:
 * - Safely handles permission denial (never crashes).
 * - Explicitly returns LocationAvailabilityState (PERMISSION_DENIED, LOCATION_DISABLED, NO_FIX_AVAILABLE, AVAILABLE).
 * - Processes routine geofences & nearest routine places on-device without cloud APIs.
 */
class AndroidLocationContextProvider(
    private val context: Context,
    private val routinePlaceManager: RoutinePlaceManager = RoutinePlaceManager()
) : LocationContextProvider {

    override val isAvailable: Boolean
        get() {
            val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            return hasFine || hasCoarse
        }

    override fun getContextData(): LocationContextData {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            Log.d(TAG, "Location permission not granted")
            return LocationContextData(
                isPermissionGranted = false,
                isLocationEnabled = false,
                availabilityState = LocationAvailabilityState.PERMISSION_DENIED,
                locationCategory = "UNKNOWN"
            )
        }

        return try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            val isGpsEnabled = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) ?: false
            val isNetEnabled = locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ?: false

            if (!isGpsEnabled && !isNetEnabled) {
                Log.d(TAG, "Location providers disabled in device settings")
                return LocationContextData(
                    isPermissionGranted = true,
                    isLocationEnabled = false,
                    availabilityState = LocationAvailabilityState.LOCATION_DISABLED,
                    locationCategory = "UNKNOWN"
                )
            }

            var lastLocation: Location? = null
            if (isGpsEnabled) {
                lastLocation = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            }
            if (lastLocation == null && isNetEnabled) {
                lastLocation = locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            }

            if (lastLocation != null) {
                val lat = lastLocation.latitude
                val lng = lastLocation.longitude
                val currentPlace = routinePlaceManager.findCurrentPlace(lat, lng)
                val nearestPair = routinePlaceManager.findNearestPlace(lat, lng)

                val category = currentPlace?.name ?: if (currentPlace != null) "Home" else "Commute"

                LocationContextData(
                    latitude = lat,
                    longitude = lng,
                    locationCategory = category,
                    accuracyMeters = lastLocation.accuracy,
                    isPermissionGranted = true,
                    isLocationEnabled = true,
                    availabilityState = LocationAvailabilityState.AVAILABLE,
                    currentPlace = currentPlace,
                    nearestKnownRoutinePlace = nearestPair?.first,
                    distanceToNearestRoutinePlaceMeters = nearestPair?.second
                )
            } else {
                Log.d(TAG, "Location fix pending / unavailable")
                LocationContextData(
                    isPermissionGranted = true,
                    isLocationEnabled = true,
                    availabilityState = LocationAvailabilityState.NO_FIX_AVAILABLE,
                    locationCategory = "Home" // Graceful baseline when fix is pending
                )
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException accessing LocationManager: ${e.message}")
            LocationContextData(
                isPermissionGranted = false,
                isLocationEnabled = false,
                availabilityState = LocationAvailabilityState.PERMISSION_DENIED,
                locationCategory = "UNKNOWN"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring location data", e)
            LocationContextData(
                isPermissionGranted = true,
                isLocationEnabled = false,
                availabilityState = LocationAvailabilityState.NO_FIX_AVAILABLE,
                locationCategory = "UNKNOWN"
            )
        }
    }

    companion object {
        private const val TAG = "AndroidLocationProvider"
    }
}
