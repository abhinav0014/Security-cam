package com.onnet.securitycam.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onnet.securitycam.data.DataStoreManager
import com.onnet.securitycam.utils.Utils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

class StreamViewModel(application: Application) : AndroidViewModel(application) {
    private val dataStore = DataStoreManager.getInstance(application)

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _statusMessage = MutableStateFlow("Idle")
    val statusMessage = _statusMessage.asStateFlow()

    val ipAddress = MutableStateFlow(Utils.getLocalIpAddress(application))

    fun startStreaming() {
        Timber.i("Request start streaming")
        viewModelScope.launch {
            // Start background service (StreamingService) via context
            _isStreaming.value = true
            _statusMessage.value = "Streaming"
        }
    }

    fun stopStreaming() {
        Timber.i("Request stop streaming")
        viewModelScope.launch {
            _isStreaming.value = false
            _statusMessage.value = "Idle"
        }
    }

    fun refreshIp() {
        ipAddress.value = Utils.getLocalIpAddress(getApplication())
    }
}
