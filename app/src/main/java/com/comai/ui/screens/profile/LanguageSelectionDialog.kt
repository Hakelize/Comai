package com.comai.ui.screens.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import com.comai.R
import com.comai.ui.theme.*
import com.comai.voice.ComaiLanguage

@Composable
fun LanguageSelectionDialog(
    currentLanguage: ComaiLanguage,
    onDismiss: () -> Unit,
    onLanguageSelected: (ComaiLanguage) -> Unit
) {
    val languages = listOf(
        ComaiLanguage.ENGLISH to "English",
        ComaiLanguage.TAMIL to "தமிழ் (Tamil)",
        ComaiLanguage.TELUGU to "తెలుగు (Telugu)",
        ComaiLanguage.HINDI to "हिन्दी (Hindi)",
        ComaiLanguage.MALAYALAM to "മലയാളം (Malayalam)",
        ComaiLanguage.TANGLISH to "Tanglish (Mixed Language)"
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = Color(0xFF131722),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color(0xFF222B3D)),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = stringResource(R.string.lang_dialog_title),
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = stringResource(R.string.lang_dialog_sub),
                    color = TextSecondary,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    languages.forEach { (lang, label) ->
                        val isSelected = lang == currentLanguage
                        Surface(
                            color = if (isSelected) Color(0xFF0F252E) else Color(0xFF161C28),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, if (isSelected) ElectricTeal else Color(0xFF222B3D)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onLanguageSelected(lang)
                                    onDismiss()
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) ElectricTeal else TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )

                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = ElectricTeal,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(R.string.close), color = TextSecondary, fontSize = 14.sp)
                }
            }
        }
    }
}
