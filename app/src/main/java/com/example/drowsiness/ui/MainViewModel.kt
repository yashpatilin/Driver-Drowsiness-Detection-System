package com.example.drowsiness.ui

import  android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.drowsiness.bluetooth.BluetoothService
import android.content.Context
import com.example.drowsiness.data.DrowsinessLog
import com.example.drowsiness.data.MainRepository
import com.example.drowsiness.ml.FaceDetectionHelper
import android.content.SharedPreferences
import com.example.drowsiness.network.ConnectivityManager
import com.example.drowsiness.network.ESP32ApiService
import com.example.drowsiness.network.ESP32Config
import com.example.drowsiness.network.StreamManager
import com.example.drowsiness.vision.MediaPipeProcessor
import com.example.drowsiness.vision.MediaPipeEARCalculator
import com.example.drowsiness.vision.MediaPipeMARCalculator
import com.example.drowsiness.vision.SmoothingFilter
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import android.util.Log
import com.example.drowsiness.ml.SvmInferenceManager
import com.example.drowsiness.ml.SvmDebugData
import com.example.drowsiness.ml.PredictionResult

enum class DriverState {
    AWAKE, DROWSY, RECOVERING
}

enum class ConnectionState {
    DISCONNECTED,
    PROVISIONING,
    CONNECTING,
    CONNECTED,
    FAILED
}

