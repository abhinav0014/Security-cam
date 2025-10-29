package com.onnet.securitycam

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaRecorder
import android.util.Log
import java.nio.ByteBuffer

class AudioCapture(
    private val sampleRate: Int,
    private val bitrate: Int,
    private val onFormatChanged: ((MediaFormat) -> Unit)? = null,
    private val onEncodedSample: (ByteArray, MediaCodec.BufferInfo) -> Unit
) {
    companion object {
        private const val TAG = "AudioCapture"
    }

    private var audioRecord: AudioRecord? = null
    private var codec: MediaCodec? = null
    @Volatile private var isCapturing = false
    private var captureThread: Thread? = null
    private var encodingThread: Thread? = null
    private var bufferSize: Int = 0
    private var outputFormat: MediaFormat? = null

    fun start() {
        try {
            // Calculate buffer size
            val minBufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            
            if (minBufferSize <= 0) {
                // Fallback to a conservative buffer size: 1 second of audio
                bufferSize = sampleRate * 2 // 2 bytes per sample for PCM16
                Log.w(TAG, "Invalid minBufferSize ($minBufferSize), using fallback size: $bufferSize")
            } else {
                bufferSize = minBufferSize * 2 // Double buffer for safety
            }

            // Create AudioRecord
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "Failed to initialize AudioRecord")
                return
            }

            // Create and configure AAC encoder
            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1)
                format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }

            isCapturing = true
            audioRecord?.startRecording()

            // Start capture thread
            captureThread = Thread(::captureLoop, "AudioCaptureThread").apply { start() }
            encodingThread = Thread(::encodingLoop, "AudioEncodingThread").apply { start() }

            Log.i(TAG, "Audio capture started: sampleRate=$sampleRate, bitrate=$bitrate")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio capture", e)
            release()
        }
    }

    fun stop() {
        isCapturing = false
        audioRecord?.stop()
        
        // Signal end of stream to encoder
        codec?.let { codec ->
            val inputBufferIndex = codec.dequeueInputBuffer(1000)
            if (inputBufferIndex >= 0) {
                codec.queueInputBuffer(
                    inputBufferIndex, 
                    0, 
                    0, 
                    0, 
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                )
                
                // Drain remaining output
                val bufferInfo = MediaCodec.BufferInfo()
                var timeoutUs = 10000L
                var sawEOS = false
                
                while (!sawEOS && timeoutUs > 0) {
                    val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 100)
                    if (outputBufferIndex >= 0) {
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            sawEOS = true
                        } else {
                            // Process any remaining output
                            val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                            outputBuffer?.let {
                                it.position(bufferInfo.offset)
                                it.limit(bufferInfo.offset + bufferInfo.size)
                                val data = ByteArray(bufferInfo.size)
                                it.get(data)
                                onEncodedSample(data, bufferInfo)
                            }
                        }
                        codec.releaseOutputBuffer(outputBufferIndex, false)
                    }
                    timeoutUs -= 100
                }
            }
        }
        
        captureThread?.let {
            try {
                it.join(1000)
            } catch (e: InterruptedException) {
                Log.w(TAG, "Interrupted while joining capture thread", e)
            }
        }
        
        encodingThread?.let {
            try {
                it.join(1000)
            } catch (e: InterruptedException) {
                Log.w(TAG, "Interrupted while joining encoding thread", e)
            }
        }

        Log.i(TAG, "Audio capture stopped")
    }

    fun release() {
        stop()
        
        codec?.apply {
            try {
                stop()
                release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing codec", e)
            }
        }
        codec = null

        audioRecord?.release()
        audioRecord = null

        Log.i(TAG, "Audio capture released")
    }

    fun getOutputFormat(): MediaFormat? {
        return outputFormat ?: codec?.outputFormat
    }

    private fun captureLoop() {
        val buffer = ByteArray(bufferSize)
        var frameCount = 0
        var totalSamples = 0L

        while (isCapturing) {
            try {
                val read = audioRecord?.read(buffer, 0, bufferSize) ?: -1
                if (read > 0) {
                    codec?.let { codec ->
                        val inputBufferIndex = codec.dequeueInputBuffer(10000)
                        if (inputBufferIndex >= 0) {
                            val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                            inputBuffer?.clear()
                            inputBuffer?.put(buffer, 0, read)
                            
                            // Calculate PTS based on sample count (PCM16 mono = 2 bytes per sample)
                            val samplesInBuffer = read / 2
                            totalSamples += samplesInBuffer
                            val presentationTimeUs = (totalSamples * 1_000_000L) / sampleRate
                            codec.queueInputBuffer(inputBufferIndex, 0, read, presentationTimeUs, 0)

                            frameCount++
                            if (frameCount % 100 == 0) {
                                Log.d(TAG, "Captured $frameCount audio frames")
                            }
                        }
                    }
                    else -> {
                        // No output available or other status codes (INFO_TRY_AGAIN_LATER etc.)
                        // Continue loop without action
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error in capture loop", e)
                break
            }
        }

        Log.d(TAG, "Capture loop exited")
    }

    private fun encodingLoop() {
        val bufferInfo = MediaCodec.BufferInfo()
        var frameCount = 0
        while (isCapturing) {
            try {
                codec?.let { codec ->
                    val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
                    when {
                        outputBufferIndex >= 0 -> {
                            val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                            outputBuffer?.let {
                                val originalPosition = it.position()
                                val originalLimit = it.limit()
                                it.position(bufferInfo.offset)
                                it.limit(bufferInfo.offset + bufferInfo.size)
                                val data = ByteArray(bufferInfo.size)
                                it.get(data)
                                it.position(originalPosition)
                                it.limit(originalLimit)
                                onEncodedSample(data, bufferInfo)
                                frameCount++
                                if (frameCount % 100 == 0) {
                                    Log.d(TAG, "Encoded $frameCount audio frames")
                                }
                            }
                            codec.releaseOutputBuffer(outputBufferIndex, false)
                        }
                        outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            outputFormat = codec.outputFormat
                            onFormatChanged?.invoke(codec.outputFormat)
                            Log.i(TAG, "Audio encoder output format changed: ${codec.outputFormat}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error in encoding loop", e)
                break
            }
        }
        Log.d(TAG, "Encoding loop exited")
    }
}