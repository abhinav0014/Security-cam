package com.onnet.securitycam.features

import android.graphics.Bitmap
import android.graphics.Color

object NightVisionFilter {
    /**
     * Apply a simple brightness boost and green tint to simulate night-vision.
     */
    fun apply(source: Bitmap, gain: Float = 1.5f): Bitmap {
        val out = source.copy(source.config, true)
        val width = out.width
        val height = out.height
        val pixels = IntArray(width * height)
        out.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (Color.red(c) * gain).toInt().coerceAtMost(255)
            val g = (Color.green(c) * gain * 1.2f).toInt().coerceAtMost(255)
            val b = (Color.blue(c) * gain * 0.8f).toInt().coerceAtMost(255)
            pixels[i] = Color.argb(Color.alpha(c), r, g, b)
        }

        out.setPixels(pixels, 0, width, 0, 0, width, height)
        return out
    }
}
