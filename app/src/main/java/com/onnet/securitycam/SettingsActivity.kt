package com.onnet.securitycam

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onnet.securitycam.ui.theme.SecurityCamTheme
import com.onnet.securitycam.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SecurityCamTheme {
                SettingsScreen(
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Collect states from ViewModel
    val streamPort by viewModel.streamPort.collectAsState()
    val resolution by viewModel.resolution.collectAsState()
    val bitrate by viewModel.bitrate.collectAsState()
    val fps by viewModel.fps.collectAsState()
    val audioEnabled by viewModel.enableAudio.collectAsState()
    val motionDetection by viewModel.motionDetection.collectAsState()
    val nightVision by viewModel.nightVision.collectAsState()
    
    // Local state for text fields
    var portText by remember(streamPort) { mutableStateOf(streamPort.toString()) }
    var showSaveButton by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Server Settings Section
            SettingsSectionHeader("Server Settings")
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Stream Port",
                        style = MaterialTheme.typography.titleMedium
                    )
                    OutlinedTextField(
                        value = portText,
                        onValueChange = { 
                            portText = it
                            showSaveButton = true
                        },
                        label = { Text("Port Number") },
                        placeholder = { Text("8554") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (showSaveButton) {
                        Button(
                            onClick = {
                                portText.toIntOrNull()?.let { port ->
                                    if (port in 1024..65535) {
                                        viewModel.setStreamPort(port)
                                        showSaveButton = false
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                "Port saved: $port"
                                            )
                                        }
                                    } else {
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                "Port must be between 1024 and 65535"
                                            )
                                        }
                                    }
                                } ?: scope.launch {
                                    snackbarHostState.showSnackbar(
                                        "Invalid port number"
                                    )
                                }
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Save Port")
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Video Settings Section
            SettingsSectionHeader("Video Settings")
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Resolution Setting
                    Column {
                        Text(
                            text = "Video Resolution",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Current: $resolution",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ResolutionButton(
                                text = "640x480",
                                isSelected = resolution == "640x480",
                                onClick = { viewModel.setResolution("640x480") },
                                modifier = Modifier.weight(1f)
                            )
                            ResolutionButton(
                                text = "1280x720",
                                isSelected = resolution == "1280x720",
                                onClick = { viewModel.setResolution("1280x720") },
                                modifier = Modifier.weight(1f)
                            )
                            ResolutionButton(
                                text = "1920x1080",
                                isSelected = resolution == "1920x1080",
                                onClick = { viewModel.setResolution("1920x1080") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    
                    Divider()
                    
                    // Bitrate Setting
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Bitrate",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "${"%.1f".format(bitrate)} Mbps",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalIconButton(
                                    onClick = { 
                                        viewModel.setBitrate((bitrate - 0.5).coerceAtLeast(0.5))
                                    }
                                ) {
                                    Text("-", style = MaterialTheme.typography.titleLarge)
                                }
                                FilledTonalIconButton(
                                    onClick = { 
                                        viewModel.setBitrate((bitrate + 0.5).coerceAtMost(10.0))
                                    }
                                ) {
                                    Text("+", style = MaterialTheme.typography.titleLarge)
                                }
                            }
                        }
                        Slider(
                            value = bitrate.toFloat(),
                            onValueChange = { viewModel.setBitrate(it.toDouble()) },
                            valueRange = 0.5f..10f,
                            steps = 18,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    Divider()
                    
                    // FPS Setting
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Frame Rate (FPS)",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "$fps fps",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalIconButton(
                                    onClick = { 
                                        viewModel.setFps((fps - 1).coerceAtLeast(5))
                                    }
                                ) {
                                    Text("-", style = MaterialTheme.typography.titleLarge)
                                }
                                FilledTonalIconButton(
                                    onClick = { 
                                        viewModel.setFps((fps + 1).coerceAtMost(60))
                                    }
                                ) {
                                    Text("+", style = MaterialTheme.typography.titleLarge)
                                }
                            }
                        }
                        Slider(
                            value = fps.toFloat(),
                            onValueChange = { viewModel.setFps(it.toInt()) },
                            valueRange = 5f..60f,
                            steps = 54,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Features Section
            SettingsSectionHeader("Features")
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SettingSwitch(
                        title = "Enable Audio",
                        description = "Record audio with video stream",
                        checked = audioEnabled,
                        onCheckedChange = { viewModel.setEnableAudio(it) }
                    )
                    
                    Divider()
                    
                    SettingSwitch(
                        title = "Motion Detection",
                        description = "Detect motion and trigger alerts",
                        checked = motionDetection,
                        onCheckedChange = { viewModel.setMotionDetection(it) }
                    )
                    
                    Divider()
                    
                    SettingSwitch(
                        title = "Night Vision",
                        description = "Enhanced low-light performance",
                        checked = nightVision,
                        onCheckedChange = { viewModel.setNightVision(it) }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "ℹ️ Settings Info",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Changes to video settings will take effect on the next stream session. Server port changes require app restart.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun SettingsSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
    )
}

@Composable
fun ResolutionButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isSelected) {
        Button(
            onClick = onClick,
            modifier = modifier,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier
        ) {
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun SettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
