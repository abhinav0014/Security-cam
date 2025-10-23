package com.onnet.securitycam.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { inner: PaddingValues ->
        Column(modifier = Modifier.padding(inner).padding(16.dp)) {
            Text("Stream Port")
            Text("Video Resolution")
            Text("Bitrate")
            Text("Frame Rate")
            Text("Enable Audio")
            Text("Motion Detection")
            Text("Night Vision")
            Button(onClick = { /* save */ }, modifier = Modifier.padding(top = 16.dp)) {
                Text("Save Settings")
            }
        }
    }
}
