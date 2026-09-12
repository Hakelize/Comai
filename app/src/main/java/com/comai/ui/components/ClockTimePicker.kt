package com.comai.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.comai.ui.theme.*
import com.comai.util.Time12
import com.comai.util.TimeUtils
import java.util.Locale
import kotlin.math.*

enum class ClockPickerMode {
    HOUR,
    MINUTE
}

/**
 * ClockTimeBar: A sleek, compact 12-hour time bar with clock icon,
 * formatted 12-hour time, and dedicated AM/PM toggle pill.
 * Clicking opens the circular Clock-Style Time Picker dialog.
 */
@Composable
fun ClockTimeBar(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    defaultTime: Time12 = Time12(8, 0, true)
) {
    var showDialog by remember { mutableStateOf(false) }
    val isSet = value.trim().isNotBlank()
    val parsedTime = remember(value) { TimeUtils.parseTime(value, defaultTime) }

    Surface(
        color = DarkSurfaceVariant,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = 1.dp,
            color = when {
                isError -> Color(0xFFE57373)
                else -> DarkSurface
            }
        ),
        modifier = modifier
            .height(52.dp)
            .clickable { showDialog = true }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Clock icon + Time digits
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Outlined.AccessTime,
                    contentDescription = "Select Time",
                    tint = if (isSet) ElectricTeal else TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isSet) {
                        String.format(Locale.US, "%d:%02d", parsedTime.hour, parsedTime.minute)
                    } else {
                        String.format(Locale.US, "%d:%02d", defaultTime.hour, defaultTime.minute)
                    },
                    color = if (isSet) TextPrimary else TextSecondary.copy(alpha = 0.6f),
                    fontSize = 15.sp,
                    fontWeight = if (isSet) FontWeight.SemiBold else FontWeight.Normal
                )
            }

            // AM / PM Toggle Pill
            Surface(
                color = Color(0xFF0F1520),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFF1E2838))
            ) {
                Row(
                    modifier = Modifier.padding(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isAmActive = isSet && parsedTime.isAm
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isAmActive) ElectricTeal else Color.Transparent)
                            .clickable {
                                val base = if (isSet) parsedTime else defaultTime
                                val updated = base.copy(isAm = true)
                                onValueChange(updated.format())
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "AM",
                            color = if (isAmActive) DarkBackground else TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    val isPmActive = isSet && !parsedTime.isAm
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isPmActive) ElectricTeal else Color.Transparent)
                            .clickable {
                                val base = if (isSet) parsedTime else defaultTime
                                val updated = base.copy(isAm = false)
                                onValueChange(updated.format())
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "PM",
                            color = if (isPmActive) DarkBackground else TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    if (showDialog) {
        ClockTimePickerDialog(
            initialTime = if (isSet) parsedTime else defaultTime,
            onDismiss = { showDialog = false },
            onConfirm = { newTime ->
                onValueChange(newTime.format())
                showDialog = false
            }
        )
    }
}

/**
 * ClockTimePickerDialog: A consumer-grade clock-style dialog featuring
 * a circular analog clock dial, digital time switcher, and AM/PM selector.
 */
