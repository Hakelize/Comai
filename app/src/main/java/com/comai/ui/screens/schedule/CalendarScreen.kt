package com.comai.ui.screens.schedule

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.comai.R
import com.comai.data.models.PersonalPlan
import com.comai.data.models.ReminderType
import com.comai.scheduling.AlarmPermissionUtils
import com.comai.scheduling.ScheduleRelativeTimeUtils
import com.comai.ui.components.ComaiBottomBar
import com.comai.ui.navigation.Routes
import com.comai.ui.theme.*
import com.comai.util.TimeUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Calendar Screen displays all personal schedules organized by date.
 * Reads directly from the single-source-of-truth PersonalPlanRepository.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: PersonalScheduleViewModel,
    onBack: () -> Unit,
    onNavigateToPersonalSchedule: () -> Unit = onBack,
    onNavigateToHome: () -> Unit = {},
    onNavigateToChat: () -> Unit = {},
    onNavigateToRoutine: () -> Unit = {},
    onNavigateToMemory: () -> Unit = {},
    onNavigateToProfile: () -> Unit = onBack
) {
    val context = LocalContext.current
    val plans by viewModel.plans.collectAsState()

    var displayedMonth by remember {
        mutableStateOf(Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        })
    }

    var selectedDate by remember {
        mutableStateOf(Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        })
    }

    var planToEdit by remember { mutableStateOf<PersonalPlan?>(null) }
    var planForDetails by remember { mutableStateOf<PersonalPlan?>(null) }
    var isAddingNewPlan by remember { mutableStateOf(false) }

    val hasNotifPermission = remember { AlarmPermissionUtils.hasNotificationPermission(context) }
    val canExactAlarm = remember { AlarmPermissionUtils.canScheduleExactAlarms(context) }

    // Selected ISO date: "yyyy-MM-dd"
    val selectedIsoDate = remember(selectedDate) {
        String.format(
            Locale.US,
            "%04d-%02d-%02d",
            selectedDate.get(Calendar.YEAR),
            selectedDate.get(Calendar.MONTH) + 1,
            selectedDate.get(Calendar.DAY_OF_MONTH)
        )
    }

    // Filter schedules that apply to selected date
    val schedulesForSelectedDate = remember(plans, selectedDate, selectedIsoDate) {
        plans.filter { planMatchesDate(it, selectedDate, selectedIsoDate) }
            .sortedBy { TimeUtils.parseTime(it.time).toMinutesOfDay() }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.calendar_title),
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.calendar_subtitle),
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    // Button to switch to List View
                    TextButton(onClick = onNavigateToPersonalSchedule) {
                        Text(
                            text = stringResource(R.string.calendar_view_schedule),
                            color = ElectricTeal,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    // Add schedule button
                    IconButton(onClick = { isAddingNewPlan = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.calendar_add_schedule),
                            tint = ElectricTeal
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        bottomBar = {
            Column(modifier = Modifier.navigationBarsPadding()) {
                ComaiBottomBar(
                    currentRoute = Routes.PROFILE,
                    onNavigate = { targetRoute ->
                        when (targetRoute) {
                            Routes.HOME -> onNavigateToHome()
                            Routes.CHAT -> onNavigateToChat()
                            Routes.ROUTINE -> onNavigateToRoutine()
                            Routes.MEMORY -> onNavigateToMemory()
                            Routes.PROFILE -> onNavigateToProfile()
                        }
                    }
                )
            }
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            // Permission Banner if Exact Alarm or Notification missing
            if (!canExactAlarm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Surface(
                    color = Color(0xFF261918),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF5C2B22)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.schedule_exact_alarm_required),
                                color = Color(0xFFFF8A80),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.schedule_exact_alarm_desc),
                                color = TextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { AlarmPermissionUtils.openExactAlarmSetting(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                stringResource(R.string.schedule_exact_alarm_btn),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Month Navigation Bar
            MonthHeaderBar(
                displayedMonth = displayedMonth,
                onPreviousMonth = {
                    val prev = (displayedMonth.clone() as Calendar).apply {
                        add(Calendar.MONTH, -1)
                    }
                    displayedMonth = prev
                },
                onNextMonth = {
                    val next = (displayedMonth.clone() as Calendar).apply {
                        add(Calendar.MONTH, 1)
                    }
                    displayedMonth = next
                },
                onTodayClick = {
                    val today = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    selectedDate = today
                    displayedMonth = (today.clone() as Calendar).apply {
                        set(Calendar.DAY_OF_MONTH, 1)
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Calendar Month Grid View
            CalendarMonthGrid(
                displayedMonth = displayedMonth,
                selectedDate = selectedDate,
                plans = plans,
                onDateSelected = { clickedDate ->
                    selectedDate = clickedDate
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Selected Date Summary Row
            SelectedDateHeader(
                selectedDate = selectedDate,
                scheduleCount = schedulesForSelectedDate.size,
                onAddSchedule = { isAddingNewPlan = true }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // List of schedules for the selected date
            if (schedulesForSelectedDate.isEmpty()) {
                Surface(
                    color = Color(0xFF131722),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFF222B3D)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.EventAvailable,
                            contentDescription = null,
                            tint = TextSecondary.copy(alpha = 0.6f),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.calendar_no_schedules),
                            color = TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { isAddingNewPlan = true },
                            border = BorderStroke(1.dp, ElectricTeal),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = ElectricTeal,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.calendar_add_schedule),
                                color = ElectricTeal,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(schedulesForSelectedDate, key = { it.id }) { plan ->
                        CalendarScheduleCard(
                            plan = plan,
                            selectedDate = selectedDate,
                            canExactAlarm = canExactAlarm,
                            onToggle = { viewModel.togglePlan(plan.id) },
                            onClick = { planForDetails = plan }
                        )
                    }
                }
            }
        }
    }

    // Schedule Details Dialog (allows view details, Edit, Delete, Toggle)
    planForDetails?.let { plan ->
        ScheduleDetailDialog(
            plan = plan,
            selectedDate = selectedDate,
            canExactAlarm = canExactAlarm,
            onDismiss = { planForDetails = null },
            onEdit = {
                planToEdit = plan
                planForDetails = null
            },
            onDelete = {
                viewModel.deletePlan(plan.id)
                planForDetails = null
            },
            onToggle = {
                viewModel.togglePlan(plan.id)
                planForDetails = plan.copy(isEnabled = !plan.isEnabled)
            }
        )
    }

    // Add Plan Dialog (Prefilled with selected date)
    if (isAddingNewPlan) {
        PlanDialog(
            initialPlan = null,
            initialDate = selectedIsoDate,
            onDismiss = { isAddingNewPlan = false },
            onSave = { title, time, repeatFrequency, reminderType, date ->
                viewModel.addPlan(title, time, repeatFrequency, reminderType, date)
                isAddingNewPlan = false
            }
        )
    }

    // Edit Plan Dialog
    planToEdit?.let { plan ->
        PlanDialog(
            initialPlan = plan,
            initialDate = plan.date ?: selectedIsoDate,
            onDismiss = { planToEdit = null },
            onSave = { title, time, repeatFrequency, reminderType, date ->
                viewModel.updatePlan(plan.id, title, time, repeatFrequency, reminderType, plan.isEnabled, date)
                planToEdit = null
            }
        )
    }
}

/**
 * Month navigation header with arrows and Today button.
 */
@Composable
internal fun MonthHeaderBar(
    displayedMonth: Calendar,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onTodayClick: () -> Unit
) {
    val monthTitle = remember(displayedMonth) {
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(displayedMonth.time)
    }

    Surface(
        color = Color(0xFF131722),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF222B3D)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPreviousMonth, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Previous Month",
                        tint = ElectricTeal,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Text(
                    text = monthTitle,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                IconButton(onClick = onNextMonth, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Next Month",
                        tint = ElectricTeal,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Surface(
                color = Color(0xFF182230),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFF222E42)),
                modifier = Modifier.clickable { onTodayClick() }
            ) {
                Text(
                    text = stringResource(R.string.calendar_today),
                    color = ElectricTeal,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/**
 * Monthly calendar grid with day indicators for Notification and Alarm events.
 */
@Composable
internal fun CalendarMonthGrid(
    displayedMonth: Calendar,
    selectedDate: Calendar,
    plans: List<PersonalPlan>,
    onDateSelected: (Calendar) -> Unit
) {
    val daysOfWeek = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    val todayCal = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    val year = displayedMonth.get(Calendar.YEAR)
    val month = displayedMonth.get(Calendar.MONTH)

    val maxDaysInMonth = remember(year, month) {
        val c = (displayedMonth.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
        }
        c.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    // 0 = Monday, 6 = Sunday
    val firstDayOffset = remember(year, month) {
        val c = (displayedMonth.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val dow = c.get(Calendar.DAY_OF_WEEK)
        // Calendar.SUNDAY is 1, MONDAY is 2
        (dow + 5) % 7
    }

    Surface(
        color = Color(0xFF131722),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF222B3D)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Days of Week Header
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                daysOfWeek.forEach { dayName ->
                    Text(
                        text = dayName,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            val totalCells = firstDayOffset + maxDaysInMonth
            val rows = (totalCells + 6) / 7

            for (rowIndex in 0 until rows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    for (colIndex in 0 until 7) {
                        val cellIndex = rowIndex * 7 + colIndex
                        val dayNumber = cellIndex - firstDayOffset + 1

                        if (dayNumber in 1..maxDaysInMonth) {
                            val cellCal = (displayedMonth.clone() as Calendar).apply {
                                set(Calendar.DAY_OF_MONTH, dayNumber)
                                set(Calendar.HOUR_OF_DAY, 0)
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }

                            val isSelected = isSameDay(cellCal, selectedDate)
                            val isToday = isSameDay(cellCal, todayCal)

                            val cellIsoDate = String.format(
                                Locale.US,
                                "%04d-%02d-%02d",
                                cellCal.get(Calendar.YEAR),
                                cellCal.get(Calendar.MONTH) + 1,
                                dayNumber
                            )

                            // Check plans for dots
                            val dayPlans = plans.filter { planMatchesDate(it, cellCal, cellIsoDate) }
                            val hasAlarm = dayPlans.any { it.isEnabled && it.reminderType == ReminderType.ALARM }
                            val hasNotif = dayPlans.any { it.isEnabled && it.reminderType == ReminderType.NOTIFICATION }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .padding(2.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isSelected -> ElectricTeal
                                            else -> Color.Transparent
                                        }
                                    )
                                    .then(
                                        if (isToday && !isSelected) {
                                            Modifier.border(1.dp, ElectricTeal, CircleShape)
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clickable { onDateSelected(cellCal) },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "$dayNumber",
                                        color = when {
                                            isSelected -> DarkBackground
                                            isToday -> ElectricTeal
                                            else -> TextPrimary
                                        },
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium
                                    )

                                    // Indicator dots under day number
                                    if (dayPlans.isNotEmpty()) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(top = 1.dp)
                                        ) {
                                            if (hasNotif) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(4.dp)
                                                        .clip(CircleShape)
                                                        .background(if (isSelected) DarkBackground else ElectricTeal)
                                                )
                                            }
                                            if (hasAlarm) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(4.dp)
                                                        .clip(CircleShape)
                                                        .background(if (isSelected) DarkBackground else Color(0xFFFF5252))
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // Empty placeholder cell
                            Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Header showing selected day name and quick add button.
 */
@Composable
internal fun SelectedDateHeader(
    selectedDate: Calendar,
    scheduleCount: Int,
    onAddSchedule: () -> Unit
) {
    val fullDateString = remember(selectedDate) {
        SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(selectedDate.time)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = fullDateString,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.calendar_schedules_count, scheduleCount),
                color = ElectricTeal,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }

        IconButton(onClick = onAddSchedule, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(R.string.calendar_add_schedule),
                tint = ElectricTeal,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Visual card representing a scheduled item on the selected calendar date.
 */
@Composable
internal fun CalendarScheduleCard(
    plan: PersonalPlan,
    selectedDate: Calendar,
    canExactAlarm: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit
) {
    val isAlarm = plan.reminderType == ReminderType.ALARM
    val status = computeScheduleStatus(plan, selectedDate)

    Surface(
        color = Color(0xFF131722),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.dp,
            if (isAlarm) Color(0xFF381F26) else Color(0xFF1E283A)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon Pill
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isAlarm) Color(0xFF2E171C) else Color(0xFF0F242C)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isAlarm) Icons.Outlined.Alarm else Icons.Outlined.Notifications,
                    contentDescription = null,
                    tint = if (isAlarm) Color(0xFFFF8A80) else ElectricTeal,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = plan.title,
                    color = if (plan.isEnabled) TextPrimary else TextSecondary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Time Pill
                    Surface(
                        color = if (plan.isEnabled) Color(0xFF0F242C) else DarkSurfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = plan.time,
                            color = if (plan.isEnabled) ElectricTeal else TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Reminder Type Pill
                    Surface(
                        color = if (isAlarm) Color(0xFF2B161B) else Color(0xFF122329),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (isAlarm) stringResource(R.string.schedule_alarm) else stringResource(R.string.schedule_notification),
                            color = if (isAlarm) Color(0xFFFF8A80) else ElectricTeal,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Status Pill (Upcoming / Completed / Disabled)
                    Surface(
                        color = when (status) {
                            ScheduleStatus.COMPLETED -> Color(0xFF1F2826)
                            ScheduleStatus.UPCOMING -> Color(0xFF132832)
                            ScheduleStatus.DISABLED -> DarkSurfaceVariant
                        },
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = when (status) {
                                ScheduleStatus.COMPLETED -> stringResource(R.string.calendar_completed)
                                ScheduleStatus.UPCOMING -> stringResource(R.string.calendar_upcoming)
                                ScheduleStatus.DISABLED -> "Off"
                            },
                            color = when (status) {
                                ScheduleStatus.COMPLETED -> Color(0xFF81C784)
                                ScheduleStatus.UPCOMING -> ElectricTeal
                                ScheduleStatus.DISABLED -> TextSecondary
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Relative time for upcoming occurrences
                if (plan.isEnabled && status == ScheduleStatus.UPCOMING) {
                    val relativeTime = remember(plan.time, plan.repeatFrequency) {
                        ScheduleRelativeTimeUtils.computeRelativeTime(plan.time, plan.repeatFrequency, plan.isEnabled)
                    }
                    if (relativeTime.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = relativeTime,
                            color = ElectricTeal.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Enable / Disable switch
            Switch(
                checked = plan.isEnabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = ElectricTeal,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = DarkSurface
                ),
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}

/**
 * Detailed bottom modal/dialog showing all attributes with Edit and Delete options.
 */
@Composable
internal fun ScheduleDetailDialog(
    plan: PersonalPlan,
    selectedDate: Calendar,
    canExactAlarm: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggle: () -> Unit
) {
    val isAlarm = plan.reminderType == ReminderType.ALARM
    val status = computeScheduleStatus(plan, selectedDate)

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
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.calendar_schedule_details),
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Visual icon
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (isAlarm) Color(0xFF2E171C) else Color(0xFF0F242C)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isAlarm) Icons.Outlined.Alarm else Icons.Outlined.Notifications,
                            contentDescription = null,
                            tint = if (isAlarm) Color(0xFFFF8A80) else ElectricTeal,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title
                Text(
                    text = plan.title,
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Attribute Rows
                DetailItemRow(
                    icon = Icons.Outlined.AccessTime,
                    label = stringResource(R.string.schedule_time),
                    value = plan.time
                )

                DetailItemRow(
                    icon = Icons.Outlined.Repeat,
                    label = stringResource(R.string.schedule_repeat),
                    value = plan.repeatFrequency
                )

                DetailItemRow(
                    icon = if (isAlarm) Icons.Outlined.Alarm else Icons.Outlined.Notifications,
                    label = stringResource(R.string.schedule_reminder_type),
                    value = if (isAlarm) {
                        if (canExactAlarm) stringResource(R.string.schedule_alarm_enabled)
                        else "${stringResource(R.string.schedule_alarm)} (${stringResource(R.string.schedule_permission_required)})"
                    } else {
                        stringResource(R.string.schedule_notification_enabled)
                    }
                )

                DetailItemRow(
                    icon = Icons.Outlined.Info,
                    label = stringResource(R.string.calendar_status),
                    value = when (status) {
                        ScheduleStatus.COMPLETED -> stringResource(R.string.calendar_completed)
                        ScheduleStatus.UPCOMING -> stringResource(R.string.calendar_upcoming)
                        ScheduleStatus.DISABLED -> "Disabled"
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Actions: Edit, Delete, Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDelete,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF5C2B22)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF8A80)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.delete), fontSize = 13.sp)
                    }

                    Button(
                        onClick = onEdit,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricTeal),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = DarkBackground,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.edit), color = DarkBackground, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailItemRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$label:",
            color = TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.width(110.dp)
        )
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

enum class ScheduleStatus {
    UPCOMING,
    COMPLETED,
    DISABLED
}

/**
 * Calculates whether a scheduled item on selectedDate is Upcoming, Completed, or Disabled.
 */
internal fun computeScheduleStatus(plan: PersonalPlan, selectedDate: Calendar): ScheduleStatus {
    if (!plan.isEnabled) return ScheduleStatus.DISABLED

    val now = Calendar.getInstance()

    // Compare date parts
    val selectedY = selectedDate.get(Calendar.YEAR)
    val selectedDoy = selectedDate.get(Calendar.DAY_OF_YEAR)
    val nowY = now.get(Calendar.YEAR)
    val nowDoy = now.get(Calendar.DAY_OF_YEAR)

    if (selectedY < nowY || (selectedY == nowY && selectedDoy < nowDoy)) {
        return ScheduleStatus.COMPLETED
    }

    if (selectedY > nowY || (selectedY == nowY && selectedDoy > nowDoy)) {
        return ScheduleStatus.UPCOMING
    }

    // Today: check hour and minute
    val parsed = TimeUtils.parseTime(plan.time)
    val hourOfDay = when {
        parsed.isAm && parsed.hour == 12 -> 0
        parsed.isAm -> parsed.hour
        !parsed.isAm && parsed.hour == 12 -> 12
        else -> parsed.hour + 12
    }

    val planCal = (now.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, hourOfDay)
        set(Calendar.MINUTE, parsed.minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    return if (planCal.timeInMillis <= now.timeInMillis) {
        ScheduleStatus.COMPLETED
    } else {
        ScheduleStatus.UPCOMING
    }
}

/**
 * Determines whether a personal plan matches a given calendar date.
 */
internal fun planMatchesDate(
    plan: PersonalPlan,
    targetCal: Calendar,
    targetIsoDate: String
): Boolean {
    // If plan has an explicit date, it strictly matches that date
    if (!plan.date.isNullOrBlank()) {
        return plan.date == targetIsoDate
    }

    val dow = targetCal.get(Calendar.DAY_OF_WEEK)
    return when {
        plan.repeatFrequency.equals("Daily", ignoreCase = true) -> true
        plan.repeatFrequency.equals("Weekdays", ignoreCase = true) -> {
            dow in listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY)
        }
        plan.repeatFrequency.equals("Weekends", ignoreCase = true) -> {
            dow == Calendar.SATURDAY || dow == Calendar.SUNDAY
        }
        plan.repeatFrequency.contains("Mon", ignoreCase = true) &&
        plan.repeatFrequency.contains("Wed", ignoreCase = true) -> {
            dow == Calendar.MONDAY || dow == Calendar.WEDNESDAY || dow == Calendar.FRIDAY
        }
        plan.repeatFrequency.equals("Once", ignoreCase = true) -> {
            // If date is null for Once, check creation date
            val createdCal = Calendar.getInstance().apply { timeInMillis = plan.createdAtMs }
            isSameDay(createdCal, targetCal)
        }
        else -> true
    }
}

internal fun isSameDay(c1: Calendar, c2: Calendar): Boolean {
    return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
            c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
}
