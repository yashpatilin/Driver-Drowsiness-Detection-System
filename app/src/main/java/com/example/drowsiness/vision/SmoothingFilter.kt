package com.example.drowsiness.vision

/**
 * Lightweight Exponential Moving Average filter for stabilizing high-frequency sensor/vision data.
 * alpha: Smoothing factor between 0.0 and 1.0. Lower = more smoothing.
 */
class SmoothingFilter(private val alpha: Float = 0.4f) {
    private var previousValue: Float? = null

    fun filter(currentValue: Float): Float {
        val prev = previousValue
        if (prev == null) {
            previousValue = currentValue
            return currentValue
        }
        val smoothed = (alpha * currentValue) + ((1.0f - alpha) * prev)
        previousValue = smoothed
        return smoothed
    }
    
    fun reset() {
        previousValue = null
    }
}
