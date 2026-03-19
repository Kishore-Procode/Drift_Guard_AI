package com.driftdetector.desktop.util

import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

object FileUtils {

    val MODEL_EXTENSIONS = setOf("tflite", "onnx", "h5", "pb", "pt", "pth")
    val DATA_EXTENSIONS = setOf("csv", "json", "tsv", "txt", "psv", "dat")

    fun isValidModelFile(file: File): Boolean {
        return file.exists() && file.isFile && file.extension.lowercase() in MODEL_EXTENSIONS
    }

    fun isValidDataFile(file: File): Boolean {
        return file.exists() && file.isFile && file.extension.lowercase() in DATA_EXTENSIONS
    }

    fun isValidCsvFile(file: File): Boolean {
        return file.exists() && file.extension.lowercase() == "csv" && file.isFile
    }

    fun isValidJsonFile(file: File): Boolean {
        return file.exists() && file.extension.lowercase() == "json" && file.isFile
    }

    fun getFileSize(file: File): String {
        return when {
            file.length() < 1024 -> "${file.length()} B"
            file.length() < 1024 * 1024 -> "${file.length() / 1024} KB"
            else -> "${file.length() / (1024 * 1024)} MB"
        }
    }

    fun readCsvHeaders(file: File): List<String> {
        return try {
            file.readLines().firstOrNull()?.split(",") ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun countCsvRows(file: File): Int {
        return try {
            file.readLines().size - 1
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Open a file chooser dialog for model files.
     */
    fun chooseModelFile(): File? {
        val chooser = JFileChooser().apply {
            dialogTitle = "Select ML Model File"
            fileFilter = FileNameExtensionFilter(
                "Model Files (${MODEL_EXTENSIONS.joinToString(", ") { ".$it" }})",
                *MODEL_EXTENSIONS.toTypedArray()
            )
        }
        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }

    /**
     * Open a file chooser dialog for data files.
     */
    fun chooseDataFile(): File? {
        val chooser = JFileChooser().apply {
            dialogTitle = "Select Data File"
            fileFilter = FileNameExtensionFilter(
                "Data Files (${DATA_EXTENSIONS.joinToString(", ") { ".$it" }})",
                *DATA_EXTENSIONS.toTypedArray()
            )
        }
        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }

    /**
     * Open a save dialog for export files.
     */
    fun chooseSaveFile(defaultName: String, extension: String): File? {
        val chooser = JFileChooser().apply {
            dialogTitle = "Save As"
            selectedFile = File(defaultName)
            fileFilter = FileNameExtensionFilter("$extension files", extension)
        }
        return if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            val file = chooser.selectedFile
            if (file.extension.isEmpty()) File("${file.absolutePath}.$extension") else file
        } else null
    }
}
