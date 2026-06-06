package com.example.drowsiness.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult

class LandmarkOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var results: FaceLandmarkerResult? = null
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1

    private val pointPaint = Paint().apply {
        color = Color.GREEN
        strokeWidth = 5f
        style = Paint.Style.FILL
    }
    
    private val keyLandmarks = mutableSetOf<Int>()

    companion object {
        private val LEFT_EYE_INDICES = intArrayOf(362, 385, 387, 263, 373, 380)
        private val RIGHT_EYE_INDICES = intArrayOf(33, 160, 158, 133, 153, 144)
        private val LIP_INDICES = intArrayOf(78, 308, 82, 87, 13, 14, 312, 317)
    }

    init {
        LEFT_EYE_INDICES.forEach { keyLandmarks.add(it) }
        RIGHT_EYE_INDICES.forEach { keyLandmarks.add(it) }
        LIP_INDICES.forEach { keyLandmarks.add(it) }
    }

    fun setResults(faceLandmarkerResult: FaceLandmarkerResult?, iw: Int, ih: Int) {
        results = faceLandmarkerResult
        imageWidth = iw
        imageHeight = ih
        invalidate()
    }

    fun clear() {
        results = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val result = results ?: return
        if (result.faceLandmarks().isEmpty()) return

        // Map normalized coordinates [0.0, 1.0] to FitCenter ImageView dimensions
        val scaleFactor = Math.min(width * 1f / imageWidth, height * 1f / imageHeight)
        
        val scaledWidth = imageWidth * scaleFactor
        val scaledHeight = imageHeight * scaleFactor
        
        val leftOffset = (width - scaledWidth) / 2f
        val topOffset = (height - scaledHeight) / 2f

        for (landmarkList in result.faceLandmarks()) {
            for ((index, landmark) in landmarkList.withIndex()) {
                if (keyLandmarks.contains(index)) {
                    val cx = (landmark.x() * scaledWidth) + leftOffset
                    val cy = (landmark.y() * scaledHeight) + topOffset
                    canvas.drawCircle(cx, cy, 3f, pointPaint)
                }
            }
        }
    }
}
