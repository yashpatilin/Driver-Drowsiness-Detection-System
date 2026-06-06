package com.example.drowsiness.vision

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.pow
import kotlin.math.sqrt

object MediaPipeMARCalculator {

    // Inner lip contour indices
    // Horizontal: 78 (left corner), 308 (right corner)
    // Vertical 1: 82 (upper), 87 (lower)
    // Vertical 2: 13 (upper center), 14 (lower center)
    // Vertical 3: 312 (upper), 317 (lower)
    
    fun calculateMAR(landmarks: List<NormalizedLandmark>, width: Int = 1, height: Int = 1): Float {
        if (landmarks.size < 478) return 0f

        val p78 = landmarks[78]
        val p308 = landmarks[308]
        
        val p82 = landmarks[82]
        val p87 = landmarks[87]
        
        val p13 = landmarks[13]
        val p14 = landmarks[14]
        
        val p312 = landmarks[312]
        val p317 = landmarks[317]

        val horizontal = distance(p78, p308, width, height)
        
        val vertical1 = distance(p82, p87, width, height)
        val vertical2 = distance(p13, p14, width, height)
        val vertical3 = distance(p312, p317, width, height)

        if (horizontal == 0f) return 0f

        return (vertical1 + vertical2 + vertical3) / (3.0f * horizontal)
    }

    private fun distance(p1: NormalizedLandmark, p2: NormalizedLandmark, width: Int, height: Int): Float {
        val dx = (p1.x() - p2.x()) * width
        val dy = (p1.y() - p2.y()) * height
        return sqrt(dx.pow(2) + dy.pow(2))
    }
}
