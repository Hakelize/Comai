package com.comai.ui.screens.onboarding

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.comai.ui.components.ComaiCompactTimeBar
import com.comai.util.Time12
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.comai.ui.components.SiriVoiceOrb
import com.comai.ui.theme.*
import com.comai.voice.ComaiLanguage
import com.comai.voice.VoiceState

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onOnboardingFinished: (ComaiLanguage) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Activity Result Launchers for Permissions
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.setMicGranted(isGranted)
        if (isGranted) {
            viewModel.nextStep()
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.setLocationGranted(granted)
        viewModel.nextStep()
    }

    // Check initial permission statuses
    LaunchedEffect(Unit) {
        val hasMic = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        val hasLoc = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        viewModel.setMicGranted(hasMic)
        viewModel.setLocationGranted(hasLoc)
    }

    Scaffold(
        containerColor = DarkBackground,
        contentWindowInsets = WindowInsets(0)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ── Top Header with Step Indicators ──────────────────────────
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (uiState.currentStep.ordinal > 0 && uiState.currentStep != OnboardingStep.FINISH) {
                        IconButton(
                            onClick = { viewModel.prevStep() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TextSecondary
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(36.dp))
                    }

                    // Progress Dots
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OnboardingStep.entries.forEach { step ->
                            val isActive = step == uiState.currentStep
                            val isPassed = step.ordinal < uiState.currentStep.ordinal
                            Box(
                                modifier = Modifier
                                    .size(if (isActive) 10.dp else 6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isActive -> ElectricTeal
                                            isPassed -> SoftPurple
                                            else -> DarkSurfaceVariant
                                        }
                                    )
                            )
                        }
                    }

                    // Skip button on optional steps
                    if (uiState.currentStep == OnboardingStep.LOCATION_SETUP || uiState.currentStep == OnboardingStep.WHAT_COMAI_DOES) {
                        TextButton(onClick = { viewModel.nextStep() }) {
                            Text("Skip", color = TextSecondary, fontSize = 13.sp)
                        }
                    } else {
                        Spacer(modifier = Modifier.size(36.dp))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Animated Step Content ────────────────────────────────────
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = uiState.currentStep,
                        transitionSpec = {
                            if (targetState.ordinal > initialState.ordinal) {
                                slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                            } else {
                                slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                            }
                        },
                        label = "onboarding_steps"
                    ) { step ->
                        when (step) {
                            OnboardingStep.WELCOME -> WelcomeStepView(
                                onGetStarted = { viewModel.nextStep() }
                            )
                            OnboardingStep.WHAT_COMAI_DOES -> WhatComaiDoesStepView(
                                onContinue = { viewModel.nextStep() }
                            )
                            OnboardingStep.CHOOSE_LANGUAGE -> ChooseLanguageStepView(
                                selectedLanguage = uiState.preferredLanguage,
                                onSelectLanguage = { viewModel.updateLanguage(it) },
                                onContinue = { viewModel.nextStep() }
                            )
                            OnboardingStep.VOICE_SETUP -> VoiceSetupStepView(
                                isGranted = uiState.isMicGranted,
                                onRequestPermission = {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                onContinue = { viewModel.nextStep() }
                            )
                            OnboardingStep.PROFILE_AND_ROUTINE -> ProfileAndRoutineStepView(
                                name = uiState.name,
                                weekdayType = uiState.weekdayType,
                                workplace = uiState.workplace,
                                college = uiState.college,
                                placeName = uiState.placeName,
                                wakeTime = uiState.wakeTime,
                                leaveHomeTime = uiState.leaveHomeTime,
                                returnHomeTime = uiState.returnHomeTime,
                                sleepTime = uiState.sleepTime,
                                travelMode = uiState.travelMode,
                                nameError = uiState.nameError,
                                workplaceError = uiState.workplaceError,
                                collegeError = uiState.collegeError,
                                wakeTimeError = uiState.wakeTimeError,
                                leaveHomeTimeError = uiState.leaveHomeTimeError,
                                returnHomeTimeError = uiState.returnHomeTimeError,
                                sleepTimeError = uiState.sleepTimeError,
                                generalError = uiState.generalError,
                                onNameChange = { viewModel.updateName(it) },
                                onWeekdayTypeChange = { viewModel.updateWeekdayType(it) },
                                onWorkplaceChange = { viewModel.updateWorkplace(it) },
                                onCollegeChange = { viewModel.updateCollege(it) },
                                onPlaceNameChange = { viewModel.updatePlaceName(it) },
                                onWakeTimeChange = { viewModel.updateWakeTime(it) },
                                onLeaveHomeTimeChange = { viewModel.updateLeaveHomeTime(it) },
                                onReturnHomeTimeChange = { viewModel.updateReturnHomeTime(it) },
                                onSleepTimeChange = { viewModel.updateSleepTime(it) },
                                onTravelModeChange = { viewModel.updateTravelMode(it) },
                                onContinue = { viewModel.validateProfileAndProceed() }
                            )
                            OnboardingStep.LOCATION_SETUP -> LocationSetupStepView(
                                isGranted = uiState.isLocationGranted,
                                onRequestPermission = {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                },
                                onContinue = { viewModel.nextStep() },
                                onSkip = { viewModel.nextStep() }
                            )
                            OnboardingStep.PRIVACY -> PrivacyStepView(
                                onContinue = { viewModel.nextStep() }
                            )
                            OnboardingStep.FINISH -> FinishStepView(
                                onLaunch = {
                                    viewModel.completeOnboarding(onOnboardingFinished)
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════
//  Step 1: Meet Comai (Welcome)
// ═════════════════════════════════════════════════════════════════════
@Composable
private fun WelcomeStepView(onGetStarted: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        SiriVoiceOrb(
            state = VoiceState.IDLE,
            orbSize = 180.dp
        )

        Spacer(modifier = Modifier.height(36.dp))

        Text(
            text = "Meet Comai",
            color = TextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Your personal AI companion that learns your routine, understands your day, and helps you at the right time.",
            color = TextSecondary,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onGetStarted,
            colors = ButtonDefaults.buttonColors(containerColor = ElectricTeal),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(
                text = "Get Started",
                color = DarkBackground,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════════════
//  Step 2: What Comai Can Do
// ═════════════════════════════════════════════════════════════════════
@Composable
private fun WhatComaiDoesStepView(onContinue: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "What Comai Can Do",
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "A companion that understands your day and fits into your life.",
            color = TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        FeatureCard(
            icon = Icons.Outlined.Schedule,
            title = "Comai gets to know your routine",
            description = "Over time, Comai learns things like your usual schedule, places you visit, and everyday habits."
        )

        Spacer(modifier = Modifier.height(14.dp))

        FeatureCard(
            icon = Icons.Outlined.Lightbulb,
            title = "Comai remembers what matters",
            description = "You can tell Comai what you want it to remember, and you stay in control of your memories."
        )

        Spacer(modifier = Modifier.height(14.dp))

        FeatureCard(
            icon = Icons.Outlined.NotificationsActive,
            title = "Comai helps when things change",
            description = "If something is different from your usual routine, Comai can check in and help."
        )

        Spacer(modifier = Modifier.height(36.dp))

        Button(
            onClick = onContinue,
            colors = ButtonDefaults.buttonColors(containerColor = ElectricTeal),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(
                text = "Continue",
                color = DarkBackground,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun FeatureCard(icon: ImageVector, title: String, description: String) {
    Surface(
        color = DarkSurfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(DarkSurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = ElectricTeal,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════
//  Step 3: How would you like to talk to Comai?
// ═════════════════════════════════════════════════════════════════════
@Composable
private fun ChooseLanguageStepView(
    selectedLanguage: ComaiLanguage,
    onSelectLanguage: (ComaiLanguage) -> Unit,
    onContinue: () -> Unit
) {
    val languages = listOf(
        ComaiLanguage.ENGLISH to "English",
        ComaiLanguage.TAMIL to "தமிழ் (Tamil)",
        ComaiLanguage.TELUGU to "తెలుగు (Telugu)",
        ComaiLanguage.HINDI to "हिन्दी (Hindi)",
        ComaiLanguage.MALAYALAM to "മലയാളം (Malayalam)",
        ComaiLanguage.TANGLISH to "I usually mix languages"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "How would you like to talk to Comai?",
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Choose the language you feel most comfortable speaking.",
            color = TextSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            languages.forEach { (lang, label) ->
                val isSelected = lang == selectedLanguage
                Surface(
                    color = if (isSelected) ElectricTeal.copy(alpha = 0.15f) else DarkSurfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) ElectricTeal else DarkSurface,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable { onSelectLanguage(lang) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) ElectricTeal else TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = ElectricTeal,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onContinue,
            colors = ButtonDefaults.buttonColors(containerColor = ElectricTeal),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(
                text = "Continue",
                color = DarkBackground,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════════════
//  Step 4: Voice Setup
// ═════════════════════════════════════════════════════════════════════
@Composable
private fun VoiceSetupStepView(
    isGranted: Boolean,
    onRequestPermission: () -> Unit,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        SiriVoiceOrb(
            state = if (isGranted) VoiceState.LISTENING else VoiceState.IDLE,
            orbSize = 150.dp
        )

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "Voice Setup",
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Talk to Comai naturally. You can use your voice whenever you want.",
            color = TextSecondary,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Surface(
            color = DarkSurfaceVariant,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isGranted) Icons.Default.Check else Icons.Default.Mic,
                    contentDescription = null,
                    tint = if (isGranted) OnlineGreen else ElectricTeal,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (isGranted) "Microphone Enabled" else "Ready to configure",
                    color = if (isGranted) OnlineGreen else TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

        if (!isGranted) {
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricTeal),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = "Enable Microphone",
                    color = DarkBackground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onContinue) {
                Text("Continue without voice", color = TextSecondary, fontSize = 13.sp)
            }
        } else {
            Button(
                onClick = onContinue,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricTeal),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = "Continue",
                    color = DarkBackground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════
//  Step 5: Let's get to know you (Profile & Routine)
// ═════════════════════════════════════════════════════════════════════
@Composable
private fun ProfileAndRoutineStepView(
    name: String,
    weekdayType: String,
    workplace: String,
    college: String,
    placeName: String,
    wakeTime: String,
    leaveHomeTime: String,
    returnHomeTime: String,
    sleepTime: String,
    travelMode: String,
    nameError: String? = null,
    workplaceError: String? = null,
    collegeError: String? = null,
    wakeTimeError: String? = null,
    leaveHomeTimeError: String? = null,
    returnHomeTimeError: String? = null,
    sleepTimeError: String? = null,
    generalError: String? = null,
    onNameChange: (String) -> Unit,
    onWeekdayTypeChange: (String) -> Unit,
    onWorkplaceChange: (String) -> Unit,
    onCollegeChange: (String) -> Unit,
    onPlaceNameChange: (String) -> Unit,
    onWakeTimeChange: (String) -> Unit,
    onLeaveHomeTimeChange: (String) -> Unit,
    onReturnHomeTimeChange: (String) -> Unit,
    onSleepTimeChange: (String) -> Unit,
    onTravelModeChange: (String) -> Unit,
    onContinue: () -> Unit
) {
    val weekdayOptions = listOf("Work", "College", "Both", "Other")
    val travelOptions = listOf("Walk", "Bike", "Car", "Public transport", "Other")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "Let's get to know you",
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Tell Comai a little about your everyday rhythm so it can help at the right times.",
            color = TextSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Friendly general error banner if validation failed
        if (generalError != null) {
            Surface(
                color = Color(0xFF2C1618),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFE57373).copy(alpha = 0.6f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFFE57373),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = generalError,
                        color = Color(0xFFFFCDD2),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 1. Name (Required)
        Text("What's your name? *", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            placeholder = { Text("Enter your name") },
            singleLine = true,
            isError = nameError != null,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ElectricTeal,
                unfocusedBorderColor = DarkSurface,
                errorBorderColor = Color(0xFFE57373),
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedContainerColor = DarkSurfaceVariant,
                unfocusedContainerColor = DarkSurfaceVariant,
                errorContainerColor = DarkSurfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        )
        if (nameError != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = nameError,
                color = Color(0xFFE57373),
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 2. Weekday Type
        Text("What best describes your weekday? *", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            weekdayOptions.forEach { option ->
                val isSelected = option == weekdayType
                Surface(
                    color = if (isSelected) ElectricTeal else DarkSurfaceVariant,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.clickable { onWeekdayTypeChange(option) }
                ) {
                    Text(
                        text = option,
                        color = if (isSelected) DarkBackground else TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Conditional Place Fields
        when (weekdayType) {
            "College" -> {
                Text("College / Campus name *", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(6.dp))
                val collegeVal = if (college.isNotBlank()) college else placeName
                OutlinedTextField(
                    value = collegeVal,
                    onValueChange = {
                        onCollegeChange(it)
                        onPlaceNameChange(it)
                    },
                    placeholder = { Text("e.g. Stanford University / IIT") },
                    singleLine = true,
                    isError = collegeError != null,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricTeal,
                        unfocusedBorderColor = DarkSurface,
                        errorBorderColor = Color(0xFFE57373),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = DarkSurfaceVariant,
                        unfocusedContainerColor = DarkSurfaceVariant,
                        errorContainerColor = DarkSurfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (collegeError != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = collegeError,
                        color = Color(0xFFE57373),
                        fontSize = 12.sp
                    )
                }
            }
            "Both" -> {
                // Workplace field
                Text("Workplace / Office name *", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = workplace,
                    onValueChange = onWorkplaceChange,
                    placeholder = { Text("e.g. Tech Hub Office") },
                    singleLine = true,
                    isError = workplaceError != null,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricTeal,
                        unfocusedBorderColor = DarkSurface,
                        errorBorderColor = Color(0xFFE57373),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = DarkSurfaceVariant,
                        unfocusedContainerColor = DarkSurfaceVariant,
                        errorContainerColor = DarkSurfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (workplaceError != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = workplaceError,
                        color = Color(0xFFE57373),
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // College field
                Text("College / Campus name *", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = college,
                    onValueChange = onCollegeChange,
                    placeholder = { Text("e.g. City College") },
                    singleLine = true,
                    isError = collegeError != null,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricTeal,
                        unfocusedBorderColor = DarkSurface,
                        errorBorderColor = Color(0xFFE57373),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = DarkSurfaceVariant,
                        unfocusedContainerColor = DarkSurfaceVariant,
                        errorContainerColor = DarkSurfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (collegeError != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = collegeError,
                        color = Color(0xFFE57373),
                        fontSize = 12.sp
                    )
                }
            }
            "Other" -> {
                Text("Primary daily location (Optional)", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = placeName,
                    onValueChange = onPlaceNameChange,
                    placeholder = { Text("e.g. Home Studio / Freelance") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricTeal,
                        unfocusedBorderColor = DarkSurface,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = DarkSurfaceVariant,
                        unfocusedContainerColor = DarkSurfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            else -> {
                // "Work"
                Text("Workplace / Office name *", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(6.dp))
                val workVal = if (workplace.isNotBlank()) workplace else placeName
                OutlinedTextField(
                    value = workVal,
                    onValueChange = {
                        onWorkplaceChange(it)
                        onPlaceNameChange(it)
                    },
                    placeholder = { Text("e.g. Tech Hub Office") },
                    singleLine = true,
                    isError = workplaceError != null,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricTeal,
                        unfocusedBorderColor = DarkSurface,
                        errorBorderColor = Color(0xFFE57373),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = DarkSurfaceVariant,
                        unfocusedContainerColor = DarkSurfaceVariant,
                        errorContainerColor = DarkSurfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (workplaceError != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = workplaceError,
                        color = Color(0xFFE57373),
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 4. Daily Schedule (Typical times)
        Text("Typical Daily Schedule *", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Wake, departure, return, and sleep times help Comai assist you at the right moments.",
            color = TextSecondary,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Wake up
            Column(modifier = Modifier.weight(1f)) {
                Text("Wake up *", color = TextSecondary, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                ComaiCompactTimeBar(
                    value = wakeTime,
                    onValueChange = onWakeTimeChange,
                    isError = wakeTimeError != null,
                    defaultTime = Time12(7, 0, true)
                )
                if (wakeTimeError != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = wakeTimeError,
                        color = Color(0xFFE57373),
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }
            }

            // Leave home
            Column(modifier = Modifier.weight(1f)) {
                Text("Leave home *", color = TextSecondary, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                ComaiCompactTimeBar(
                    value = leaveHomeTime,
                    onValueChange = onLeaveHomeTimeChange,
                    isError = leaveHomeTimeError != null,
                    defaultTime = Time12(8, 30, true)
                )
                if (leaveHomeTimeError != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = leaveHomeTimeError,
                        color = Color(0xFFE57373),
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Return home
            Column(modifier = Modifier.weight(1f)) {
                Text("Return home *", color = TextSecondary, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                ComaiCompactTimeBar(
                    value = returnHomeTime,
                    onValueChange = onReturnHomeTimeChange,
                    isError = returnHomeTimeError != null,
                    defaultTime = Time12(6, 0, false)
                )
                if (returnHomeTimeError != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = returnHomeTimeError,
                        color = Color(0xFFE57373),
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }
            }

            // Sleep
            Column(modifier = Modifier.weight(1f)) {
                Text("Sleep *", color = TextSecondary, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                ComaiCompactTimeBar(
                    value = sleepTime,
                    onValueChange = onSleepTimeChange,
                    isError = sleepTimeError != null,
                    defaultTime = Time12(11, 0, false)
                )
                if (sleepTimeError != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = sleepTimeError,
                        color = Color(0xFFE57373),
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 5. Travel Mode (Optional)
        Text("Usual mode of travel (Optional)", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            travelOptions.forEach { option ->
                val isSelected = option == travelMode
                Surface(
                    color = if (isSelected) ElectricTeal else DarkSurfaceVariant,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.clickable { onTravelModeChange(option) }
                ) {
                    Text(
                        text = option,
                        color = if (isSelected) DarkBackground else TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = onContinue,
            colors = ButtonDefaults.buttonColors(containerColor = ElectricTeal),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(
                text = "Continue",
                color = DarkBackground,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

// ═════════════════════════════════════════════════════════════════════
//  Step 6: Location & Commute Setup
// ═════════════════════════════════════════════════════════════════════
@Composable
private fun LocationSetupStepView(
    isGranted: Boolean,
    onRequestPermission: () -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(if (isGranted) OnlineGreen.copy(alpha = 0.15f) else DarkSurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MyLocation,
                contentDescription = "Location",
                tint = if (isGranted) OnlineGreen else SoftPurple,
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "Places & Commute",
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Location helps Comai understand places that are part of your routine and provide better context.",
            color = TextSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Surface(
            color = DarkSurfaceVariant,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isGranted) Icons.Default.Check else Icons.Default.MyLocation,
                    contentDescription = null,
                    tint = if (isGranted) OnlineGreen else SoftPurple,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (isGranted) "Location Enabled" else "Optional Setting",
                    color = if (isGranted) OnlineGreen else TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

        if (!isGranted) {
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricTeal),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = "Enable Location",
                    color = DarkBackground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onSkip) {
                Text("Skip for now", color = TextSecondary, fontSize = 13.sp)
            }
        } else {
            Button(
                onClick = onContinue,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricTeal),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = "Continue",
                    color = DarkBackground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════
//  Step 7: Privacy by Design
// ═════════════════════════════════════════════════════════════════════
@Composable
private fun PrivacyStepView(onContinue: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(OnlineGreen.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = "Privacy",
                tint = OnlineGreen,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Privacy by Design",
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Your information should stay under your control.",
            color = ElectricTeal,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(24.dp))

        PrivacyItem(
            icon = Icons.Default.Security,
            title = "Kept on your phone",
            description = "Your habits, voice notes, and routines stay private on your device."
        )

        Spacer(modifier = Modifier.height(12.dp))

        PrivacyItem(
            icon = Icons.Outlined.Lock,
            title = "You choose what is remembered",
            description = "Review, edit, or delete any memory whenever you want."
        )

        Spacer(modifier = Modifier.height(12.dp))

        PrivacyItem(
            icon = Icons.Outlined.VisibilityOff,
            title = "No ads or tracking",
            description = "Comai is built purely to help you, not to profile you for ads."
        )

        Spacer(modifier = Modifier.height(36.dp))

        Button(
            onClick = onContinue,
            colors = ButtonDefaults.buttonColors(containerColor = ElectricTeal),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(
                text = "I Understand",
                color = DarkBackground,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PrivacyItem(icon: ImageVector, title: String, description: String) {
    Surface(
        color = DarkSurfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(DarkSurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = OnlineGreen,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════
//  Step 8: Finish (You're all set)
// ═════════════════════════════════════════════════════════════════════
@Composable
private fun FinishStepView(onLaunch: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        SiriVoiceOrb(
            state = VoiceState.SPEAKING,
            orbSize = 140.dp
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "You're all set.",
            color = TextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Comai is ready to get to know your routine.",
            color = TextSecondary,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onLaunch,
            colors = ButtonDefaults.buttonColors(containerColor = ElectricTeal),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(
                text = "Go to Comai",
                color = DarkBackground,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
