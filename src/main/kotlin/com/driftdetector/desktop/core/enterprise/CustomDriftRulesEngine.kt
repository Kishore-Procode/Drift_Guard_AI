package com.driftdetector.desktop.core.enterprise

/**
 * Custom domain rule engine for high-priority drift alerts.
 *
 * Supports simple expressions such as:
 * "temp > 50 && humidity < 20"
 */
class CustomDriftRulesEngine {

    data class Rule(
        val name: String,
        val expression: String,
        val severity: String,
        val message: String
    )

    data class RuleTrigger(
        val ruleName: String,
        val severity: String,
        val message: String
    )

    fun evaluate(rules: List<Rule>, sample: Map<String, Double>): List<RuleTrigger> {
        return rules.filter { matches(it.expression, sample) }
            .map { RuleTrigger(it.name, it.severity, it.message) }
    }

    private fun matches(expression: String, sample: Map<String, Double>): Boolean {
        val andParts = expression.split("&&").map { it.trim() }
        return andParts.all { clause -> evaluateClause(clause, sample) }
    }

    private fun evaluateClause(clause: String, sample: Map<String, Double>): Boolean {
        val operators = listOf(">=", "<=", ">", "<", "==")
        val op = operators.firstOrNull { clause.contains(it) } ?: return false
        val parts = clause.split(op).map { it.trim() }
        if (parts.size != 2) return false

        val key = parts[0]
        val left = sample[key] ?: return false
        val right = parts[1].toDoubleOrNull() ?: return false

        return when (op) {
            ">=" -> left >= right
            "<=" -> left <= right
            ">" -> left > right
            "<" -> left < right
            "==" -> left == right
            else -> false
        }
    }
}
