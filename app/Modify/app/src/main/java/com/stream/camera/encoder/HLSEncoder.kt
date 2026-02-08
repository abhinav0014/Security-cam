package com.stream.camera.encoder

import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream

/**
 * HLS Encoder - Creates HLS segments for streaming
 * Simplified version that creates segments from camera frames
 */
class HLSEncoder(private val outputDir: File) {
    
    private var currentSegmentIndex = 0
    private val segments = mutableListOf<SegmentInfo>()
    private val maxSegments = 10
    
    private var bitrate = 2000 // kbps
    private var frameRate = 30
    
    private var isRunning = false
    private var segmentJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var currentSegmentFile: File? = null
    private var currentSegmentStream: FileOutputStream? = null
    private var frameBuffer = mutableListOf<ByteArray>()
    private val framesPerSegment = 120 // 4 seconds at 30fps
    
    companion object {
        private const val TAG = "HLSEncoder"
    }
    
    init {
        createInitialPlaylist()
        startSegmentGeneration()
    }
    
    private fun createInitialPlaylist() {
        updatePlaylist()
    }
    
    private fun startSegmentGeneration() {
        isRunning = true
        segmentJob = scope.launch {
            while (isRunning) {
                delay(4000) // Create new segment every 4 seconds
                createNewSegment()
            }
        }
    }
    
    @Synchronized
    fun encodeFrame(data: ByteArray) {
        if (!isRunning) return
        
        try {
            // Add frame to buffer
            frameBuffer.add(data)
            
            // Write to current segment
            currentSegmentStream?.write(data)
            
            // If buffer is full, we could process it here
            if (frameBuffer.size >= framesPerSegment) {
                frameBuffer.clear()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error encoding frame: ${e.message}")
        }
    }
    
    @Synchronized
    private fun createNewSegment() {
        try {
            // Close previous segment
            currentSegmentStream?.flush()
            currentSegmentStream?.close()
            
            // Create new segment file
            val filename = "segment_${currentSegmentIndex}.ts"
            val segmentFile = File(outputDir, filename)
            
            currentSegmentFile = segmentFile
            currentSegmentStream = FileOutputStream(segmentFile)
            
            // Write TS header (simplified - just for testing)
            // In production, you'd use proper MPEG-TS muxing
            writeTSHeader(currentSegmentStream!!)
            
            // Add to segments list
            val segmentInfo = SegmentInfo(
                filename = filename,
                duration = 4.0,
                index = currentSegmentIndex
            )
            
            segments.add(segmentInfo)
            
            // Remove old segments
            while (segments.size > maxSegments) {
                val oldSegment = segments.removeAt(0)
                File(outputDir, oldSegment.filename).delete()
            }
            
            currentSegmentIndex++
            
            // Update playlist
            updatePlaylist()
            
            Log.d(TAG, "Created segment: $filename (total: ${segments.size})")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error creating segment: ${e.message}")
        }
    }
    
    private fun writeTSHeader(stream: FileOutputStream) {
        // Write basic TS sync byte pattern
        // This is simplified - proper TS muxing would require full MPEG-TS structure
        val syncByte = 0x47.toByte()
        val header = ByteArray(188)
        header[0] = syncByte
        stream.write(header)
    }
    
    @Synchronized
    private fun updatePlaylist() {
        try {
            val playlistFile = File(outputDir, "playlist.m3u8")
            
            val playlist = buildString {
                appendLine("#EXTM3U")
                appendLine("#EXT-X-VERSION:3")
                appendLine("#EXT-X-TARGETDURATION:5")
                appendLine("#EXT-X-MEDIA-SEQUENCE:${segments.firstOrNull()?.index ?: 0}")
                appendLine()
                
                if (segments.isEmpty()) {
                    // Add a placeholder segment if none exist
                    appendLine("#EXTINF:4.0,")
                    appendLine("/segments/segment_0.ts")
                } else {
                    segments.forEach { segment ->
                        appendLine("#EXTINF:${segment.duration},")
                        appendLine("/segments/${segment.filename}")
                    }
                }
            }
            
            playlistFile.writeText(playlist)
            Log.d(TAG, "Updated playlist with ${segments.size} segments")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating playlist: ${e.message}")
        }
    }
    
    fun setBitrate(newBitrate: Int) {
        bitrate = newBitrate
        Log.d(TAG, "Bitrate set to: $bitrate kbps")
    }
    
    fun getCurrentBitrate(): Int = bitrate
    
    fun getCurrentFps(): Int = frameRate
    
    @Synchronized
    fun stop() {
        try {
            isRunning = false
            segmentJob?.cancel()
            currentSegmentStream?.flush()
            currentSegmentStream?.close()
            currentSegmentStream = null
            frameBuffer.clear()
            Log.d(TAG, "Encoder stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping encoder: ${e.message}")
        }
    }
}

data class SegmentInfo(
    val filename: String,
    val duration: Double,
    val index: Int
)
