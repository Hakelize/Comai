package com.comai.ui.screens.routine

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.comai.ui.components.ComaiBottomBar
import com.comai.ui.navigation.Routes
import com.comai.ui.screens.home.HomeViewModel
import com.comai.ui.theme.*

@Composable
fun RoutineScreen(
    homeViewModel: HomeViewModel,
    onNavigateToHome: () -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToMemory: () -> Unit = {}
) {
    val homeState by homeViewModel.uiState.collectAsState()
    val profile = homeState.profile

    Scaffold(
        containerColor = DarkBackground,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            Column(modifier = Modifier.navigationBarsPadding()) {
                ComaiBottomBar(
                    currentRoute = Routes.ROUTINE,
                    onNavigate = { targetRoute ->
                        when (targetRoute) {
                            Routes.HOME -> onNavigateToHome()
                            Routes.CHAT -> onNavigateToChat()
                            Routes.PROFILE -> onNavigateToProfile()
                            Routes.MEMORY -> onNavigateToMemory()
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "DAILY ROUTINE",
                color = ElectricTeal,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Your Day at a Glance",
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Comai aligns with your natural schedule to help without getting in the way.",
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            // ── Current Context Card ("Right Now") ──────────────────
            Surface(
                color = Color(0xFF11151F),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Color(0xFF1E2638)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Navigation,
                                contentDescription = null,
                                tint = ElectricTeal,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = homeState.currentContextTitle,
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Surface(
                            color = Color(0xFF18232B),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = homeState.currentContextStatus,
                                color = ElectricTeal,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = homeState.currentContextDetail,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    // Contextual headline
                    if (homeState.contextualHeadline.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = homeState.contextualHeadline,
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Timeline Milestones ─────────────────────────────────
            val placeLabel = profile.placeName.ifBlank {
                profile.workplace.ifBlank { profile.college.ifBlank { "Workspace" } }
            }

            val milestoneItems = listOf(
                TimelineNode(
                    time = profile.wakeTime.ifBlank { "07:00" },
                    title = "Wake Up",
                    description = "Morning schedule baseline",
                    icon = Icons.Outlined.WbSunny
                ),
                TimelineNode(
                    time = profile.leaveHomeTime.ifBlank { "08:30" },
                    title = "Departure",
                    description = "Commute via ${profile.travelMode.ifBlank { "Car" }} to $placeLabel",
                    icon = Icons.Outlined.DirectionsCar
                ),
                TimelineNode(
                    time = "${profile.leaveHomeTime.ifBlank { "08:30" }} - ${profile.returnHomeTime.ifBlank { "18:00" }}",
                    title = if (profile.weekdayType == "College") "Campus Session" else "Workspace Session",
                    description = placeLabel,
                    icon = Icons.Outlined.Business
                ),
                TimelineNode(
                    time = profile.returnHomeTime.ifBlank { "18:00" },
                    title = "Return Home",
                    description = "Evening transition & rest",
                    icon = Icons.Outlined.Home
                ),
                TimelineNode(
                    time = profile.sleepTime.ifBlank { "23:00" },
                    title = "Rest & Recovery",
                    description = "Sleep schedule & wind-down",
                    icon = Icons.Outlined.Nightlight
                )
            )

            milestoneItems.forEachIndexed { index, node ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Left vertical track
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(36.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF161E2E)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = node.icon,
                                contentDescription = null,
                                tint = ElectricTeal,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        if (index < milestoneItems.size - 1) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(54.dp)
                                    .background(Color(0xFF222B3D))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    // Milestone card
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(bottom = if (index < milestoneItems.size - 1) 18.dp else 0.dp)
                    ) {
                        Surface(
                            color = Color(0xFF11151F),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFF1E2638)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = node.title,
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = node.time,
                                        color = ElectricTeal,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = node.description,
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── Intelligent Care Capabilities ───────────────────────
            Text(
                text = "Ambient Care Points",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "How Comai assists during routine events:",
                color = TextSecondary,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            CarePointCard(
                title = "Commute & Route Insights",
                detail = "Alerts you ahead of departure if unusual delays occur on your route.",
                icon = Icons.Outlined.Commute
            )

            Spacer(modifier = Modifier.height(8.dp))

            CarePointCard(
                title = "Late Work Check-in",
                detail = "Notice when you're working past your usual departure time and checks in calmly.",
                icon = Icons.Outlined.AccessTime
            )

            Spacer(modifier = Modifier.height(8.dp))

            CarePointCard(
                title = "Rest Preparation",
                detail = "Helps transition to wind-down mode before your designated sleep time.",
                icon = Icons.Outlined.Bedtime
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

private data class TimelineNode(
    val time: String,
    val title: String,
    val description: String,
    val icon: ImageVector
)

@Composable
private fun CarePointCard(
    title: String,
    detail: String,
    icon: ImageVector
) {
    Surface(
        color = Color(0xFF11151F),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFF1E2638)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF161E2E)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = SoftPurple,
                    modifier = Modifier.size(15.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = detail,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
