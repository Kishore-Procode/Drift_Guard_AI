# DriftGuardAI Desktop Application

A professional Compose Multiplatform Desktop application for monitoring and fixing ML model drift. Built with Kotlin, Jetpack Compose, and Material 3.

For full enterprise architecture details, hardening fixes, and latest update notes, see:
- [ENTERPRISE_APP_README.md](ENTERPRISE_APP_README.md)

## 🚀 Features

### 🏢 Power-User & Enterprise Features (Desktop)
- **Batch Processing Engine**: Process 100+ CSV files in parallel with scheduled audits
- **Batch Audit Scheduler**: Nightly scans and Monday 8:00 AM report workflow
- **Built-In Explainability Engine**: Per-sample feature attribution for drifted data analysis
- **Production Scenario Simulator**: Inject synthetic drift and evaluate mitigation confidence
- **Multi-Version Model Comparison**: Compare model versions for safer rollout and rollback
- **Custom Drift Rules Engine**: Domain rules like `temp > 50 && humidity < 20` to trigger HIGH alerts
- **Dataset Diff & Merge (Roadmap)**: Row-level compare/merge with provenance tracking
- **A/B Test Analyzer (Roadmap)**: Control/treatment significance and drift direction analysis

### Core Functionality
- **Data Upload**: Upload CSV/JSON/TSV/PSV/DAT datasets via native file picker dialogs
- **Local Drift Detection**: PSI (Population Stability Index), KS Test, distribution shift analysis — runs entirely on desktop, no backend required
- **Intelligent Auto-Patching**: 5-strategy patch engine (clipping, normalization, reweighting, threshold tuning, ultra-aggressive combined)
- **Instant Drift Fix**: One-click workflow — select files → detect → auto-patch → export
- **Patch Management**: Apply individual patches or "Apply All", rollback support, safety/effectiveness scoring
- **AI Assistant (DriftBot)**: Rule-based chatbot with `/help`, `/status`, `/drift`, `/patches`, `/tips` commands
- **Analytics & Insights**: Drift type distribution, feature drift heatmap, alert summary
- **Data Export**: Export drift results and patches to JSON/CSV/Text (appears after patches are applied)
- **Real-time Monitoring**: WebSocket client with auto-reconnect and exponential backoff
- **Model Management**: Upload, manage, and select ML models (.tflite, .onnx, .h5, .pt)

### UI/UX Highlights
- **12-Tab Navigation**: Dashboard, Upload, Drift Analysis, Patches, Instant Fix, Explainability, What-If Lab, Batch Audits, Model Versions, AI Assistant, Analytics, Settings
- **Dark/Light Theme**: Toggle via sidebar or Settings, persisted across sessions
- **Navy Blue Premium Palette**: Material 3 color scheme matching the Android app
- **Live Status Indicators**: Color-coded patch badges (green=APPLIED, orange=ROLLED_BACK, red=FAILED)
- **Canvas Charts**: Drift timeline chart with threshold line and color-coded data points
- **Connection Status**: Real WebSocket connectivity indicator in sidebar

### Architecture
- **MVVM Pattern**: Clean separation with ViewModels, Repository, and ApiClient layers
- **Coroutines**: All async operations run without blocking the UI
- **Ktor HTTP Client**: Backend integration with WebSocket support
- **Kotlinx Serialization**: Type-safe API communication
- **Settings Persistence**: JSON-based config at `~/.driftguard/settings.json`
- **Enterprise Engines**: Dedicated analytics, simulation, and audit services for desktop-scale workflows

## 📁 Project Structure

```
desktop app/
├── build.gradle.kts
├── gradle.properties
├── demo_data/                           # Sample data for testing
│   ├── reference_data.csv              # Baseline dataset (50 rows, 5 features)
│   ├── current_data_WITH_DRIFT.csv     # Drifted dataset (triggers COVARIATE_DRIFT)
│   ├── current_data_NO_DRIFT.csv       # Normal dataset (no drift)
│   └── demo_model.tflite              # Demo model file
└── src/main/kotlin/com/driftdetector/desktop/
    ├── Main.kt                         # App entry point
    ├── core/
    │   ├── ai/
    │   │   └── AIAssistant.kt          # DriftBot chatbot engine
    │   ├── analytics/
    │   │   └── ExplainabilityEngine.kt # Per-sample feature attribution engine
    │   ├── data/
    │   │   └── DataFileParser.kt       # Multi-format file parser
    │   ├── drift/
    │   │   └── DriftDetector.kt        # PSI, KS test, drift classification
    │   ├── enterprise/
    │   │   ├── BatchAuditEngine.kt     # Parallel folder audits + scheduling
    │   │   ├── MultiVersionModelComparator.kt # Version comparison & rollback support
    │   │   ├── ProductionScenarioSimulator.kt # What-if synthetic drift lab
    │   │   └── CustomDriftRulesEngine.kt # Domain-specific alert rules
    │   ├── export/
    │   │   └── ExportManager.kt        # JSON/CSV/Text export with save dialogs
    │   ├── ml/
    │   │   └── Predictor.kt            # Generic model prediction interface
    │   ├── patch/
    │   │   └── PatchGenerator.kt       # 5-strategy intelligent patching
    │   ├── realtime/
    │   │   └── WebSocketMonitor.kt     # WebSocket with auto-reconnect
    │   └── settings/
    │       └── SettingsManager.kt      # Persistent app settings
    ├── data/
    │   ├── model/
    │   │   └── Models.kt               # API request/response models
    │   ├── remote/
    │   │   └── ApiClient.kt            # Ktor HTTP client
    │   └── repository/
    │       └── DriftDetectorRepository.kt
    ├── domain/
    │   └── model/
    │       └── DriftModels.kt          # Domain models & enums
    ├── presentation/
    │   ├── screen/
    │   │   ├── MainScreen.kt           # Main navigation + dashboard screens
    │   │   └── EnterpriseTabs.kt       # Explainability, What-If, Batch, Model Comparison tabs
    │   ├── theme/
    │   │   └── Theme.kt                # Material 3 dark/light themes
    │   └── viewmodel/
    │       ├── ViewModels.kt           # Core application ViewModels
    │       └── EnterpriseViewModel.kt  # Enterprise feature orchestration
    └── util/
        └── FileUtils.kt               # File dialogs & validation
```

