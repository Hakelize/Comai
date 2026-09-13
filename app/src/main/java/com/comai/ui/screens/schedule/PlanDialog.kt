package com.comai.ui.screens.schedule

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.comai.R
import com.comai.data.models.PersonalPlan
import com.comai.data.models.ReminderType
import com.comai.scheduling.AlarmPermissionUtils
import com.comai.ui.components.ComaiCompactTimeBar
import com.comai.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Dialog for creating or editing a personal schedule plan with date, time,
 * repeat frequency, and notification/alarm selection.
 */
@Composable
fun PlanDialog(
    initialPlan: PersonalPlan? = null,
    initialDate: String? = null,
    onDismiss: () -> Unit,
    onSave: (title: String, time: String, repeatFrequency: String, reminderType: ReminderType, date: String?) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(initialPlan?.title ?: "") }
    var time by remember { mutableStateOf(initialPlan?.time ?: "09:00 AM") }
    var repeatFrequency by remember { mutableStateOf(initialPlan?.repeatFrequency ?: "Once") }
    var reminderType by remember { mutableStateOf(initialPlan?.reminderType ?: ReminderType.NOTIFICATION) }
    var date by remember { mutableStateOf(initialPlan?.date ?: initialDate) }
    var titleError by remember { mutableStateOf<String?>(null) }
    var canExactAlarm by remember { mutableStateOf(AlarmPermissionUtils.canScheduleExactAlarms(context)) }

    val quickTitles = listOf("Drink water", "Call Mom", "Go to gym", "Take a break", "Study", "Medication")
    val repeatOptions = listOf("Once", "Daily", "Mon-Fri", "Weekends", "Weekly", "Yearly")

    val calendar = Calendar.getInstance()
    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val picked = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, dayOfMonth)
            }
            date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(picked.time)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    val formattedDateDisplay = remember(date, repeatFrequency) {
        if (repeatFrequency == "Once" || repeatFrequency == "Weekly" || repeatFrequency == "Yearly") {
            try {
                if (date != null) {
                    val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(date!!)
                    if (parsed != null) SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()).format(parsed)
                    else date ?: "Select Date"
                } else {
                    "Today"
                }
            } catch (e: Exception) {
                date ?: "Select Date"
            }
        } else {
            "Today / Dynamic"
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .systemBarsPadding()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = Color(0xFF131722),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color(0xFF222B3D)),
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                Text(
                    text = if (initialPlan == null) stringResource(R.string.schedule_add_plan_title) else stringResource(R.string.schedule_edit_plan_title),
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Title field
                Text(
                    text = stringResource(R.string.schedule_plan_title),
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

                Spacer(modifier = Modifier.height(14.dp))

                // Date Selection
                Text(
                    text = stringResource(R.string.calendar_date),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = Color(0xFF182230),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF222E42)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { datePickerDialog.show() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.CalendarMonth,
                                contentDescription = null,
                                tint = ElectricTeal,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = formattedDateDisplay,
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            text = "Change",
                            color = ElectricTeal,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Time picker
                Text(
                    text = stringResource(R.string.schedule_time),
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

                Spacer(modifier = Modifier.height(14.dp))

                // Repeat frequency
                Text(
                    text = stringResource(R.string.schedule_repeat),
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
                            modifier = Modifier.clickable { repeatFrequency = opt }
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

                Spacer(modifier = Modifier.height(14.dp))

                // Reminder Type (Notification vs Alarm)
                Text(
                    text = stringResource(R.string.schedule_reminder_type),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val isNotification = reminderType == ReminderType.NOTIFICATION
                    Surface(
                        color = if (isNotification) ElectricTeal else Color(0xFF182230),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, if (isNotification) ElectricTeal else Color(0xFF222E42)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { reminderType = ReminderType.NOTIFICATION }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Notifications,
                                contentDescription = null,
                                tint = if (isNotification) DarkBackground else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.schedule_notification),
                                color = if (isNotification) DarkBackground else TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = if (isNotification) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }

                    val isAlarm = reminderType == ReminderType.ALARM
                    Surface(
                        color = if (isAlarm) Color(0xFFFF5252) else Color(0xFF182230),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, if (isAlarm) Color(0xFFFF5252) else Color(0xFF222E42)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { reminderType = ReminderType.ALARM }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Alarm,
                                contentDescription = null,
                                tint = if (isAlarm) Color.White else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.schedule_alarm),
                                color = if (isAlarm) Color.White else TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = if (isAlarm) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                // If Alarm is selected but exact alarm permission is missing on Android 12+
                if (reminderType == ReminderType.ALARM && !canExactAlarm) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = Color(0xFF2B1C1A),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF5C2B22)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Exact alarm permission required for exact time alerts.",
                                color = Color(0xFFFFAB91),
                                fontSize = 11.sp,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { AlarmPermissionUtils.openExactAlarmSetting(context) }) {
                                Text("Settings", color = ElectricTeal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

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
                        Text(stringResource(R.string.cancel), fontSize = 14.sp)
                    }

                    Button(
                        onClick = {
                            if (title.trim().isBlank()) {
                                titleError = "Please enter a plan title"
                            } else {
                                onSave(title.trim(), time, repeatFrequency, reminderType, date)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricTeal),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        Text(
                            text = if (initialPlan == null) stringResource(R.string.schedule_save_reminder) else stringResource(R.string.schedule_save_changes),
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
}

/**
 * 4-parameter overload of PlanDialog for full backward compatibility with existing callers.
 */
@Composable
fun PlanDialog(
    initialPlan: PersonalPlan? = null,
    onDismiss: () -> Unit,
    onSave: (title: String, time: String, repeatFrequency: String, reminderType: ReminderType) -> Unit
) {
    PlanDialog(
        initialPlan = initialPlan,
        initialDate = null,
        onDismiss = onDismiss,
        onSave = { title, time, repeatFrequency, reminderType, _ ->
            onSave(title, time, repeatFrequency, reminderType)
        }
    )
}
