package com.example.drowsiness.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.drowsiness.R
import com.example.drowsiness.bluetooth.BluetoothService
import com.example.drowsiness.data.AppDatabase
import com.example.drowsiness.data.MainRepository
import com.example.drowsiness.databinding.ActivityMainBinding
import android.bluetooth.BluetoothManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    
    val viewModelFactory: MainViewModelFactory by lazy {
        val db = AppDatabase.getDatabase(applicationContext)
        val repo = MainRepository(db.drowsinessDao())
        val btManager = getSystemService(BluetoothManager::class.java)
        val btService = BluetoothService(this, btManager?.adapter)
        
        // Short-timeout client for API calls and ping — never shares connections with the stream
        val apiClient = okhttp3.OkHttpClient.Builder()
            .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        val connectivityManager = com.example.drowsiness.network.ConnectivityManager(apiClient)
        val streamManager = com.example.drowsiness.network.StreamManager()
        val esp32ApiService = com.example.drowsiness.network.ESP32ApiService(apiClient)
        
        MainViewModelFactory(applicationContext, repo, btService, connectivityManager, streamManager, esp32ApiService)
    }
    
    val viewModel: MainViewModel by viewModels { viewModelFactory }

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

        checkPermissions()
        
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        
        binding.bottomNav.setOnItemSelectedListener { item ->
            val navOptions = androidx.navigation.NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setRestoreState(true)
                .setPopUpTo(navController.graph.startDestinationId, false, true)
                .setEnterAnim(android.R.anim.fade_in)
                .setExitAnim(android.R.anim.fade_out)
                .setPopEnterAnim(android.R.anim.fade_in)
                .setPopExitAnim(android.R.anim.fade_out)
                .build()
            try {
                navController.navigate(item.itemId, null, navOptions)
                true
            } catch (e: IllegalArgumentException) {
                false
            }
        }
        
        // Keep the selected state synced if navigated by other means
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val menu = binding.bottomNav.menu
            for (i in 0 until menu.size()) {
                val item = menu.getItem(i)
                if (item.itemId == destination.id) {
                    item.isChecked = true
                    break
                }
            }
        }
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
}
