package com.driftdetector.desktop.core.realtime

import io.ktor.client.*
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * WebSocket monitor for real-time drift alerts and telemetry.
 */
class WebSocketMonitor(private val wsUrl: String = "ws://localhost:8080") {

    private val client = HttpClient(Java) { install(WebSockets) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState = _connectionState.asStateFlow()

    private val _alerts = MutableSharedFlow<DriftAlert>(replay = 10)
    val alerts = _alerts.asSharedFlow()

    private val _telemetry = MutableSharedFlow<TelemetryEvent>(replay = 5)
    val telemetry = _telemetry.asSharedFlow()

    private var reconnectJob: Job? = null
    private var wsSession: DefaultClientWebSocketSession? = null

    enum class ConnectionState { CONNECTED, CONNECTING, DISCONNECTED, ERROR }

    data class DriftAlert(
        val modelId: String = "",
        val severity: String = "low",
        val driftScore: Double = 0.0,
        val message: String = "",
        val timestamp: Long = System.currentTimeMillis()
    )

    data class TelemetryEvent(
        val modelId: String = "",
        val prediction: Double = 0.0,
        val confidence: Double = 0.0,
        val latency: Int = 0,
        val timestamp: Long = System.currentTimeMillis()
    )

    fun connect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            var retryDelay = 1000L
            while (isActive) {
                _connectionState.value = ConnectionState.CONNECTING
                try {
                    client.webSocket(wsUrl) {
                        wsSession = this
                        _connectionState.value = ConnectionState.CONNECTED
                        retryDelay = 1000L // Reset on successful connect

                        // Send auth
                        send(Frame.Text("""{"type":"auth","token":"desktop-client"}"""))

                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                handleMessage(frame.readText())
                            }
                        }
                    }
                } catch (e: Exception) {
                    _connectionState.value = ConnectionState.ERROR
                }

                _connectionState.value = ConnectionState.DISCONNECTED
                wsSession = null
                delay(retryDelay)
                retryDelay = (retryDelay * 2).coerceAtMost(30000L) // Exponential backoff
            }
        }
    }

    fun disconnect() {
        reconnectJob?.cancel()
        scope.launch {
            try { wsSession?.close() } catch (_: Exception) {}
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    private suspend fun handleMessage(text: String) {
        try {
            val obj = json.parseToJsonElement(text).jsonObject
            val type = obj["type"]?.jsonPrimitive?.content ?: return

            when (type) {
                "drift_alert" -> {
                    val data = obj["data"]?.jsonObject ?: return
                    _alerts.emit(DriftAlert(
                        modelId = data["modelId"]?.jsonPrimitive?.content ?: "",
                        severity = data["severity"]?.jsonPrimitive?.content ?: "low",
                        driftScore = data["driftScore"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
                        message = data["message"]?.jsonPrimitive?.content ?: ""
                    ))
                }
                "telemetry" -> {
                    val data = obj["data"]?.jsonObject ?: return
                    _telemetry.emit(TelemetryEvent(
                        modelId = data["modelId"]?.jsonPrimitive?.content ?: "",
                        prediction = data["prediction"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
                        confidence = data["confidence"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
                        latency = data["latency"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    ))
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Check if the backend is reachable (simple socket connectivity check).
     */
    suspend fun checkHealth(): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = java.net.URI(wsUrl.replace("ws://", "http://").replace("wss://", "https://"))
            val port = if (url.port > 0) url.port else 80
            val socket = java.net.Socket()
            socket.connect(java.net.InetSocketAddress(url.host, port), 3000)
            socket.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun close() {
        disconnect()
        scope.cancel()
        client.close()
    }
}
