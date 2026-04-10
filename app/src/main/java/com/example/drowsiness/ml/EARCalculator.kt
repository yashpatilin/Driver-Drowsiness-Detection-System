package com.example.drowsiness.ml

import android.graphics.PointF
import kotlin.math.pow
import kotlin.math.sqrt

object EARCalculator {

    fun calculateEAR(contourPoints: List<PointF>): Float {
        if (contourPoints.size != 16) return -1f
        
        // Approximate vertical distances
        val v1 = distance(contourPoints[2], contourPoints[14])
        val v2 = distance(contourPoints[6], contourPoints[10])
        // Horizontal distance
        val h = distance(contourPoints[0], contourPoints[8])
        
        if (h == 0f) return 0f
        
        return (v1 + v2) / (2.0f * h)
    }

    private fun distance(p1: PointF, p2: PointF): Float {
        return sqrt((p1.x - p2.x).pow(2) + (p1.y - p2.y).pow(2))
    }
}
