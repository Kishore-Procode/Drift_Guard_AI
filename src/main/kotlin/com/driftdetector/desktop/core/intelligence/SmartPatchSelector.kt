package com.driftdetector.desktop.core.intelligence

import com.driftdetector.desktop.domain.model.*

/**
 * Smart Patch Selection Engine — automatically chooses the BEST patch strategy.
 * Item #3: Decision engine that selects optimal patch based on drift characteristics.
 */
class SmartPatchSelector {

    data class PatchRecommendation(
        val bestPatch: Patch,
        val reason: String,
        val allRanked: List<RankedPatch>
    )

    data class RankedPatch(
        val patch: Patch,
        val score: Double,
        val reason: String
    )

    /**
     * Automatically select the best patch from the generated list.
     */
    fun selectBestPatch(patches: List<Patch>, driftResult: DriftResult): PatchRecommendation {
        val ranked = patches.map { patch ->
            val score = calculatePatchScore(patch, driftResult)
            val reason = explainSelection(patch, driftResult)
            RankedPatch(patch, score, reason)
        }.sortedByDescending { it.score }

        val best = ranked.first()
        return PatchRecommendation(
            bestPatch = best.patch,
            reason = best.reason,
            allRanked = ranked
        )
    }

    private fun calculatePatchScore(patch: Patch, driftResult: DriftResult): Double {
        val driftScore = driftResult.overallDriftScore
        val driftType = driftResult.driftType

        // Base score from safety * effectiveness
        var score = patch.safetyScore * 0.4 + patch.effectivenessScore * 0.6

        // Bonus for matching drift severity to patch aggressiveness
        when {
            driftScore > 0.7 && patch.patchType.contains("ULTRA") -> score += 0.3
            driftScore > 0.7 && patch.patchType.contains("CLIPPING") -> score += 0.15
            driftScore in 0.3..0.7 && patch.patchType.contains("NORMALIZATION") -> score += 0.2
            driftScore in 0.3..0.7 && patch.patchType.contains("REWEIGHTING") -> score += 0.15
            driftScore < 0.3 && patch.patchType.contains("THRESHOLD") -> score += 0.25
        }

        // Bonus for matching drift type to strategy
        when (driftType) {
            DriftType.COVARIATE_DRIFT -> {
                if (patch.patchType.contains("NORMALIZATION")) score += 0.15
                if (patch.patchType.contains("CLIPPING")) score += 0.1
            }
            DriftType.CONCEPT_DRIFT -> {
                if (patch.patchType.contains("REWEIGHTING")) score += 0.15
                if (patch.patchType.contains("THRESHOLD")) score += 0.1
            }
            DriftType.PRIOR_DRIFT -> {
                if (patch.patchType.contains("THRESHOLD")) score += 0.2
            }
            DriftType.NO_DRIFT -> {}
        }

        return score.coerceIn(0.0, 1.0)
    }

    private fun explainSelection(patch: Patch, driftResult: DriftResult): String {
        val driftScore = driftResult.overallDriftScore
        val level = when {
            driftScore > 0.7 -> "critical"
            driftScore > 0.5 -> "high"
            driftScore > 0.3 -> "moderate"
            else -> "low"
        }
        return when {
            patch.patchType.contains("ULTRA") ->
                "Ultra-aggressive: Best for $level drift (score ${"%.2f".format(driftScore)}). Maximum drift reduction."
            patch.patchType.contains("NORMALIZATION") ->
                "Normalization: Re-centers distributions for ${driftResult.driftType.name}. Safe with good effectiveness."
            patch.patchType.contains("CLIPPING") ->
                "Clipping: Constrains outliers to reference range. Good for covariate drift."
            patch.patchType.contains("REWEIGHTING") ->
                "Reweighting: Reduces drifted feature influence. Best for concept/prior drift."
            patch.patchType.contains("THRESHOLD") ->
                "Threshold tuning: Minimal change, safest option for $level drift."
            else -> "Selected based on safety/effectiveness balance."
        }
    }
}
