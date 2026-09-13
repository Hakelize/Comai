package com.comai.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.comai.R
import com.comai.ui.theme.TextPrimary
import com.comai.ui.theme.TextSecondary
import com.comai.voice.ComaiLanguage
import java.util.Calendar

object HomeGreetingUtils {

    /**
     * Computes the dynamic greeting based on real device time, selected UI language, and saved user profile name.
     * Absolutely NO hardcoded name is used.
     * If name is blank or missing, gracefully falls back to the time-based greeting alone.
     */
    fun computeGreeting(
        name: String,
        calendar: Calendar
    ): String = computeGreeting(name, ComaiLanguage.ENGLISH, calendar)

    fun computeGreeting(
        name: String,
        language: ComaiLanguage = ComaiLanguage.ENGLISH,
        calendar: Calendar = Calendar.getInstance()
    ): String {
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val cleanName = name.trim()

        val salutation = when (language) {
            ComaiLanguage.TAMIL -> when (hour) {
                in 5..11 -> "காலை வணக்கம்"
                in 12..16 -> "மதிய வணக்கம்"
                in 17..21 -> "மாலை வணக்கம்"
                else -> "இரவு வணக்கம்"
            }
            ComaiLanguage.HINDI -> when (hour) {
                in 5..11 -> "शुभ प्रभात"
                in 12..16 -> "शुभ दोपहर"
                in 17..21 -> "शुभ संध्या"
                else -> "शुभ रात्रि"
            }
            ComaiLanguage.TELUGU -> when (hour) {
                in 5..11 -> "శుభోదయం"
                in 12..16 -> "శుభ మధ్యాహ్నం"
                in 17..21 -> "శుభ సాయంత్రం"
                else -> "శుభ రాత్రి"
            }
            ComaiLanguage.MALAYALAM -> when (hour) {
                in 5..11 -> "സുപ്രഭാതം"
                in 12..16 -> "ശുഭ ഉച്ചതിരിഞ്ഞ്"
                in 17..21 -> "ശുഭ സായാഹ്നം"
                else -> "ശുഭ രാത്രി"
            }
            ComaiLanguage.ENGLISH, ComaiLanguage.TANGLISH -> when (hour) {
                in 5..11 -> "Good morning"
                in 12..16 -> "Good afternoon"
                in 17..21 -> "Good evening"
                else -> "Good night"
            }
        }

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
            text = stringResource(R.string.how_can_i_help),
            color = TextSecondary.copy(alpha = 0.7f),
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}
