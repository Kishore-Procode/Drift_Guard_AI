package com.driftdetector.desktop.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.driftdetector.desktop.presentation.viewmodel.DriftDetectionViewModel
import com.driftdetector.desktop.presentation.viewmodel.EnterpriseViewModel
import com.driftdetector.desktop.presentation.viewmodel.UploadViewModel
import com.driftdetector.desktop.core.enterprise.ParallelBatchProcessorUIBridge
import com.driftdetector.desktop.core.enterprise.OfflinePerformanceBenchmark
import com.driftdetector.desktop.core.enterprise.EnterpriseAutomationShowcase
import com.driftdetector.desktop.util.MemoryAnalysis
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@Composable
fun ExplainabilityTab(
    viewModel: EnterpriseViewModel,
    driftViewModel: DriftDetectionViewModel,
    uploadViewModel: UploadViewModel
) {
    val driftResult by driftViewModel.currentDriftResult.collectAsState()
    val referenceFile by uploadViewModel.selectedReferenceFile.collectAsState()
    val currentFile by uploadViewModel.selectedCurrentFile.collectAsState()

    val isLoading by viewModel.isExplainingLoading.collectAsState()
    val explanationText by viewModel.explanationText.collectAsState()
    val explanationState by viewModel.explanationState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Why Did Drift Happen?", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 16.dp))
        Text(
            "Understand which features caused drift and their impact on model confidence.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Button(
            onClick = {
                val dr = driftResult
                val rf = referenceFile
                val cf = currentFile
                if (dr != null && rf != null && cf != null) {
                    viewModel.explainDrift(dr, rf, cf)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading && driftResult != null && referenceFile != null && currentFile != null
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp).padding(end = 8.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            Text("Explain Drift")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (explanationText.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Text(explanationText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
            }
        }

        if (explanationState.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Feature Impact on Drift", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(12.dp))

                    explanationState.forEach { (feature, impact) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(feature, modifier = Modifier.weight(0.3f))
                            LinearProgressIndicator(
                                progress = { (impact / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier.weight(0.5f).height(8.dp)
                            )
                            Text(
                                String.format("%.1f%%", impact),
                                modifier = Modifier.weight(0.2f),
                                textAlign = TextAlign.End,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            Button(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                Text("Export Explanation (JSON)")
            }
        }
    }
}

@Composable
fun ScenarioLabTab(viewModel: EnterpriseViewModel, uploadViewModel: UploadViewModel) {
    val referenceFile by uploadViewModel.selectedReferenceFile.collectAsState()
    val currentFile by uploadViewModel.selectedCurrentFile.collectAsState()

    val isSimulating by viewModel.isSimulating.collectAsState()
    val scenarioResults by viewModel.scenarioResults.collectAsState()
    val bestPatch by viewModel.bestRecommendedPatch.collectAsState()

    var selectedScenario by remember { mutableStateOf("covariate_shift") }
    var parameterValue by remember { mutableStateOf("10") }
    var scenarioDropdownExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())
    ) {
        Text("What-If Lab", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 16.dp))
        Text(
            "Test how different scenarios affect model predictions before they hit production.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Scenario Type", style = MaterialTheme.typography.labelMedium)
                Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Button(onClick = { scenarioDropdownExpanded = !scenarioDropdownExpanded }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            when (selectedScenario) {
                                "covariate_shift" -> "Covariate Shift (+/- value)"
                                "sensor_failure" -> "Sensor Failure (force to 0)"
                                "seasonal_shift" -> "Seasonal Shift (sigma-based)"
                                else -> "Select scenario"
                            }
                        )
                    }

                    DropdownMenu(expanded = scenarioDropdownExpanded, onDismissRequest = { scenarioDropdownExpanded = false }) {
                        listOf(
                            "covariate_shift" to "Covariate Shift (+/- value)",
                            "sensor_failure" to "Sensor Failure (force to 0)",
                            "seasonal_shift" to "Seasonal Shift (sigma-based)"
                        ).forEach { (key, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    selectedScenario = key
                                    scenarioDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = parameterValue,
            onValueChange = { parameterValue = it },
            label = { Text("Parameter Value") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val rf = referenceFile
                val cf = currentFile
                if (rf != null && cf != null) {
                    viewModel.runScenarioSimulation(selectedScenario, parameterValue.toDoubleOrNull() ?: 10.0, rf, cf)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSimulating && referenceFile != null && currentFile != null
        ) {
            if (isSimulating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp).padding(end = 8.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            Text("Run Scenario")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (scenarioResults.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Results", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(12.dp))

                    scenarioResults.forEach { result ->
                        Column(modifier = Modifier.padding(bottom = 12.dp)) {
                            Text("Scenario: ${result.scenarioName}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 4.dp))
                            Text("Predicted Impact: ${String.format("%.2f", result.predictedAccuracyDrop)}% accuracy drop", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "Recommended Patch: ${result.recommendedPatch}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            if (result != scenarioResults.last()) {
                                Divider(modifier = Modifier.padding(top = 8.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Best Patch: $bestPatch", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                }
            }
        }
    }
}

@Composable
fun BatchAuditTab(viewModel: EnterpriseViewModel, uploadViewModel: UploadViewModel) {
    val isAuditRunning by viewModel.isAuditRunning.collectAsState()
    val auditResults by viewModel.auditResults.collectAsState()
    val totalCount by viewModel.auditTotalCount.collectAsState()
    val driftedCount by viewModel.auditDriftedCount.collectAsState()
    val referenceFile by uploadViewModel.selectedReferenceFile.collectAsState()

    var folderPath by remember { mutableStateOf("C:\\Users\\Kishore\\DriftGuardAi\\DriftGuard_Ai\\desktop app\\demo_data") }
    var scheduleOption by remember { mutableStateOf("weekly") }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())
    ) {
        Text("Batch Audit Scheduler", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 16.dp))
        Text(
            "Scan folders for drift automatically. Schedule weekly audits or run on-demand.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        OutlinedTextField(
            value = folderPath,
            onValueChange = { folderPath = it },
            label = { Text("Folder Path") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            trailingIcon = {
                IconButton(onClick = {}) { Icon(Icons.Default.Edit, contentDescription = "Browse") }
            }
        )

        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Schedule", style = MaterialTheme.typography.labelMedium)
                listOf(
                    "weekly" to "Weekly (Mondays 8:00 AM)",
                    "daily" to "Daily (11:00 PM)",
                    "manual" to "Manual Only"
                ).forEach { (key, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { scheduleOption = key }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = scheduleOption == key, onClick = { scheduleOption = key }, modifier = Modifier.padding(end = 8.dp))
                        Text(label, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    val rf = referenceFile
                    if (rf != null) {
                        viewModel.runBatchAudit(folderPath, rf)
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !isAuditRunning && referenceFile != null
            ) {
                if (isAuditRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp).padding(end = 8.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Text("Run Now")
            }

            Button(
                onClick = {
                    val rf = referenceFile
                    if (rf != null) {
                        viewModel.scheduleAudit(scheduleOption, folderPath, rf)
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors()
            ) {
                Text("Schedule")
            }
        }

        if (auditResults.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Audit Summary", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("$totalCount", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                            Text("Total Files", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Column {
                            Text("$driftedCount", style = MaterialTheme.typography.headlineSmall, color = Color(0xFFD32F2F))
                            Text("Drifted", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Column {
                            val rate = if (totalCount > 0) (driftedCount * 100.0) / totalCount else 0.0
                            Text(String.format("%.0f%%", rate), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.secondary)
                            Text("Rate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Details (Top 10)", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(12.dp))

                    auditResults.take(10).forEach { result ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    result.filePath.substringAfterLast("\\"),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    result.driftType,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when (result.driftType) {
                                        "COVARIATE_DRIFT" -> Color.Red
                                        "CONCEPT_DRIFT" -> Color(0xFFFFA500)
                                        else -> Color.Green
                                    }
                                )
                            }
                            Text(
                                String.format("%.2f", result.driftScore),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.background(
                                    when {
                                        result.driftScore > 0.5 -> Color(0xFFFFCDD2)
                                        result.driftScore > 0.25 -> Color(0xFFFFE0B2)
                                        else -> Color(0xFFC8E6C9)
                                    }
                                ).padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (auditResults.size > 10) {
                        Text(
                            "+${auditResults.size - 10} more",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            Button(onClick = {}, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text("Export Audit Report (PDF)")
            }
        }
    }
}

@Composable
fun ModelComparisonTab(viewModel: EnterpriseViewModel, uploadViewModel: UploadViewModel) {
    val isComparing by viewModel.isComparingModels.collectAsState()
    val result by viewModel.comparisonResult.collectAsState()
    val referenceFile by uploadViewModel.selectedReferenceFile.collectAsState()

    var model1Path by remember { mutableStateOf("models/v1.0.onnx") }
    var model2Path by remember { mutableStateOf("models/v2.3.onnx") }
    var testDataPath by remember { mutableStateOf("desktop app/demo_data/current_data_WITH_DRIFT.csv") }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())
    ) {
        Text("Model Version Comparison", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 16.dp))
        Text(
            "Compare two model versions to detect drift and make safe rollback decisions.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        OutlinedTextField(
            value = model1Path,
            onValueChange = { model1Path = it },
            label = { Text("Model v1 Path") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            trailingIcon = { IconButton(onClick = {}) { Icon(Icons.Default.Edit, contentDescription = "Browse") } }
        )

        OutlinedTextField(
            value = model2Path,
            onValueChange = { model2Path = it },
            label = { Text("Model v2 Path") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            trailingIcon = { IconButton(onClick = {}) { Icon(Icons.Default.Edit, contentDescription = "Browse") } }
        )

        OutlinedTextField(
            value = testDataPath,
            onValueChange = { testDataPath = it },
            label = { Text("Test Dataset Path") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            trailingIcon = { IconButton(onClick = {}) { Icon(Icons.Default.Edit, contentDescription = "Browse") } }
        )

        Button(
            onClick = {
                viewModel.compareModelVersions(model1Path, model2Path, testDataPath, referenceFile)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isComparing
        ) {
            if (isComparing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp).padding(end = 8.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            Text("Compare Models")
        }

        Spacer(modifier = Modifier.height(16.dp))

        result?.let { r ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Comparison Results", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(12.dp))

                    ComparisonMetricRow(
                        label = "Disagreement Rate",
                        value = String.format("%.2f%%", r.disagreementPercentage),
                        valueColor = if (r.disagreementPercentage > 10) Color(0xFFD32F2F) else Color(0xFF388E3C)
                    )
                    ComparisonMetricRow(
                        label = "Drift Score (v1 → v2)",
                        value = String.format("%.3f", r.driftScore),
                        valueColor = MaterialTheme.colorScheme.secondary
                    )
                    ComparisonMetricRow(
                        label = "v1 Avg Confidence",
                        value = String.format("%.2f%%", r.v1AverageConfidence * 100),
                        valueColor = MaterialTheme.colorScheme.secondary
                    )
                    ComparisonMetricRow(
                        label = "v2 Avg Confidence",
                        value = String.format("%.2f%%", r.v2AverageConfidence * 100),
                        valueColor = MaterialTheme.colorScheme.secondary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = if (r.shouldRollback) Color(0xFFFFEBEE) else Color(0xFFE8F5E9))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                if (r.shouldRollback) "Rollback Recommended" else "Safe to Deploy",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (r.shouldRollback) Color(0xFFD32F2F) else Color(0xFF388E3C)
                            )
                            Text(r.recommendation, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ComparisonMetricRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.secondary
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}

@Composable
fun JuryProofTab() {
    val scope = rememberCoroutineScope()
    var selectedDemo by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("scale") }
    var outputText by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Jury-Proof Demos", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        
        // Demo selector
        listOf(
            "scale" to "1️⃣  Scale Benchmark (Parallel Processing)",
            "latency" to "2️⃣  Latency: Desktop vs Cloud",
            "memory" to "3️⃣  Memory Analysis",
            "enterprise" to "4️⃣  Enterprise Workflow"
        ).forEach { (key, label) ->
            Button(
                onClick = { 
                    selectedDemo = key 
                    outputText = ""
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                colors = if (selectedDemo == key) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors()
            ) {
                Text(label)
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Results card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                when (selectedDemo) {
                    "scale" -> {
                        Text("Parallel Batch Processing", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Button(onClick = {
                            scope.launch {
                                outputText = ParallelBatchProcessorUIBridge().runDemoBenchmark()
                            }
                        }) {
                            Text("Run 100-File Benchmark")
                        }
                    }
                    "latency" -> {
                        Text("Performance Metrics", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Button(onClick = {
                            val metrics = OfflinePerformanceBenchmark.benchmarkAllOperations()
                            outputText = OfflinePerformanceBenchmark.prettyPrint(metrics)
                        }) {
                            Text("Run All Benchmarks")
                        }
                    }
                    "memory" -> {
                        Text("Memory Usage Analysis", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Button(onClick = {
                            outputText = MemoryAnalysis.analyzeMemoryUsage()
                        }) {
                            Text("Analyze Memory")
                        }
                    }
                    "enterprise" -> {
                        Text("Enterprise Automation", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(12.dp))
                        outputText = EnterpriseAutomationShowcase.getDemoWorkflow()
                    }
                }
                
                if (outputText.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = outputText,
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp).fillMaxWidth()
                    )
                }
            }
        }
    }
}
