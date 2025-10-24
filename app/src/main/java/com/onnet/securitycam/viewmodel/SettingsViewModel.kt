package com.onnet.securitycam.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onnet.securitycam.data.DataStoreManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val ds = DataStoreManager.getInstance(application)

    val streamPort: StateFlow<Int> = ds.getStreamPort()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 8554)

    val resolution: StateFlow<String> = ds.getResolution()
        .stateIn(viewModelScope, SharingStarted.Eagerly, "1280x720")

    val bitrate: StateFlow<Double> = ds.getBitrate()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 2.0)

    val fps: StateFlow<Int> = ds.getFps()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 24)

    val enableAudio: StateFlow<Boolean> = ds.isAudioEnabled()
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val motionDetection: StateFlow<Boolean> = ds.isMotionDetectionEnabled()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val nightVision: StateFlow<Boolean> = ds.isNightVisionEnabled()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setStreamPort(port: Int) = viewModelScope.launch { ds.setStreamPort(port) }
    fun setResolution(res: String) = viewModelScope.launch { ds.setResolution(res) }
    fun setBitrate(mbps: Double) = viewModelScope.launch { ds.setBitrate(mbps) }
    fun setFps(f: Int) = viewModelScope.launch { ds.setFps(f) }
    fun setEnableAudio(enabled: Boolean) = viewModelScope.launch { ds.setEnableAudio(enabled) }
    fun setMotionDetection(enabled: Boolean) = viewModelScope.launch { ds.setMotionDetection(enabled) }
    fun setNightVision(enabled: Boolean) = viewModelScope.launch { ds.setNightVision(enabled) }
}
