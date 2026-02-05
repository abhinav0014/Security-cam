package com.abster.camerastream.mjpeg

import android.graphics.Bitmap

/**
 * Simple frame holder with metadata
 */
data class Frame(
    val bitmap: Bitmap,
    val timestamp: Long,
    val seq: Long
) {
    val width: Int get() = bitmap.width
    val height: Int get() = bitmap.height
}
