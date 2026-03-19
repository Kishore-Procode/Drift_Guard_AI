package com.driftdetector.desktop.core.data

import java.io.File

/**
 * Multi-format data file parser supporting CSV, JSON, TSV, PSV, DAT with auto-detection.
 * Ported from the Android DataFileParser for desktop use.
 */
class DataFileParser {

    data class ParseResult(
        val data: List<FloatArray>,
        val featureNames: List<String>,
        val rowCount: Int,
        val colCount: Int
    )

    /**
     * Parse any supported data file and return numeric data as float arrays.
     */
    fun parseFile(file: File): ParseResult {
        return try {
            require(file.exists()) { "File does not exist: ${file.absolutePath}" }
            val lines = file.readLines().filter { it.isNotBlank() }
            require(lines.isNotEmpty()) { "File is empty: ${file.name}" }

            val delimiter = detectDelimiter(file.extension, lines.first())
            val result = parseDelimitedFile(lines, delimiter)
            require(result.data.isNotEmpty()) { "No valid rows found in ${file.name}" }
            require(result.colCount > 0) { "No columns detected in ${file.name}" }
            result
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to parse ${file.name}: ${e.message}", e)
        }
    }

    /**
     * Detect the delimiter based on file extension and first line content.
     */
    private fun detectDelimiter(extension: String, firstLine: String): String {
        return when (extension.lowercase()) {
            "csv" -> ","
            "tsv" -> "\t"
            "psv" -> "|"
            "dat" -> " "
            "json" -> return "json" // special handling
            "txt" -> detectDelimiterFromContent(firstLine)
            else -> detectDelimiterFromContent(firstLine)
        }
    }

    /**
     * Auto-detect delimiter from content by counting common separators.
     */
    private fun detectDelimiterFromContent(line: String): String {
        val delimiters = mapOf(
            "," to line.count { it == ',' },
            "\t" to line.count { it == '\t' },
            "|" to line.count { it == '|' },
            ";" to line.count { it == ';' }
        )
        return delimiters.maxByOrNull { it.value }?.key ?: ","
    }

    /**
     * Parse a delimited file (CSV, TSV, PSV, etc.)
     */
    private fun parseDelimitedFile(lines: List<String>, delimiter: String): ParseResult {
        if (delimiter == "json") {
            return parseJsonFile(lines.joinToString("\n"))
        }

        // Check if first line is a header (contains non-numeric values)
        val firstLineParts = splitWithQuotes(lines.first(), delimiter)
        val hasHeader = firstLineParts.any { field ->
            field.trim().toDoubleOrNull() == null && field.trim().isNotEmpty()
        }

        val featureNames: List<String>
        val dataLines: List<String>

        if (hasHeader) {
            featureNames = firstLineParts.map { it.trim().trim('"', '\'') }
            dataLines = lines.drop(1)
        } else {
            featureNames = List(firstLineParts.size) { "feature_$it" }
            dataLines = lines
        }

        val data = mutableListOf<FloatArray>()
        var malformedRows = 0
        for (line in dataLines) {
            if (line.isBlank()) continue
            val parts = splitWithQuotes(line, delimiter)
            if (parts.size != featureNames.size) {
                malformedRows++
            }
            val values = FloatArray(featureNames.size)
            for (i in featureNames.indices) {
                values[i] = parts.getOrNull(i)?.trim()?.trim('"', '\'')?.toFloatOrNull() ?: 0f
            }
            data.add(values)
        }

        // Guard against highly inconsistent CSV structure.
        if (data.isNotEmpty() && malformedRows > data.size / 3) {
            throw IllegalArgumentException(
                "Column mismatch detected in $malformedRows of ${data.size} rows. " +
                    "Expected ${featureNames.size} columns."
            )
        }

        return ParseResult(
            data = data,
            featureNames = featureNames,
            rowCount = data.size,
            colCount = featureNames.size
        )
    }

    /**
     * Split a line respecting quoted fields.
     */
    private fun splitWithQuotes(line: String, delimiter: String): List<String> {
        if (delimiter.length > 1) return listOf(line) // json
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == delimiter[0] && !inQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> current.append(ch)
            }
        }
        result.add(current.toString())
        return result
    }

    /**
     * Parse a JSON file (array of objects or array of arrays).
     */
    private fun parseJsonFile(content: String): ParseResult {
        val trimmed = content.trim()

        // Simple JSON array of objects parsing
        if (trimmed.startsWith("[")) {
            return parseJsonArray(trimmed)
        }
        // JSON object with "data" key
        if (trimmed.startsWith("{")) {
            val dataMatch = Regex("\"data\"\\s*:\\s*(\\[.*])").find(trimmed)
            if (dataMatch != null) {
                return parseJsonArray(dataMatch.groupValues[1])
            }
        }
        throw IllegalArgumentException("Unsupported JSON format")
    }

    /**
     * Parse a JSON array (best-effort without full JSON parser).
     */
    private fun parseJsonArray(jsonArray: String): ParseResult {
        // Use Gson for reliable JSON parsing
        val gson = com.google.gson.Gson()
        val type = object : com.google.gson.reflect.TypeToken<List<Map<String, Any>>>() {}.type

        try {
            val records: List<Map<String, Any>> = gson.fromJson(jsonArray, type)
            if (records.isEmpty()) return ParseResult(emptyList(), emptyList(), 0, 0)

            val featureNames = records.first().keys.toList()
            val data = records.map { record ->
                FloatArray(featureNames.size) { i ->
                    val value = record[featureNames[i]]
                    when (value) {
                        is Number -> value.toFloat()
                        is String -> value.toFloatOrNull() ?: 0f
                        else -> 0f
                    }
                }
            }

            return ParseResult(data, featureNames, data.size, featureNames.size)
        } catch (e: Exception) {
            // Fallback: try as array of arrays
            val arrayType = object : com.google.gson.reflect.TypeToken<List<List<Any>>>() {}.type
            val arrays: List<List<Any>> = gson.fromJson(jsonArray, arrayType)
            if (arrays.isEmpty()) return ParseResult(emptyList(), emptyList(), 0, 0)

            val colCount = arrays.first().size
            val featureNames = List(colCount) { "feature_$it" }
            val data = arrays.map { row ->
                FloatArray(colCount) { i ->
                    when (val v = row.getOrNull(i)) {
                        is Number -> v.toFloat()
                        is String -> v.toFloatOrNull() ?: 0f
                        else -> 0f
                    }
                }
            }
            return ParseResult(data, featureNames, data.size, colCount)
        }
    }

    companion object {
        val SUPPORTED_EXTENSIONS = setOf("csv", "json", "tsv", "txt", "psv", "dat")

        fun isSupported(file: File): Boolean {
            return file.extension.lowercase() in SUPPORTED_EXTENSIONS
        }
    }
}
