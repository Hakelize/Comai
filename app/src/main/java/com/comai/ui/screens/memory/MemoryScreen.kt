package com.comai.ui.screens.memory

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
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
import com.comai.R
import com.comai.contextengine.db.Memory
import com.comai.data.models.PersonalPlan
import com.comai.data.models.ReminderType
import com.comai.nearby.LocationState
import com.comai.nearby.NearbyContext
import com.comai.nearby.TrafficLevel
import com.comai.scheduling.AlarmPermissionUtils
import com.comai.ui.components.ComaiBottomBar
import com.comai.ui.components.OsmOfflineMapView
import com.comai.ui.navigation.Routes
import com.comai.ui.screens.schedule.*
import com.comai.ui.theme.*
import com.comai.util.TimeUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * MemoryScreen: Unified Memory Tab displaying:
 * 1. REAL CALENDAR (Month navigation, days grid S M T W T F S, event dots)
 * 2. SELECTED DATE & SCHEDULES (Single source of truth from Room database)
 * 3. LOCATION MAP ("● You" current location, coordinates, map controls, permission UI)
 * 4. NEARBY INFORMATION (Traffic, Local News, Events, offline resilience)
 * 5. PERSONAL MEMORIES (Stored personal knowledge and preferences)
 *
 * Vertically scrollable, responsive across all screen sizes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(
    viewModel: MemoryViewModel,
    onBack: () -> Unit,
    onNavigateToHome: () -> Unit = {},
    onNavigateToChat: () -> Unit = {},
    onNavigateToRoutine: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {}
) {
    val context = LocalContext.current

    // Observe Single Source of Truth for plans, memories, and nearby context
    val plans by viewModel.plans.collectAsState()
    val memories by viewModel.memories.collectAsState()
    val nearbyContext by viewModel.nearbyContext.collectAsState()
    val isRefreshingNearby by viewModel.isRefreshingNearby.collectAsState()

    // Calendar state
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

    // Location Permission launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshNearbyContext()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.nav_memory),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
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
                    IconButton(onClick = { isAddingNewPlan = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.calendar_add_schedule),
                            tint = ElectricTeal
                        )
                    }
                    IconButton(onClick = { viewModel.refreshNearbyContext() }) {
                        if (isRefreshingNearby) {
                            CircularProgressIndicator(
                                color = ElectricTeal,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.location_refresh),
                                tint = ElectricTeal
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        bottomBar = {
            Column(modifier = Modifier.navigationBarsPadding()) {
                ComaiBottomBar(
                    currentRoute = Routes.MEMORY,
                    onNavigate = { targetRoute ->
                        when (targetRoute) {
                            Routes.HOME -> onNavigateToHome()
                            Routes.CHAT -> onNavigateToChat()
                            Routes.ROUTINE -> onNavigateToRoutine()
                            Routes.PROFILE -> onNavigateToProfile()
                        }
                    }
                )
            }
        },
        containerColor = DarkBackground
    ) { padding ->
        // Single Vertically Scrollable Feed containing Calendar, Schedules, Location Map, and Nearby Info
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp)
        ) {
            // ==========================================
            // 1. CALENDAR SECTION
            // ==========================================
            item {
                Text(
                    text = stringResource(R.string.section_calendar),
                    color = ElectricTeal,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                // Month Navigation Header
                MemoryMonthHeaderBar(
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

                // Monthly Calendar Grid with S M T W T F S
                MemoryCalendarMonthGrid(
                    displayedMonth = displayedMonth,
                    selectedDate = selectedDate,
                    plans = plans,
                    onDateSelected = { clickedCal ->
                        selectedDate = clickedCal
                    }
                )
            }

            // ==========================================
            // 2. SELECTED DATE & SCHEDULES
            // ==========================================
            item {
                val fullDateFormatted = remember(selectedDate) {
                    SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(selectedDate.time)
                }

                Surface(
                    color = Color(0xFF131722),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFF222B3D)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "${stringResource(R.string.selected_date_label)}: $fullDateFormatted",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(R.string.todays_schedules),
                                    color = ElectricTeal,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            IconButton(
                                onClick = { isAddingNewPlan = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = stringResource(R.string.calendar_add_schedule),
                                    tint = ElectricTeal,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (schedulesForSelectedDate.isEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.EventBusy,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.calendar_no_schedules_day),
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                schedulesForSelectedDate.forEach { plan ->
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
            }

            // ==========================================
            // 3. LOCATION MAP SECTION
            // ==========================================
            item {
                Text(
                    text = stringResource(R.string.section_location),
                    color = ElectricTeal,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                MemoryLocationMapCard(
                    locationState = nearbyContext.location,
                    isRefreshing = isRefreshingNearby,
                    onRequestPermission = {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    },
                    onRefresh = { viewModel.refreshNearbyContext() },
                    onOpenSettings = {
                        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                        context.startActivity(intent)
                    },
                    onOpenMaps = { lat, lng ->
                        val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(Current+Location)")
                        val mapIntent = Intent(Intent.ACTION_VIEW, uri)
                        try {
                            context.startActivity(mapIntent)
                        } catch (_: Exception) {
                            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=$lat,$lng"))
                            context.startActivity(webIntent)
                        }
                    }
                )
            }

            // ==========================================
            // 4. NEARBY INFORMATION SECTION
            // ==========================================
            item {
                Text(
                    text = stringResource(R.string.section_nearby),
                    color = ElectricTeal,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                // Smart Schedule + Location Context Insight (if available)
                nearbyContext.contextualInsight?.let { insight ->
                    Surface(
                        color = Color(0xFF16232E),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ElectricTeal.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Lightbulb,
                                contentDescription = null,
                                tint = ElectricTeal,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = insight,
                                color = Color.White,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                if (nearbyContext.isOffline) {
                    // Offline Notice
                    Surface(
                        color = Color(0xFF1C1E26),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0xFF2A3042)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CloudOff,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.nearby_offline),
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            OutlinedButton(
                                onClick = { viewModel.refreshNearbyContext() },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF2E3A52)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Retry", color = ElectricTeal, fontSize = 11.sp)
                            }
                        }
                    }
                } else {
                    // Traffic Card
                    NearbyTrafficCard(traffic = nearbyContext.traffic)

                    Spacer(modifier = Modifier.height(10.dp))

                    // Local Incidents & News Card
                    NearbyNewsCard(newsList = nearbyContext.news)

                    Spacer(modifier = Modifier.height(10.dp))

                    // Nearby Events Card
                    NearbyEventsCard(eventsList = nearbyContext.events)
                }
            }

            // ==========================================
            // 5. PERSONAL MEMORIES (Stored Knowledge)
            // ==========================================
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.section_memories),
                        color = ElectricTeal,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    if (memories.isNotEmpty()) {
                        Surface(
                            color = DarkSurfaceVariant,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "${memories.size} saved",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            if (memories.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("🧠", fontSize = 28.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "No personal memories yet",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Talk to Comai in chat (e.g. \"My favorite programming language is Python\" or \"I usually leave college at 5 PM\") to build your local private memory.",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(memories, key = { it.id }) { memory ->
                    MemoryCardItem(
                        memory = memory,
                        onDelete = { viewModel.deleteMemory(memory.id) }
                    )
                }
            }
        }
    }

    // Schedule Details Dialog (reusing existing tested dialog with Edit/Delete/Toggle)
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

    // Add Plan Dialog (Prefilled with selected date, clock time picker, exact alarm)
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
private fun MemoryMonthHeaderBar(
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
                .padding(horizontal = 10.dp, vertical = 6.dp),
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
                    modifier = Modifier.padding(horizontal = 6.dp)
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
 * Monthly calendar grid with S M T W T F S day headers and event indicators.
 */
@Composable
private fun MemoryCalendarMonthGrid(
    displayedMonth: Calendar,
    selectedDate: Calendar,
    plans: List<PersonalPlan>,
    onDateSelected: (Calendar) -> Unit
) {
    val daysOfWeek = listOf("S", "M", "T", "W", "T", "F", "S")

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

    // Sunday = 0, Monday = 1, ..., Saturday = 6
    val firstDayOffset = remember(year, month) {
        val c = (displayedMonth.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val dow = c.get(Calendar.DAY_OF_WEEK)
        dow - Calendar.SUNDAY
    }

    Surface(
        color = Color(0xFF131722),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF222B3D)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Days of Week Header (S M T W T F S)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                daysOfWeek.forEach { dayName ->
                    Text(
                        text = dayName,
                        color = TextSecondary,
                        fontSize = 12.sp,
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

                            // Check plans for indicator dots
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
                            Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Prominently rendered Location Map Card showing "● You" marker, coordinates,
 * accuracy, and map controls.
 */
@Composable
private fun MemoryLocationMapCard(
    locationState: LocationState,
    isRefreshing: Boolean,
    onRequestPermission: () -> Unit,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMaps: (Double, Double) -> Unit
) {
    Surface(
        color = Color(0xFF131722),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF222B3D)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            when {
                // 1. Permission Denied State
                !locationState.isPermissionGranted -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1A2230)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = ElectricTeal,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.location_perm_needed),
                            color = Color.White,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onRequestPermission,
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricTeal),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.location_allow_btn),
                                color = DarkBackground,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // 2. Location Services Disabled State
                !locationState.isLocationServicesEnabled -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.LocationDisabled,
                            contentDescription = null,
                            tint = Color(0xFFFF8A80),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.location_disabled_desc),
                            color = Color(0xFFFF8A80),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = onOpenSettings,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.location_open_settings),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // 3. Location available: render offline OSM map + location info
                else -> {
                    // ── Offline OSM Map ──────────────────────────────────────
                    // Library: osmdroid 6.1.20 | Data: © OpenStreetMap contributors
                    // Tiles are cached in app-specific storage after first download.
                    // Map renders from cache when internet is OFF.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, Color(0xFF1E283A), RoundedCornerShape(14.dp))
                    ) {
                        OsmOfflineMapView(
                            latitude = locationState.latitude,
                            longitude = locationState.longitude,
                            modifier = Modifier.fillMaxSize(),
                            zoom = 15.0
                        )

                        // Fix-status badge (top-right)
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                        ) {
                            Surface(
                                color = if (locationState.isLiveFix) Color(0xFF0E2E20) else Color(0xFF1A2436),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, if (locationState.isLiveFix) OnlineGreen else Color(0xFF2E3D56))
                            ) {
                                Text(
                                    text = if (locationState.isLiveFix)
                                        stringResource(R.string.location_status_live)
                                    else
                                        stringResource(R.string.location_status_cached),
                                    color = if (locationState.isLiveFix) OnlineGreen else TextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // OSM attribution (bottom-left) — required by ODbL license
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(6.dp)
                        ) {
                            Surface(
                                color = Color(0xCC0C131F),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "© OpenStreetMap contributors",
                                    color = Color(0xFFB0C4DE),
                                    fontSize = 9.sp,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Location Name & Details
                    Text(
                        text = "Current location: ${locationState.placeName.ifBlank { "Locating your position…" }}",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            if (locationState.latitude != null && locationState.longitude != null) {
                                Text(
                                    text = "%.4f° N, %.4f° E".format(locationState.latitude, locationState.longitude),
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            locationState.accuracyMeters?.let { acc ->
                                Text(
                                    text = stringResource(R.string.location_accuracy, acc.toInt()),
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = onRefresh,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF2E3A52)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(stringResource(R.string.location_refresh), color = ElectricTeal, fontSize = 11.sp)
                            }

                            Button(
                                onClick = {
                                    if (locationState.latitude != null && locationState.longitude != null) {
                                        onOpenMaps(locationState.latitude, locationState.longitude)
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricTeal),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.location_open_maps),
                                    color = DarkBackground,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Traffic card displaying current commute conditions and delays.
 */
@Composable
private fun NearbyTrafficCard(traffic: com.comai.nearby.NearbyTraffic) {
    Surface(
        color = Color(0xFF131722),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFF222B3D)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.DirectionsCar,
                        contentDescription = null,
                        tint = when (traffic.level) {
                            TrafficLevel.NORMAL -> OnlineGreen
                            TrafficLevel.MODERATE -> Color(0xFFFFB74D)
                            TrafficLevel.HEAVY, TrafficLevel.INCIDENT -> Color(0xFFFF5252)
                        },
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.nearby_traffic),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    color = when (traffic.level) {
                        TrafficLevel.NORMAL -> Color(0xFF0E2E20)
                        TrafficLevel.MODERATE -> Color(0xFF2E2413)
                        TrafficLevel.HEAVY, TrafficLevel.INCIDENT -> Color(0xFF33161A)
                    },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = when (traffic.level) {
                            TrafficLevel.NORMAL -> "Normal"
                            TrafficLevel.MODERATE -> "+${traffic.delayMinutes}m Moderate"
                            TrafficLevel.HEAVY -> "+${traffic.delayMinutes}m Heavy"
                            TrafficLevel.INCIDENT -> "Disruption"
                        },
                        color = when (traffic.level) {
                            TrafficLevel.NORMAL -> OnlineGreen
                            TrafficLevel.MODERATE -> Color(0xFFFFB74D)
                            TrafficLevel.HEAVY, TrafficLevel.INCIDENT -> Color(0xFFFF8A80)
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (traffic.summary.isNotBlank()) traffic.summary else stringResource(R.string.nearby_traffic_normal),
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            traffic.incidentDescription?.let { incident ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = incident,
                    color = Color(0xFFFF8A80),
                    fontSize = 11.sp
                )
            }
        }
    }
}

/**
 * Local Incidents and Geographically relevant news card.
 */
@Composable
private fun NearbyNewsCard(newsList: List<com.comai.nearby.NearbyNewsItem>) {
    Surface(
        color = Color(0xFF131722),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFF222B3D)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Campaign,
                    contentDescription = null,
                    tint = ElectricTeal,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.nearby_news),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (newsList.isEmpty()) {
                Text(
                    text = stringResource(R.string.nearby_no_news),
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    newsList.forEach { item ->
                        Column {
                            Text(
                                text = item.title,
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.description,
                                color = TextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Nearby public events card.
 */
@Composable
private fun NearbyEventsCard(eventsList: List<com.comai.nearby.NearbyEventItem>) {
    Surface(
        color = Color(0xFF131722),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFF222B3D)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Celebration,
                    contentDescription = null,
                    tint = SoftPurple,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.nearby_events),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (eventsList.isEmpty()) {
                Text(
                    text = stringResource(R.string.nearby_no_events),
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    eventsList.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${item.locationName} • ${item.timeDesc}",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            Surface(
                                color = Color(0xFF241C30),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = item.category,
                                    color = SoftPurple,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MemoryCardItem(
    memory: Memory,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Surface(
                    color = when (memory.type) {
                        "PREFERENCE" -> Color(0xFF1E2A38)
                        "CONTEXT" -> Color(0xFF1A2E26)
                        "PERSONAL_KNOWLEDGE" -> Color(0xFF2E1A38)
                        else -> DarkSurfaceVariant
                    },
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = memory.categoryLabel().uppercase(Locale.ROOT),
                        color = when (memory.type) {
                            "PREFERENCE" -> ElectricTeal
                            "CONTEXT" -> OnlineGreen
                            "PERSONAL_KNOWLEDGE" -> SoftPurple
                            else -> TextSecondary
                        },
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = memory.toDisplayString(),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = DarkSurfaceVariant,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = memory.source,
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Local • Private",
                        color = OnlineGreen,
                        fontSize = 11.sp
                    )
                }
            }

            if (memory.isUserDeletable) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete memory",
                        tint = ErrorRed
                    )
                }
            }
        }
    }
}
