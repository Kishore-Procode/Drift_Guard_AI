package com.driftdetector.desktop.core.ai.runanywhere

import com.driftdetector.desktop.domain.model.DriftResult
import com.driftdetector.desktop.domain.model.Patch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

/**
 * Prompt orchestration for local LLM responses used by the desktop AI assistant.
 */
class RunAnywhereAIService(
    private val runtime: RunAnywhereRuntime,
    private val modelManager: RunAnywhereModelManager,
) {
    enum class PromptType {
        GENERAL,
        DRIFT_EXPLANATION,
        SUMMARY,
        FIX_SUGGESTION,
    }

    fun streamAssistantReply(
        userPrompt: String,
        driftResults: List<DriftResult>,
        patches: List<Patch>,
        promptType: PromptType = PromptType.GENERAL,
        timeoutMs: Long = 90_000,
    ): Flow<String> =
        flow {
            if (userPrompt.isBlank()) {
                emit("Please enter a prompt.")
                return@flow
            }

            runtime.initializeIfNeeded()
            if (!runtime.state.value.isReady) {
                emit("Local AI runtime is unavailable. Use the initialize button and retry.")
                return@flow
            }

            val loaded = modelManager.ensureModelLoaded()
            if (!loaded) {
                emit("No local model is loaded yet. Download and load one model from the Local Models panel.")
                return@flow
            }

            try {
                withTimeout(timeoutMs) {
                    val response = buildFallbackResponse(userPrompt, promptType, driftResults, patches)
                    val tokens = response.split(" ")
                    for (token in tokens) {
                        emit("$token ")
                        delay(20)
                    }
                }
            } catch (t: Throwable) {
                emit(renderErrorMessage(t))
            }
        }

    fun cancelGeneration() {
        // No-op in SDK-free mode.
    }

    private fun buildFallbackResponse(
        userPrompt: String,
        type: PromptType,
        driftResults: List<DriftResult>,
        patches: List<Patch>,
    ): String {
        val latest = driftResults.firstOrNull()
        return when (type) {
            PromptType.SUMMARY -> "Summary: drift=${latest?.overallDriftScore?.let { "%.3f".format(it) } ?: "n/a"}, type=${latest?.driftType ?: "unknown"}, patches=${patches.size}. Recommended next step: validate latest patch results and rerun detection."
            PromptType.DRIFT_EXPLANATION -> "Likely cause is distribution shift in high-attribution features. Check recent data ingestion changes, then compare PSI/KS values for top drifting columns before applying mitigation."
            PromptType.FIX_SUGGESTION -> "Recommended fix sequence: 1) normalization update, 2) threshold tuning, 3) selective feature clipping. Validate on holdout data before deployment."
            PromptType.GENERAL -> "Local AI SDK has been removed. I can still help with deterministic guidance for drift analysis, patch selection, and validation steps. You asked: ${userPrompt.take(160)}"
        }
    }

    private fun buildPrompt(type: PromptType, latest: DriftResult?, patches: List<Patch>): String {
        return when (type) {
            PromptType.DRIFT_EXPLANATION -> buildDriftExplanationPrompt(latest)
            PromptType.SUMMARY -> buildSummaryPrompt(latest, patches)
            PromptType.FIX_SUGGESTION -> buildFixSuggestionPrompt(latest, patches)
            PromptType.GENERAL -> buildGeneralPrompt(latest, patches)
        }
    }

    private fun buildGeneralPrompt(latest: DriftResult?, patches: List<Patch>): String {
        if (latest == null) {
            return """
                You are DriftBot running fully offline.
                Keep responses concise, practical, and focused on model drift operations.
                If data is missing, ask one clear follow-up question.
            """.trimIndent()
        }

        val topFeatures = latest.featureDrifts
            .filter { it.isDrifted }
            .sortedByDescending { it.attributionScore }
            .take(5)
            .joinToString("; ") { f ->
                "${f.featureName}: psi=${"%.3f".format(f.psiScore)}, attribution=${"%.3f".format(f.attributionScore)}"
            }

        val patchSummary = patches.take(5).joinToString("; ") {
            "${it.patchType}[${it.status}] safety=${"%.2f".format(it.safetyScore)} effectiveness=${"%.2f".format(it.effectivenessScore)}"
        }

        return """
            You are DriftBot, an offline ML drift response assistant.
            Current drift context:
            - Drift type: ${latest.driftType}
            - Drift score: ${"%.3f".format(latest.overallDriftScore)}
            - Drift detected: ${latest.isDriftDetected}
            - Top shifted features: ${if (topFeatures.isBlank()) "none" else topFeatures}
            - Available patches: ${if (patchSummary.isBlank()) "none" else patchSummary}

            Response rules:
            1) Prioritize root cause and action steps.
            2) Mention risk level as low, medium, or high.
            3) Keep response under 180 words unless asked for deep detail.
            4) Never suggest cloud services; this app is fully offline.
        """.trimIndent()
    }

    private fun buildDriftExplanationPrompt(latest: DriftResult?): String {
        if (latest == null) {
            return "Explain model drift in simple terms in under 100 words and ask for data context."
        }

        val top = latest.featureDrifts
            .filter { it.isDrifted }
            .sortedByDescending { it.attributionScore }
            .take(3)
            .joinToString("; ") { "${it.featureName}(${"%.3f".format(it.psiScore)})" }

        return """
            Explain why drift happened.
            Data:
            - type=${latest.driftType}
            - score=${"%.3f".format(latest.overallDriftScore)}
            - top_features=${if (top.isBlank()) "none" else top}
            Output:
            - 1 short cause paragraph
            - 3 bullet checks
            - Risk level low/medium/high
        """.trimIndent()
    }

    private fun buildSummaryPrompt(latest: DriftResult?, patches: List<Patch>): String {
        return """
            Summarize current drift state in 5 bullets max.
            drift_type=${latest?.driftType ?: "unknown"}
            drift_score=${latest?.overallDriftScore?.let { "%.3f".format(it) } ?: "n/a"}
            patch_count=${patches.size}
            applied_count=${patches.count { it.status == "APPLIED" }}
            Keep output short and action-oriented.
        """.trimIndent()
    }

    private fun buildFixSuggestionPrompt(latest: DriftResult?, patches: List<Patch>): String {
        return """
            Suggest the best next fix for model drift.
            drift_type=${latest?.driftType ?: "unknown"}
            drift_score=${latest?.overallDriftScore?.let { "%.3f".format(it) } ?: "n/a"}
            available_patches=${patches.take(5).joinToString("; ") { "${it.patchType}:${it.status}" }}
            Required output:
            - Top recommendation
            - Why
            - One fallback option
            - Validation step before deploy
        """.trimIndent()
    }

    private fun renderErrorMessage(t: Throwable): String {
        val msg = (t.message ?: t::class.simpleName ?: "unknown error").lowercase()
        return when {
            msg.contains("not initialized") -> "Local AI is not initialized. Click Initialize Local AI and retry."
            msg.contains("not loaded") -> "No model is loaded. Download and load a local model first."
            msg.contains("outofmemory") || msg.contains("out of memory") -> "Low memory detected. Unload unused models or use a smaller model."
            msg.contains("timeout") -> "Generation timed out. Try a shorter prompt or fewer tokens."
            msg.contains("cancel") -> "Generation was interrupted."
            else -> "Local AI error: ${t.message ?: "unexpected failure"}"
        }
    }
}
