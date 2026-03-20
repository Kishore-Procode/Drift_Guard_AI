package com.driftdetector.desktop.presentation.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.driftdetector.desktop.core.export.ExportManager
import com.driftdetector.desktop.core.realtime.WebSocketMonitor
import com.driftdetector.desktop.core.settings.SettingsManager
import com.driftdetector.desktop.domain.model.*
import com.driftdetector.desktop.presentation.viewmodel.*

enum class NavigationTab {
    DASHBOARD, UPLOAD, DRIFT_DETECTION, PATCH_MANAGEMENT, INSTANT_FIX,
    EXPLAINABILITY, WHAT_IF_LAB, BATCH_AUDITS, MODEL_VERSIONS, JURY_DEMO,
    EVALUATION, SIMULATION, MONITORING, AI_ASSISTANT, ANALYTICS, SETTINGS
}

@Composable
fun MainScreen(
    uploadViewModel: UploadViewModel,
    driftViewModel: DriftDetectionViewModel,
    patchViewModel: PatchGenerationViewModel,
    modelViewModel: ModelManagementViewModel,
    aiViewModel: AIAssistantViewModel,
    evalViewModel: EvaluationViewModel,
    settingsManager: SettingsManager,
    wsMonitor: WebSocketMonitor,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    var currentTab by remember { mutableStateOf(NavigationTab.DASHBOARD) }
    val enterpriseViewModel = remember { EnterpriseViewModel() }
    val connectionState by wsMonitor.connectionState.collectAsState()

    val driftResults by driftViewModel.driftResults.collectAsState()
    val patches by patchViewModel.generatedPatches.collectAsState()
    val loading by driftViewModel.loading.collectAsState()
    val error by driftViewModel.error.collectAsState()
    val success by driftViewModel.success.collectAsState()

    Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Sidebar
        NavigationSidebar(
            currentTab = currentTab,
            onTabSelected = { currentTab = it },
            connectionState = connectionState,
            isDarkTheme = isDarkTheme,
            onToggleTheme = onToggleTheme,
            modifier = Modifier.width(240.dp)
        )

        Column(modifier = Modifier.fillMaxSize().weight(1f)) {
            // Top bar
            TopHeaderBar(currentTab, isDarkTheme, onToggleTheme)

            // Feedback banners
            error?.let { msg ->
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Error, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(msg, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                        IconButton(onClick = { driftViewModel.clearMessages() }) {
                            Icon(Icons.Filled.Close, "Dismiss")
                        }
                    }
                }
            }
            success?.let { msg ->
                Surface(color = Color(0xFFE8F5E9), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF2E7D32))
                        Spacer(Modifier.width(8.dp))
                        Text(msg, color = Color(0xFF1B5E20), modifier = Modifier.weight(1f))
                        IconButton(onClick = { driftViewModel.clearMessages() }) {
                            Icon(Icons.Filled.Close, "Dismiss")
                        }
                    }
                }
            }

            // Loading indicator
            if (loading) { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }

            // Content
            Box(modifier = Modifier.fillMaxSize().weight(1f).padding(16.dp)) {
                when (currentTab) {
                    NavigationTab.DASHBOARD -> DashboardContent(driftResults, patches, evalViewModel)
                    NavigationTab.UPLOAD -> UploadContent(uploadViewModel)
                    NavigationTab.DRIFT_DETECTION -> DriftDetectionContent(driftViewModel, uploadViewModel, evalViewModel)
                    NavigationTab.PATCH_MANAGEMENT -> PatchManagementContent(patchViewModel, driftViewModel, uploadViewModel, evalViewModel)
                    NavigationTab.INSTANT_FIX -> InstantDriftFixContent(driftViewModel, patchViewModel, uploadViewModel, evalViewModel)
                    NavigationTab.EXPLAINABILITY -> ExplainabilityTab(enterpriseViewModel, driftViewModel, uploadViewModel)
                    NavigationTab.WHAT_IF_LAB -> ScenarioLabTab(enterpriseViewModel, uploadViewModel)
                    NavigationTab.BATCH_AUDITS -> BatchAuditTab(enterpriseViewModel, uploadViewModel)
                    NavigationTab.MODEL_VERSIONS -> ModelComparisonTab(enterpriseViewModel, uploadViewModel)
                    NavigationTab.JURY_DEMO -> JuryProofTab()
                    NavigationTab.EVALUATION -> EvaluationContent(evalViewModel, driftViewModel, patchViewModel, uploadViewModel)
                    NavigationTab.SIMULATION -> SimulationContent(evalViewModel, uploadViewModel)
                    NavigationTab.MONITORING -> MonitoringContent(evalViewModel, uploadViewModel)
                    NavigationTab.AI_ASSISTANT -> AIAssistantContent(aiViewModel, driftResults, patches)
                    NavigationTab.ANALYTICS -> AnalyticsContent(driftResults, evalViewModel)
                    NavigationTab.SETTINGS -> SettingsContent(settingsManager, isDarkTheme, onToggleTheme)
                }
            }
        }
    }
}

// ==================== SIDEBAR ====================

