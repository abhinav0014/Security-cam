package com.onnet.securitycam.features

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import java.io.ByteArrayOutputStream

object JpegToNV21Converter {
    private const val TAG = "JpegToNV21Converter"

    /**
     * Decode a JPEG byte array to NV21 byte array using an intermediate Bitmap -> YuvImage path.
     * This is not the fastest path but is robust for small frame sizes used in streaming.
     */
    fun jpegToNV21(jpegBytes: ByteArray): ByteArray? {
        return try {
            val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
            val jpeg = out.toByteArray()

            // Convert compressed JPEG to NV21 via YuvImage
            val yuvImage = YuvImage(jpeg, ImageFormat.NV21, bitmap.width, bitmap.height, null)
            val buffer = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, bitmap.width, bitmap.height), 100, buffer)
            // The above returns JPEG again, but we will instead use the decoded bitmap to create NV21

            val yuv = ByteArray(bitmap.width * bitmap.height * 3 / 2)

            // Quick and simple RGB->NV21 conversion (not optimized)
            var yi = 0
            var uvIndex = bitmap.width * bitmap.height
            for (j in 0 until bitmap.height) {
                for (i in 0 until bitmap.width) {
                    val color = bitmap.getPixel(i, j)
                    val r = (color shr 16) and 0xff
                    val g = (color shr 8) and 0xff
                    val b = color and 0xff

                    val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128

                    yuv[yi++] = y.toByte()
                    if (j % 2 == 0 && i % 2 == 0) {
                        yuv[uvIndex++] = v.toByte()
                        yuv[uvIndex++] = u.toByte()
                    }
                }
            }

            yuv
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert jpeg to NV21", e)
            null
        }
    }
}
