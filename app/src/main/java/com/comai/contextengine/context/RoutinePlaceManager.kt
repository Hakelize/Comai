package com.comai.contextengine.context

import com.comai.contextengine.providers.models.RoutinePlace
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Manages user-defined routine places (Home, Office, Gym, Custom) on-device without cloud dependencies.
 */
class RoutinePlaceManager {

    private val routinePlaces = CopyOnWriteArrayList<RoutinePlace>()

    init {
        // Default baseline placeholders for local testing/demo
        routinePlaces.add(
            RoutinePlace(
                id = "home_place",
                name = "Home",
                category = "HOME",
                latitude = 37.7749,
                longitude = -122.4194,
                radiusMeters = 200f
            )
        )
        routinePlaces.add(
            RoutinePlace(
                id = "office_place",
                name = "Office",
                category = "OFFICE",
                latitude = 37.7833,
                longitude = -122.4167,
                radiusMeters = 200f
            )
        )
    }

    fun getAllPlaces(): List<RoutinePlace> = routinePlaces.toList()

    fun addPlace(place: RoutinePlace) {
        routinePlaces.removeAll { it.id == place.id }
        routinePlaces.add(place)
    }

    fun removePlace(placeId: String) {
        routinePlaces.removeAll { it.id == placeId }
    }

    /**
     * Identifies if given coordinates fall inside any registered routine place geofence.
     */
    fun findCurrentPlace(lat: Double, lng: Double): RoutinePlace? {
        for (place in routinePlaces) {
            val dist = calculateDistanceMeters(lat, lng, place.latitude, place.longitude)
            if (dist <= place.radiusMeters) {
                return place
            }
        }
        return null
    }

    /**
     * Finds the nearest registered routine place and returns a Pair(place, distanceInMeters).
     */
    fun findNearestPlace(lat: Double, lng: Double): Pair<RoutinePlace, Float>? {
        if (routinePlaces.isEmpty()) return null

        var minDistance = Float.MAX_VALUE
        var nearest: RoutinePlace? = null

        for (place in routinePlaces) {
            val dist = calculateDistanceMeters(lat, lng, place.latitude, place.longitude)
            if (dist < minDistance) {
                minDistance = dist
                nearest = place
            }
        }

        return if (nearest != null) Pair(nearest, minDistance) else null
    }

    /**
     * Computes Haversine distance in meters between two lat/lng coordinates on-device.
     */
    fun calculateDistanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
        val earthRadiusMeters = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return (earthRadiusMeters * c).toFloat()
    }
}
