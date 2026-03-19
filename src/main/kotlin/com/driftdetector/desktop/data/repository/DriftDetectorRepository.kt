package com.driftdetector.desktop.data.repository

import com.driftdetector.desktop.domain.model.*
import com.driftdetector.desktop.data.remote.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Main Repository class that coordinates drift detection, patch generation, and model management
 */
class DriftDetectorRepository(private val apiClient: ApiClient) {

    // ==================== DRIFT DETECTION ====================

    suspend fun detectDrift(
        modelId: String,
        dataBytes: ByteArray,
        fileName: String
    ): Result<DriftResult> = withContext(Dispatchers.IO) {
        try {
            val result = apiClient.detectDrift(modelId, dataBytes, fileName).getOrNull()
                ?: throw Exception("Failed to detect drift")
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun detectDriftBatch(
        modelId: String,
        dataFiles: List<Pair<String, ByteArray>>
    ): Result<List<DriftResult>> = withContext(Dispatchers.IO) {
        apiClient.detectDriftBatch(modelId, dataFiles)
    }

    suspend fun getDriftHistory(modelId: String, limit: Int = 100): Result<List<DriftResult>> =
        withContext(Dispatchers.IO) {
            apiClient.getDriftHistory(modelId, limit)
        }

    // ==================== PATCH GENERATION & APPLICATION ====================

    suspend fun generatePatches(
        driftResult: DriftResult,
        aggressiveness: String = "normal"
    ): Result<List<Patch>> = withContext(Dispatchers.IO) {
        try {
            val patches = apiClient.generatePatches(driftResult, aggressiveness).getOrNull()
                ?: throw Exception("Failed to generate patches")
            Result.success(patches)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun validatePatch(patch: Patch): Result<ValidationResult> = withContext(Dispatchers.IO) {
        apiClient.validatePatch(patch)
    }

    suspend fun applyPatch(patchId: String, modelId: String): Result<Patch> = 
        withContext(Dispatchers.IO) {
            apiClient.applyPatch(patchId, modelId)
        }

    suspend fun rollbackPatch(patchId: String, modelId: String): Result<Patch> = 
        withContext(Dispatchers.IO) {
            apiClient.rollbackPatch(patchId, modelId)
        }

    suspend fun getPatchHistory(modelId: String): Result<List<Patch>> = withContext(Dispatchers.IO) {
        apiClient.getPatchHistory(modelId)
    }

    // ==================== MODEL MANAGEMENT ====================

    suspend fun uploadModel(
        modelFile: ByteArray,
        fileName: String,
        modelType: String
    ): Result<MLModel> = withContext(Dispatchers.IO) {
        try {
            val model = apiClient.uploadModel(modelFile, fileName, modelType).getOrNull()
                ?: throw Exception("Failed to upload model")
            Result.success(model)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getModels(): Result<List<MLModel>> = withContext(Dispatchers.IO) {
        apiClient.getModels()
    }

    suspend fun getModelMetadata(modelId: String): Result<MLModel> = withContext(Dispatchers.IO) {
        apiClient.getModelMetadata(modelId)
    }

    suspend fun setActiveModel(modelId: String): Result<MLModel> = withContext(Dispatchers.IO) {
        apiClient.setActiveModel(modelId)
    }

    suspend fun deleteModel(modelId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        apiClient.deleteModel(modelId)
    }

    // ==================== DATA UPLOAD & MANAGEMENT ====================

    suspend fun uploadDataFile(
        dataFile: ByteArray,
        fileName: String
    ): Result<DataFile> = withContext(Dispatchers.IO) {
        apiClient.uploadDataFile(dataFile, fileName)
    }

    suspend fun getRecentFiles(): Result<List<DataFile>> = withContext(Dispatchers.IO) {
        apiClient.getRecentFiles()
    }

    // ==================== ANALYTICS ====================

    suspend fun getAnalytics(modelId: String): Result<ModelAnalytics> = 
        withContext(Dispatchers.IO) {
            apiClient.getAnalytics(modelId)
        }

    suspend fun getAnalyticsRange(
        modelId: String,
        startDate: String,
        endDate: String
    ): Result<ModelAnalytics> = withContext(Dispatchers.IO) {
        apiClient.getAnalyticsRange(modelId, startDate, endDate)
    }

    // ==================== EXPORT ====================

    suspend fun exportPatchesAsJson(patchIds: List<String>): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            apiClient.exportPatchesAsJson(patchIds)
        }

    suspend fun exportPatchesAsCsv(patchIds: List<String>): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            apiClient.exportPatchesAsCsv(patchIds)
        }

    suspend fun exportDriftResultsAsJson(driftIds: List<String>): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            apiClient.exportDriftResultsAsJson(driftIds)
        }

    // ==================== CONNECTION MANAGEMENT ====================

    suspend fun testBackendConnection(): Boolean = withContext(Dispatchers.IO) {
        apiClient.testConnection()
    }

    suspend fun getBackendHealth(): Result<Unit> = withContext(Dispatchers.IO) {
        apiClient.healthCheck()
    }
}
