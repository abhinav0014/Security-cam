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

    // HLS streaming on/off
    fun setHlsEnabled(enabled: Boolean) = prefs.edit().putBoolean("hls", enabled).apply()
    fun isHlsEnabled(): Boolean = prefs.getBoolean("hls", true)

    // Server port
    fun setPort(value: Int) = prefs.edit().putInt("port", value).apply()
    fun getPort(): Int = prefs.getInt("port", 8080)
}