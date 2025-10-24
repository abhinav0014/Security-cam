package com.onnet.securitycam

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.util.Size
import androidx.annotation.RequiresApi
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.Lifecycle
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.Executors


class CameraService : Service() {

    private val TAG = "CameraService"

    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var preferences: PreferencesManager
    private lateinit var codec: MediaCodec
    private var hlsWriter: HLSWriter? = null

    override fun onCreate() {
        super.onCreate()
        preferences = PreferencesManager(this)
        startForegroundNotification()
        startCameraStream()
    }

    private fun startForegroundNotification() {
        val channelId = "camera_stream_channel"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Camera Stream Service",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Security Camera")
            .setContentText("Streaming active on port ${preferences.getPort()}")
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
    }

    private fun startCameraStream() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build()

            val resolution = when (preferences.getResolution()) {
                "480p" -> Size(640, 480)
                "720p" -> Size(1280, 720)
                else -> Size(1920, 1080)
            }

            val imageCapture = ImageCapture.Builder()
                .setTargetResolution(resolution)
                .build()

            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(resolution)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            setupEncoder(resolution)

            imageAnalysis.setAnalyzer(executor) { image ->
                try {
                    val buffer = image.planes[0].buffer
                    // Normally, you'd convert YUV → NV12/NV21 → encode. 
                    // For simplicity, we’re skipping that heavy part here.
                    encodeDummyFrame()
                } catch (e: Exception) {
                    Log.e(TAG, "Frame encode error: ${e.message}")
                } finally {
                    image.close()
                }
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            val lifecycleOwner = object : LifecycleOwner {
                private val lifecycle = LifecycleRegistry(this)
                override fun getLifecycle(): Lifecycle {
                    lifecycle.currentState = Lifecycle.State.RESUMED
                    return lifecycle
                }
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture,
                    imageAnalysis
                )
            } catch (e: Exception) {
                Log.e(TAG, "Camera start failed: ${e.message}")
            }

        }, executor)
    }

    private fun setupEncoder(resolution: Size) {
        val width = resolution.width
        val height = resolution.height
        val fps = preferences.getFps()
        val bitrate = preferences.getBitrate()

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, fps)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)

        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface = codec.createInputSurface()
        codec.start()

        hlsWriter = HLSWriter(this, File(filesDir, "hls"))
        startEncodingThread()
    }

    private fun startEncodingThread() {
        Thread {
            val bufferInfo = MediaCodec.BufferInfo()
            while (true) {
                val index = codec.dequeueOutputBuffer(bufferInfo, 10000)
                if (index >= 0) {
                    val outputBuffer: ByteBuffer = codec.getOutputBuffer(index) ?: continue
                    val data = ByteArray(bufferInfo.size)
                    outputBuffer.get(data)
                    outputBuffer.clear()
                    codec.releaseOutputBuffer(index, false)
                    hlsWriter?.writeSample(data, bufferInfo)
                }
            }
        }.start()
    }

    private fun encodeDummyFrame() {
        // This is a placeholder for YUV → H.264 encoding logic.
        // Actual conversion (YUV to NV21 to Surface) would go here.
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            codec.stop()
            codec.release()
            hlsWriter?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping encoder: ${e.message}")
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
}