package com.onnet.securitycam

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.onnet.securitycam.config.SettingsManager
import com.onnet.securitycam.features.CameraProcessor
import com.onnet.securitycam.service.CameraService

class MainActivity : AppCompatActivity() {
    private var server: EmbeddedServer? = null
    private val settingsManager by lazy { SettingsManager.getInstance(this) }
    private var cameraProcessor: CameraProcessor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (!PermissionHelper.hasAll(this)) {
            PermissionHelper.request(this)
        } else {
            checkBatteryOptimization()
            setupCamera()
        }

        startServer()
        updateUI()
    }

    private fun checkBatteryOptimization() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val packageName = packageName
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent().apply {
                action = android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                data = android.net.Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }

    private fun setupCamera() {
        cameraProcessor = CameraProcessor(this, onFrame = { frame ->
            server?.updateFrame(frame)
        })
        cameraProcessor?.start(settingsManager.cameraSettings)
    }

    private fun startServer() {
        server = EmbeddedServer(
            port = 8080,
            context = applicationContext
        ).apply {
            try {
                start(30000) // 30 seconds timeout
                updateStatusText("Server running on port 8080")
            } catch (e: Exception) {
                e.printStackTrace()
                updateStatusText("Failed to start server: ${e.message}")
            }
        }
    }

    private fun updateUI() {
        val settings = settingsManager.cameraSettings
        findViewById<TextView>(R.id.textView).text = "Web Server Status"
        findViewById<TextView>(R.id.instructionsText).text = 
            "Connect to this device using a web browser:\nhttp://[device-ip]:8080\n\n" +
            "Current Quality: ${settings.streamQuality}"
        findViewById<Button>(R.id.button).text = "Server is running"
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (PermissionHelper.hasAll(this)) {
            setupCamera()
        }
    }

    private fun updateStatusText(text: String) {
        findViewById<TextView>(R.id.instructionsText).text = text
    }

    override fun onDestroy() {
        server?.stop()
        super.onDestroy()
    }
}