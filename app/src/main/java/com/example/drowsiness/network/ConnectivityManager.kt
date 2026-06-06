package com.example.drowsiness.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import android.util.Log

class ConnectivityManager(private val client: OkHttpClient) {

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private var pingJob: Job? = null

    fun startMonitoring(scope: CoroutineScope) {
        stopMonitoring()
        Log.d("ESP32_DEBUG", "Starting connectivity monitoring ping loop")
        pingJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val isReachable = pingESP32()
                if (_isConnected.value != isReachable) {
                    Log.d("ESP32_DEBUG", "Connectivity state changed: $isReachable")
                    _isConnected.value = isReachable
                }
                delay(10000) // ping every 10 seconds (reduced frequency to lower ESP32 contention)
            }
        }
    }

    fun stopMonitoring() {
        Log.d("ESP32_DEBUG", "Stopping connectivity monitoring")
        pingJob?.cancel()
        pingJob = null
        _isConnected.value = false
    }

    private fun pingESP32(): Boolean {
        // Ping root to check if ESP32 is reachable
        val request = Request.Builder().url(ESP32Config.pingUrl).build()
        return try {
            client.newCall(request).execute().use { response ->
                val success = response.isSuccessful
                Log.d("ESP32_DEBUG", "Ping to ${ESP32Config.pingUrl} result: $success")
                success
            }
        } catch (e: IOException) {
            Log.d("ESP32_DEBUG", "Ping to ${ESP32Config.pingUrl} failed: ${e.message}")
            false
        }
    }
}
