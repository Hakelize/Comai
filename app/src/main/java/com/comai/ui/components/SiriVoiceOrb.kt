package com.comai.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.comai.voice.VoiceState
import kotlin.math.cos
import kotlin.math.sin

/**
 * Apple Siri-style multi-chromatic animated voice orb.
 *
 * Implements fluid radial gradients, breathing aura, and RMS-reactive acoustic waves
 * for voice interaction states (IDLE, LISTENING, PROCESSING, SPEAKING, ERROR).
 */
@Composable
fun SiriVoiceOrb(
    state: VoiceState,
    rmsDb: Float = 0f,
    modifier: Modifier = Modifier,
    orbSize: Dp = 240.dp,
    onClick: () -> Unit = {}
) {
    // ── Continuous rotation & breathing animations ──────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "siriOrbAnimations")

    // Slow organic rotation for chromatic layers
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    VoiceState.LISTENING -> 4500
                    VoiceState.PROCESSING -> 2200
                    VoiceState.SPEAKING -> 3200
                    VoiceState.ERROR -> 8000
                    VoiceState.IDLE -> 6000
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotationAngle"
    )

    // Fluid organic breathing scale
    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    VoiceState.LISTENING -> 1400
                    VoiceState.PROCESSING -> 850
                    VoiceState.SPEAKING -> 1100
                    VoiceState.ERROR -> 2000
                    VoiceState.IDLE -> 2400
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathingScale"
    )

    // Dynamic scale driven by audio input (RMS) when listening
    val normalizedRms = remember(rmsDb) {
        ((rmsDb - 2f) / 10f).coerceIn(0f, 1f)
    }
    val activeScale = when (state) {
        VoiceState.LISTENING -> breathingScale * (1f + normalizedRms * 0.18f)
        VoiceState.PROCESSING -> breathingScale * 1.04f
        VoiceState.SPEAKING -> breathingScale * 1.08f
        VoiceState.ERROR -> 0.95f
        VoiceState.IDLE -> breathingScale
    }

    // Dynamic chromatic colors mapped to state
    val animatedPrimaryGlow by animateColorAsState(
        targetValue = when (state) {
            VoiceState.IDLE -> Color(0xFF007AFF)       // Electric Apple Blue
            VoiceState.LISTENING -> Color(0xFF00E5FF)  // Vibrant Cyan
            VoiceState.PROCESSING -> Color(0xFFAF52DE) // Royal Purple
            VoiceState.SPEAKING -> Color(0xFFFF2D55)   // Magenta Crimson
            VoiceState.ERROR -> Color(0xFFFF3B30)      // Coral Red
        },
        animationSpec = tween(durationMillis = 500),
        label = "primaryGlow"
    )

    val animatedSecondaryGlow by animateColorAsState(
        targetValue = when (state) {
            VoiceState.IDLE -> Color(0xFFAF52DE)      // Vibrant Violet
            VoiceState.LISTENING -> Color(0xFF34C759) // Emerald Green
            VoiceState.PROCESSING -> Color(0xFFFF2D55)// Electric Pink
            VoiceState.SPEAKING -> Color(0xFFFF9500)  // Sunset Amber
            VoiceState.ERROR -> Color(0xFFFF9500)     // Amber alert
        },
        animationSpec = tween(durationMillis = 500),
        label = "secondaryGlow"
    )

    val animatedTertiaryGlow by animateColorAsState(
        targetValue = when (state) {
            VoiceState.IDLE -> Color(0xFF5856D6)       // Indigo Deep
            VoiceState.LISTENING -> Color(0xFF007AFF)  // Bright Blue
            VoiceState.PROCESSING -> Color(0xFF00C7BE) // Deep Teal
            VoiceState.SPEAKING -> Color(0xFFFF375F)   // Neon Pink
            VoiceState.ERROR -> Color(0xFF8E8E93)      // Muted Gray
        },
        animationSpec = tween(durationMillis = 500),
        label = "tertiaryGlow"
    )

    Box(
        modifier = modifier
            .size(orbSize)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // ── Canvas: Multi-layer organic chromatic bloom ─────────────────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension / 2f * 0.42f

            // 1. Acoustic resonance rings (LISTENING mode)
            if (state == VoiceState.LISTENING) {
                val ringCount = 3
                for (i in 1..ringCount) {
                    val ringRadius = baseRadius + (i * 24.dp.toPx()) * (0.6f + normalizedRms * 0.5f)
                    val alpha = (0.28f - i * 0.08f).coerceAtLeast(0.04f)
                    drawCircle(
                        color = animatedPrimaryGlow.copy(alpha = alpha),
                        radius = ringRadius,
                        center = center,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }

            // 2. Diffuse atmospheric halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        animatedPrimaryGlow.copy(alpha = 0.35f),
                        animatedSecondaryGlow.copy(alpha = 0.18f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * 2.2f * activeScale
                ),
                radius = baseRadius * 2.2f * activeScale,
                center = center
            )

            // 3. Chromatic focal lobes (rotating phase offset)
            val rad = Math.toRadians(rotationAngle.toDouble())
            val offsetDist = baseRadius * 0.35f
            val lobe1Offset = center + Offset((cos(rad).toFloat() * offsetDist), (sin(rad).toFloat() * offsetDist))
            val lobe2Offset = center + Offset((cos(rad + 2.1).toFloat() * offsetDist), (sin(rad + 2.1).toFloat() * offsetDist))
            val lobe3Offset = center + Offset((cos(rad + 4.2).toFloat() * offsetDist), (sin(rad + 4.2).toFloat() * offsetDist))

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(animatedPrimaryGlow.copy(alpha = 0.55f), Color.Transparent),
                    center = lobe1Offset,
                    radius = baseRadius * 1.4f
                ),
                radius = baseRadius * 1.4f,
                center = lobe1Offset,
                blendMode = BlendMode.Plus
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(animatedSecondaryGlow.copy(alpha = 0.50f), Color.Transparent),
                    center = lobe2Offset,
                    radius = baseRadius * 1.4f
                ),
                radius = baseRadius * 1.4f,
                center = lobe2Offset,
                blendMode = BlendMode.Plus
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(animatedTertiaryGlow.copy(alpha = 0.45f), Color.Transparent),
                    center = lobe3Offset,
                    radius = baseRadius * 1.4f
                ),
                radius = baseRadius * 1.4f,
                center = lobe3Offset,
                blendMode = BlendMode.Plus
            )
        }

        // ── Frosted glass inner core with rotating gradient rim ────────────
        val coreSize = orbSize * 0.46f
        Box(
            modifier = Modifier
                .size(coreSize)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF0D111A),
                            Color(0xFF05070B)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            // Rotating chromatic rim shader
            Canvas(modifier = Modifier.fillMaxSize().graphicsLayer { rotationZ = rotationAngle }) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            animatedPrimaryGlow.copy(alpha = 0.85f),
                            animatedSecondaryGlow.copy(alpha = 0.75f),
                            animatedTertiaryGlow.copy(alpha = 0.85f),
                            animatedPrimaryGlow.copy(alpha = 0.85f)
                        )
                    ),
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            // Minimalist status icon
            Icon(
                imageVector = when (state) {
                    VoiceState.LISTENING -> Icons.Default.Mic
                    VoiceState.PROCESSING -> Icons.Outlined.Sync
                    VoiceState.SPEAKING -> Icons.Outlined.GraphicEq
                    VoiceState.ERROR -> Icons.Default.MicOff
                    VoiceState.IDLE -> Icons.Default.Mic
                },
                contentDescription = state.name,
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(coreSize * 0.38f)
            )
        }
    }
}
