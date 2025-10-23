package com.onnet.securitycam.features

import android.content.Context
import android.util.Log
import androidx.camera.core.ImageProxy
import com.onnet.securitycam.config.RecordingSettings
import com.onnet.securitycam.config.SettingsManager
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manages video recording sessions using VideoEncoderHelper
 */
class RecordingManager(
    private val context: Context
) {
    companion object {
        private const val TAG = "RecordingManager"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val settingsManager = SettingsManager.getInstance(context)
    
    private var videoEncoder: VideoEncoderHelper? = null

    // Minimal VideoEncoderHelper stub to satisfy compilation; replace with real encoder implementation.
    private class VideoEncoderHelper(
        private val outputFile: File,
        private val width: Int,
        private val height: Int,
        private val bitrate: Int,
        private val fps: Int
    ) {
        private var frameCount = 0L

        fun start() {
            // Initialize encoder resources here
        }

        fun addFrame(imageProxy: ImageProxy) {
            try {
                // Encode/process the frame here. This stub just counts frames.
                frameCount++
            } finally {
                // Ensure the ImageProxy is always closed to avoid leaks
                imageProxy.close()
            }
        }

        fun release() {
            // Release encoder resources here
        }

        fun getFrameCount(): Long = frameCount
    }

    private var recordingJob: Job? = null
    private var outputFile: File? = null
    private var startTime: Long = 0
    
    @Volatile
    private var isRecording = false

    /**
     * Start a new recording session
     */
    fun startRecording(settings: RecordingSettings) {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return
        }

        recordingJob = scope.launch {
            try {
                // Create output file
                outputFile = createOutputFile()
                startTime = System.currentTimeMillis()
                
                // Initialize encoder
                videoEncoder = VideoEncoderHelper(
                    outputFile = outputFile!!,
                    width = settings.quality.width,
                    height = settings.quality.height,
                    bitrate = settings.quality.bitrate,
                    fps = settings.quality.fps
                )
                
                videoEncoder?.start()
                isRecording = true
                
                Log.i(TAG, "Recording started: ${outputFile?.name}")
                Log.i(TAG, "Quality: ${settings.quality.width}x${settings.quality.height} @ ${settings.quality.fps}fps")
                
                // Auto-stop after recording interval
                delay(settings.recordingInterval * 1000)
                stopRecording()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error starting recording", e)
                stopRecording()
            }
        }
    }

    /**
     * Process a camera frame for recording
     */
    fun processFrame(imageProxy: ImageProxy, motionDetected: Boolean = false) {
        if (!isRecording) {
            imageProxy.close()
            return
        }
        
        val settings = settingsManager.cameraSettings.recording
        
        // Skip frame if motion-triggered recording is enabled and no motion detected
        if (settings.motionTriggeredOnly && !motionDetected) {
            imageProxy.close()
            return
        }

        // Add frame to encoder
        videoEncoder?.addFrame(imageProxy)
    }

    /**
     * Stop the current recording session
     */
    fun stopRecording() {
        if (!isRecording) return
        
        isRecording = false
        
        scope.launch {
            try {
                val duration = (System.currentTimeMillis() - startTime) / 1000
                val frameCount = videoEncoder?.getFrameCount() ?: 0
                
                Log.i(TAG, "Stopping recording...")
                Log.i(TAG, "Duration: ${duration}s, Frames: $frameCount")
                
                videoEncoder?.release()
                videoEncoder = null
                
                outputFile?.let { file ->
                    if (file.exists()) {
                        val sizeKB = file.length() / 1024
                        Log.i(TAG, "Recording saved: ${file.name} (${sizeKB}KB)")
                    }
                }
                
                // Cleanup old recordings if needed
                cleanupStorageIfNeeded()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping recording", e)
            }
        }
    }

    /**
     * Create output file with timestamp
     */
    private fun createOutputFile(): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename = "VID_$timestamp.mp4"
        val directory = settingsManager.getDefaultRecordingDirectory()
        directory.mkdirs()
        return File(directory, filename)
    }

    /**
     * Cleanup old recordings based on retention policy
     */
    private fun cleanupStorageIfNeeded() {
        try {
            val storageUsage = settingsManager.getStorageUsage()
            val maxStorage = settingsManager.cameraSettings.recording.maxStorageSize

            if (storageUsage > maxStorage) {
                Log.i(TAG, "Storage limit exceeded ($storageUsage > $maxStorage), cleaning up...")
                settingsManager.cleanupOldRecordings()
                
                val newUsage = settingsManager.getStorageUsage()
                Log.i(TAG, "Storage after cleanup: $newUsage bytes")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up storage", e)
        }
    }

    /**
     * Get current recording status
     */
    fun isRecording(): Boolean = isRecording

    /**
     * Get current recording duration in seconds
     */
    fun getRecordingDuration(): Long {
        if (!isRecording) return 0
        return (System.currentTimeMillis() - startTime) / 1000
    }

    /**
     * Get current frame count
     */
    fun getFrameCount(): Long {
        return videoEncoder?.getFrameCount() ?: 0
    }

    /**
     * Get current output file
     */
    fun getCurrentFile(): File? = outputFile

    /**
     * Release all resources
     */
    fun release() {
        Log.i(TAG, "Releasing RecordingManager")
        stopRecording()
        recordingJob?.cancel()
        scope.cancel()
    }
}