package com.stream.camera

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.stream.camera.service.StreamingService
import com.stream.camera.ui.theme.CameraStreamTheme
import com.stream.camera.utils.NetworkUtils
import kotlinx.coroutines.launch

/**
 * Camera Stream App - Live HLS Streaming
 * Created by ABSTER
 * 
 * Main Activity for camera streaming application
 */
class MainActivity : ComponentActivity() {
    
    private var isStreaming by mutableStateOf(false)
    private var streamUrl by mutableStateOf("")
    private var errorMessage by mutableStateOf<String?>(null)
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            errorMessage = "Camera and audio permissions are required for streaming"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        requestPermissions()
        
        setContent {
            CameraStreamTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    StreamingScreen(
                        isStreaming = isStreaming,
                        streamUrl = streamUrl,
                        errorMessage = errorMessage,
                        onStartStreaming = { startStreaming() },
                        onStopStreaming = { stopStreaming() },
                        onDismissError = { errorMessage = null }
                    )
                }
            }
        }
    }
    
    private fun requestPermissions() {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS
            )
        )
    }
    
    private fun startStreaming() {
        lifecycleScope.launch {
            try {
                val ipAddress = NetworkUtils.getLocalIpAddress(this@MainActivity)
                if (ipAddress == null) {
                    errorMessage = "Unable to get network IP address. Please connect to WiFi."
                    return@launch
                }
                
                streamUrl = "http://$ipAddress:8080"
                
                val intent = Intent(this@MainActivity, StreamingService::class.java)
                startForegroundService(intent)
                
                isStreaming = true
                errorMessage = null
            } catch (e: Exception) {
                errorMessage = "Failed to start streaming: ${e.message}"
                isStreaming = false
            }
        }
    }
    
    private fun stopStreaming() {
        try {
            val intent = Intent(this, StreamingService::class.java)
            stopService(intent)
            
            isStreaming = false
            streamUrl = ""
            errorMessage = null
        } catch (e: Exception) {
            errorMessage = "Failed to stop streaming: ${e.message}"
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (isStreaming) {
            stopStreaming()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamingScreen(
    isStreaming: Boolean,
    streamUrl: String,
    errorMessage: String?,
    onStartStreaming: () -> Unit,
    onStopStreaming: () -> Unit,
    onDismissError: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A237E),
                        Color(0xFF283593),
                        Color(0xFF3949AB)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = "Camera",
                    tint = Color.White,
                    modifier = Modifier.size(80.dp)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "HLS Camera Stream",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Text(
                    text = "by ABSTER",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            // Status Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.1f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(
                                    color = if (isStreaming) Color(0xFF4CAF50) else Color(0xFFFF5252),
                                    shape = RoundedCornerShape(6.dp)
                                )
                        )
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Text(
                            text = if (isStreaming) "LIVE" else "OFFLINE",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    
                    if (isStreaming && streamUrl.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Divider(color = Color.White.copy(alpha = 0.2f))
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Column(
                            horizontalAlignment = Alignment.Start,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            InfoRow(
                                icon = Icons.Default.Link,
                                label = "Stream URL",
                                value = streamUrl
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            InfoRow(
                                icon = Icons.Default.VideoLibrary,
                                label = "HLS Playlist",
                                value = "$streamUrl/stream.m3u8"
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            InfoRow(
                                icon = Icons.Default.Settings,
                                label = "Control Panel",
                                value = "$streamUrl/control"
                            )
                        }
                    }
                }
            }
            
            // Action Buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = if (isStreaming) onStopStreaming else onStartStreaming,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = RoundedCornerShape(32.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isStreaming) Color(0xFFFF5252) else Color(0xFF4CAF50)
                    )
                ) {
                    Icon(
                        imageVector = if (isStreaming) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Text(
                        text = if (isStreaming) "Stop Streaming" else "Start Streaming",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                if (isStreaming) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedButton(
                        onClick = { /* Open web UI */ },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInBrowser,
                            contentDescription = null
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Text("Open Control Panel")
                    }
                }
            }
        }
        
        // Error Snackbar
        errorMessage?.let { error ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                action = {
                    TextButton(onClick = onDismissError) {
                        Text("Dismiss")
                    }
                }
            ) {
                Text(error)
            }
        }
    }
}

@Composable
fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = label,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = value,
            fontSize = 14.sp,
            color = Color.White,
            modifier = Modifier.padding(start = 28.dp)
        )
    }
}