@Composable
private fun NavigationSidebar(
    currentTab: NavigationTab, onTabSelected: (NavigationTab) -> Unit,
    connectionState: WebSocketMonitor.ConnectionState, isDarkTheme: Boolean,
    onToggleTheme: () -> Unit, modifier: Modifier = Modifier
) {
    val navItems = listOf(
        NavigationTab.DASHBOARD to ("Dashboard" to Icons.Filled.Dashboard),
        NavigationTab.UPLOAD to ("Upload" to Icons.Filled.CloudUpload),
        NavigationTab.DRIFT_DETECTION to ("Drift Analysis" to Icons.Filled.TrendingUp),
        NavigationTab.PATCH_MANAGEMENT to ("Patches" to Icons.Filled.Build),
        NavigationTab.INSTANT_FIX to ("Instant Fix" to Icons.Filled.FlashOn),
        NavigationTab.EXPLAINABILITY to ("Explainability" to Icons.Filled.Insights),
        NavigationTab.WHAT_IF_LAB to ("What-If Lab" to Icons.Filled.Science),
        NavigationTab.BATCH_AUDITS to ("Batch Audits" to Icons.Filled.FolderSpecial),
        NavigationTab.MODEL_VERSIONS to ("Model Versions" to Icons.Filled.CompareArrows),
        NavigationTab.JURY_DEMO to ("Jury Demo" to Icons.Filled.Star),
        NavigationTab.EVALUATION to ("Evaluation" to Icons.Filled.Assessment),
        NavigationTab.SIMULATION to ("Simulation" to Icons.Filled.Science),
        NavigationTab.MONITORING to ("Auto Monitor" to Icons.Filled.MonitorHeart),
        NavigationTab.AI_ASSISTANT to ("AI Assistant" to Icons.Filled.SmartToy),
        NavigationTab.ANALYTICS to ("Analytics" to Icons.Filled.BarChart),
        NavigationTab.SETTINGS to ("Settings" to Icons.Filled.Settings)
    )

    Column(
        modifier = modifier.fillMaxHeight().background(MaterialTheme.colorScheme.primaryContainer).padding(8.dp)
    ) {
        // Logo
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(8.dp))
            Text("DriftGuardAI", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            navItems.forEach { (tab, labelIcon) ->
                val (label, icon) = labelIcon
                val isSelected = currentTab == tab
                val bgColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(bgColor, RoundedCornerShape(8.dp))
                        .clickable { onTabSelected(tab) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(icon, label, Modifier.size(20.dp),
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(label, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp)
                }
            }
        }

        // Theme toggle
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onToggleTheme() }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(if (isDarkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode, "Toggle theme", Modifier.size(20.dp))
            Text(if (isDarkTheme) "Light Mode" else "Dark Mode", fontSize = 13.sp)
        }

        // Connection status
        ElevatedCard(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val (color, text) = when (connectionState) {
                    WebSocketMonitor.ConnectionState.CONNECTED -> Color(0xFF00C853) to "Connected"
                    WebSocketMonitor.ConnectionState.CONNECTING -> Color(0xFFFFAB00) to "Connecting..."
                    WebSocketMonitor.ConnectionState.ERROR -> Color(0xFFFF1744) to "Error"
                    WebSocketMonitor.ConnectionState.DISCONNECTED -> Color(0xFF9E9E9E) to "Offline"
                }
                Box(Modifier.size(8.dp).background(color, RoundedCornerShape(50)))
                Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun TopHeaderBar(currentTab: NavigationTab, isDarkTheme: Boolean, onToggleTheme: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().height(56.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(when (currentTab) {
                NavigationTab.DASHBOARD -> "Dashboard"
                NavigationTab.UPLOAD -> "Data & Model Upload"
                NavigationTab.DRIFT_DETECTION -> "Drift Detection Analysis"
                NavigationTab.PATCH_MANAGEMENT -> "Patch Management"
                NavigationTab.INSTANT_FIX -> "⚡ Instant Drift Fix"
                NavigationTab.EXPLAINABILITY -> "🎯 Why Did Drift Happen?"
                NavigationTab.WHAT_IF_LAB -> "🎪 What-If Lab"
                NavigationTab.BATCH_AUDITS -> "📊 Batch Audit Scheduler"
                NavigationTab.MODEL_VERSIONS -> "🔀 Model Version Comparison"
                NavigationTab.JURY_DEMO -> "🏆 Jury-Proof Demo"
                NavigationTab.EVALUATION -> "📊 Model Evaluation"
                NavigationTab.SIMULATION -> "🔬 Drift Simulation"
                NavigationTab.MONITORING -> "🔄 Continuous Monitoring"
                NavigationTab.AI_ASSISTANT -> "🤖 AI Assistant (DriftBot)"
                NavigationTab.ANALYTICS -> "Analytics & Insights"
                NavigationTab.SETTINGS -> "Settings"
            }, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

// ==================== DASHBOARD ====================

@Composable
private fun DashboardContent(driftResults: List<DriftResult>, patches: List<Patch>, evalViewModel: EvaluationViewModel) {
    val evaluation by evalViewModel.evaluation.collectAsState()
    val deployedVersions by evalViewModel.deployedVersions.collectAsState()

    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("System Overview", fontSize = 18.sp, fontWeight = FontWeight.Bold)

        // Metric Cards
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MetricCard("Total Analyses", driftResults.size.toString(), Icons.Filled.Assessment, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            MetricCard("Drift Detected", driftResults.count { it.isDriftDetected }.toString(), Icons.Filled.Warning, MaterialTheme.colorScheme.error, Modifier.weight(1f))
            MetricCard("Patches Generated", patches.size.toString(), Icons.Filled.Build, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
        }

        // Drift Reduction + Confidence (Items #2, #9)
        evaluation?.let { eval ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val reductionColor = if (eval.isValid) Color(0xFF00C853) else Color(0xFFFF1744)
                MetricCard("Drift Reduced", "${"%,.1f".format(eval.driftReductionPercent)}%", Icons.Filled.TrendingDown, reductionColor, Modifier.weight(1f))
                MetricCard("Confidence", "${"%,.0f".format(eval.confidenceScore * 100)}%", Icons.Filled.VerifiedUser, Color(0xFF3949AB), Modifier.weight(1f))
                MetricCard("Validation", if (eval.isValid) "PASS" else "FAIL", Icons.Filled.Security, reductionColor, Modifier.weight(1f))
            }
        }

        // Deployment status
        if (deployedVersions.isNotEmpty()) {
            val active = deployedVersions.lastOrNull { it.status == "ACTIVE" }
            active?.let {
                MetricCard("Active Version", it.version, Icons.Filled.Rocket, Color(0xFF3949AB), Modifier.fillMaxWidth())
            }
        }

        // Avg drift score
        val avgScore = driftResults.takeIf { it.isNotEmpty() }?.map { it.overallDriftScore }?.average() ?: 0.0
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MetricCard("Avg Drift Score", "%.3f".format(avgScore), Icons.Filled.ShowChart, MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
            val criticalCount = driftResults.count { it.overallDriftScore > 0.5 }
            MetricCard("Critical Alerts", criticalCount.toString(), Icons.Filled.Error, Color(0xFFFF1744), Modifier.weight(1f))
        }

        // Drift Timeline Chart
        val timeline by evalViewModel.driftTimeline.collectAsState()
        val dataToPlot = timeline.ifEmpty { 
            driftResults.mapIndexed { i, r -> EvaluationViewModel.TimelineEntry("Run $i", r.overallDriftScore, r.driftType.name) } 
        }
        if (dataToPlot.isNotEmpty()) {
            Text("Continuous Drift Timeline (Time-Based)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            ElevatedCard(Modifier.fillMaxWidth().height(200.dp)) {
                DriftTimelineChart(dataToPlot, Modifier.fillMaxSize().padding(16.dp))
            }
        }

        // Recent drift results
        Text("Recent Drift Events", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        if (driftResults.isEmpty()) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Text("No drift analyses yet. Upload data and run detection.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.outline)
            }
        } else {
            driftResults.take(5).forEach { result -> DriftResultCard(result) }
        }
    }
}

// ==================== UPLOAD ====================

@Composable
private fun UploadContent(uploadViewModel: UploadViewModel) {
    val models by uploadViewModel.uploadedModels.collectAsState()
    val files by uploadViewModel.uploadedFiles.collectAsState()
    val refFile by uploadViewModel.selectedReferenceFile.collectAsState()
    val curFile by uploadViewModel.selectedCurrentFile.collectAsState()

    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Upload Models & Data", fontSize = 18.sp, fontWeight = FontWeight.Bold)

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Step 1: Upload ML Model", fontWeight = FontWeight.SemiBold)
                Button(onClick = { uploadViewModel.pickModelFile() }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.CloudUpload, null); Spacer(Modifier.width(8.dp))
                    Text("Choose Model File (.tflite, .onnx, .h5, .pt)")
                }
                models.lastOrNull()?.let { Text("✅ ${it.name} (${it.framework})", color = Color(0xFF2E7D32)) }
            }
        }

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Step 2: Upload Reference Data", fontWeight = FontWeight.SemiBold)
                Button(onClick = { uploadViewModel.pickReferenceDataFile() }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.CloudUpload, null); Spacer(Modifier.width(8.dp))
                    Text("Choose Reference Data (.csv, .json, .tsv, .psv, .dat)")
                }
                refFile?.let { Text("✅ ${it.name}", color = Color(0xFF2E7D32)) }
            }
        }

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Step 3: Upload Current/Production Data", fontWeight = FontWeight.SemiBold)
                Button(onClick = { uploadViewModel.pickCurrentDataFile() }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.CloudUpload, null); Spacer(Modifier.width(8.dp))
                    Text("Choose Current Data (.csv, .json, .tsv, .psv, .dat)")
                }
                curFile?.let { Text("✅ ${it.name}", color = Color(0xFF2E7D32)) }
            }
        }

        if (models.isNotEmpty()) {
            Text("Loaded Models (${models.size})", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            models.forEach { m -> ModelCard(m) }
        }
    }
}