## 🛠️ Build & Run

### Prerequisites
- JDK 17 or higher
- Gradle 8.0+
- Node.js backend on `http://localhost:3000/api` (optional — local detection works offline)

### Run the Application
```bash
# From the project root
.\gradlew.bat ":desktop app:run"

# Build only
.\gradlew.bat ":desktop app:build"

# Create native distribution (Windows MSI/EXE)
.\gradlew.bat ":desktop app:packageDistributionForCurrentOS"
```

## 🧪 Quick Test with Demo Data

Demo files are included in `demo_data/`:

| File | Purpose | Expected Result |
|------|---------|-----------------|
| `reference_data.csv` | Baseline data (50 rows, 5 sensor features) | Use as reference |
| `current_data_WITH_DRIFT.csv` | Drifted data (temp +7°C, vibration 3x) | **COVARIATE_DRIFT** detected |
| `current_data_NO_DRIFT.csv` | Normal data (same distribution) | **NO_DRIFT** |
| `demo_model.tflite` | Demo model metadata | For testing model upload |

### Test Workflow
1. Launch the app
2. **Upload tab** → Load `demo_model.tflite`, `reference_data.csv`, `current_data_WITH_DRIFT.csv`
3. **Drift Detection tab** → Click **Detect Drift** → See COVARIATE_DRIFT with high score
4. **Patches tab** → Click **Generate Patches** → Click **✅ Apply All Patches**
5. Export card appears → Click **Export JSON** to save results
6. **Instant Fix tab** → One-click alternative: select files, click **🚀 Run Instant Fix**

## 🧠 Explainability Dashboard (Desktop)

Use the built-in explainability engine to answer: **"Why did drift hurt this prediction?"**

- Load model + drifted sample
- Compute per-feature attribution on the selected data point
- Rank top features that pushed confidence up/down
- Export attribution values for reporting pipelines

Pseudo-flow:

```kotlin
val explainer = ExplainabilityEngine(predictor)
val explanation = explainer.explainSample(featureNames, sample, referenceMeans)
```

Example insight:

`Temperature is +7C vs baseline -> confidence drops by 45%`

## 🎪 Production Scenario Simulator

The scenario simulator supports pre-production stress tests:

- Inject covariate drift (example: temperature +10C)
- Simulate failed sensors (force feature to 0)
- Shift selected features by N sigma to mimic new customer segments
- Re-run drift score to estimate impact before deployment

Pseudo-flow:

```kotlin
val simulator = ProductionScenarioSimulator()
val result = simulator.runScenario(
    modelId = "prod-model",
    referenceData = referenceData,
    currentData = currentData,
    featureNames = featureNames,
    scenario = ProductionScenarioSimulator.Scenario(
        name = "Temp Spike",
        featureShifts = mapOf("temperature" to 10.0)
    )
)
```

## 📊 Batch Audit Engine

Batch audits support enterprise automation on desktop nodes:

- Process large folders in parallel
- Detect drift across all CSV files
- Auto-generate and apply patches when enabled
- Schedule daily/nightly audits and weekly Monday 8:00 AM runs
- Send summarized reports through an email sender implementation

Pseudo-flow:

```kotlin
val engine = BatchAuditEngine()
engine.scheduleWeeklyMonday8am(
    referenceFile = File("reference.csv"),
    folder = File("data/production"),
    autoPatch = true,
    recipients = listOf("ml-ops@company.com")
)
```

## 🔀 Multi-Version Model Drift Comparison

Compare two model versions on the same data before rollout:

- `v1.0` vs `v2.3` drift response
- Prediction disagreement percentage
- Suggested rollback decision when disagreement is high

Pseudo-flow:

