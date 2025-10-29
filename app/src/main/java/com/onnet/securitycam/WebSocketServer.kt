package com.onnet.securitycam

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoWSD
import fi.iki.elonen.NanoWSD.WebSocket
import fi.iki.elonen.NanoWSD.WebSocketFrame
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

class WebSocketServer(port: Int) : NanoWSD(port) {
    companion object {
        private const val TAG = "WebSocketServer"
        private const val FRAME_TYPE_VIDEO: Byte = 1
        private const val FRAME_TYPE_AUDIO: Byte = 2
        private const val FRAME_TYPE_VIDEO_CONFIG: Byte = 3
        private const val FRAME_TYPE_AUDIO_CONFIG: Byte = 4
        private const val FLAG_KEYFRAME: Byte = 1
        private const val HEADER_SIZE = 14 // 1 type + 1 flags + 8 timestamp + 4 length
    }

    private val clients = ConcurrentHashMap.newKeySet<WebSocket>()
    private var videoFormat: MediaFormat? = null
    private var audioFormat: MediaFormat? = null
    private var lastSpsNal: ByteArray? = null
    private var lastPpsNal: ByteArray? = null

    override fun openWebSocket(session: IHTTPSession): WebSocket {
        Log.d(TAG, "New WebSocket connection attempt from ${session.remoteIpAddress} to ${session.uri}")
        
        if (session.uri != "/stream") {
            Log.w(TAG, "Blocked WebSocket connection to invalid path ${session.uri} from ${session.remoteIpAddress}")
            return object : WebSocket(session) {
                override fun onOpen() {
                    close(WebSocketFrame.CloseCode.PolicyViolation, "Invalid WebSocket path. Use /stream")
                }
                override fun onClose(code: WebSocketFrame.CloseCode?, reason: String?, initiatedByRemote: Boolean) {}
                override fun onMessage(message: WebSocketFrame?) {}
                override fun onPong(pong: WebSocketFrame?) {}
                override fun onException(exception: IOException?) {}
            }
        }

        Log.d(TAG, "Accepted WebSocket connection from ${session.remoteIpAddress}")
        return StreamWebSocket(session)
    }

    inner class StreamWebSocket(handshake: IHTTPSession) : WebSocket(handshake) {
        override fun onOpen() {
            clients.add(this)
            Log.d(TAG, "WebSocket client connected. Total clients: ${clients.size}")
            sendConfigToClient(this)
        }

        override fun onClose(code: WebSocketFrame.CloseCode?, reason: String?, initiatedByRemote: Boolean) {
            clients.remove(this)
            Log.d(TAG, "WebSocket client disconnected. Reason: $reason. Total clients: ${clients.size}")
        }

        override fun onMessage(message: WebSocketFrame) {
            // Handle client messages (e.g., keyframe requests) in the future
            Log.d(TAG, "Received message from client: ${message.textPayload}")
        }

        override fun onPong(pong: WebSocketFrame) {
            // Log heartbeat response if needed
        }

        override fun onException(exception: IOException) {
            clients.remove(this)
            Log.e(TAG, "WebSocket error: ${exception.message}", exception)
        }
    }

    fun setVideoFormat(format: MediaFormat) {
        videoFormat = format
        
        // Try Annex-B format in csd-0 first
        if (tryParseAnnexB(format.getByteBuffer("csd-0")?.let { csd0 ->
            ByteArray(csd0.remaining()).also { csd0.get(it) }
        })) {
            Log.d(TAG, "Successfully parsed SPS/PPS from csd-0 Annex-B format")
            return
        }

        // Try csd-1 for PPS if PPS is missing
        if (lastPpsNal == null) {
            format.getByteBuffer("csd-1")?.let { csd1 ->
                val csd1Bytes = ByteArray(csd1.remaining())
                csd1.get(csd1Bytes)
                if (tryParseAnnexB(csd1Bytes)) {
                    Log.d(TAG, "Successfully parsed PPS from csd-1 Annex-B format")
                    return
                }
            }
        }

        // Try avcC format as last resort
        format.getByteBuffer("csd-0")?.let { csd0 ->
            val csd0Bytes = ByteArray(csd0.remaining())
            csd0.get(csd0Bytes)
            if (tryParseAvcC(csd0Bytes)) {
                Log.d(TAG, "Successfully parsed SPS/PPS from avcC format")
                return
            }
        }

        Log.w(TAG, "Failed to extract SPS/PPS from any known format")
    }

    private fun tryParseAnnexB(data: ByteArray?): Boolean {
        if (data == null) return false
        
        var offset = 0
        var foundSps = lastSpsNal != null
        var foundPps = lastPpsNal != null
        
        while (offset < data.size) {
            // Find NAL unit start code (0x00 0x00 0x00 0x01)
            if (offset + 4 <= data.size &&
                data[offset] == 0.toByte() &&
                data[offset + 1] == 0.toByte() &&
                data[offset + 2] == 0.toByte() &&
                data[offset + 3] == 1.toByte()
            ) {
                val nalType = (data[offset + 4].toInt() and 0x1F)
                if (!foundSps && nalType == 7) { // SPS
                    var end = offset + 5
                    while (end < data.size - 3) {
                        if (data[end] == 0.toByte() &&
                            data[end + 1] == 0.toByte() &&
                            data[end + 2] == 0.toByte() &&
                            data[end + 3] == 1.toByte()
                        ) {
                            break
                        }
                        end++
                    }
                    lastSpsNal = data.sliceArray(offset until end)
                    foundSps = true
                    offset = end
                } else if (!foundPps && nalType == 8) { // PPS
                    lastPpsNal = data.sliceArray(offset until data.size)
                    foundPps = true
                    break
                }
            }
            offset++
        }
        return foundSps || foundPps
    }

