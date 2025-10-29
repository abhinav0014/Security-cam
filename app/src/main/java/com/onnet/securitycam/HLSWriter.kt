package com.onnet.securitycam

import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger

class HLSWriter(
    private val context: Context,
    private val outputDir: File,
    private val audioRequired: Boolean = false // new flag
) {

    private val TAG = "HLSWriter"
    private val segmentDuration = 4 // seconds (reduced for faster startup)
    private val maxSegments = 6
    private val segmentCounter = AtomicInteger(0)

    private var currentSegmentFile: File? = null
    private var currentMuxer: MediaMuxer? = null
    private var segmentStartPtsUs: Long = -1
    private var lastSamplePtsUs: Long = -1
    private var segmentCreatedRealtimeMs: Long = 0L
    private val audioStartGraceMs: Long = 400 // configurable grace period
    private var baseTimestampUs: Long = -1
    private var sampleCount = 0

    private var videoFormat: MediaFormat? = null
    private var audioFormat: MediaFormat? = null
    private var videoTrackIndex: Int = -1
    private var audioTrackIndex: Int = -1
    private var muxerStarted: Boolean = false
    private val pendingVideoSamples = mutableListOf<Pair<ByteArray, MediaCodec.BufferInfo>>()
    private val pendingAudioSamples = mutableListOf<Pair<ByteArray, MediaCodec.BufferInfo>>()
    private val segmentList = mutableListOf<SegmentInfo>()

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
                #EXT-X-VERSION:4
                #EXT-X-TARGETDURATION:${segmentDuration + 1}
                #EXT-X-MEDIA-SEQUENCE:0
                #EXT-X-INDEPENDENT-SEGMENTS
                #EXT-X-PLAYLIST-TYPE:EVENT
            """.trimIndent()
            playlistFile.writeText(playlist)
            Log.d(TAG, "Created initial playlist")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating initial playlist: ${e.message}", e)
        }
    }

    // New methods for muxer-based HLS
    @Synchronized
    fun setVideoFormat(format: MediaFormat) {
        videoFormat = format
        Log.i(TAG, "Video format set: $format")
        tryStartMuxer()
    }

    @Synchronized
    fun setAudioFormat(format: MediaFormat) {
        audioFormat = format
        Log.i(TAG, "Audio format set: $format")
        tryStartMuxer()
    }

    @Synchronized
    fun writeVideoSample(data: ByteArray, bufferInfo: MediaCodec.BufferInfo) {
        if (baseTimestampUs < 0) {
            baseTimestampUs = bufferInfo.presentationTimeUs
        }
        val adjustedPts = bufferInfo.presentationTimeUs - baseTimestampUs
        lastSamplePtsUs = adjustedPts
        val isKeyFrame = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
        val segmentDurationUs = segmentDuration * 1_000_000L
        if (isKeyFrame && (segmentStartPtsUs < 0 || adjustedPts - segmentStartPtsUs >= segmentDurationUs)) {
            if (muxerStarted) finishCurrentSegment()
            startNewSegment(adjustedPts)
        }
        val info = MediaCodec.BufferInfo().apply {
            set(0, data.size, adjustedPts, bufferInfo.flags)
        }
        if (muxerStarted) {
            try {
                currentMuxer?.writeSampleData(videoTrackIndex, ByteBuffer.wrap(data), info)
            } catch (e: Exception) {
                Log.e(TAG, "Error writing video sample: ${e.message}", e)
            }
        } else {
            pendingVideoSamples.add(data to info)
        }
        sampleCount++
        if (sampleCount % 100 == 0) {
            Log.d(TAG, "Wrote $sampleCount video samples")
        }
    }

    @Synchronized
    fun writeAudioSample(data: ByteArray, bufferInfo: MediaCodec.BufferInfo) {
        if (baseTimestampUs < 0) return // Wait for video base
        val adjustedPts = bufferInfo.presentationTimeUs - baseTimestampUs
        val info = MediaCodec.BufferInfo().apply {
            set(0, data.size, adjustedPts, bufferInfo.flags)
        }
        if (!muxerStarted) {
            // Buffer until muxer starts
            pendingAudioSamples.add(data to info)
        } else if (audioTrackIndex >= 0) {
            try {
                currentMuxer?.writeSampleData(audioTrackIndex, ByteBuffer.wrap(data), info)
            } catch (e: Exception) {
                Log.e(TAG, "Error writing audio sample: ${e.message}", e)
            }
        } else {
            // Muxer started video-only, discard audio
            Log.d(TAG, "Audio sample discarded: muxer started video-only")
        }
        if (pendingAudioSamples.size % 100 == 0) {
            Log.d(TAG, "Buffered ${pendingAudioSamples.size} audio samples")
        }
    }

    private fun startNewSegment(startPtsUs: Long) {
        try {
            val segmentNumber = segmentCounter.incrementAndGet()
            val filename = "segment_$segmentNumber.mp4"
            val file = File(outputDir, filename)
            currentSegmentFile = file
            videoTrackIndex = -1
            audioTrackIndex = -1
            muxerStarted = false
            currentMuxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            segmentStartPtsUs = startPtsUs
            segmentCreatedRealtimeMs = System.currentTimeMillis()
            tryStartMuxer()
            Log.d(TAG, "Started new segment: $filename, videoFormat=${videoFormat != null}, audioFormat=${audioFormat != null}, segmentStartPtsUs=$segmentStartPtsUs, audioRequired=$audioRequired")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting new segment: ${e.message}", e)
        }
    }

    private fun tryStartMuxer() {
        if (currentMuxer != null && videoFormat != null && !muxerStarted) {
            if (audioRequired) {
                if (audioFormat == null) {
                    val now = System.currentTimeMillis()
                    val graceExpired = (now - segmentCreatedRealtimeMs) >= audioStartGraceMs
                    if (!graceExpired) {
                        // Wait for audio format to arrive
                        Log.d(TAG, "Waiting for audio format before starting muxer (grace period not expired)")
                        return
                    } else {
                        // Grace expired, start video-only segment
                        Log.w(TAG, "Audio format not available after grace period; starting video-only segment")
                        audioTrackIndex = -1
                        muxerStarted = true
                        try {
                            videoTrackIndex = currentMuxer!!.addTrack(videoFormat!!)
                            currentMuxer!!.start()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error starting video-only muxer: ${e.message}", e)
                        }
                        // Discard any pending audio samples
                        pendingAudioSamples.clear()
                        Log.i(TAG, "Segment started video-only after grace period expired")
                        // Flush pending video samples
                        for ((data, info) in pendingVideoSamples) {
                            currentMuxer!!.writeSampleData(videoTrackIndex, ByteBuffer.wrap(data), info)
                        }
                        pendingVideoSamples.clear()
                        return
                    }
                }
            }
            // Start muxer with both tracks if audio is available or audio not required
            try {
                videoTrackIndex = currentMuxer!!.addTrack(videoFormat!!)
                if (audioRequired && audioFormat != null) {
                    audioTrackIndex = currentMuxer!!.addTrack(audioFormat!!)
                } else {
                    audioTrackIndex = -1
                }
                currentMuxer!!.start()
                muxerStarted = true
                Log.i(TAG, "Muxer started: videoTrack=$videoTrackIndex, audioTrack=$audioTrackIndex")
                // Flush pending video samples
                for ((data, info) in pendingVideoSamples) {
                    currentMuxer!!.writeSampleData(videoTrackIndex, ByteBuffer.wrap(data), info)
                }
                pendingVideoSamples.clear()
                // Flush pending audio samples if audio track exists
                if (audioTrackIndex >= 0) {
                    for ((data, info) in pendingAudioSamples) {
                        currentMuxer!!.writeSampleData(audioTrackIndex, ByteBuffer.wrap(data), info)
                    }
                }
                pendingAudioSamples.clear()
                if (audioRequired && audioTrackIndex >= 0) {
                    Log.i(TAG, "Segment started with audio")
                } else if (audioRequired) {
                    Log.i(TAG, "Segment started video-only (audio missing)")
                } else {
                    Log.i(TAG, "Segment started video-only (audio disabled)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting muxer: ${e.message}", e)
            }
        }
    }

    private fun finishCurrentSegment() {
        try {
            val duration = if (lastSamplePtsUs >= segmentStartPtsUs) (lastSamplePtsUs - segmentStartPtsUs) / 1_000_000f else segmentDuration.toFloat()
            if (muxerStarted) {
                try {
                    currentMuxer?.stop()
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping muxer: ${e.message}", e)
                }
            }
            try {
                currentMuxer?.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing muxer: ${e.message}", e)
            }
            currentMuxer = null
            muxerStarted = false
            pendingVideoSamples.clear()
            pendingAudioSamples.clear()
            currentSegmentFile?.let { file ->
                if (file.exists() && file.length() > 0) {
                    val segmentInfo = SegmentInfo(
                        filename = file.name,
                        duration = duration,
                        sequence = segmentCounter.get()
                    )
                    segmentList.add(segmentInfo)
                    while (segmentList.size > maxSegments) {
                        val removed = segmentList.removeAt(0)
                        val oldFile = File(outputDir, removed.filename)
                        if (oldFile.exists()) {
                            oldFile.delete()
                            Log.d(TAG, "Deleted old segment: ${removed.filename}")
                        }
                    }
                    updatePlaylist()
                    Log.d(TAG, "Finished segment: ${file.name}, duration: $duration seconds, size: ${file.length()} bytes")
                } else {
                    Log.w(TAG, "Segment file is empty or doesn't exist: ${file.name}")
                    file.delete()
                }
            }
            currentSegmentFile = null
            segmentStartPtsUs = -1
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
            playlist.append("#EXT-X-VERSION:4\n")
            playlist.append("#EXT-X-TARGETDURATION:$targetDuration\n")
            playlist.append("#EXT-X-MEDIA-SEQUENCE:${segmentList.firstOrNull()?.sequence ?: 0}\n")
            playlist.append("#EXT-X-INDEPENDENT-SEGMENTS\n")
            playlist.append("#EXT-X-PLAYLIST-TYPE:EVENT\n")
            playlist.append("#EXT-X-ALLOW-CACHE:YES\n")
            // No #EXT-X-STREAM-INF in media playlist
            for (segment in segmentList) {
                val segmentFile = File(outputDir, segment.filename)
                if (segmentFile.exists()) {
                    playlist.append("#EXTINF:${String.format("%.3f", segment.duration)},\n")
                    playlist.append("${segment.filename}\n")
                }
            }
            playlistFile.writeText(playlist.toString())
            Log.d(TAG, "Updated playlist with ${segmentList.size} segments")
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
            if (currentSegmentFile != null) {
                finishCurrentSegment() // This will handle stopping and releasing the muxer
            }
            videoFormat = null
            audioFormat = null
            currentSegmentFile = null
            Log.d(TAG, "HLS Writer closed. Total samples written: $sampleCount")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing HLS writer: ${e.message}", e)
        }
    }
}