package com.onnet.securitycam.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("security_cam_prefs")

class DataStoreManager(private val context: Context) {
    companion object {
        val STREAM_PORT = intPreferencesKey("stream_port")
        val RESOLUTION = stringPreferencesKey("resolution")
        val BITRATE = doublePreferencesKey("bitrate_mbps")
        val FPS = intPreferencesKey("fps")
        val ENABLE_AUDIO = booleanPreferencesKey("enable_audio")
        val MOTION_DETECTION = booleanPreferencesKey("motion_detection")
        val NIGHT_VISION = booleanPreferencesKey("night_vision")

        @Volatile
        private var INSTANCE: DataStoreManager? = null

        fun getInstance(context: Context): DataStoreManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DataStoreManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    suspend fun setStreamPort(port: Int) {
        context.dataStore.edit { prefs -> prefs[STREAM_PORT] = port }
    }

    fun getStreamPort(): Flow<Int> = context.dataStore.data.map { it[STREAM_PORT] ?: 8554 }

    suspend fun setResolution(res: String) { context.dataStore.edit { it[RESOLUTION] = res } }
    fun getResolution(): Flow<String> = context.dataStore.data.map { it[RESOLUTION] ?: "1280x720" }

    suspend fun setBitrate(mbps: Double) { context.dataStore.edit { it[BITRATE] = mbps } }
    fun getBitrate(): Flow<Double> = context.dataStore.data.map { it[BITRATE] ?: 2.0 }

    suspend fun setFps(fps: Int) { context.dataStore.edit { it[FPS] = fps } }
    fun getFps(): Flow<Int> = context.dataStore.data.map { it[FPS] ?: 24 }

    suspend fun setEnableAudio(enabled: Boolean) { context.dataStore.edit { it[ENABLE_AUDIO] = enabled } }
    fun isAudioEnabled(): Flow<Boolean> = context.dataStore.data.map { it[ENABLE_AUDIO] ?: true }

    suspend fun setMotionDetection(enabled: Boolean) { context.dataStore.edit { it[MOTION_DETECTION] = enabled } }
    fun isMotionDetectionEnabled(): Flow<Boolean> = context.dataStore.data.map { it[MOTION_DETECTION] ?: false }

    suspend fun setNightVision(enabled: Boolean) { context.dataStore.edit { it[NIGHT_VISION] = enabled } }
    fun isNightVisionEnabled(): Flow<Boolean> = context.dataStore.data.map { it[NIGHT_VISION] ?: false }
}
