package com.comai.ui.screens.schedule

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.comai.R
import com.comai.data.menstrualcycle.MenstrualCycleCalculator
import com.comai.data.menstrualcycle.MenstrualCycleData
import com.comai.data.menstrualcycle.MenstrualCycleEstimate
import com.comai.data.menstrualcycle.MenstrualCyclePreferences
import com.comai.data.models.PersonalPlan
import com.comai.data.models.ReminderType
import com.comai.ui.components.ComaiBottomBar
import com.comai.ui.navigation.Routes
import com.comai.ui.screens.onboarding.OnboardingPreferences
import com.comai.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

import androidx.compose.material.icons.outlined.CalendarMonth
import com.comai.scheduling.AlarmPermissionUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalScheduleScreen(
    viewModel: PersonalScheduleViewModel,
    onBack: () -> Unit,
    onNavigateToCalendar: () -> Unit = {},
    onNavigateToHome: () -> Unit = {},
    onNavigateToChat: () -> Unit = {},
    onNavigateToRoutine: () -> Unit = {},
    onNavigateToMemory: () -> Unit = {},
    onNavigateToProfile: () -> Unit = onBack
) {
    val context = LocalContext.current
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
    }

    // Dynamic Gender & Menstrual Cycle Setup (Female only)
    val onboardingPrefs = remember { OnboardingPreferences(context) }
    val userProfile = remember { onboardingPrefs.getProfile() }
    val isFemale = userProfile.gender.equals("Female", ignoreCase = true)

    val menstrualCyclePrefs = remember { MenstrualCyclePreferences(context) }
    var cycleData by remember { mutableStateOf(menstrualCyclePrefs.getCycleData()) }
    val cycleEstimate = remember(cycleData) { MenstrualCycleCalculator.calculateEstimate(cycleData) }
    var showCycleDialog by remember { mutableStateOf(false) }

    val plans by viewModel.plans.collectAsState()

    var planToEdit by remember { mutableStateOf<PersonalPlan?>(null) }
    var isAddingNewPlan by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.schedule_title), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
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
                    IconButton(onClick = onNavigateToCalendar) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarMonth,
                            contentDescription = stringResource(R.string.calendar_title),
                            tint = ElectricTeal
                        )
                    }
                    IconButton(onClick = { isAddingNewPlan = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.schedule_add_plan),
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
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.schedule_time_based_routines),
                color = ElectricTeal,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.schedule_title),
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.schedule_subtitle),
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Calendar shortcut banner
            Surface(
                color = Color(0xFF131B26),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFF1F2C3F)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToCalendar() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0F242C)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CalendarMonth,
                                contentDescription = null,
                                tint = ElectricTeal,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.calendar_view_calendar),
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(R.string.calendar_subtitle),
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = ElectricTeal,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val canExactAlarm = remember { AlarmPermissionUtils.canScheduleExactAlarms(context) }
            val hasAlarmPlan = plans.any { it.isEnabled && it.reminderType == ReminderType.ALARM }

            // Exact Alarm Permission Banner if needed
            if (hasAlarmPlan && !canExactAlarm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Surface(
                    color = Color(0xFF261918),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF5C2B22)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
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

            if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Surface(
                    color = Color(0xFF261D15),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF5C3C1A)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = WarmAmber,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.schedule_enable_notif_banner),
                                color = TextPrimary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = WarmAmber,
                                contentColor = DarkBackground
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(stringResource(R.string.enable), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ── Dynamic Menstrual Cycle Section (Female Only) ───────
            if (isFemale) {
                MenstrualCycleCard(
                    cycleData = cycleData,
                    estimate = cycleEstimate,
                    onConfigureClick = { showCycleDialog = true }
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            if (plans.isEmpty()) {
                EmptyPlanView(
                    onAddPlan = { isAddingNewPlan = true },
                    onQuickAdd = { title, time ->
                        viewModel.addPlan(title, time, "Daily", ReminderType.NOTIFICATION)
                    }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(plans, key = { it.id }) { plan ->
                        PlanCard(
                            plan = plan,
                            onToggle = { viewModel.togglePlan(plan.id) },
                            onEdit = { planToEdit = plan },
                            onDelete = { viewModel.deletePlan(plan.id) }
                        )
                    }
                }
            }
        }
    }

    // Menstrual Cycle Setup / Edit Dialog
    if (showCycleDialog) {
        MenstrualCycleDialog(
            initialData = cycleData,
            onDismiss = { showCycleDialog = false },
            onSave = { updated ->
                menstrualCyclePrefs.saveCycleData(updated)
                cycleData = updated
                showCycleDialog = false
            }
        )
    }

    // Add Plan Dialog
    if (isAddingNewPlan) {
        PlanDialog(
            initialPlan = null,
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
            onDismiss = { planToEdit = null },
            onSave = { title, time, repeatFrequency, reminderType, date ->
                viewModel.updatePlan(plan.id, title, time, repeatFrequency, reminderType, plan.isEnabled, date)
                planToEdit = null
            }
        )
    }
}

