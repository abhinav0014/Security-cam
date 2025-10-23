package com.onnet.securitycam.features

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlin.math.abs

/**
 * Basic motion detector using frame differencing on decoded JPEG frames.
 * This is not as robust as OpenCV but works without native dependencies.
 */
class MotionDetector(private val sensitivity: Int = 20) {
    companion object { private const val TAG = "MotionDetector" }

    private var lastFrame: Bitmap? = null

    fun detect(jpegBytes: ByteArray): Boolean {
        try {
            val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
            val last = lastFrame
            if (last == null) {
                lastFrame = bitmap.copy(bitmap.config, true)
                return false
            }

            // Resize both to a small thumbnail to speed up
            val w = 160
            val h = (bitmap.height * (160f / bitmap.width)).toInt().coerceAtLeast(10)
            val current = Bitmap.createScaledBitmap(bitmap, w, h, true)
            val prev = Bitmap.createScaledBitmap(last, w, h, true)

            var diffSum = 0L
            val pixelsCur = IntArray(w * h)
            val pixelsPrev = IntArray(w * h)
            current.getPixels(pixelsCur, 0, w, 0, 0, w, h)
            prev.getPixels(pixelsPrev, 0, w, 0, 0, w, h)

            for (i in pixelsCur.indices) {
                val c = pixelsCur[i]
                val p = pixelsPrev[i]
                val r = abs(((c shr 16) and 0xff) - ((p shr 16) and 0xff))
                val g = abs(((c shr 8) and 0xff) - ((p shr 8) and 0xff))
                val b = abs((c and 0xff) - (p and 0xff))
                diffSum += (r + g + b) / 3
            }

            val avg = diffSum / pixelsCur.size
            lastFrame = bitmap.copy(bitmap.config, true)

            Log.d(TAG, "Motion avg diff=$avg")
            return avg > sensitivity
        } catch (e: Exception) {
            Log.e(TAG, "Motion detection error", e)
            return false
        }
    }

    fun release() {
        lastFrame?.recycle()
        lastFrame = null
    }
}