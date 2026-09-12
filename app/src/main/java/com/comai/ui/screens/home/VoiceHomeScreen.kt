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
import com.comai.data.models.ChatMessage
import com.comai.ui.components.ComaiBottomBar
import com.comai.ui.components.HomeGreeting
import com.comai.ui.components.HomeGreetingUtils
import com.comai.ui.components.SiriVoiceOrb
import com.comai.ui.components.VoiceSubtitle
import com.comai.ui.navigation.Routes
import com.comai.ui.screens.chat.ChatViewModel
import com.comai.ui.theme.*
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
    val greetingText = remember(userName) {
        HomeGreetingUtils.computeGreeting(userName)
    }

    // ── Observe voice state ──────────────────────────────────────────
    val voiceState by voiceManager?.state?.collectAsState()
        ?: remember { mutableStateOf(VoiceState.IDLE) }
    val partialText by voiceManager?.partialText?.collectAsState()
        ?: remember { mutableStateOf("") }
    val rmsDb by voiceManager?.rmsDb?.collectAsState()
        ?: remember { mutableStateOf(0f) }
    val errorMessage by voiceManager?.errorMessage?.collectAsState()
        ?: remember { mutableStateOf(null) }

    // ── Latest AI response ───────────────────────────────────────────
    val messages by chatViewModel.messages.collectAsState()
    val initialMessageCount = remember { messages.size }
    val lastAiMessage: ChatMessage? = remember(messages) {
        messages.lastOrNull { !it.isFromUser }
    }

    // ── Greeting Lifecycle: disappears as soon as voice interaction begins ──
    var hasUserInteracted by remember { mutableStateOf(false) }

    LaunchedEffect(partialText, voiceState, messages.size) {
        if (!hasUserInteracted) {
            if (partialText.isNotBlank() ||
                voiceState == VoiceState.PROCESSING ||
                voiceState == VoiceState.SPEAKING ||
                messages.size > initialMessageCount
            ) {
                hasUserInteracted = true
            }
        }
    }

    // ── State label text ─────────────────────────────────────────────
    val stateLabel = when (voiceState) {
        VoiceState.IDLE -> "Tap to talk"
        VoiceState.LISTENING -> "Listening..."
        VoiceState.PROCESSING -> "Thinking..."
        VoiceState.SPEAKING -> "Speaking..."
        VoiceState.ERROR -> "Tap to retry"
    }

    val stateColor = when (voiceState) {
        VoiceState.IDLE -> TextSecondary
        VoiceState.LISTENING -> OnlineGreen
        VoiceState.PROCESSING -> WarmAmber
        VoiceState.SPEAKING -> ElectricTeal
        VoiceState.ERROR -> ErrorRed
    }

    // Automatically enter listening state when opening Home
    LaunchedEffect(Unit) {
        homeViewModel.refreshState()
        if (voiceManager?.state?.value == VoiceState.IDLE) {
            voiceManager.startListening()
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
                aiResponse = lastAiMessage?.content,
                errorMessage = errorMessage,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp)
            )
        }
    }
}
