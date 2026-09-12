package com.comai.ui.screens.onboarding

import com.comai.contextengine.db.UserProfile
import com.comai.contextengine.db.UserProfileDao
import com.comai.voice.ComaiLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeUserProfileDao : UserProfileDao {
        var savedProfile: UserProfile? = null

        override suspend fun getUserProfile(): UserProfile? {
            return savedProfile
        }

        override suspend fun insertOrUpdateProfile(profile: UserProfile) {
            savedProfile = profile
        }

        override suspend fun updateProfile(profile: UserProfile) {
            savedProfile = profile
        }
    }

    private class FakeOnboardingPreferences : OnboardingPreferences(null as android.content.Context?) {
        private var completed = false
        private var profile = UserOnboardingProfile()

        override fun isOnboardingCompleted(): Boolean = completed

        override fun setOnboardingCompleted(completed: Boolean) {
            this.completed = completed
        }

        override fun getProfile(): UserOnboardingProfile = profile

        override fun saveProfile(profile: UserOnboardingProfile) {
            this.profile = profile
        }

        override fun reset() {
            completed = false
            profile = UserOnboardingProfile()
        }
    }

    @Test
    fun testOnboardingStepOrderAndCount() {
        val steps = OnboardingStep.entries
        assertEquals(8, steps.size)
        assertEquals(OnboardingStep.WELCOME, steps[0])
        assertEquals(OnboardingStep.WHAT_COMAI_DOES, steps[1])
        assertEquals(OnboardingStep.CHOOSE_LANGUAGE, steps[2])
        assertEquals(OnboardingStep.VOICE_SETUP, steps[3])
        assertEquals(OnboardingStep.PROFILE_AND_ROUTINE, steps[4])
        assertEquals(OnboardingStep.LOCATION_SETUP, steps[5])
        assertEquals(OnboardingStep.PRIVACY, steps[6])
        assertEquals(OnboardingStep.FINISH, steps[7])
    }

    @Test
    fun testOnboardingViewModelStepNavigation() {
        val prefs = FakeOnboardingPreferences()
        val dao = FakeUserProfileDao()
        val viewModel = OnboardingViewModel(prefs, dao)

        assertEquals(OnboardingStep.WELCOME, viewModel.uiState.value.currentStep)

        // Forward progression
        viewModel.nextStep()
        assertEquals(OnboardingStep.WHAT_COMAI_DOES, viewModel.uiState.value.currentStep)

        viewModel.nextStep()
        assertEquals(OnboardingStep.CHOOSE_LANGUAGE, viewModel.uiState.value.currentStep)

        viewModel.nextStep()
        assertEquals(OnboardingStep.VOICE_SETUP, viewModel.uiState.value.currentStep)

        viewModel.nextStep()
        assertEquals(OnboardingStep.PROFILE_AND_ROUTINE, viewModel.uiState.value.currentStep)

        // Backward navigation
        viewModel.prevStep()
        assertEquals(OnboardingStep.VOICE_SETUP, viewModel.uiState.value.currentStep)

        viewModel.prevStep()
        assertEquals(OnboardingStep.CHOOSE_LANGUAGE, viewModel.uiState.value.currentStep)

        viewModel.prevStep()
        assertEquals(OnboardingStep.WHAT_COMAI_DOES, viewModel.uiState.value.currentStep)

        viewModel.prevStep()
        assertEquals(OnboardingStep.WELCOME, viewModel.uiState.value.currentStep)

        // Boundary check at start
        viewModel.prevStep()
        assertEquals(OnboardingStep.WELCOME, viewModel.uiState.value.currentStep)
    }

    @Test
    fun testOnboardingProfileFieldUpdates() {
        val prefs = FakeOnboardingPreferences()
        val dao = FakeUserProfileDao()
        val viewModel = OnboardingViewModel(prefs, dao)

        viewModel.updateName("Rakesh")
        viewModel.updateLanguage(ComaiLanguage.TAMIL)
        viewModel.updateWeekdayType("College")
        viewModel.updatePlaceName("Anna University")
        viewModel.updateWakeTime("06:30")
        viewModel.updateLeaveHomeTime("08:00")
        viewModel.updateReturnHomeTime("17:00")
        viewModel.updateSleepTime("22:30")
        viewModel.updateTravelMode("Bike")
        viewModel.setMicGranted(true)
        viewModel.setLocationGranted(true)

        val state = viewModel.uiState.value
        assertEquals("Rakesh", state.name)
        assertEquals(ComaiLanguage.TAMIL, state.preferredLanguage)
        assertEquals("College", state.weekdayType)
        assertEquals("Anna University", state.placeName)
        assertEquals("06:30", state.wakeTime)
        assertEquals("08:00", state.leaveHomeTime)
        assertEquals("17:00", state.returnHomeTime)
        assertEquals("22:30", state.sleepTime)
        assertEquals("Bike", state.travelMode)
        assertTrue(state.isMicGranted)
        assertTrue(state.isLocationGranted)
    }

    @Test
    fun testCompleteOnboardingFlow() = runTest {
        val prefs = FakeOnboardingPreferences()
        val dao = FakeUserProfileDao()
        val viewModel = OnboardingViewModel(prefs, dao)

        assertFalse(prefs.isOnboardingCompleted())

        viewModel.updateName("Rakesh")
        viewModel.updateLanguage(ComaiLanguage.TANGLISH)
        viewModel.updateWeekdayType("Work")
        viewModel.updatePlaceName("Tech Hub Office")
        viewModel.updateWakeTime("07:00")
        viewModel.updateLeaveHomeTime("08:30")
        viewModel.updateReturnHomeTime("18:00")
        viewModel.updateSleepTime("23:00")
        viewModel.updateTravelMode("Car")

        var finishedLanguage: ComaiLanguage? = null
        viewModel.completeOnboarding { lang ->
            finishedLanguage = lang
        }
        advanceUntilIdle()

        // 1. Preferences marked as completed
        assertTrue(prefs.isOnboardingCompleted())

        // 2. Profile persisted in preferences
        val saved = prefs.getProfile()
        assertEquals("Rakesh", saved.name)
        assertEquals(ComaiLanguage.TANGLISH, saved.preferredLanguage)
        assertEquals("Work", saved.weekdayType)
        assertEquals("Tech Hub Office", saved.placeName)
        assertEquals("07:00", saved.wakeTime)
        assertEquals("08:30", saved.leaveHomeTime)
        assertEquals("18:00", saved.returnHomeTime)
        assertEquals("23:00", saved.sleepTime)
        assertEquals("Car", saved.travelMode)

        // 3. UserProfile synced to Room DAO
        val roomProfile = dao.savedProfile
        assertEquals("07:00", roomProfile?.baselineWakeTime)
        assertEquals("23:00", roomProfile?.baselineSleepTime)
        assertEquals("18:00", roomProfile?.historicalOfficeDepartureTime)
        assertEquals("Tech Hub Office", roomProfile?.workLocationTag)

        // 4. Callback invoked with chosen language
        assertEquals(ComaiLanguage.TANGLISH, finishedLanguage)
        assertTrue(viewModel.uiState.value.isCompleted)
    }

    @Test
    fun testEmptyProfileFailsValidationAndDoesNotAdvance() {
        val prefs = FakeOnboardingPreferences()
        val dao = FakeUserProfileDao()
        val viewModel = OnboardingViewModel(prefs, dao)

        // Move to PROFILE_AND_ROUTINE
        viewModel.nextStep() // WHAT_COMAI_DOES
        viewModel.nextStep() // CHOOSE_LANGUAGE
        viewModel.nextStep() // VOICE_SETUP
        viewModel.nextStep() // PROFILE_AND_ROUTINE
        assertEquals(OnboardingStep.PROFILE_AND_ROUTINE, viewModel.uiState.value.currentStep)

        // Attempt proceed with all required fields empty
        viewModel.validateProfileAndProceed()

        // 1. MUST NOT advance to next step
        assertEquals(OnboardingStep.PROFILE_AND_ROUTINE, viewModel.uiState.value.currentStep)

        // 2. Clear field-specific and general errors must be set
        val state = viewModel.uiState.value
        assertEquals("Please enter your name", state.nameError)
        assertEquals("Please enter your workplace / office name", state.workplaceError)
        assertEquals("Please enter wake-up time", state.wakeTimeError)
        assertEquals("Please enter departure time", state.leaveHomeTimeError)
        assertEquals("Please enter return time", state.returnHomeTimeError)
        assertEquals("Please enter sleep time", state.sleepTimeError)
        assertEquals("Just a few details are needed to personalize Comai.", state.generalError)

        // 3. Onboarding must NOT be completed
        assertFalse(prefs.isOnboardingCompleted())
    }

    @Test
    fun testPartialProfileFailsValidation() {
        val prefs = FakeOnboardingPreferences()
        val dao = FakeUserProfileDao()
        val viewModel = OnboardingViewModel(prefs, dao)

        // Fill only Name
        viewModel.updateName("Rakesh")
        val isValid = viewModel.validateProfile()

        assertFalse(isValid)
        assertEquals(null, viewModel.uiState.value.nameError)
        assertEquals("Please enter your workplace / office name", viewModel.uiState.value.workplaceError)
        assertEquals("Please enter wake-up time", viewModel.uiState.value.wakeTimeError)
    }

    @Test
    fun testConditionalCollegeValidation() {
        val prefs = FakeOnboardingPreferences()
        val dao = FakeUserProfileDao()
        val viewModel = OnboardingViewModel(prefs, dao)

        viewModel.updateName("Priya")
        viewModel.updateWeekdayType("College")
        viewModel.updateWakeTime("06:30")
        viewModel.updateLeaveHomeTime("07:30")
        viewModel.updateReturnHomeTime("16:00")
        viewModel.updateSleepTime("22:30")

        // College is empty
        assertFalse(viewModel.validateProfile())
        assertEquals("Please enter your college name", viewModel.uiState.value.collegeError)

        // Provide college
        viewModel.updateCollege("IIT Madras")
        assertTrue(viewModel.validateProfile())
        assertEquals(null, viewModel.uiState.value.collegeError)
    }

    @Test
    fun testConditionalBothValidation() {
        val prefs = FakeOnboardingPreferences()
        val dao = FakeUserProfileDao()
        val viewModel = OnboardingViewModel(prefs, dao)

        viewModel.updateName("Ravi")
        viewModel.updateWeekdayType("Both")
        viewModel.updateWakeTime("06:30")
        viewModel.updateLeaveHomeTime("07:30")
        viewModel.updateReturnHomeTime("18:00")
        viewModel.updateSleepTime("23:00")

        // Both workplace and college are empty
        assertFalse(viewModel.validateProfile())
        assertEquals("Please enter your workplace name", viewModel.uiState.value.workplaceError)
        assertEquals("Please enter your college name", viewModel.uiState.value.collegeError)

        // Only workplace provided
        viewModel.updateWorkplace("Tech Corp")
        assertFalse(viewModel.validateProfile())
        assertEquals("Please enter your college name", viewModel.uiState.value.collegeError)

        // Both provided
        viewModel.updateCollege("City College")
        assertTrue(viewModel.validateProfile())
        assertEquals(null, viewModel.uiState.value.workplaceError)
        assertEquals(null, viewModel.uiState.value.collegeError)
    }

    @Test
    fun testConditionalOtherValidation() {
        val prefs = FakeOnboardingPreferences()
        val dao = FakeUserProfileDao()
        val viewModel = OnboardingViewModel(prefs, dao)

        viewModel.updateName("Ananya")
        viewModel.updateWeekdayType("Other")
        viewModel.updateWakeTime("08:00")
        viewModel.updateLeaveHomeTime("09:00")
        viewModel.updateReturnHomeTime("17:00")
        viewModel.updateSleepTime("00:00")

        // Workplace and college not required for "Other"
        assertTrue(viewModel.validateProfile())
        assertEquals(null, viewModel.uiState.value.workplaceError)
        assertEquals(null, viewModel.uiState.value.collegeError)
    }

    @Test
    fun testTimeFormatValidation() {
        // Valid 24h times
        assertTrue(OnboardingViewModel.isValidTime("07:00"))
        assertTrue(OnboardingViewModel.isValidTime("7:00"))
        assertTrue(OnboardingViewModel.isValidTime("18:30"))
        assertTrue(OnboardingViewModel.isValidTime("23:59"))
        assertTrue(OnboardingViewModel.isValidTime("00:00"))

        // Valid 12h times
        assertTrue(OnboardingViewModel.isValidTime("7:00 AM"))
        assertTrue(OnboardingViewModel.isValidTime("07:00 am"))
        assertTrue(OnboardingViewModel.isValidTime("11:30 PM"))
        assertTrue(OnboardingViewModel.isValidTime("12:00 pm"))

        // Invalid times
        assertFalse(OnboardingViewModel.isValidTime(""))
        assertFalse(OnboardingViewModel.isValidTime("   "))
        assertFalse(OnboardingViewModel.isValidTime("25:00"))
        assertFalse(OnboardingViewModel.isValidTime("12:65"))
        assertFalse(OnboardingViewModel.isValidTime("morning"))
        assertFalse(OnboardingViewModel.isValidTime("7 oclock"))
    }

    @Test
    fun testCompleteOnboardingBlocksIfProfileInvalid() = runTest {
        val prefs = FakeOnboardingPreferences()
        val dao = FakeUserProfileDao()
        val viewModel = OnboardingViewModel(prefs, dao)

        var calledSuccess = false
        viewModel.completeOnboarding {
            calledSuccess = true
        }

        // Must NOT succeed or mark preferences
        assertFalse(calledSuccess)
        assertFalse(prefs.isOnboardingCompleted())
        assertEquals(OnboardingStep.PROFILE_AND_ROUTINE, viewModel.uiState.value.currentStep)
        assertEquals("Just a few details are needed to personalize Comai.", viewModel.uiState.value.generalError)
    }
}
