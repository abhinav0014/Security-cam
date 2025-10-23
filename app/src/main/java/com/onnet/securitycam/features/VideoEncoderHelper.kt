package com.onnet.securitycam.features

import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingQueue

/**
 * Complete video encoder for converting camera frames to H.264 MP4 video
 */
class VideoEncoderHelper(
    private val outputFile: File,
    private val width: Int,
    private val height: Int,
    private val bitrate: Int = 2_000_000,
    private val fps: Int = 30
) {
    companion object {
        private const val TAG = "VideoEncoderHelper"
        private const val MIME_TYPE = "video/avc"
        private const val TIMEOUT_US = 10000L
        private const val I_FRAME_INTERVAL = 1
    }

    private var encoder: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var trackIndex = -1
    private var muxerStarted = false
    private var frameIndex = 0L
    private val frameQueue = LinkedBlockingQueue<ByteArray>(30)
    private var isRunning = false
    
    @Volatile
    private var encoderThread: Thread? = null

    fun start() {
        try {
            Log.i(TAG, "Starting encoder: ${width}x${height} @ ${fps}fps, ${bitrate}bps")
            
            // Setup muxer
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            // Configure encoder format
            val format = MediaFormat.createVideoFormat(MIME_TYPE, width, height).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
            }

            // Create and start encoder
            encoder = MediaCodec.createEncoderByType(MIME_TYPE)
            encoder?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder?.start()

            isRunning = true
            startEncoderThread()

            Log.i(TAG, "Encoder started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting encoder", e)
            release()
            throw e
        }
    }

    private fun startEncoderThread() {
        encoderThread = Thread {
            try {
                Log.d(TAG, "Encoder thread started")
                while (isRunning) {
                    val frameData = frameQueue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS)
                    if (frameData != null) {
                        encodeFrame(frameData)
                    }
                }
                Log.d(TAG, "Encoder thread finished")
            } catch (e: InterruptedException) {
                Log.d(TAG, "Encoder thread interrupted")
            } catch (e: Exception) {
                Log.e(TAG, "Error in encoder thread", e)
            }
        }.apply {
            name = "VideoEncoderThread"
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun addFrame(imageProxy: ImageProxy) {
        if (!isRunning) {
            imageProxy.close()
            return
        }

        try {
            // Convert ImageProxy to byte array
            val frameData = imageProxyToNV21(imageProxy)
            
            // Add to queue (drop if full to prevent memory issues)
            if (!frameQueue.offer(frameData)) {
                Log.w(TAG, "Frame queue full, dropping frame")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adding frame", e)
        } finally {
            imageProxy.close()
        }
    }

    private fun encodeFrame(frameData: ByteArray) {
        val codec = encoder ?: return

        try {
            // Get input buffer
            val inputBufferIndex = codec.dequeueInputBuffer(TIMEOUT_US)
            if (inputBufferIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                inputBuffer?.clear()
                inputBuffer?.put(frameData)

                val presentationTimeUs = frameIndex * 1_000_000L / fps
                codec.queueInputBuffer(inputBufferIndex, 0, frameData.size, presentationTimeUs, 0)
                frameIndex++
            } else {
                Log.w(TAG, "No input buffer available")
            }

            // Drain output buffers
            drainEncoder(false)

        } catch (e: Exception) {
            Log.e(TAG, "Error encoding frame", e)
        }
    }

    private fun drainEncoder(endOfStream: Boolean) {
        val codec = encoder ?: return
        val currentMuxer = muxer ?: return
        val bufferInfo = MediaCodec.BufferInfo()

        while (true) {
            val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)

            when {
                outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) break
                }
                outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (muxerStarted) {
                        throw RuntimeException("Format changed twice")
                    }
                    val newFormat = codec.outputFormat
                    trackIndex = currentMuxer.addTrack(newFormat)
                    currentMuxer.start()
                    muxerStarted = true
                    Log.i(TAG, "Muxer started, track index: $trackIndex")
                }
                outputBufferIndex < 0 -> {
                    // Ignore other info codes
                }
                else -> {
                    val encodedData = codec.getOutputBuffer(outputBufferIndex)
                    
                    if (encodedData == null) {
                        Log.w(TAG, "Encoder output buffer was null")
                        codec.releaseOutputBuffer(outputBufferIndex, false)
                        continue
                    }
                    
                    // Skip codec config data
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufferInfo.size = 0
                    }
                    
                    if (bufferInfo.size != 0) {
                        if (!muxerStarted) {
                            throw RuntimeException("Muxer hasn't started")
                        }
                        
                        // Adjust buffer position and limit
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        
                        // Write encoded data to muxer
                        currentMuxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                    }
                    
                    codec.releaseOutputBuffer(outputBufferIndex, false)
                    
                    // Check for end of stream
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        if (!endOfStream) {
                            Log.w(TAG, "Reached end of stream unexpectedly")
                        }
                        break
                    }
                }
            }
        }
    }

    /**
     * Convert ImageProxy to NV21 byte array (YUV format)
     */
    private fun imageProxyToNV21(image: ImageProxy): ByteArray {
        val width = image.width
        val height = image.height
        val ySize = width * height
        val uvSize = width * height / 2
        val nv21 = ByteArray(ySize + uvSize)

        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        var rowStride = image.planes[0].rowStride
        var pos = 0

        // Copy Y plane
        if (rowStride == width) {
            yBuffer.get(nv21, 0, ySize)
            pos += ySize
        } else {
            var yBufferPos = 0
            for (row in 0 until height) {
                yBuffer.position(yBufferPos)
                yBuffer.get(nv21, pos, width)
                yBufferPos += rowStride
                pos += width
            }
        }

        // Copy UV planes
        rowStride = image.planes[2].rowStride
        val pixelStride = image.planes[2].pixelStride

        for (row in 0 until height / 2) {
            for (col in 0 until width / 2) {
                val vuPos = col * pixelStride + row * rowStride
                nv21[pos++] = vBuffer.get(vuPos)  // V
                nv21[pos++] = uBuffer.get(vuPos)  // U
            }
        }

        return nv21
    }

    /**
     * Alternative method using YuvImage conversion (slower but more reliable)
     */
    private fun imageProxyToJpegBytes(image: ImageProxy): ByteArray {
        val yuvBytes = imageProxyToNV21(image)
        val yuvImage = YuvImage(yuvBytes, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
        return out.toByteArray()
    }

    fun stop() {
        if (!isRunning) return
        
        Log.i(TAG, "Stopping encoder...")
        isRunning = false

        try {
            // Wait for queue to empty
            var waitCount = 0
            while (frameQueue.isNotEmpty() && waitCount < 50) {
                Thread.sleep(100)
                waitCount++
            }

            // Signal end of stream
            encoder?.let { codec ->
                try {
                    val inputBufferIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputBufferIndex >= 0) {
                        codec.queueInputBuffer(
                            inputBufferIndex,
                            0,
                            0,
                            frameIndex * 1_000_000L / fps,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                    }
                    
                    // Drain remaining data
                    drainEncoder(true)
                } catch (e: Exception) {
                    Log.e(TAG, "Error signaling end of stream", e)
                }
            }

            // Wait for encoder thread to finish
            encoderThread?.join(2000)

        } catch (e: Exception) {
            Log.e(TAG, "Error stopping encoder", e)
        }

        Log.i(TAG, "Encoder stopped. Total frames: $frameIndex")
    }

    fun release() {
        stop()

        try {
            encoder?.stop()
            encoder?.release()
            encoder = null

            if (muxerStarted) {
                muxer?.stop()
            }
            muxer?.release()
            muxer = null

            frameQueue.clear()
            trackIndex = -1
            muxerStarted = false
            frameIndex = 0

            Log.i(TAG, "Encoder released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing encoder", e)
        }
    }

    fun getFrameCount(): Long = frameIndex
    
    fun isRecording(): Boolean = isRunning && muxerStarted
}