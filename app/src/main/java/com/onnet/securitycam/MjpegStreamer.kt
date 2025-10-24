package com.onnet.securitycam

import android.util.Log
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * MJPEG Streamer - Streams video as Motion JPEG to multiple clients
 */
class MjpegStreamer {
    companion object {
        private const val TAG = "MjpegStreamer"
        private const val BOUNDARY = "mjpeg_boundary"
        private const val CONTENT_TYPE = "multipart/x-mixed-replace;boundary=$BOUNDARY"
    }

    private val clients = ConcurrentHashMap<Int, StreamClient>()
    private val clientIdCounter = AtomicInteger(0)
    
    @Volatile
    private var currentFrame: ByteArray? = null
    private val frameLock = Object()

    data class StreamClient(
        val id: Int,
        val outputStream: OutputStream,
        var isActive: Boolean = true
    )

    /**
     * Register a new client for streaming
     */
    fun addClient(outputStream: OutputStream): Int {
        val clientId = clientIdCounter.incrementAndGet()
        val client = StreamClient(clientId, outputStream)
        clients[clientId] = client
        
        Log.d(TAG, "Client $clientId connected. Total clients: ${clients.size}")
        
        // Send initial headers
        try {
            sendHeaders(outputStream)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending headers to client $clientId", e)
            removeClient(clientId)
        }
        
        return clientId
    }

    /**
     * Remove a client from streaming
     */
    fun removeClient(clientId: Int) {
        clients.remove(clientId)?.let {
            try {
                it.outputStream.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
        Log.d(TAG, "Client $clientId disconnected. Total clients: ${clients.size}")
    }

    /**
     * Update frame and broadcast to all clients
     */
    fun updateFrame(jpegData: ByteArray) {
        synchronized(frameLock) {
            currentFrame = jpegData
        }
        broadcastFrame(jpegData)
    }

    /**
     * Send HTTP headers for MJPEG stream
     */
    private fun sendHeaders(outputStream: OutputStream) {
        val headers = """
            HTTP/1.1 200 OK
            Content-Type: $CONTENT_TYPE
            Cache-Control: no-cache, no-store, must-revalidate
            Pragma: no-cache
            Expires: 0
            Connection: close
            Access-Control-Allow-Origin: *
            
        """.trimIndent().replace("\n", "\r\n")
        
        outputStream.write(headers.toByteArray())
        outputStream.flush()
    }

    /**
     * Broadcast frame to all connected clients
     */
    private fun broadcastFrame(jpegData: ByteArray) {
        val deadClients = mutableListOf<Int>()
        
        clients.forEach { (clientId, client) ->
            if (!client.isActive) {
                deadClients.add(clientId)
                return@forEach
            }
            
            try {
                sendFrameToClient(client.outputStream, jpegData)
            } catch (e: IOException) {
                Log.w(TAG, "Client $clientId stream error", e)
                client.isActive = false
                deadClients.add(clientId)
            } catch (e: Exception) {
                Log.e(TAG, "Error sending frame to client $clientId", e)
                client.isActive = false
                deadClients.add(clientId)
            }
        }
        
        // Remove dead clients
        deadClients.forEach { removeClient(it) }
    }

    /**
     * Send a single frame to a client
     */
    private fun sendFrameToClient(outputStream: OutputStream, jpegData: ByteArray) {
        val frameHeader = """
            --$BOUNDARY
            Content-Type: image/jpeg
            Content-Length: ${jpegData.size}
            
        """.trimIndent().replace("\n", "\r\n") + "\r\n"
        
        outputStream.write(frameHeader.toByteArray())
        outputStream.write(jpegData)
        outputStream.write("\r\n".toByteArray())
        outputStream.flush()
    }

    /**
     * Get current frame for snapshot
     */
    fun getCurrentFrame(): ByteArray? {
        synchronized(frameLock) {
            return currentFrame
        }
    }

    /**
     * Get number of connected clients
     */
    fun getClientCount(): Int = clients.size

    /**
     * Close all client connections
     */
    fun closeAll() {
        Log.d(TAG, "Closing all ${clients.size} client connections")
        clients.keys.toList().forEach { removeClient(it) }
    }
}
