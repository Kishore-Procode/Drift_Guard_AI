package com.driftdetector.desktop.data.model

import kotlinx.serialization.Serializable

// Upload & Dataset Models
@Serializable
data class DatasetFile(
    val id: String = "",
    val name: String,
    val type: String, // "csv" or "json"
    val filePath: String,
    val uploadedAt: String = "",
    val rowCount: Int = 0
)

@Serializable
data class UploadResponse(
    val success: Boolean,
    val datasetId: String,
    val message: String,
    val rowCount: Int = 0
)

// Drift Detection Models
@Serializable
data class DriftDetectionRequest(
    val baselineDatasetId: String,
    val newDatasetId: String,
    val modelId: String = ""
)

@Serializable
data class FeatureDrift(
    val name: String,
    val type: String, // "numeric" or "categorical"
    val driftScore: Double,
    val driftDetected: Boolean,
    val details: Map<String, String> = emptyMap()
)

@Serializable
data class DriftDetectionResult(
    val id: String,
    val baselineDatasetId: String,
    val newDatasetId: String,
    val overallDriftScore: Double,
    val driftDetected: Boolean,
    val features: List<FeatureDrift>,
    val timestamp: String,
    val status: String = "completed"
)

// Patch Generation Models
@Serializable
data class PatchGenerationRequest(
    val driftDetectionId: String,
    val strategy: String = "auto"
)

@Serializable
data class PatchSuggestion(
    val id: String,
    val strategy: String,
    val description: String,
    val expectedImprovement: Double,
    val affectedFeatures: List<String>
)

@Serializable
data class PatchGenerationResult(
    val id: String,
    val driftDetectionId: String,
    val suggestions: List<PatchSuggestion>,
    val status: String = "completed",
    val timestamp: String
)

// Model Management Models
@Serializable
data class ModelInfo(
    val id: String,
    val name: String,
    val type: String,
    val accuracy: Double,
    val createdAt: String,
    val status: String = "active"
)

@Serializable
data class ModelUploadRequest(
    val name: String,
    val type: String,
    val modelData: String // base64 encoded
)

// Backend Response Models
@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val message: String = "",
    val error: String? = null
)

@Serializable
data class ApiError(
    val code: String,
    val message: String,
    val details: Map<String, String> = emptyMap()
)
