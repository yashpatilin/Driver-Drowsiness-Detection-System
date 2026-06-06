package com.example.drowsiness.vision

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.pow
import kotlin.math.sqrt

object MediaPipeEARCalculator {

    // Right Eye: [33, 160, 158, 133, 153, 144]
    // 33: outer corner, 133: inner corner
    // 160, 158: upper eyelid
    // 144, 153: lower eyelid
    private val rightEyeIndices = intArrayOf(33, 160, 158, 133, 153, 144)

    // Left Eye: [362, 385, 387, 263, 373, 380]
    // 362: inner corner, 263: outer corner
    // 385, 387: upper eyelid
    // 380, 373: lower eyelid
    private val leftEyeIndices = intArrayOf(362, 385, 387, 263, 373, 380)

    fun calculateAverageEAR(landmarks: List<NormalizedLandmark>, width: Int = 1, height: Int = 1): Float {
        if (landmarks.size < 478) return 0f

        val leftEAR = calculateEyeEAR(landmarks, leftEyeIndices, width, height)
        val rightEAR = calculateEyeEAR(landmarks, rightEyeIndices, width, height)

        return (leftEAR + rightEAR) / 2.0f
    }

    private fun calculateEyeEAR(landmarks: List<NormalizedLandmark>, indices: IntArray, width: Int, height: Int): Float {
        val p1 = landmarks[indices[0]] // Corner 1
        val p2 = landmarks[indices[1]] // Upper 1
        val p3 = landmarks[indices[2]] // Upper 2
        val p4 = landmarks[indices[3]] // Corner 2
        val p5 = landmarks[indices[4]] // Lower 2
        val p6 = landmarks[indices[5]] // Lower 1

        val vertical1 = distance(p2, p6, width, height)
        val vertical2 = distance(p3, p5, width, height)
        val horizontal = distance(p1, p4, width, height)

        if (horizontal == 0f) return 0f

        return (vertical1 + vertical2) / (2.0f * horizontal)
    }

    private fun distance(p1: NormalizedLandmark, p2: NormalizedLandmark, width: Int, height: Int): Float {
        val dx = (p1.x() - p2.x()) * width
        val dy = (p1.y() - p2.y()) * height
        return sqrt(dx.pow(2) + dy.pow(2))
    }
}
