package com.driftdetector.desktop.core.patch

import com.driftdetector.desktop.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Intelligent patch generator that creates comprehensive patches for all drift types.
 * Ported from the Android IntelligentPatchGenerator + EnhancedPatchGenerator.
 */
class PatchGenerator {

    /**
     * Generate comprehensive patches for drift mitigation.
     */
    suspend fun generateComprehensivePatches(
        modelId: String,
        driftResult: DriftResult,
        referenceData: List<FloatArray>,
        currentData: List<FloatArray>
    ): List<Patch> = withContext(Dispatchers.Default) {
        val patches = mutableListOf<Patch>()

        val driftedFeatures = driftResult.featureDrifts.filter { it.isDrifted }
        if (driftedFeatures.isEmpty()) return@withContext patches

        // 1. Feature Clipping patch
        patches.add(createClippingPatch(modelId, driftResult, referenceData, driftedFeatures))

        // 2. Normalization Update patch
        patches.add(createNormalizationPatch(modelId, driftResult, referenceData, currentData, driftedFeatures))

        // 3. Feature Reweighting patch (for concept/prior drift)
        if (driftResult.driftType == DriftType.CONCEPT_DRIFT || driftResult.driftType == DriftType.PRIOR_DRIFT) {
            patches.add(createReweightingPatch(modelId, driftResult, driftedFeatures))
        }

        // 4. Threshold Tuning patch
        patches.add(createThresholdPatch(modelId, driftResult))

        // 5. Ultra-aggressive combined patch for critical drift
        if (driftResult.overallDriftScore > 0.5) {
            patches.add(createUltraAggressivePatch(modelId, driftResult, referenceData, driftedFeatures))
        }

        patches
    }

    private fun createClippingPatch(
        modelId: String, driftResult: DriftResult, refData: List<FloatArray>, driftedFeatures: List<FeatureDrift>
    ): Patch {
        val config = mutableMapOf<String, String>()
        driftedFeatures.forEachIndexed { i, feat ->
            val idx = driftResult.featureDrifts.indexOf(feat)
            if (idx >= 0 && refData.isNotEmpty() && idx < refData.first().size) {
                val values = refData.map { it[idx] }.sorted()
                val pctLow = if (feat.psiScore > 0.7) 0.05 else 0.01
                val pctHigh = if (feat.psiScore > 0.7) 0.95 else 0.99
                config["clip_${feat.featureName}_min"] = values[(values.size * pctLow).toInt()].toString()
                config["clip_${feat.featureName}_max"] = values[(values.size * pctHigh).toInt()].toString()
            }
        }
        val safety = (1.0 - driftResult.overallDriftScore * 0.3).coerceIn(0.5, 0.98)
        val effectiveness = (driftResult.overallDriftScore * 0.7 + 0.2).coerceIn(0.4, 0.95)
        return Patch(
            id = UUID.randomUUID().toString(), modelId = modelId, driftResultId = driftResult.id,
            patchType = "FEATURE_CLIPPING", status = "CREATED", createdAt = java.time.Instant.now().toString(),
            config = config, safetyScore = safety, effectivenessScore = effectiveness,
            description = "Clip ${driftedFeatures.size} drifted features to reference range"
        )
    }

    private fun createNormalizationPatch(
        modelId: String, driftResult: DriftResult, refData: List<FloatArray>, curData: List<FloatArray>, driftedFeatures: List<FeatureDrift>
    ): Patch {
        val config = mutableMapOf<String, String>()
        driftedFeatures.forEach { feat ->
            val idx = driftResult.featureDrifts.indexOf(feat)
            if (idx >= 0 && refData.isNotEmpty() && idx < refData.first().size) {
                val refVals = refData.map { it[idx].toDouble() }
                val curVals = curData.map { it[idx].toDouble() }
                config["norm_${feat.featureName}_ref_mean"] = refVals.average().toString()
                config["norm_${feat.featureName}_ref_std"] = calcStd(refVals).toString()
                config["norm_${feat.featureName}_cur_mean"] = curVals.average().toString()
                config["norm_${feat.featureName}_cur_std"] = calcStd(curVals).toString()
            }
        }
        val safety = 0.9
        val effectiveness = (driftResult.overallDriftScore * 0.6 + 0.3).coerceIn(0.4, 0.9)
        return Patch(
            id = UUID.randomUUID().toString(), modelId = modelId, driftResultId = driftResult.id,
            patchType = "NORMALIZATION_UPDATE", status = "CREATED", createdAt = java.time.Instant.now().toString(),
            config = config, safetyScore = safety, effectivenessScore = effectiveness,
            description = "Update normalization parameters for ${driftedFeatures.size} features"
        )
    }

