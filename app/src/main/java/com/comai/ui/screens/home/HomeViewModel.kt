package com.comai.ui.screens.home

import androidx.lifecycle.ViewModel
import com.comai.ui.screens.onboarding.OnboardingPreferences
import com.comai.ui.screens.onboarding.UserOnboardingProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar

data class RoutineMilestone(
    val id: String,
    val title: String,
    val time: String,
    val subtitle: String,
    val isPast: Boolean = false,
    val isCurrent: Boolean = false
)

data class HomeUiState(
    val profile: UserOnboardingProfile = UserOnboardingProfile(),
    val greeting: String = "Good day",
    val contextualHeadline: String = "Your routine is running smoothly.",
    val currentContextTitle: String = "Right Now",
    val currentContextStatus: String = "At Home",
    val currentContextDetail: String = "Next: Usual departure scheduled",
    val milestones: List<RoutineMilestone> = emptyList(),
    val currentMilestoneIndex: Int = 0
)

class HomeViewModel(
    private val preferences: OnboardingPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(buildHomeState(preferences.getProfile()))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun refreshState() {
        val profile = preferences.getProfile()
        _uiState.value = buildHomeState(profile)
    }

    companion object {
        fun buildHomeState(profile: UserOnboardingProfile, calendar: Calendar = Calendar.getInstance()): HomeUiState {
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)
            val currentMinutes = hour * 60 + minute

            val userName = profile.name.trim().ifBlank { "there" }

            val timeOfDayGreeting = when {
                hour < 12 -> "Good morning"
                hour < 17 -> "Good afternoon"
                else -> "Good evening"
            }
            val greeting = "$timeOfDayGreeting, $userName"

            val wakeMinutes = parseTimeToMinutes(profile.wakeTime, default = 7 * 60)
            val leaveMinutes = parseTimeToMinutes(profile.leaveHomeTime, default = 8 * 60 + 30)
            val returnMinutes = parseTimeToMinutes(profile.returnHomeTime, default = 18 * 60)
            val sleepMinutes = parseTimeToMinutes(profile.sleepTime, default = 23 * 60)

            val placeLabel = profile.placeName.ifBlank {
                profile.workplace.ifBlank { profile.college.ifBlank { "your workspace" } }
            }

            val (headline, contextTitle, contextStatus, contextDetail, activeIndex) = when {
                currentMinutes < wakeMinutes -> {
                    StateTuple(
                        headline = "Resting ahead of your daily schedule.",
                        contextTitle = "Right Now",
                        contextStatus = "Resting",
                        contextDetail = "Next: Wake up scheduled for ${formatDisplayTime(profile.wakeTime, "07:00")}",
                        activeIndex = 0
                    )
                }
                currentMinutes < leaveMinutes -> {
                    StateTuple(
                        headline = "Preparing for your day at $placeLabel.",
                        contextTitle = "Morning Routine",
                        contextStatus = "At Home",
                        contextDetail = "Next: Departure for $placeLabel at ${formatDisplayTime(profile.leaveHomeTime, "08:30")}",
                        activeIndex = 0
                    )
                }
                currentMinutes < (leaveMinutes + 45) -> {
                    StateTuple(
                        headline = "Commute in progress via ${profile.travelMode}.",
                        contextTitle = "In Transit",
                        contextStatus = "Heading to $placeLabel",
                        contextDetail = "Travel mode: ${profile.travelMode}",
                        activeIndex = 1
                    )
                }
                currentMinutes < returnMinutes -> {
                    StateTuple(
                        headline = "Focus session at $placeLabel.",
                        contextTitle = "Day Schedule",
                        contextStatus = "At $placeLabel",
                        contextDetail = "Expected departure: ${formatDisplayTime(profile.returnHomeTime, "18:00")}",
                        activeIndex = 2
                    )
                }
                currentMinutes < (returnMinutes + 60) -> {
                    StateTuple(
                        headline = "Returning home from $placeLabel.",
                        contextTitle = "Evening Commute",
                        contextStatus = "Heading Home",
                        contextDetail = "Travel mode: ${profile.travelMode}",
                        activeIndex = 3
                    )
                }
                currentMinutes < sleepMinutes -> {
                    StateTuple(
                        headline = "Winding down after your day.",
                        contextTitle = "Evening Routine",
                        contextStatus = "At Home",
                        contextDetail = "Next: Rest scheduled for ${formatDisplayTime(profile.sleepTime, "23:00")}",
                        activeIndex = 3
                    )
                }
                else -> {
                    StateTuple(
                        headline = "Rest period for recovery.",
                        contextTitle = "Night",
                        contextStatus = "Resting",
                        contextDetail = "Sleep schedule active",
                        activeIndex = 4
                    )
                }
            }

            val milestones = listOf(
                RoutineMilestone(
                    id = "wake",
                    title = "Wake Up",
                    time = formatDisplayTime(profile.wakeTime, "07:00"),
                    subtitle = "Morning baseline",
                    isPast = currentMinutes > wakeMinutes,
                    isCurrent = activeIndex == 0
                ),
                RoutineMilestone(
                    id = "leave",
                    title = "Departure",
                    time = formatDisplayTime(profile.leaveHomeTime, "08:30"),
                    subtitle = "Commute via ${profile.travelMode}",
                    isPast = currentMinutes > leaveMinutes,
                    isCurrent = activeIndex == 1
                ),
                RoutineMilestone(
                    id = "work",
                    title = if (profile.weekdayType == "College") "Campus" else "Workspace",
                    time = "${formatDisplayTime(profile.leaveHomeTime, "08:30")} - ${formatDisplayTime(profile.returnHomeTime, "18:00")}",
                    subtitle = placeLabel,
                    isPast = currentMinutes > returnMinutes,
                    isCurrent = activeIndex == 2
                ),
                RoutineMilestone(
                    id = "return",
                    title = "Return Home",
                    time = formatDisplayTime(profile.returnHomeTime, "18:00"),
                    subtitle = "Evening arrival",
                    isPast = currentMinutes > (returnMinutes + 60),
                    isCurrent = activeIndex == 3
                ),
                RoutineMilestone(
                    id = "sleep",
                    title = "Rest",
                    time = formatDisplayTime(profile.sleepTime, "23:00"),
                    subtitle = "Wind down & recharge",
                    isPast = false,
                    isCurrent = activeIndex == 4
                )
            )

            return HomeUiState(
                profile = profile,
                greeting = greeting,
                contextualHeadline = headline,
                currentContextTitle = contextTitle,
                currentContextStatus = contextStatus,
                currentContextDetail = contextDetail,
                milestones = milestones,
                currentMilestoneIndex = activeIndex
            )
        }

        private data class StateTuple(
            val headline: String,
            val contextTitle: String,
            val contextStatus: String,
            val contextDetail: String,
            val activeIndex: Int
        )

        private fun parseTimeToMinutes(timeStr: String, default: Int): Int {
            if (timeStr.isBlank()) return default
            val clean = timeStr.trim().uppercase()
            val isPm = clean.contains("PM")
            val isAm = clean.contains("AM")
            val timePart = clean.replace("AM", "").replace("PM", "").trim()
            val parts = timePart.split(":")
            if (parts.size >= 2) {
                var h = parts[0].toIntOrNull() ?: return default
                val m = parts[1].toIntOrNull() ?: return default
                if (isPm && h < 12) h += 12
                if (isAm && h == 12) h = 0
                return h * 60 + m
            }
            return default
        }

        private fun formatDisplayTime(timeStr: String, fallback: String): String {
            return timeStr.trim().ifBlank { fallback }
        }
    }
}
