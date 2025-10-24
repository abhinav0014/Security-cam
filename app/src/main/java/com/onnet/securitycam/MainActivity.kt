package com.onnet.securitycam

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.Surface
import android.view.TextureView
import android.widget.TextView
import android.util.Log
import android.graphics.SurfaceTexture
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.chip.Chip
import com.google.android.material.card.MaterialCardView
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import java.io.ByteArrayOutputStream
import android.graphics.ImageFormat
import android.graphics.YuvImage
import java.net.NetworkInterface
import java.net.Inet4Address
import androidx.lifecycle.ViewModel

private const val TAG = "MainActivity"

class MainActivity : AppCompatActivity(), TextureView.SurfaceTextureListener {
    // Views
    private lateinit var viewModel: StreamViewModel
    private lateinit var toolbar: MaterialToolbar
    private lateinit var previewCard: MaterialCardView
    private lateinit var previewView: TextureView
    private lateinit var btnRecord: MaterialButton
    private lateinit var statusCard: MaterialCardView
    private lateinit var tvRecordingStatus: TextView
    private lateinit var tvStreamUrl: TextView
    private lateinit var streamStatusChip: Chip

    // State
    private var recordingActive = false
    private var isPreviewReady = false
    private var cameraProvider: ProcessCameraProvider? = null
    private var server: CameraServer? = null

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        isPreviewReady = true
        startCamera()
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        // Handle texture size changes if needed
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        isPreviewReady = false
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        // Called when the texture is updated
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[StreamViewModel::class.java]

        // Set up views
        toolbar = findViewById(R.id.topAppBar)
        previewView = previewCard.findViewById(R.id.previewView)
        btnRecord = findViewById(R.id.btnRecord)
        
        // Set up status views
        tvRecordingStatus = findViewById(R.id.tvRecordingStatus)
        tvStreamUrl = findViewById(R.id.tvStreamUrl)
        streamStatusChip = findViewById(R.id.streamStatusChip)

        setSupportActionBar(toolbar)

        // Set up TextureView for preview
        previewView.surfaceTextureListener = this

        btnRecord.setOnClickListener {
            toggleRecording()
        }

        tvStreamUrl.text = "Starting camera..."
        streamStatusChip.text = "Starting..."
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Setup image analysis use case
            val imageAnalysis = ImageAnalysis.Builder()
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(
                        Executors.newSingleThreadExecutor()
                    ) { image ->
                        val jpeg = imageToJpeg(image)
                        viewModel.latestFrame.set(jpeg)
                        image.close()
                    }
                }

            // Preview use case for TextureView
            val preview = Preview.Builder()
                .build()
                .also { preview ->
                    preview.setSurfaceProvider { request ->
                        val surface = Surface(previewView.surfaceTexture)
                        request.provideSurface(surface, Executors.newSingleThreadExecutor()) { }
                    }
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                Log.e("MainActivity", "Camera binding failed", e)
                Snackbar.make(toolbar, "Failed to start camera", Snackbar.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun imageToJpeg(image: ImageProxy): ByteArray {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer
        
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        
        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuv = YuvImage(
            nv21,
            ImageFormat.NV21,
            image.width,
            image.height,
            null
        )

        val out = ByteArrayOutputStream()
        yuv.compressToJpeg(
            android.graphics.Rect(0, 0, image.width, image.height),
            60,
            out
        )
        return out.toByteArray()
    }

    private fun startServer() {
        try {
            server = CameraServer(viewModel.latestFrame)
            server?.start()
            val ip = getLocalIpAddress()
            tvStreamUrl.text = "Stream URL: http://$ip:8080/"
            streamStatusChip.text = "Live"
            Snackbar.make(toolbar, "Server started on port 8080", Snackbar.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to start server", e)
            tvStreamUrl.text = "Server failed to start"
            streamStatusChip.text = "Offline"
            Snackbar.make(toolbar, "Failed to start server", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun toggleRecording() {
        recordingActive = !recordingActive
        if (recordingActive) {
            btnRecord.text = "Stop Recording"
            btnRecord.setIconResource(R.drawable.ic_stop)
            tvRecordingStatus.text = "Recording..."
        } else {
            btnRecord.text = "Start Recording"
            btnRecord.setIconResource(R.drawable.ic_fiber_manual_record)
            tvRecordingStatus.text = "Not recording"
        }
        invalidateOptionsMenu()
    }

    private fun getLocalIpAddress(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress ?: "Unknown"
                    }
                }
            }
            "Unknown"
        } catch (e: Exception) {
            Log.e("MainActivity", "Error getting IP address", e)
            "Unknown"
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
                // TODO: Show settings
                Snackbar.make(toolbar, "Settings clicked", Snackbar.LENGTH_SHORT).show()
                true
            }
            R.id.action_server -> {
                val serverRunning = server?.wasStarted() == true
                Snackbar.make(toolbar,
                    if (serverRunning) "Server running on port 8080"
                    else "Server is stopped",
                    Snackbar.LENGTH_LONG
                ).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        server?.stop()
    }
}

class StreamViewModel : ViewModel() {
    val latestFrame = AtomicReference<ByteArray>()
}
