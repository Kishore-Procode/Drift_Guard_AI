package com.driftdetector.desktop.core.ai.runanywhere

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Owns RunAnywhere SDK lifecycle for the desktop app.
 */
class RunAnywhereRuntime {
    data class State(
        val isInitialized: Boolean = false,
        val isReady: Boolean = false,
        val isInitializing: Boolean = false,
        val statusMessage: String = "Local AI is not initialized",
        val retryCount: Int = 0,
        val availableMemoryMb: Long = 0,
        val isLowMemory: Boolean = false,
        val lastError: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    private val initMutex = Mutex()
    private var shutdownHookInstalled = false

    init {
        installShutdownHook()
    }

    suspend fun initializeIfNeeded(maxRetries: Int = 2): Boolean {
        if (_state.value.isReady) return true

        return initMutex.withLock {
            if (_state.value.isReady) return@withLock true
            initializeWithRetry(maxRetries)
        }
    }

    suspend fun reload(maxRetries: Int = 2): Boolean {
        shutdown()
        return initializeIfNeeded(maxRetries)
    }

    suspend fun shutdown() {
        withContext(Dispatchers.IO) {
            _state.value = snapshot(
                isInitialized = false,
                isReady = false,
                isInitializing = false,
                statusMessage = "Local AI has been reset (SDK-free mode)",
                retryCount = 0,
                lastError = null,
            )
        }
    }

    private suspend fun initializeWithRetry(maxRetries: Int): Boolean {
        var attempt = 0
        var lastError: String? = null
        while (attempt <= maxRetries) {
            attempt++
            if (initializeOnce(attempt - 1)) return true
            lastError = _state.value.lastError
        }

        _state.value = snapshot(
            isInitialized = false,
            isReady = false,
            isInitializing = false,
            statusMessage = "Local AI initialization failed after retries",
            retryCount = maxRetries,
            lastError = lastError,
        )
        return false
    }

    private suspend fun initializeOnce(retryCount: Int): Boolean {
        withContext(Dispatchers.IO) {
            _state.value = snapshot(
                isInitialized = false,
                isReady = false,
                isInitializing = true,
                statusMessage = if (retryCount == 0) "Initializing local AI runtime..." else "Retrying local AI runtime init (${retryCount + 1})...",
                retryCount = retryCount,
                lastError = null,
            )
            try {
                // SDK-free local fallback runtime.
                _state.value = snapshot(
                    isInitialized = true,
                    isReady = true,
                    isInitializing = false,
                    statusMessage = "Local AI ready (SDK-free mode)",
                    retryCount = retryCount,
                    lastError = null,
                )
            } catch (t: Throwable) {
                _state.value = snapshot(
                    isInitialized = false,
                    isReady = false,
                    isInitializing = false,
                    statusMessage = "Local AI initialization failed",
                    retryCount = retryCount,
                    lastError = t.message ?: t::class.simpleName,
                )
            }
        }
        return _state.value.isReady
    }

    private fun snapshot(
        isInitialized: Boolean,
        isReady: Boolean,
        isInitializing: Boolean,
        statusMessage: String,
        retryCount: Int,
        lastError: String?,
    ): State {
        val availableMemoryMb = getAvailableMemoryMb()
        return State(
            isInitialized = isInitialized,
            isReady = isReady,
            isInitializing = isInitializing,
            statusMessage = statusMessage,
            retryCount = retryCount,
            availableMemoryMb = availableMemoryMb,
            isLowMemory = availableMemoryMb in 1..1024,
            lastError = lastError,
        )
    }

    private fun getAvailableMemoryMb(): Long {
        val runtime = Runtime.getRuntime()
        val freeWithinHeap = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())
        return max(0L, freeWithinHeap / (1024L * 1024L))
    }

    private fun installShutdownHook() {
        if (shutdownHookInstalled) return
        runCatching {
            Runtime.getRuntime().addShutdownHook(
                Thread {
                    runCatching {
                        kotlinx.coroutines.runBlocking { shutdown() }
                    }
                },
            )
            shutdownHookInstalled = true
        }
    }
}
