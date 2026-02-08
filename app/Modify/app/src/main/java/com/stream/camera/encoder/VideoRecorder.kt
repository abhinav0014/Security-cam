package com.stream.camera.encoder

import android.content.Context
import android.media.MediaRecorder
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.util.concurrent.Executor

/**
 * Video Recorder using CameraX VideoCapture
 * Creates HLS segments properly
 */
class VideoRecorder(
    private val context: Context,
    private val outputDir: File,
    private val lifecycleOwner: LifecycleOwner
) {
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var currentSegmentIndex = 0
    private val segments = mutableListOf<SegmentInfo>()
    private val maxSegments = 10
    
    private var cameraProvider: ProcessCameraProvider? = null
    private var currentCameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    
    companion object {
        private const val TAG = "VideoRecorder"
    }
    
    suspend fun startRecording(
        onSegmentCreated: (File) -> Unit,
        onError: (Exception) -> Unit
    ) {
        try {
            val provider = ProcessCameraProvider.getInstance(context).get()
            cameraProvider = provider
            
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD))
                .build()
            
            videoCapture = VideoCapture.withOutput(recorder)
            
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                currentCameraSelector,
                videoCapture
            )
            
            startNewSegment(onSegmentCreated, onError)
            
            Log.d(TAG, "Video recording started")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            onError(e)
        }
    }
    
    private fun startNewSegment(
        onSegmentCreated: (File) -> Unit,
        onError: (Exception) -> Unit
    ) {
        try {
            recording?.stop()
            
            val filename = "segment_${currentSegmentIndex}.mp4"
            val segmentFile = File(outputDir, filename)
            
            val outputOptions = FileOutputOptions.Builder(segmentFile).build()
            
            recording = videoCapture?.output
                ?.prepareRecording(context, outputOptions)
                ?.start(ContextCompat.getMainExecutor(context)) { event ->
                    when (event) {
                        is VideoRecordEvent.Finalize -> {
                            if (event.hasError()) {
                                Log.e(TAG, "Recording error: ${event.error}")
                            } else {
                                onSegmentCreated(segmentFile)
                            }
                        }
                    }
                }
            
            val segmentInfo = SegmentInfo(
                filename = filename,
                duration = 4.0,
                index = currentSegmentIndex
            )
            segments.add(segmentInfo)
            
            while (segments.size > maxSegments) {
                val oldSegment = segments.removeAt(0)
                File(outputDir, oldSegment.filename).delete()
            }
            
            currentSegmentIndex++
            
            // Schedule next segment after 4 seconds
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                startNewSegment(onSegmentCreated, onError)
            }, 4000)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error creating segment", e)
            onError(e)
        }
    }
    
    fun stop() {
        try {
            recording?.stop()
            recording = null
            cameraProvider?.unbindAll()
            Log.d(TAG, "Recording stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
        }
    }
    
    fun getSegments(): List<SegmentInfo> = segments.toList()
}