package com.onnet.securitycam

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.onnet.securitycam.ui.SettingsScreen

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Minimal UI for now; ViewModel integration may be added later once lifecycle-compose
            // and viewmodel-compose dependencies are available.
            Scaffold(
                topBar = { TopAppBar(title = { Text("📹 Live Stream") }) },
                floatingActionButton = {
                    FloatingActionButton(onClick = { /* start/stop streaming */ }) {
                        Text("Start")
                    }
                },
                floatingActionButtonPosition = FabPosition.End
            ) { inner ->
                Column(modifier = Modifier
                    .padding(inner)
                    .fillMaxSize(), verticalArrangement = Arrangement.Top) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Status: Idle", modifier = Modifier.padding(16.dp))
                        Text("URL: rtsp://0.0.0.0:8554/live", modifier = Modifier.padding(16.dp))
                    }

                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        // Placeholder for camera preview
                        Text("Camera preview placeholder", modifier = Modifier.align(Alignment.Center))
                    }

                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Button(onClick = { /* open settings */ }) { Text("Settings") }
                        Button(onClick = { /* test stream via ExoPlayer */ }) { Text("Test Stream") }
                    }
                }
            }
        }
    }
}