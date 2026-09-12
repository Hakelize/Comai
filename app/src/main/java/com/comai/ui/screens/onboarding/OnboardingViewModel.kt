package com.comai.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comai.contextengine.db.UserProfile
import com.comai.contextengine.db.UserProfileDao
import com.comai.util.TimeUtils
import com.comai.voice.ComaiLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class OnboardingStep(val stepIndex: Int, val title: String) {
    WELCOME(0, "Meet Comai"),
    WHAT_COMAI_DOES(1, "What Comai Can Do"),
    CHOOSE_LANGUAGE(2, "Language"),
    VOICE_SETUP(3, "Voice Setup"),
    PROFILE_AND_ROUTINE(4, "Let's Get to Know You"),
    LOCATION_SETUP(5, "Location"),
    PRIVACY(6, "Privacy"),
    FINISH(7, "You're All Set")
}

data class OnboardingUiState(
    val currentStep: OnboardingStep = OnboardingStep.WELCOME,
    val name: String = "",
    val preferredLanguage: ComaiLanguage = ComaiLanguage.ENGLISH,
    val weekdayType: String = "Work",
    val workplace: String = "",
    val college: String = "",
    val placeName: String = "",
    val wakeTime: String = "",
    val leaveHomeTime: String = "",
    val returnHomeTime: String = "",
    val sleepTime: String = "",
    val travelMode: String = "Car",
    val isMicGranted: Boolean = false,
    val isLocationGranted: Boolean = false,
    val isCompleted: Boolean = false,

    // Validation errors
    val nameError: String? = null,
    val workplaceError: String? = null,
    val collegeError: String? = null,
    val wakeTimeError: String? = null,
    val leaveHomeTimeError: String? = null,
    val returnHomeTimeError: String? = null,
    val sleepTimeError: String? = null,
    val generalError: String? = null
)

