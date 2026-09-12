package com.comai.ui.screens.schedule

import android.Manifest
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.EventNote
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
import androidx.core.content.ContextCompat
import com.comai.data.models.PersonalPlan
import com.comai.ui.components.ComaiBottomBar
import com.comai.ui.navigation.Routes
import com.comai.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalScheduleScreen(
    viewModel: PersonalScheduleViewModel,
    onBack: () -> Unit,
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

    val plans by viewModel.plans.collectAsState()
    var planToEdit by remember { mutableStateOf<PersonalPlan?>(null) }
    var isAddingNewPlan by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("Personal Schedule", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { isAddingNewPlan = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Plan",
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
                text = "Time-Based Routines",
                color = ElectricTeal,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Your Personal Plans",
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Comai keeps track of what you intend to do throughout the day.",
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

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
                                text = "Enable notifications to receive your scheduled reminders on time.",
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
                            Text("Enable", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (plans.isEmpty()) {
                EmptyPlanView(
                    onAddPlan = { isAddingNewPlan = true },
                    onQuickAdd = { title, time ->
                        viewModel.addPlan(title, time, "Daily")
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

    // Add Plan Dialog
    if (isAddingNewPlan) {
        PlanDialog(
            initialPlan = null,
            onDismiss = { isAddingNewPlan = false },
            onSave = { title, time, repeatFrequency ->
                viewModel.addPlan(title, time, repeatFrequency)
                isAddingNewPlan = false
            }
        )
    }

    // Edit Plan Dialog
    planToEdit?.let { plan ->
        PlanDialog(
            initialPlan = plan,
            onDismiss = { planToEdit = null },
            onSave = { title, time, repeatFrequency ->
                viewModel.updatePlan(plan.id, title, time, repeatFrequency, plan.isEnabled)
                planToEdit = null
            }
        )
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
                    contentDescription = "Edit",
                    tint = TextSecondary,
                    modifier = Modifier.size(17.dp)
                )
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = ErrorRed.copy(alpha = 0.8f),
                    modifier = Modifier.size(17.dp)
                )
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
            text = "No Personal Plans Yet",
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Add plans and daily goals for Comai to keep in mind.",
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
            Text("Create a Plan", color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "Suggested Plans",
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
