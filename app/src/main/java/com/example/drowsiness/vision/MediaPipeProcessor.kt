package com.example.drowsiness.vision

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MediaPipeProcessor(private val context: Context, private val scope: CoroutineScope) {

    private var faceLandmarker: FaceLandmarker? = null

    private val _landmarkResult = MutableStateFlow<FaceLandmarkerResult?>(null)
    val landmarkResult: StateFlow<FaceLandmarkerResult?> = _landmarkResult

    private val frameFlow = MutableSharedFlow<Bitmap>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private var processingJob: Job? = null
    
    init {
        setupLandmarker()
        startProcessingPipeline()
    }

    private fun setupLandmarker() {
        val baseOptionsBuilder = BaseOptions.builder()
            .setModelAssetPath("face_landmarker.task")
        
        try {
            baseOptionsBuilder.setDelegate(Delegate.GPU)
        } catch (e: Exception) {
            baseOptionsBuilder.setDelegate(Delegate.CPU)
        }

        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptionsBuilder.build())
            .setRunningMode(RunningMode.IMAGE) 
            .setNumFaces(1)
            .setMinFaceDetectionConfidence(0.3f)
            .setMinTrackingConfidence(0.3f)
            .setMinFacePresenceConfidence(0.3f)
            .build()

        try {
            faceLandmarker = FaceLandmarker.createFromOptions(context, options)
        } catch (e: Exception) {
            try {
                // Revert to CPU if GPU fails
                baseOptionsBuilder.setDelegate(Delegate.CPU)
                val cpuOptions = FaceLandmarker.FaceLandmarkerOptions.builder()
                    .setBaseOptions(baseOptionsBuilder.build())
                    .setRunningMode(RunningMode.IMAGE)
                    .setNumFaces(1)
                    .setMinFaceDetectionConfidence(0.3f)
                    .setMinTrackingConfidence(0.3f)
                    .setMinFacePresenceConfidence(0.3f)
                    .build()
                faceLandmarker = FaceLandmarker.createFromOptions(context, cpuOptions)
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
    }

    private fun startProcessingPipeline() {
        processingJob = scope.launch(Dispatchers.Default) {
            frameFlow.conflate().collect { bitmap ->
                processFrame(bitmap)
            }
        }
    }

    fun sendFrame(bitmap: Bitmap) {
        frameFlow.tryEmit(bitmap)
    }

    private suspend fun processFrame(bitmap: Bitmap) {
        withContext(Dispatchers.Default) {
            try {
                faceLandmarker?.let { landmarker ->
                    val mpBitmap = if (bitmap.config != Bitmap.Config.ARGB_8888) {
                        bitmap.copy(Bitmap.Config.ARGB_8888, false)
                    } else bitmap

                    val mpImage = BitmapImageBuilder(mpBitmap).build()
                    val result = landmarker.detect(mpImage)
                    
                    if (result.faceLandmarks().isEmpty()) {
                        _landmarkResult.value = null
                    } else {
                        _landmarkResult.value = result
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun clear() {
        processingJob?.cancel()
        faceLandmarker?.close()
        faceLandmarker = null
        _landmarkResult.value = null
    }

    fun resetResult() {
        _landmarkResult.value = null
    }
}
