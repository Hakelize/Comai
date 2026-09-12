package com.comai.ui.screens.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
fun ProfileScreen(
    homeViewModel: HomeViewModel,
    onNavigateToHome: () -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToRoutine: () -> Unit,
    onNavigateToMemory: () -> Unit
) {
    val homeState by homeViewModel.uiState.collectAsState()
    val profile = homeState.profile

    Scaffold(
        containerColor = DarkBackground,
        contentWindowInsets = WindowInsets(0),
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
                text = "USER PROFILE",
                color = ElectricTeal,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = profile.name.ifBlank { "User" },
                color = TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Personalized preferences and schedule for Comai assistance.",
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── Primary Details ───────────────────────────────────────
            Surface(
                color = Color(0xFF11151F),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Color(0xFF1E2638)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "General Settings",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    ProfileItemRow(
                        label = "Preferred Language",
                        value = profile.preferredLanguage.displayName,
                        icon = Icons.Outlined.Translate
                    )

                    HorizontalDivider(color = Color(0xFF1A2130), modifier = Modifier.padding(vertical = 10.dp))

                    ProfileItemRow(
                        label = "Weekday Type",
                        value = profile.weekdayType,
                        icon = Icons.Outlined.Work
                    )

                    HorizontalDivider(color = Color(0xFF1A2130), modifier = Modifier.padding(vertical = 10.dp))

                    val placeLabel = profile.placeName.ifBlank {
                        profile.workplace.ifBlank { profile.college.ifBlank { "Not set" } }
                    }
                    ProfileItemRow(
                        label = if (profile.weekdayType == "College") "College / Campus" else "Workplace / Office",
                        value = placeLabel,
                        icon = Icons.Outlined.LocationOn
                    )

                    HorizontalDivider(color = Color(0xFF1A2130), modifier = Modifier.padding(vertical = 10.dp))

                    ProfileItemRow(
                        label = "Travel Mode",
                        value = profile.travelMode,
                        icon = Icons.Outlined.DirectionsCar
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Schedule Summary ──────────────────────────────────────
            Surface(
                color = Color(0xFF11151F),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Color(0xFF1E2638)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Daily Baseline Schedule",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    ScheduleItemRow(label = "Wake-up Time", time = profile.wakeTime.ifBlank { "07:00" }, icon = Icons.Outlined.WbSunny)
                    ScheduleItemRow(label = "Leave Home", time = profile.leaveHomeTime.ifBlank { "08:30" }, icon = Icons.Outlined.Logout)
                    ScheduleItemRow(label = "Return Home", time = profile.returnHomeTime.ifBlank { "18:00" }, icon = Icons.Outlined.Home)
                    ScheduleItemRow(label = "Sleep Time", time = profile.sleepTime.ifBlank { "23:00" }, icon = Icons.Outlined.Nightlight)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Memories Quick Link ───────────────────────────────────
            Surface(
                color = Color(0xFF11151F),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF1E2638)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToMemory() }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF161E2E)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Psychology,
                            contentDescription = null,
                            tint = SoftPurple,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Memory Vault",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Review and manage information Comai remembers",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Privacy Badge ─────────────────────────────────────────
            Surface(
                color = Color(0xFF0F151B),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFF1A262E)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = ElectricTeal,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "All routine context and memories are processed on this device. Comai does not upload your personal profile to external clouds.",
                        color = Color(0xFFB0C4DE),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ProfileItemRow(
    label: String,
    value: String,
    icon: ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ElectricTeal,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                color = TextSecondary,
                fontSize = 13.sp
            )
        }
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ScheduleItemRow(
    label: String,
    time: String,
    icon: ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = SoftPurple,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                color = TextSecondary,
                fontSize = 13.sp
            )
        }
        Text(
            text = time,
            color = ElectricTeal,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
