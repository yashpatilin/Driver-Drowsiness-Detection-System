package com.example.drowsiness.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.drowsiness.bluetooth.BluetoothService
import com.example.drowsiness.data.AppDatabase
import com.example.drowsiness.data.MainRepository
import com.example.drowsiness.databinding.ActivityMainBinding
import com.example.drowsiness.ml.FaceDetectionHelper
import com.example.drowsiness.ml.EARCalculator
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import kotlinx.coroutines.launch
import android.bluetooth.BluetoothManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    
    private val viewModel: MainViewModel by viewModels {
        val db = AppDatabase.getDatabase(applicationContext)
        val repo = MainRepository(db.drowsinessDao())
        val btManager = getSystemService(BluetoothManager::class.java)
        val btService = BluetoothService(this, btManager?.adapter)
        MainViewModelFactory(repo, btService)
    }

    private lateinit var logAdapter: LogAdapter
    private lateinit var faceDetectionHelper: FaceDetectionHelper
    private lateinit var cameraExecutor: ExecutorService
    
    private val graphEntries = mutableListOf<Entry>()
    private var timeIndex = 0f

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            Toast.makeText(this, "Permissions Granted", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        faceDetectionHelper = FaceDetectionHelper()
        cameraExecutor = Executors.newSingleThreadExecutor()

        checkPermissions()
        setupUI()
        setupGraph()
        observeViewModel()
    }

    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.INTERNET,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.CAMERA
        )
        if (permissions.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            requestPermissionLauncher.launch(permissions)
        }
    }

    private fun setupUI() {
        binding.btnConnectStream.setOnClickListener {
            viewModel.toggleCamera()
        }

        binding.btnConnectBluetooth.setOnClickListener {
            val mac = binding.etBluetoothMac.text.toString()
            viewModel.connectBluetooth(mac)
        }

        logAdapter = LogAdapter(emptyList())
        binding.rvLogs.layoutManager = LinearLayoutManager(this)
        binding.rvLogs.adapter = logAdapter
    }
    
    private fun setupGraph() {
        binding.earChart.description.isEnabled = false
        binding.earChart.setTouchEnabled(false)
        binding.earChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        binding.earChart.axisRight.isEnabled = false
        binding.earChart.axisLeft.axisMinimum = 0f
        binding.earChart.axisLeft.axisMaximum = 0.5f
    }

    private fun updateGraph(ear: Float) {
        graphEntries.add(Entry(timeIndex++, ear))
        if(graphEntries.size > 50) graphEntries.removeAt(0)
        
        val dataSet = LineDataSet(graphEntries, "EAR Value").apply {
            color = Color.BLUE
            setDrawCircles(false)
            lineWidth = 2f
            setDrawValues(false)
        }
        
        binding.earChart.data = LineData(dataSet)
        binding.earChart.invalidate()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.isStreamingFlow.collect { isStreaming ->
                        binding.btnConnectStream.text = if (isStreaming) "Stop Stream" else "View Stream"
                        if (isStreaming) {
                            startCamera()
                        } else {
                            stopCamera()
                        }
                    }
                }
                launch {
                    viewModel.toastMessage.collect { msg ->
                        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                    }
                }
                launch {
                    viewModel.currentEar.collect { ear ->
                        updateGraph(ear)
                    }
                }
                launch {
                    viewModel.status.collect { status ->
                        binding.tvDriverStatus.text = status
                        binding.tvDriverStatus.setBackgroundColor(
                            if (status == "AWAKE") Color.parseColor("#4CAF50") else Color.parseColor("#F44336")
                        )
                    }
                }
                launch {
                    viewModel.totalDrowsyTime.collect { duration ->
                        val totalSecs = duration / 1000
                        val mins = totalSecs / 60
                        val secs = totalSecs % 60
                        binding.tvTotalDrowsyTime.text = String.format(java.util.Locale.getDefault(), "%02d:%02d", mins, secs)
                    }
                }
                launch {
                    viewModel.bluetoothConnected.collect { isConnected ->
                        binding.btnConnectBluetooth.text = if (isConnected) "Connected" else "Connect BT"
                        binding.tvBluetoothStatus.text = if (isConnected) "Status: Connected" else "Status: Disconnected"
                        binding.tvBluetoothStatus.setTextColor(if (isConnected) Color.parseColor("#4CAF50") else Color.BLACK)
                    }
                }
                launch {
                    viewModel.logs.collect { logs ->
                        logAdapter.updateData(logs)
                    }
                }
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
                }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImageProxy(imageProxy)
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )
            } catch (exc: Exception) {
                Toast.makeText(this, "Use case binding failed", Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        val cameraProvider = cameraProviderFuture.get()
        cameraProvider.unbindAll()
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            lifecycleScope.launch {
                val contours = faceDetectionHelper.processImage(image)
                if (contours != null) {
                    val leftEAR = EARCalculator.calculateEAR(contours.first)
                    val rightEAR = EARCalculator.calculateEAR(contours.second)
                    if (leftEAR > 0 && rightEAR > 0) {
                        val avgEAR = (leftEAR + rightEAR) / 2f
                        viewModel.updateEAR(avgEAR)
                    }
                }
                imageProxy.close()
            }
        } else {
            imageProxy.close()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
