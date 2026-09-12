package com.comai.ui.screens.onboarding

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.comai.voice.ComaiLanguage
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Physical device instrumented tests running on the actual connected Android device.
 * Tests real Android SharedPreferences persistence and validation behaviors.
 */
@RunWith(AndroidJUnit4::class)
class OnboardingDeviceValidationTest {

    private lateinit var preferences: OnboardingPreferences
    private lateinit var viewModel: OnboardingViewModel

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        preferences = OnboardingPreferences(context)
        preferences.reset()
        viewModel = OnboardingViewModel(preferences, null)
    }

    @Test
    fun testPhysicalDevice_FreshStateNotCompleted() {
        assertFalse("Fresh state must not be marked completed", preferences.isOnboardingCompleted())
        val profile = preferences.getProfile()
        assertEquals("", profile.name)
        assertEquals("", profile.workplace)
        assertEquals("", profile.wakeTime)
        assertEquals("", profile.sleepTime)
    }

    @Test
    fun testPhysicalDevice_EmptyProfileSubmissionBlocked() {
        // Step 1 -> Step 4 -> PROFILE_AND_ROUTINE
        viewModel.nextStep() // WHAT_COMAI_DOES
        viewModel.nextStep() // CHOOSE_LANGUAGE
        viewModel.nextStep() // VOICE_SETUP
        viewModel.nextStep() // PROFILE_AND_ROUTINE

        // Attempt proceed with all fields empty
        viewModel.validateProfileAndProceed()

        // MUST stay on PROFILE_AND_ROUTINE
        assertEquals(OnboardingStep.PROFILE_AND_ROUTINE, viewModel.uiState.value.currentStep)
        assertFalse(preferences.isOnboardingCompleted())

        // Required errors must be present
        val state = viewModel.uiState.value
        assertEquals("Please enter your name", state.nameError)
        assertEquals("Please enter your workplace / office name", state.workplaceError)
        assertEquals("Please enter wake-up time", state.wakeTimeError)
        assertEquals("Please enter departure time", state.leaveHomeTimeError)
        assertEquals("Please enter return time", state.returnHomeTimeError)
        assertEquals("Please enter sleep time", state.sleepTimeError)
        assertEquals("Just a few details are needed to personalize Comai.", state.generalError)
    }

    @Test
    fun testPhysicalDevice_PartialSubmissionBlocked() {
        viewModel.updateName("Rakesh")
        assertFalse("Partial submission with only name must fail", viewModel.validateProfile())
        assertNull(viewModel.uiState.value.nameError)
        assertNotNull(viewModel.uiState.value.workplaceError)
        assertNotNull(viewModel.uiState.value.wakeTimeError)
        assertFalse(preferences.isOnboardingCompleted())
    }

    @Test
    fun testPhysicalDevice_ConditionalValidation() {
        // Work requires workplace
        viewModel.updateName("Rakesh")
        viewModel.updateWeekdayType("Work")
        viewModel.updateWakeTime("07:00")
        viewModel.updateLeaveHomeTime("08:30")
        viewModel.updateReturnHomeTime("18:00")
        viewModel.updateSleepTime("23:00")

        assertFalse(viewModel.validateProfile())
        assertEquals("Please enter your workplace / office name", viewModel.uiState.value.workplaceError)

        viewModel.updateWorkplace("Tech Hub Office")
        assertTrue(viewModel.validateProfile())

        // College requires college name
        viewModel.updateWeekdayType("College")
        assertFalse(viewModel.validateProfile())
        assertEquals("Please enter your college name", viewModel.uiState.value.collegeError)

        viewModel.updateCollege("IIT Madras")
        assertTrue(viewModel.validateProfile())

        // Both requires both
        viewModel.updateWeekdayType("Both")
        viewModel.updateWorkplace("Tech Hub Office")
        viewModel.updateCollege("IIT Madras")
        assertTrue(viewModel.validateProfile())

        // Other does not require workplace or college
        viewModel.updateWeekdayType("Other")
        viewModel.updateWorkplace("")
        viewModel.updateCollege("")
        viewModel.updatePlaceName("")
        assertTrue(viewModel.validateProfile())
    }

    @Test
    fun testPhysicalDevice_TimeFormatValidation() {
        assertTrue(OnboardingViewModel.isValidTime("07:00"))
        assertTrue(OnboardingViewModel.isValidTime("7:00"))
        assertTrue(OnboardingViewModel.isValidTime("18:30"))
        assertTrue(OnboardingViewModel.isValidTime("7:00 AM"))
        assertTrue(OnboardingViewModel.isValidTime("11:30 PM"))

        assertFalse(OnboardingViewModel.isValidTime(""))
        assertFalse(OnboardingViewModel.isValidTime("   "))
        assertFalse(OnboardingViewModel.isValidTime("25:00"))
        assertFalse(OnboardingViewModel.isValidTime("12:65"))
        assertFalse(OnboardingViewModel.isValidTime("morning"))
    }

    @Test
    fun testPhysicalDevice_CompleteOnlyAfterValidProfile() {
        // Attempt completion while empty
        var completed = false
        viewModel.completeOnboarding {
            completed = true
        }
        assertFalse(completed)
        assertFalse(preferences.isOnboardingCompleted())

        // Fill all required fields
        viewModel.updateName("Rakesh")
        viewModel.updateWeekdayType("Work")
        viewModel.updateWorkplace("Tech Hub Office")
        viewModel.updateWakeTime("07:00")
        viewModel.updateLeaveHomeTime("08:30")
        viewModel.updateReturnHomeTime("18:00")
        viewModel.updateSleepTime("23:00")
        viewModel.updateTravelMode("Car")
        viewModel.updateLanguage(ComaiLanguage.ENGLISH)

        // Now complete
        var finishedLang: ComaiLanguage? = null
        viewModel.completeOnboarding { lang ->
            finishedLang = lang
            completed = true
        }

        assertTrue("Completion callback must be called", completed)
        assertEquals(ComaiLanguage.ENGLISH, finishedLang)
        assertTrue("Preferences must be marked completed", preferences.isOnboardingCompleted())

        // Verify persistence in real Android SharedPreferences
        val saved = preferences.getProfile()
        assertEquals("Rakesh", saved.name)
        assertEquals("Work", saved.weekdayType)
        assertEquals("Tech Hub Office", saved.workplace)
        assertEquals("07:00", saved.wakeTime)
        assertEquals("08:30", saved.leaveHomeTime)
        assertEquals("18:00", saved.returnHomeTime)
        assertEquals("23:00", saved.sleepTime)
        assertEquals("Car", saved.travelMode)

        // Simulate app restart: re-instantiate preferences and verify status
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val restartedPrefs = OnboardingPreferences(context)
        assertTrue("Onboarding completed flag must survive app restart", restartedPrefs.isOnboardingCompleted())
        val restartedProfile = restartedPrefs.getProfile()
        assertEquals("Rakesh", restartedProfile.name)
        assertEquals("Tech Hub Office", restartedProfile.workplace)
    }
}
