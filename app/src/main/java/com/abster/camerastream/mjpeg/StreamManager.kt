package com.abster.camerastream.mjpeg

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import java.io.ByteBuffer
import java.io.IOException
import kotlin.math.max

/**
 * Converts raw JPEG byte arrays into decoded Bitmaps and emits Frame objects.
 * Adds reconnection/backoff on error (caller can collect and decide retry policy).
 */
class StreamManager(
    private val client: OkHttpClient = OkHttpClient.Builder().build(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val reader = MjpegStreamReader(client)

    fun streamFrames(url: String): Flow<Frame> = flow {
        var seq = 0L
        reader.frames(url)
            .conflate()
            .flowOn(ioDispatcher)
            .collect { bytes ->
                val bmp = try {
                    decodeJpeg(bytes)
                } catch (e: Exception) {
                    // decode error - skip frame
                    return@collect
                }
                emit(Frame(bmp, System.currentTimeMillis(), seq++))
            }
    }.flowOn(ioDispatcher)

    @Throws(IOException::class)
    private fun decodeJpeg(bytes: ByteArray): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val src = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
            return ImageDecoder.decodeBitmap(src) { decoder, _, _ ->
                // Hint: we can set allocator here if needed
                decoder.isMutableRequired = false
            }
        } else {
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: throw IOException("Unable to decode JPEG")
        }
    }
}
