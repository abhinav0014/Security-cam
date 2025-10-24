package com.onnet.securitycam

import fi.iki.elonen.NanoHTTPD
import java.io.InputStream
import java.util.concurrent.atomic.AtomicReference

class CameraServer(private val latestFrame: AtomicReference<ByteArray>, private val port: Int = 8080) :
    NanoHTTPD(port) {

    override fun serve(session: IHTTPSession?): Response {
        // Use a unique frame boundary
        val boundary = "frameboundary"
        
        // Create an input stream that continuously delivers frames
        val stream = object : InputStream() {
            private var buffer: ByteArray? = null
            private var index = 0

            override fun read(): Int {
                // When buffer is empty or fully read, get a new frame
                if (buffer == null || index >= buffer!!.size) {
                    val frame = latestFrame.get() ?: return -1
                    // Construct MJPEG frame with headers
                    val header = "--$boundary\r\n" +
                            "Content-Type: image/jpeg\r\n" +
                            "Content-Length: ${frame.size}\r\n\r\n"
                    // Combine header, frame data and trailing newline
                    buffer = (header.toByteArray() + frame + "\r\n".toByteArray())
                    index = 0
                }
                return buffer!![index++].toInt() and 0xFF
            }
        }

        // Return a chunked response for MJPEG streaming
        return newChunkedResponse(
            Response.Status.OK,
            "multipart/x-mixed-replace; boundary=$boundary",
            stream
        )
    }
}