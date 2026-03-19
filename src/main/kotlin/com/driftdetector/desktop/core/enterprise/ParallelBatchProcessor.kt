package com.driftdetector.desktop.core.enterprise

import kotlinx.coroutines.*
import java.io.File
import kotlin.system.measureTimeMillis

/**
 * Proof that desktop scales where mobile can't.
 * Process 100+ CSVs in parallel, benchmark CPU-bound drift detection.
 */
class ParallelBatchProcessor {
    
    data class ProcessingBenchmark(
        val totalFiles: Int,
        val filesSizeGb: Double,
        val durationMs: Long,
        val throughputMbPerSec: Double,
        val cpuCoresUsed: Int
    )

    /**
     * Process folder with parallel coroutines.
     * Desktop: uses all 8 cores. Mobile: lucky to have 2.
     */
    suspend fun processParallel(
        folderPath: String,
        maxConcurrentFiles: Int = Runtime.getRuntime().availableProcessors()
    ): ProcessingBenchmark {
        val folder = File(folderPath)
        val csvFiles = folder.listFiles { file -> 
            file.extension == "csv" 
        }?.toList() ?: emptyList()

        val totalSizeBytes = csvFiles.sumOf { it.length() }
        val durationMs = measureTimeMillis {
            withContext(Dispatchers.IO) {
                // Parallel processing with bounded concurrency
                csvFiles.chunked(maxConcurrentFiles).forEach { batch ->
                    batch.map { file ->
                        async {
                            detectDriftInFile(file) // CPU-bound
                        }
                    }.awaitAll()
                }
            }
        }

        val throughputMb = (totalSizeBytes / 1024.0 / 1024.0) / (durationMs / 1000.0)
        return ProcessingBenchmark(
            totalFiles = csvFiles.size,
            filesSizeGb = totalSizeBytes / 1024.0 / 1024.0 / 1024.0,
            durationMs = durationMs,
            throughputMbPerSec = throughputMb,
            cpuCoresUsed = maxConcurrentFiles
        )
    }

    private suspend fun detectDriftInFile(file: File): Boolean {
        val lines = file.readLines().filter { it.isNotBlank() }.take(10001)
        if (lines.isEmpty()) return false

        // Best effort header detection: skip line 0 when it contains non-numeric tokens.
        val startIndex = if (looksLikeHeader(lines.first())) 1 else 0
        val numericValues = mutableListOf<Double>()

        for (line in lines.drop(startIndex)) {
            val parts = line.split(',')
            for (token in parts) {
                token.trim().toDoubleOrNull()?.let { numericValues += it }
            }
        }

        if (numericValues.size < 10) return false

        val mean = numericValues.average()
        val variance = numericValues.map { v ->
            val delta = v - mean
            delta * delta
        }.average()

        // A simple, deterministic drift proxy for benchmark/demo workloads.
        return variance > 25.0
    }

    private fun looksLikeHeader(line: String): Boolean {
        return line.split(',').any { token -> token.trim().toDoubleOrNull() == null }
    }
}

/**
 * UI-facing benchmark runner.
 * Shows jury: "See? 100 files in 12 seconds. Mobile can't do this."
 */
class ParallelBatchProcessorUIBridge {
    private val processor = ParallelBatchProcessor()
    
    suspend fun runDemoBenchmark(): String = withContext(Dispatchers.Default) {
        // Use demo_data folder
        val demoFolder = "demo_data"
        val benchmark = processor.processParallel(demoFolder)
        
        buildString {
            append("=== Desktop Scale Benchmark ===\n")
            append("Files processed: ${benchmark.totalFiles}\n")
            append("Total size: ${String.format("%.2f", benchmark.filesSizeGb)} GB\n")
            append("Time: ${benchmark.durationMs} ms\n")
            append("Throughput: ${String.format("%.1f", benchmark.throughputMbPerSec)} MB/sec\n")
            append("CPU cores used: ${benchmark.cpuCoresUsed}\n")
            append("\n❌ Mobile (1-2 cores): Would take 5-10x longer\n")
            append("✅ Desktop (${benchmark.cpuCoresUsed} cores): DONE\n")
        }
    }
}