// ==================== DRIFT DETECTION ====================

@Composable
private fun DriftDetectionContent(driftViewModel: DriftDetectionViewModel, uploadViewModel: UploadViewModel, evalViewModel: EvaluationViewModel) {
    val refFile by uploadViewModel.selectedReferenceFile.collectAsState()
    val curFile by uploadViewModel.selectedCurrentFile.collectAsState()
    val results by driftViewModel.driftResults.collectAsState()
    val current by driftViewModel.currentDriftResult.collectAsState()
    val loading by driftViewModel.loading.collectAsState()

    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Drift Detection Analysis", fontSize = 18.sp, fontWeight = FontWeight.Bold)

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Run Local Drift Detection", fontWeight = FontWeight.SemiBold)
                Text("Reference: ${refFile?.name ?: "Not selected"}", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                Text("Current: ${curFile?.name ?: "Not selected"}", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)

                Button(
                    onClick = {
                        if (refFile != null && curFile != null) {
                            driftViewModel.detectDriftLocal(refFile!!, curFile!!)
                        }
                    },
                    enabled = refFile != null && curFile != null && !loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else { Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Detect Drift") }
                }

                if (refFile == null || curFile == null) {
                    Text("💡 Go to Upload tab first to select reference and current data files.", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                }
            }
        }

        // Current result
        current?.let { result ->
            Text("Latest Result", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            DriftDetailCard(result)
        }

        // Export
        if (results.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { ExportManager().exportDriftResults(results, ExportManager.ExportFormat.JSON) }) {
                    Icon(Icons.Filled.Save, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Export JSON")
                }
                OutlinedButton(onClick = { ExportManager().exportDriftResults(results, ExportManager.ExportFormat.CSV) }) {
                    Icon(Icons.Filled.Save, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Export CSV")
                }
            }
        }

        // History
        if (results.isNotEmpty()) {
            Text("Detection History (${results.size})", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            results.forEach { DriftResultCard(it) }
        }
    }
}

// ==================== PATCH MANAGEMENT ====================

@Composable
private fun PatchManagementContent(patchViewModel: PatchGenerationViewModel, driftViewModel: DriftDetectionViewModel, uploadViewModel: UploadViewModel, evalViewModel: EvaluationViewModel) {
    val patches by patchViewModel.generatedPatches.collectAsState()
    val currentDrift by driftViewModel.currentDriftResult.collectAsState()
    val refFile by uploadViewModel.selectedReferenceFile.collectAsState()
    val curFile by uploadViewModel.selectedCurrentFile.collectAsState()
    val loading by patchViewModel.loading.collectAsState()

    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Patch Management", fontSize = 18.sp, fontWeight = FontWeight.Bold)

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Generate Patches from Latest Drift", fontWeight = FontWeight.SemiBold)
                Button(
                    onClick = {
                        if (currentDrift != null && refFile != null && curFile != null)
                            patchViewModel.generatePatchesLocal(currentDrift!!, refFile!!, curFile!!)
                    },
                    enabled = currentDrift != null && !loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else { Icon(Icons.Filled.Build, null); Spacer(Modifier.width(8.dp)); Text("Generate Patches") }
                }
                if (currentDrift == null) Text("Run drift detection first.", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
            }
        }

        // Actions
        if (patches.isNotEmpty()) {
            // Apply All button
            val hasCreated = patches.any { it.status == "CREATED" }
            Button(
                onClick = { patchViewModel.applyAllPatchesLocal() },
                enabled = hasCreated,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853))
            ) {
                Icon(Icons.Filled.DoneAll, null); Spacer(Modifier.width(8.dp))
                Text("✅ Apply All Patches", fontWeight = FontWeight.Bold)
            }

        }

        // Patch list
        if (patches.isEmpty()) {
            Text("No patches generated yet.", color = MaterialTheme.colorScheme.outline)
        } else {
            Text("Generated Patches (${patches.size})", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            patches.forEach { PatchCard(it, patchViewModel) }
        }

        // Export only after all patches applied
        val allApplied = patches.isNotEmpty() && patches.all { it.status == "APPLIED" }
        if (allApplied) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("✅ All patches applied! Export your fixes:", fontWeight = FontWeight.SemiBold, color = Color(0xFF2E7D32))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { ExportManager().exportPatches(patches, ExportManager.ExportFormat.JSON) }) {
                            Icon(Icons.Filled.Save, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Export JSON")
                        }
                        Button(onClick = { ExportManager().exportPatches(patches, ExportManager.ExportFormat.CSV) }) {
                            Icon(Icons.Filled.Save, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Export CSV")
                        }
                        Button(onClick = { ExportManager().exportPatches(patches, ExportManager.ExportFormat.TEXT) }) {
                            Icon(Icons.Filled.Save, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Export Text")
                        }
                    }
                }
            }
        }
    }
}