    private fun tryParseAvcC(data: ByteArray): Boolean {
        if (data.size < 6) return false
        
        try {
            // Verify avcC header
            if (data[0] != 1.toByte()) { // configurationVersion
                return false
            }

            val lengthSize = (data[4] and 0x03) + 1 // lengthSizeMinusOne
            var offset = 5

            // Skip 3 bytes
            if (offset + 3 > data.size) return false
            val numSps = data[offset + 1].toInt() and 0x1F
            offset += 2

            // Parse SPS
            for (i in 0 until numSps) {
                if (offset + 2 > data.size) return false
                val spsLen = (data[offset].toInt() and 0xFF shl 8) or (data[offset + 1].toInt() and 0xFF)
                offset += 2
                if (offset + spsLen > data.size) return false
                
                lastSpsNal = ByteArray(spsLen + 4)
                // Add Annex-B start code
                lastSpsNal!![0] = 0
                lastSpsNal!![1] = 0
                lastSpsNal!![2] = 0
                lastSpsNal!![3] = 1
                System.arraycopy(data, offset, lastSpsNal!!, 4, spsLen)
                offset += spsLen
            }

            // Parse PPS
            if (offset + 1 > data.size) return false
            val numPps = data[offset++].toInt() and 0xFF
            for (i in 0 until numPps) {
                if (offset + 2 > data.size) return false
                val ppsLen = (data[offset].toInt() and 0xFF shl 8) or (data[offset + 1].toInt() and 0xFF)
                offset += 2
                if (offset + ppsLen > data.size) return false

                lastPpsNal = ByteArray(ppsLen + 4)
                // Add Annex-B start code
                lastPpsNal!![0] = 0
                lastPpsNal!![1] = 0
                lastPpsNal!![2] = 0
                lastPpsNal!![3] = 1
                System.arraycopy(data, offset, lastPpsNal!!, 4, ppsLen)
                offset += ppsLen
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing avcC format", e)
            return false
        }
    }
        }
        Log.d(TAG, "Video format set: $format")
    }

    fun setAudioFormat(format: MediaFormat) {
        audioFormat = format
        Log.d(TAG, "Audio format set: $format")
    }

    fun broadcastVideoFrame(data: ByteArray, bufferInfo: MediaCodec.BufferInfo) {
        val isKeyframe = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
        val message = createMessage(
            FRAME_TYPE_VIDEO,
            if (isKeyframe) FLAG_KEYFRAME else 0.toByte(),
            bufferInfo.presentationTimeUs,
            if (isKeyframe && lastSpsNal != null && lastPpsNal != null) {
                // For keyframes, prepend SPS and PPS NAL units
                ByteBuffer.allocate(lastSpsNal!!.size + lastPpsNal!!.size + data.size)
                    .put(lastSpsNal!!)
                    .put(lastPpsNal!!)
                    .put(data)
                    .array()
            } else {
                data
            }
        )
        broadcast(message)
    }

    fun broadcastAudioFrame(data: ByteArray, bufferInfo: MediaCodec.BufferInfo) {
        val message = createMessage(
            FRAME_TYPE_AUDIO,
            0.toByte(),
            bufferInfo.presentationTimeUs,
            data
        )
        broadcast(message)
    }

    private fun createMessage(type: Byte, flags: Byte, timestamp: Long, payload: ByteArray): ByteArray {
        return ByteBuffer.allocate(HEADER_SIZE + payload.size).apply {
            put(type)
            put(flags)
            putLong(timestamp)
            putInt(payload.size)
            put(payload)
        }.array()
    }

    private fun broadcast(message: ByteArray) {
        val iterator = clients.iterator()
        while (iterator.hasNext()) {
            val client = iterator.next()
            try {
                client.send(message)
            } catch (e: IOException) {
                Log.e(TAG, "Error broadcasting to client: ${e.message}")
                iterator.remove()
                try {
                    client.close(WebSocketFrame.CloseCode.NormalClosure, "Broadcasting error")
                } catch (e: IOException) {
                    Log.e(TAG, "Error closing client socket: ${e.message}")
                }
            }
        }
    }

    private fun sendConfigToClient(socket: WebSocket) {
        try {
            // Send video configuration
            if (lastSpsNal != null && lastPpsNal != null) {
                val videoConfigMessage = createMessage(
                    FRAME_TYPE_VIDEO_CONFIG,
                    0.toByte(),
                    0,
                    ByteBuffer.allocate(lastSpsNal!!.size + lastPpsNal!!.size)
                        .put(lastSpsNal!!)
                        .put(lastPpsNal!!)
                        .array()
                )
                socket.send(videoConfigMessage)
            }

            // Send audio configuration if available
            audioFormat?.getByteBuffer("csd-0")?.let { csd0 ->
                val audioConfig = ByteArray(csd0.remaining())
                csd0.get(audioConfig)
                val audioConfigMessage = createMessage(
                    FRAME_TYPE_AUDIO_CONFIG,
                    0.toByte(),
                    0,
                    audioConfig
                )
                socket.send(audioConfigMessage)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error sending config to client: ${e.message}")
            clients.remove(socket)
            try {
                socket.close(WebSocketFrame.CloseCode.NormalClosure, "Config send error")
            } catch (e: IOException) {
                Log.e(TAG, "Error closing client socket: ${e.message}")
            }
        }
    }

    fun getClientCount(): Int = clients.size

    override fun stop() {
        super.stop()
        clients.forEach { client ->
            try {
                client.close(WebSocketFrame.CloseCode.NormalClosure, "Server shutting down")
            } catch (e: IOException) {
                Log.e(TAG, "Error closing client socket during shutdown: ${e.message}")
            }
        }
        clients.clear()
        Log.d(TAG, "WebSocket server stopped")
    }
}