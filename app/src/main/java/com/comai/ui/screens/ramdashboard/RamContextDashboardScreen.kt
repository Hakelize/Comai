package com.comai.ui.screens.ramdashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.comai.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RamContextDashboardScreen(
    viewModel: RamContextDashboardViewModel,
    onBack: () -> Unit
) {
    val result by viewModel.contextResult.collectAsState()
    val capabilities by viewModel.capabilities.collectAsState()
    val pipelineStages by viewModel.pipelineStages.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "RAM Context Dashboard",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Person 1 — Technical Inspection Console",
                            color = ElectricTeal,
                            fontSize = 11.sp
                        )
                    }
                },
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
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = ElectricTeal
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
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // ─── SECTION 1: CURRENT CONTEXT ─────────────────────────────────
            CurrentContextSection(result = result)

            // ─── SECTION 2: DEVICE CAPABILITIES ──────────────────────────────
            DeviceCapabilitiesSection(capabilities = capabilities)

            // ─── SECTION 3: CONTEXT PIPELINE ─────────────────────────────────
            ContextPipelineSection(stages = pipelineStages)
        }
    }
}

@Composable
private fun CurrentContextSection(result: com.comai.contextengine.models.ContextProcessingResult?) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "CURRENT CONTEXT",
                color = ElectricTeal,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "PERSON 1",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                val loc = result?.deviceContext?.location
                val placeName = loc?.currentPlace?.name ?: loc?.locationCategory ?: "Unknown Location"
                val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                val timeLabel = "$timeStr (${result?.deviceContext?.time?.formattedTime ?: "Daytime"})"
                val activityStr = "${result?.deviceContext?.activity?.activityType ?: "STILL"} (${result?.deviceContext?.activity?.confidencePercentage ?: 90}%)"
                val isDeviation = result?.routineContext?.isRoutineDeviation == true
                val deviationStr = if (isDeviation) {
                    "YES (+${result?.routineContext?.deviationMinutes ?: 0}m: ${result?.routineContext?.deviationReason ?: "Deviation"})"
                } else {
                    "NO (On Schedule)"
                }
                val confScore = ((result?.confidence?.confidenceScore ?: 0.85f) * 100).toInt()
                val confLevel = result?.confidence?.confidenceLevel?.name ?: "HIGH"

                ContextItemRow(
                    icon = "📍",
                    label = "Current location/place",
                    value = placeName
                )
                HorizontalDivider(color = DividerColor, thickness = 1.dp)

                ContextItemRow(
                    icon = "🕐",
                    label = "Current time",
                    value = timeLabel
                )
                HorizontalDivider(color = DividerColor, thickness = 1.dp)

                ContextItemRow(
                    icon = "🚶",
                    label = "Current activity",
                    value = activityStr
                )
                HorizontalDivider(color = DividerColor, thickness = 1.dp)

                ContextItemRow(
                    icon = "📊",
                    label = "Routine deviation",
                    value = deviationStr,
                    badgeColor = if (isDeviation) WarmAmber else SuccessGreen
                )
                HorizontalDivider(color = DividerColor, thickness = 1.dp)

                ContextItemRow(
                    icon = "🎯",
                    label = "Context confidence",
                    value = "$confScore% ($confLevel)",
                    badgeColor = when (confLevel) {
                        "HIGH" -> SuccessGreen
                        "MEDIUM" -> WarmAmber
                        else -> ErrorRed
                    }
                )
            }
        }
    }
}

@Composable
private fun ContextItemRow(
    icon: String,
    label: String,
    value: String,
    badgeColor: Color? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = icon, fontSize = 16.sp)
            Text(text = label, color = TextSecondary, fontSize = 13.sp)
        }
        if (badgeColor != null) {
            Surface(
                color = badgeColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f))
            ) {
                Text(
                    text = value,
                    color = badgeColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        } else {
            Text(
                text = value,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun DeviceCapabilitiesSection(capabilities: List<DeviceCapabilityItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "DEVICE CAPABILITIES",
            color = ElectricTeal,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                capabilities.forEachIndexed { index, cap ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = cap.name,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = cap.details,
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }

                        CapabilityStatusBadge(status = cap.status)
                    }

                    if (index < capabilities.size - 1) {
                        HorizontalDivider(color = DividerColor, thickness = 1.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CapabilityStatusBadge(status: String) {
    val (bgColor, textColor, label) = when (status) {
        "AVAILABLE" -> Triple(SuccessGreen.copy(alpha = 0.15f), SuccessGreen, "AVAILABLE")
        "UNAVAILABLE" -> Triple(ErrorRed.copy(alpha = 0.15f), ErrorRed, "UNAVAILABLE")
        "PERMISSION REQUIRED" -> Triple(WarmAmber.copy(alpha = 0.15f), WarmAmber, "PERMISSION REQUIRED")
        "OPTIONAL" -> Triple(SoftPurple.copy(alpha = 0.15f), SoftPurple, "OPTIONAL")
        else -> Triple(DarkSurfaceVariant, TextSecondary, status)
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, textColor.copy(alpha = 0.4f))
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun ContextPipelineSection(stages: List<PipelineStageItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "CONTEXT PIPELINE",
            color = ElectricTeal,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                stages.forEachIndexed { index, stage ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Stage Indicator Dot & Line
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(ElectricTeal.copy(alpha = 0.2f))
                                    .border(1.dp, ElectricTeal, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    color = ElectricTeal,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (index < stages.size - 1) {
                                Box(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .height(36.dp)
                                        .background(ElectricTeal.copy(alpha = 0.3f))
                                )
                            }
                        }

                        // Stage Info
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stage.stageName,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Surface(
                                    color = DarkSurfaceVariant,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = stage.status,
                                        color = ElectricTeal,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stage.summary,
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
