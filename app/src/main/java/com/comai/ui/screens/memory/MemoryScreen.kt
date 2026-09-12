package com.comai.ui.screens.memory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.comai.contextengine.db.Memory
import com.comai.ui.components.ComaiBottomBar
import com.comai.ui.navigation.Routes
import com.comai.ui.theme.*

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
    val memories by viewModel.memories.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("Memory & Privacy", color = Color.White, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Comai Remembers",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "All personal knowledge is stored privately on your device. You can inspect or delete any memory at any time.",
                color = TextSecondary,
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (memories.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 48.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
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
                                    imageVector = Icons.Outlined.Lightbulb,
                                    contentDescription = null,
                                    tint = SoftPurple,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "No memories saved yet",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Talk to Comai in chat (e.g. \"I usually leave work around 5:30\" or \"I work at Tech Hub Office\") to build your local routine memory.",
                                color = TextSecondary,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val preferences = memories.filter { it.type == "PREFERENCE" }
                    val context = memories.filter { it.type == "CONTEXT" }
                    val other = memories.filter { it.type != "PREFERENCE" && it.type != "CONTEXT" }

                    if (preferences.isNotEmpty()) {
                        item {
                            Text(
                                text = "Preferences",
                                color = ElectricTeal,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        items(preferences, key = { it.id }) { memory ->
                            MemoryCardItem(
                                memory = memory,
                                onDelete = { viewModel.deleteMemory(memory.id) }
                            )
                        }
                    }

                    if (context.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Context",
                                color = ElectricTeal,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        items(context, key = { it.id }) { memory ->
                            MemoryCardItem(
                                memory = memory,
                                onDelete = { viewModel.deleteMemory(memory.id) }
                            )
                        }
                    }

                    if (other.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Personal Knowledge",
                                color = ElectricTeal,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        items(other, key = { it.id }) { memory ->
                            MemoryCardItem(
                                memory = memory,
                                onDelete = { viewModel.deleteMemory(memory.id) }
                            )
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
                val displayTitle = when (memory.key) {
                    "preferred_work_departure" -> "Usually leaves work at ${memory.value}"
                    "workplace" -> "Workplace: ${memory.value}"
                    "after_work_music" -> "Likes ${memory.value} music after work"
                    else -> "${memory.key}: ${memory.value}"
                }
                Text(
                    text = displayTitle,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
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
                        text = "Confidence: 100%",
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
