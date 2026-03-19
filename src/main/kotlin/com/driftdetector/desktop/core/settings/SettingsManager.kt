package com.driftdetector.desktop.core.settings

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File

/**
 * Persists application settings to ~/.driftguard/settings.json.
 */
class SettingsManager {

    data class AppSettings(
        var darkTheme: Boolean = false,
        var backendUrl: String = "http://localhost:3000/api",
        var wsUrl: String = "ws://localhost:8080",
        var psiThreshold: Double = 0.35,
        var ksThreshold: Double = 0.10,
        var autoPatching: Boolean = true,
        var aggressiveness: String = "normal",  // "conservative", "normal", "aggressive", "ultra"
        var exportDirectory: String = ""
    )

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val settingsDir = File(System.getProperty("user.home"), ".driftguard")
    private val settingsFile = File(settingsDir, "settings.json")

    private var _settings: AppSettings = load()
    val settings: AppSettings get() = _settings

    private fun load(): AppSettings {
        return try {
            if (settingsFile.exists()) {
                gson.fromJson(settingsFile.readText(), AppSettings::class.java) ?: AppSettings()
            } else {
                AppSettings()
            }
        } catch (e: Exception) {
            AppSettings()
        }
    }

    fun save() {
        try {
            settingsDir.mkdirs()
            settingsFile.writeText(gson.toJson(_settings))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun update(block: AppSettings.() -> Unit) {
        block(_settings)
        save()
    }

    fun reset() {
        _settings = AppSettings()
        save()
    }
}
