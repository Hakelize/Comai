package com.lifeloop.comai.ui.screens.audio

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
import com.lifeloop.comai.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioCanvasScreen(
    viewModel: AudioViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val spokenText by viewModel.spokenText.collectAsState()
    val responseText by viewModel.aiResponseText.collectAsState()

    // Breathing pulse animation for the orb
    val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

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
                    text = when (state) {
                        AudioState.IDLE -> "TAP TO SPEAK"
                        AudioState.LISTENING -> "LISTENING..."
                        AudioState.PROCESSING -> "PROCESSING..."
                        AudioState.SPEAKING -> "CAMOI SPEAKING..."
                    },
                    color = when (state) {
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
                    text = spokenText,
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
                        onClick = { viewModel.onOrbClicked() }
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Outer glow halo
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val radius = size.minDimension / 2 * pulseScale
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                when (state) {
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
                                colors = when (state) {
                                    AudioState.IDLE -> listOf(OrbCoreColor, Color(0xFF3F51B5))
                                    AudioState.LISTENING -> listOf(Color(0xFF00E676), Color(0xFF00B0FF))
                                    AudioState.PROCESSING -> listOf(Color(0xFFFF9100), Color(0xFFFF5252))
                                    AudioState.SPEAKING -> listOf(SoftPurple, UserBubbleColor)
                                }
                            )
                        )
                )
            }

            // AI Response Subtitle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp)
                    .padding(bottom = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                if (responseText.isNotBlank()) {
                    Text(
                        text = responseText,
                        color = TextSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
