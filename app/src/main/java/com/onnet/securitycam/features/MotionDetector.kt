package com.onnet.securitycam.features

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy

/**
 * Motion Detection temporarily disabled - requires OpenCV library setup
 * To enable: Add OpenCV dependency and uncomment implementation
 */
class MotionDetector(
    private val sensitivity: Float = 0.5f,
    private val minArea: Double = 500.0
) {
    fun detect(image: ImageProxy): Boolean {
        // Motion detection disabled - would require OpenCV
        return false
    }

    fun release() {
        // Cleanup if needed
    }
}