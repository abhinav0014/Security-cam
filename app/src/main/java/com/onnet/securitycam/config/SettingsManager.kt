package com.onnet.securitycam.config

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import java.io.File

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("camera_settings", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val appContext = context.applicationContext

    var cameraSettings: CameraSettings
        get() = prefs.getString("camera_settings", null)?.let {
            try {
                gson.fromJson(it, CameraSettings::class.java)
            } catch (e: Exception) {
                CameraSettings()
            }
        } ?: CameraSettings()
        set(value) {
            prefs.edit {
                putString("camera_settings", gson.toJson(value))
            }
        }

    fun getDefaultRecordingDirectory(): File {
        val baseDir = appContext.getExternalFilesDir(null) ?: appContext.filesDir
        return File(baseDir, "recordings").apply { mkdirs() }
    }

    fun saveSettings(settings: CameraSettings) {
        cameraSettings = settings
    }

    fun getStorageUsage(): Long {
        return getDefaultRecordingDirectory().walkBottomUp()
            .filter { it.isFile }
            .map { it.length() }
            .sum()
    }

    fun cleanupOldRecordings() {
        val settings = cameraSettings
        val retentionMillis = settings.recording.retentionDays * 24 * 60 * 60 * 1000L
        val now = System.currentTimeMillis()

        getDefaultRecordingDirectory().walkBottomUp()
            .filter { it.isFile }
            .filter { now - it.lastModified() > retentionMillis }
            .forEach { it.delete() }
    }

    companion object {
        @Volatile private var instance: SettingsManager? = null

        fun getInstance(context: Context): SettingsManager {
            return instance ?: synchronized(this) {
                instance ?: SettingsManager(context).also { instance = it }
            }
        }
    }
}