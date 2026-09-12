package com.comai.ui.screens.audio

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.comai.ui.theme.*
import com.comai.voice.VoiceInteractionManager
import com.comai.voice.VoiceState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioCanvasScreen(
    viewModel: AudioViewModel,
    voiceManager: VoiceInteractionManager? = null,
    onBack: () -> Unit
) {
    val simulatedState by viewModel.state.collectAsState()
    val simulatedSpoken by viewModel.spokenText.collectAsState()
    val responseText by viewModel.aiResponseText.collectAsState()

    val realVoiceState by voiceManager?.state?.collectAsState() ?: remember { mutableStateOf(VoiceState.IDLE) }
    val realPartialText by voiceManager?.partialText?.collectAsState() ?: remember { mutableStateOf("") }
    val rmsDb by voiceManager?.rmsDb?.collectAsState() ?: remember { mutableStateOf(0f) }

    val isRealVoice = voiceManager != null
    val currentState = if (isRealVoice) {
        when (realVoiceState) {
            VoiceState.IDLE -> AudioState.IDLE
            VoiceState.LISTENING -> AudioState.LISTENING
            VoiceState.PROCESSING -> AudioState.PROCESSING
            VoiceState.SPEAKING -> AudioState.SPEAKING
            VoiceState.ERROR -> AudioState.IDLE
        }
    } else {
        simulatedState
    }

    val displaySpokenText = if (isRealVoice) {
        when (realVoiceState) {
            VoiceState.IDLE -> "Tap the orb to start speaking with Comai"
            VoiceState.LISTENING -> realPartialText.ifBlank { "Listening... speak now" }
            VoiceState.PROCESSING -> "Processing your request..."
            VoiceState.SPEAKING -> "Comai is speaking..."
            VoiceState.ERROR -> "Voice error — tap to try again"
        }
    } else {
        simulatedSpoken
    }

    // Breathing pulse animation for the orb
    val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")
    val basePulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Dynamic RMS reaction during real listening
    val pulseScale = if (currentState == AudioState.LISTENING && rmsDb > 0) {
        (1.0f + (rmsDb.coerceIn(0f, 10f) / 10f) * 0.35f)
    } else {
        basePulseScale
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Voice Canvas", color = Color.White, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // State & Prompt Card
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = when (currentState) {
                        AudioState.IDLE -> "TAP TO SPEAK"
                        AudioState.LISTENING -> "LISTENING..."
                        AudioState.PROCESSING -> "PROCESSING..."
                        AudioState.SPEAKING -> "COMAI SPEAKING..."
                    },
                    color = when (currentState) {
                        AudioState.IDLE -> ElectricTeal
                        AudioState.LISTENING -> OnlineGreen
                        AudioState.PROCESSING -> WarmAmber
                        AudioState.SPEAKING -> UserBubbleColor
                    },
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = displaySpokenText,
                    color = TextPrimary,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium
                )
            }

            // Central Glowing Orb
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            if (voiceManager != null) {
                                when (realVoiceState) {
                                    VoiceState.LISTENING -> voiceManager.stopListening()
                                    VoiceState.SPEAKING -> voiceManager.cancel()
                                    else -> voiceManager.startListening()
                                }
                            } else {
                                viewModel.onOrbClicked()
                            }
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Outer glow halo
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val radius = size.minDimension / 2 * pulseScale
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                when (currentState) {
                                    AudioState.IDLE -> OrbGlowColor.copy(alpha = 0.35f)
                                    AudioState.LISTENING -> OnlineGreen.copy(alpha = 0.45f)
                                    AudioState.PROCESSING -> WarmAmber.copy(alpha = 0.45f)
                                    AudioState.SPEAKING -> OrbCoreColor.copy(alpha = 0.55f)
                                },
                                Color.Transparent
                            )
                        ),
                        radius = radius
                    )
                }

                // Inner Orb Core
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = when (currentState) {
                                    AudioState.IDLE -> listOf(OrbCoreColor, Color(0xFF3F51B5))
                                    AudioState.LISTENING -> listOf(Color(0xFF00E676), Color(0xFF00B0FF))
                                    AudioState.PROCESSING -> listOf(Color(0xFFFFB300), Color(0xFFFF7043))
                                    AudioState.SPEAKING -> listOf(Color(0xFF9C27B0), Color(0xFF2979FF))
                                }
                            )
                        )
                )
            }

            // Bottom status info
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = CircleShape,
                modifier = Modifier
                    .padding(bottom = 32.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = when (currentState) {
                        AudioState.IDLE -> "Single-tap push-to-talk enabled"
                        AudioState.LISTENING -> "Speaking now — tap to finish early"
                        AudioState.PROCESSING -> "Synthesizing AI response..."
                        AudioState.SPEAKING -> "Tap orb to interrupt speech"
                    },
                    color = TextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 16.dp)
                )
            }
        }
    }
}
