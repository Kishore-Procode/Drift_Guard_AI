package com.driftdetector.desktop.core.ai.runanywhere

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File

/**
 * Persists local AI state under ~/.driftguard/runanywhere_state.json.
 */
class RunAnywhereStateStore {
    data class ChatRecord(
        val text: String,
        val isUser: Boolean,
        val timestamp: Long,
    )

    data class State(
        var selectedModelId: String? = null,
        var maxTokens: Int = 320,
        var temperature: Float = 0.25f,
        var chatHistory: List<ChatRecord> = emptyList(),
    )

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val baseDir = File(System.getProperty("user.home"), ".driftguard")
    private val stateFile = File(baseDir, "runanywhere_state.json")

    fun load(): State {
        return try {
            if (stateFile.exists()) {
                gson.fromJson(stateFile.readText(), State::class.java) ?: State()
            } else {
                State()
            }
        } catch (_: Exception) {
            State()
        }
    }

    fun save(state: State) {
        try {
            baseDir.mkdirs()
            stateFile.writeText(gson.toJson(state))
        } catch (_: Exception) {
            // Keep runtime resilient even if persistence fails.
        }
    }
}
