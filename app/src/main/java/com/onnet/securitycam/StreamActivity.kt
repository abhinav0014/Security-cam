package com.onnet.securitycam

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class StreamActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private var server: EmbeddedServer? = null
    private var cameraStreamer: CameraStreamer? = null
    private var usingBack = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stream)

        previewView = findViewById(R.id.previewView)
        val btnStart = findViewById<Button>(R.id.btnStart)
        val btnStop = findViewById<Button>(R.id.btnStop)

        btnStart.setOnClickListener {
            if (!PermissionHelper.hasAll(this)) {
                PermissionHelper.request(this)
                return@setOnClickListener
            }
            startCameraAndServer()
        }

        btnStop.setOnClickListener {
            server?.stop()
            cameraStreamer?.stop()
        }
    }

    private fun startCameraAndServer() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val selector = if (usingBack) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, selector, preview)
            } catch (e: Exception) { e.printStackTrace() }

            cameraStreamer = CameraStreamer(this, previewView)
            cameraStreamer?.start(usingBack)

            server = EmbeddedServer(8080)
            server?.start(30000)

        }, ContextCompat.getMainExecutor(this))
    }
}
