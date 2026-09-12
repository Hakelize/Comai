package com.comai.ui.screens.capability

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.comai.capability.CapabilityStatus
import com.comai.capability.CapabilityType
import com.comai.capability.DeviceCapability
import com.comai.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapabilityDashboardScreen(
    viewModel: CapabilityViewModel,
    onBack: () -> Unit
) {
    val deviceInfo by viewModel.deviceInfo.collectAsState()
    val capabilities by viewModel.capabilities.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device Capability Audit", color = Color.White, fontSize = 18.sp) },
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
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // Device Hardware Specs Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "${deviceInfo.manufacturer} ${deviceInfo.model}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Android ${deviceInfo.androidVersion} (API ${deviceInfo.apiLevel})",
                            color = ElectricTeal,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("SoC / Hardware: ${deviceInfo.hardware}", color = TextSecondary, fontSize = 12.sp)
                            Text("RAM: ${deviceInfo.availableRam} / ${deviceInfo.totalRam}", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Summary Counters
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val availableCount = capabilities.count { it.status == CapabilityStatus.AVAILABLE || it.status == CapabilityStatus.GRANTED }
                    val notGrantedCount = capabilities.count { it.status == CapabilityStatus.NOT_GRANTED }
                    val restrictedCount = capabilities.count { it.status == CapabilityStatus.RESTRICTED }

                    StatusCountPill(label = "Granted / Ready", count = availableCount, color = OnlineGreen, modifier = Modifier.weight(1f))
                    StatusCountPill(label = "Not Granted", count = notGrantedCount, color = WarmAmber, modifier = Modifier.weight(1f))
                    StatusCountPill(label = "Restricted", count = restrictedCount, color = ErrorRed, modifier = Modifier.weight(1f))
                }
            }

            // Capability Items
            items(capabilities) { capability ->
                CapabilityCardItem(capability = capability)
            }
        }
    }
}

@Composable
fun StatusCountPill(
    label: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = count.toString(), color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(text = label, color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun CapabilityCardItem(capability: DeviceCapability) {
    val statusColor = when (capability.status) {
        CapabilityStatus.AVAILABLE, CapabilityStatus.GRANTED -> OnlineGreen
        CapabilityStatus.NOT_GRANTED -> WarmAmber
        CapabilityStatus.RESTRICTED -> Color(0xFFFF5252)
        CapabilityStatus.UNSUPPORTED -> Color.Gray
    }

    val typeBadgeBg = when (capability.type) {
        CapabilityType.PLATFORM_HARDWARE -> Color(0xFF1E3A5F)
        CapabilityType.RUNTIME_PERMISSION -> Color(0xFF3E2723)
        CapabilityType.SPECIAL_ACCESS -> Color(0xFF4A148C)
        CapabilityType.SYSTEM_RESTRICTION -> Color(0xFF37474F)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Row 1: Capability Name + Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = capability.name,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )

                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = capability.status.name,
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Row 2: Category Badge + Permission/Access String
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = typeBadgeBg,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = capability.type.displayName,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = capability.permissionOrAccess,
                    color = ElectricTeal,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Row 3: Details String
            Text(
                text = capability.details,
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
    }
}
