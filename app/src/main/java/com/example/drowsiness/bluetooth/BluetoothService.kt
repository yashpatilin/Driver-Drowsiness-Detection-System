package com.example.drowsiness.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.util.UUID

class BluetoothService(private val context: Context, private val adapter: BluetoothAdapter?) {

    private var socket: BluetoothSocket? = null
    private var outStream: OutputStream? = null
    
    private val SPP_UUID = java.util.UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    suspend fun connectToDevice(macAddress: String): Boolean = withContext(Dispatchers.IO) {
        if (adapter == null) return@withContext false
        
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return@withContext false
        }
        
        try {
            val device: BluetoothDevice = adapter.getRemoteDevice(macAddress)
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            adapter.cancelDiscovery()
            socket?.connect()
            outStream = socket?.outputStream
            true
        } catch (e: Exception) {
            e.printStackTrace()
            socket?.close()
            socket = null
            false
        }
    }

    suspend fun sendCommand(command: String) = withContext(Dispatchers.IO) {
        try {
            outStream?.write(command.toByteArray())
            outStream?.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun disconnect() {
        try {
            outStream?.close()
            socket?.close()
            socket = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
