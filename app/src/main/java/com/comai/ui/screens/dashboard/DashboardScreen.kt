package com.comai.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.comai.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onBackToChat: () -> Unit,
    onNavigateToMemory: () -> Unit = {},
    onNavigateToCapability: () -> Unit = {},
    onNavigateToRamDashboard: () -> Unit = {}
) {
    val selectedTime by viewModel.selectedTime.collectAsState()
    val selectedLocation by viewModel.selectedLocation.collectAsState()
    val routineDeviation by viewModel.routineDeviation.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Developer Override Console", color = Color.White, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBackToChat) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Engine Status Card
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Active Engine", color = TextSecondary, fontSize = 12.sp)
                        Text(viewModel.getEngineName(), color = ElectricTeal, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Surface(
                        color = OnlineGreen.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "STABLE",
                            color = OnlineGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Time Override Selector
            Text("Override Time Context", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("morning", "noon", "evening", "night").forEach { time ->
                    val isSelected = selectedTime == time
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setTime(time) },
                        label = { Text(time.capitalize()) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = UserBubbleColor,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            // Routine Deviation Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurface)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Routine Deviation Flag", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("Simulate user waking early or leaving office late", color = TextSecondary, fontSize = 12.sp)
                }
                Switch(
                    checked = routineDeviation,
                    onCheckedChange = { viewModel.setDeviation(it) }
                )
            }

            // Technical Dashboards & Scenarios
            Text("Developer Technical Tools & Scenarios", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)

            Button(
                onClick = onNavigateToRamDashboard,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricTeal),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("📍 Person 1 — RAM Context Pipeline Dashboard", color = Color.Black, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onNavigateToMemory,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = UserBubbleColor),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("🧠 Inspect & Manage Stored Memories", color = Color.White, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onNavigateToCapability,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("📊 Device Capability & Security Audit", color = Color.White, fontWeight = FontWeight.Bold)
            }

            val scenarios = listOf(
                Triple("Morning Wakeup", "morning", "☀️ Morning: Early Wakeup & Routine"),
                Triple("Commute Traffic", "commute", "🚗 Commute: Route Traffic Check"),
                Triple("Tribe Finder Event", "tribe event", "🏃 Tribe: Run Club Suggestion"),
                Triple("Lunch Routine", "lunch", "🍽️ Midday: Lunch Assist"),
                Triple("Office Late Check-in", "late leaving office", "👋 Evening: Routine Deviation Check"),
                Triple("Night Medication", "night medication", "🌙 Night: Wind-down & Meds"),
                Triple("Memory & Privacy", "what do you remember about me", "🧠 Memory: Privacy Inspection")
            )

            scenarios.forEach { (task, prompt, title) ->
                Button(
                    onClick = {
                        viewModel.triggerStoryBeat(task, prompt)
                        onBackToChat()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(title, color = Color.White, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}
