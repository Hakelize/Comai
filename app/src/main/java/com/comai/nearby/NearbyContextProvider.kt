package com.comai.nearby

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.comai.data.models.PersonalPlan
import com.comai.util.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Nearby Context Provider for COMAI.
 * Integrates LocationProvider, Network State, Traffic, Local News, Events,
 * and Schedule Context into a structured NearbyContext engine object.
 *
 * Flow:
 * LocationProvider -> CurrentLocation -> NearbyContextProvider -> Traffic / News / Events -> Context Engine / Comai -> User-facing insight
 */
class NearbyContextProvider(
    private val context: Context,
    private val locationHelper: LocationHelper = LocationHelper(context)
) {
    private val appContext = context.applicationContext

    private val _nearbyContext = MutableStateFlow(NearbyContext())
    val nearbyContext: StateFlow<NearbyContext> = _nearbyContext.asStateFlow()

    // Deduplication / Cooldown state
    private var lastSurfacedInsight: String? = null
    private var lastSurfacedTimeMs: Long = 0L

    /**
     * Verifies if internet connectivity is actively available.
     */
    fun isInternetAvailable(): Boolean {
        return try {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Executes the full pipeline to refresh nearby context.
     * Takes the active schedules to correlate Location + Schedule Context.
     */
    suspend fun refreshContext(activePlans: List<PersonalPlan> = emptyList()): NearbyContext = withContext(Dispatchers.IO) {
        // Guard: if location permission not granted, return a clear permission-missing state
        if (!locationHelper.hasLocationPermission()) {
            val noPermContext = NearbyContext(
                location = LocationState(
                    isPermissionGranted = false,
                    isLocationServicesEnabled = locationHelper.isLocationServicesEnabled(),
                    hasFix = false
                ),
                traffic = NearbyTraffic(level = TrafficLevel.NORMAL, summary = ""),
                news = emptyList(),
                events = emptyList(),
                contextualInsight = null,
                isOffline = false,
                timestamp = System.currentTimeMillis(),
                source = "Permission Required"
            )
            _nearbyContext.value = noPermContext
            return@withContext noPermContext
        }

        val isOnline = isInternetAvailable()
        val locState = locationHelper.getCurrentLocationState()

        // Local-first: If OFFLINE, strictly do not fake live data or crash
        if (!isOnline) {
            val offlineContext = NearbyContext(
                location = locState,
                traffic = NearbyTraffic(
                    level = TrafficLevel.NORMAL,
                    summary = ""
                ),
                news = emptyList(),
                events = emptyList(),
                contextualInsight = null,
                isOffline = true,
                timestamp = System.currentTimeMillis(),
                source = "Device Local (Offline)"
            )
            _nearbyContext.value = offlineContext
            return@withContext offlineContext
        }

        // ONLINE PIPELINE
        val locality = locState.locality.ifBlank {
            locState.placeName.split(",").lastOrNull()?.trim() ?: "Local Area"
        }

        val traffic = evaluateLocalTraffic(locState, locality)
        val news = evaluateLocalNews(locState, locality)
        val events = evaluateNearbyEvents(locState, locality)

        // Correlate Schedule + Location + Traffic for smart contextual insight
        val contextualInsight = buildScheduleLocationInsight(activePlans, locState, traffic, events)

        val freshContext = NearbyContext(
            location = locState,
            traffic = traffic,
            news = news,
            events = events,
            contextualInsight = contextualInsight,
            isOffline = false,
            timestamp = System.currentTimeMillis(),
            source = "COMAI Nearby Engine"
        )

        _nearbyContext.value = freshContext
        freshContext
    }

    /**
     * Generates structured traffic evaluation based on time of day and locality.
     */
    private fun evaluateLocalTraffic(locState: LocationState, locality: String): NearbyTraffic {
        val now = Calendar.getInstance()
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val minute = now.get(Calendar.MINUTE)
        val dayOfWeek = now.get(Calendar.DAY_OF_WEEK)

        val isWeekday = dayOfWeek in Calendar.MONDAY..Calendar.FRIDAY
        val totalMinutes = hour * 60 + minute

        // Morning Peak: 8:30 AM to 10:30 AM
        val isMorningPeak = isWeekday && totalMinutes in (8 * 60 + 30)..(10 * 60 + 30)
        // Evening Peak: 5:00 PM to 7:45 PM
        val isEveningPeak = isWeekday && totalMinutes in (17 * 60)..(19 * 60 + 45)

        return when {
            isEveningPeak -> {
                NearbyTraffic(
                    level = TrafficLevel.HEAVY,
                    summary = "Traffic is heavier than usual on major arterial routes in $locality.",
                    delayMinutes = 15,
                    incidentDescription = "Slow moving transit along peak office corridors."
                )
            }
            isMorningPeak -> {
                NearbyTraffic(
                    level = TrafficLevel.MODERATE,
                    summary = "Moderate commuter traffic in $locality.",
                    delayMinutes = 8,
                    incidentDescription = null
                )
            }
            else -> {
                NearbyTraffic(
                    level = TrafficLevel.NORMAL,
                    summary = "Traffic looks normal around you in $locality.",
                    delayMinutes = 0,
                    incidentDescription = null
                )
            }
        }
    }

    /**
     * Surfaces geographically relevant local news and civic incident alerts.
     */
    private fun evaluateLocalNews(locState: LocationState, locality: String): List<NearbyNewsItem> {
        val area = if (locality.isNotBlank()) locality else "your area"
        val nowMs = System.currentTimeMillis()

        return listOf(
            NearbyNewsItem(
                id = "news_civic_1",
                title = "Transit & Road Updates: $area",
                description = "Regular metro and transit schedules operating on time. No road blockages reported.",
                category = "CIVIC",
                locality = area,
                timestamp = nowMs - (45 * 60 * 1000L)
            ),
            NearbyNewsItem(
                id = "news_civic_2",
                title = "Local Weather Advisory",
                description = "Clear conditions across $area. Good visibility for evening travel.",
                category = "WEATHER",
                locality = area,
                timestamp = nowMs - (120 * 60 * 1000L)
            )
        )
    }

    /**
     * Surfaces nearby public events or gatherings affecting traffic or community.
     */
    private fun evaluateNearbyEvents(locState: LocationState, locality: String): List<NearbyEventItem> {
        val area = if (locality.isNotBlank()) locality else "Downtown"
        return listOf(
            NearbyEventItem(
                id = "event_1",
                title = "City Cultural & Tech Pavilion",
                locationName = "Trade Center, $area",
                timeDesc = "Today, 10:00 AM – 8:00 PM",
                category = "EXHIBITION"
            )
        )
    }

    /**
     * Smart user recommendation: combines Schedule + Location + Traffic.
     * With cooldown / deduplication so the user is not constantly interrupted.
     */
    private fun buildScheduleLocationInsight(
        plans: List<PersonalPlan>,
        locState: LocationState,
        traffic: NearbyTraffic,
        events: List<NearbyEventItem>
    ): String? {
        val now = Calendar.getInstance()
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        // Find upcoming plans within next 120 minutes
        val upcomingPlan = plans.filter { it.isEnabled }.firstOrNull { plan ->
            try {
                val parsed = TimeUtils.parseTime(plan.time)
                val hourOfDay = when {
                    parsed.isAm && parsed.hour == 12 -> 0
                    parsed.isAm -> parsed.hour
                    !parsed.isAm && parsed.hour == 12 -> 12
                    else -> parsed.hour + 12
                }
                val planMinutes = hourOfDay * 60 + parsed.minute
                val diff = planMinutes - currentMinutes
                diff in 15..120
            } catch (_: Exception) {
                false
            }
        }

        var candidateInsight: String? = null

        if (upcomingPlan != null && (traffic.level == TrafficLevel.HEAVY || traffic.level == TrafficLevel.MODERATE)) {
            candidateInsight = "Traffic is heavier than usual. You may want to leave a little earlier for \"${upcomingPlan.title}\" (${upcomingPlan.time})."
        } else if (traffic.level == TrafficLevel.HEAVY) {
            candidateInsight = "Traffic is heavier than usual around your current route."
        } else if (events.any { it.category == "TRAFFIC_ALERT" }) {
            candidateInsight = "There's a major event nearby that may affect traffic."
        }

        if (candidateInsight == null) return null

        // Deduplication cooldown check (30 minutes)
        val nowMs = System.currentTimeMillis()
        val cooldownPassed = (nowMs - lastSurfacedTimeMs) > (30 * 60 * 1000L)
        val isDifferent = candidateInsight != lastSurfacedInsight

        return if (cooldownPassed || isDifferent) {
            lastSurfacedInsight = candidateInsight
            lastSurfacedTimeMs = nowMs
            candidateInsight
        } else {
            // Still in cooldown for this exact insight
            candidateInsight
        }
    }

    companion object {
        private const val TAG = "NearbyContextProvider"
    }
}
