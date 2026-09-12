package com.comai

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.comai.capability.CapabilityAuditor
import com.comai.ui.navigation.ComaiNavGraph
import com.comai.ui.screens.audio.AudioViewModel
import com.comai.ui.screens.capability.CapabilityViewModel
import com.comai.ui.screens.chat.ChatViewModel
import com.comai.ui.screens.dashboard.DashboardViewModel
import com.comai.ui.screens.home.HomeViewModel
import com.comai.ui.screens.memory.MemoryViewModel
import com.comai.ui.theme.ComaiTheme
import com.comai.voice.LanguagePreferences
import com.comai.voice.TextNormalizer
import com.comai.voice.VoiceInteractionManager
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var chatViewModel: ChatViewModel? = null
    private var voiceInteractionManager: VoiceInteractionManager? = null

    private val requestAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            voiceInteractionManager?.startListening()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as ComaiApplication
        val aiEngine = app.aiEngine
        val ttsManager = app.ttsManager
        val contextBridge = app.contextBridge
        val memoryRepository = app.memoryRepository

        // Start the Context Engine Foreground Service
        val serviceIntent = Intent(this, com.comai.contextengine.service.ContextForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        // Multilingual support
        val textNormalizer = TextNormalizer()
        val languagePrefs = LanguagePreferences(this)
        val savedLanguage = languagePrefs.getLanguage()

        // Factory to supply custom dependencies to ViewModels
        val chatViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ChatViewModel(aiEngine, ttsManager, contextBridge, memoryRepository, textNormalizer) as T
            }
        })[ChatViewModel::class.java]

        // Restore saved language into ChatViewModel
        chatViewModel.setLanguage(savedLanguage)

        val audioViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return AudioViewModel(aiEngine, ttsManager) as T
            }
        })[AudioViewModel::class.java]

        val dashboardViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return DashboardViewModel(aiEngine, ttsManager, chatViewModel) as T
            }
        })[DashboardViewModel::class.java]

        val memoryViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return MemoryViewModel(memoryRepository) as T
            }
        })[MemoryViewModel::class.java]

        val capabilityAuditor = CapabilityAuditor(this)
        val capabilityViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return CapabilityViewModel(capabilityAuditor) as T
            }
        })[CapabilityViewModel::class.java]

        // Native push-to-talk voice manager
        val voiceManager = VoiceInteractionManager(
            context = this,
            ttsManager = ttsManager,
            scope = lifecycleScope,
            onSpeechRecognized = { recognizedText ->
                chatViewModel.sendMessage(recognizedText)
            }
        ).apply {
            onPermissionRequired = {
                requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        // Restore saved language into voiceManager and ttsManager
        voiceManager.setLanguage(savedLanguage)
        ttsManager.setLanguage(savedLanguage.ttsLocale)

        this.chatViewModel = chatViewModel
        this.voiceInteractionManager = voiceManager

        handleEvaluationIntent(intent)

        // Onboarding and User Profile preferences
        val onboardingPreferences = com.comai.ui.screens.onboarding.OnboardingPreferences(this)
        val userProfileDao = (application as ComaiApplication).database.userProfileDao()
        val onboardingViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return com.comai.ui.screens.onboarding.OnboardingViewModel(onboardingPreferences, userProfileDao) as T
            }
        })[com.comai.ui.screens.onboarding.OnboardingViewModel::class.java]

        val homeViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return HomeViewModel(onboardingPreferences) as T
            }
        })[HomeViewModel::class.java]

        val startDestination = if (onboardingPreferences.isOnboardingCompleted()) {
            com.comai.ui.navigation.Routes.HOME
        } else {
            com.comai.ui.navigation.Routes.ONBOARDING
        }

        setContent {
            ComaiTheme {
                ComaiNavGraph(
                    startDestination = startDestination,
                    homeViewModel = homeViewModel,
                    chatViewModel = chatViewModel,
                    audioViewModel = audioViewModel,
                    dashboardViewModel = dashboardViewModel,
                    memoryViewModel = memoryViewModel,
                    capabilityViewModel = capabilityViewModel,
                    onboardingViewModel = onboardingViewModel,
                    voiceManager = voiceManager,
                    onLanguageChanged = { lang ->
                        voiceManager.setLanguage(lang)
                        ttsManager.setLanguage(lang.ttsLocale)
                        chatViewModel.setLanguage(lang)
                        languagePrefs.setLanguage(lang)
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleEvaluationIntent(intent)
    }

    override fun onDestroy() {
        voiceInteractionManager?.cancel()
        super.onDestroy()
    }

    private fun handleEvaluationIntent(intent: Intent?) {
        if (intent == null) return

        if (intent.hasExtra("extra_chat_message")) {
            val message = intent.getStringExtra("extra_chat_message")
            if (!message.isNullOrBlank()) {
                chatViewModel?.sendMessage(message)
            }
        }

        if (intent.hasExtra("extra_delete_memory_id")) {
            val idToDelete = intent.getLongExtra("extra_delete_memory_id", -1L)
            if (idToDelete != -1L) {
                lifecycleScope.launch {
                    (application as ComaiApplication).memoryRepository.deleteMemory(idToDelete)
                }
            }
        }

        if (intent.action == com.comai.contextengine.service.ContextForegroundService.ACTION_TRIGGER_EVALUATION) {
            val serviceIntent = Intent(this, com.comai.contextengine.service.ContextForegroundService::class.java).apply {
                action = com.comai.contextengine.service.ContextForegroundService.ACTION_TRIGGER_EVALUATION
                putExtras(intent)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }
    }
}
