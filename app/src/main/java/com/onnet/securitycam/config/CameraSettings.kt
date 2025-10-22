package com.onnet.securitycam.config

data class StreamQuality(
    val width: Int,
    val height: Int,
    val fps: Int,
    val bitrate: Int
) {
    companion object {
        val LOW = StreamQuality(640, 480, 15, 500_000)      // 500Kbps
        val MEDIUM = StreamQuality(1280, 720, 24, 2_000_000) // 2Mbps
        val HIGH = StreamQuality(1920, 1080, 30, 4_000_000)  // 4Mbps
    }
}

data class RecordingSettings(
    val enabled: Boolean = false,
    val storageLocation: String = "default",
    val recordingInterval: Long = 300, // 5 minutes in seconds
    val maxStorageSize: Long = 5L * 1024 * 1024 * 1024, // 5GB in bytes
    val quality: StreamQuality = StreamQuality.MEDIUM,
    val retentionDays: Int = 7,
    val motionTriggeredOnly: Boolean = false
)

data class EnhancementSettings(
    val nightMode: Boolean = false,
    val nightModeGain: Float = 1.5f,
    val motionDetection: Boolean = false,
    val motionSensitivity: Float = 0.5f, // 0.0 to 1.0
    val noiseReduction: Boolean = true
)

data class CameraSettings(
    var streamQuality: StreamQuality = StreamQuality.MEDIUM,
    var recording: RecordingSettings = RecordingSettings(),
    var enhancement: EnhancementSettings = EnhancementSettings(),
    var useBackCamera: Boolean = true,
    var autoFocus: Boolean = true,
    var flashEnabled: Boolean = false
)