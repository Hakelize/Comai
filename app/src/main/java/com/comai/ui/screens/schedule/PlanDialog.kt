package com.comai.ui.screens.schedule

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.comai.data.models.PersonalPlan
import com.comai.ui.components.ComaiCompactTimeBar
import com.comai.ui.theme.*

/**
 * Dialog for creating or editing a personal schedule plan.
 */
@Composable
fun PlanDialog(
    initialPlan: PersonalPlan? = null,
    onDismiss: () -> Unit,
    onSave: (title: String, time: String, repeatFrequency: String) -> Unit
) {
    var title by remember { mutableStateOf(initialPlan?.title ?: "") }
    var time by remember { mutableStateOf(initialPlan?.time ?: "10:00 AM") }
    var repeatFrequency by remember { mutableStateOf(initialPlan?.repeatFrequency ?: "Daily") }
    var titleError by remember { mutableStateOf<String?>(null) }

    val quickTitles = listOf("Drink water", "Call Mom", "Go to gym", "Take a break", "Study", "Medication")
    val repeatOptions = listOf("Daily", "Weekdays", "Weekends", "Mon, Wed, Fri", "Once")

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
                    text = if (initialPlan == null) "Add Personal Plan" else "Edit Plan",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Title field
                Text(
                    text = "Plan Title *",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        titleError = null
                    },
                    placeholder = { Text("e.g. Drink water") },
                    singleLine = true,
                    isError = titleError != null,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricTeal,
                        unfocusedBorderColor = DarkSurface,
                        errorBorderColor = Color(0xFFE57373),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = DarkSurfaceVariant,
                        unfocusedContainerColor = DarkSurfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (titleError != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = titleError ?: "",
                        color = Color(0xFFE57373),
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Quick suggestions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickTitles.forEach { suggestion ->
                        Surface(
                            color = Color(0xFF182230),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFF222E42)),
                            modifier = Modifier.clickable { title = suggestion }
                        ) {
                            Text(
                                text = suggestion,
                                color = if (title == suggestion) ElectricTeal else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Time picker
                Text(
                    text = "Time *",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                ComaiCompactTimeBar(
                    value = time,
                    onValueChange = { time = it },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Repeat frequency
                Text(
                    text = "Repeat",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeatOptions.forEach { opt ->
                        val isSelected = opt == repeatFrequency
                        Surface(
                            color = if (isSelected) ElectricTeal else Color(0xFF182230),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, if (isSelected) ElectricTeal else Color(0xFF222E42)),
                            modifier = Modifier
                                .clickable { repeatFrequency = opt }
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = opt,
                                    color = if (isSelected) DarkBackground else TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF28344A)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        Text("Cancel", fontSize = 14.sp)
                    }

                    Button(
                        onClick = {
                            if (title.trim().isBlank()) {
                                titleError = "Please enter a plan title"
                            } else {
                                onSave(title.trim(), time, repeatFrequency)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricTeal),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        Text(
                            text = if (initialPlan == null) "Save Plan" else "Update",
                            color = DarkBackground,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
