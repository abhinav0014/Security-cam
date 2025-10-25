package com.onnet.securitycam

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.Executors

class CameraService : Service(), LifecycleOwner {

    private val TAG = "CameraService"
    
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var preferences: PreferencesManager
    private var codec: MediaCodec? = null
    private var hlsWriter: HLSWriter? = null
    private var streamServer: StreamServer? = null
    private var isEncodingActive = false
    
    // Lifecycle implementation
    private val lifecycleRegistry = LifecycleRegistry(this)
    
    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        preferences = PreferencesManager(this)
        startForegroundNotification()
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        
        // Start HTTP server
        startHttpServer()
        
        // Start camera stream
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

    private fun startHttpServer() {
        try {
            val hlsDir = File(filesDir, "hls")
            if (!hlsDir.exists()) {
                hlsDir.mkdirs()
            }
            streamServer = StreamServer(preferences.getPort(), hlsDir, preferences)
            streamServer?.start()
            Log.d(TAG, "HTTP Server started on port ${preferences.getPort()}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start HTTP server: ${e.message}", e)
        }
    }

    private fun startCameraStream() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                
                val resolution = when (preferences.getResolution()) {
                    "480p" -> Size(640, 480)
                    "720p" -> Size(1280, 720)
                    else -> Size(1920, 1080)
                }

                // Setup Image Analysis
                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(resolution)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()

                // Setup encoder
                setupEncoder(resolution)

                imageAnalysis.setAnalyzer(executor) { imageProxy ->
                    try {
                        if (isEncodingActive) {
                            processFrame(imageProxy)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Frame processing error: ${e.message}", e)
                    } finally {
                        imageProxy.close()
                    }
                }

                // Use back camera
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        this,
                        cameraSelector,
                        imageAnalysis
                    )
                    
                    Log.d(TAG, "Camera started successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Camera binding failed: ${e.message}", e)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Camera initialization failed: ${e.message}", e)
            }
        }, executor)
    }

    private fun setupEncoder(resolution: Size) {
        try {
            val width = resolution.width
            val height = resolution.height
            val fps = preferences.getFps()
            val bitrate = preferences.getBitrate()

            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
            format.setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
            )
            format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            format.setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)

            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            codec?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec?.start()

            hlsWriter = HLSWriter(this, File(filesDir, "hls"))
            isEncodingActive = true
            
            startEncodingThread()
            
            Log.d(TAG, "Encoder setup complete: ${width}x${height} @ ${fps}fps, ${bitrate}bps")
        } catch (e: Exception) {
            Log.e(TAG, "Encoder setup failed: ${e.message}", e)
        }
    }

    private fun processFrame(imageProxy: ImageProxy) {
        try {
            val nv21Data = Utils.yuv420ToNv21(imageProxy)
            
            codec?.let { encoder ->
                val inputBufferIndex = encoder.dequeueInputBuffer(10000)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = encoder.getInputBuffer(inputBufferIndex)
                    inputBuffer?.clear()
                    // Fixed: Explicitly specify we're putting a ByteArray
                    inputBuffer?.put(nv21Data, 0, nv21Data.size)
                    
                    encoder.queueInputBuffer(
                        inputBufferIndex,
                        0,
                        nv21Data.size,
                        System.nanoTime() / 1000,
                        0
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Frame processing error: ${e.message}", e)
        }
    }

    private fun startEncodingThread() {
        Thread {
            val bufferInfo = MediaCodec.BufferInfo()
            
            while (isEncodingActive) {
                try {
                    codec?.let { encoder ->
                        val index = encoder.dequeueOutputBuffer(bufferInfo, 10000)
                        
                        when {
                            index >= 0 -> {
                                val outputBuffer: ByteBuffer? = encoder.getOutputBuffer(index)
                                outputBuffer?.let { buffer ->
                                    val data = ByteArray(bufferInfo.size)
                                    buffer.get(data)
                                    buffer.clear()
                                    
                                    hlsWriter?.writeSample(data, bufferInfo)
                                }
                                encoder.releaseOutputBuffer(index, false)
                            }
                            index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                val newFormat = encoder.outputFormat
                                Log.d(TAG, "Output format changed: $newFormat")
                            }
                            index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                                // No output available yet
                            }
                            else -> {
                                Log.w(TAG, "Unexpected output buffer index: $index")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Encoding thread error: ${e.message}", e)
                    break
                }
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        isEncodingActive = false
        
        try {
            codec?.stop()
            codec?.release()
            codec = null
            
            hlsWriter?.close()
            hlsWriter = null
            
            streamServer?.stop()
            streamServer = null
            
            executor.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping service: ${e.message}", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
}