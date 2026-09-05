package com.lifeloop.comai.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lifeloop.comai.ui.screens.audio.AudioCanvasScreen
import com.lifeloop.comai.ui.screens.audio.AudioViewModel
import com.lifeloop.comai.ui.screens.chat.ChatScreen
import com.lifeloop.comai.ui.screens.chat.ChatViewModel
import com.lifeloop.comai.ui.screens.dashboard.DashboardScreen
import com.lifeloop.comai.ui.screens.dashboard.DashboardViewModel

object Routes {
    const val CHAT = "chat"
    const val AUDIO = "audio"
    const val DASHBOARD = "dashboard"
}

@Composable
fun CamoiNavGraph(
    navController: NavHostController = rememberNavController(),
    chatViewModel: ChatViewModel,
    audioViewModel: AudioViewModel,
    dashboardViewModel: DashboardViewModel
) {
    NavHost(
        navController = navController,
        startDestination = Routes.CHAT
    ) {
        composable(Routes.CHAT) {
            ChatScreen(
                viewModel = chatViewModel,
                onNavigateToAudio = { navController.navigate(Routes.AUDIO) },
                onNavigateToDashboard = { navController.navigate(Routes.DASHBOARD) }
            )
        }

        composable(Routes.AUDIO) {
            AudioCanvasScreen(
                viewModel = audioViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.DASHBOARD) {
            DashboardScreen(
                viewModel = dashboardViewModel,
                onBackToChat = { navController.popBackStack() }
            )
        }
    }
}
