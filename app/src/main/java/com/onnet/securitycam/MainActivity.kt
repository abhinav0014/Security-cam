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

    private var cameraProcessor: CameraProcessor? = null    private var server: EmbeddedServer? = null

    private var pendingSurface: Surface? = null    private var recordingManager: RecordingManager? = null

    private lateinit var viewModel: StreamViewModel    private var cameraProcessor: CameraProcessor? = null

    private lateinit var toolbar: MaterialToolbar    private var pendingSurface: Surface? = null

    private lateinit var btnRecord: MaterialButton    private lateinit var viewModel: StreamViewModel

    private lateinit var tvRecordingStatus: TextView    private lateinit var toolbar: MaterialToolbar

    private lateinit var btnRecord: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {    private lateinit var tvRecordingStatus: TextView

        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        // Initialize ViewModel        setContentView(R.layout.activity_main)

        viewModel = ViewModelProvider(this)[StreamViewModel::class.java]

        // Initialize ViewModel

        // Set up toolbar        viewModel = ViewModelProvider(this)[StreamViewModel::class.java]

        toolbar = findViewById(R.id.topAppBar)

        setSupportActionBar(toolbar)        // Set up toolbar

        toolbar = findViewById(R.id.topAppBar)

        // Initialize views        setSupportActionBar(toolbar)

        btnRecord = findViewById(R.id.btnRecord)

        tvRecordingStatus = findViewById(R.id.tvRecordingStatus)        // Initialize views

        // Create camera processor for JPEG frames        tvRecordingStatus = findViewById(R.id.tvRecordingStatus)

        cameraProcessor = CameraProcessor(this) { jpeg ->

            // Forward frames to server and recording        // Create camera processor for JPEG frames

            try {        cameraProcessor = CameraProcessor(this) { jpeg ->

                server?.updateFrame(jpeg)            // Forward frames to server and recording

            } catch (e: Exception) {            try {

                Log.e("MainActivity", "Error updating server frame", e)                server?.updateFrame(jpeg)

            }            } catch (e: Exception) {

                Log.e("MainActivity", "Error updating server frame", e)

            try {            }

                recordingManager?.processJpegFrame(jpeg)

            } catch (e: Exception) {            try {

                Log.e("MainActivity", "Error processing recording frame", e)                recordingManager?.processJpegFrame(jpeg)

            }            } catch (e: Exception) {

        }                Log.e("MainActivity", "Error processing recording frame", e)

            }

        recordingManager = RecordingManager(this)        }



        // Set up recording button        recordingManager = RecordingManager(this)

        btnRecord.setOnClickListener {

            toggleRecording()        // Set up recording button

        }        btnRecord.setOnClickListener {

            if (recordingManager?.isRecording() == true) {

        // Check camera permission and request if needed                recordingManager?.stopRecording()

        if (!PermissionHelper.hasAll(this)) {                btnRecord.text = "Start Recording"

            PermissionHelper.request(this)                tvRecordingStatus.text = "Not recording"

        } else {            } else {

            startServer()                recordingManager?.startRecording(CameraSettings().recording)

        }                btnRecord.text = "Stop Recording"

    }                tvRecordingStatus.text = "Recording..."

            }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {        }

        menuInflater.inflate(R.menu.main_menu, menu)            // Obtain the ViewModel via Compose helper which uses the Activity's default ViewModelStore

        return true        // Check camera permission and request if needed

    }        if (!PermissionHelper.hasAll(this)) {

            PermissionHelper.request(this)

    override fun onOptionsItemSelected(item: MenuItem): Boolean {        } else {

        return when (item.itemId) {            startServer()

            R.id.action_record -> {        }

                toggleRecording()        }

                true    }

            }

            R.id.action_settings -> {    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {

                // TODO: Show settings fragment/dialog        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

                Snackbar.make(toolbar, "Settings clicked", Snackbar.LENGTH_SHORT).show()        if (requestCode == PermissionHelper.REQUEST_PERMISSIONS) {

                true            val denied = PermissionHelper.getDeniedPermissions(this)

            }            if (denied.isEmpty()) {

            R.id.action_server -> {                // Start server and camera if we have a pending surface

                // Show server status                if (server == null) {

                val serverRunning = server != null                    server = EmbeddedServer(port = 8080, context = applicationContext)

                Snackbar.make(toolbar,                     try { server?.start(30000) } catch (e: Exception) { /* ignore */ }

                    if (serverRunning) "Server is running on port 8080"                 }

                    else "Server is not running",

                    Snackbar.LENGTH_LONG                pendingSurface?.let { surface ->

                ).show()                    cameraProcessor?.start(CameraSettings(), surface)

                true                    pendingSurface = null

            }                }

            R.id.action_about -> {            }

                Snackbar.make(toolbar, "SecurityCam v1.0", Snackbar.LENGTH_SHORT).show()        }

                true    }

            }

            else -> super.onOptionsItemSelected(item)    override fun onDestroy() {

        }        super.onDestroy()

    }        try {

            cameraProcessor?.release()

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {        } catch (e: Exception) { /* ignore */ }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults)        try { recordingManager?.release() } catch (e: Exception) { }

        if (requestCode == PermissionHelper.REQUEST_PERMISSIONS) {        try { server?.stop() } catch (e: Exception) { }

            val denied = PermissionHelper.getDeniedPermissions(this)    }

            if (denied.isEmpty()) {}
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
        } catch (e: Exception) { /* ignore */ }
        try { recordingManager?.release() } catch (e: Exception) { }
        try { server?.stop() } catch (e: Exception) { }
    }
}