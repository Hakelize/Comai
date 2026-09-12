package com.comai.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.comai.data.models.ChatMessage
import com.comai.ui.components.ComaiBottomBar
import com.comai.ui.components.SiriVoiceOrb
import com.comai.ui.navigation.Routes
import com.comai.ui.screens.chat.ChatViewModel
import com.comai.ui.theme.*
import com.comai.voice.ComaiLanguage
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
    onNavigateToDashboard: () -> Unit = {},
    onLanguageChanged: (ComaiLanguage) -> Unit = {}
) {
    // ── Observe home state ──────────────────────────────────────────
    val homeState by homeViewModel.uiState.collectAsState()

    // ── Observe voice state ──────────────────────────────────────────
    val voiceState by voiceManager?.state?.collectAsState()
        ?: remember { mutableStateOf(VoiceState.IDLE) }
    val partialText by voiceManager?.partialText?.collectAsState()
        ?: remember { mutableStateOf("") }
    val rmsDb by voiceManager?.rmsDb?.collectAsState()
        ?: remember { mutableStateOf(0f) }

    // ── Language state ───────────────────────────────────────────────
    val currentLanguage by voiceManager?.language?.collectAsState()
        ?: remember { mutableStateOf(ComaiLanguage.ENGLISH) }
    val errorMessage by voiceManager?.errorMessage?.collectAsState()
        ?: remember { mutableStateOf(null) }
    var showLanguagePicker by remember { mutableStateOf(false) }

    // ── Latest AI response ───────────────────────────────────────────
    val messages by chatViewModel.messages.collectAsState()
    val lastAiMessage: ChatMessage? = remember(messages) {
        messages.lastOrNull { !it.isFromUser }
    }

    // ── State label text ─────────────────────────────────────────────
    val stateLabel = when (voiceState) {
        VoiceState.IDLE -> "Tap to talk"
        VoiceState.LISTENING -> "Listening..."
        VoiceState.PROCESSING -> "Thinking..."
        VoiceState.SPEAKING -> "Speaking..."
        VoiceState.ERROR -> "Something went wrong. Tap to try again."
    }

    val stateColor = when (voiceState) {
        VoiceState.IDLE -> TextSecondary
        VoiceState.LISTENING -> OnlineGreen
        VoiceState.PROCESSING -> WarmAmber
        VoiceState.SPEAKING -> ElectricTeal
        VoiceState.ERROR -> ErrorRed
    }

    // ── Live card content ────────────────────────────────────────────
    val cardText = when (voiceState) {
        VoiceState.LISTENING -> partialText.ifBlank { null }
        VoiceState.PROCESSING -> partialText.ifBlank { null }
        VoiceState.SPEAKING -> lastAiMessage?.content
        VoiceState.ERROR -> errorMessage ?: "Something went wrong. Tap to try again."
        VoiceState.IDLE -> lastAiMessage?.content
    }
    val showCard = !cardText.isNullOrBlank()

    // Refresh state when returning to screen
    LaunchedEffect(Unit) {
        homeViewModel.refreshState()
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
            // ── Top row: branding + language selector ────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "COMAI",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp
                )

                // Language selector pill
                Box {
                    Surface(
                        color = Color(0xFF151922),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, Color(0xFF222A3A)),
                        modifier = Modifier.clickable { showLanguagePicker = !showLanguagePicker }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Translate,
                                contentDescription = "Language",
                                tint = ElectricTeal,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = currentLanguage.displayName,
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showLanguagePicker,
                        onDismissRequest = { showLanguagePicker = false },
                        modifier = Modifier.background(DarkSurface)
                    ) {
                        ComaiLanguage.entries.forEach { lang ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = lang.displayName,
                                        color = if (lang == currentLanguage) ElectricTeal else TextPrimary,
                                        fontWeight = if (lang == currentLanguage) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    onLanguageChanged(lang)
                                    showLanguagePicker = false
                                }
                            )
                        }
                    }
                }
            }

            // ── Central content: greeting + orb + state label ────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Subtle greeting
                Text(
                    text = homeState.greeting,
                    color = TextSecondary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.3.sp
                )

                Spacer(modifier = Modifier.height(36.dp))

                // ── Central Siri-style Voice Orb ─────────────────────
                SiriVoiceOrb(
                    state = voiceState,
                    rmsDb = rmsDb,
                    orbSize = 240.dp,
                    onClick = {
                        when (voiceState) {
                            VoiceState.LISTENING -> voiceManager?.stopListening()
                            VoiceState.SPEAKING -> voiceManager?.cancel()
                            else -> voiceManager?.startListening()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // State label
                Text(
                    text = stateLabel,
                    color = stateColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // ── Speech / Response Floating Card ──────────────────
                AnimatedVisibility(
                    visible = showCard,
                    enter = fadeIn() + slideInVertically { it / 3 },
                    exit = fadeOut() + slideOutVertically { it / 3 }
                ) {
                    Surface(
                        color = Color(0xFF131722),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0xFF202738)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(34.dp)
                                    .background(
                                        stateColor,
                                        RoundedCornerShape(2.dp)
                                    )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = cardText ?: "",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
