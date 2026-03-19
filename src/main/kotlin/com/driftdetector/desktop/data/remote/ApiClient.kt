package com.driftdetector.desktop.data.remote

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import com.driftdetector.desktop.domain.model.*

/**
 * API client using Ktor for communicating with the Node.js backend
 */
class ApiClient(
    private val baseUrl: String = "http://localhost:3000/api"
) {
    private val client = HttpClient(Java) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        install(WebSockets)
        install(HttpTimeout) {
            requestTimeoutMillis = 30000
            connectTimeoutMillis = 10000
            socketTimeoutMillis = 30000
        }
    }

    // ==================== DRIFT DETECTION ====================

    suspend fun detectDrift(
        modelId: String,
        dataBytes: ByteArray,
        fileName: String
    ): Result<DriftResult> = runCatching {
        val response: DriftResult = client.post("$baseUrl/drift/detect") {
            setBody(mapOf(
                "modelId" to modelId,
                "fileName" to fileName
            ))
        }.body()
        response
    }

    suspend fun detectDriftBatch(
        modelId: String,
        dataFiles: List<Pair<String, ByteArray>>
    ): Result<List<DriftResult>> = runCatching {
        val response: List<DriftResult> = client.post("$baseUrl/drift/detect-batch") {
            setBody(mapOf(
                "modelId" to modelId,
                "files" to dataFiles.map { it.first }
            ))
        }.body()
        response
    }

    suspend fun getDriftHistory(modelId: String, limit: Int = 100): Result<List<DriftResult>> = 
        runCatching {
            client.get("$baseUrl/drift/history") {
                parameter("modelId", modelId)
                parameter("limit", limit)
            }.body()
        }

    // ==================== PATCH GENERATION ====================

    suspend fun generatePatches(driftResult: DriftResult, aggressiveness: String = "normal"): 
        Result<List<Patch>> = runCatching {
        client.post("$baseUrl/patch/generate") {
            setBody(mapOf(
                "driftResult" to driftResult,
                "aggressiveness" to aggressiveness
            ))
        }.body()
    }

    suspend fun validatePatch(patch: Patch): Result<ValidationResult> = runCatching {
        client.post("$baseUrl/patch/validate") {
            setBody(patch)
        }.body()
    }

    suspend fun applyPatch(patchId: String, modelId: String): Result<Patch> = runCatching {
        client.post("$baseUrl/patch/apply") {
            setBody(mapOf(
                "patchId" to patchId,
                "modelId" to modelId
            ))
        }.body()
    }

    suspend fun rollbackPatch(patchId: String, modelId: String): Result<Patch> = runCatching {
        client.post("$baseUrl/patch/rollback") {
            setBody(mapOf(
                "patchId" to patchId,
                "modelId" to modelId
            ))
        }.body()
    }

    suspend fun getPatchHistory(modelId: String): Result<List<Patch>> = runCatching {
        client.get("$baseUrl/patch/history") {
            parameter("modelId", modelId)
        }.body()
    }

    // ==================== MODEL MANAGEMENT ====================

    suspend fun uploadModel(
        modelFile: ByteArray,
        fileName: String,
        modelType: String
    ): Result<MLModel> = runCatching {
        client.post("$baseUrl/model/upload") {
            setBody(mapOf(
                "fileName" to fileName,
                "modelType" to modelType
            ))
        }.body()
    }

    suspend fun getModels(): Result<List<MLModel>> = runCatching {
        client.get("$baseUrl/model/list").body()
    }

    suspend fun getModelMetadata(modelId: String): Result<MLModel> = runCatching {
        client.get("$baseUrl/model/$modelId").body()
    }

    suspend fun setActiveModel(modelId: String): Result<MLModel> = runCatching {
        client.post("$baseUrl/model/activate") {
            parameter("modelId", modelId)
        }.body()
    }

    suspend fun deleteModel(modelId: String): Result<Boolean> = runCatching {
        client.delete("$baseUrl/model/$modelId").status.isSuccess()
    }

    // ==================== DATA UPLOAD ====================

    suspend fun uploadDataFile(
        dataFile: ByteArray,
        fileName: String
    ): Result<DataFile> = runCatching {
        client.post("$baseUrl/data/upload") {
            setBody(mapOf("fileName" to fileName))
        }.body()
    }

    suspend fun getRecentFiles(): Result<List<DataFile>> = runCatching {
        client.get("$baseUrl/data/recent-files").body()
    }

    // ==================== ANALYTICS ====================

    suspend fun getAnalytics(modelId: String): Result<ModelAnalytics> = runCatching {
        client.get("$baseUrl/analytics") {
            parameter("modelId", modelId)
        }.body()
    }

    suspend fun getAnalyticsRange(
        modelId: String,
        startDate: String,
        endDate: String
    ): Result<ModelAnalytics> = runCatching {
        client.get("$baseUrl/analytics/range") {
            parameter("modelId", modelId)
            parameter("startDate", startDate)
            parameter("endDate", endDate)
        }.body()
    }

    // ==================== EXPORT ====================

    suspend fun exportPatchesAsJson(patchIds: List<String>): Result<ByteArray> = runCatching {
        client.post("$baseUrl/export/patches-json") {
            setBody(mapOf("patchIds" to patchIds))
        }.body()
    }

    suspend fun exportPatchesAsCsv(patchIds: List<String>): Result<ByteArray> = runCatching {
        client.post("$baseUrl/export/patches-csv") {
            setBody(mapOf("patchIds" to patchIds))
        }.body()
    }

    suspend fun exportDriftResultsAsJson(driftIds: List<String>): Result<ByteArray> = runCatching {
        client.post("$baseUrl/export/drift-json") {
            setBody(mapOf("driftIds" to driftIds))
        }.body()
    }

    // ==================== HEALTH CHECK ====================

    suspend fun healthCheck(): Result<Unit> = runCatching {
        val response = client.get("$baseUrl/health")
        if (!response.status.isSuccess()) {
            throw Exception("Backend health check failed: ${response.status}")
        }
    }

    suspend fun testConnection(): Boolean {
        return healthCheck().isSuccess
    }

    fun close() {
        client.close()
    }
}
