package com.driftdetector.desktop.core.analytics

import com.driftdetector.desktop.core.ml.Predictor
import kotlin.math.abs

/**
 * Lightweight local explainability engine.
 *
 * This uses perturbation-based attributions so it can run fully on desktop
 * even when full SHAP libraries are not available.
 */
class ExplainabilityEngine(
    private val predictor: Predictor
) {

    data class FeatureAttribution(
        val feature: String,
        val value: Double,
        val contribution: Double,
        val direction: String
    )

    data class SampleExplanation(
        val basePrediction: Double,
        val finalPrediction: Double,
        val confidenceDeltaPercent: Double,
        val attributions: List<FeatureAttribution>
    )

    /**
     * Explain a single sample by perturbing each feature to a reference value
     * and measuring prediction deltas.
     */
    fun explainSample(
        featureNames: List<String>,
        sample: FloatArray,
        referenceMeans: FloatArray
    ): SampleExplanation {
        require(featureNames.size == sample.size) { "Feature names and sample size mismatch" }
        require(referenceMeans.size == sample.size) { "Reference means and sample size mismatch" }

        val baseline = predictor.predict(sample)
        val attributions = mutableListOf<FeatureAttribution>()

        for (idx in sample.indices) {
            val perturbed = sample.copyOf()
            perturbed[idx] = referenceMeans[idx]
            val perturbedPred = predictor.predict(perturbed)
            val contribution = baseline - perturbedPred
            attributions += FeatureAttribution(
                feature = featureNames[idx],
                value = sample[idx].toDouble(),
                contribution = contribution,
                direction = if (contribution >= 0.0) "pushes_up" else "pushes_down"
            )
        }

        val ranked = attributions.sortedByDescending { abs(it.contribution) }
        val confidenceDelta = if (baseline == 0.0) 0.0 else (ranked.sumOf { it.contribution } / baseline) * 100.0

        return SampleExplanation(
            basePrediction = baseline,
            finalPrediction = baseline,
            confidenceDeltaPercent = confidenceDelta,
            attributions = ranked
        )
    }
}

/**
 * SHAP-style (SHapley Additive exPlanations) local feature attribution.
 * Shows EXACTLY which features pushed confidence up/down.
 * 
 * This is what separates us from Datadog/SageMaker.
 * They say "drift detected". We say "temperature +7C → -45% confidence".
 */
data class SHAPExplanation(
    val sampleId: String,
    val predictions: Map<String, Double>, // feature -> shap value
    val baselineValue: Double,             // model output baseline
    val predictedValue: Double,            // model output for this sample
    val topPositiveFeatures: List<Pair<String, Double>>, // help predictions
    val topNegativeFeatures: List<Pair<String, Double>>  // hurt predictions
)

fun explainSingleSample(
    sample: Map<String, Double>,
    referenceMean: Map<String, Double>,
    predictorFn: (Map<String, Double>) -> Double
): SHAPExplanation {
    
    val baseline = predictorFn(referenceMean)
    val prediction = predictorFn(sample)
    
    // Perturbation-based SHAP approximation
    val shaplyValues = mutableMapOf<String, Double>()
    
    sample.forEach { (feature, value) ->
        // Measure impact of this feature
        val withoutFeature = sample.toMutableMap()
        withoutFeature[feature] = referenceMean[feature] ?: value
        
        val impactWithout = predictorFn(withoutFeature)
        val shaplValue = impactWithout - baseline
        shaplyValues[feature] = shaplValue
    }
    
    val topPositive = shaplyValues
        .filter { it.value > 0 }
        .entries
        .sortedByDescending { it.value }
        .take(5)
        .map { it.key to it.value }
    
    val topNegative = shaplyValues
        .filter { it.value < 0 }
        .entries
        .sortedBy { it.value }
        .take(5)
        .map { it.key to it.value }
    
    return SHAPExplanation(
        sampleId = "sample_${System.currentTimeMillis()}",
        predictions = shaplyValues,
        baselineValue = baseline,
        predictedValue = prediction,
        topPositiveFeatures = topPositive,
        topNegativeFeatures = topNegative
    )
}