@Composable
fun ClockTimePickerDialog(
    initialTime: Time12,
    onDismiss: () -> Unit,
    onConfirm: (Time12) -> Unit
) {
    var selectedHour by remember { mutableIntStateOf(initialTime.hour) }
    var selectedMinute by remember { mutableIntStateOf(initialTime.minute) }
    var isAm by remember { mutableStateOf(initialTime.isAm) }
    var currentMode by remember { mutableStateOf(ClockPickerMode.HOUR) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = Color(0xFF131722),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color(0xFF222B3D)),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Dialog Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Set Time",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = if (currentMode == ClockPickerMode.HOUR) "Select Hour" else "Select Minute",
                        color = ElectricTeal,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Digital Header with Mode Switcher & AM/PM Pills
                Surface(
                    color = Color(0xFF0C0F17),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFF1C2433)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Digital Time readout with clickable Hour and Minute
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Hour
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (currentMode == ClockPickerMode.HOUR) Color(0xFF1A2A38)
                                        else Color.Transparent
                                    )
                                    .clickable { currentMode = ClockPickerMode.HOUR }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = String.format(Locale.US, "%d", selectedHour),
                                    color = if (currentMode == ClockPickerMode.HOUR) ElectricTeal else TextSecondary,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Text(
                                text = ":",
                                color = TextSecondary,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 2.dp)
                            )

                            // Minute
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (currentMode == ClockPickerMode.MINUTE) Color(0xFF1A2A38)
                                        else Color.Transparent
                                    )
                                    .clickable { currentMode = ClockPickerMode.MINUTE }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = String.format(Locale.US, "%02d", selectedMinute),
                                    color = if (currentMode == ClockPickerMode.MINUTE) ElectricTeal else TextSecondary,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // AM / PM Switcher Pill
                        Surface(
                            color = Color(0xFF151B26),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF243044))
                        ) {
                            Row(modifier = Modifier.padding(3.dp)) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isAm) ElectricTeal else Color.Transparent)
                                        .clickable { isAm = true }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "AM",
                                        color = if (isAm) DarkBackground else TextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (!isAm) ElectricTeal else Color.Transparent)
                                        .clickable { isAm = false }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "PM",
                                        color = if (!isAm) DarkBackground else TextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Circular Analog Clock Face
                ClockDial(
                    mode = currentMode,
                    selectedHour = selectedHour,
                    selectedMinute = selectedMinute,
                    onSelectHour = { hour ->
                        selectedHour = hour
                        // Smoothly transition to minute mode after selecting hour
                        currentMode = ClockPickerMode.MINUTE
                    },
                    onSelectMinute = { minute ->
                        selectedMinute = minute
                    },
                    modifier = Modifier.size(230.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Quick Minute Selection Chips (00, 15, 30, 45)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(0, 15, 30, 45).forEach { min ->
                        val isSelected = selectedMinute == min
                        Surface(
                            color = if (isSelected) ElectricTeal.copy(alpha = 0.2f) else Color(0xFF161B26),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) ElectricTeal else Color(0xFF222B3C)
                            ),
                            modifier = Modifier
                                .clickable {
                                    selectedMinute = min
                                    currentMode = ClockPickerMode.MINUTE
                                }
                        ) {
                            Text(
                                text = String.format(Locale.US, ":%02d", min),
                                color = if (isSelected) ElectricTeal else TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons: Cancel and Set
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TextSecondary, fontSize = 14.sp)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            onConfirm(Time12(selectedHour, selectedMinute, isAm))
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricTeal,
                            contentColor = DarkBackground
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Set Time",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * ClockDial: Analog circular clock face with hour/minute numbers,
 * glowing selector hand, and touch gesture support.
 */
@Composable
fun ClockDial(
    mode: ClockPickerMode,
    selectedHour: Int,
    selectedMinute: Int,
    onSelectHour: (Int) -> Unit,
    onSelectMinute: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color(0xFF0D111A))
            .border(1.dp, Color(0xFF1E2838), CircleShape)
            .pointerInput(mode) {
                detectTapGestures { offset ->
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val dx = offset.x - center.x
                    val dy = offset.y - center.y
                    var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
                    if (angle < 0f) angle += 360f

                    if (mode == ClockPickerMode.HOUR) {
                        var hour = ((angle + 15f) / 30f).toInt()
                        if (hour == 0) hour = 12
                        if (hour > 12) hour -= 12
                        onSelectHour(hour)
                    } else {
                        var minute = ((angle + 3f) / 6f).toInt()
                        if (minute >= 60) minute = 0
                        onSelectMinute(minute)
                    }
                }
            }
            .pointerInput(mode) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val dx = change.position.x - center.x
                    val dy = change.position.y - center.y
                    var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
                    if (angle < 0f) angle += 360f

                    if (mode == ClockPickerMode.HOUR) {
                        var hour = ((angle + 15f) / 30f).toInt()
                        if (hour == 0) hour = 12
                        if (hour > 12) hour -= 12
                        onSelectHour(hour)
                    } else {
                        var minute = ((angle + 3f) / 6f).toInt()
                        if (minute >= 60) minute = 0
                        onSelectMinute(minute)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Draw clock hands and center pin
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f - 30.dp.toPx()

            val angle = if (mode == ClockPickerMode.HOUR) {
                val h = selectedHour % 12
                (h * 30f) - 90f
            } else {
                (selectedMinute * 6f) - 90f
            }

            val rad = Math.toRadians(angle.toDouble())
            val targetX = center.x + radius * cos(rad).toFloat()
            val targetY = center.y + radius * sin(rad).toFloat()

            // Hand line
            drawLine(
                color = ElectricTeal.copy(alpha = 0.8f),
                start = center,
                end = Offset(targetX, targetY),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Selector circle around selected number
            drawCircle(
                color = ElectricTeal,
                radius = 18.dp.toPx(),
                center = Offset(targetX, targetY)
            )

            // Inner center pin
            drawCircle(
                color = Color(0xFF0D111A),
                radius = 4.dp.toPx(),
                center = center
            )
            drawCircle(
                color = ElectricTeal,
                radius = 3.dp.toPx(),
                center = center
            )
        }

        // Draw Clock Numbers (1..12 for Hour, 00, 05, 10...55 for Minute)
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val centerPx = maxWidth / 2
            val dialRadius = centerPx - 30.dp

            if (mode == ClockPickerMode.HOUR) {
                (1..12).forEach { hour ->
                    val angleDeg = (hour * 30) - 90
                    val angleRad = Math.toRadians(angleDeg.toDouble())
                    val xOffset = dialRadius * cos(angleRad).toFloat()
                    val yOffset = dialRadius * sin(angleRad).toFloat()
                    val isSelected = hour == selectedHour

                    Box(
                        modifier = Modifier
                            .offset(x = centerPx + xOffset - 16.dp, y = centerPx + yOffset - 16.dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable { onSelectHour(hour) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = hour.toString(),
                            color = if (isSelected) DarkBackground else TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            } else {
                // Minute Mode: Show 5-minute increments
                (0..11).forEach { i ->
                    val min = i * 5
                    val angleDeg = (min * 6) - 90
                    val angleRad = Math.toRadians(angleDeg.toDouble())
                    val xOffset = dialRadius * cos(angleRad).toFloat()
                    val yOffset = dialRadius * sin(angleRad).toFloat()
                    val isSelected = selectedMinute == min

                    Box(
                        modifier = Modifier
                            .offset(x = centerPx + xOffset - 16.dp, y = centerPx + yOffset - 16.dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable { onSelectMinute(min) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = String.format(Locale.US, "%02d", min),
                            color = if (isSelected) DarkBackground else TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
