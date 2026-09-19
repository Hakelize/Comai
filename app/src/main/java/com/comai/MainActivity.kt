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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
        com.comai.voice.AppLocaleManager.applyLocale(this, savedLanguage)

        // Factory to supply custom dependencies to ViewModels
        val chatViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ChatViewModel(aiEngine, ttsManager, contextBridge, memoryRepository, textNormalizer, app.memoryContextProvider, applicationContext) as T
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

        // Personal Schedule repository (single source of truth for Schedule & Calendar)
        val personalPlanRepository = com.comai.data.repository.PersonalPlanRepository.getInstance(this)
        val nearbyContextProvider = com.comai.nearby.NearbyContextProvider(this)

        val memoryViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return MemoryViewModel(
                    memoryRepository = memoryRepository,
                    personalPlanRepository = personalPlanRepository,
                    nearbyContextProvider = nearbyContextProvider
                ) as T
            }
        })[MemoryViewModel::class.java]

        val capabilityAuditor = CapabilityAuditor(this)
        val capabilityViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return CapabilityViewModel(capabilityAuditor) as T
            }
        })[CapabilityViewModel::class.java]

        // Onboarding and User Profile preferences
        val onboardingPreferences = com.comai.ui.screens.onboarding.OnboardingPreferences(this)
        val userProfileDao = (application as ComaiApplication).database.userProfileDao()
        val onboardingViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return com.comai.ui.screens.onboarding.OnboardingViewModel(
                    onboardingPreferences,
                    userProfileDao,
                    memoryRepository
                ) as T
            }
        })[com.comai.ui.screens.onboarding.OnboardingViewModel::class.java]

        val homeViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return HomeViewModel(onboardingPreferences) as T
            }
        })[HomeViewModel::class.java]

        // Native push-to-talk voice manager (retained across configuration changes / rotations in HomeViewModel)
        val voiceManager = homeViewModel.voiceManager ?: VoiceInteractionManager(
            context = applicationContext,
            ttsManager = ttsManager
        ).also {
            homeViewModel.voiceManager = it
        }

        voiceManager.setOnSpeechRecognizedListener { recognizedText ->
            chatViewModel.sendMessage(recognizedText)
        }
        voiceManager.onPermissionRequired = {
            requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        // Restore saved language into voiceManager and ttsManager
        voiceManager.setLanguage(savedLanguage)
        ttsManager.setLanguage(savedLanguage.ttsLocale)

        this.chatViewModel = chatViewModel
        this.voiceInteractionManager = voiceManager

        handleEvaluationIntent(intent)

        // Personal Schedule (single source of truth)
        val personalScheduleViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return com.comai.ui.screens.schedule.PersonalScheduleViewModel(personalPlanRepository) as T
            }
        })[com.comai.ui.screens.schedule.PersonalScheduleViewModel::class.java]

        val isFromReminder = intent.getBooleanExtra("extra_opened_from_reminder", false)
        if (isFromReminder) {
            com.comai.scheduling.AlarmAudioPlayer.stop()
        }

        val startDestination = if (onboardingPreferences.isOnboardingCompleted()) {
            if (isFromReminder) com.comai.ui.navigation.Routes.CALENDAR else com.comai.ui.navigation.Routes.HOME
        } else {
            com.comai.ui.navigation.Routes.ONBOARDING
        }

        setContent {
            ComaiTheme {
                var currentAppLanguage by androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf(savedLanguage)
                }

                val locale = androidx.compose.runtime.remember(currentAppLanguage) {
                    java.util.Locale(currentAppLanguage.uiLocaleTag)
                }
                val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                val localizedConfiguration = androidx.compose.runtime.remember(currentAppLanguage, configuration) {
                    android.content.res.Configuration(configuration).apply {
                        setLocale(locale)
                    }
                }
                val baseActivity = this@MainActivity
                val localizedContext = androidx.compose.runtime.remember(currentAppLanguage, baseActivity) {
                    object : android.content.ContextWrapper(baseActivity.createConfigurationContext(localizedConfiguration)),
                        androidx.activity.result.ActivityResultRegistryOwner {
                        override val activityResultRegistry: androidx.activity.result.ActivityResultRegistry
                            get() = baseActivity.activityResultRegistry
                    }
                }

                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalConfiguration provides localizedConfiguration,
                    androidx.compose.ui.platform.LocalContext provides localizedContext,
                    androidx.activity.compose.LocalActivityResultRegistryOwner provides baseActivity
                ) {
                    ComaiNavGraph(
                        startDestination = startDestination,
                        homeViewModel = homeViewModel,
                        chatViewModel = chatViewModel,
                        audioViewModel = audioViewModel,
                        dashboardViewModel = dashboardViewModel,
                        memoryViewModel = memoryViewModel,
                        capabilityViewModel = capabilityViewModel,
                        personalScheduleViewModel = personalScheduleViewModel,
                        onboardingViewModel = onboardingViewModel,
                        voiceManager = voiceManager,
                        onLanguageChanged = { lang ->
                            currentAppLanguage = lang
                            com.comai.voice.AppLocaleManager.applyLocale(this@MainActivity, lang)
                            voiceManager.setLanguage(lang)
                            ttsManager.setLanguage(lang.ttsLocale)
                            chatViewModel.setLanguage(lang)
                            languagePrefs.setLanguage(lang)
                            val currentProf = onboardingPreferences.getProfile()
                            onboardingPreferences.saveProfile(currentProf.copy(preferredLanguage = lang))
                            homeViewModel.refreshState()
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("extra_opened_from_reminder", false)) {
            com.comai.scheduling.AlarmAudioPlayer.stop()
        }
        handleEvaluationIntent(intent)
    }

    override fun onDestroy() {
        if (!isChangingConfigurations) {
            voiceInteractionManager?.cancel()
        }
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
