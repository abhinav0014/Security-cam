package com.abster.camerastream.mjpeg

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Very small MJPEG reader that emits raw JPEG byte arrays for each frame.
 * It scans the byte stream for JPEG SOI (0xFFD8) and EOI (0xFFD9) markers.
 * This is intentionally simple and robust enough for most MJPEG cameras.
 */
class MjpegStreamReader(private val client: OkHttpClient = OkHttpClient.Builder().build()) {

    fun frames(url: String): Flow<ByteArray> = flow {
        val request = Request.Builder().url(url).build()
        val call = client.newCall(request)

        call.execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val input = resp.body?.byteStream() ?: throw IOException("Empty response body")

            val buffer = ByteArray(8192)
            var prev = -1
            val baos = ByteArrayOutputStream()
            var inFrame = false

            while (true) {
                val read = input.read(buffer)
                if (read == -1) break

                for (i in 0 until read) {
                    val b = buffer[i].toInt() and 0xFF
                    if (!inFrame) {
                        // look for 0xFF 0xD8 (SOI)
                        if (prev == 0xFF && b == 0xD8) {
                            inFrame = true
                            baos.reset()
                            baos.write(0xFF)
                            baos.write(0xD8)
                        }
                    } else {
                        baos.write(b)
                        // look for 0xFF 0xD9 (EOI)
                        if (prev == 0xFF && b == 0xD9) {
                            emit(baos.toByteArray())
                            inFrame = false
                            baos.reset()
                        }
                    }
                    prev = b
                }
            }
        }
    }.flowOn(Dispatchers.IO)
}
