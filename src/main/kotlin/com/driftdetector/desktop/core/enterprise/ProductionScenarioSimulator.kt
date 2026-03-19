package com.driftdetector.desktop.core.enterprise

import com.driftdetector.desktop.core.drift.DriftDetector
import com.driftdetector.desktop.domain.model.DriftResult

/**
 * Desktop "What-If Lab" engine for synthetic drift injection.
 */
class ProductionScenarioSimulator(
    private val detector: DriftDetector = DriftDetector()
) {

    data class Scenario(
        val name: String,
        val featureShifts: Map<String, Double> = emptyMap(),
        val failedSensors: Set<String> = emptySet(),
        val scaleBySigma: Map<String, Double> = emptyMap()
    )

    data class ScenarioResult(
        val scenario: Scenario,
        val beforeScore: Double,
        val afterScore: Double,
        val detected: Boolean,
        val driftType: String
    )

    suspend fun runScenario(
        modelId: String,
        referenceData: List<FloatArray>,
        currentData: List<FloatArray>,
        featureNames: List<String>,
        scenario: Scenario
    ): ScenarioResult {
        val before = detector.detectDrift(modelId, referenceData, currentData, featureNames)
        val transformed = applyScenario(currentData, featureNames, scenario)
        val after = detector.detectDrift(modelId, referenceData, transformed, featureNames)

        return ScenarioResult(
            scenario = scenario,
            beforeScore = before.overallDriftScore,
            afterScore = after.overallDriftScore,
            detected = after.isDriftDetected,
            driftType = after.driftType.name
        )
    }

    private fun applyScenario(
        data: List<FloatArray>,
        featureNames: List<String>,
        scenario: Scenario
    ): List<FloatArray> {
        if (data.isEmpty()) return data

        val stdByFeature = featureNames.indices.associateWith { idx ->
            val mean = data.map { it[idx] }.average()
            val variance = data.map { (it[idx] - mean) * (it[idx] - mean) }.average()
            kotlin.math.sqrt(variance).coerceAtLeast(0.0001)
        }

        return data.map { row ->
            val out = row.copyOf()
            featureNames.forEachIndexed { idx, name ->
                if (name in scenario.failedSensors) {
                    out[idx] = 0f
                    return@forEachIndexed
                }
                val absoluteShift = scenario.featureShifts[name] ?: 0.0
                val sigmaShift = (scenario.scaleBySigma[name] ?: 0.0) * (stdByFeature[idx] ?: 0.0)
                out[idx] = (out[idx] + absoluteShift + sigmaShift).toFloat()
            }
            out
        }
    }
}
