package com.onnet.securitycam

import android.content.Context
import android.graphics.Bitmap
import androidx.camera.view.PreviewView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

class CameraStreamer(private val context: Context, private val previewView: PreviewView) {
    @Volatile
    private var latestJpeg: ByteArray? = null

    private var captureJob: Job? = null
    private var useBackCamera = false

    fun start(backCamera: Boolean = false) {
        useBackCamera = backCamera
        // Start camera via CameraX Preview; PreviewView will attach automatically when using CameraX in StreamActivity
        // Start periodic capture of PreviewView bitmap
        captureJob?.cancel()
        captureJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                try {
                    val bmp: Bitmap? = previewView.bitmap
                    if (bmp != null) {
                        val baos = ByteArrayOutputStream()
                        bmp.compress(Bitmap.CompressFormat.JPEG, 70, baos)
                        latestJpeg = baos.toByteArray()
                    }
                } catch (_: Exception) {
                }
                delay(200) // ~5 FPS
            }
        }
    }

    fun switchCamera() {
        useBackCamera = !useBackCamera
        // The actual camera selector change should be managed by the activity binding CameraX lifecycle.
        // StreamActivity will re-bind the camera when switch requested.
    }

    fun getLatestJpeg(): ByteArray? = latestJpeg

    fun stop() {
        captureJob?.cancel()
    }
}

