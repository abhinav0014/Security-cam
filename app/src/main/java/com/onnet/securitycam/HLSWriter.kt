package com.onnet.securitycam

import android.content.Context
import android.media.MediaCodec
import java.io.File

class HLSWriter(private val context: Context, private val outputDir: File) {

    fun writeSample(data: ByteArray, bufferInfo: MediaCodec.BufferInfo) {
        // TODO: Implement HLS segment writing logic here
        
    }

    fun close() {
        // TODO: Implement cleanup logic here.
    }
}