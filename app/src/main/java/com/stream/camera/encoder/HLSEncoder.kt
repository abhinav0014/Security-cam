package com.stream.camera.encoder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger

/**
 * HLS Encoder - Encodes video frames to HLS format
 * Creates .m3u8 playlist and .ts segments with proper metadata
 */
class HLSEncoder(private val outputDir: File) {
    
    private var mediaCodec: MediaCodec? = null
    private var segmentIndex = AtomicInteger(0)
    private var currentSegmentFile: File? = null
    private var currentSegmentStream: FileOutputStream? = null
    
    private var width = 1920
    private var height = 1080
    private var bitrate = 2000000 // 2 Mbps
    private var frameRate = 30
    
    private val segmentDuration = 4 // seconds
    private val maxSegments = 10
    private val segments = mutableListOf<SegmentInfo>()
    
    companion object {
        private const val TAG = "HLSEncoder"
        private const val MIME_TYPE = "video/avc"
        private const val I_FRAME_INTERVAL = 2
    }
    
    init {
        initializeCodec()
    }
    
    private fun initializeCodec() {
        try {
            val format = MediaFormat.createVideoFormat(MIME_TYPE, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
                setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh)
                setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel42)
            }
            
            mediaCodec = MediaCodec.createEncoderByType(MIME_TYPE).apply {
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }
            
            Log.d(TAG, "MediaCodec initialized: ${width}x${height} @ ${bitrate}bps")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize codec", e)
            throw e
        }
    }
    
    fun encodeFrame(data: ByteArray) {
        try {
            val codec = mediaCodec ?: return
            
            val inputBufferIndex = codec.dequeueInputBuffer(10000)
            if (inputBufferIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                inputBuffer?.clear()
                inputBuffer?.put(data)
                
                codec.queueInputBuffer(
                    inputBufferIndex,
                    0,
                    data.size,
                    System.nanoTime() / 1000,
                    0
                )
            }
            
            val bufferInfo = MediaCodec.BufferInfo()
            var outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
            
            while (outputBufferIndex >= 0) {
                val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                
                if (outputBuffer != null && bufferInfo.size > 0) {
                    writeToSegment(outputBuffer, bufferInfo)
                }
                
                codec.releaseOutputBuffer(outputBufferIndex, false)
                outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error encoding frame", e)
        }
    }
    
    private fun writeToSegment(buffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        try {
            if (currentSegmentFile == null || shouldCreateNewSegment()) {
                createNewSegment()
            }
            
            val data = ByteArray(bufferInfo.size)
            buffer.position(bufferInfo.offset)
            buffer.get(data, 0, bufferInfo.size)
            
            currentSegmentStream?.write(data)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error writing to segment", e)
        }
    }
    
    private fun shouldCreateNewSegment(): Boolean {
        // Create new segment every 4 seconds worth of frames
        val currentSegment = segmentIndex.get()
        val estimatedDuration = (currentSegment * segmentDuration)
        return estimatedDuration % segmentDuration == 0
    }
    
    private fun createNewSegment() {
        try {
            currentSegmentStream?.close()
            
            val index = segmentIndex.getAndIncrement()
            val filename = "segment_$index.ts"
            currentSegmentFile = File(outputDir, filename)
            currentSegmentStream = FileOutputStream(currentSegmentFile)
            
            val segmentInfo = SegmentInfo(
                filename = filename,
                duration = segmentDuration.toDouble(),
                index = index
            )
            segments.add(segmentInfo)
            
            // Keep only recent segments
            while (segments.size > maxSegments) {
                val oldSegment = segments.removeAt(0)
                File(outputDir, oldSegment.filename).delete()
            }
            
            updatePlaylist()
            
            Log.d(TAG, "Created new segment: $filename")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error creating segment", e)
        }
    }
    
    private fun updatePlaylist() {
        try {
            val playlistFile = File(outputDir, "playlist.m3u8")
            val targetDuration = segmentDuration + 1
            
            val playlist = buildString {
                appendLine("#EXTM3U")
                appendLine("#EXT-X-VERSION:3")
                appendLine("#EXT-X-TARGETDURATION:$targetDuration")
                appendLine("#EXT-X-MEDIA-SEQUENCE:${segments.firstOrNull()?.index ?: 0}")
                appendLine("#EXT-X-PLAYLIST-TYPE:EVENT")
                appendLine()
                
                segments.forEach { segment ->
                    appendLine("#EXTINF:${segment.duration},")
                    appendLine("/segments/${segment.filename}")
                }
            }
            
            playlistFile.writeText(playlist)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating playlist", e)
        }
    }
    
    fun setBitrate(newBitrate: Int) {
        bitrate = newBitrate * 1000 // Convert to bps
        reinitializeCodec()
    }
    
    fun setResolution(newWidth: Int, newHeight: Int) {
        width = newWidth
        height = newHeight
        reinitializeCodec()
    }
    
    private fun reinitializeCodec() {
        try {
            mediaCodec?.stop()
            mediaCodec?.release()
            initializeCodec()
        } catch (e: Exception) {
            Log.e(TAG, "Error reinitializing codec", e)
        }
    }
    
    fun getCurrentBitrate(): Int = bitrate / 1000 // Return in kbps
    
    fun getCurrentFps(): Int = frameRate
    
    fun stop() {
        try {
            currentSegmentStream?.close()
            mediaCodec?.stop()
            mediaCodec?.release()
            mediaCodec = null
            
            Log.d(TAG, "Encoder stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping encoder", e)
        }
    }
}

data class SegmentInfo(
    val filename: String,
    val duration: Double,
    val index: Int
)
