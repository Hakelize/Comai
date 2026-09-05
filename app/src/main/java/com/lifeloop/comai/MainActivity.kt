package com.lifeloop.comai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.lifeloop.comai.ui.navigation.CamoiNavGraph
import com.lifeloop.comai.ui.screens.audio.AudioViewModel
import com.lifeloop.comai.ui.screens.chat.ChatViewModel
import com.lifeloop.comai.ui.screens.dashboard.DashboardViewModel
import com.lifeloop.comai.ui.theme.CamoiTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as CamoiApplication
        val aiEngine = app.aiEngine
        val ttsManager = app.ttsManager

        // Factory to supply custom dependencies to ViewModels
        val chatViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ChatViewModel(aiEngine, ttsManager) as T
            }
        })[ChatViewModel::class.java]

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

        setContent {
            CamoiTheme {
                CamoiNavGraph(
                    chatViewModel = chatViewModel,
                    audioViewModel = audioViewModel,
                    dashboardViewModel = dashboardViewModel
                )
            }
        }
    }
}
