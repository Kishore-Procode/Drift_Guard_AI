package com.driftdetector.desktop.core.drift

import com.driftdetector.desktop.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.*
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Core drift detection engine with PSI, KS test, and distribution shift analysis.
 * Ported from the Android DriftDetector for desktop use.
 */
class DriftDetector(
    private val psiThreshold: Double = 0.35,
    private val ksThreshold: Double = 0.10
) {

    /**
     * Detect drift between reference and current data.
     */
    suspend fun detectDrift(
        modelId: String,
        referenceData: List<FloatArray>,
        currentData: List<FloatArray>,
        featureNames: List<String>
    ): DriftResult = withContext(Dispatchers.Default) {
        val featureDrifts = mutableListOf<FeatureDrift>()
        val statisticalTests = mutableListOf<StatisticalTest>()

        for (featureIdx in featureNames.indices) {
            val refFeature = referenceData.map { it[featureIdx].toDouble() }
            val curFeature = currentData.map { it[featureIdx].toDouble() }

            val psi = calculatePSI(refFeature, curFeature)
            val ksResult = performKSTest(refFeature, curFeature)
            val distributionShift = calculateDistributionShift(refFeature, curFeature)
            val attribution = psi / psiThreshold

            val isDrifted = psi > psiThreshold || ksResult.pValue < ksThreshold

            featureDrifts.add(
                FeatureDrift(
                    featureName = featureNames[featureIdx],
                    psiScore = psi,
                    ksStatistic = ksResult.statistic,
                    pValue = ksResult.pValue,
                    attributionScore = attribution,
                    distributionShift = distributionShift,
                    isDrifted = isDrifted
                )
            )

            statisticalTests.add(ksResult)
        }

        val overallDriftScore = calculateWeightedDriftScore(featureDrifts)
        val isDriftDetected = featureDrifts.any { it.isDrifted }
        val driftType = determineDriftType(featureDrifts, isDriftDetected)

        DriftResult(
            id = UUID.randomUUID().toString(),
            modelId = modelId,
            timestamp = Instant.now().toString(),
            driftType = driftType,
            overallDriftScore = overallDriftScore,
            psiThreshold = psiThreshold,
            ksThreshold = ksThreshold,
            isDriftDetected = isDriftDetected,
            featureDrifts = featureDrifts,
            statisticalTests = statisticalTests,
            recommendedPatchTypes = recommendPatchTypes(driftType, overallDriftScore),
            description = buildDescription(driftType, overallDriftScore, featureDrifts)
        )
    }

    // ==================== Normalization ====================

    private fun normalizeData(data: List<FloatArray>): List<FloatArray> {
        if (data.isEmpty()) return data
        val numFeatures = data.first().size
        return data.map { sample ->
            FloatArray(numFeatures) { i ->
                val featureValues = data.map { it[i].toDouble() }
                val mean = featureValues.average()
                val std = calculateStd(featureValues, mean).coerceAtLeast(1e-6)
                ((sample[i] - mean) / std).toFloat()
            }
        }
    }

    // ==================== Drift Score ====================

    private fun calculateWeightedDriftScore(featureDrifts: List<FeatureDrift>): Double {
        if (featureDrifts.isEmpty()) return 0.0
        val weights = featureDrifts.map { exp(it.psiScore / psiThreshold) }
        val totalWeight = weights.sum()
        if (totalWeight == 0.0) return 0.0
        return featureDrifts.zip(weights).sumOf { (d, w) -> d.psiScore * w } / totalWeight
    }

    // ==================== PSI ====================

    private fun calculatePSI(reference: List<Double>, current: List<Double>, bins: Int = 10): Double {
        val refMin = reference.minOrNull() ?: 0.0
        val refMax = reference.maxOrNull() ?: 1.0
        val binWidth = (refMax - refMin) / bins
        var psi = 0.0
        for (i in 0 until bins) {
            val binStart = refMin + i * binWidth
            val binEnd = binStart + binWidth
            val refPct = (reference.count { it >= binStart && it < binEnd }.toDouble() / reference.size).coerceAtLeast(0.0001)
            val curPct = (current.count { it >= binStart && it < binEnd }.toDouble() / current.size).coerceAtLeast(0.0001)
            psi += (curPct - refPct) * ln(curPct / refPct)
        }
        return psi
    }

    // ==================== KS Test ====================

    private fun performKSTest(reference: List<Double>, current: List<Double>): StatisticalTest {
        val sortedRef = reference.sorted()
        val sortedCur = current.sorted()
        var maxDiff = 0.0
        var refIdx = 0
        var curIdx = 0
        while (refIdx < sortedRef.size && curIdx < sortedCur.size) {
            val refCdf = refIdx.toDouble() / sortedRef.size
            val curCdf = curIdx.toDouble() / sortedCur.size
            maxDiff = maxOf(maxDiff, abs(refCdf - curCdf))
            if (sortedRef[refIdx] < sortedCur[curIdx]) refIdx++ else curIdx++
        }
        val ne = (reference.size.toDouble() * current.size) / (reference.size + current.size)
        val pValue = approximateKSPValue(maxDiff, ne)
        return StatisticalTest(
            testName = "Kolmogorov-Smirnov",
            statistic = maxDiff,
            pValue = pValue,
            threshold = ksThreshold,
            isSignificant = pValue < ksThreshold
        )
    }

    private fun approximateKSPValue(statistic: Double, ne: Double): Double {
        val lambda = (sqrt(ne) + 0.12 + 0.11 / sqrt(ne)) * statistic
        var sum = 0.0
        for (k in 1..10) {
            val sign = if (k % 2 == 0) 1.0 else -1.0
            sum += sign * exp(-2.0 * k * k * lambda * lambda)
        }
        return (2.0 * sum).coerceIn(0.0, 1.0)
    }

    // ==================== Distribution Shift ====================

    private fun calculateDistributionShift(reference: List<Double>, current: List<Double>): DistributionShift {
        val refMean = reference.average()
        val curMean = current.average()
        val refStd = calculateStd(reference, refMean)
        val curStd = calculateStd(current, curMean)

        // Skewness
        val refSkew = if (refStd > 0) reference.map { ((it - refMean) / refStd).let { v -> v * v * v } }.average() else 0.0
        val curSkew = if (curStd > 0) current.map { ((it - curMean) / curStd).let { v -> v * v * v } }.average() else 0.0

        // Kurtosis
        val refKurt = if (refStd > 0) reference.map { ((it - refMean) / refStd).let { v -> v * v * v * v } }.average() - 3.0 else 0.0
        val curKurt = if (curStd > 0) current.map { ((it - curMean) / curStd).let { v -> v * v * v * v } }.average() - 3.0 else 0.0

        // Median
        val refMedian = reference.sorted().let { it[it.size / 2] }
        val curMedian = current.sorted().let { it[it.size / 2] }

        return DistributionShift(
            meanShift = curMean - refMean,
            varianceShift = curStd - refStd,
            medianShift = curMedian - refMedian,
            skewnessChange = curSkew - refSkew,
            kurtosisChange = curKurt - refKurt
        )
    }

    // ==================== Drift Type ====================

    private fun determineDriftType(featureDrifts: List<FeatureDrift>, isDriftDetected: Boolean): DriftType {
        if (!isDriftDetected) return DriftType.NO_DRIFT
        val driftedFeatures = featureDrifts.filter { it.isDrifted }
        if (driftedFeatures.isEmpty()) return DriftType.NO_DRIFT

        val driftRatio = driftedFeatures.size.toDouble() / featureDrifts.size
        val meanShifts = driftedFeatures.map { abs(it.distributionShift.meanShift) }
        val varShifts = driftedFeatures.map { abs(it.distributionShift.varianceShift) }
        val avgMeanShift = meanShifts.average()
        val avgVarShift = varShifts.average()

        val driftScores = driftedFeatures.map { it.psiScore }
        val avgDriftScore = driftScores.average()
        val driftVariance = driftScores.map { (it - avgDriftScore) * (it - avgDriftScore) }.average()
        val driftConsistency = if (avgDriftScore > 0.01) sqrt(driftVariance) / avgDriftScore else 0.0
        val shapeChangeRatio = if (avgMeanShift > 0.01) avgVarShift / avgMeanShift else avgVarShift

        return when {
            driftRatio < 0.20 && avgMeanShift > avgVarShift * 2.0 && driftConsistency < 0.5 -> DriftType.PRIOR_DRIFT
            driftRatio in 0.20..0.50 && (driftConsistency > 0.5 || shapeChangeRatio > 2.0) -> DriftType.CONCEPT_DRIFT
            driftConsistency > 0.7 -> DriftType.CONCEPT_DRIFT
            driftRatio > 0.50 && driftConsistency < 0.5 -> DriftType.COVARIATE_DRIFT
            avgMeanShift > 0.3 && avgVarShift > 0.3 -> DriftType.COVARIATE_DRIFT
            driftRatio > 0.40 -> DriftType.COVARIATE_DRIFT
            driftRatio > 0.20 -> DriftType.CONCEPT_DRIFT
            else -> DriftType.PRIOR_DRIFT
        }
    }

    // ==================== Helpers ====================

    private fun calculateStd(data: List<Double>, mean: Double): Double {
        if (data.isEmpty()) return 0.0
        return sqrt(data.map { (it - mean) * (it - mean) }.average())
    }

    private fun recommendPatchTypes(driftType: DriftType, score: Double): List<String> {
        return when {
            score > 0.7 -> listOf("FEATURE_CLIPPING", "NORMALIZATION_UPDATE", "FEATURE_REWEIGHTING")
            driftType == DriftType.COVARIATE_DRIFT -> listOf("NORMALIZATION_UPDATE", "FEATURE_CLIPPING")
            driftType == DriftType.CONCEPT_DRIFT -> listOf("FEATURE_REWEIGHTING", "THRESHOLD_TUNING")
            driftType == DriftType.PRIOR_DRIFT -> listOf("THRESHOLD_TUNING")
            else -> listOf("NORMALIZATION_UPDATE")
        }
    }

    private fun buildDescription(driftType: DriftType, score: Double, featureDrifts: List<FeatureDrift>): String {
        val driftedCount = featureDrifts.count { it.isDrifted }
        val severity = when {
            score > 0.7 -> "CRITICAL"
            score > 0.5 -> "HIGH"
            score > 0.3 -> "MEDIUM"
            score > 0.1 -> "LOW"
            else -> "NONE"
        }
        return "$severity ${driftType.name} detected. Score: ${"%.3f".format(score)}. " +
                "$driftedCount of ${featureDrifts.size} features affected."
    }
}
