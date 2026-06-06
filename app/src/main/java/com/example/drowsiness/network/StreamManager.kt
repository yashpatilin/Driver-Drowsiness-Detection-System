package com.example.drowsiness.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import android.util.Log

/**
 * StreamManager reads MJPEG frames from the ESP32-CAM over HTTP.
 *
 * Key design decisions to prevent freeze:
 * 1. Uses HttpURLConnection instead of OkHttp to avoid Okio buffering/connection-pool issues
 * 2. Stores activeConnection reference so stopStream() can call disconnect() to unblock read()
 * 3. Frame acquisition is completely independent of UI display and MediaPipe processing
 * 4. Uses BufferedInputStream for efficient socket reads
 */
class StreamManager {

    companion object {
        private const val TAG = "ESP32_STREAM"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000
    }

    private val _streamFlow = MutableStateFlow<Bitmap?>(null)
    val streamFlow: StateFlow<Bitmap?> = _streamFlow

    private var streamJob: Job? = null

    // Held so that stopStream() can call disconnect() to unblock a stuck read()
    @Volatile
    private var activeConnection: HttpURLConnection? = null

    fun startStream(scope: CoroutineScope) {
        stopStream()
        Log.d(TAG, "startStream invoked")
        streamJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    connectAndRead()
                } catch (e: Exception) {
                    Log.d(TAG, "Stream loop error: ${e.javaClass.simpleName}: ${e.message}")
                }
                // Brief delay before reconnect
                if (isActive) {
                    Log.d(TAG, "Reconnecting in 1 s …")
                    delay(1000)
                }
            }
            Log.d(TAG, "Stream coroutine exiting (isActive=false)")
        }
    }

    /**
     * Opens an HTTP connection and continuously reads MJPEG frames.
     * The method blocks until the stream ends, an error occurs, or
     * the connection is forcibly disconnected via [stopStream].
     */
    private fun connectAndRead() {
        val streamUrl = ESP32Config.streamUrl
        Log.d(TAG, "Connecting to $streamUrl")

        val url = URL(streamUrl)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Connection", "keep-alive")
            // Prevent OkHttp-style gzip interference
            setRequestProperty("Accept-Encoding", "identity")
            doInput = true
        }

        activeConnection = connection

        try {
            connection.connect()
            val responseCode = connection.responseCode
            Log.d(TAG, "HTTP $responseCode, Content-Type: ${connection.contentType}")

            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.d(TAG, "Non-200 response: $responseCode")
                return
            }

            val inputStream = BufferedInputStream(connection.inputStream, 16_384)
            readMjpegStream(inputStream)
        } finally {
            activeConnection = null
            try { connection.disconnect() } catch (_: Exception) {}
            Log.d(TAG, "Connection closed & cleaned up")
        }
    }

    /**
     * Byte-level MJPEG parser.  Scans for JPEG SOI (FF D8) and EOI (FF D9) markers.
     * Each complete JPEG is decoded to a Bitmap and pushed into [_streamFlow].
     */
    private fun readMjpegStream(inputStream: InputStream) {
        val buffer = ByteArray(4096)
        var prevByte = -1
        var inJpeg = false
        val jpegBuffer = ByteArrayOutputStream(65_536)
        var frameCount = 0
        var lastLogTime = System.currentTimeMillis()

        Log.d(TAG, "Entering MJPEG read loop")

        while (true) {
            val bytesRead = inputStream.read(buffer)
            if (bytesRead == -1) {
                Log.d(TAG, "read() returned -1 after $frameCount frames — stream ended")
                break
            }

            for (i in 0 until bytesRead) {
                val currentByte = buffer[i].toInt() and 0xFF

                if (!inJpeg) {
                    if (prevByte == 0xFF && currentByte == 0xD8) {
                        inJpeg = true
                        jpegBuffer.reset()
                        jpegBuffer.write(0xFF)
                        jpegBuffer.write(0xD8)
                    }
                } else {
                    jpegBuffer.write(currentByte)
                    if (prevByte == 0xFF && currentByte == 0xD9) {
                        // Complete JPEG frame received
                        inJpeg = false
                        val jpegBytes = jpegBuffer.toByteArray()
                        jpegBuffer.reset()

                        val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                        if (bitmap != null) {
                            frameCount++
                            _streamFlow.value = bitmap

                            // Periodic logging (every 3 seconds or every 30 frames)
                            val now = System.currentTimeMillis()
                            if (frameCount <= 5 || frameCount % 30 == 0 || now - lastLogTime > 3000) {
                                Log.d(TAG, "Frame #$frameCount  ${bitmap.width}x${bitmap.height}  jpegSize=${jpegBytes.size}")
                                lastLogTime = now
                            }
                        } else {
                            Log.w(TAG, "BitmapFactory.decode returned null (jpegSize=${jpegBytes.size})")
                        }
                    }
                }
                prevByte = currentByte
            }
        }
    }

    fun stopStream() {
        Log.d(TAG, "stopStream invoked")
        streamJob?.cancel()
        streamJob = null
        // Force-close the socket so any blocking read() throws immediately
        try { activeConnection?.disconnect() } catch (_: Exception) {}
        activeConnection = null
        _streamFlow.value = null
    }
}