// ==================== INSTANT DRIFT FIX ====================

@Composable
private fun InstantDriftFixContent(driftViewModel: DriftDetectionViewModel, patchViewModel: PatchGenerationViewModel, uploadViewModel: UploadViewModel, evalViewModel: EvaluationViewModel) {
    val refFile by uploadViewModel.selectedReferenceFile.collectAsState()
    val curFile by uploadViewModel.selectedCurrentFile.collectAsState()
    val currentDrift by driftViewModel.currentDriftResult.collectAsState()
    val patches by patchViewModel.generatedPatches.collectAsState()
    val driftLoading by driftViewModel.loading.collectAsState()
    val patchLoading by patchViewModel.loading.collectAsState()

    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("⚡ Instant Drift Fix", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("One-click workflow: Select files → Detect → Auto-patch", color = MaterialTheme.colorScheme.outline)

        // Step 1: Select files
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("1️⃣ Select Data Files", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { uploadViewModel.pickReferenceDataFile() }, Modifier.weight(1f)) {
                        Text(refFile?.name ?: "Reference Data", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Button(onClick = { uploadViewModel.pickCurrentDataFile() }, Modifier.weight(1f)) {
                        Text(curFile?.name ?: "Current Data", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        // Step 2: Run
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("2️⃣ Detect & Fix", fontWeight = FontWeight.SemiBold)
                Button(
                    onClick = {
                        if (refFile != null && curFile != null) {
                            driftViewModel.detectDriftLocal(refFile!!, curFile!!)
                        }
                    },
                    enabled = refFile != null && curFile != null && !driftLoading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    if (driftLoading) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White) }
                    else { Icon(Icons.Filled.FlashOn, null); Spacer(Modifier.width(8.dp)); Text("🚀 Run Instant Fix", fontWeight = FontWeight.Bold) }
                }
            }
        }

        // Step 3: Auto-generate patches when drift detected
        LaunchedEffect(currentDrift) {
            if (currentDrift != null && currentDrift!!.isDriftDetected && refFile != null && curFile != null) {
                patchViewModel.generatePatchesLocal(currentDrift!!, refFile!!, curFile!!)
            }
        }

        // Results
        currentDrift?.let { result ->
            Text("3️⃣ Results", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            DriftDetailCard(result)
        }

        if (patches.isNotEmpty()) {
            Text("4️⃣ Generated Patches (${patches.size})", fontSize = 16.sp, fontWeight = FontWeight.Bold)

            // Fix All button
            val hasCreated = patches.any { it.status == "CREATED" }
            Button(
                onClick = { patchViewModel.applyAllPatchesLocal() },
                enabled = hasCreated,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853))
            ) {
                Icon(Icons.Filled.DoneAll, null); Spacer(Modifier.width(8.dp))
                Text("⚡ Fix All — Apply All Patches", fontWeight = FontWeight.Bold)
            }

            patches.forEach { PatchCard(it, patchViewModel) }

            // Export only after all patches applied
            val allFixed = patches.all { it.status == "APPLIED" }
            if (allFixed) {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("⚡ All fixed! Export your patches:", fontWeight = FontWeight.SemiBold, color = Color(0xFF2E7D32))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { ExportManager().exportPatches(patches, ExportManager.ExportFormat.JSON) }) {
                                Icon(Icons.Filled.Save, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Export JSON")
                            }
                            Button(onClick = { ExportManager().exportPatches(patches, ExportManager.ExportFormat.CSV) }) {
                                Icon(Icons.Filled.Save, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Export CSV")
                            }
                            Button(onClick = { ExportManager().exportPatches(patches, ExportManager.ExportFormat.TEXT) }) {
                                Icon(Icons.Filled.Save, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Export Text")
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== AI ASSISTANT ====================

@Composable
private fun AIAssistantContent(aiViewModel: AIAssistantViewModel, driftResults: List<DriftResult>, patches: List<Patch>) {
    val messages by aiViewModel.messages.collectAsState()
    val runtimeState by aiViewModel.runtimeState.collectAsState()
    val models by aiViewModel.models.collectAsState()
    val activeModelId by aiViewModel.activeModelId.collectAsState()
    val downloadProgress by aiViewModel.downloadProgress.collectAsState()
    val downloadIssues by aiViewModel.downloadIssues.collectAsState()
    val isGenerating by aiViewModel.isGenerating.collectAsState()
    var inputText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        ElevatedCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Local AI Runtime", fontWeight = FontWeight.SemiBold)
                Text(runtimeState.statusMessage, fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                Text(
                    "Available RAM: ${runtimeState.availableMemoryMb} MB | Status: ${if (runtimeState.isReady) "Ready" else if (runtimeState.isInitializing) "Loading" else "Error"}",
                    fontSize = 12.sp,
                    color = if (runtimeState.isLowMemory) Color(0xFFB71C1C) else MaterialTheme.colorScheme.outline,
                )
                runtimeState.lastError?.let { err ->
                    Text("Error: $err", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { aiViewModel.initializeLocalAI() }) {
                        Text("Initialize Local AI")
                    }
                    OutlinedButton(onClick = { aiViewModel.reloadRuntime() }) {
                        Text("Reload Runtime")
                    }
                    OutlinedButton(onClick = { aiViewModel.refreshModels() }) {
                        Text("Refresh Models")
                    }
                    OutlinedButton(onClick = { aiViewModel.unloadModel() }) {
                        Text("Unload Model")
                    }
                    if (isGenerating) {
                        OutlinedButton(onClick = { aiViewModel.cancelGeneration() }) {
                            Text("Stop")
                        }
                    }
                }

                if (models.isNotEmpty()) {
                    Text("Local Models", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    models.take(4).forEach { model ->
                        val progress = downloadProgress[model.id] ?: 0f
                        val isDownloaded = model.isDownloaded
                        val isLoaded = activeModelId == model.id
                        val sizeMb = aiViewModel.estimatedModelSizeMb(model.id)
                        val ramMb = aiViewModel.estimatedModelRamMb(model.id)
                        val status = when {
                            isLoaded -> "Loaded"
                            isDownloaded -> "Downloaded"
                            progress > 0f && progress < 1f -> "Downloading ${(progress * 100).toInt()}%"
                            else -> "Not downloaded"
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(model.name, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Text(status, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                if (sizeMb > 0) {
                                    Text("Size: ${sizeMb}MB | Est. RAM: ${ramMb}MB", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                                }
                                downloadIssues[model.id]?.let { issue ->
                                    Text(issue, fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
                                }
                                if (progress > 0f && progress < 1f) {
                                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                                }
                            }
                            if (!isDownloaded) {
                                OutlinedButton(onClick = { aiViewModel.downloadModel(model.id) }) {
                                    Text("Download", fontSize = 11.sp)
                                }
                            } else if (!isLoaded) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(onClick = { aiViewModel.loadModel(model.id) }) {
                                        Text("Load", fontSize = 11.sp)
                                    }
                                    OutlinedButton(onClick = { aiViewModel.deleteModel(model.id) }) {
                                        Text("Delete", fontSize = 11.sp)
                                    }
                                }
                            } else {
                                OutlinedButton(onClick = { aiViewModel.deleteModel(model.id) }) {
                                    Text("Delete", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Chat messages
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(bottom = 8.dp), reverseLayout = true) {
            items(messages.reversed()) { msg ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (msg.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.widthIn(max = 500.dp)
                    ) {
                        Text(
                            msg.text,
                            modifier = Modifier.padding(12.dp),
                            color = if (msg.isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        // Quick actions
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("/help", "/status", "/drift", "/models", "/tips").forEach { cmd ->
                OutlinedButton(onClick = { aiViewModel.sendMessage(cmd, driftResults, patches) }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                    Text(cmd, fontSize = 12.sp)
                }
            }
        }

        // Input
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = inputText, onValueChange = { inputText = it },
                placeholder = { Text("Ask DriftBot anything (offline)...") },
                modifier = Modifier.weight(1f), singleLine = true
            )
            Button(onClick = {
                if (inputText.isNotBlank()) { aiViewModel.sendMessage(inputText, driftResults, patches); inputText = "" }
            }) { Icon(Icons.Filled.Send, "Send") }
        }
    }
}

// ==================== ANALYTICS ====================

@Composable
private fun AnalyticsContent(driftResults: List<DriftResult>, evalViewModel: EvaluationViewModel) {
    val featureImpacts by evalViewModel.featureImpacts.collectAsState()
    val rootCause by evalViewModel.rootCause.collectAsState()
    val latest = driftResults.firstOrNull()

    // Auto-run analysis
    LaunchedEffect(latest) {
        latest?.let {
            evalViewModel.rankFeatures(it)
            evalViewModel.analyzeRootCause(it)
        }
    }
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Analytics & Insights", fontSize = 18.sp, fontWeight = FontWeight.Bold)

        // Root Cause Analysis (Item #14)
        rootCause?.let { cause ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Root Cause Engine", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Text(cause.primaryCause, fontSize = 16.sp, color = MaterialTheme.colorScheme.error)
                    Text("Severity: ${cause.severity}", fontWeight = FontWeight.SemiBold)
                    Text("Recommendations:", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                    cause.recommendations.forEach {
                        Row { Text("• ", color = MaterialTheme.colorScheme.primary); Text(it, fontSize = 14.sp) }
                    }
                }
            }
        }

        // Feature Impact Ranking (Item #5)
        if (featureImpacts.isNotEmpty()) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Top Contributors (Feature Impact)", fontWeight = FontWeight.SemiBold)
                    featureImpacts.take(5).forEachIndexed { i, impact ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}. ${impact.featureName}", Modifier.width(160.dp), fontWeight = FontWeight.Medium)
                            LinearProgressIndicator(
                                progress = { (impact.impactPercent / 100).toFloat() },
                                modifier = Modifier.weight(1f).height(12.dp),
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("${"%.0f".format(impact.impactPercent)}%", modifier = Modifier.width(40.dp), fontWeight = FontWeight.Bold)
                        }
                        Text("Cause: ${impact.rootCause}", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(start = 16.dp, bottom = 4.dp))
                    }
                }
            }
        }

        if (driftResults.isEmpty()) {
            ElevatedCard(Modifier.fillMaxWidth()) { Text("No analytics data yet. Run drift detection first.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.outline) }
            return
        }

        // Drift Type Distribution
        val typeCounts = driftResults.groupBy { it.driftType }.mapValues { it.value.size }
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text("Drift Type Distribution", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                typeCounts.forEach { (type, count) ->
                    val pct = count.toFloat() / driftResults.size
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(type.name, Modifier.width(140.dp), fontSize = 13.sp)
                        LinearProgressIndicator(progress = { pct }, modifier = Modifier.weight(1f).height(12.dp), trackColor = MaterialTheme.colorScheme.surfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text("$count (${"%.0f".format(pct * 100)}%)", fontSize = 12.sp)
                    }
                }
            }
        }

        // Feature Drift Heatmap (latest)
        val latest = driftResults.firstOrNull()
        if (latest != null && latest.featureDrifts.isNotEmpty()) {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("Feature Drift Heatmap (Latest)", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    latest.featureDrifts.forEach { feat ->
                        val color = when {
                            feat.psiScore > 0.5 -> Color(0xFFFF1744)
                            feat.psiScore > 0.25 -> Color(0xFFFF9100)
                            feat.psiScore > 0.1 -> Color(0xFFFFC107)
                            else -> Color(0xFF00C853)
                        }
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(feat.featureName, Modifier.width(120.dp), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Box(Modifier.weight(1f).height(16.dp).background(color.copy(alpha = 0.3f), RoundedCornerShape(4.dp))) {
                                Box(Modifier.fillMaxHeight().fillMaxWidth(fraction = (feat.psiScore / 1.0).toFloat().coerceIn(0f, 1f)).background(color, RoundedCornerShape(4.dp)))
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("%.3f".format(feat.psiScore), fontSize = 11.sp, fontWeight = if (feat.isDrifted) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
        }

        // Alerts summary
        val critical = driftResults.count { it.overallDriftScore > 0.5 }
        val warning = driftResults.count { it.overallDriftScore in 0.2..0.5 }
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text("Alert Summary", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(critical.toString(), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF1744))
                        Text("Critical", fontSize = 12.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(warning.toString(), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9100))
                        Text("Warning", fontSize = 12.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(driftResults.count { !it.isDriftDetected }.toString(), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00C853))
                        Text("Normal", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ==================== EVALUATION ====================

@Composable
private fun EvaluationContent(evalViewModel: EvaluationViewModel, driftViewModel: DriftDetectionViewModel, patchViewModel: PatchGenerationViewModel, uploadViewModel: UploadViewModel) {
    val evaluation by evalViewModel.evaluation.collectAsState()
    val refFile by uploadViewModel.selectedReferenceFile.collectAsState()
    val curFile by uploadViewModel.selectedCurrentFile.collectAsState()
    val driftResult by driftViewModel.currentDriftResult.collectAsState()
    val patches by patchViewModel.generatedPatches.collectAsState()
    val recommendation by evalViewModel.recommendation.collectAsState()
    val loading by evalViewModel.loading.collectAsState()

    LaunchedEffect(patches, driftResult) {
        if (patches.isNotEmpty() && driftResult != null) {
            evalViewModel.selectBestPatch(patches, driftResult!!)
        }
    }

    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Model Evaluation Module", fontSize = 18.sp, fontWeight = FontWeight.Bold)

        if (driftResult == null || patches.isEmpty() || refFile == null || curFile == null) {
            ElevatedCard(Modifier.fillMaxWidth()) { Text("Detection & patching required first.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.outline) }
            return
        }

        // Smart Recommendation (Item #3)
        recommendation?.let { rec ->
            ElevatedCard(Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🤖 Smart Patch Recommendation", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("Best Strategy: ${rec.bestPatch.patchType}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(rec.reason, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { evalViewModel.evaluatePatches(refFile!!, curFile!!, listOf(rec.bestPatch), driftResult!!) }, enabled = !loading) {
                        Text(if (loading) "Evaluating..." else "Evaluate Selected Patch")
                    }
                }
            }
        }

        // Run Evaluation
        Button(
            onClick = { evalViewModel.evaluatePatches(refFile!!, curFile!!, patches, driftResult!!) },
            enabled = !loading, modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (loading) "Running Pre/Post Evaluation..." else "🧪 Evaluate All Patches Impact")
        }

        // Evaluation Results (Items #1, #2, #9)
        evaluation?.let { eval ->
            Text("Evaluation Results", fontSize = 16.sp, fontWeight = FontWeight.Bold)

            // Huge Metric Card for Reduction
            val bgColor = if (eval.isValid) Color(0xFFF1F8E9) else Color(0xFFFFEBEE)
            val textColor = if (eval.isValid) Color(0xFF1B5E20) else Color(0xFFB71C1C)
            val icon = if (eval.isValid) "✅" else "❌"
            val titleText = if (eval.isValid) "Patch Successful" else "Patch Failed Validation"

            ElevatedCard(Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = bgColor)) {
                Column(Modifier.padding(32.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(titleText, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = textColor)
                    Spacer(Modifier.height(8.dp))
                    Text("Drift Reduced: ${"%,.1f".format(eval.driftReductionPercent)}% $icon", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = textColor)
                    
                    if (eval.isValid) {
                        Text("Accuracy Improved: ${"%+.1f".format(eval.accuracyImprovement * 100)}%", fontSize = 16.sp, color = Color(0xFF388E3C), modifier = Modifier.padding(top = 8.dp))
                    } else {
                        Text("Reason: Insufficient drift reduction or metrics drop.", fontSize = 14.sp, color = textColor, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }

            // Before vs After Accuracy
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ElevatedCard(Modifier.weight(1f)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Before Patch", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                        Text("Accuracy: ${"%,.1f".format(eval.beforeMetrics.accuracy * 100)}%", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("F1 Score: ${"%,.3f".format(eval.beforeMetrics.f1Score)}")
                        Text("Drift Score: ${"%,.3f".format(eval.driftScoreBefore)}")
                    }
                }
                ElevatedCard(Modifier.weight(1f)) {
                    Column(Modifier.padding(16.dp)) {
                        val colorAfter = if (eval.isValid) Color(0xFF00C853) else Color(0xFFFF1744)
                        Text("After Patch", fontWeight = FontWeight.SemiBold, color = colorAfter)
                        Text("Accuracy: ${"%,.1f".format(eval.afterMetrics.accuracy * 100)}%", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("F1 Score: ${"%,.3f".format(eval.afterMetrics.f1Score)}")
                        Text("Drift Score: ${"%,.3f".format(eval.driftScoreAfter)}")
                    }
                }
            }

            // Confidence & Deployment
            ElevatedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Patch Confidence: ${"%,.1f".format(eval.confidenceScore * 100)}%", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Risk Level: ${eval.riskLevel}")
                    }
                    Button(onClick = { evalViewModel.deployPatch(patches) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3949AB))) {
                        Icon(Icons.Filled.Rocket, null); Spacer(Modifier.width(8.dp))
                        Text("Deploy Model v2")
                    }
                }
            }
        }
    }
}

// ==================== SIMULATION ====================

@Composable
private fun SimulationContent(evalViewModel: EvaluationViewModel, uploadViewModel: UploadViewModel) {
    val refFile by uploadViewModel.selectedReferenceFile.collectAsState()
    val simResult by evalViewModel.simulationResult.collectAsState()
    val groundTruth by evalViewModel.groundTruth.collectAsState()
    var sliderValue by remember { mutableStateOf(0.0f) }
    val loading by evalViewModel.loading.collectAsState()

    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Drift Simulation & Ground Truth", fontSize = 18.sp, fontWeight = FontWeight.Bold)

        if (refFile == null) {
            ElevatedCard(Modifier.fillMaxWidth()) { Text("Please upload a reference dataset in the Upload tab first.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.outline) }
            return
        }

        // Scenario Simulation (Item #8)
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Scenario Simulation", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Inject theoretical drift into your reference data to see how the system reacts in real-time.", color = MaterialTheme.colorScheme.outline)
                
                Text(when {
                    sliderValue < 0.1f -> "Pristine Data (0.0)"
                    sliderValue < 0.3f -> "Slight Noise (${"%.1f".format(sliderValue)})"
                    sliderValue < 0.6f -> "Moderate Drift (${"%.1f".format(sliderValue)})"
                    sliderValue < 0.8f -> "High Drift (${"%.1f".format(sliderValue)})"
                    else -> "Complete Distribution Shift (${"%.1f".format(sliderValue)})"
                }, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)

                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = { evalViewModel.simulateDrift(refFile!!, sliderValue.toDouble()) },
                    enabled = !loading, modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (loading) "Simulating..." else "🧪 Simulate Drift Scenario")
                }
            }
        }

        simResult?.let { sim ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Simulation Result", fontWeight = FontWeight.SemiBold)
                    Text("System Reaction: ${sim.systemReaction}", fontWeight = FontWeight.Bold, color = if (sim.driftLevel > 0.3) MaterialTheme.colorScheme.error else Color(0xFF00C853))
                    Text("Detected Drift Score: ${"%,.3f".format(sim.driftResult.overallDriftScore)}")
                    Text("Patches Auto-Generated: ${sim.patchesGenerated}")
                    Text("Theoretical Drift Reduction: ${"%,.1f".format(sim.driftReductionPercent)}%")
                }
            }
        }

        Divider(Modifier.padding(vertical = 8.dp))

        // Ground Truth Testing (Item #10)
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Ground Truth Testing Mode", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Run internal validation using 8 known datasets to prove system accuracy.", color = MaterialTheme.colorScheme.outline)
                
                Button(
                    onClick = { evalViewModel.runGroundTruthTest(refFile!!) },
                    enabled = !loading, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text(if (loading) "Running 8 Test Scenarios..." else "✅ Run Ground Truth Validation")
                }
            }
        }

        groundTruth?.let { gt ->
            ElevatedCard(Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFFE3F2FD))) {
                Column(Modifier.padding(20.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("System Accuracy Proven", fontSize = 16.sp, color = Color(0xFF1565C0))
                    Text("${"%,.1f".format(gt.systemAccuracy)}%", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0D47A1))
                    val cm = gt.confusionMatrix
                    Text("TP: ${cm.truePositives} | FP: ${cm.falsePositives} | TN: ${cm.trueNegatives} | FN: ${cm.falseNegatives}", modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

// ==================== CONTINUOUS MONITORING ====================

@Composable
private fun MonitoringContent(evalViewModel: EvaluationViewModel, uploadViewModel: UploadViewModel) {
    val refFile by uploadViewModel.selectedReferenceFile.collectAsState()
    val curFile by uploadViewModel.selectedCurrentFile.collectAsState()
    val active by evalViewModel.autoMonitorActive.collectAsState()
    val logs by evalViewModel.autoMonitorLog.collectAsState()

    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Continuous Auto-Monitoring", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("Self-healing system mode (Item #7)", color = MaterialTheme.colorScheme.outline)

        if (refFile == null || curFile == null) {
            ElevatedCard(Modifier.fillMaxWidth()) { Text("Please select reference and current data files in the Upload tab.", Modifier.padding(16.dp)) }
            return
        }

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Self-Healing Monitor Loop", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Automatically detects drift and triggers patches every 10s", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                    }
                    if (active) {
                        Button(onClick = { evalViewModel.stopAutoMonitor() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                            Text("⏹️ Stop Monitor")
                        }
                    } else {
                        Button(onClick = { evalViewModel.startAutoMonitor(refFile!!, curFile!!) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853))) {
                            Text("▶️ Start Auto-Monitor")
                        }
                    }
                }

                Surface(
                    color = Color.Black,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(300.dp)
                ) {
                    val scrollState = rememberScrollState()
                    LaunchedEffect(logs.size) { scrollState.animateScrollTo(scrollState.maxValue) }
                    Column(Modifier.padding(12.dp).verticalScroll(scrollState)) {
                        if (logs.isEmpty()) {
                            Text("Waiting to start...", color = Color.Gray, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        } else {
                            logs.forEach { log ->
                                val color = when {
                                    log.contains("DRIFT") || log.contains("Error") -> Color(0xFFFF5252)
                                    log.contains("OK") -> Color(0xFF69F0AE)
                                    else -> Color.White
                                }
                                Text(log, color = color, fontSize = 13.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== SETTINGS ====================

@Composable
private fun SettingsContent(settingsManager: SettingsManager, isDarkTheme: Boolean, onToggleTheme: () -> Unit) {
    var backendUrl by remember { mutableStateOf(settingsManager.settings.backendUrl) }
    var psiThreshold by remember { mutableStateOf(settingsManager.settings.psiThreshold.toFloat()) }
    var ksThreshold by remember { mutableStateOf(settingsManager.settings.ksThreshold.toFloat()) }
    var autoPatching by remember { mutableStateOf(settingsManager.settings.autoPatching) }

    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Settings", fontSize = 18.sp, fontWeight = FontWeight.Bold)

        // Appearance
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Appearance", fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Dark Theme")
                    Switch(checked = isDarkTheme, onCheckedChange = { onToggleTheme() })
                }
            }
        }

        // Backend
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Backend Connection", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(value = backendUrl, onValueChange = { backendUrl = it }, label = { Text("Backend URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Button(onClick = { settingsManager.update { this.backendUrl = backendUrl } }) { Text("Save") }
            }
        }

        // Drift Thresholds
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Drift Detection Thresholds", fontWeight = FontWeight.SemiBold)
                Text("PSI Threshold: ${"%.2f".format(psiThreshold)}", fontSize = 13.sp)
                Slider(value = psiThreshold, onValueChange = { psiThreshold = it }, valueRange = 0.05f..1.0f, steps = 18,
                    onValueChangeFinished = { settingsManager.update { this.psiThreshold = psiThreshold.toDouble() } })
                Text("KS Threshold: ${"%.2f".format(ksThreshold)}", fontSize = 13.sp)
                Slider(value = ksThreshold, onValueChange = { ksThreshold = it }, valueRange = 0.01f..0.5f, steps = 9,
                    onValueChangeFinished = { settingsManager.update { this.ksThreshold = ksThreshold.toDouble() } })
            }
        }

        // Auto-patching
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Auto-Patching", fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Enable Auto-Patching")
                    Switch(checked = autoPatching, onCheckedChange = { autoPatching = it; settingsManager.update { this.autoPatching = autoPatching } })
                }
            }
        }

        // About
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text("About", fontWeight = FontWeight.SemiBold)
                Text("DriftGuardAI Desktop v1.0.0", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                Text("ML Drift Monitoring & Automated Patching System", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                Text("Built with Kotlin + Compose Multiplatform", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

// ==================== REUSABLE COMPONENTS ====================

@Composable
private fun MetricCard(title: String, value: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(color.copy(alpha = 0.15f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(24.dp), tint = color)
            }
            Column {
                Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DriftResultCard(result: DriftResult) {
    ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(result.driftType.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("Score: ${"%.3f".format(result.overallDriftScore)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
            }
            val badgeColor = if (result.isDriftDetected) MaterialTheme.colorScheme.error else Color(0xFF00C853)
            Surface(shape = RoundedCornerShape(4.dp), color = badgeColor) {
                Text(if (result.isDriftDetected) "DRIFT" else "NORMAL", Modifier.padding(horizontal = 8.dp, vertical = 2.dp), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DriftDetailCard(result: DriftResult) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(result.driftType.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                val badgeColor = if (result.isDriftDetected) MaterialTheme.colorScheme.error else Color(0xFF00C853)
                Surface(shape = RoundedCornerShape(4.dp), color = badgeColor) {
                    Text(if (result.isDriftDetected) "DRIFT DETECTED" else "NO DRIFT", Modifier.padding(horizontal = 8.dp, vertical = 2.dp), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text("Overall Score: ${"%.4f".format(result.overallDriftScore)}", fontSize = 14.sp)
            Text(result.description, fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
            if (result.featureDrifts.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text("Feature Breakdown:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                result.featureDrifts.take(10).forEach { feat ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(feat.featureName, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text("PSI: ${"%.3f".format(feat.psiScore)}", fontSize = 12.sp, color = if (feat.isDrifted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelCard(model: MLModel) {
    ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Memory, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(model.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("Framework: ${model.framework} | v${model.version}", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun PatchCard(patch: Patch, patchViewModel: PatchGenerationViewModel? = null) {
    val statusColor = when (patch.status) {
        "APPLIED" -> Color(0xFF00C853)
        "ROLLED_BACK" -> Color(0xFFFF9100)
        "FAILED" -> Color(0xFFFF1744)
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val statusTextColor = when (patch.status) {
        "APPLIED", "FAILED", "ROLLED_BACK" -> Color.White
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(patch.patchType, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Surface(shape = RoundedCornerShape(4.dp), color = statusColor) {
                    Text(patch.status, Modifier.padding(horizontal = 8.dp, vertical = 3.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = statusTextColor)
                }
            }
            Text(patch.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Safety: ${"%.0f".format(patch.safetyScore * 100)}%", fontSize = 12.sp, color = Color(0xFF00C853))
                Text("Effectiveness: ${"%.0f".format(patch.effectivenessScore * 100)}%", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }
            // Apply / Rollback buttons
            if (patchViewModel != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (patch.status == "CREATED") {
                        Button(
                            onClick = { patchViewModel.applyPatchLocal(patch.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Filled.Check, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Apply", fontSize = 12.sp)
                        }
                    }
                    if (patch.status == "APPLIED") {
                        OutlinedButton(
                            onClick = { patchViewModel.rollbackPatchLocal(patch.id) },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Filled.Undo, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Rollback", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

// ==================== CHART ====================

@Composable
private fun DriftTimelineChart(timeline: List<EvaluationViewModel.TimelineEntry>, modifier: Modifier = Modifier) {
    if (timeline.isEmpty()) return
    val scores = timeline.map { it.score.toFloat() }.reversed()
    if (scores.isEmpty()) return

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val maxScore = (scores.maxOrNull() ?: 1f).coerceAtLeast(0.5f)
        val stepX = if (scores.size > 1) w / (scores.size - 1) else w

        // Grid lines
        for (i in 0..4) {
            val y = h * (1 - i / 4f)
            drawLine(Color.Gray.copy(alpha = 0.2f), Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        }

        // Threshold line
        val thresholdY = h * (1 - 0.35f / maxScore)
        drawLine(Color.Red.copy(alpha = 0.5f), Offset(0f, thresholdY), Offset(w, thresholdY), strokeWidth = 2f)

        // Line path
        if (scores.size >= 2) {
            val path = Path()
            scores.forEachIndexed { i, score ->
                val x = i * stepX
                val y = h * (1 - score / maxScore)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, Color(0xFF3949AB), style = Stroke(width = 3f))
        }

        // Data points
        scores.forEachIndexed { i, score ->
            val x = i * stepX
            val y = h * (1 - score / maxScore)
            val ptColor = if (score > 0.35f) Color(0xFFFF1744) else Color(0xFF00C853)
            drawCircle(ptColor, radius = 5f, center = Offset(x, y))
        }
    }
}
