package com.comai.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.comai.voice.VoiceState
import kotlinx.coroutines.delay

/**
 * Movie subtitle / caption style UI for live spoken interactions on the Voice Home screen.
 * Displays live speech feedback subtly without looking like a chat bubble.
 */
@Composable
fun VoiceSubtitle(
    voiceState: VoiceState,
    userSpeech: String,
    aiResponse: String?,
    errorMessage: String? = null,
    modifier: Modifier = Modifier
) {
    // Current text to display as live subtitle
    val activeText = when (voiceState) {
        VoiceState.LISTENING -> userSpeech.trim()
        VoiceState.PROCESSING -> userSpeech.trim()
        VoiceState.SPEAKING -> aiResponse?.trim() ?: ""
        VoiceState.ERROR -> errorMessage ?: "Tap orb to retry"
        VoiceState.IDLE -> ""
    }

    // Auto-fade timer when going idle
    var displayedText by remember { mutableStateOf("") }
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(activeText, voiceState) {
        if (activeText.isNotBlank()) {
            displayedText = activeText
            isVisible = true
        } else if (voiceState == VoiceState.IDLE) {
            // Give user 3.5 seconds to read the final AI subtitle after speaking completes
            delay(3500)
            isVisible = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = isVisible && displayedText.isNotBlank(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .background(
                        color = Color(0x660B0E17),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = displayedText,
                    color = Color(0xFFEDF2F7),
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
