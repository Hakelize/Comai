package com.comai.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.comai.ui.screens.audio.AudioCanvasScreen
import com.comai.ui.screens.audio.AudioViewModel
import com.comai.ui.screens.capability.CapabilityDashboardScreen
import com.comai.ui.screens.capability.CapabilityViewModel
import com.comai.ui.screens.chat.ChatScreen
import com.comai.ui.screens.chat.ChatViewModel
import com.comai.ui.screens.dashboard.DashboardScreen
import com.comai.ui.screens.dashboard.DashboardViewModel
import com.comai.ui.screens.memory.MemoryScreen
import com.comai.ui.screens.memory.MemoryViewModel
import com.comai.voice.VoiceInteractionManager

import com.comai.ui.screens.ramdashboard.RamContextDashboardScreen
import com.comai.ui.screens.ramdashboard.RamContextDashboardViewModel

object Routes {
    const val CHAT = "chat"
    const val AUDIO = "audio"
    const val DASHBOARD = "dashboard"
    const val MEMORY = "memory"
    const val CAPABILITY = "capability"
    const val RAM_DASHBOARD = "ram_dashboard"
}

@Composable
fun ComaiNavGraph(
    navController: NavHostController = rememberNavController(),
    chatViewModel: ChatViewModel,
    audioViewModel: AudioViewModel,
    dashboardViewModel: DashboardViewModel,
    memoryViewModel: MemoryViewModel,
    capabilityViewModel: CapabilityViewModel,
    voiceManager: VoiceInteractionManager? = null
) {
    NavHost(
        navController = navController,
        startDestination = Routes.CHAT
    ) {
        composable(Routes.CHAT) {
            ChatScreen(
                viewModel = chatViewModel,
                voiceManager = voiceManager,
                onNavigateToAudio = { navController.navigate(Routes.AUDIO) },
                onNavigateToDashboard = { navController.navigate(Routes.DASHBOARD) },
                onNavigateToMemory = { navController.navigate(Routes.MEMORY) }
            )
        }

        composable(Routes.AUDIO) {
            AudioCanvasScreen(
                viewModel = audioViewModel,
                voiceManager = voiceManager,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.DASHBOARD) {
            DashboardScreen(
                viewModel = dashboardViewModel,
                onBackToChat = { navController.popBackStack() },
                onNavigateToMemory = { navController.navigate(Routes.MEMORY) },
                onNavigateToCapability = { navController.navigate(Routes.CAPABILITY) },
                onNavigateToRamDashboard = { navController.navigate(Routes.RAM_DASHBOARD) }
            )
        }

        composable(Routes.MEMORY) {
            MemoryScreen(
                viewModel = memoryViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.CAPABILITY) {
            CapabilityDashboardScreen(
                viewModel = capabilityViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.RAM_DASHBOARD) {
            val ramViewModel: RamContextDashboardViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            RamContextDashboardScreen(
                viewModel = ramViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
