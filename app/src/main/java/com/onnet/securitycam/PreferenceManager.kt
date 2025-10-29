package com.onnet.securitycam

import android.content.Context

class PreferencesManager(context: Context) {

    private val prefs = context.getSharedPreferences("cam_prefs", Context.MODE_PRIVATE)

    // Resolution: "480p", "720p", "1080p"
    fun setResolution(value: String) = prefs.edit().putString("resolution", value).apply()
    fun getResolution(): String = prefs.getString("resolution", "1080p")!!

    // FPS: 15, 30, 60
    fun setFps(value: Int) = prefs.edit().putInt("fps", value).apply()
    fun getFps(): Int = prefs.getInt("fps", 30)

    // Bitrate in bps: e.g., 4000000 = 4 Mbps
    fun setBitrate(value: Int) = prefs.edit().putInt("bitrate", value).apply()
    fun getBitrate(): Int = prefs.getInt("bitrate", 4000000)

    // Audio streaming on/off
    fun setAudioEnabled(enabled: Boolean) = prefs.edit().putBoolean("audio", enabled).apply()
    fun isAudioEnabled(): Boolean = prefs.getBoolean("audio", true)

    // Audio sample rate (Hz)
    fun setAudioSampleRate(value: Int) = prefs.edit().putInt("audio_sample_rate", value).apply()
    fun getAudioSampleRate(): Int = prefs.getInt("audio_sample_rate", 44100)

    // Audio bitrate (bps)
    fun setAudioBitrate(value: Int) = prefs.edit().putInt("audio_bitrate", value).apply()
    fun getAudioBitrate(): Int = prefs.getInt("audio_bitrate", 128000)

    // HLS streaming on/off
    fun setHlsEnabled(enabled: Boolean) = prefs.edit().putBoolean("hls", enabled).apply()
    fun isHlsEnabled(): Boolean = prefs.getBoolean("hls", true)

    // Server port
    fun setPort(value: Int) = prefs.edit().putInt("port", value).apply()
    fun getPort(): Int = prefs.getInt("port", 8080)

    // WebSocket server port
    fun setWebSocketPort(value: Int) = prefs.edit().putInt("websocket_port", value).apply()
    fun getWebSocketPort(): Int = prefs.getInt("websocket_port", getPort() + 1)

    // Stream protocol
    fun setStreamProtocol(value: String) = prefs.edit().putString("stream_protocol", value).apply()
    fun getStreamProtocol(): String = prefs.getString("stream_protocol", PROTOCOL_HLS)!!

    companion object {
        const val PROTOCOL_HLS = "HLS"
        const val PROTOCOL_WEBSOCKET = "WebSocket"
        const val PROTOCOL_BOTH = "Both"
    }
}