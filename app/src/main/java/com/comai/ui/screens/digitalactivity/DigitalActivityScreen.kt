package com.comai.ui.screens.digitalactivity

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.comai.R
import com.comai.digitalactivity.data.AppLimitEntity
import com.comai.digitalactivity.intelligence.DeviceActivityState
import com.comai.digitalactivity.model.AppCategory
import com.comai.digitalactivity.model.AppUsageInfo
import com.comai.digitalactivity.monitoring.UsagePermissionManager
import com.comai.ui.theme.DarkBackground
import com.comai.ui.theme.DarkSurface
import com.comai.ui.theme.DarkSurfaceVariant
import com.comai.ui.theme.ElectricTeal
import com.comai.ui.theme.TextPrimary
import com.comai.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DigitalActivityScreen(
    viewModel: DigitalActivityViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Automatically check permission when resuming screen
    DisposableEffect(Unit) {
        viewModel.refresh()
        onDispose {}
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.activity_title),
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
        ) {
            // ── 1. Permission Warning Banner ──────────────────────────
            if (!state.hasPermission) {
                item {
                    Surface(
                        color = Color(0xFF261D10),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0xFFD97706)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFFBBF24),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.activity_permission_required),
                                    color = Color(0xFFFDE68A),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.activity_permission_desc),
                                color = Color(0xFFF59E0B),
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = {
                                    context.startActivity(UsagePermissionManager.createSettingsIntent())
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFF59E0B),
                                    contentColor = Color(0xFF1F1206)
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text(stringResource(R.string.activity_grant_permission), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            // ── 2. Monitoring Switch Card ─────────────────────────────
            item {
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E2638)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.activity_monitoring_title),
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.activity_monitoring_desc),
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = state.isMonitoringEnabled,
                            onCheckedChange = { viewModel.setMonitoringEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ElectricTeal,
                                checkedTrackColor = ElectricTeal.copy(alpha = 0.3f),
                                uncheckedThumbColor = TextSecondary,
                                uncheckedTrackColor = Color(0xFF1E2638)
                            )
                        )
                    }
                }
            }

            // ── 3. Today's Screen Time Overview ───────────────────────
            item {
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E2638)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = stringResource(R.string.activity_today_screen_time),
                            color = ElectricTeal,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.snapshot.totalScreenTimeFormatted,
                            color = TextPrimary,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        val topApp = state.snapshot.topUsedApp
                        if (topApp != null) {
                            Text(
                                text = stringResource(R.string.activity_most_used, topApp.appName, topApp.usageFormatted),
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.activity_no_usage),
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // ── 4. Awake / Sleep & Active Inferred State Card ───────────
            item {
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E2638)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.activity_contextual_intelligence),
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            val stateColor = when (state.inferredState.state) {
                                DeviceActivityState.ACTIVE -> Color(0xFF10B981)
                                DeviceActivityState.LIKELY_AWAKE -> ElectricTeal
                                DeviceActivityState.RECENTLY_ACTIVE -> Color(0xFF38BDF8)
                                DeviceActivityState.LIKELY_ASLEEP -> Color(0xFFA855F7)
                                DeviceActivityState.INACTIVE -> Color(0xFF94A3B8)
                                DeviceActivityState.UNKNOWN -> TextSecondary
                            }
                            Surface(
                                color = stateColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, stateColor)
                            ) {
                                Text(
                                    text = state.inferredState.state.displayName.uppercase(),
                                    color = stateColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = when (state.inferredState.state) {
                                    DeviceActivityState.LIKELY_AWAKE -> Icons.Outlined.WbSunny
                                    DeviceActivityState.LIKELY_ASLEEP -> Icons.Outlined.Bedtime
                                    DeviceActivityState.ACTIVE -> Icons.Outlined.TouchApp
                                    else -> Icons.Outlined.PhoneAndroid
                                },
                                contentDescription = null,
                                tint = ElectricTeal,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                val confidencePct = (state.inferredState.confidence * 100).toInt()
                                Text(
                                    text = "${state.inferredState.state.displayName} (${confidencePct}% confidence)",
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Reason: ${formatReason(state.inferredState.reason)} • Last active: ${state.inferredState.lastActivityFormatted}",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            // ── 5. Learned Routine Patterns ───────────────────────────
            item {
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E2638)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "LEARNED ROUTINE PATTERNS",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "${state.learnedRoutine.observationCount} days observed",
                                color = ElectricTeal,
                                fontSize = 11.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Typical Morning Start",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = state.learnedRoutine.typicalMorningStartFormatted,
                                    color = TextPrimary,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Typical Night Rest",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = state.learnedRoutine.typicalNightRestFormatted,
                                    color = TextPrimary,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // ── 6. Category Breakdown ─────────────────────────────────
            item {
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E2638)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = stringResource(R.string.activity_categories),
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        if (state.snapshot.categoryBreakdown.isEmpty()) {
                            Text(
                                text = "No category data available yet.",
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        } else {
                            state.snapshot.categoryBreakdown.take(6).forEach { categorySummary ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = categorySummary.category.displayName,
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = categorySummary.durationFormatted,
                                            color = ElectricTeal,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "(${categorySummary.percentage.toInt()}%)",
                                            color = TextSecondary,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── 7. App Limits & Productivity ──────────────────────────
            item {
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E2638)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.activity_productivity_limits),
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            IconButton(onClick = { viewModel.setShowAddLimitDialog(true) }) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = stringResource(R.string.activity_add_limit),
                                    tint = ElectricTeal,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        if (state.limits.isEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "No app limits configured. Tap '+' above to set daily limits.",
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        } else {
                            val usageMap = state.snapshot.appUsageList.associate { it.packageName to it.usageMinutes }
                            state.limits.forEach { limit ->
                                val usedMinutes = usageMap[limit.packageName] ?: 0L
                                val isExceeded = usedMinutes >= limit.dailyLimitMinutes
                                val progress = (usedMinutes.toFloat() / limit.dailyLimitMinutes).coerceIn(0f, 1f)

                                Spacer(modifier = Modifier.height(10.dp))
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF0C0F17), RoundedCornerShape(12.dp))
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = limit.appName,
                                            color = TextPrimary,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (isExceeded) {
                                                Surface(
                                                    color = Color(0xFFEF4444).copy(alpha = 0.15f),
                                                    shape = RoundedCornerShape(6.dp),
                                                    border = BorderStroke(1.dp, Color(0xFFEF4444))
                                                ) {
                                                    Text(
                                                        text = "LIMIT EXCEEDED",
                                                        color = Color(0xFFEF4444),
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                            }
                                            IconButton(
                                                onClick = { viewModel.deleteLimit(limit.packageName) },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = stringResource(R.string.delete),
                                                    tint = TextSecondary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "${usedMinutes}m / ${limit.dailyLimitMinutes}m",
                                        color = if (isExceeded) Color(0xFFEF4444) else ElectricTeal,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = if (isExceeded) Color(0xFFEF4444) else ElectricTeal,
                                        trackColor = Color(0xFF1A2130),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── 8. Top Applications Today ─────────────────────────────
            item {
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E2638)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = stringResource(R.string.activity_top_apps),
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        if (state.snapshot.appUsageList.isEmpty()) {
                            Text(
                                text = "No app usage detected for today.",
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        } else {
                            val maxDuration = state.snapshot.appUsageList.firstOrNull()?.usageDurationMs?.toFloat() ?: 1f
                            state.snapshot.appUsageList.take(8).forEach { app ->
                                val progress = (app.usageDurationMs / maxDuration).coerceIn(0f, 1f)
                                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = app.appName,
                                                color = TextPrimary,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Surface(
                                                color = Color(0xFF151C28),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = app.category.displayName,
                                                    color = TextSecondary,
                                                    fontSize = 10.sp,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = app.usageFormatted,
                                            color = ElectricTeal,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp)),
                                        color = ElectricTeal,
                                        trackColor = Color(0xFF1A2130),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── 9. Privacy Controls / Clear History ───────────────────
            item {
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E2638)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "PRIVACY & LOCAL DATA",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "All activity records and learned patterns remain strictly on this device. You can erase all recorded routine patterns at any time.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedButton(
                            onClick = { viewModel.setShowClearHistoryDialog(true) },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFEF4444)
                            ),
                            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Clear Activity History", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────
    if (state.showAddLimitDialog) {
        AddAppLimitDialog(
            availableApps = state.snapshot.appUsageList,
            onDismiss = { viewModel.setShowAddLimitDialog(false) },
            onConfirm = { pkg, name, minutes ->
                viewModel.addOrUpdateLimit(pkg, name, minutes)
                viewModel.setShowAddLimitDialog(false)
            }
        )
    }

    if (state.showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.setShowClearHistoryDialog(false) },
            containerColor = Color(0xFF11151F),
            title = {
                Text("Clear Activity History?", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "This will delete all locally stored daily activity summaries and learned routine patterns. Configured app limits will be kept.",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearHistory()
                        viewModel.setShowClearHistoryDialog(false)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Clear History", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setShowClearHistoryDialog(false) }) {
                    Text(stringResource(R.string.cancel), color = TextSecondary)
                }
            }
        )
    }
}

@Composable
fun AddAppLimitDialog(
    availableApps: List<AppUsageInfo>,
    onDismiss: () -> Unit,
    onConfirm: (packageName: String, appName: String, limitMinutes: Int) -> Unit
) {
    var selectedPackage by remember { mutableStateOf(availableApps.firstOrNull()?.packageName ?: "") }
    var selectedAppName by remember { mutableStateOf(availableApps.firstOrNull()?.appName ?: "") }
    var customPackage by remember { mutableStateOf("") }
    var customAppName by remember { mutableStateOf("") }
    var selectedMinutes by remember { mutableIntStateOf(60) }

    val minutePresets = listOf(15, 30, 45, 60, 90, 120)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF11151F),
        title = {
            Text("Set App Usage Limit", color = TextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Select Application", color = TextSecondary, fontSize = 12.sp)

                if (availableApps.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 140.dp)
                            .background(Color(0xFF0C0F17), RoundedCornerShape(10.dp))
                            .padding(8.dp)
                    ) {
                        LazyColumn {
                            items(availableApps.take(10)) { app ->
                                val isSelected = selectedPackage == app.packageName
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) ElectricTeal.copy(alpha = 0.2f) else Color.Transparent)
                                        .clickable {
                                            selectedPackage = app.packageName
                                            selectedAppName = app.appName
                                        }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = app.appName,
                                        color = if (isSelected) ElectricTeal else TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    Text(
                                        text = app.usageFormatted,
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = customAppName,
                        onValueChange = {
                            customAppName = it
                            selectedAppName = it
                            selectedPackage = "custom." + it.lowercase().replace(" ", "")
                        },
                        label = { Text("App Name (e.g. YouTube)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Text("Daily Limit", color = TextSecondary, fontSize = 12.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    minutePresets.take(4).forEach { min ->
                        val isSelected = selectedMinutes == min
                        Surface(
                            color = if (isSelected) ElectricTeal else Color(0xFF161B26),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable { selectedMinutes = min }
                        ) {
                            Text(
                                text = "${min}m",
                                color = if (isSelected) Color(0xFF090C13) else TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start
                ) {
                    minutePresets.drop(4).forEach { min ->
                        val isSelected = selectedMinutes == min
                        Surface(
                            color = if (isSelected) ElectricTeal else Color(0xFF161B26),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .clickable { selectedMinutes = min }
                                .padding(end = 8.dp)
                        ) {
                            val label = if (min >= 60) "${min / 60}h" else "${min}m"
                            Text(
                                text = label,
                                color = if (isSelected) Color(0xFF090C13) else TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val pkg = selectedPackage.ifBlank { "custom.app" }
                    val name = selectedAppName.ifBlank { "App" }
                    onConfirm(pkg, name, selectedMinutes)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricTeal,
                    contentColor = DarkBackground
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(stringResource(R.string.save), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = TextSecondary)
            }
        }
    )
}

private fun formatReason(reason: String): String {
    return when (reason) {
        "RECENT_DEVICE_ACTIVITY" -> "Active usage"
        "ACTIVITY_WITHIN_LAST_20_MINUTES" -> "Used recently"
        "FIRST_MORNING_ACTIVITY" -> "Morning activity"
        "PROLONGED_NIGHT_INACTIVITY" -> "Night inactivity"
        "DAYTIME_INACTIVITY", "PROLONGED_DAY_INACTIVITY" -> "Daytime inactivity"
        "NO_USAGE_PERMISSION" -> "Permission needed"
        "MONITORING_DISABLED" -> "Monitoring off"
        else -> reason.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
    }
}
