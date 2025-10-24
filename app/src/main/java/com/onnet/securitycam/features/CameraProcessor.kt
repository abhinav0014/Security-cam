package com.onnet.securitycam.features

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import com.onnet.securitycam.config.CameraSettings
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class CameraProcessor(
    private val context: Context,
    private val onFrame: (ByteArray) -> Unit
) {
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private val cameraManager: CameraManager by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }
    private val cameraThread = HandlerThread("CameraBackground").apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)
    private val cameraOpenCloseLock = Semaphore(1)

    fun start(settings: CameraSettings, previewSurface: Surface? = null) {
        this.previewSurface = previewSurface
        val cameraId = getCameraId(settings.useBackCamera)
        setupImageReader(settings)
        openCamera(cameraId)
    }

    private var previewSurface: Surface? = null

    private fun getCameraId(useBack: Boolean): String {
        return cameraManager.cameraIdList.first { id ->
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            if (useBack) facing == CameraCharacteristics.LENS_FACING_BACK
            else facing == CameraCharacteristics.LENS_FACING_FRONT
        }
    }

    private fun setupImageReader(settings: CameraSettings) {
        imageReader = ImageReader.newInstance(
            settings.streamQuality.width,
            settings.streamQuality.height,
            ImageFormat.JPEG,
            2
        ).apply {
            setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                try {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    onFrame(bytes)
                } finally {
                    image?.close()
                }
            }, cameraHandler)
        }
    }

    private fun openCamera(cameraId: String) {
        try {
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraOpenCloseLock.release()
                    cameraDevice = camera
                    createCaptureSession()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    cameraOpenCloseLock.release()
                    camera.close()
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    cameraOpenCloseLock.release()
                    camera.close()
                    cameraDevice = null
                }
            }, cameraHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        } catch (e: SecurityException) {
            e.printStackTrace()
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera opening.", e)
        }
    }

    private fun createCaptureSession() {
        try {
            val device = cameraDevice ?: return
            val targets = mutableListOf<Surface>()
            imageReader?.surface?.let { targets.add(it) }
            previewSurface?.let { targets.add(it) }

            device.createCaptureSession(targets, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    try {
                        val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                        imageReader?.surface?.let { builder.addTarget(it) }
                        previewSurface?.let { builder.addTarget(it) }
                        session.setRepeatingRequest(builder.build(), null, cameraHandler)
                    } catch (e: CameraAccessException) {
                        e.printStackTrace()
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    // Handle configuration failure
                }
            }, cameraHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    fun stop() {
        try {
            cameraOpenCloseLock.acquire()
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
            imageReader?.close()
            imageReader = null
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera closing.", e)
        } finally {
            cameraOpenCloseLock.release()
        }
    }

    fun release() {
        stop()
        cameraThread.quitSafely()
        try {
            cameraThread.join()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }
}