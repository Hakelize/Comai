package com.comai.ui.screens.home

import com.comai.ui.screens.onboarding.OnboardingPreferences
import com.comai.ui.screens.onboarding.UserOnboardingProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class HomeViewModelTest {

    private class FakeOnboardingPreferences : OnboardingPreferences(null as android.content.Context?) {
        private var _profile = UserOnboardingProfile()

        override fun getProfile(): UserOnboardingProfile = _profile

        override fun saveProfile(profile: UserOnboardingProfile) {
            _profile = profile
        }

        fun setFakeProfile(profile: UserOnboardingProfile) {
            _profile = profile
        }
    }

    private fun createCalendar(hour: Int, minute: Int): Calendar {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal
    }

    @Test
    fun `greeting changes with time of day and incorporates user name`() {
        val profile = UserOnboardingProfile(name = "Viswa")

        val morningCal = createCalendar(9, 30)
        val morningState = HomeViewModel.buildHomeState(profile, morningCal)
        assertEquals("Good morning, Viswa", morningState.greeting)

        val afternoonCal = createCalendar(14, 15)
        val afternoonState = HomeViewModel.buildHomeState(profile, afternoonCal)
        assertEquals("Good afternoon, Viswa", afternoonState.greeting)

        val eveningCal = createCalendar(20, 0)
        val eveningState = HomeViewModel.buildHomeState(profile, eveningCal)
        assertEquals("Good evening, Viswa", eveningState.greeting)
    }

    @Test
    fun `greeting falls back to time of day when name is empty`() {
        val profile = UserOnboardingProfile(name = "")
        val morningCal = createCalendar(8, 0)
        val state = HomeViewModel.buildHomeState(profile, morningCal)
        assertEquals("Good morning", state.greeting)
    }

    @Test
    fun `milestones adapt to user schedule`() {
        val workProfile = UserOnboardingProfile(
            name = "Sarah",
            weekdayType = "Work",
            workplace = "Acme Corp",
            wakeTime = "06:30 AM",
            leaveHomeTime = "08:15 AM",
            returnHomeTime = "06:00 PM",
            sleepTime = "10:30 PM",
            travelMode = "Metro"
        )

        val calMorning = createCalendar(7, 0)
        val state = HomeViewModel.buildHomeState(workProfile, calMorning)

        assertEquals(5, state.milestones.size)
        assertEquals("Wake Up", state.milestones[0].title)
        assertEquals("06:30 AM", state.milestones[0].time)
        assertEquals("Departure", state.milestones[1].title)
        assertEquals("08:15 AM", state.milestones[1].time)
        assertTrue(state.milestones[1].subtitle.contains("Metro"))

        // Wake milestone is past (07:00 > 06:30), departure is current (morning routine)
        assertTrue(state.milestones[0].isPast)
    }

    @Test
    fun `college user displays campus milestones`() {
        val collegeProfile = UserOnboardingProfile(
            name = "Karthik",
            weekdayType = "College",
            college = "IIT Madras",
            wakeTime = "07:00 AM",
            leaveHomeTime = "08:30 AM",
            returnHomeTime = "05:30 PM",
            sleepTime = "11:00 PM"
        )

        val calAtCampus = createCalendar(12, 0)
        val state = HomeViewModel.buildHomeState(collegeProfile, calAtCampus)

        // weekdayType == College produces "Campus" for the workspace milestone
        assertEquals("Campus", state.milestones[2].title)
        // placeLabel should be "IIT Madras" from college field
        assertEquals("At IIT Madras", state.currentContextStatus)
        assertTrue(state.milestones[0].isPast) // wake at 07:00
        assertTrue(state.milestones[1].isPast) // depart at 08:30
    }

    @Test
    fun `refreshState loads updated profile from preferences`() {
        val fakePrefs = FakeOnboardingPreferences()
        fakePrefs.setFakeProfile(UserOnboardingProfile(name = "Initial Name"))

        val viewModel = HomeViewModel(fakePrefs)
        assertTrue(viewModel.uiState.value.greeting.contains("Initial Name"))

        fakePrefs.setFakeProfile(UserOnboardingProfile(name = "Updated Name"))
        viewModel.refreshState()
        assertTrue(viewModel.uiState.value.greeting.contains("Updated Name"))
    }
}