```kotlin
val comparator = MultiVersionModelComparator()
val result = comparator.compare(referenceData, currentData, featureNames, modelV1, modelV23)
```

## 🧩 Custom Drift Rules Engine

Define domain-specific alert rules:

- Example rule: `temp > 50 && humidity < 20`
- Trigger severity levels (LOW, MEDIUM, HIGH, CRITICAL)
- Attach operational response messages

Pseudo-flow:

```kotlin
val rules = listOf(
    CustomDriftRulesEngine.Rule(
        name = "Heatwave Risk",
        expression = "temp > 50 && humidity < 20",
        severity = "HIGH",
        message = "Trigger cooling fallback immediately"
    )
)
```

## 🧭 Priority Roadmap

Top 3 recommendations (impact first):

| Feature | Impact | Effort | Why First |
|---------|--------|--------|-----------|
| Built-in Explainability | High | Medium | Explains prediction impact, not just drift presence |
| Batch Audit Scheduler | High | Low | Automates enterprise monitoring and reporting |
| Multi-Model Comparison | Medium | Low | Safer promotion/rollback decisions across versions |

## 📊 Drift Detection Engine

### Statistical Tests
- **PSI (Population Stability Index)**: Measures distribution shift across binned data
- **KS Test (Kolmogorov-Smirnov)**: Non-parametric test for distribution differences
- **Distribution Shift Analysis**: Mean, variance, median, skewness, kurtosis changes

### Drift Types
| Type | Description | Trigger |
|------|-------------|---------|
| `COVARIATE_DRIFT` | Feature distributions shifted | >50% features drifted |
| `CONCEPT_DRIFT` | Model decision boundary shifted | 20-50% features, high variance |
| `PRIOR_DRIFT` | Class distribution changed | <20% features, consistent shift |
| `NO_DRIFT` | No significant change | All features within thresholds |

### Configurable Thresholds (via Settings)
- **PSI Threshold**: Default 0.35 (range 0.05–1.0)
- **KS Threshold**: Default 0.10 (range 0.01–0.5)

## 🔧 Patching Strategies

| Strategy | Safety | Effectiveness | Use Case |
|----------|--------|---------------|----------|
| Feature Clipping | High | Medium | Constrain values to reference range |
| Normalization Update | Very High | Medium | Re-center feature distributions |
| Feature Reweighting | High | Medium | Reduce weight of drifted features |
| Threshold Tuning | Very High | Low | Adjust decision boundary |
| Ultra-Aggressive Combined | Medium | Very High | Critical drift (>0.5 score) |

## 🤖 AI Assistant Commands

| Command | Description |
|---------|-------------|
| `/help` | Show all available commands |
| `/status` | Current system status and counts |
| `/drift` | Latest drift analysis summary |
| `/patches` | List generated patches with scores |
| `/tips` | Drift detection best practices |
| `/about` | App version and info |

## 🔌 Backend Integration

### API Endpoints (Optional)
The app works fully offline with local detection, but connects to the backend when available:

```
POST /api/upload           Upload data files
POST /api/drift/detect     Server-side drift detection
POST /api/patches/generate Server-side patch generation
GET  /api/models           List available models
POST /api/models/upload    Upload model to server
```

### WebSocket (Optional)
```
ws://localhost:8080         Real-time drift alerts & telemetry
```

## ⚙️ Settings

Settings are persisted to `~/.driftguard/settings.json`:

| Setting | Default | Description |
|---------|---------|-------------|
| Dark Theme | Off | Toggle dark/light mode |
| Backend URL | `http://localhost:3000/api` | API server address |
| WebSocket URL | `ws://localhost:8080` | Real-time server |
| PSI Threshold | 0.35 | Drift sensitivity |
| KS Threshold | 0.10 | Statistical test threshold |
| Auto-Patching | On | Auto-generate patches on drift |
| Aggressiveness | Normal | Patch aggressiveness level |

## 🎨 Design System

### Colors (Material 3 — Navy Blue)
- **Primary**: #3949AB (Indigo)
- **Secondary**: #00BFA5 (Teal)
- **Tertiary**: #FF6D00 (Orange)
- **Error/Alert**: #FF1744 (Red)
- **Success**: #00C853 (Green)

### Patch Status Colors
- 🟢 **APPLIED** — Green badge
- 🟠 **ROLLED_BACK** — Orange badge
- 🔴 **FAILED** — Red badge
- ⚪ **CREATED** — Default badge

## 🐛 Troubleshooting

### Build Issues
```bash
.\gradlew.bat ":desktop app:clean" ":desktop app:build"
.\gradlew.bat --stop   # Reset Gradle daemon
```

### Runtime Issues
- **File picker not opening**: Check Java AWT permissions
- **Drift detection fails**: Ensure both files have matching column counts
- **Theme not switching**: Check `~/.driftguard/settings.json` exists
- **Backend offline**: App works fully offline with local detection engine

## 📄 License

Copyright © 2026 DriftGuardAI. All rights reserved.

---

**Happy Drift Monitoring! 🛡️**
"# Drift_Guard_AI" 
