package com.onnet.securitycam

import android.content.Context
import android.media.MediaCodec
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger

class HLSWriter(private val context: Context, private val outputDir: File) {

    private val TAG = "HLSWriter"
    private val segmentDuration = 6 // seconds
    private val maxSegments = 5
    private val segmentCounter = AtomicInteger(0)
    
    private var currentSegmentFile: File? = null
    private var currentSegmentStream: FileOutputStream? = null
    private var currentSegmentStartTime: Long = 0
    private var segmentList = mutableListOf<SegmentInfo>()
    
    private data class SegmentInfo(
        val filename: String,
        val duration: Float,
        val sequence: Int
    )

    init {
        // Ensure output directory exists
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
        Log.d(TAG, "HLS Writer initialized. Output dir: ${outputDir.absolutePath}")
    }

    @Synchronized
    fun writeSample(data: ByteArray, bufferInfo: MediaCodec.BufferInfo) {
        try {
            // Check if this is a keyframe (sync frame)
            val isKeyFrame = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
            
            // Start new segment on keyframe if enough time has passed
            val currentTime = bufferInfo.presentationTimeUs / 1_000_000 // Convert to seconds
            
            if (currentSegmentFile == null || 
                (isKeyFrame && currentTime - currentSegmentStartTime >= segmentDuration)) {
                finishCurrentSegment(currentTime)
                startNewSegment(currentTime)
            }
            
            // Write data to current segment
            currentSegmentStream?.write(data)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error writing sample: ${e.message}", e)
        }
    }

    private fun startNewSegment(timestamp: Long) {
        val segmentNumber = segmentCounter.incrementAndGet()
        val filename = "segment_$segmentNumber.ts"
        currentSegmentFile = File(outputDir, filename)
        currentSegmentStream = FileOutputStream(currentSegmentFile)
        currentSegmentStartTime = timestamp
        
        Log.d(TAG, "Started new segment: $filename at $timestamp seconds")
    }

    private fun finishCurrentSegment(timestamp: Long) {
        currentSegmentStream?.flush()
        currentSegmentStream?.close()
        
        currentSegmentFile?.let { file ->
            if (file.exists()) {
                val duration = (timestamp - currentSegmentStartTime).toFloat()
                val segmentInfo = SegmentInfo(
                    filename = file.name,
                    duration = duration,
                    sequence = segmentCounter.get()
                )
                
                segmentList.add(segmentInfo)
                
                // Keep only the last N segments
                while (segmentList.size > maxSegments) {
                    val removed = segmentList.removeAt(0)
                    File(outputDir, removed.filename).delete()
                }
                
                // Update playlist
                updatePlaylist()
                
                Log.d(TAG, "Finished segment: ${file.name}, duration: $duration seconds")
            }
        }
        
        currentSegmentFile = null
        currentSegmentStream = null
    }

    private fun updatePlaylist() {
        try {
            val playlistFile = File(outputDir, "stream.m3u8")
            val targetDuration = (segmentDuration + 1) // Round up
            
            val playlist = StringBuilder()
            playlist.append("#EXTM3U\n")
            playlist.append("#EXT-X-VERSION:3\n")
            playlist.append("#EXT-X-TARGETDURATION:$targetDuration\n")
            playlist.append("#EXT-X-MEDIA-SEQUENCE:${segmentList.firstOrNull()?.sequence ?: 0}\n")
            
            // Add segments
            for (segment in segmentList) {
                playlist.append("#EXTINF:${String.format("%.3f", segment.duration)},\n")
                playlist.append("${segment.filename}\n")
            }
            
            // Write playlist
            playlistFile.writeText(playlist.toString())
            
            Log.d(TAG, "Updated playlist with ${segmentList.size} segments")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating playlist: ${e.message}", e)
        }
    }

    @Synchronized
    fun close() {
        try {
            // Finish current segment
            finishCurrentSegment(System.currentTimeMillis() / 1000)
            
            // Clean up
            currentSegmentStream?.close()
            currentSegmentStream = null
            currentSegmentFile = null
            
            Log.d(TAG, "HLS Writer closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing HLS writer: ${e.message}", e)
        }
    }
}