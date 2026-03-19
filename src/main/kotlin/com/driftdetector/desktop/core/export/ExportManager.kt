package com.driftdetector.desktop.core.export

import com.driftdetector.desktop.domain.model.*
import com.driftdetector.desktop.util.FileUtils
import com.google.gson.GsonBuilder
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manages export of drift results and patches to files on desktop.
 */
class ExportManager {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    enum class ExportFormat { JSON, CSV, TEXT }

    data class ExportResult(
        val success: Boolean,
        val message: String,
        val file: File? = null
    )

    /**
     * Export drift results to a file.
     */
    fun exportDriftResults(results: List<DriftResult>, format: ExportFormat = ExportFormat.JSON): ExportResult {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val ext = when (format) { ExportFormat.JSON -> "json"; ExportFormat.CSV -> "csv"; ExportFormat.TEXT -> "txt" }
        val defaultName = "drift_results_$timestamp.$ext"
        val file = FileUtils.chooseSaveFile(defaultName, ext) ?: return ExportResult(false, "Export cancelled")

        return try {
            when (format) {
                ExportFormat.JSON -> file.writeText(gson.toJson(results.map { it.toExportMap() }))
                ExportFormat.CSV -> file.writeText(driftResultsToCsv(results))
                ExportFormat.TEXT -> file.writeText(driftResultsToText(results))
            }
            ExportResult(true, "Exported ${results.size} results to ${file.name}", file)
        } catch (e: Exception) {
            ExportResult(false, "Export failed: ${e.message}")
        }
    }

    /**
     * Export patches to a file.
     */
    fun exportPatches(patches: List<Patch>, format: ExportFormat = ExportFormat.JSON): ExportResult {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val ext = when (format) { ExportFormat.JSON -> "json"; ExportFormat.CSV -> "csv"; ExportFormat.TEXT -> "txt" }
        val defaultName = "patches_$timestamp.$ext"
        val file = FileUtils.chooseSaveFile(defaultName, ext) ?: return ExportResult(false, "Export cancelled")

        return try {
            when (format) {
                ExportFormat.JSON -> file.writeText(gson.toJson(patches.map { it.toExportMap() }))
                ExportFormat.CSV -> file.writeText(patchesToCsv(patches))
                ExportFormat.TEXT -> file.writeText(patchesToText(patches))
            }
            ExportResult(true, "Exported ${patches.size} patches to ${file.name}", file)
        } catch (e: Exception) {
            ExportResult(false, "Export failed: ${e.message}")
        }
    }

    private fun DriftResult.toExportMap() = mapOf(
        "id" to id, "modelId" to modelId, "timestamp" to timestamp,
        "driftType" to driftType.name, "overallDriftScore" to overallDriftScore,
        "isDriftDetected" to isDriftDetected, "description" to description,
        "featuresAffected" to featureDrifts.count { it.isDrifted },
        "totalFeatures" to featureDrifts.size
    )

    private fun Patch.toExportMap() = mapOf(
        "id" to id, "modelId" to modelId, "patchType" to patchType,
        "status" to status, "safetyScore" to safetyScore,
        "effectivenessScore" to effectivenessScore, "description" to description,
        "createdAt" to createdAt
    )

    private fun driftResultsToCsv(results: List<DriftResult>): String {
        val sb = StringBuilder("id,modelId,timestamp,driftType,overallDriftScore,isDriftDetected,featuresAffected,totalFeatures\n")
        results.forEach { r ->
            sb.appendLine("${r.id},${r.modelId},${r.timestamp},${r.driftType.name},${r.overallDriftScore},${r.isDriftDetected},${r.featureDrifts.count { it.isDrifted }},${r.featureDrifts.size}")
        }
        return sb.toString()
    }

    private fun driftResultsToText(results: List<DriftResult>): String {
        val sb = StringBuilder("=== DriftGuardAI Drift Detection Report ===\n\n")
        results.forEachIndexed { i, r ->
            sb.appendLine("--- Result ${i + 1} ---")
            sb.appendLine("Type: ${r.driftType.name}")
            sb.appendLine("Score: ${"%.3f".format(r.overallDriftScore)}")
            sb.appendLine("Detected: ${r.isDriftDetected}")
            sb.appendLine("Features: ${r.featureDrifts.count { it.isDrifted }}/${r.featureDrifts.size} affected")
            sb.appendLine(r.description)
            sb.appendLine()
        }
        return sb.toString()
    }

    private fun patchesToCsv(patches: List<Patch>): String {
        val sb = StringBuilder("id,patchType,status,safetyScore,effectivenessScore,description\n")
        patches.forEach { p ->
            sb.appendLine("${p.id},${p.patchType},${p.status},${p.safetyScore},${p.effectivenessScore},\"${p.description}\"")
        }
        return sb.toString()
    }

    private fun patchesToText(patches: List<Patch>): String {
        val sb = StringBuilder("=== DriftGuardAI Patch Report ===\n\n")
        patches.forEachIndexed { i, p ->
            sb.appendLine("--- Patch ${i + 1}: ${p.patchType} ---")
            sb.appendLine("Safety: ${"%.2f".format(p.safetyScore)} | Effectiveness: ${"%.2f".format(p.effectivenessScore)}")
            sb.appendLine("Status: ${p.status}")
            sb.appendLine(p.description)
            sb.appendLine()
        }
        return sb.toString()
    }
}
