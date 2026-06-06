package com.example.drowsiness.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log

class SvmInferenceManager(context: Context) {
    private val ortEnv: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var ortSession: OrtSession? = null

    var modelLoadStatus: String = "PENDING"
    var exceptionClass: String? = null
    var exceptionMessage: String? = null

    fun isSessionNull(): Boolean = ortSession == null

    // Hardcoded StandardScaler values
    private val EAR_MEAN = 0.28035561f
    private val MAR_MEAN = 0.38348451f
    private val EAR_SCALE = 0.06206596f
    private val MAR_SCALE = 0.0798009f

    init {
        try {
            val modelBytes = context.assets.open("drowsiness_model.onnx").readBytes()
            ortSession = ortEnv.createSession(modelBytes)
            modelLoadStatus = "SUCCESS"
            Log.d("SVM_DEBUG", "ONNX Model loaded successfully")
        } catch (e: Throwable) {
            modelLoadStatus = "FAILED"
            exceptionClass = e.javaClass.name
            exceptionMessage = e.message ?: "No message"
            Log.e("SVM_DEBUG", "Failed to load ONNX model: ${e.message}", e)
        }
    }

    fun predict(ear: Float, mar: Float, normEar: Float = 0f, normMar: Float = 0f): PredictionResult {
        Log.d("SVM_DEBUG", "Inference Stage 1: Received EAR = $ear, MAR = $mar, normEar = $normEar, normMar = $normMar")
        Log.d("SVM_DEBUG", "Inference Stage 2: Model load status (ortSession is ${if (ortSession != null) "INITIALIZED" else "NULL"})")
        if (ortSession == null) {
            Log.d("SVM_DEBUG", "Inference Stage 2b: Execution stopping at line 33 because ortSession is null. Defaulting to AWAKE (0)")
            val emptyDebug = SvmDebugData(
                rawEar = ear,
                rawMar = mar,
                scaledEar = 0f,
                scaledMar = 0f,
                outputKeys = emptyList(),
                outputValues = mapOf(
                    "diag_ear_before" to String.format(java.util.Locale.US, "%.4f", normEar),
                    "diag_ear_after" to String.format(java.util.Locale.US, "%.4f", ear),
                    "diag_mar_before" to String.format(java.util.Locale.US, "%.4f", normMar),
                    "diag_mar_after" to String.format(java.util.Locale.US, "%.4f", mar)
                ),
                finalPrediction = 0
            )
            return PredictionResult(0, emptyDebug)
        }

        // Apply scaling
        val scaledEar = (ear - EAR_MEAN) / EAR_SCALE
        val scaledMar = (mar - MAR_MEAN) / MAR_SCALE
        Log.d("SVM_DEBUG", "Inference Stage 3: Scaled EAR = $scaledEar, Scaled MAR = $scaledMar")

        val inputArray = arrayOf(floatArrayOf(scaledEar, scaledMar))

        return try {
            val inputName = ortSession?.inputNames?.iterator()?.next() ?: "input"
            Log.d("SVM_DEBUG", "Inference Stage 4: Creating OnnxTensor with input name '$inputName' and tensor data [${scaledEar}, ${scaledMar}]")
            val tensor = OnnxTensor.createTensor(ortEnv, inputArray)
            Log.d("SVM_DEBUG", "Inference Stage 5: Tensor created successfully")
            
            Log.d("SVM_DEBUG", "Inference Stage 6: Running ONNX session inference")
            val result = ortSession?.run(mapOf(inputName to tensor))
            Log.d("SVM_DEBUG", "Inference Stage 7: Inference execution completed successfully")
            
            val keys = result?.map { it.key } ?: emptyList()
            Log.d("SVM_DEBUG", "Inference Stage 8: ONNX Output Keys = $keys")
            val rawValuesMap = mutableMapOf<String, String>()
            
            // Log details and populate raw values map
            result?.forEachIndexed { index, entry ->
                Log.d("SVM_DEBUG", "Inference Stage 8b: OUTPUT_INDEX=$index, KEY=${entry.key}")
            }
            result?.forEach { (key, value) ->
                val rawVal = value.value
                val formattedVal = formatRawValue(rawVal)
                Log.d("SVM_DEBUG", "RAW_${key.uppercase()}=$formattedVal")
                rawValuesMap[key] = formattedVal
            }
            
            // Add temporary diagnostic outputs
            rawValuesMap["diag_ear_before"] = String.format(java.util.Locale.US, "%.4f", normEar)
            rawValuesMap["diag_ear_after"] = String.format(java.util.Locale.US, "%.4f", ear)
            rawValuesMap["diag_mar_before"] = String.format(java.util.Locale.US, "%.4f", normMar)
            rawValuesMap["diag_mar_after"] = String.format(java.util.Locale.US, "%.4f", mar)

            // Extract the prediction using the output key 'label' instead of index
            val labelValue = result?.find { it.key == "label" }?.value?.value
            val prediction = when (labelValue) {
                is LongArray -> labelValue[0].toInt()
                is IntArray -> labelValue[0]
                is FloatArray -> labelValue[0].toInt()
                is Array<*> -> {
                    val firstRow = labelValue[0]
                    when (firstRow) {
                        is LongArray -> firstRow[0].toInt()
                        is IntArray -> firstRow[0]
                        is FloatArray -> firstRow[0].toInt()
                        else -> 0
                    }
                }
                else -> 0
            }

            Log.d("SVM_DEBUG", "FINAL_PREDICTION=$prediction")
            Log.d(
                "SVM_DEBUG",
                "Raw [EAR: $ear, MAR: $mar] | Scaled [EAR: $scaledEar, MAR: $scaledMar] | Prediction: $prediction"
            )

            tensor.close()
            result?.close()
            
            val debugData = SvmDebugData(
                rawEar = ear,
                rawMar = mar,
                scaledEar = scaledEar,
                scaledMar = scaledMar,
                outputKeys = keys,
                outputValues = rawValuesMap,
                finalPrediction = prediction
            )
            PredictionResult(prediction, debugData)
        } catch (e: Exception) {
            Log.e("SVM_DEBUG", "Inference error: ${e.message}")
            val errorDebug = SvmDebugData(
                rawEar = ear,
                rawMar = mar,
                scaledEar = scaledEar,
                scaledMar = scaledMar,
                outputKeys = emptyList(),
                outputValues = mapOf(
                    "error" to (e.message ?: "Unknown error"),
                    "diag_ear_before" to String.format(java.util.Locale.US, "%.4f", normEar),
                    "diag_ear_after" to String.format(java.util.Locale.US, "%.4f", ear),
                    "diag_mar_before" to String.format(java.util.Locale.US, "%.4f", normMar),
                    "diag_mar_after" to String.format(java.util.Locale.US, "%.4f", mar)
                ),
                finalPrediction = 0
            )
            PredictionResult(0, errorDebug)
        }
    }

    private fun formatRawValue(rawVal: Any?): String {
        return when (rawVal) {
            is LongArray -> if (rawVal.size == 1) rawVal[0].toString() else rawVal.contentToString()
            is IntArray -> if (rawVal.size == 1) rawVal[0].toString() else rawVal.contentToString()
            is FloatArray -> if (rawVal.size == 1) rawVal[0].toString() else rawVal.contentToString()
            is Array<*> -> {
                if (rawVal.size == 1) formatRawValue(rawVal[0])
                else rawVal.map { formatRawValue(it) }.toString()
            }
            else -> rawVal?.toString() ?: "null"
        }
    }

    fun close() {
        ortSession?.close()
        ortEnv.close()
    }
}

data class SvmDebugData(
    val rawEar: Float,
    val rawMar: Float,
    val scaledEar: Float,
    val scaledMar: Float,
    val outputKeys: List<String>,
    val outputValues: Map<String, String>,
    val finalPrediction: Int
)

data class PredictionResult(
    val prediction: Int,
    val debugData: SvmDebugData
)