class OnboardingViewModel(
    private val preferences: OnboardingPreferences,
    private val userProfileDao: UserProfileDao? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        run {
            val saved = preferences.getProfile()
            OnboardingUiState(
                name = saved.name,
                preferredLanguage = saved.preferredLanguage,
                weekdayType = saved.weekdayType,
                workplace = saved.workplace,
                college = saved.college,
                placeName = saved.placeName,
                wakeTime = if (saved.wakeTime.isNotBlank()) TimeUtils.normalizeTo12Hour(saved.wakeTime) else "",
                leaveHomeTime = if (saved.leaveHomeTime.isNotBlank()) TimeUtils.normalizeTo12Hour(saved.leaveHomeTime) else "",
                returnHomeTime = if (saved.returnHomeTime.isNotBlank()) TimeUtils.normalizeTo12Hour(saved.returnHomeTime) else "",
                sleepTime = if (saved.sleepTime.isNotBlank()) TimeUtils.normalizeTo12Hour(saved.sleepTime) else "",
                travelMode = saved.travelMode
            )
        }
    )
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun nextStep() {
        val steps = OnboardingStep.entries
        val currentIndex = _uiState.value.currentStep.ordinal
        if (currentIndex < steps.size - 1) {
            _uiState.value = _uiState.value.copy(currentStep = steps[currentIndex + 1])
        }
    }

    fun prevStep() {
        val steps = OnboardingStep.entries
        val currentIndex = _uiState.value.currentStep.ordinal
        if (currentIndex > 0) {
            _uiState.value = _uiState.value.copy(currentStep = steps[currentIndex - 1])
        }
    }

    fun updateName(name: String) {
        _uiState.value = _uiState.value.copy(
            name = name,
            nameError = null,
            generalError = null
        )
    }

    fun updateLanguage(language: ComaiLanguage) {
        _uiState.value = _uiState.value.copy(preferredLanguage = language)
    }

    fun updateWeekdayType(type: String) {
        _uiState.value = _uiState.value.copy(
            weekdayType = type,
            workplaceError = null,
            collegeError = null,
            generalError = null
        )
    }

    fun updateWorkplace(workplace: String) {
        _uiState.value = _uiState.value.copy(
            workplace = workplace,
            placeName = if (_uiState.value.weekdayType == "College") _uiState.value.placeName else workplace,
            workplaceError = null,
            generalError = null
        )
    }

    fun updateCollege(college: String) {
        _uiState.value = _uiState.value.copy(
            college = college,
            placeName = if (_uiState.value.weekdayType == "College") college else _uiState.value.placeName,
            collegeError = null,
            generalError = null
        )
    }

    fun updatePlaceName(placeName: String) {
        when (_uiState.value.weekdayType) {
            "College" -> updateCollege(placeName)
            "Work" -> updateWorkplace(placeName)
            else -> {
                _uiState.value = _uiState.value.copy(
                    placeName = placeName,
                    generalError = null
                )
            }
        }
    }

    fun updateWakeTime(time: String) {
        _uiState.value = _uiState.value.copy(
            wakeTime = time,
            wakeTimeError = null,
            generalError = null
        )
    }

    fun updateLeaveHomeTime(time: String) {
        _uiState.value = _uiState.value.copy(
            leaveHomeTime = time,
            leaveHomeTimeError = null,
            generalError = null
        )
    }

    fun updateReturnHomeTime(time: String) {
        _uiState.value = _uiState.value.copy(
            returnHomeTime = time,
            returnHomeTimeError = null,
            generalError = null
        )
    }

    fun updateSleepTime(time: String) {
        _uiState.value = _uiState.value.copy(
            sleepTime = time,
            sleepTimeError = null,
            generalError = null
        )
    }

    fun updateTravelMode(mode: String) {
        _uiState.value = _uiState.value.copy(travelMode = mode)
    }

    fun updateWorkSchedule(schedule: String) {
        if (schedule.contains("-")) {
            val parts = schedule.split("-").map { it.trim() }
            if (parts.size >= 2) {
                _uiState.value = _uiState.value.copy(
                    leaveHomeTime = parts[0],
                    returnHomeTime = parts[1],
                    leaveHomeTimeError = null,
                    returnHomeTimeError = null
                )
            }
        }
    }

    fun setMicGranted(granted: Boolean) {
        _uiState.value = _uiState.value.copy(isMicGranted = granted)
    }

    fun setLocationGranted(granted: Boolean) {
        _uiState.value = _uiState.value.copy(isLocationGranted = granted)
    }

    /**
     * Validates all required profile fields according to specification.
     * Returns true if all fields are valid, false otherwise.
     */
    fun validateProfile(): Boolean {
        val state = _uiState.value
        var isValid = true

        var nameErr: String? = null
        var workplaceErr: String? = null
        var collegeErr: String? = null
        var wakeErr: String? = null
        var leaveErr: String? = null
        var returnErr: String? = null
        var sleepErr: String? = null

        // 1. Name is required
        if (state.name.trim().isBlank()) {
            nameErr = "Please enter your name"
            isValid = false
        }

        // 2. Conditional place validation
        when (state.weekdayType) {
            "Work" -> {
                if (state.workplace.trim().isBlank()) {
                    workplaceErr = "Please enter your workplace / office name"
                    isValid = false
                }
            }
            "College" -> {
                if (state.college.trim().isBlank()) {
                    collegeErr = "Please enter your college name"
                    isValid = false
                }
            }
            "Both" -> {
                if (state.workplace.trim().isBlank()) {
                    workplaceErr = "Please enter your workplace name"
                    isValid = false
                }
                if (state.college.trim().isBlank()) {
                    collegeErr = "Please enter your college name"
                    isValid = false
                }
            }
            "Other" -> {
                // Not required for "Other"
            }
        }

        // 3. Time validation (all required times must not be blank and must be valid format)
        if (state.wakeTime.trim().isBlank()) {
            wakeErr = "Please enter wake-up time"
            isValid = false
        } else if (!isValidTime(state.wakeTime)) {
            wakeErr = "Invalid time (e.g. 07:00)"
            isValid = false
        }

        if (state.leaveHomeTime.trim().isBlank()) {
            leaveErr = "Please enter departure time"
            isValid = false
        } else if (!isValidTime(state.leaveHomeTime)) {
            leaveErr = "Invalid time (e.g. 08:30)"
            isValid = false
        }

        if (state.returnHomeTime.trim().isBlank()) {
            returnErr = "Please enter return time"
            isValid = false
        } else if (!isValidTime(state.returnHomeTime)) {
            returnErr = "Invalid time (e.g. 18:00)"
            isValid = false
        }

        if (state.sleepTime.trim().isBlank()) {
            sleepErr = "Please enter sleep time"
            isValid = false
        } else if (!isValidTime(state.sleepTime)) {
            sleepErr = "Invalid time (e.g. 23:00)"
            isValid = false
        }

        val generalMsg = if (!isValid) "Just a few details are needed to personalize Comai." else null

        _uiState.value = _uiState.value.copy(
            nameError = nameErr,
            workplaceError = workplaceErr,
            collegeError = collegeErr,
            wakeTimeError = wakeErr,
            leaveHomeTimeError = leaveErr,
            returnHomeTimeError = returnErr,
            sleepTimeError = sleepErr,
            generalError = generalMsg
        )

        return isValid
    }

    /**
     * Called when the user clicks Continue on the Profile and Routine step.
     * Prevents moving to the next step if validation fails.
     */
    fun validateProfileAndProceed() {
        if (validateProfile()) {
            _uiState.value = _uiState.value.copy(generalError = null)
            saveDraftProfile()
            nextStep()
        }
    }

    /**
     * Saves user inputs as a draft profile without marking onboarding as complete.
     */
    private fun saveDraftProfile() {
        val state = _uiState.value
        val primaryPlace = when (state.weekdayType) {
            "College" -> state.college.trim().ifBlank { state.placeName.trim() }
            "Both" -> {
                val wp = state.workplace.trim().ifBlank { state.placeName.trim() }
                val cl = state.college.trim()
                if (wp.isNotBlank() && cl.isNotBlank()) "$wp & $cl" else wp.ifBlank { cl }
            }
            "Other" -> state.workplace.trim().ifBlank { state.placeName.trim() }
            else -> state.workplace.trim().ifBlank { state.placeName.trim() }
        }

        val profile = UserOnboardingProfile(
            name = state.name.trim(),
            preferredLanguage = state.preferredLanguage,
            weekdayType = state.weekdayType,
            placeName = primaryPlace,
            workplace = state.workplace.trim().ifBlank { state.placeName.trim() },
            college = state.college.trim(),
            wakeTime = state.wakeTime.trim(),
            leaveHomeTime = state.leaveHomeTime.trim(),
            returnHomeTime = state.returnHomeTime.trim(),
            sleepTime = state.sleepTime.trim(),
            travelMode = state.travelMode.trim()
        )
        preferences.saveProfile(profile)
    }

    /**
     * Completes onboarding ONLY after all required fields pass validation.
     * Marks onboarding completed and notifies callback with the chosen language.
     */
    fun completeOnboarding(onSuccess: (ComaiLanguage) -> Unit) {
        if (!validateProfile()) {
            // Keep user on profile step if validation fails
            _uiState.value = _uiState.value.copy(
                currentStep = OnboardingStep.PROFILE_AND_ROUTINE,
                generalError = "Just a few details are needed to personalize Comai."
            )
            return
        }

        val state = _uiState.value
        val primaryPlace = when (state.weekdayType) {
            "College" -> state.college.trim().ifBlank { state.placeName.trim() }
            "Both" -> {
                val wp = state.workplace.trim().ifBlank { state.placeName.trim() }
                val cl = state.college.trim()
                if (wp.isNotBlank() && cl.isNotBlank()) "$wp & $cl" else wp.ifBlank { cl }
            }
            "Other" -> state.workplace.trim().ifBlank { state.placeName.trim() }
            else -> state.workplace.trim().ifBlank { state.placeName.trim() }
        }

        val profile = UserOnboardingProfile(
            name = state.name.trim(),
            preferredLanguage = state.preferredLanguage,
            weekdayType = state.weekdayType,
            placeName = primaryPlace,
            workplace = state.workplace.trim().ifBlank { state.placeName.trim() },
            college = state.college.trim(),
            wakeTime = state.wakeTime.trim(),
            leaveHomeTime = state.leaveHomeTime.trim(),
            returnHomeTime = state.returnHomeTime.trim(),
            sleepTime = state.sleepTime.trim(),
            travelMode = state.travelMode.trim()
        )

        // Save valid profile & mark completed
        preferences.saveProfile(profile)
        preferences.setOnboardingCompleted(true)
        _uiState.value = _uiState.value.copy(isCompleted = true, generalError = null)

        // Sync to Room UserProfile if DAO is provided
        if (userProfileDao != null) {
            viewModelScope.launch {
                try {
                    val userProfile = UserProfile(
                        id = 1,
                        baselineWakeTime = profile.wakeTime,
                        baselineSleepTime = profile.sleepTime,
                        historicalOfficeDepartureTime = profile.returnHomeTime,
                        workLocationTag = profile.placeName
                    )
                    userProfileDao.insertOrUpdateProfile(userProfile)
                } catch (e: Exception) {
                    android.util.Log.w("OnboardingViewModel", "Could not sync UserProfile to DB: ${e.message}")
                } finally {
                    onSuccess(state.preferredLanguage)
                }
            }
        } else {
            onSuccess(state.preferredLanguage)
        }
    }

    companion object {
        /**
         * Validates standard 24h or 12h time string formats (e.g., "07:00", "7:00", "18:30", "7:00 AM", "11:30 pm").
         */
        fun isValidTime(timeStr: String): Boolean {
            val trimmed = timeStr.trim()
            if (trimmed.isBlank()) return false

            // 24-hour format: "07:00", "7:00", "23:59"
            val regex24 = Regex("^([0-1]?[0-9]|2[0-3]):[0-5][0-9]$")
            if (regex24.matches(trimmed)) return true

            // 12-hour format: "7:00 AM", "07:00 PM", "7:00am", "12:30 pm"
            val regex12 = Regex("^(0?[1-9]|1[0-2]):[0-5][0-9]\\s*([AaPp][Mm])$")
            if (regex12.matches(trimmed)) return true

            return false
        }
    }
}
