package com.driftdetector.desktop

import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.driftdetector.desktop.core.settings.SettingsManager
import com.driftdetector.desktop.core.realtime.WebSocketMonitor
import com.driftdetector.desktop.data.remote.ApiClient
import com.driftdetector.desktop.data.repository.DriftDetectorRepository
import com.driftdetector.desktop.presentation.screen.MainScreen
import com.driftdetector.desktop.presentation.theme.DriftGuardTheme
import com.driftdetector.desktop.presentation.viewmodel.*

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "DriftGuardAI - ML Drift Monitoring & Patching System",
        resizable = true
    ) {
        // Settings
        val settingsManager = remember { SettingsManager() }
        val isDarkTheme = remember { mutableStateOf(settingsManager.settings.darkTheme) }

        // Backend
        val apiClient = remember { ApiClient(baseUrl = settingsManager.settings.backendUrl) }
        val repository = remember { DriftDetectorRepository(apiClient) }
        val wsMonitor = remember { WebSocketMonitor(settingsManager.settings.wsUrl) }

        // ViewModels
        val uploadViewModel = remember { UploadViewModel(repository) }
        val driftViewModel = remember { DriftDetectionViewModel(repository) }
        val patchViewModel = remember { PatchGenerationViewModel(repository) }
        val modelViewModel = remember { ModelManagementViewModel(repository) }
        val aiViewModel = remember { AIAssistantViewModel(repository) }
        val evalViewModel = remember { EvaluationViewModel(repository) }

        DriftGuardTheme(darkTheme = isDarkTheme.value) {
            Surface {
                MainScreen(
                    uploadViewModel = uploadViewModel,
                    driftViewModel = driftViewModel,
                    patchViewModel = patchViewModel,
                    modelViewModel = modelViewModel,
                    aiViewModel = aiViewModel,
                    evalViewModel = evalViewModel,
                    settingsManager = settingsManager,
                    wsMonitor = wsMonitor,
                    isDarkTheme = isDarkTheme.value,
                    onToggleTheme = {
                        isDarkTheme.value = !isDarkTheme.value
                        settingsManager.update { darkTheme = isDarkTheme.value }
                    }
                )
            }
        }
    }
}

