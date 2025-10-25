package com.onnet.securitycam

import android.content.Context
import android.media.MediaCodec
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger

class HLSWriter(private val context: Context, private val outputDir: File) {

    private val TAG = "HLSWriter"
    private val segmentDuration = 4 // seconds (reduced for faster startup)
    private val maxSegments = 6
    private val segmentCounter = AtomicInteger(0)
    
    private var currentSegmentFile: File? = null
    private var currentSegmentStream: FileOutputStream? = null
    private var currentSegmentStartTime: Long = 0
    private var segmentList = mutableListOf<SegmentInfo>()
    private var isFirstFrame = true
    private var sampleCount = 0
    
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
        
        // Create initial empty playlist
        createInitialPlaylist()
        
        Log.d(TAG, "HLS Writer initialized. Output dir: ${outputDir.absolutePath}")
    }

    private fun createInitialPlaylist() {
        try {
            val playlistFile = File(outputDir, "stream.m3u8")
            val playlist = """
                #EXTM3U
                #EXT-X-VERSION:3
                #EXT-X-TARGETDURATION:${segmentDuration + 1}
                #EXT-X-MEDIA-SEQUENCE:0
            """.trimIndent()
            playlistFile.writeText(playlist)
            Log.d(TAG, "Created initial playlist")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating initial playlist: ${e.message}", e)
        }
    }

    @Synchronized
    fun writeSample(data: ByteArray, bufferInfo: MediaCodec.BufferInfo) {
        try {
            // Check if this is a keyframe (sync frame)
            val isKeyFrame = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
            
            // Convert presentation time to seconds
            val currentTime = bufferInfo.presentationTimeUs / 1_000_000
            
            // Start first segment immediately on first keyframe
            if (isFirstFrame && isKeyFrame) {
                startNewSegment(currentTime)
                isFirstFrame = false
                Log.d(TAG, "Started first segment on keyframe")
            }
            
            // Start new segment on keyframe if enough time has passed
            if (currentSegmentFile != null && isKeyFrame && 
                currentTime - currentSegmentStartTime >= segmentDuration) {
                finishCurrentSegment(currentTime)
                startNewSegment(currentTime)
            }
            
            // Write data to current segment
            currentSegmentStream?.let { stream ->
                stream.write(data)
                sampleCount++
                
                if (sampleCount % 100 == 0) {
                    Log.d(TAG, "Written $sampleCount samples to segment")
                }
            }
            
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
        try {
            currentSegmentStream?.flush()
            currentSegmentStream?.close()
            
            currentSegmentFile?.let { file ->
                if (file.exists() && file.length() > 0) {
                    val duration = maxOf((timestamp - currentSegmentStartTime).toFloat(), 1.0f)
                    val segmentInfo = SegmentInfo(
                        filename = file.name,
                        duration = duration,
                        sequence = segmentCounter.get()
                    )
                    
                    segmentList.add(segmentInfo)
                    
                    // Keep only the last N segments
                    while (segmentList.size > maxSegments) {
                        val removed = segmentList.removeAt(0)
                        val oldFile = File(outputDir, removed.filename)
                        if (oldFile.exists()) {
                            oldFile.delete()
                            Log.d(TAG, "Deleted old segment: ${removed.filename}")
                        }
                    }
                    
                    // Update playlist
                    updatePlaylist()
                    
                    Log.d(TAG, "Finished segment: ${file.name}, duration: $duration seconds, size: ${file.length()} bytes")
                } else {
                    Log.w(TAG, "Segment file is empty or doesn't exist: ${file.name}")
                    file.delete()
                }
            }
            
            currentSegmentFile = null
            currentSegmentStream = null
        } catch (e: Exception) {
            Log.e(TAG, "Error finishing segment: ${e.message}", e)
        }
    }

    private fun updatePlaylist() {
        try {
            val playlistFile = File(outputDir, "stream.m3u8")
            val targetDuration = segmentDuration + 1
            
            val playlist = StringBuilder()
            playlist.append("#EXTM3U\n")
            playlist.append("#EXT-X-VERSION:3\n")
            playlist.append("#EXT-X-TARGETDURATION:$targetDuration\n")
            playlist.append("#EXT-X-MEDIA-SEQUENCE:${segmentList.firstOrNull()?.sequence ?: 0}\n")
            
            // Add segments
            for (segment in segmentList) {
                val segmentFile = File(outputDir, segment.filename)
                if (segmentFile.exists()) {
                    playlist.append("#EXTINF:${String.format("%.3f", segment.duration)},\n")
                    playlist.append("${segment.filename}\n")
                }
            }
            
            // Write playlist
            playlistFile.writeText(playlist.toString())
            
            Log.d(TAG, "Updated playlist with ${segmentList.size} segments")
            
            // Log playlist contents for debugging
            if (segmentList.size <= 3) {
                Log.d(TAG, "Playlist content:\n${playlist}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating playlist: ${e.message}", e)
        }
    }

    @Synchronized
    fun close() {
        try {
            // Finish current segment if it exists
            if (currentSegmentFile != null) {
                finishCurrentSegment(System.currentTimeMillis() / 1000)
            }
            
            // Clean up
            currentSegmentStream?.close()
            currentSegmentStream = null
            currentSegmentFile = null
            
            Log.d(TAG, "HLS Writer closed. Total samples written: $sampleCount")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing HLS writer: ${e.message}", e)
        }
    }
}