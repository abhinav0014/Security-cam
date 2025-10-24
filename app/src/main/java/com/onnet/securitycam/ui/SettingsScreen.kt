package com.onnet.securitycam.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onnet.securitycam.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = viewModel()) {
    val port by vm.streamPort.collectAsStateWithLifecycle()
    val resolution by vm.resolution.collectAsStateWithLifecycle()
    val bitrate by vm.bitrate.collectAsStateWithLifecycle()
    val fps by vm.fps.collectAsStateWithLifecycle()
    val audioEnabled by vm.enableAudio.collectAsStateWithLifecycle()
    val motionEnabled by vm.motionDetection.collectAsStateWithLifecycle()
    val nightEnabled by vm.nightVision.collectAsStateWithLifecycle()

    var portText by rememberSaveable { mutableStateOf(port.toString()) }

    Scaffold(topBar = { androidx.compose.material3.TopAppBar(title = { Text("Settings") }) }) { inner: PaddingValues ->
        Column(modifier = Modifier.padding(inner).padding(16.dp)) {
            Text("Stream Port")
            TextField(value = portText, onValueChange = { new -> portText = new }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.padding(8.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Text("Video Resolution: $resolution", modifier = Modifier.weight(1f))
                Button(onClick = { vm.setResolution("1280x720") }) { Text("1280x720") }
                Spacer(modifier = Modifier.padding(4.dp))
                Button(onClick = { vm.setResolution("1920x1080") }) { Text("1920x1080") }
            }

            Spacer(modifier = Modifier.padding(8.dp))
            Text("Bitrate: ${bitrate} Mbps")
            Row {
                Button(onClick = { vm.setBitrate((bitrate - 0.5).coerceAtLeast(0.5)) }) { Text("-") }
                Spacer(modifier = Modifier.padding(8.dp))
                Button(onClick = { vm.setBitrate((bitrate + 0.5)) }) { Text("+") }
            }

            Spacer(modifier = Modifier.padding(8.dp))
            Text("FPS: $fps")
            Row {
                Button(onClick = { vm.setFps((fps - 1).coerceAtLeast(1)) }) { Text("-") }
                Spacer(modifier = Modifier.padding(8.dp))
                Button(onClick = { vm.setFps((fps + 1)) }) { Text("+") }
            }

            Spacer(modifier = Modifier.padding(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), ) {
                Text("Enable Audio", modifier = Modifier.weight(1f))
                Switch(checked = audioEnabled, onCheckedChange = { vm.setEnableAudio(it) })
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                Text("Motion Detection", modifier = Modifier.weight(1f))
                Switch(checked = motionEnabled, onCheckedChange = { vm.setMotionDetection(it) })
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                Text("Night Vision", modifier = Modifier.weight(1f))
                Switch(checked = nightEnabled, onCheckedChange = { vm.setNightVision(it) })
            }

            Spacer(modifier = Modifier.padding(12.dp))
            Row {
                Button(onClick = {
                    // Save port if valid integer
                    portText.toIntOrNull()?.let { value -> vm.setStreamPort(value) }
                }) { Text("Save Settings") }
                Spacer(modifier = Modifier.padding(8.dp))
                Button(onClick = onBack) { Text("Back") }
            }
        }
    }
}
