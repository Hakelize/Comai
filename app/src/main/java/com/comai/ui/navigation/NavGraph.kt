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
import com.comai.ui.screens.home.HomeViewModel
import com.comai.ui.screens.home.VoiceHomeScreen
import com.comai.ui.screens.memory.MemoryScreen
import com.comai.ui.screens.memory.MemoryViewModel
import com.comai.ui.screens.onboarding.OnboardingScreen
import com.comai.ui.screens.onboarding.OnboardingViewModel
import com.comai.ui.screens.profile.ProfileScreen
import com.comai.ui.screens.routine.RoutineScreen
import com.comai.voice.ComaiLanguage
import com.comai.voice.VoiceInteractionManager

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val CHAT = "chat"
    const val ROUTINE = "routine"
    const val PROFILE = "profile"
    const val AUDIO = "audio"
    const val DASHBOARD = "dashboard"
    const val MEMORY = "memory"
    const val CAPABILITY = "capability"
}

@Composable
fun ComaiNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Routes.HOME,
    homeViewModel: HomeViewModel,
    chatViewModel: ChatViewModel,
    audioViewModel: AudioViewModel,
    dashboardViewModel: DashboardViewModel,
    memoryViewModel: MemoryViewModel,
    capabilityViewModel: CapabilityViewModel,
    onboardingViewModel: OnboardingViewModel? = null,
    voiceManager: VoiceInteractionManager? = null,
    onLanguageChanged: (ComaiLanguage) -> Unit = {}
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Routes.ONBOARDING) {
            if (onboardingViewModel != null) {
                OnboardingScreen(
                    viewModel = onboardingViewModel,
                    onOnboardingFinished = { selectedLang ->
                        homeViewModel.refreshState()
                        onLanguageChanged(selectedLang)
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    }
                )
            }
        }

        composable(Routes.HOME) {
            VoiceHomeScreen(
                chatViewModel = chatViewModel,
                homeViewModel = homeViewModel,
                voiceManager = voiceManager,
                onNavigateToChat = { navController.navigate(Routes.CHAT) },
                onNavigateToRoutine = { navController.navigate(Routes.ROUTINE) },
                onNavigateToProfile = { navController.navigate(Routes.PROFILE) },
                onNavigateToMemory = { navController.navigate(Routes.MEMORY) },
                onNavigateToDashboard = { navController.navigate(Routes.DASHBOARD) },
                onLanguageChanged = onLanguageChanged
            )
        }

        composable(Routes.CHAT) {
            ChatScreen(
                viewModel = chatViewModel,
                voiceManager = voiceManager,
                onBack = { navController.popBackStack() },
                onNavigateToAudio = { navController.navigate(Routes.AUDIO) },
                onNavigateToDashboard = { navController.navigate(Routes.DASHBOARD) },
                onNavigateToMemory = { navController.navigate(Routes.MEMORY) }
            )
        }

        composable(Routes.ROUTINE) {
            RoutineScreen(
                homeViewModel = homeViewModel,
                onNavigateToHome = { navController.navigate(Routes.HOME) },
                onNavigateToChat = { navController.navigate(Routes.CHAT) },
                onNavigateToProfile = { navController.navigate(Routes.PROFILE) },
                onNavigateToMemory = { navController.navigate(Routes.MEMORY) }
            )
        }

        composable(Routes.PROFILE) {
            ProfileScreen(
                homeViewModel = homeViewModel,
                onNavigateToHome = { navController.navigate(Routes.HOME) },
                onNavigateToChat = { navController.navigate(Routes.CHAT) },
                onNavigateToRoutine = { navController.navigate(Routes.ROUTINE) },
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
                onNavigateToCapability = { navController.navigate(Routes.CAPABILITY) }
            )
        }

        composable(Routes.MEMORY) {
            MemoryScreen(
                viewModel = memoryViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToHome = { navController.navigate(Routes.HOME) },
                onNavigateToChat = { navController.navigate(Routes.CHAT) },
                onNavigateToRoutine = { navController.navigate(Routes.ROUTINE) },
                onNavigateToProfile = { navController.navigate(Routes.PROFILE) }
            )
        }

        composable(Routes.CAPABILITY) {
            CapabilityDashboardScreen(
                viewModel = capabilityViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
