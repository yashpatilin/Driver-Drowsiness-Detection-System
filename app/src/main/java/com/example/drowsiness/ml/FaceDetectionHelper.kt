package com.example.drowsiness.ml

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.tasks.await

class FaceDetectionHelper {

    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
        .build()

    private val detector = FaceDetection.getClient(options)
    
    // Process input image and return face contours (left and right eye points)
    suspend fun processImage(image: InputImage): Pair<List<android.graphics.PointF>, List<android.graphics.PointF>>? {
        return try {
            val faces = detector.process(image).await()
            if (faces.isEmpty()) return null
            
            val face = faces.first()
            val leftEyeContour = face.getContour(FaceContour.LEFT_EYE)?.points
            val rightEyeContour = face.getContour(FaceContour.RIGHT_EYE)?.points
            
            if (leftEyeContour != null && rightEyeContour != null) {
                Pair(leftEyeContour, rightEyeContour)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
