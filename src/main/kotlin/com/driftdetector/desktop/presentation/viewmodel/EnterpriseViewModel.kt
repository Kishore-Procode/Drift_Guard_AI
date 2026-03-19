package com.driftdetector.desktop.presentation.viewmodel

import com.driftdetector.desktop.core.analytics.ExplainabilityEngine
import com.driftdetector.desktop.core.data.DataFileParser
import com.driftdetector.desktop.core.enterprise.BatchAuditEngine
import com.driftdetector.desktop.core.enterprise.MultiVersionModelComparator
import com.driftdetector.desktop.core.enterprise.ProductionScenarioSimulator
import com.driftdetector.desktop.core.ml.Predictor
import com.driftdetector.desktop.domain.model.DriftResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs

class EnterpriseViewModel {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val parser = DataFileParser()
    private val batchAuditEngine = BatchAuditEngine()
    private val scenarioSimulator = ProductionScenarioSimulator()
    private val modelComparator = MultiVersionModelComparator()

    data class ScenarioSummary(
        val scenarioName: String,
        val predictedAccuracyDrop: Double,
        val recommendedPatch: String
    )

    data class AuditItem(
        val filePath: String,
        val driftType: String,
        val driftScore: Double
    )

    data class ModelComparisonUiResult(
        val disagreementPercentage: Double,
        val driftScore: Double,
        val v1AverageConfidence: Double,
        val v2AverageConfidence: Double,
        val shouldRollback: Boolean,
        val recommendation: String
    )

    private val _isExplainingLoading = MutableStateFlow(false)
    val isExplainingLoading = _isExplainingLoading.asStateFlow()

    private val _explanationText = MutableStateFlow("")
    val explanationText = _explanationText.asStateFlow()

    private val _explanationState = MutableStateFlow<List<Pair<String, Float>>>(emptyList())
    val explanationState = _explanationState.asStateFlow()

    private val _isSimulating = MutableStateFlow(false)
    val isSimulating = _isSimulating.asStateFlow()

    private val _scenarioResults = MutableStateFlow<List<ScenarioSummary>>(emptyList())
    val scenarioResults = _scenarioResults.asStateFlow()

    private val _bestRecommendedPatch = MutableStateFlow("N/A")
    val bestRecommendedPatch = _bestRecommendedPatch.asStateFlow()

    private val _isAuditRunning = MutableStateFlow(false)
    val isAuditRunning = _isAuditRunning.asStateFlow()

    private val _auditResults = MutableStateFlow<List<AuditItem>>(emptyList())
    val auditResults = _auditResults.asStateFlow()

    private val _auditTotalCount = MutableStateFlow(0)
    val auditTotalCount = _auditTotalCount.asStateFlow()

    private val _auditDriftedCount = MutableStateFlow(0)
    val auditDriftedCount = _auditDriftedCount.asStateFlow()

    private val _isComparingModels = MutableStateFlow(false)
    val isComparingModels = _isComparingModels.asStateFlow()

    private val _comparisonResult = MutableStateFlow<ModelComparisonUiResult?>(null)
    val comparisonResult = _comparisonResult.asStateFlow()

    fun explainDrift(driftResult: DriftResult, referenceFile: File, currentFile: File) {
        scope.launch {
            _isExplainingLoading.value = true
            try {
                val refData = parser.parseFile(referenceFile)
                val curData = parser.parseFile(currentFile)
                val sample = curData.data.firstOrNull()
                if (sample == null) {
                    _explanationText.value = "No sample rows found in current dataset."
                    _explanationState.value = emptyList()
                    return@launch
                }

                val referenceMeans = FloatArray(refData.colCount) { idx ->
                    refData.data.map { it[idx] }.average().toFloat()
                }

                val predictor = Predictor { features ->
                    // Deterministic local score for explainability fallback.
                    features.mapIndexed { i, v -> (i + 1) * v.toDouble() }.sum() / (features.size * 100.0)
                }

                val explainer = ExplainabilityEngine(predictor)
                val explanation = explainer.explainSample(refData.featureNames, sample, referenceMeans)
                val top3 = explanation.attributions.take(3)

                _explanationState.value = explanation.attributions.map { it.feature to abs(it.contribution).toFloat().coerceIn(0f, 100f) }
                _explanationText.value = buildString {
                    append("Drift type: ${driftResult.driftType}. ")
                    append("Score: ${"%.3f".format(driftResult.overallDriftScore)}. ")
                    if (top3.isNotEmpty()) {
                        append("Top drivers: ")
                        append(top3.joinToString { "${it.feature} (${"%.2f".format(it.contribution)})" })
                    }
                }
            } catch (e: Exception) {
                _explanationText.value = "Explainability failed: ${e.message}"
                _explanationState.value = emptyList()
            } finally {
                _isExplainingLoading.value = false
            }
        }
    }

