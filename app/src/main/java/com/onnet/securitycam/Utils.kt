package com.onnet.securitycam

import android.content.Context
import android.net.wifi.WifiManager
import androidx.camera.core.ImageProxy
import java.net.Inet4Address
import java.net.NetworkInterface
import java.nio.ByteBuffer

object Utils {
    
    /**
     * Get the device's IP address on the local network
     */
    fun getIPAddress(context: Context): String {
        try {
            // Try to get IP from WiFi first
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifiManager?.connectionInfo?.let { connectionInfo ->
                val ipAddress = connectionInfo.ipAddress
                if (ipAddress != 0) {
                    return String.format(
                        "%d.%d.%d.%d",
                        ipAddress and 0xff,
                        ipAddress shr 8 and 0xff,
                        ipAddress shr 16 and 0xff,
                        ipAddress shr 24 and 0xff
                    )
                }
            }

            // Fallback: Get IP from network interfaces
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress ?: "0.0.0.0"
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "0.0.0.0"
    }

    /**
     * Format bitrate from bps to human-readable format
     */
    fun formatBitrate(bitrate: Int): String {
        return when {
            bitrate >= 1_000_000 -> "${bitrate / 1_000_000} Mbps"
            bitrate >= 1_000 -> "${bitrate / 1_000} Kbps"
            else -> "$bitrate bps"
        }
    }

    /**
     * Convert ImageProxy YUV_420_888 format to NV21 byte array
     * NV21 is the format expected by MediaCodec
     */
    fun yuv420ToNv21(image: ImageProxy): ByteArray {
        val width = image.width
        val height = image.height
        val ySize = width * height
        val uvSize = width * height / 2

        val nv21 = ByteArray(ySize + uvSize)

        val yBuffer = image.planes[0].buffer // Y
        val uBuffer = image.planes[1].buffer // U
        val vBuffer = image.planes[2].buffer // V

        val yRowStride = image.planes[0].rowStride
        val yPixelStride = image.planes[0].pixelStride
        val uvRowStride = image.planes[1].rowStride
        val uvPixelStride = image.planes[1].pixelStride

        // Copy Y plane
        var pos = 0
        if (yPixelStride == 1) {
            // Optimized path for packed Y plane
            for (row in 0 until height) {
                yBuffer.position(row * yRowStride)
                yBuffer.get(nv21, pos, width)
                pos += width
            }
        } else {
            // General case
            for (row in 0 until height) {
                for (col in 0 until width) {
                    nv21[pos++] = yBuffer.get(row * yRowStride + col * yPixelStride)
                }
            }
        }

        // Copy UV planes (interleaved as VU for NV21)
        val uvHeight = height / 2
        val uvWidth = width / 2
        
        if (uvPixelStride == 2) {
            // Optimized path when U and V are already interleaved
            for (row in 0 until uvHeight) {
                vBuffer.position(row * uvRowStride)
                for (col in 0 until uvWidth) {
                    nv21[pos++] = vBuffer.get(col * uvPixelStride)     // V
                    nv21[pos++] = uBuffer.get(row * uvRowStride + col * uvPixelStride) // U
                }
            }
        } else {
            // General case - manually interleave
            for (row in 0 until uvHeight) {
                for (col in 0 until uvWidth) {
                    val vIndex = row * uvRowStride + col * uvPixelStride
                    val uIndex = row * uvRowStride + col * uvPixelStride
                    nv21[pos++] = vBuffer.get(vIndex) // V
                    nv21[pos++] = uBuffer.get(uIndex) // U
                }
            }
        }

        // Reset buffer positions
        yBuffer.rewind()
        uBuffer.rewind()
        vBuffer.rewind()

        return nv21
    }

    /**
     * Alternative simpler conversion (may be less efficient but more compatible)
     */
    fun yuv420ToNv21Simple(image: ImageProxy): ByteArray {
        val width = image.width
        val height = image.height
        val ySize = width * height
        val uvSize = width * height / 2

        val nv21 = ByteArray(ySize + uvSize)

        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        // Copy Y
        yBuffer.get(nv21, 0, ySize)

        // Copy and interleave VU
        val uvPixelStride = image.planes[1].pixelStride
        if (uvPixelStride == 1) {
            vBuffer.get(nv21, ySize, uvSize / 2)
            uBuffer.get(nv21, ySize + uvSize / 2, uvSize / 2)
        } else {
            // Interleave manually
            var pos = ySize
            val uvHeight = height / 2
            val uvWidth = width / 2
            for (row in 0 until uvHeight) {
                for (col in 0 until uvWidth) {
                    nv21[pos++] = vBuffer.get(row * image.planes[2].rowStride + col * uvPixelStride)
                    nv21[pos++] = uBuffer.get(row * image.planes[1].rowStride + col * uvPixelStride)
                }
            }
        }

        yBuffer.rewind()
        uBuffer.rewind()
        vBuffer.rewind()

        return nv21
    }

    /**
     * Calculate file size in human-readable format
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824 -> "%.2f GB".format(bytes / 1_073_741_824.0)
            bytes >= 1_048_576 -> "%.2f MB".format(bytes / 1_048_576.0)
            bytes >= 1_024 -> "%.2f KB".format(bytes / 1_024.0)
            else -> "$bytes B"
        }
    }

    /**
     * Format duration in human-readable format
     */
    fun formatDuration(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60

        return when {
            hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, secs)
            minutes > 0 -> String.format("%d:%02d", minutes, secs)
            else -> String.format("0:%02d", secs)
        }
    }
}