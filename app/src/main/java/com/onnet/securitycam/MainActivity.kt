package com.onnet.securitycam

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.format.Formatter
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.onnet.securitycam.config.SettingsManager
import com.onnet.securitycam.features.CameraProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private var server: EmbeddedServer? = null
    private val settingsManager by lazy { SettingsManager.getInstance(this) }
    private var cameraProcessor: CameraProcessor? = null
    private var serverJob: Job? = null
    
    private lateinit var tvStatus: TextView
    private lateinit var tvInstructions: TextView
    private lateinit var btnAction: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.textView)
        tvInstructions = findViewById(R.id.instructionsText)
        btnAction = findViewById(R.id.button)

        btnAction.setOnClickListener {
            if (server == null) {
                checkAndRequestPermissions()
            } else {
                stopServer()
            }
        }

        // Auto-start if permissions are already granted
        if (PermissionHelper.hasAll(this)) {
            checkBatteryOptimization()
            startServer()
        } else {
            updateUI(false, "Permissions required")
        }
    }

    private fun checkAndRequestPermissions() {
        if (!PermissionHelper.hasAll(this)) {
            if (PermissionHelper.shouldShowRationale(this)) {
                showPermissionRationale()
            } else {
                PermissionHelper.request(this)
            }
        } else {
            checkBatteryOptimization()
            startServer()
        }
    }

    private fun showPermissionRationale() {
        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage("""
                SecurityCam requires the following permissions to function:
                
                • Camera - To capture video feed
                • Microphone - For audio recording
                • Storage - To save recordings
                • Location - For GPS tagging (optional)
                • Notifications - For alerts (Android 13+)
                
                Please grant all permissions to continue.
            """.trimIndent())
            .setPositiveButton("Grant Permissions") { _, _ ->
                PermissionHelper.request(this)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            val packageName = packageName
            
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                AlertDialog.Builder(this)
                    .setTitle("Battery Optimization")
                    .setMessage("For best performance, disable battery optimization for SecurityCam. This ensures the camera service runs continuously.")
                    .setPositiveButton("Settings") { _, _ ->
                        try {
                            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(this, "Could not open battery settings", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("Skip", null)
                    .show()
            }
        }
    }

    private fun startServer() {
        if (server != null) {
            Toast.makeText(this, "Server is already running", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // Initialize camera processor
            cameraProcessor = CameraProcessor(this) { frame ->
                server?.updateFrame(frame)
            }
            cameraProcessor?.start(settingsManager.cameraSettings)

            // Start embedded server
            val port = 8080
            server = EmbeddedServer(port = port, context = applicationContext)
            server?.start(30000)

            val ipAddress = getIpAddress()
            updateUI(true, "Server running at:\nhttp://$ipAddress:$port")
            
            Toast.makeText(this, "Server started successfully", Toast.LENGTH_SHORT).show()

            // Start periodic status updates
            startStatusUpdates()

        } catch (e: Exception) {
            e.printStackTrace()
            updateUI(false, "Failed to start server: ${e.message}")
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            stopServer()
        }
    }

    private fun stopServer() {
        try {
            serverJob?.cancel()
            server?.stop()
            server = null
            cameraProcessor?.stop()
            cameraProcessor = null
            
            updateUI(false, "Server stopped")
            Toast.makeText(this, "Server stopped", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startStatusUpdates() {
        serverJob?.cancel()
        serverJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                val ipAddress = getIpAddress()
                val port = 8080
                updateUI(true, "Server running at:\nhttp://$ipAddress:$port\n\nOpen this URL in any browser on your network")
                delay(5000)
            }
        }
    }

    private fun updateUI(isRunning: Boolean, statusMessage: String) {
        runOnUiThread {
            if (isRunning) {
                tvStatus.text = "🟢 Server Active"
                tvStatus.setTextColor(getColor(android.R.color.holo_green_dark))
                btnAction.text = "Stop Server"
                btnAction.setBackgroundColor(getColor(android.R.color.holo_red_dark))
            } else {
                tvStatus.text = "⚪ Server Offline"
                tvStatus.setTextColor(getColor(android.R.color.darker_gray))
                btnAction.text = "Start Server"
                btnAction.setBackgroundColor(getColor(android.R.color.holo_blue_dark))
            }
            tvInstructions.text = statusMessage
        }
    }

    private fun getIpAddress(): String {
        return try {
            val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            val ipAddress = wifiManager.connectionInfo.ipAddress
            if (ipAddress != 0) {
                Formatter.formatIpAddress(ipAddress)
            } else {
                "Not connected to WiFi"
            }
        } catch (e: Exception) {
            "Unable to get IP"
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PermissionHelper.REQUEST_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "All permissions granted", Toast.LENGTH_SHORT).show()
                checkBatteryOptimization()
                startServer()
            } else {
                val deniedPermissions = PermissionHelper.getDeniedPermissions(this)
                
                AlertDialog.Builder(this)
                    .setTitle("Permissions Denied")
                    .setMessage("The following permissions were denied:\n\n${deniedPermissions.joinToString("\n")}\n\nThe app cannot function without these permissions.")
                    .setPositiveButton("Retry") { _, _ ->
                        PermissionHelper.request(this)
                    }
                    .setNegativeButton("Exit") { _, _ ->
                        finish()
                    }
                    .setCancelable(false)
                    .show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopServer()
    }

    override fun onPause() {
        super.onPause()
        // Keep server running in background
    }

    override fun onResume() {
        super.onResume()
        // Update UI if server is running
        if (server != null) {
            val ipAddress = getIpAddress()
            updateUI(true, "Server running at:\nhttp://$ipAddress:8080")
        }
    }
}