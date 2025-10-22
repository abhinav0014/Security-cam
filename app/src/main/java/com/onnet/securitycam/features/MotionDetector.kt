package com.onnet.securitycam.features

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.media.Image
import androidx.camera.core.ImageProxy
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer

class MotionDetector(
    private val sensitivity: Float = 0.5f,
    private val minArea: Double = 500.0
) {
    private var previousFrame: Mat? = null
    private val kernel = Mat.ones(3, 3, CvType.CV_8U)

    fun detect(image: ImageProxy): Boolean {
        val currentFrame = image.toBitmap().toMat()
        val motionDetected = processFrame(currentFrame)
        previousFrame = currentFrame
        return motionDetected
    }

    private fun processFrame(currentFrame: Mat): Boolean {
        if (previousFrame == null) {
            return false
        }

        val gray = Mat()
        Imgproc.cvtColor(currentFrame, gray, Imgproc.COLOR_BGR2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(21.0, 21.0), 0.0)

        val prevGray = Mat()
        Imgproc.cvtColor(previousFrame!!, prevGray, Imgproc.COLOR_BGR2GRAY)
        Imgproc.GaussianBlur(prevGray, prevGray, Size(21.0, 21.0), 0.0)

        val frameDelta = Mat()
        Core.absdiff(prevGray, gray, frameDelta)

        val thresh = Mat()
        Imgproc.threshold(frameDelta, thresh, 25.0, 255.0, Imgproc.THRESH_BINARY)
        Imgproc.dilate(thresh, thresh, kernel, Point(-1.0, -1.0), 2)

        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(
            thresh, contours, hierarchy,
            Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE
        )

        val motionThreshold = sensitivity * minArea
        var motionDetected = false

        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            if (area > motionThreshold) {
                motionDetected = true
                break
            }
        }

        // Cleanup
        gray.release()
        prevGray.release()
        frameDelta.release()
        thresh.release()
        hierarchy.release()
        contours.forEach { it.release() }

        return motionDetected
    }

    private fun ImageProxy.toBitmap(): Bitmap {
        val buffer: ByteBuffer = planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun Bitmap.toMat(): Mat {
        val mat = Mat()
        Utils.bitmapToMat(this, mat)
        return mat
    }

    fun release() {
        previousFrame?.release()
        kernel.release()
    }
}