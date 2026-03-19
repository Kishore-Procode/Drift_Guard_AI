package com.driftdetector.desktop.core.enterprise

import com.driftdetector.desktop.core.drift.DriftDetector
import com.driftdetector.desktop.core.ml.Predictor

/**
 * Compare model versions on the same data for safe rollout/rollback decisions.
 */
class MultiVersionModelComparator(
    private val detector: DriftDetector = DriftDetector()
) {

    data class VersionedModel(
        val version: String,
        val predictor: Predictor
    )

    data class ComparisonResult(
        val versionA: String,
        val versionB: String,
        val driftA: Double,
        val driftB: Double,
        val predictionDisagreementPercent: Double,
        val recommendedVersion: String,
        val rollbackSuggested: Boolean
    )

    suspend fun compare(
        referenceData: List<FloatArray>,
        currentData: List<FloatArray>,
        featureNames: List<String>,
        modelA: VersionedModel,
        modelB: VersionedModel
    ): ComparisonResult {
        val driftResultA = detector.detectDrift("model-${modelA.version}", referenceData, currentData, featureNames)
        val driftResultB = detector.detectDrift("model-${modelB.version}", referenceData, currentData, featureNames)

        val disagreement = predictionDisagreement(currentData, modelA.predictor, modelB.predictor)
        val recommended = if (driftResultA.overallDriftScore <= driftResultB.overallDriftScore) modelA.version else modelB.version
        val rollback = disagreement > 20.0

        return ComparisonResult(
            versionA = modelA.version,
            versionB = modelB.version,
            driftA = driftResultA.overallDriftScore,
            driftB = driftResultB.overallDriftScore,
            predictionDisagreementPercent = disagreement,
            recommendedVersion = recommended,
            rollbackSuggested = rollback
        )
    }

    private fun predictionDisagreement(
        rows: List<FloatArray>,
        predictorA: Predictor,
        predictorB: Predictor
    ): Double {
        if (rows.isEmpty()) return 0.0

        var diffCount = 0
        rows.forEach { row ->
            val a = predictorA.predict(row)
            val b = predictorB.predict(row)
            val aClass = if (a >= 0.5) 1 else 0
            val bClass = if (b >= 0.5) 1 else 0
            if (aClass != bClass) diffCount++
        }

        return (diffCount.toDouble() / rows.size.toDouble()) * 100.0
    }
}
