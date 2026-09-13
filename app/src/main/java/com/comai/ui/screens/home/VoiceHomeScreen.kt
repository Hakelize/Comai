package com.comai.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.comai.R
import com.comai.data.models.ChatMessage
import com.comai.ui.components.ComaiBottomBar
import com.comai.ui.components.HomeGreeting
import com.comai.ui.components.HomeGreetingUtils
import com.comai.ui.components.SiriVoiceOrb
import com.comai.ui.components.VoiceSubtitle
import com.comai.ui.navigation.Routes
import com.comai.ui.screens.chat.ChatViewModel
import com.comai.ui.theme.*
import com.comai.voice.ConversationGreetingManager
import com.comai.voice.VoiceInteractionManager
import com.comai.voice.VoiceState

@Composable
fun VoiceHomeScreen(
    chatViewModel: ChatViewModel,
    homeViewModel: HomeViewModel,
    voiceManager: VoiceInteractionManager? = null,
    onNavigateToChat: () -> Unit,
    onNavigateToRoutine: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToMemory: () -> Unit,
    onNavigateToDashboard: () -> Unit = {}
) {
    // ── Observe user profile for dynamic name ────────────────────────
    val homeUiState by homeViewModel.uiState.collectAsState()
    val userName = homeUiState.profile.name

    // ── Latest AI response & conversation context ────────────────────
    val messages by chatViewModel.messages.collectAsState()
    val initialMessageCount = remember { messages.size }
    val isReturningConversation = remember(initialMessageCount) { initialMessageCount > 0 }
    val lastAiMessage: ChatMessage? = remember(messages) {
        messages.lastOrNull { !it.isFromUser }
    }

    val preferredLanguage = homeUiState.profile.preferredLanguage

    // Dynamic, context-aware spoken greeting using saved profile name (NEVER hardcoded)
    val spokenGreeting = remember(userName, preferredLanguage, isReturningConversation) {
        ConversationGreetingManager.generateGreeting(
            userName = userName,
            uiLanguage = preferredLanguage,
            isReturning = isReturningConversation
        )
    }

    val greetingText = remember(userName, preferredLanguage) {
        HomeGreetingUtils.computeGreeting(userName, preferredLanguage)
    }

    // ── Observe voice state ──────────────────────────────────────────
    val idleVoiceStateFlow = remember { kotlinx.coroutines.flow.MutableStateFlow(VoiceState.IDLE) }
    val emptyPartialTextFlow = remember { kotlinx.coroutines.flow.MutableStateFlow("") }
    val zeroRmsDbFlow = remember { kotlinx.coroutines.flow.MutableStateFlow(0f) }
    val nullErrorFlow = remember { kotlinx.coroutines.flow.MutableStateFlow<String?>(null) }

    val voiceState by (voiceManager?.state ?: idleVoiceStateFlow).collectAsState()
    val partialText by (voiceManager?.partialText ?: emptyPartialTextFlow).collectAsState()
    val rmsDb by (voiceManager?.rmsDb ?: zeroRmsDbFlow).collectAsState()
    val errorMessage by (voiceManager?.errorMessage ?: nullErrorFlow).collectAsState()

    // ── Greeting Lifecycle: disappears once user speech / processing begins ──
    var hasUserInteracted by remember { mutableStateOf(false) }

    LaunchedEffect(partialText, voiceState, messages.size) {
        if (!hasUserInteracted) {
            if (partialText.isNotBlank() ||
                voiceState == VoiceState.PROCESSING ||
                messages.size > initialMessageCount
            ) {
                hasUserInteracted = true
            }
        }
    }

    // ── State label text ─────────────────────────────────────────────
    val stateLabel = when (voiceState) {
        VoiceState.IDLE -> stringResource(R.string.tap_to_talk)
        VoiceState.LISTENING -> stringResource(R.string.listening)
        VoiceState.PROCESSING -> stringResource(R.string.thinking)
        VoiceState.SPEAKING -> stringResource(R.string.speaking)
        VoiceState.ERROR -> stringResource(R.string.tap_to_retry)
    }

    val stateColor = when (voiceState) {
        VoiceState.IDLE -> TextSecondary
        VoiceState.LISTENING -> OnlineGreen
        VoiceState.PROCESSING -> WarmAmber
        VoiceState.SPEAKING -> ElectricTeal
        VoiceState.ERROR -> ErrorRed
    }

    // Automatically speak greeting once per session then enter listening state when opening Home
    // Uses homeViewModel.hasInitialGreetingPlayed so configuration changes (screen rotations) do NOT restart greeting
    LaunchedEffect(Unit) {
        homeViewModel.refreshState()
        if (!homeViewModel.hasInitialGreetingPlayed && voiceManager?.state?.value == VoiceState.IDLE) {
            homeViewModel.hasInitialGreetingPlayed = true
            voiceManager.speakGreetingAndListen(spokenGreeting)
        }
    }

    Scaffold(
        containerColor = DarkBackground,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            Column(modifier = Modifier.navigationBarsPadding()) {
                ComaiBottomBar(
                    currentRoute = Routes.HOME,
                    onNavigate = { targetRoute ->
                        when (targetRoute) {
                            Routes.CHAT -> onNavigateToChat()
                            Routes.ROUTINE -> onNavigateToRoutine()
                            Routes.PROFILE -> onNavigateToProfile()
                            Routes.MEMORY -> onNavigateToMemory()
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ── Top branding (Clean, minimal, no cards, no language selector) ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "COMAI",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp
                )
            }

            // ── Central Voice Orb Focus & Dynamic Greeting ───────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Dynamic time-based greeting above the orb
                AnimatedVisibility(
                    visible = !hasUserInteracted,
                    enter = fadeIn(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        HomeGreeting(greeting = greetingText)
                        Spacer(modifier = Modifier.height(36.dp))
                    }
                }

                // Large central Siri-style Voice Orb
                SiriVoiceOrb(
                    state = voiceState,
                    rmsDb = rmsDb,
                    orbSize = 250.dp,
                    onClick = {
                        when (voiceState) {
                            VoiceState.LISTENING -> voiceManager?.stopListening()
                            VoiceState.SPEAKING -> voiceManager?.cancel()
                            else -> voiceManager?.startListening()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Voice state indication
                Text(
                    text = stateLabel,
                    color = stateColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp
                )
            }

            // ── Movie Subtitle / Live Spoken Interaction ─────────────
            VoiceSubtitle(
                voiceState = voiceState,
                userSpeech = partialText,
                aiResponse = lastAiMessage?.content ?: if (voiceState == VoiceState.SPEAKING && !hasUserInteracted) spokenGreeting else null,
                errorMessage = errorMessage,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp)
            )
        }
    }
}
