package com.onnet.securitycam

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.Surface
import android.widget.TextView
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.onnet.securitycam.features.RecordingManager
import com.onnet.securitycam.features.CameraProcessor
import com.onnet.securitycam.config.CameraSettings
import com.onnet.securitycam.viewmodel.StreamViewModel

class MainActivity : AppCompatActivity() {

    private var recordingManager: RecordingManager? = null
    private var cameraProcessor: CameraProcessor? = null
    private var server: EmbeddedServer? = null
    private var pendingSurface: Surface? = null
    
    private lateinit var viewModel: StreamViewModel
    private lateinit var toolbar: MaterialToolbar
    private lateinit var btnRecord: MaterialButton
    private lateinit var tvRecordingStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[StreamViewModel::class.java]

        // Set up toolbar
        toolbar = findViewById(R.id.topAppBar)
        setSupportActionBar(toolbar)

        // Initialize views
        btnRecord = findViewById(R.id.btnRecord)
        tvRecordingStatus = findViewById(R.id.tvRecordingStatus)

        // Create camera processor for JPEG frames
        cameraProcessor = CameraProcessor(this) { jpeg ->
            // Forward frames to server and recording
            try {
                server?.updateFrame(jpeg)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error updating server frame", e)
            }

            try {
                recordingManager?.processJpegFrame(jpeg)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error processing recording frame", e)
            }
        }

        recordingManager = RecordingManager(this)

        // Set up recording button
        btnRecord.setOnClickListener {
            toggleRecording()
        }

        // Check camera permission and request if needed
        if (!PermissionHelper.hasAll(this)) {
            PermissionHelper.request(this)
        } else {
            startServer()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_record -> {
                toggleRecording()
                true
            }
            R.id.action_settings -> {
                // Navigate to Settings Activity
                startActivity(android.content.Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_server -> {
                // Show server status
                val serverRunning = server != null
                Snackbar.make(toolbar, 
                    if (serverRunning) "Server is running on port 8080"
                    else "Server is not running",
                    Snackbar.LENGTH_LONG
                ).show()
                true
            }
            R.id.action_about -> {
                Snackbar.make(toolbar, "SecurityCam v1.0", Snackbar.LENGTH_SHORT).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, 
        permissions: Array<out String>, 
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PermissionHelper.REQUEST_PERMISSIONS) {
            val denied = PermissionHelper.getDeniedPermissions(this)
            if (denied.isEmpty()) {
                startServer()
            } else {
                Snackbar.make(toolbar, "Camera permission required", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun startServer() {
        if (server == null) {
            server = EmbeddedServer(port = 8080, context = applicationContext)
            try {
                server?.start(30000)
                Snackbar.make(toolbar, "Server started on port 8080", Snackbar.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.e("MainActivity", "Error starting server", e)
                Snackbar.make(toolbar, "Failed to start server", Snackbar.LENGTH_LONG).show()
            }
        }

        // Start camera if we have a pending surface
        pendingSurface?.let { surface ->
            cameraProcessor?.start(CameraSettings(), surface)
            pendingSurface = null
        }
    }

    private fun toggleRecording() {
        val recordingActive = recordingManager?.isRecording() == true
        val newRecordingState = !recordingActive

        if (newRecordingState) {
            recordingManager?.startRecording(CameraSettings().recording)
            btnRecord.text = "Stop Recording"
            tvRecordingStatus.text = "Recording..."
            invalidateOptionsMenu() // Update menu icons
        } else {
            recordingManager?.stopRecording()
            btnRecord.text = "Start Recording"
            tvRecordingStatus.text = "Not recording"
            invalidateOptionsMenu()
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val recordingActive = recordingManager?.isRecording() == true
        menu.findItem(R.id.action_record)?.setIcon(
            if (recordingActive) R.drawable.ic_stop
            else R.drawable.ic_fiber_manual_record
        )
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            cameraProcessor?.release()
        } catch (e: Exception) { 
            Log.e("MainActivity", "Error releasing camera processor", e)
        }
        try { 
            recordingManager?.release() 
        } catch (e: Exception) { 
            Log.e("MainActivity", "Error releasing recording manager", e)
        }
        try { 
            server?.stop() 
        } catch (e: Exception) { 
            Log.e("MainActivity", "Error stopping server", e)
        }
    }
}
