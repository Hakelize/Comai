package com.comai.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.comai.ui.theme.TextPrimary
import com.comai.ui.theme.TextSecondary
import java.util.Calendar

object HomeGreetingUtils {

    /**
     * Computes the dynamic greeting based on real device time and saved user profile name.
     * Absolutely NO hardcoded name is used.
     * If name is blank or missing, gracefully falls back to the time-based greeting alone.
     */
    fun computeGreeting(name: String, calendar: Calendar = Calendar.getInstance()): String {
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val salutation = when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Good night"
        }

        val cleanName = name.trim()
        return if (cleanName.isNotBlank()) {
            "$salutation, $cleanName"
        } else {
            salutation
        }
    }
}

/**
 * HomeGreeting: Visual greeting displayed above the voice orb.
 */
@Composable
fun HomeGreeting(
    greeting: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = greeting,
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "How can I help you today?",
            color = TextSecondary.copy(alpha = 0.7f),
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}