    fun runScenarioSimulation(selectedScenario: String, parameter: Double, referenceFile: File, currentFile: File) {
        scope.launch {
            _isSimulating.value = true
            try {
                val refData = parser.parseFile(referenceFile)
                val curData = parser.parseFile(currentFile)

                val scenario = when (selectedScenario) {
                    "covariate_shift" -> ProductionScenarioSimulator.Scenario(
                        name = "Covariate Shift",
                        featureShifts = mapOf("temperature" to parameter)
                    )
                    "sensor_failure" -> ProductionScenarioSimulator.Scenario(
                        name = "Sensor Failure",
                        failedSensors = setOf("temperature")
                    )
                    else -> ProductionScenarioSimulator.Scenario(
                        name = "Seasonal Shift",
                        scaleBySigma = mapOf("temperature" to parameter)
                    )
                }

                val result = scenarioSimulator.runScenario(
                    modelId = "scenario-lab",
                    referenceData = refData.data,
                    currentData = curData.data,
                    featureNames = refData.featureNames,
                    scenario = scenario
                )

                val drop = ((result.afterScore - result.beforeScore) * 100.0).coerceAtLeast(0.0)
                val recommendedPatch = when {
                    result.afterScore > 0.7 -> "ULTRA_AGGRESSIVE"
                    result.afterScore > 0.4 -> "NORMALIZATION + REWEIGHTING"
                    else -> "LIGHT CLIPPING"
                }

                _scenarioResults.value = listOf(
                    ScenarioSummary(result.scenario.name, drop, recommendedPatch)
                )
                _bestRecommendedPatch.value = recommendedPatch
            } catch (e: Exception) {
                _scenarioResults.value = listOf(ScenarioSummary("Error", 0.0, "N/A: ${e.message}"))
                _bestRecommendedPatch.value = "N/A"
            } finally {
                _isSimulating.value = false
            }
        }
    }

    fun runBatchAudit(folderPath: String, referenceFile: File) {
        scope.launch {
            _isAuditRunning.value = true
            try {
                val report = batchAuditEngine.runBatchAudit(
                    referenceFile = referenceFile,
                    folder = File(folderPath),
                    autoPatch = true,
                    parallelism = 8
                )

                _auditTotalCount.value = report.totalFiles
                _auditDriftedCount.value = report.driftDetectedFiles
                _auditResults.value = report.results.map {
                    AuditItem(
                        filePath = it.fileName,
                        driftType = it.driftType,
                        driftScore = it.driftScoreAfter.takeIf { score -> it.patchApplied } ?: it.driftScoreBefore
                    )
                }
            } catch (e: Exception) {
                _auditResults.value = listOf(AuditItem("ERROR", "ERROR", 0.0))
                _auditTotalCount.value = 0
                _auditDriftedCount.value = 0
            } finally {
                _isAuditRunning.value = false
            }
        }
    }

    fun scheduleAudit(scheduleOption: String, folderPath: String, referenceFile: File) {
        when (scheduleOption) {
            "weekly" -> batchAuditEngine.scheduleWeeklyMonday8am(
                referenceFile = referenceFile,
                folder = File(folderPath),
                autoPatch = true,
                recipients = emptyList()
            )
            "daily" -> batchAuditEngine.scheduleDailyAt(
                hour24 = 23,
                minute = 0,
                referenceFile = referenceFile,
                folder = File(folderPath),
                autoPatch = true,
                recipients = emptyList()
            )
            else -> {
                // manual: no scheduler
            }
        }
    }

    fun compareModelVersions(model1Path: String, model2Path: String, testDataPath: String, referenceFile: File?) {
        scope.launch {
            _isComparingModels.value = true
            try {
                val testFile = File(testDataPath)
                if (!testFile.exists()) {
                    _comparisonResult.value = ModelComparisonUiResult(
                        disagreementPercentage = 0.0,
                        driftScore = 0.0,
                        v1AverageConfidence = 0.0,
                        v2AverageConfidence = 0.0,
                        shouldRollback = false,
                        recommendation = "Test dataset not found: $testDataPath"
                    )
                    return@launch
                }

                val testData = parser.parseFile(testFile)
                val refData = if (referenceFile != null && referenceFile.exists()) {
                    parser.parseFile(referenceFile)
                } else {
                    testData
                }

                val predictorA = Predictor { row -> scoreForModel(row, model1Path) }
                val predictorB = Predictor { row -> scoreForModel(row, model2Path) }

                val compared = modelComparator.compare(
                    referenceData = refData.data,
                    currentData = testData.data,
                    featureNames = refData.featureNames,
                    modelA = MultiVersionModelComparator.VersionedModel("v1", predictorA),
                    modelB = MultiVersionModelComparator.VersionedModel("v2", predictorB)
                )

                val avgA = testData.data.map { predictorA.predict(it) }.average().coerceIn(0.0, 1.0)
                val avgB = testData.data.map { predictorB.predict(it) }.average().coerceIn(0.0, 1.0)

                _comparisonResult.value = ModelComparisonUiResult(
                    disagreementPercentage = compared.predictionDisagreementPercent,
                    driftScore = minOf(compared.driftA, compared.driftB),
                    v1AverageConfidence = avgA,
                    v2AverageConfidence = avgB,
                    shouldRollback = compared.rollbackSuggested,
                    recommendation = if (compared.rollbackSuggested)
                        "Rollback recommended. Prefer ${compared.recommendedVersion}."
                    else
                        "Safe to deploy. Recommended: ${compared.recommendedVersion}."
                )
            } catch (e: Exception) {
                _comparisonResult.value = ModelComparisonUiResult(
                    disagreementPercentage = 0.0,
                    driftScore = 0.0,
                    v1AverageConfidence = 0.0,
                    v2AverageConfidence = 0.0,
                    shouldRollback = false,
                    recommendation = "Comparison failed: ${e.message}"
                )
            } finally {
                _isComparingModels.value = false
            }
        }
    }

    private fun scoreForModel(row: FloatArray, modelPath: String): Double {
        // Placeholder deterministic scorer until full ONNX/TFLite runtime is connected.
        val salt = (modelPath.hashCode().toLong() and 0xFFFF).toDouble() / 65535.0
        val raw = row.mapIndexed { i, v -> (i + 1) * v.toDouble() }.sum() / (row.size * 120.0) + (salt * 0.1)
        return raw.coerceIn(0.0, 1.0)
    }
}
