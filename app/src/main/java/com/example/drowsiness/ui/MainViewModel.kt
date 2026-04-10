package com.example.drowsiness.ui

import  android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.drowsiness.bluetooth.BluetoothService
import com.example.drowsiness.data.DrowsinessLog
import com.example.drowsiness.data.MainRepository
import com.example.drowsiness.ml.FaceDetectionHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel(
    private val repository: MainRepository,
    private val bluetoothService: BluetoothService
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
    
    val logs = repository.recentLogs

    val totalDrowsyTime = MutableStateFlow(0L)

    private val EAR_THRESHOLD = 0.17f
    private var drowsyFramesCount = 0
    private val DROWSY_FRAMES_LIMIT = 20
    private var drowsyStartTime: Long = 0L
    private var isDrowsy = false

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
    }

    fun connectBluetooth(macAddress: String) {
        viewModelScope.launch {
            val success = bluetoothService.connectToDevice(macAddress)
            _bluetoothConnected.value = success
        }
    }
}

class MainViewModelFactory(
    private val repository: MainRepository,
    private val bluetoothService: BluetoothService
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository, bluetoothService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
