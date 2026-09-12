package com.comai.contextengine.providers.android

import com.comai.contextengine.providers.interfaces.GeofenceContextProvider
import com.comai.contextengine.providers.interfaces.LocationContextProvider
import com.comai.contextengine.providers.models.GeofenceContextData

/**
 * Android implementation for Geofence Context. Derives zone boundaries locally from LocationContextProvider on-device.
 */
class AndroidGeofenceContextProvider(
    private val locationContextProvider: LocationContextProvider
) : GeofenceContextProvider {

    override val isAvailable: Boolean
        get() = locationContextProvider.isAvailable

    override fun getContextData(): GeofenceContextData {
        val locationData = locationContextProvider.getContextData()
        val currentPlace = locationData.currentPlace

        if (!locationData.isPermissionGranted || locationData.latitude == null || locationData.longitude == null) {
            return GeofenceContextData(
                currentZone = "UNKNOWN",
                isInsideGeofence = false,
                currentPlace = null,
                distanceToNearestZoneMeters = locationData.distanceToNearestRoutinePlaceMeters
            )
        }

        val zoneName = when (currentPlace?.category) {
            "HOME" -> "HOME_ZONE"
            "OFFICE" -> "OFFICE_ZONE"
            "GYM" -> "GYM_ZONE"
            else -> if (currentPlace != null) "${currentPlace.name.uppercase()}_ZONE" else "UNKNOWN"
        }

        return GeofenceContextData(
            currentZone = zoneName,
            isInsideGeofence = currentPlace != null,
            currentPlace = currentPlace,
            distanceToNearestZoneMeters = locationData.distanceToNearestRoutinePlaceMeters
        )
    }
}
