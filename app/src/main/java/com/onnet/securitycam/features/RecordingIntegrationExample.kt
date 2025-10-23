package com.onnet.securitycam.examples

import android.content.Context
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleOwner
import com.onnet.securitycam.config.SettingsManager
import com.onnet.securitycam.features.RecordingManager
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Example showing how to integrate RecordingManager with CameraX
 */
class RecordingIntegrationExample(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    companion object {
        private const val TAG = "RecordingIntegration"
    }

    private val settingsManager = SettingsManager.getInstance(context)
    private val recordingManager = RecordingManager(context)
    private lateinit var cameraExecutor: ExecutorService
    private var imageAnalysis: ImageAnalysis? = null

    fun initialize() {
        cameraExecutor = Executors.newSingleThreadExecutor()
        setupCamera()
    }

    private fun setupCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            
            // Build preview
            val preview = Preview.Builder()
                .build()
            
            // Build image analysis for recording
            val settings = settingsManager.cameraSettings
            imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(
                    android.util.Size(
                        settings.streamQuality.width,
                        settings.streamQuality.height
                    )
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        // Process frame for recording
                        if (recordingManager.isRecording()) {
                            recordingManager.processFrame(imageProxy)
                        } else {
                            imageProxy.close()
                        }
                    }
                }
            
            // Select camera
            val cameraSelector = if (settings.useBackCamera) {
                CameraSelector.DEFAULT_BACK_CAMERA
            } else {
                CameraSelector.DEFAULT_FRONT_CAMERA
            }
            
            try {
                // Unbind all use cases before rebinding
                cameraProvider.unbindAll()
                
                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
                
                Log.i(TAG, "Camera initialized successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "Camera initialization failed", e)
            }
            
        }, androidx.core.content.ContextCompat.getMainExecutor(context))
    }

    /**
     * Start recording
     */
    fun startRecording() {
        val settings = settingsManager.cameraSettings.recording
        recordingManager.startRecording(settings)
        Log.i(TAG, "Recording started")
    }

    /**
     * Stop recording
     */
    fun stopRecording() {
        recordingManager.stopRecording()
        Log.i(TAG, "Recording stopped")
    }

    /**
     * Check if currently recording
     */
    fun isRecording(): Boolean {
        return recordingManager.isRecording()
    }

    /**
     * Get recording stats
     */
    fun getRecordingStats(): String {
        return if (recordingManager.isRecording()) {
            val duration = recordingManager.getRecordingDuration()
            val frames = recordingManager.getFrameCount()
            val fps = if (duration > 0) frames.toFloat() / duration else 0f
            "Recording: ${duration}s, Frames: $frames, FPS: ${"%.1f".format(fps)}"
        } else {
            "Not recording"
        }
    }

    /**
     * Release resources
     */
    fun release() {
        recordingManager.release()
        cameraExecutor.shutdown()
        Log.i(TAG, "Resources released")
    }
}

/**
 * Usage example in an Activity:
 *
 * class MainActivity : AppCompatActivity() {
 *     private lateinit var recordingExample: RecordingIntegrationExample
 *     
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *         
 *         recordingExample = RecordingIntegrationExample(this, this)
 *         recordingExample.initialize()
 *         
 *         btnStartRecording.setOnClickListener {
 *             if (recordingExample.isRecording()) {
 *                 recordingExample.stopRecording()
 *                 btnStartRecording.text = "Start Recording"
 *             } else {
 *                 recordingExample.startRecording()
 *                 btnStartRecording.text = "Stop Recording"
 *             }
 *         }
 *         
 *         // Update recording stats every second
 *         lifecycleScope.launch {
 *             while (true) {
 *                 tvRecordingStats.text = recordingExample.getRecordingStats()
 *                 delay(1000)
 *             }
 *         }
 *     }
 *     
 *     override fun onDestroy() {
 *         super.onDestroy()
 *         recordingExample.release()
 *     }
 * }
 */