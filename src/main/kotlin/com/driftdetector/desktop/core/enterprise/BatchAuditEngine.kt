package com.driftdetector.desktop.core.enterprise

import com.driftdetector.desktop.core.data.DataFileParser
import com.driftdetector.desktop.core.drift.DriftDetector
import com.driftdetector.desktop.core.patch.PatchGenerator
import com.driftdetector.desktop.core.simulation.DriftSimulator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime

/**
 * Batch engine for enterprise-scale audits (100+ CSV files) and scheduling.
 */
class BatchAuditEngine(
    private val parser: DataFileParser = DataFileParser(),
    private val detector: DriftDetector = DriftDetector(),
    private val patchGenerator: PatchGenerator = PatchGenerator(),
    private val simulator: DriftSimulator = DriftSimulator(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    data class FileAuditResult(
        val fileName: String,
        val driftDetected: Boolean,
        val driftType: String,
        val driftScoreBefore: Double,
        val driftScoreAfter: Double,
        val reductionPercent: Double,
        val patchesGenerated: Int,
        val patchApplied: Boolean,
        val error: String? = null
    )

    data class BatchAuditReport(
        val startedAt: String,
        val completedAt: String,
        val folder: String,
        val totalFiles: Int,
        val driftDetectedFiles: Int,
        val patchedFiles: Int,
        val averageReductionPercent: Double,
        val results: List<FileAuditResult>
    )

    interface EmailReportSender {
        suspend fun send(report: BatchAuditReport, recipients: List<String>)
    }

    class ConsoleEmailReportSender : EmailReportSender {
        override suspend fun send(report: BatchAuditReport, recipients: List<String>) {
            println("[BatchAuditEngine] Email report dispatched to ${recipients.joinToString()} (${report.totalFiles} files)")
        }
    }

    private var scheduledJob: Job? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun runBatchAudit(
        referenceFile: File,
        folder: File,
        autoPatch: Boolean,
        parallelism: Int = 8
    ): BatchAuditReport = coroutineScope {
        require(referenceFile.exists()) { "Reference file not found: ${referenceFile.path}" }
        require(folder.exists() && folder.isDirectory) { "Folder not found or invalid: ${folder.path}" }

        val startTs = LocalDateTime.now().toString()
        val reference = parser.parseFile(referenceFile)

        val csvFiles = folder.listFiles { f ->
            f.isFile && f.extension.equals("csv", ignoreCase = true) && f.absolutePath != referenceFile.absolutePath
        }?.toList().orEmpty()

        val dispatcher = Dispatchers.Default.limitedParallelism(parallelism.coerceIn(1, 32))
        val results = csvFiles.map { file ->
            async(dispatcher) {
                auditSingleFile("batch-audit", reference.data, reference.featureNames, file, autoPatch)
            }
        }.awaitAll()

        val drifted = results.count { it.driftDetected }
        val patched = results.count { it.patchApplied }
        val avgReduction = results.filter { it.patchApplied }.map { it.reductionPercent }.average().takeIf { !it.isNaN() } ?: 0.0

        BatchAuditReport(
            startedAt = startTs,
            completedAt = LocalDateTime.now().toString(),
            folder = folder.absolutePath,
            totalFiles = results.size,
            driftDetectedFiles = drifted,
            patchedFiles = patched,
            averageReductionPercent = avgReduction,
            results = results
        )
    }

    fun scheduleWeeklyMonday8am(
        referenceFile: File,
        folder: File,
        autoPatch: Boolean,
        recipients: List<String> = emptyList(),
        emailSender: EmailReportSender = ConsoleEmailReportSender()
    ) {
        scheduleRecurring(referenceFile, folder, autoPatch, recipients, emailSender) {
            val now = LocalDateTime.now()
            var next = now.withHour(8).withMinute(0).withSecond(0).withNano(0)
            while (next.dayOfWeek != DayOfWeek.MONDAY || !next.isAfter(now)) {
                next = next.plusDays(1)
            }
            Duration.between(now, next)
        }
    }

    fun scheduleDailyAt(
        hour24: Int,
        minute: Int,
        referenceFile: File,
        folder: File,
        autoPatch: Boolean,
        recipients: List<String> = emptyList(),
        emailSender: EmailReportSender = ConsoleEmailReportSender()
    ) {
        scheduleRecurring(referenceFile, folder, autoPatch, recipients, emailSender) {
            val now = LocalDateTime.now()
            var next = now.withHour(hour24).withMinute(minute).withSecond(0).withNano(0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            Duration.between(now, next)
        }
    }

    suspend fun stopSchedule() {
        scheduledJob?.cancelAndJoin()
        scheduledJob = null
    }

    private fun scheduleRecurring(
        referenceFile: File,
        folder: File,
        autoPatch: Boolean,
        recipients: List<String>,
        emailSender: EmailReportSender,
        initialDelayProvider: () -> Duration
    ) {
        scheduledJob?.cancel()
        scheduledJob = scope.launch {
            delay(initialDelayProvider().toMillis().coerceAtLeast(1000L))
            while (isActive) {
                val report = runBatchAudit(referenceFile, folder, autoPatch)
                if (recipients.isNotEmpty()) {
                    emailSender.send(report, recipients)
                }
                delay(Duration.ofDays(1).toMillis())
            }
        }
    }

    private suspend fun auditSingleFile(
        modelId: String,
        referenceData: List<FloatArray>,
        featureNames: List<String>,
        currentFile: File,
        autoPatch: Boolean
    ): FileAuditResult {
        return try {
            val current = parser.parseFile(currentFile)
            val before = detector.detectDrift(modelId, referenceData, current.data, featureNames)

            if (!before.isDriftDetected || !autoPatch) {
                FileAuditResult(
                    fileName = currentFile.name,
                    driftDetected = before.isDriftDetected,
                    driftType = before.driftType.name,
                    driftScoreBefore = before.overallDriftScore,
                    driftScoreAfter = before.overallDriftScore,
                    reductionPercent = 0.0,
                    patchesGenerated = 0,
                    patchApplied = false
                )
            } else {
                val patches = patchGenerator.generateComprehensivePatches(
                    modelId = modelId,
                    driftResult = before,
                    referenceData = referenceData,
                    currentData = current.data
                )
                val patchedData = simulator.applyAggressivePatchPipeline(referenceData, current.data)
                val after = detector.detectDrift(modelId, referenceData, patchedData, featureNames)
                val reduction = if (before.overallDriftScore > 1e-9) {
                    ((before.overallDriftScore - after.overallDriftScore) / before.overallDriftScore * 100.0).coerceIn(0.0, 100.0)
                } else 0.0

                FileAuditResult(
                    fileName = currentFile.name,
                    driftDetected = true,
                    driftType = before.driftType.name,
                    driftScoreBefore = before.overallDriftScore,
                    driftScoreAfter = after.overallDriftScore,
                    reductionPercent = reduction,
                    patchesGenerated = patches.size,
                    patchApplied = true
                )
            }
        } catch (e: Exception) {
            FileAuditResult(
                fileName = currentFile.name,
                driftDetected = false,
                driftType = "ERROR",
                driftScoreBefore = 0.0,
                driftScoreAfter = 0.0,
                reductionPercent = 0.0,
                patchesGenerated = 0,
                patchApplied = false,
                error = e.message
            )
        }
    }
}