@Composable
fun MenstrualCycleCard(
    cycleData: MenstrualCycleData,
    estimate: MenstrualCycleEstimate?,
    onConfigureClick: () -> Unit
) {
    Surface(
        color = Color(0xFF141926),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color(0xFF273148)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onConfigureClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2E1928)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.WaterDrop,
                            contentDescription = null,
                            tint = Color(0xFFFF80AB),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.schedule_menstrual_cycle),
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = Color(0xFF1E2235),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Lock,
                                        contentDescription = null,
                                        tint = Color(0xFFB0BEC5),
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = stringResource(R.string.schedule_on_device),
                                        color = Color(0xFFB0BEC5),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                        Text(
                            text = stringResource(R.string.schedule_cycle_disclaimer),
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }

                IconButton(onClick = onConfigureClick, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.schedule_cycle_configure),
                        tint = TextSecondary,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (cycleData.isConfigured && estimate != null) {
                Surface(
                    color = Color(0xFF1B1828),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF382346)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.schedule_next_period),
                                color = Color(0xFFFF80AB),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = estimate.formattedEstimatedDate,
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Surface(
                            color = Color(0xFF351C33),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (estimate.daysRemaining == 0) "Today" else "In ${estimate.daysRemaining} days",
                                color = Color(0xFFFF4081),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = Color(0xFF181F2C),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "${cycleData.typicalCycleLengthDays} day cycle",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    Surface(
                        color = Color(0xFF181F2C),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "${cycleData.periodDurationDays} day duration",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    Surface(
                        color = if (cycleData.isRemindersEnabled) Color(0xFF14242A) else Color(0xFF181F2C),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (cycleData.isRemindersEnabled) "Reminders On" else "Reminders Off",
                            color = if (cycleData.isRemindersEnabled) ElectricTeal else TextSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.schedule_cycle_setup_desc),
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onConfigureClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF4081),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(stringResource(R.string.schedule_cycle_setup_btn), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanCard(
    plan: PersonalPlan,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = Color(0xFF11151F),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF1E2638)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Clock/Icon indicator
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF161E2E)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.AccessTime,
                    contentDescription = null,
                    tint = if (plan.isEnabled) ElectricTeal else TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Title & Badges
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Time pill
                    Surface(
                        color = if (plan.isEnabled) Color(0xFF0F242C) else DarkSurfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = plan.time,
                            color = if (plan.isEnabled) ElectricTeal else TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }

                    // Repeat frequency
                    Surface(
                        color = Color(0xFF181F2C),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = plan.repeatFrequency,
                            color = TextSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Reminder Type Pill
                    val isAlarm = plan.reminderType == ReminderType.ALARM
                    Surface(
                        color = if (isAlarm) Color(0xFF2B161B) else Color(0xFF122329),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = if (isAlarm) Icons.Outlined.Alarm else Icons.Outlined.Notifications,
                                contentDescription = null,
                                tint = if (isAlarm) Color(0xFFFF8A80) else ElectricTeal,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (isAlarm) stringResource(R.string.schedule_alarm) else stringResource(R.string.schedule_notification),
                                color = if (isAlarm) Color(0xFFFF8A80) else ElectricTeal,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Dynamic relative time remaining for upcoming occurrence
                val relativeTime = remember(plan.time, plan.repeatFrequency, plan.isEnabled) {
                    com.comai.scheduling.ScheduleRelativeTimeUtils.computeRelativeTime(
                        timeString = plan.time,
                        repeatFrequency = plan.repeatFrequency,
                        isEnabled = plan.isEnabled
                    )
                }

                if (relativeTime.isNotBlank()) {
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = relativeTime,
                        color = if (plan.isEnabled) ElectricTeal.copy(alpha = 0.9f) else TextSecondary.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Toggle Switch
            Switch(
                checked = plan.isEnabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = ElectricTeal,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = DarkSurface
                ),
                modifier = Modifier.padding(end = 2.dp)
            )

            // Edit & Delete actions
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = stringResource(R.string.edit),
                    tint = TextSecondary,
                    modifier = Modifier.size(17.dp)
                )
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = ErrorRed.copy(alpha = 0.8f),
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

@Composable
fun MenstrualCycleDialog(
    initialData: MenstrualCycleData,
    onDismiss: () -> Unit,
    onSave: (MenstrualCycleData) -> Unit
) {
    val context = LocalContext.current
    var lastPeriodStartDateMs by remember {
        mutableStateOf(if (initialData.lastPeriodStartDateMs > 0) initialData.lastPeriodStartDateMs else System.currentTimeMillis())
    }
    var cycleLengthDays by remember { mutableStateOf(initialData.typicalCycleLengthDays) }
    var periodDurationDays by remember { mutableStateOf(initialData.periodDurationDays) }
    var isRemindersEnabled by remember { mutableStateOf(initialData.isRemindersEnabled) }

    val formattedSelectedDate = remember(lastPeriodStartDateMs) {
        SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(lastPeriodStartDateMs))
    }

    val cal = Calendar.getInstance().apply { timeInMillis = lastPeriodStartDateMs }
    val datePickerDialog = remember {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val chosen = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                lastPeriodStartDateMs = chosen.timeInMillis
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

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
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.Start
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2E1928)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.WaterDrop,
                            contentDescription = null,
                            tint = Color(0xFFFF80AB),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Menstrual Cycle Setup",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Last period start date
                Text(
                    text = "Last Period Start Date *",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = Color(0xFF181F2C),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF222E42)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { datePickerDialog.show() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formattedSelectedDate,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            imageVector = Icons.Outlined.EventNote,
                            contentDescription = "Pick Date",
                            tint = ElectricTeal,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Typical cycle length
                Text(
                    text = "Typical Cycle Length: $cycleLengthDays days",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { if (cycleLengthDays > 21) cycleLengthDays-- },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("-1 day", fontSize = 12.sp)
                    }

                    Text(
                        text = "$cycleLengthDays days (21-45)",
                        color = ElectricTeal,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    OutlinedButton(
                        onClick = { if (cycleLengthDays < 45) cycleLengthDays++ },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("+1 day", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Period duration
                Text(
                    text = "Period Duration: $periodDurationDays days",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { if (periodDurationDays > 2) periodDurationDays-- },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("-1 day", fontSize = 12.sp)
                    }

                    Text(
                        text = "$periodDurationDays days (2-10)",
                        color = SoftPurple,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    OutlinedButton(
                        onClick = { if (periodDurationDays < 10) periodDurationDays++ },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("+1 day", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Reminders Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Cycle Reminders",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Local reminder before estimated date",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = isRemindersEnabled,
                        onCheckedChange = { isRemindersEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = ElectricTeal,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = DarkSurface
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Non-medical disclaimer
                Surface(
                    color = Color(0xFF101720),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E2A3A)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = ElectricTeal,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Estimated based on your saved cycle information. Not intended for medical prediction, diagnosis, or contraception.",
                            color = Color(0xFF90A4AE),
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
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
                        Text("Cancel", fontSize = 14.sp)
                    }

                    Button(
                        onClick = {
                            onSave(
                                MenstrualCycleData(
                                    lastPeriodStartDateMs = lastPeriodStartDateMs,
                                    typicalCycleLengthDays = cycleLengthDays,
                                    periodDurationDays = periodDurationDays,
                                    isRemindersEnabled = isRemindersEnabled,
                                    isConfigured = true
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4081)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        Text("Save Details", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyPlanView(
    onAddPlan: () -> Unit,
    onQuickAdd: (title: String, time: String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color(0xFF161E2E)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.EventNote,
                contentDescription = null,
                tint = SoftPurple,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = stringResource(R.string.schedule_empty_title),
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = stringResource(R.string.schedule_empty_sub),
            color = TextSecondary,
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(18.dp))

        Button(
            onClick = onAddPlan,
            colors = ButtonDefaults.buttonColors(containerColor = ElectricTeal),
            shape = RoundedCornerShape(20.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = DarkBackground,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.schedule_add_plan), color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = stringResource(R.string.schedule_quick_suggestions),
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(8.dp))

        val suggestions = listOf(
            "Drink water" to "10:00 AM",
            "Take a break" to "3:30 PM",
            "Call Mom" to "6:00 PM",
            "Go to gym" to "7:00 PM",
            "Study" to "8:00 PM"
        )

        suggestions.forEach { (title, time) ->
            Surface(
                color = Color(0xFF11151F),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF1E2638)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(time, color = ElectricTeal, fontSize = 12.sp)
                    }

                    TextButton(onClick = { onQuickAdd(title, time) }) {
                        Text("+ Add", color = ElectricTeal, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

