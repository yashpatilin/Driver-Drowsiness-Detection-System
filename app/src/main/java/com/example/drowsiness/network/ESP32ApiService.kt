package com.example.drowsiness.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import android.util.Log

class ESP32ApiService(private val client: OkHttpClient) {

    suspend fun triggerAlertOn(): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(ESP32Config.alertOnUrl).build()
        try {
            Log.d("ESP32_DEBUG", "Sending ALERT ON to ${ESP32Config.alertOnUrl}")
            client.newCall(request).execute().use { response ->
                val success = response.isSuccessful
                Log.d("ESP32_DEBUG", "ALERT ON result: $success")
                success
            }
        } catch (e: IOException) {
            Log.d("ESP32_DEBUG", "ALERT ON failed: ${e.message}")
            false
        }
    }

    suspend fun triggerAlertOff(): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(ESP32Config.alertOffUrl).build()
        try {
            Log.d("ESP32_DEBUG", "Sending ALERT OFF to ${ESP32Config.alertOffUrl}")
            client.newCall(request).execute().use { response ->
                val success = response.isSuccessful
                Log.d("ESP32_DEBUG", "ALERT OFF result: $success")
                success
            }
        } catch (e: IOException) {
            Log.d("ESP32_DEBUG", "ALERT OFF failed: ${e.message}")
            false
        }
    }

    suspend fun setWifi(ssid: String, pass: String): Boolean = withContext(Dispatchers.IO) {
        val url = ESP32Config.setWifiUrl(ssid, pass)
        val request = Request.Builder().url(url).build()
        try {
            Log.d("ESP32_DEBUG", "Sending SET WIFI to $url")
            client.newCall(request).execute().use { response ->
                val success = response.isSuccessful
                Log.d("ESP32_DEBUG", "SET WIFI result: $success")
                success
            }
        } catch (e: IOException) {
            Log.d("ESP32_DEBUG", "SET WIFI failed: ${e.message}")
            false
        }
    }

    suspend fun resetWifi(): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(ESP32Config.resetWifiUrl).build()
        try {
            Log.d("ESP32_DEBUG", "Sending RESET WIFI to ${ESP32Config.resetWifiUrl}")
            client.newCall(request).execute().use { response ->
                val success = response.isSuccessful
                Log.d("ESP32_DEBUG", "RESET WIFI result: $success")
                success
            }
        } catch (e: IOException) {
            Log.d("ESP32_DEBUG", "RESET WIFI failed: ${e.message}")
            false
        }
    }
}
