# DriftGuardAI Desktop Enterprise Guide

## Overview
DriftGuardAI Desktop is an offline-first ML drift monitoring and remediation workbench designed for ML engineers.

The app combines:
- Drift detection and root-cause analysis
- Patch generation and aggressive auto-remediation
- Scenario simulation and model version comparison
- Batch audits and scheduling for enterprise workflows
- Explainability for feature-level attribution

## Key Value Proposition
- Local-first execution: low latency, no mandatory cloud dependency
- Actionable drift remediation: not just detection
- Enterprise automation workflows from the desktop UI
- Strong demo support with included sample data

## Current Tab Suite
Main desktop navigation includes:
- Dashboard
- Upload
- Drift Analysis
- Patches
- Instant Fix
- Explainability
- What-If Lab
- Batch Audits
- Model Versions
- Jury Demo
- Evaluation
- Simulation
- Auto Monitor
- AI Assistant
- Analytics
- Settings

## Core Architecture

### Presentation Layer
- Main shell and navigation: src/main/kotlin/com/driftdetector/desktop/presentation/screen/MainScreen.kt
- Enterprise tabs: src/main/kotlin/com/driftdetector/desktop/presentation/screen/EnterpriseTabs.kt
- Enterprise orchestration VM: src/main/kotlin/com/driftdetector/desktop/presentation/viewmodel/EnterpriseViewModel.kt
- Existing application VMs: src/main/kotlin/com/driftdetector/desktop/presentation/viewmodel/ViewModels.kt

### Enterprise Core Layer
- Batch audit + scheduling: src/main/kotlin/com/driftdetector/desktop/core/enterprise/BatchAuditEngine.kt
- Parallel benchmark processor: src/main/kotlin/com/driftdetector/desktop/core/enterprise/ParallelBatchProcessor.kt
- Offline performance benchmark: src/main/kotlin/com/driftdetector/desktop/core/enterprise/OfflinePerformanceBenchmark.kt
- Scenario simulator: src/main/kotlin/com/driftdetector/desktop/core/enterprise/ProductionScenarioSimulator.kt
- Multi-version comparison: src/main/kotlin/com/driftdetector/desktop/core/enterprise/MultiVersionModelComparator.kt
- Custom rules engine: src/main/kotlin/com/driftdetector/desktop/core/enterprise/CustomDriftRulesEngine.kt

### Analytics and ML Interfaces
- Explainability engine: src/main/kotlin/com/driftdetector/desktop/core/analytics/ExplainabilityEngine.kt
- Predictor abstraction: src/main/kotlin/com/driftdetector/desktop/core/ml/Predictor.kt

### Data and Drift
- Parser and validation: src/main/kotlin/com/driftdetector/desktop/core/data/DataFileParser.kt
- Drift detection: src/main/kotlin/com/driftdetector/desktop/core/drift/DriftDetector.kt
- Patch generation: src/main/kotlin/com/driftdetector/desktop/core/patch/PatchGenerator.kt
- Aggressive simulation pipeline: src/main/kotlin/com/driftdetector/desktop/core/simulation/DriftSimulator.kt

## Implemented Updates (This Iteration)

### 1) Enterprise UI is wired into main navigation
- EnterpriseViewModel is instantiated in MainScreen
- Explainability, What-If Lab, Batch Audits, and Model Versions routes are active
- Sidebar and top-header labels include enterprise sections

### 2) Batch benchmark correctness improvements
File: src/main/kotlin/com/driftdetector/desktop/core/enterprise/ParallelBatchProcessor.kt
- Removed data race on total byte counting
- Fixed CSV numeric parsing for benchmark workload
- Added robust header detection for CSV lines
- Added safer fallback when numeric extraction is insufficient

### 3) Realistic benchmark workload improvements
File: src/main/kotlin/com/driftdetector/desktop/core/enterprise/OfflinePerformanceBenchmark.kt
- Replaced unstable random map/list-heavy loops with deterministic seeded workloads
- Used measurable numeric arrays and explicit variance calculations
- Preserved measured timing behavior with less noise and less allocation overhead

### 4) Data parser resilience improvements
File: src/main/kotlin/com/driftdetector/desktop/core/data/DataFileParser.kt
- Wrapped parse flow with clearer exception propagation
- Added explicit checks for empty data and zero-column outputs
- Added malformed-row tracking for delimited files
- Added column mismatch threshold guard to reject highly inconsistent CSVs

## Known Limitations (Transparent)
- Real ONNX/TFLite execution is still abstracted behind Predictor; current model scoring in enterprise VM uses deterministic fallback logic for demo continuity.
- PDF export and SMTP sender are not fully wired yet.
- Some enterprise tab export actions are placeholders.

## Why This Is Still Demo-Strong
- All major enterprise tabs load and run non-crashing workflows
- Explainability computes per-sample perturbation attributions dynamically
- Batch audits execute asynchronously and avoid UI blocking by using background coroutines
- Drift simulation and comparison flows produce deterministic, inspectable outcomes

## Demo Data
Use the built-in sample data in:
- desktop app/demo_data/reference_data.csv
- desktop app/demo_data/current_data_WITH_DRIFT.csv
- desktop app/demo_data/current_data_NO_DRIFT.csv
- desktop app/demo_data/demo_model.tflite

## Build and Verification
From workspace root:

1. Compile
- .\gradlew.bat ":desktop app:compileKotlin" --no-daemon

2. Build
- .\gradlew.bat ":desktop app:build" --no-daemon --console=plain

3. Run
- .\gradlew.bat ":desktop app:run" --no-daemon

## 5-Minute Validation Checklist
- All enterprise tabs render without crashes
- Explainability produces non-empty ranked feature contributions
- What-If Lab produces scenario result and recommended patch
- Batch audit processes files and shows summary counts
- Model comparison yields disagreement score and recommendation
- Parser rejects severely malformed CSV with clear error message

## Recommended Next Upgrades
1. Integrate real ONNX and TFLite predictor implementations
2. Add SMTP sender for scheduled batch report delivery
3. Add PDF exporter for batch audit reports
4. Add row-level dataset diff and merge with provenance logging
5. Add statistical A/B analyzer with p-values and confidence intervals

## Summary
DriftGuardAI Desktop now has a concrete, integrated enterprise feature path with improved robustness in parsing, benchmarking, and batch processing. The system is suitable for hackathon demos and can be evolved into production with model runtime integrations and reporting add-ons.
