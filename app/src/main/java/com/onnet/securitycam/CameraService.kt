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
import androidx.core.content.ContextCompat
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
    private var webSocketServer: WebSocketServer? = null
    private var isEncodingActive = false
    private var frameCount = 0
    private var audioCapture: AudioCapture? = null
    
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
        
        // Start streaming servers
        startServers()
        
        // Start camera stream
        startCameraStream()
        
        // Start audio capture if enabled
        if (preferences.isAudioEnabled()) {
            startAudioCapture()
        }
    }
    
    private fun startAudioCapture() {
        // Check RECORD_AUDIO permission
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) 
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Cannot start audio capture: RECORD_AUDIO permission not granted")
            return
        }
        try {
            audioCapture = AudioCapture(
                sampleRate = preferences.getAudioSampleRate(),
                bitrate = preferences.getAudioBitrate(),
                onFormatChanged = { format ->
                    hlsWriter?.setAudioFormat(format)
                    webSocketServer?.setAudioFormat(format)
                    Log.d(TAG, "Audio format set: $format")
                }
            ) { data, bufferInfo ->
                hlsWriter?.writeAudioSample(data, bufferInfo)
                webSocketServer?.broadcastAudioFrame(data, bufferInfo)
            }
            audioCapture?.start()
            Log.d(TAG, "Audio capture started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio capture", e)
        }
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
            .setContentText("Streaming video${if (preferences.isAudioEnabled()) " + audio" else ""} on port ${preferences.getPort()}")
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        } else {
            startForeground(1, notification)
        }
    }

    private fun startServers() {
        try {
            val hlsDir = File(filesDir, "hls")
            if (!hlsDir.exists()) {
                hlsDir.mkdirs()
            }
            
            // Clean up old segments
            hlsDir.listFiles()?.forEach { it.delete() }
            
            val protocol = preferences.getStreamProtocol()
            
            // Start HTTP server for HLS if needed
            if (protocol == PreferencesManager.PROTOCOL_HLS || protocol == PreferencesManager.PROTOCOL_BOTH) {
                streamServer = StreamServer(preferences.getPort(), hlsDir, preferences, this)
                streamServer?.start()
                Log.d(TAG, "HTTP Server started on port ${preferences.getPort()}")
                Log.d(TAG, "HLS directory: ${hlsDir.absolutePath}")
            }
            
            // Start WebSocket server if needed
            if (protocol == PreferencesManager.PROTOCOL_WEBSOCKET || protocol == PreferencesManager.PROTOCOL_BOTH) {
                val wsPort = preferences.getWebSocketPort()
                webSocketServer = WebSocketServer(wsPort)
                webSocketServer?.start()
                Log.d(TAG, "WebSocket Server started on port $wsPort")
            }
            
            Log.d(TAG, "Streaming protocol: $protocol")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start servers: ${e.message}", e)
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

                // Setup encoder first
                setupEncoder(resolution)

                // Setup Image Analysis
                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(resolution)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()

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
            
            // Try different color formats for better compatibility
            format.setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
            )
            format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            format.setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1) // Keyframe every 1 second
            format.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)

            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            codec?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec?.start()

            val protocol = preferences.getStreamProtocol()
            if (protocol == PreferencesManager.PROTOCOL_HLS || protocol == PreferencesManager.PROTOCOL_BOTH) {
                val hlsDir = File(filesDir, "hls")
                hlsWriter = HLSWriter(this, hlsDir, preferences.isAudioEnabled())
            }
            
            isEncodingActive = true
            
            startEncodingThread()
            
            Log.d(TAG, "Encoder setup complete: ${width}x${height} @ ${fps}fps, ${bitrate}bps")
        } catch (e: Exception) {
            Log.e(TAG, "Encoder setup failed: ${e.message}", e)
        }
    }

    private fun processFrame(imageProxy: ImageProxy) {
        try {
            if (codec == null || !isEncodingActive) return
            
            val nv21Data = yuv420ToNv21(imageProxy)
            
            codec?.let { encoder ->
                try {
                    val inputBufferIndex = encoder.dequeueInputBuffer(10000)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = encoder.getInputBuffer(inputBufferIndex)
                        inputBuffer?.clear()
                        inputBuffer?.put(nv21Data)
                        
                        encoder.queueInputBuffer(
                            inputBufferIndex,
                            0,
                            nv21Data.size,
                            System.nanoTime() / 1000,
                            0
                        )
                        
                        frameCount++
                        if (frameCount % 30 == 0) {
                            Log.d(TAG, "Processed $frameCount frames")
                        }else{
                            Log.d(TAG, "Processed $frameCount frames")
                        }
                    }else {
                        Log.w(TAG, "No available input buffer")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error queuing input buffer: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Frame processing error: ${e.message}", e)
        }
    }

    private fun yuv420ToNv21(image: ImageProxy): ByteArray {
        val width = image.width
        val height = image.height
        val ySize = width * height
        val uvSize = width * height / 2
        val nv21 = ByteArray(ySize + uvSize)

        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val yRowStride = image.planes[0].rowStride
        val yPixelStride = image.planes[0].pixelStride
        val uvRowStride = image.planes[1].rowStride
        val uvPixelStride = image.planes[1].pixelStride

        // Copy Y plane
        var pos = 0
        for (row in 0 until height) {
            yBuffer.position(row * yRowStride)
            for (col in 0 until width) {
                nv21[pos++] = yBuffer.get(col * yPixelStride)
            }
        }

        // Copy UV planes (interleaved as VU for NV21)
        val uvHeight = height / 2
        val uvWidth = width / 2
        
        for (row in 0 until uvHeight) {
            for (col in 0 until uvWidth) {
                val vIndex = row * uvRowStride + col * uvPixelStride
                val uIndex = row * uvRowStride + col * uvPixelStride
                nv21[pos++] = vBuffer.get(vIndex)
                nv21[pos++] = uBuffer.get(uIndex)
            }
        }

        yBuffer.rewind()
        uBuffer.rewind()
        vBuffer.rewind()

        return nv21
    }

    private fun startEncodingThread() {
        Thread {
            val bufferInfo = MediaCodec.BufferInfo()
            var outputCount = 0
            
            Log.d(TAG, "Encoding thread started")
            
            while (isEncodingActive) {
                try {
                    codec?.let { encoder ->
                        val index = encoder.dequeueOutputBuffer(bufferInfo, 10000)
                        
                        when {
                            index >= 0 -> {
                                val outputBuffer: ByteBuffer? = encoder.getOutputBuffer(index)
                                outputBuffer?.let { buffer ->
                                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                        // Skip codec config frames - they are handled in format change
                                        encoder.releaseOutputBuffer(index, false)
                                        return@let
                                    }
                                    
                                    // Set proper buffer position and limit
                                    buffer.position(bufferInfo.offset)
                                    buffer.limit(bufferInfo.offset + bufferInfo.size)
                                    
                                    val data = ByteArray(bufferInfo.size)
                                    buffer.get(data)
                                    
                                    hlsWriter?.writeVideoSample(data, bufferInfo)
                                    webSocketServer?.broadcastVideoFrame(data, bufferInfo)
                                    
                                    outputCount++
                                    if (outputCount % 30 == 0) {
                                        Log.d(TAG, "Encoded $outputCount samples, size: ${bufferInfo.size}")
                                    }
                                }
                                encoder.releaseOutputBuffer(index, false)
                            }
                            index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                val newFormat = encoder.outputFormat
                                hlsWriter?.setVideoFormat(newFormat)
                                webSocketServer?.setVideoFormat(newFormat)
                                Log.d(TAG, "Video format changed: $newFormat")
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
                    if (!isEncodingActive) break
                }
            }
            
            Log.d(TAG, "Encoding thread stopped")
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        isEncodingActive = false
        
        try {
            // Give encoding thread time to finish
            Thread.sleep(500)
            
            // Stop and release audio capture
            audioCapture?.release()
            audioCapture = null
            
            codec?.stop()
            codec?.release()
            codec = null
            
            hlsWriter?.close()
            hlsWriter = null
            
            streamServer?.stop()
            streamServer = null

            webSocketServer?.stop()
            webSocketServer = null
            
            executor.shutdown()
            
            Log.d(TAG, "Service destroyed, processed $frameCount frames")
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