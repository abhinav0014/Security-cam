package com.onnet.securitycam.features

import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import androidx.camera.core.ImageProxy
import com.onnet.securitycam.config.RecordingSettings
import com.onnet.securitycam.config.SettingsManager
import kotlinx.coroutines.*
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.CoroutineContext

class RecordingManager(
    private val context: Context,
    private val coroutineContext: CoroutineContext = Dispatchers.IO
) : CoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + Job()

    private val settingsManager = SettingsManager.getInstance(context)
    private var currentRecording: MediaMuxer? = null
    private var recordingJob: Job? = null
    private var isRecording = false

    fun startRecording(settings: RecordingSettings) = launch {
        if (isRecording) return@launch

        val outputFile = createOutputFile()
        currentRecording = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        
        // Configure video format based on quality settings
        val videoFormat = MediaFormat.createVideoFormat(
            MediaFormat.MIMETYPE_VIDEO_AVC,
            settings.quality.width,
            settings.quality.height
        ).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, settings.quality.bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, settings.quality.fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        // Start recording process
        isRecording = true
        currentRecording?.start()

        // Schedule cleanup based on settings
        launch {
            delay(settings.recordingInterval * 1000)
            stopRecording()
            cleanupStorageIfNeeded()
        }
    }

    fun stopRecording() {
        isRecording = false
        currentRecording?.apply {
            stop()
            release()
        }
        currentRecording = null
    }

    private fun createOutputFile(): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename = "VID_$timestamp.mp4"
        return File(settingsManager.getDefaultRecordingDirectory(), filename)
    }

    private fun cleanupStorageIfNeeded() {
        val storageUsage = settingsManager.getStorageUsage()
        val maxStorage = settingsManager.cameraSettings.recording.maxStorageSize

        if (storageUsage > maxStorage) {
            settingsManager.cleanupOldRecordings()
        }
    }

    fun processFrame(image: ImageProxy, motionDetected: Boolean) {
        if (!isRecording) return
        if (settingsManager.cameraSettings.recording.motionTriggeredOnly && !motionDetected) return

        // Process and encode frame
        // Note: Actual frame encoding implementation would go here
        // This is a placeholder for the actual video encoding logic
    }

    fun release() {
        stopRecording()
        recordingJob?.cancel()
    }
}