class MainViewModel(
    private val context: Context,
    private val repository: MainRepository,
    private val bluetoothService: BluetoothService,
    private val connectivityManager: ConnectivityManager,
    private val streamManager: StreamManager,
    private val esp32ApiService: ESP32ApiService
) : ViewModel() {


    private val _isStreamingFlow = MutableStateFlow(false)
    val isStreamingFlow: StateFlow<Boolean> = _isStreamingFlow

    private val _toastMessage = kotlinx.coroutines.flow.MutableSharedFlow<String>()
    val toastMessage: kotlinx.coroutines.flow.SharedFlow<String> = _toastMessage

    private val _currentEar = MutableStateFlow(0f)
    val currentEar: StateFlow<Float> = _currentEar

    private val _status = MutableStateFlow("AWAKE")
    val status: StateFlow<String> = _status

    private val _bluetoothConnected = MutableStateFlow(false)
    val bluetoothConnected: StateFlow<Boolean> = _bluetoothConnected
    
    val esp32ConnectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val esp32Connected: StateFlow<Boolean> = connectivityManager.isConnected
    val esp32StreamFlow: StateFlow<Bitmap?> = streamManager.streamFlow
    
    private val mediaPipeProcessor = MediaPipeProcessor(context, viewModelScope)
    val esp32Landmarks: StateFlow<FaceLandmarkerResult?> = mediaPipeProcessor.landmarkResult
    
    val esp32Ear = MutableStateFlow(0f)
    val esp32Mar = MutableStateFlow(0f)
    val svmPredictionFlow = MutableStateFlow(0)
    val esp32DrowsyFramesCountFlow = MutableStateFlow(0)
    val esp32DriverStateFlow = MutableStateFlow(DriverState.AWAKE)
    val svmDebugDataFlow = MutableStateFlow<SvmDebugData?>(null)
    val playgroundResultFlow = MutableStateFlow<SvmDebugData?>(null)
    
    private val svmInferenceManager = SvmInferenceManager(context)
    
    private val earFilter = SmoothingFilter(0.4f)
    private val marFilter = SmoothingFilter(0.4f)
    
    private var currentEsp32Ip: String? = null
    private val prefs: SharedPreferences = context.getSharedPreferences("DrowsinessApp", Context.MODE_PRIVATE)
    
    @Volatile private var currentFrameWidth: Int = 640
    @Volatile private var currentFrameHeight: Int = 480
    
    val logs = repository.recentLogs

    val totalDrowsyTime = MutableStateFlow(0L)

    private val ESP32_EAR_THRESHOLD = 0.17f
    private var esp32DrowsyFramesCount = 0
    private val ESP32_DROWSY_FRAMES_LIMIT = 28
    private var esp32RecoveryFramesCount = 0
    private val ESP32_RECOVERY_FRAMES_LIMIT = 15
    private var esp32DrowsyStartTime: Long = 0L
    private var esp32DriverState = DriverState.AWAKE
    private var consecutiveLostFrames = 0

    private val EAR_THRESHOLD = 0.17f
    private var drowsyFramesCount = 0
    private val DROWSY_FRAMES_LIMIT = 20
    private var drowsyStartTime: Long = 0L
    private var isDrowsy = false

    init {
        viewModelScope.launch(Dispatchers.Default) {
            mediaPipeProcessor.landmarkResult.collect { result ->
                if (result != null && result.faceLandmarks().isNotEmpty()) {
                    consecutiveLostFrames = 0
                    val landmarks = result.faceLandmarks().first()
                    
                    val w = currentFrameWidth
                    val h = currentFrameHeight
                    
                    val normEar = MediaPipeEARCalculator.calculateAverageEAR(landmarks)
                    val normMar = MediaPipeMARCalculator.calculateMAR(landmarks)
                    
                    val rawEar = MediaPipeEARCalculator.calculateAverageEAR(landmarks, w, h)
                    val rawMar = MediaPipeMARCalculator.calculateMAR(landmarks, w, h)
                    
                    // Disable EMA smoothing and send raw EAR/MAR directly to SVM
                    val ear = rawEar
                    val mar = rawMar
                    
                    esp32Ear.value = ear
                    esp32Mar.value = mar
                    
                    val svmResult = svmInferenceManager.predict(ear, mar, normEar, normMar)
                    val svmPrediction = svmResult.prediction
                    svmDebugDataFlow.value = svmResult.debugData
                    
                    if (svmPrediction == 1) {
                        esp32RecoveryFramesCount = 0
                        esp32DrowsyFramesCount++
                        if (esp32DrowsyFramesCount >= ESP32_DROWSY_FRAMES_LIMIT && esp32DriverState != DriverState.DROWSY) {
                            val previousState = esp32DriverState
                            esp32DriverState = DriverState.DROWSY
                            _status.value = "DROWSY"
                            esp32DrowsyStartTime = System.currentTimeMillis()
                            if (previousState == DriverState.AWAKE) {
                                viewModelScope.launch { esp32ApiService.triggerAlertOn() }
                            }
                        }
                    } else {
                        esp32DrowsyFramesCount = 0
                        if (esp32DriverState == DriverState.DROWSY) {
                            esp32DriverState = DriverState.RECOVERING
                        }
                        
                        if (esp32DriverState == DriverState.RECOVERING) {
                            esp32RecoveryFramesCount++
                            if (esp32RecoveryFramesCount >= ESP32_RECOVERY_FRAMES_LIMIT) {
                                esp32DriverState = DriverState.AWAKE
                                _status.value = "AWAKE"
                                esp32RecoveryFramesCount = 0
                                viewModelScope.launch { esp32ApiService.triggerAlertOff() }
                                
                                val duration = System.currentTimeMillis() - esp32DrowsyStartTime
                                if (duration >= 500) {
                                    totalDrowsyTime.value += duration
                                    viewModelScope.launch {
                                        repository.insertLog(DrowsinessLog(
                                            timestamp = esp32DrowsyStartTime,
                                            earValue = ear,
                                            duration = duration,
                                            status = "DROWSY"
                                        ))
                                    }
                                }
                            }
                        }
                    }
                    svmPredictionFlow.value = svmPrediction
                    esp32DrowsyFramesCountFlow.value = esp32DrowsyFramesCount
                    esp32DriverStateFlow.value = esp32DriverState
                } else {
                    consecutiveLostFrames++
                    if (consecutiveLostFrames >= 3) {
                        esp32Ear.value = 0f
                        esp32Mar.value = 0f
                        earFilter.reset()
                        marFilter.reset()
                        svmPredictionFlow.value = 0
                        svmDebugDataFlow.value = null
                        esp32DrowsyFramesCountFlow.value = esp32DrowsyFramesCount
                        esp32DriverStateFlow.value = esp32DriverState
                    } else {
                        Log.d("TRACKING_DEBOUNCE", "Lost face frame ignored. consecutiveLostFrames = $consecutiveLostFrames")
                    }
                }
            }
        }

        // Dedicated frame-processing collector — runs on IO, completely independent of UI.
        // This ensures that even if the Main thread is busy rendering, frame acquisition
        // and MediaPipe processing are never stalled.
        viewModelScope.launch(Dispatchers.IO) {
            streamManager.streamFlow.collect { bitmap ->
                if (bitmap != null) {
                    if (esp32ConnectionState.value == ConnectionState.CONNECTING) {
                        Log.d("ESP32_DEBUG", "First frame received, setting state to CONNECTED")
                        esp32ConnectionState.value = ConnectionState.CONNECTED
                    }
                    // Process every incoming frame for MediaPipe (fire-and-forget to sendFrame)
                    currentFrameWidth = bitmap.width
                    currentFrameHeight = bitmap.height
                    mediaPipeProcessor.sendFrame(bitmap)
                }
            }
        }

        viewModelScope.launch {
            connectivityManager.isConnected.collect { isConnected ->
                Log.d("ESP32_DEBUG", "Connectivity collector received: $isConnected")
                if (isConnected) {
                    // Waiting for first frame to emit CONNECTED
                } else if (esp32ConnectionState.value == ConnectionState.CONNECTING || esp32ConnectionState.value == ConnectionState.CONNECTED) {
                    Log.d("ESP32_DEBUG", "Connection dropped or failed to connect. Setting state to FAILED")
                    esp32ConnectionState.value = ConnectionState.FAILED
                    
                    // Clear overlay state when connection drops
                    mediaPipeProcessor.resetResult()
                    esp32Ear.value = 0f
                    esp32Mar.value = 0f
                    earFilter.reset()
                    marFilter.reset()
                    svmDebugDataFlow.value = null
                }
            }
        }

        val savedIp = prefs.getString("LAST_ESP32_IP", null)
        Log.d("ESP32_DEBUG", "App launched. Saved IP: $savedIp")
        if (savedIp != null) {
            connectEsp32(savedIp)
        } else {
            // No saved config, default to FAILED to trigger provisioning UI
            esp32ConnectionState.value = ConnectionState.FAILED
        }
    }

    fun toggleCamera() {
        val newState = !_isStreamingFlow.value
        _isStreamingFlow.value = newState
        if (!newState) {
            _currentEar.value = 0f
            drowsyFramesCount = 0
            isDrowsy = false
            _status.value = "AWAKE"
            if (_bluetoothConnected.value) {
                viewModelScope.launch { bluetoothService.sendCommand("ALERT_OFF\n") }
            }
        }
    }

    fun updateEAR(ear: Float) {
        if (!_isStreamingFlow.value) return
        
        _currentEar.value = ear
        
        if (ear < EAR_THRESHOLD) {
            drowsyFramesCount++
            if (drowsyFramesCount >= DROWSY_FRAMES_LIMIT && !isDrowsy) {
                isDrowsy = true
                drowsyStartTime = System.currentTimeMillis()
                _status.value = "DROWSY"
                if (_bluetoothConnected.value) {
                    viewModelScope.launch { bluetoothService.sendCommand("ALERT_ON\n") }
                }
            }
        } else {
            drowsyFramesCount = 0
            if (isDrowsy) {
                isDrowsy = false
                _status.value = "AWAKE"
                val currentTime = System.currentTimeMillis()
                val duration = currentTime - drowsyStartTime
                
                if (_bluetoothConnected.value) {
                    viewModelScope.launch { bluetoothService.sendCommand("ALERT_OFF\n") }
                }
                
                if (duration >= 500) {
                    totalDrowsyTime.value += duration
                    viewModelScope.launch {
                        repository.insertLog(DrowsinessLog(
                            timestamp = drowsyStartTime,
                            earValue = ear,
                            duration = duration,
                            status = "DROWSY"
                        ))
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (_bluetoothConnected.value && isDrowsy) {
            viewModelScope.launch { bluetoothService.sendCommand("ALERT_OFF\n") }
        }
        mediaPipeProcessor.clear()
        svmInferenceManager.close()
    }

    fun connectBluetooth(macAddress: String) {
        viewModelScope.launch {
            val success = bluetoothService.connectToDevice(macAddress)
            _bluetoothConnected.value = success
        }
    }

    fun processEsp32Frame(bitmap: Bitmap) {
        currentFrameWidth = bitmap.width
        currentFrameHeight = bitmap.height
        mediaPipeProcessor.sendFrame(bitmap)
    }

    fun connectEsp32(ip: String) {
        Log.d("ESP32_DEBUG", "connectEsp32 invoked with IP: $ip")
        currentEsp32Ip = ip
        ESP32Config.baseIp = ip
        prefs.edit().putString("LAST_ESP32_IP", ip).apply()
        
        esp32ConnectionState.value = ConnectionState.CONNECTING
        Log.d("ESP32_DEBUG", "State set to CONNECTING. Starting stream and ping monitors.")
        
        connectivityManager.startMonitoring(viewModelScope)
        streamManager.startStream(viewModelScope)
    }

    fun disconnectEsp32() {
        Log.d("ESP32_DEBUG", "disconnectEsp32 invoked. Stopping monitors.")
        connectivityManager.stopMonitoring()
        streamManager.stopStream()
        mediaPipeProcessor.resetResult()
        consecutiveLostFrames = 0
        esp32Ear.value = 0f
        esp32Mar.value = 0f
        earFilter.reset()
        marFilter.reset()
        svmPredictionFlow.value = 0
        svmDebugDataFlow.value = null
        esp32DrowsyFramesCountFlow.value = 0
        esp32DriverStateFlow.value = DriverState.AWAKE
        currentEsp32Ip = null
        esp32ConnectionState.value = ConnectionState.DISCONNECTED
    }

    fun provisionWifi(ssid: String, pass: String) {
        viewModelScope.launch {
            Log.d("ESP32_DEBUG", "provisionWifi invoked for SSID: $ssid")
            esp32ConnectionState.value = ConnectionState.PROVISIONING
            val success = esp32ApiService.setWifi(ssid, pass)
            if (success) {
                Log.d("ESP32_DEBUG", "Provisioning API returned success")
                _toastMessage.emit("Provisioning Sent! ESP32 rebooting...")
                esp32ConnectionState.value = ConnectionState.DISCONNECTED
            } else {
                Log.d("ESP32_DEBUG", "Provisioning API failed")
                _toastMessage.emit("Provisioning Failed")
                esp32ConnectionState.value = ConnectionState.FAILED
            }
        }
    }

    fun sendTestAlert() {
        viewModelScope.launch {
            val success = esp32ApiService.triggerAlertOn()
            if (success) {
                _toastMessage.emit("ESP32 Alert Sent")
                kotlinx.coroutines.delay(2000)
                esp32ApiService.triggerAlertOff()
            } else {
                _toastMessage.emit("Failed to reach ESP32")
            }
        }
    }

    fun runPlaygroundPrediction(ear: Float, mar: Float) {
        val result = svmInferenceManager.predict(ear, mar)
        playgroundResultFlow.value = result.debugData
    }

    val svmModelLoadStatus: String
        get() = svmInferenceManager.modelLoadStatus
    val svmExceptionClass: String?
        get() = svmInferenceManager.exceptionClass
    val svmExceptionMessage: String?
        get() = svmInferenceManager.exceptionMessage
    val svmSessionNull: Boolean
        get() = svmInferenceManager.isSessionNull()
}

class MainViewModelFactory(
    private val context: Context,
    private val repository: MainRepository,
    private val bluetoothService: BluetoothService,
    private val connectivityManager: ConnectivityManager,
    private val streamManager: StreamManager,
    private val esp32ApiService: ESP32ApiService
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(context, repository, bluetoothService, connectivityManager, streamManager, esp32ApiService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