    private fun createReweightingPatch(
        modelId: String, driftResult: DriftResult, driftedFeatures: List<FeatureDrift>
    ): Patch {
        val config = mutableMapOf<String, String>()
        driftedFeatures.forEach { feat ->
            val w = when {
                feat.psiScore > 0.7 -> 0.3
                feat.psiScore > 0.5 -> 0.5
                feat.psiScore > 0.3 -> 0.7
                else -> 0.9
            }
            config["weight_${feat.featureName}"] = w.toString()
        }
        return Patch(
            id = UUID.randomUUID().toString(), modelId = modelId, driftResultId = driftResult.id,
            patchType = "FEATURE_REWEIGHTING", status = "CREATED", createdAt = java.time.Instant.now().toString(),
            config = config, safetyScore = 0.85, effectivenessScore = 0.7,
            description = "Reweight ${driftedFeatures.size} drifted features"
        )
    }

    private fun createThresholdPatch(modelId: String, driftResult: DriftResult): Patch {
        val adjustment = when (driftResult.driftType) {
            DriftType.PRIOR_DRIFT -> driftResult.overallDriftScore * 0.15
            DriftType.CONCEPT_DRIFT -> driftResult.overallDriftScore * 0.10
            else -> driftResult.overallDriftScore * 0.05
        }
        val newThreshold = (0.5 + adjustment).coerceIn(0.1, 0.9)
        return Patch(
            id = UUID.randomUUID().toString(), modelId = modelId, driftResultId = driftResult.id,
            patchType = "THRESHOLD_TUNING", status = "CREATED", createdAt = java.time.Instant.now().toString(),
            config = mapOf("original_threshold" to "0.5", "new_threshold" to newThreshold.toString()),
            safetyScore = 0.95, effectivenessScore = 0.5,
            description = "Adjust decision threshold from 0.5 to ${"%.3f".format(newThreshold)}"
        )
    }

    private fun createUltraAggressivePatch(
        modelId: String, driftResult: DriftResult, refData: List<FloatArray>, driftedFeatures: List<FeatureDrift>
    ): Patch {
        val config = mutableMapOf<String, String>()
        config["mode"] = "ULTRA_AGGRESSIVE"
        config["pipeline"] = "outlier_removal,clip_to_reference_range,mean_std_alignment,quantile_mapping,weighted_rebalancing"
        config["quantile_bins"] = "100"
        config["rebalancing_bins"] = "20"
        driftedFeatures.forEach { feat ->
            val idx = driftResult.featureDrifts.indexOf(feat)
            if (idx >= 0 && refData.isNotEmpty() && idx < refData.first().size) {
                val values = refData.map { it[idx] }.sorted()
                val refVals = refData.map { it[idx].toDouble() }
                val mean = refVals.average()
                val std = calcStd(refVals).coerceAtLeast(1e-6)
                val q1 = values[(values.size * 0.25).toInt().coerceIn(0, values.lastIndex)]
                val q3 = values[(values.size * 0.75).toInt().coerceIn(0, values.lastIndex)]
                val iqr = (q3 - q1).coerceAtLeast(1e-6f)
                config["ultra_${feat.featureName}_p10"] = values[(values.size * 0.10).toInt()].toString()
                config["ultra_${feat.featureName}_p90"] = values[(values.size * 0.90).toInt()].toString()
                config["ultra_${feat.featureName}_min"] = values.first().toString()
                config["ultra_${feat.featureName}_max"] = values.last().toString()
                config["ultra_${feat.featureName}_mean"] = mean.toString()
                config["ultra_${feat.featureName}_std"] = std.toString()
                config["ultra_${feat.featureName}_outlier_low"] = (q1 - 1.5f * iqr).toString()
                config["ultra_${feat.featureName}_outlier_high"] = (q3 + 1.5f * iqr).toString()
            }
        }
        return Patch(
            id = UUID.randomUUID().toString(), modelId = modelId, driftResultId = driftResult.id,
            patchType = "ULTRA_AGGRESSIVE_COMBINED", status = "CREATED", createdAt = java.time.Instant.now().toString(),
            config = config, safetyScore = 0.6, effectivenessScore = 0.95,
            description = "Ultra-aggressive distribution matching patch with clipping, mean/std alignment, quantile mapping and weighted rebalancing"
        )
    }

    private fun calcStd(data: List<Double>): Double {
        if (data.isEmpty()) return 0.0
        val mean = data.average()
        return sqrt(data.map { (it - mean) * (it - mean) }.average())
    }
}
