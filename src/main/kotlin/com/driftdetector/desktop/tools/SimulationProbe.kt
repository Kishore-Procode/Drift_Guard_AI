package com.driftdetector.desktop.tools

import com.driftdetector.desktop.core.data.DataFileParser
import com.driftdetector.desktop.core.drift.DriftDetector
import com.driftdetector.desktop.core.simulation.DriftSimulator
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * One-off CLI probe to print before/after drift scores on demo datasets.
 */
fun main() = runBlocking {
    val root = File(System.getProperty("user.dir"))
    val directDemoDir = File(root, "demo_data")
    val nestedDemoDir = File(root, "desktop app/demo_data")
    val demoDir = when {
        directDemoDir.exists() -> directDemoDir
        nestedDemoDir.exists() -> nestedDemoDir
        else -> directDemoDir
    }

    val refFile = File(demoDir, "reference_data.csv")
    val curFile = File(demoDir, "current_data_WITH_DRIFT.csv")

    require(refFile.exists()) { "Reference file not found: ${refFile.absolutePath}" }
    require(curFile.exists()) { "Current file not found: ${curFile.absolutePath}" }

    val parser = DataFileParser()
    val reference = parser.parseFile(refFile)
    val current = parser.parseFile(curFile)

    val detector = DriftDetector()
    val simulator = DriftSimulator()

    val before = detector.detectDrift(
        modelId = "probe-model",
        referenceData = reference.data,
        currentData = current.data,
        featureNames = reference.featureNames
    )

    val patchedData = simulator.applyAggressivePatchPipeline(
        referenceData = reference.data,
        currentData = current.data
    )

    val after = detector.detectDrift(
        modelId = "probe-model",
        referenceData = reference.data,
        currentData = patchedData,
        featureNames = reference.featureNames
    )

    val reduction = if (before.overallDriftScore > 1e-9) {
        ((before.overallDriftScore - after.overallDriftScore) / before.overallDriftScore) * 100.0
    } else {
        0.0
    }

    println("=== Drift Patch Probe ===")
    println("Rows (reference/current): ${reference.rowCount}/${current.rowCount}")
    println("Features: ${reference.colCount}")
    println("Drift Before: ${"%.6f".format(before.overallDriftScore)}")
    println("Drift After : ${"%.6f".format(after.overallDriftScore)}")
    println("Reduction   : ${"%.2f".format(reduction)}%")
    println("Detected Before/After: ${before.isDriftDetected}/${after.isDriftDetected}")
}
