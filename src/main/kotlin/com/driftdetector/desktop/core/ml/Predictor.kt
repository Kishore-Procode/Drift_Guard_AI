package com.driftdetector.desktop.core.ml

/**
 * Generic prediction interface for local desktop model runners.
 */
fun interface Predictor {
    fun predict(features: FloatArray): Double
}
