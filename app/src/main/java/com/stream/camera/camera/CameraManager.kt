package com.stream.camera.camera

import android.content.Context
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.stream.camera.MainActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Camera Manager - Handles camera operations
 * Provides frames to the HLS encoder
 * 
 * @param context Application context for utility functions
 * @param lifecycleOwner Activity lifecycle owner for CameraX binding
 */
class CameraManager(private val context: Context, private val lifecycleOwner: LifecycleOwner) {
    
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var currentCameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var isStreamingActive = false
    
    private var currentWidth = 1920
    private var currentHeight = 1080
    
    companion object {
        private const val TAG = "CameraManager"
    }
    
    suspend fun startCamera(onFrameAvailable: (ByteArray) -> Unit) {
        try {
            val provider = getCameraProvider()
            cameraProvider = provider
            
            imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(currentWidth, currentHeight))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImageProxy(imageProxy, onFrameAvailable)
                    }
                }
            
            provider.unbindAll()
            
            provider.bindToLifecycle(
                lifecycleOwner,
                currentCameraSelector,
                imageAnalysis
            )
            
            isStreamingActive = true
            Log.d(TAG, "Camera started successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start camera", e)
            throw e
        }
    }
    
    fun stopCamera() {
        try {
            cameraProvider?.unbindAll()
            imageAnalysis = null
            isStreamingActive = false
            Log.d(TAG, "Camera stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping camera", e)
        }
    }
    
    suspend fun switchCamera() {
        currentCameraSelector = if (currentCameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        
        cameraProvider?.let { provider ->
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                currentCameraSelector,
                imageAnalysis!!
            )
        }
        
        Log.d(TAG, "Camera switched to ${getCurrentCamera()}")
    }
    
    fun setResolution(width: Int, height: Int) {
        currentWidth = width
        currentHeight = height
        
        imageAnalysis?.clearAnalyzer()
        imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(width, height))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        
        Log.d(TAG, "Resolution changed to ${width}x${height}")
    }
    
    fun isStreaming(): Boolean = isStreamingActive
    
    fun getCurrentCamera(): String {
        return if (currentCameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            "back"
        } else {
            "front"
        }
    }
    
    fun getCurrentResolution(): String = "${currentWidth}x${currentHeight}"
    
    private suspend fun getCameraProvider(): ProcessCameraProvider {
        return suspendCancellableCoroutine { continuation ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                try {
                    continuation.resume(cameraProviderFuture.get())
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            }, ContextCompat.getMainExecutor(context))
        }
    }
    
    private fun processImageProxy(imageProxy: ImageProxy, onFrameAvailable: (ByteArray) -> Unit) {
        try {
            val buffer = imageProxy.planes[0].buffer
            val data = ByteArray(buffer.remaining())
            buffer.get(data)
            
            onFrameAvailable(data)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing frame", e)
        } finally {
            imageProxy.close()
        }
    }
    
    fun release() {
        stopCamera()
        cameraExecutor.shutdown()
    }
}
