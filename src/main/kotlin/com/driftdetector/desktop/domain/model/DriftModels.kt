package com.driftdetector.desktop.domain.model

import kotlinx.serialization.Serializable
import kotlinx.datetime.Instant

/**
 * Represent different types of drift that can be detected
 */
enum class DriftType {
    CONCEPT_DRIFT,      // Model's decision boundary has shifted
    COVARIATE_DRIFT,    // Feature distribution has changed
    PRIOR_DRIFT,        // Class distribution has changed
    NO_DRIFT           // No drift detected
}

/**
 * Represents statistical test results
 */
@Serializable
data class StatisticalTest(
    val testName: String,        // "KS Test", "PSI", etc.
    val statistic: Double,       // Test statistic value
    val pValue: Double,          // P-value from the test
    val threshold: Double,       // Threshold for significance
    val isSignificant: Boolean   // Whether significant at threshold
)

/**
 * Represents drift information for a single feature
 */
@Serializable
data class FeatureDrift(
    val featureName: String,
    val psiScore: Double,                    // Population Stability Index
    val ksStatistic: Double,                 // Kolmogorov-Smirnov test statistic
    val pValue: Double,                      // P-value from KS test
    val attributionScore: Double,            // How much this feature contributes to overall drift
    val distributionShift: DistributionShift,
    val isDrifted: Boolean
)

/**
 * Represents the change in distribution for a feature
 */
@Serializable
data class DistributionShift(
    val meanShift: Double,                   // Change in mean
    val varianceShift: Double,               // Change in variance
    val medianShift: Double,                 // Change in median
    val skewnessChange: Double,              // Change in skewness
    val kurtosisChange: Double               // Change in kurtosis
)

/**
 * Main result of drift detection analysis
 */
@Serializable
data class DriftResult(
    val id: String = "",
    val modelId: String,
    val timestamp: String = "",
    val driftType: DriftType = DriftType.NO_DRIFT,
    val overallDriftScore: Double,           // 0-1 score indicating degree of drift
    val psiThreshold: Double = 0.35,
    val ksThreshold: Double = 0.10,
    val isDriftDetected: Boolean,
    val featureDrifts: List<FeatureDrift> = emptyList(),
    val statisticalTests: List<StatisticalTest> = emptyList(),
    val recommendedPatchTypes: List<String> = emptyList(),
    val description: String = ""
)

/**
 * Feature information for a model
 */
@Serializable
data class ModelFeature(
    val name: String,
    val type: String,                        // "numerical", "categorical", "binary"
    val dtype: String,                       // "float32", "int64", etc.
    val min: Double? = null,
    val max: Double? = null,
    val mean: Double? = null,
    val std: Double? = null
)

/**
 * Metadata about an ML model
 */
@Serializable
data class MLModel(
    val id: String = "",
    val name: String,
    val type: String,                        // "classification", "regression", "clustering"
    val framework: String,                   // "tensorflow", "pytorch", "sklearn", etc.
    val version: String = "1.0.0",
    val inputShape: List<Int> = emptyList(),
    val outputShape: List<Int> = emptyList(),
    val features: List<ModelFeature> = emptyList(),
    val isActive: Boolean = false,
    val uploadedAt: String = "",
    val fileSize: Long = 0,
    val accuracy: Double? = null,            // Last known accuracy if available
    val testSetSize: Int = 0
)

/**
 * Patch types that can be generated to mitigate drift
 */
enum class PatchType {
    FEATURE_CLIPPING,       // Constrain feature values to reference range
    FEATURE_REWEIGHTING,    // Adjust feature importance weights
    THRESHOLD_TUNING,       // Recalibrate decision threshold
    NORMALIZATION_UPDATE,   // Update feature scaling/normalization
    ENSEMBLE_REWEIGHT,      // Reweight ensemble members
    CALIBRATION_ADJUST      // Adjust probability calibration
}

/**
 * Patch status tracking
 */
enum class PatchStatus {
    CREATED,                // Just generated
    VALIDATED,              // Passed validation checks
    APPLIED,                // Applied to model
    FAILED,                 // Application failed
    ROLLED_BACK             // Previously applied, now rolled back
}

/**
 * Sealed class for patch configurations
 */
sealed class PatchConfig {
    data class FeatureClipping(
        val featureName: String,
        val minValue: Double,
        val maxValue: Double
    ) : PatchConfig()

    data class FeatureReweighting(
        val featureWeights: Map<String, Double>
    ) : PatchConfig()

    data class ThresholdTuning(
        val newThreshold: Double,
        val confidence: Double
    ) : PatchConfig()

    data class NormalizationUpdate(
        val meanShift: Map<String, Double>,
        val stdShift: Map<String, Double>
    ) : PatchConfig()

    data class EnsembleReweight(
        val modelWeights: Map<String, Double>
    ) : PatchConfig()

    data class CalibrationAdjust(
        val temperatureScaling: Double,
        val calibrationCurve: List<Pair<Double, Double>>
    ) : PatchConfig()
}

/**
 * Represents a patch - a reversible mitigation strategy for drift
 */
@Serializable
data class Patch(
    val id: String = "",
    val modelId: String,
    val driftResultId: String,
    val patchType: String,                   // String version for serialization
    val status: String = "CREATED",          // String version for serialization
    val createdAt: String = "",
    val appliedAt: String? = null,
    val config: Map<String, String> = emptyMap(),
    val safetyScore: Double,                 // 0-1 score of how safe this patch is
    val effectivenessScore: Double,          // 0-1 score of expected effectiveness
    val description: String = "",
    val rollbackConfig: Map<String, String>? = null
)

/**
 * Snapshot of model/patch state at a point in time
 */
@Serializable
data class PatchSnapshot(
    val id: String = "",
    val patchId: String,
    val timestamp: String = "",
    val modelMetrics: Map<String, Double> = emptyMap(),
    val notes: String = ""
)

/**
 * Data file information for upload/analysis
 */
@Serializable
data class DataFile(
    val id: String = "",
    val fileName: String,
    val fileType: String,                    // "csv", "json", "parquet", "tsv"
    val rowCount: Int = 0,
    val colCount: Int = 0,
    val columns: List<String> = emptyList(),
    val uploadedAt: String = "",
    val fileSize: Long = 0
)

/**
 * Result of file validation
 */
@Serializable
data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Analytics/metrics for monitoring
 */
@Serializable
data class ModelAnalytics(
    val totalDriftEvents: Int = 0,
    val driftDetectionRate: Double = 0.0,   // Percentage of runs with drift
    val averageDriftScore: Double = 0.0,
    val maxDriftScore: Double = 0.0,
    val minDriftScore: Double = 0.0,
    val stdDeviation: Double = 0.0,
    val driftTypeDistribution: Map<String, Int> = emptyMap(),
    val totalPatchesGenerated: Int = 0,
    val patchSuccessRate: Double = 0.0,
    val averageSafetyScore: Double = 0.0,
    val trendAnalysis: String = "",         // "increasing", "decreasing", "stable"
    val predictedDriftScore7Days: Double = 0.0,
    val lastUpdated: String = ""
)
