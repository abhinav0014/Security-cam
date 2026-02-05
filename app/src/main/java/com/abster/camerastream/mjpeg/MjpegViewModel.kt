package com.abster.camerastream.mjpeg

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.IOException
import java.io.OutputStream
import java.util.ArrayDeque
import kotlin.math.max

class MjpegViewModel(application: Application) : AndroidViewModel(application) {

    private val streamManager = StreamManager(OkHttpClient())

    private val _frame = MutableStateFlow<Frame?>(null)
    val frame: StateFlow<Frame?> = _frame.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _fps = MutableStateFlow(0.0)
    val fps: StateFlow<Double> = _fps.asStateFlow()

    private val timestamps = ArrayDeque<Long>()

    private var streamJob: Job? = null

    fun start(url: String) {
        if (_isStreaming.value) return
        _isStreaming.value = true
        streamJob = viewModelScope.launch {
            var backoff = 1000L
            while (_isStreaming.value) {
                try {
                    streamManager.streamFrames(url)
                        .collect { f ->
                            _frame.value = f
                            onFrameReceived(f.timestamp)
                            backoff = 1000L
                        }
                } catch (e: Exception) {
                    // log and retry with backoff
                    e.printStackTrace()
                    delay(backoff + (0..500).random())
                    backoff = max(1000L, backoff * 2).coerceAtMost(30_000L)
                }
            }
        }
    }

    fun stop() {
        _isStreaming.value = false
        streamJob?.cancel()
        streamJob = null
    }

    private fun onFrameReceived(ts: Long) {
        timestamps.addLast(ts)
        while (timestamps.size > 20) timestamps.removeFirst()
        if (timestamps.size >= 2) {
            val dt = (timestamps.last - timestamps.first).coerceAtLeast(1L)
            val fpsCalc = (timestamps.size - 1) * 1000.0 / dt
            _fps.value = fpsCalc
        }
    }

    suspend fun takeSnapshot(displayNamePrefix: String = "snapshot") : String {
        val current = _frame.value ?: throw IOException("No frame to snapshot")
        val bmp = current.bitmap
        val resolver = getApplication<Application>().contentResolver
        val ts = System.currentTimeMillis()
        val filename = "${displayNamePrefix}_${ts}.jpg"
        val mime = "image/jpeg"

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MJPEGCaptures")
            }
        }

        val uri = resolver.insert(collection, values) ?: throw IOException("Failed to create media store entry")
        val out: OutputStream = resolver.openOutputStream(uri) ?: throw IOException("Failed to open output stream")
        out.use { stream ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 95, stream)
        }

        return uri.toString()
    }
}
