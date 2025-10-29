package com.onnet.securitycam

import android.os.Bundle
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.onnet.securitycam.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var preferences: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferences = PreferencesManager(this)

        // Set up the toolbar
        setSupportActionBar(binding.topAppBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"

        setupUI()
        loadSettings()
    }

    private fun setupUI() {
        // Resolution dropdown
        val resolutions = arrayOf("480p", "720p", "1080p")
        val resolutionAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, resolutions)
        binding.spinnerResolution.setAdapter(resolutionAdapter)
        binding.spinnerResolution.setOnItemClickListener { _, _, position, _ ->
            preferences.setResolution(resolutions[position])
            Toast.makeText(this, "Resolution set to ${resolutions[position]}", Toast.LENGTH_SHORT).show()
        }

        // FPS dropdown
        val fpsList = arrayOf("15", "30", "60")
        val fpsAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, fpsList)
        binding.spinnerFps.setAdapter(fpsAdapter)
        binding.spinnerFps.setOnItemClickListener { _, _, position, _ ->
            val fps = fpsList[position].toInt()
            preferences.setFps(fps)
            Toast.makeText(this, "FPS set to $fps", Toast.LENGTH_SHORT).show()
        }

        // Bitrate dropdown
        val bitrateOptions = arrayOf("1 Mbps", "2 Mbps", "4 Mbps", "8 Mbps")
        val bitrateValues = arrayOf(1_000_000, 2_000_000, 4_000_000, 8_000_000)
        val bitrateAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, bitrateOptions)
        binding.spinnerBitrate.setAdapter(bitrateAdapter)
        binding.spinnerBitrate.setOnItemClickListener { _, _, position, _ ->
            preferences.setBitrate(bitrateValues[position])
            Toast.makeText(this, "Bitrate set to ${bitrateOptions[position]}", Toast.LENGTH_SHORT).show()
        }

        // Port input
        binding.btnSavePort.setOnClickListener {
            val portText = binding.etPort.text.toString()
            if (portText.isNotEmpty()) {
                try {
                    val port = portText.toInt()
                    if (port in 1024..65535) {
                        preferences.setPort(port)
                        Toast.makeText(this, "Port set to $port", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Port must be between 1024 and 65535", Toast.LENGTH_LONG).show()
                    }
                } catch (e: NumberFormatException) {
                    Toast.makeText(this, "Invalid port number", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Audio toggle
        binding.switchAudio.setOnCheckedChangeListener { _, isChecked ->
            preferences.setAudioEnabled(isChecked)
            Toast.makeText(this, "Audio ${if (isChecked) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
        }

        // Protocol selector
        val protocols = arrayOf(PreferencesManager.PROTOCOL_HLS, PreferencesManager.PROTOCOL_WEBSOCKET, PreferencesManager.PROTOCOL_BOTH)
        val protocolAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, protocols)
        binding.spinnerProtocol.setAdapter(protocolAdapter)
        binding.spinnerProtocol.setOnItemClickListener { _, _, position, _ ->
            val protocol = protocols[position]
            preferences.setStreamProtocol(protocol)
            
            // Update HLS switch to reflect protocol selection
            when (protocol) {
                PreferencesManager.PROTOCOL_HLS -> binding.switchHls.isChecked = true
                PreferencesManager.PROTOCOL_WEBSOCKET -> binding.switchHls.isChecked = false
                PreferencesManager.PROTOCOL_BOTH -> binding.switchHls.isChecked = true
            }
            
            Toast.makeText(this, "Protocol set to $protocol", Toast.LENGTH_SHORT).show()
            
            // Show warning for WebSocket
            if (protocol == PreferencesManager.PROTOCOL_WEBSOCKET) {
                MaterialAlertDialogBuilder(this)
                    .setTitle("WebSocket Mode")
                    .setMessage("WebSocket streaming requires a custom client application. HLS playlist will not be available.")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }

        // HLS toggle (now updates protocol)
        binding.switchHls.setOnCheckedChangeListener { _, isChecked ->
            // Update protocol based on HLS switch
            val currentProtocol = preferences.getStreamProtocol()
            if (isChecked && currentProtocol == PreferencesManager.PROTOCOL_WEBSOCKET) {
                preferences.setStreamProtocol(PreferencesManager.PROTOCOL_HLS)
                binding.spinnerProtocol.setText(PreferencesManager.PROTOCOL_HLS, false)
            } else if (!isChecked && currentProtocol == PreferencesManager.PROTOCOL_HLS) {
                preferences.setStreamProtocol(PreferencesManager.PROTOCOL_WEBSOCKET)
                binding.spinnerProtocol.setText(PreferencesManager.PROTOCOL_WEBSOCKET, false)
            }
            preferences.setHlsEnabled(isChecked)
            Toast.makeText(this, "HLS ${if (isChecked) "enabled" else "disabled"}", Toast.LENGTH_SHORT).show()
        }

        // Reset button
        binding.btnReset.setOnClickListener {
            showResetDialog()
        }

        // Save button
        binding.btnSave.setOnClickListener {
            Toast.makeText(this, "Settings saved successfully", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun loadSettings() {
        // Load current settings
        binding.spinnerResolution.setText(preferences.getResolution(), false)
        binding.spinnerFps.setText(preferences.getFps().toString(), false)
        
        val bitrateText = when (preferences.getBitrate()) {
            1_000_000 -> "1 Mbps"
            2_000_000 -> "2 Mbps"
            4_000_000 -> "4 Mbps"
            8_000_000 -> "8 Mbps"
            else -> "4 Mbps"
        }
        binding.spinnerBitrate.setText(bitrateText, false)
        
        binding.etPort.setText(preferences.getPort().toString())
        binding.switchAudio.isChecked = preferences.isAudioEnabled()
        binding.switchHls.isChecked = preferences.isHlsEnabled()
        binding.spinnerProtocol.setText(preferences.getStreamProtocol(), false)
    }

    private fun showResetDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Reset Settings")
            .setMessage("Are you sure you want to reset all settings to default values?")
            .setPositiveButton("Reset") { _, _ ->
                resetToDefaults()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun resetToDefaults() {
        preferences.setResolution("1080p")
        preferences.setFps(30)
        preferences.setBitrate(4_000_000)
        preferences.setPort(8080)
        preferences.setAudioEnabled(true)
        preferences.setHlsEnabled(true)
        preferences.setStreamProtocol(PreferencesManager.PROTOCOL_HLS)
        
        loadSettings()
        Toast.makeText(this, "Settings reset to defaults", Toast.LENGTH_SHORT).show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
