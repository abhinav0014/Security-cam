package com.onnet.securitycam

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.onnet.securitycam.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var preferences: PreferencesManager
    private var isStreaming = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            startStreaming()
        } else {
            showPermissionDeniedDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        preferences = PreferencesManager(this)
        
        // Set up the toolbar
        setSupportActionBar(binding.topAppBar)
        supportActionBar?.title = "Security Cam"

        setupUI()
        updateUI()
    }

    private fun setupUI() {
        // Start/Stop button
        binding.btnToggleStream.setOnClickListener {
            if (isStreaming) {
                stopStreaming()
            } else {
                checkPermissionsAndStart()
            }
        }

        // Copy URL button
        binding.btnCopyUrl.setOnClickListener {
            copyUrlToClipboard()
        }

        // Refresh status button
        binding.btnRefresh.setOnClickListener {
            updateUI()
            Toast.makeText(this, "Status refreshed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPermissionsAndStart() {
        val requiredPermissions = mutableListOf(
            Manifest.permission.CAMERA
        )

        // Add notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isEmpty()) {
            startStreaming()
        } else {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun startStreaming() {
        try {
            val intent = Intent(this, CameraService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            isStreaming = true
            updateUI()
            Toast.makeText(this, "Streaming started", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to start streaming: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopStreaming() {
        try {
            val intent = Intent(this, CameraService::class.java)
            stopService(intent)
            isStreaming = false
            updateUI()
            Toast.makeText(this, "Streaming stopped", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to stop streaming: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateUI() {
        val ipAddress = Utils.getIPAddress(this)
        val port = preferences.getPort()
        val url = "http://$ipAddress:$port"

        binding.apply {
            tvStatus.text = if (isStreaming) "Streaming Active" else "Stream Stopped"
            tvStatusIndicator.setBackgroundResource(
                if (isStreaming) R.drawable.ic_fiber_manual_record 
                else R.drawable.ic_stop
            )
            tvIpAddress.text = ipAddress
            tvPort.text = port.toString()
            tvUrl.text = url
            tvResolution.text = preferences.getResolution()
            tvFps.text = "${preferences.getFps()} fps"
            tvBitrate.text = Utils.formatBitrate(preferences.getBitrate())
            
            btnToggleStream.text = if (isStreaming) "Stop Streaming" else "Start Streaming"
            btnToggleStream.setBackgroundColor(
                ContextCompat.getColor(
                    this@MainActivity,
                    if (isStreaming) android.R.color.holo_red_dark 
                    else android.R.color.holo_green_dark
                )
            )
        }
    }

    private fun copyUrlToClipboard() {
        val ipAddress = Utils.getIPAddress(this)
        val port = preferences.getPort()
        val url = "http://$ipAddress:$port"
        
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Stream URL", url)
        clipboard.setPrimaryClip(clip)
        
        Toast.makeText(this, "URL copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun showPermissionDeniedDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Permissions Required")
            .setMessage("Camera permission is required for streaming. Please grant the permissions in app settings.")
            .setPositiveButton("Settings") { _, _ ->
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = android.net.Uri.parse("package:$packageName")
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                true
            }
            R.id.action_about -> {
                showAboutDialog()
                true
            }
            R.id.action_server -> {
                updateUI()
                Toast.makeText(this, "Server status refreshed", Toast.LENGTH_SHORT).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showAboutDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("About Security Cam")
            .setMessage("""
                Security Camera Streaming App
                
                Version: 1.0
                
                Features:
                • HLS Video Streaming
                • Customizable resolution & bitrate
                • Web-based viewer
                • Network streaming
                
                Developed with ❤️
            """.trimIndent())
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing && isStreaming) {
            stopStreaming()
        }
    }
}
