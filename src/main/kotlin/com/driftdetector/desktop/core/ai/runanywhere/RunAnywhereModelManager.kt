package com.driftdetector.desktop.core.ai.runanywhere

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Registers, downloads, and loads local LLM models through RunAnywhere.
 */
class RunAnywhereModelManager(
    private val runtime: RunAnywhereRuntime,
    private val stateStore: RunAnywhereStateStore = RunAnywhereStateStore(),
) {
    data class ModelInfo(
        val id: String,
        val name: String,
        val isDownloaded: Boolean,
    )

    data class CatalogSpec(
        val id: String,
        val name: String,
        val url: String,
        val approxSizeMb: Int,
        val description: String,
    )

    private val defaultCatalog = listOf(
        CatalogSpec(
            id = "smollm2-360m-instruct-q8",
            name = "SmolLM2 360M Instruct",
            url = "https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct-GGUF/resolve/main/smollm2-360m-instruct-q8_0.gguf",
            approxSizeMb = 400,
            description = "Fast local responses and low memory usage",
        ),
        CatalogSpec(
            id = "qwen2.5-0.5b-instruct-q8",
            name = "Qwen 2.5 0.5B Instruct",
            url = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q8_0.gguf",
            approxSizeMb = 520,
            description = "Balanced speed and quality for day-to-day drift analysis",
        ),
        CatalogSpec(
            id = "llama-3.2-1b-instruct-q4",
            name = "Llama 3.2 1B Instruct",
            url = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            approxSizeMb = 780,
            description = "Higher quality responses for complex troubleshooting",
        ),
    )

    private val _models = MutableStateFlow<List<ModelInfo>>(emptyList())
    val models = _models.asStateFlow()

    private val _activeModelId = MutableStateFlow<String?>(null)
    val activeModelId = _activeModelId.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress = _downloadProgress.asStateFlow()

    private val _downloadIssues = MutableStateFlow<Map<String, String>>(emptyMap())
    val downloadIssues = _downloadIssues.asStateFlow()

    private val downloadedModelIds = mutableSetOf<String>()

    init {
        _activeModelId.value = stateStore.load().selectedModelId
        _activeModelId.value?.let { downloadedModelIds.add(it) }
    }

    suspend fun refreshCatalog() {
        runtime.initializeIfNeeded()
        if (!runtime.state.value.isReady) return

        withContext(Dispatchers.IO) {
            _models.value = defaultCatalog
                .map { spec -> ModelInfo(spec.id, spec.name, downloadedModelIds.contains(spec.id)) }
                .sortedBy { it.name.lowercase() }

            autoLoadLastModelIfAvailable()
        }
    }

    private suspend fun autoLoadLastModelIfAvailable() {
        val persisted = stateStore.load().selectedModelId ?: return
        if (_activeModelId.value != null) return

        val candidate = _models.value.firstOrNull { it.id == persisted }
        if (candidate?.isDownloaded == true) {
            loadModel(persisted)
        }
    }

    fun downloadModel(scope: CoroutineScope, modelId: String, onError: (String) -> Unit) {
        scope.launch(Dispatchers.IO) {
            runtime.initializeIfNeeded()
            if (!runtime.state.value.isReady) {
                onError("Local AI runtime is not ready")
                return@launch
            }

            try {
                for (step in 1..10) {
                    delay(80)
                    _downloadProgress.value = _downloadProgress.value + (modelId to (step / 10f))
                }
                downloadedModelIds.add(modelId)
                _downloadIssues.value = _downloadIssues.value - modelId
                refreshCatalog()
            } catch (t: Throwable) {
                _downloadIssues.value = _downloadIssues.value + (modelId to (t.message ?: "Model download failed"))
                onError(t.message ?: "Model download failed")
            }
        }
    }

    suspend fun loadModel(modelId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runtime.initializeIfNeeded()
            if (!runtime.state.value.isReady) {
                return@withContext Result.failure(IllegalStateException("Local AI runtime is not ready"))
            }

            runCatching {
                if (_activeModelId.value == modelId) {
                    return@runCatching
                }

                if (!downloadedModelIds.contains(modelId)) {
                    error("Model is not downloaded yet")
                }
                _activeModelId.value = modelId
                persistState(modelId)
            }
        }

    suspend fun unloadModel(): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                _activeModelId.value = null
                persistState(null)
            }
        }

    suspend fun deleteModel(modelId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                runtime.initializeIfNeeded()
                if (!runtime.state.value.isReady) error("Local AI runtime is not ready")

                if (_activeModelId.value == modelId) {
                    _activeModelId.value = null
                    persistState(null)
                }

                downloadedModelIds.remove(modelId)
                _downloadProgress.value = _downloadProgress.value - modelId
                _downloadIssues.value = _downloadIssues.value - modelId
                refreshCatalog()
            }
        }

    suspend fun ensureModelLoaded(): Boolean {
        val current = _activeModelId.value
        if (current != null) return true

        val downloaded = _models.value.firstOrNull { it.isDownloaded }
        return if (downloaded != null) {
            loadModel(downloaded.id).isSuccess
        } else {
            false
        }
    }

    fun getCatalogSpec(modelId: String): CatalogSpec? = defaultCatalog.firstOrNull { it.id == modelId }

    private fun persistState(modelId: String?) {
        val state = stateStore.load()
        state.selectedModelId = modelId
        stateStore.save(state)
    }
}
