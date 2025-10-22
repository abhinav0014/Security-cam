package com.onnet.securitycam

import android.content.Context
import android.util.Base64
import com.google.gson.Gson
import com.onnet.securitycam.config.SettingsManager
import com.onnet.securitycam.features.MotionDetector
import com.onnet.securitycam.features.RecordingManager
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import fi.iki.elonen.NanoHTTPD.Response.Status
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException

class EmbeddedServer(
    private val port: Int = 8080,
    private val context: Context,
    private val settingsManager: SettingsManager = SettingsManager.getInstance(context)
) : NanoWSD(port) {
        private val gson = Gson()
        private val activeClients = mutableSetOf<WebSocket>()
        private var currentFrame: ByteArray? = null
        private var lastFrameTimestamp: Long = 0

        override fun serveHttp(session: NanoHTTPD.IHTTPSession?): NanoHTTPD.Response {
                val uri = session?.uri ?: "/"
                return when (uri) {
                        "/" -> newFixedLengthResponse(Status.OK, "text/html", indexHtml)
                        "/settings" -> handleSettings(session)
                        "/info" -> getDeviceInfo()
                        "/frame.jpg" -> getFrame()
                        "/toggleCamera" -> toggleCamera(session)
                        "/toggleFlash" -> toggleFlash(session)
                        "/recordings" -> getRecordings()
                        "/download" -> downloadRecording(session)
                        else -> newFixedLengthResponse(Status.NOT_FOUND, "text/plain", "Not found")
                }
        }

        private fun handleSettings(session: NanoHTTPD.IHTTPSession?): NanoHTTPD.Response {
            return when (session?.method) {
                Method.GET -> {
                    val settings = settingsManager.cameraSettings
                    newFixedLengthResponse(Status.OK, "application/json", gson.toJson(settings))
                }
                Method.POST -> {
                    val files = mutableMapOf<String, String>()
                    session.parseBody(files)
                    val jsonBody = files["postData"]
                    try {
                        val settings = gson.fromJson(jsonBody, CameraSettings::class.java)
                        settingsManager.saveSettings(settings)
                        newFixedLengthResponse(Status.OK, "application/json", """{"status":"success"}""")
                    } catch (e: Exception) {
                        newFixedLengthResponse(Status.BAD_REQUEST, "application/json", """{"error":"${e.message}"}""")
                    }
                }
                else -> newFixedLengthResponse(Status.METHOD_NOT_ALLOWED, "text/plain", "Method not allowed")
            }
        }

        private fun getDeviceInfo(): NanoHTTPD.Response {
            val info = JSONObject().apply {
                put("device", android.os.Build.MODEL)
                put("manufacturer", android.os.Build.MANUFACTURER)
                put("android_version", android.os.Build.VERSION.RELEASE)
                put("sdk_level", android.os.Build.VERSION.SDK_INT)
                put("server_port", port)
                put("storage_usage", settingsManager.getStorageUsage())
            }
            return newFixedLengthResponse(Status.OK, "application/json", info.toString())
        }

        private fun getFrame(): NanoHTTPD.Response {
            val frame = currentFrame
            return if (frame != null && System.currentTimeMillis() - lastFrameTimestamp < 5000) {
                newFixedLengthResponse(Status.OK, "image/jpeg", ByteArrayInputStream(frame), frame.size.toLong())
            } else {
                newFixedLengthResponse(Status.NO_CONTENT, "text/plain", "No recent frame available")
            }
        }

        private fun toggleCamera(session: NanoHTTPD.IHTTPSession?): NanoHTTPD.Response {
            val settings = settingsManager.cameraSettings
            settings.useBackCamera = !settings.useBackCamera
            settingsManager.saveSettings(settings)
            return newFixedLengthResponse(Status.OK, "application/json", """{"status":"success"}""")
        }

        private fun toggleFlash(session: NanoHTTPD.IHTTPSession?): NanoHTTPD.Response {
            val settings = settingsManager.cameraSettings
            settings.flashEnabled = !settings.flashEnabled
            settingsManager.saveSettings(settings)
            return newFixedLengthResponse(Status.OK, "application/json", """{"status":"success"}""")
        }

        private fun getRecordings(): NanoHTTPD.Response {
            val recordings = settingsManager.getDefaultRecordingDirectory()
                .listFiles()
                ?.filter { it.isFile }
                ?.map { file ->
                    JSONObject().apply {
                        put("id", file.name)
                        put("date", file.lastModified())
                        put("size", file.length())
                    }
                } ?: emptyList()
            return newFixedLengthResponse(Status.OK, "application/json", JSONObject().put("recordings", recordings).toString())
        }

        private fun downloadRecording(session: NanoHTTPD.IHTTPSession?): NanoHTTPD.Response {
            val id = session?.parameters?.get("id")?.firstOrNull() ?: return newFixedLengthResponse(
                Status.BAD_REQUEST,
                "text/plain",
                "Recording ID required"
            )

            val file = File(settingsManager.getDefaultRecordingDirectory(), id)
            return if (file.exists() && file.isFile) {
                newFixedLengthResponse(
                    Status.OK,
                    "video/mp4",
                    file.inputStream(),
                    file.length()
                )
            } else {
                newFixedLengthResponse(
                    Status.NOT_FOUND,
                    "text/plain",
                    "Recording not found"
                )
            }
        }

        fun updateFrame(jpegData: ByteArray) {
            currentFrame = jpegData
            lastFrameTimestamp = System.currentTimeMillis()
            notifyMotionDetection()
        }

        private fun notifyMotionDetection() {
            if (settingsManager.cameraSettings.enhancement.motionDetection) {
                val message = """{"type":"motion","timestamp":${System.currentTimeMillis()}}"""
                synchronized(activeClients) {
                    activeClients.forEach { client ->
                        try {
                            client.send(message)
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        }

        private fun getDeviceInfo(): NanoHTTPD.Response {
                val info = """
                    {
                        "device": "${android.os.Build.MODEL}",
                        "manufacturer": "${android.os.Build.MANUFACTURER}",
                        "android_version": "${android.os.Build.VERSION.RELEASE}",
                        "sdk_level": ${android.os.Build.VERSION.SDK_INT},
                        "server_port": $port
                    }
                """.trimIndent()
                return newFixedLengthResponse(Status.OK, "application/json", info)
        }

        override fun openWebSocket(iHTTPSession: NanoHTTPD.IHTTPSession?): NanoWSD.WebSocket {
                return object : WebSocket(iHTTPSession) {
                        override fun onOpen() {
                            synchronized(activeClients) {
                                activeClients.add(this)
                            }
                        }

                        override fun onClose(code: WebSocketFrame.CloseCode, reason: String, initiatedByRemote: Boolean) {
                            synchronized(activeClients) {
                                activeClients.remove(this)
                            }
                        }

                        override fun onMessage(message: WebSocketFrame?) {
                            message?.textPayload?.let { payload ->
                                try {
                                    val json = JSONObject(payload)
                                    when (json.optString("type")) {
                                        "settings" -> {
                                            val settings = settingsManager.cameraSettings
                                            send(gson.toJson(settings))
                                        }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }

                        override fun onPong(message: WebSocketFrame?) {}

                        override fun onException(e: Exception?) {
                            e?.printStackTrace()
                            synchronized(activeClients) {
                                activeClients.remove(this)
                            }
                        }
                }
        }

        companion object {
                private const val indexHtml = """
                        <!doctype html>
                        <html>
                        <head>
                            <meta charset="utf-8">
                            <meta name="viewport" content="width=device-width,initial-scale=1">
                            <title>SecurityCam Control Panel</title>
                            <link href="https://fonts.googleapis.com/icon?family=Material+Icons" rel="stylesheet">
                            <style>
                                :root {
                                    --primary: #2196F3;
                                    --secondary: #FF4081;
                                    --success: #4CAF50;
                                    --danger: #F44336;
                                    --warning: #FFC107;
                                    --dark: #263238;
                                    --light: #ECEFF1;
                                }
                                body {
                                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                                    margin: 0;
                                    padding: 0;
                                    background: var(--light);
                                    color: var(--dark);
                                }
                                .navbar {
                                    background: var(--dark);
                                    color: white;
                                    padding: 1rem;
                                    display: flex;
                                    justify-content: space-between;
                                    align-items: center;
                                    position: fixed;
                                    width: 100%;
                                    top: 0;
                                    z-index: 1000;
                                }
                                .content {
                                    margin-top: 4rem;
                                    padding: 1rem;
                                    display: grid;
                                    grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
                                    gap: 1rem;
                                }
                                .card {
                                    background: white;
                                    border-radius: 8px;
                                    padding: 1rem;
                                    box-shadow: 0 2px 4px rgba(0,0,0,0.1);
                                }
                                .stream-container {
                                    grid-column: 1 / -1;
                                    aspect-ratio: 16/9;
                                    background: black;
                                    position: relative;
                                }
                                .stream {
                                    width: 100%;
                                    height: 100%;
                                    object-fit: contain;
                                }
                                .controls {
                                    position: absolute;
                                    bottom: 1rem;
                                    left: 50%;
                                    transform: translateX(-50%);
                                    display: flex;
                                    gap: 0.5rem;
                                    background: rgba(0,0,0,0.5);
                                    padding: 0.5rem;
                                    border-radius: 2rem;
                                }
                                .btn {
                                    padding: 0.5rem 1rem;
                                    border: none;
                                    border-radius: 2rem;
                                    background: var(--primary);
                                    color: white;
                                    cursor: pointer;
                                    display: flex;
                                    align-items: center;
                                    gap: 0.5rem;
                                    transition: 0.3s;
                                }
                                .btn:hover {
                                    opacity: 0.9;
                                }
                                .btn-icon {
                                    padding: 0.5rem;
                                    border-radius: 50%;
                                }
                                .slider {
                                    width: 100%;
                                    margin: 1rem 0;
                                }
                                .settings-group {
                                    margin: 1rem 0;
                                }
                                .settings-group h3 {
                                    margin: 0 0 0.5rem 0;
                                    color: var(--primary);
                                }
                                .switch {
                                    position: relative;
                                    display: inline-block;
                                    width: 60px;
                                    height: 34px;
                                }
                                .switch input {
                                    opacity: 0;
                                    width: 0;
                                    height: 0;
                                }
                                .slider-toggle {
                                    position: absolute;
                                    cursor: pointer;
                                    top: 0;
                                    left: 0;
                                    right: 0;
                                    bottom: 0;
                                    background-color: #ccc;
                                    transition: .4s;
                                    border-radius: 34px;
                                }
                                .slider-toggle:before {
                                    position: absolute;
                                    content: "";
                                    height: 26px;
                                    width: 26px;
                                    left: 4px;
                                    bottom: 4px;
                                    background-color: white;
                                    transition: .4s;
                                    border-radius: 50%;
                                }
                                input:checked + .slider-toggle {
                                    background-color: var(--primary);
                                }
                                input:checked + .slider-toggle:before {
                                    transform: translateX(26px);
                                }
                                .quality-selector {
                                    display: flex;
                                    gap: 0.5rem;
                                }
                                .quality-btn {
                                    flex: 1;
                                    padding: 0.5rem;
                                    border: 1px solid var(--primary);
                                    background: transparent;
                                    color: var(--primary);
                                    border-radius: 4px;
                                    cursor: pointer;
                                }
                                .quality-btn.active {
                                    background: var(--primary);
                                    color: white;
                                }
                                .recordings-list {
                                    max-height: 300px;
                                    overflow-y: auto;
                                }
                                .recording-item {
                                    display: flex;
                                    justify-content: space-between;
                                    align-items: center;
                                    padding: 0.5rem;
                                    border-bottom: 1px solid #eee;
                                }
                                .recording-item:hover {
                                    background: #f5f5f5;
                                }
                            </style>
                        </head>
                        <body>
                            <nav class="navbar">
                                <h1>SecurityCam Control Panel</h1>
                                <button class="btn" onclick="toggleSettings()">
                                    <span class="material-icons">settings</span>
                                </button>
                            </nav>
                            
                            <main class="content">
                                <div class="stream-container card">
                                    <img id="stream" class="stream" src="frame.jpg" alt="Camera Stream">
                                    <div class="controls">
                                        <button class="btn btn-icon" onclick="toggleCamera()">
                                            <span class="material-icons">flip_camera_ios</span>
                                        </button>
                                        <button class="btn btn-icon" onclick="toggleRecording()">
                                            <span class="material-icons" id="recordIcon">fiber_manual_record</span>
                                        </button>
                                        <button class="btn btn-icon" onclick="toggleFlash()">
                                            <span class="material-icons">flash_on</span>
                                        </button>
                                    </div>
                                </div>

                                <div class="card">
                                    <div class="settings-group">
                                        <h3>Stream Quality</h3>
                                        <div class="quality-selector">
                                            <button class="quality-btn" onclick="setQuality('low')">Low</button>
                                            <button class="quality-btn active" onclick="setQuality('medium')">Medium</button>
                                            <button class="quality-btn" onclick="setQuality('high')">High</button>
                                        </div>
                                    </div>

                                    <div class="settings-group">
                                        <h3>Night Mode</h3>
                                        <label class="switch">
                                            <input type="checkbox" id="nightMode" onchange="toggleNightMode()">
                                            <span class="slider-toggle"></span>
                                        </label>
                                        <div id="nightModeSettings" style="display: none">
                                            <label>Gain</label>
                                            <input type="range" class="slider" min="1" max="4" step="0.1" value="1.5" 
                                                   oninput="updateNightModeGain(this.value)">
                                        </div>
                                    </div>

                                    <div class="settings-group">
                                        <h3>Motion Detection</h3>
                                        <label class="switch">
                                            <input type="checkbox" id="motionDetection" onchange="toggleMotionDetection()">
                                            <span class="slider-toggle"></span>
                                        </label>
                                        <div id="motionSettings" style="display: none">
                                            <label>Sensitivity</label>
                                            <input type="range" class="slider" min="0" max="1" step="0.1" value="0.5"
                                                   oninput="updateMotionSensitivity(this.value)">
                                        </div>
                                    </div>
                                </div>

                                <div class="card">
                                    <div class="settings-group">
                                        <h3>Recording Settings</h3>
                                        <div>
                                            <label>Storage Location</label>
                                            <select id="storageLocation" onchange="updateStorageLocation(this.value)">
                                                <option value="default">Default Location</option>
                                                <option value="external">External Storage</option>
                                            </select>
                                        </div>
                                        <div>
                                            <label>Recording Interval (minutes)</label>
                                            <input type="number" min="1" max="60" value="5" 
                                                   onchange="updateRecordingInterval(this.value)">
                                        </div>
                                        <div>
                                            <label>Retention Period (days)</label>
                                            <input type="number" min="1" max="90" value="7"
                                                   onchange="updateRetentionPeriod(this.value)">
                                        </div>
                                    </div>
                                </div>

                                <div class="card">
                                    <div class="settings-group">
                                        <h3>Recordings</h3>
                                        <div class="recordings-list" id="recordingsList">
                                            <!-- Recordings will be populated here -->
                                        </div>
                                    </div>
                                </div>
                            </main>

                            <script>
                                let settings = {
                                    quality: 'medium',
                                    nightMode: false,
                                    nightModeGain: 1.5,
                                    motionDetection: false,
                                    motionSensitivity: 0.5,
                                    recording: false,
                                    storageLocation: 'default',
                                    recordingInterval: 5,
                                    retentionDays: 7
                                };

                                // Initialize settings from server
                                fetch('/settings').then(r => r.json()).then(s => {
                                    settings = {...settings, ...s};
                                    updateUI();
                                });

                                function updateUI() {
                                    document.getElementById('nightMode').checked = settings.nightMode;
                                    document.getElementById('motionDetection').checked = settings.motionDetection;
                                    // Update other UI elements...
                                }

                                function toggleNightMode() {
                                    settings.nightMode = !settings.nightMode;
                                    fetch('/settings', {
                                        method: 'POST',
                                        headers: {'Content-Type': 'application/json'},
                                        body: JSON.stringify({nightMode: settings.nightMode})
                                    });
                                    document.getElementById('nightModeSettings').style.display = 
                                        settings.nightMode ? 'block' : 'none';
                                }

                                function setQuality(quality) {
                                    settings.quality = quality;
                                    fetch('/settings', {
                                        method: 'POST',
                                        headers: {'Content-Type': 'application/json'},
                                        body: JSON.stringify({quality})
                                    });
                                    document.querySelectorAll('.quality-btn').forEach(btn => {
                                        btn.classList.toggle('active', btn.textContent.toLowerCase() === quality);
                                    });
                                }

                                // Update stream image every 200ms
                                setInterval(() => {
                                    const img = document.getElementById('stream');
                                    if(img) {
                                        img.src = 'frame.jpg?ts=' + Date.now();
                                    }
                                }, 200);

                                // WebSocket connection for real-time updates
                                const ws = new WebSocket('ws://' + location.host + '/');
                                ws.onmessage = (evt) => {
                                    const data = JSON.parse(evt.data);
                                    if(data.type === 'motion') {
                                        // Handle motion detection event
                                        console.log('Motion detected!');
                                    }
                                };

                                // Additional functions for other controls...
                                function toggleCamera() {
                                    fetch('/toggleCamera', {method: 'POST'});
                                }

                                function toggleRecording() {
                                    settings.recording = !settings.recording;
                                    fetch('/toggleRecording', {
                                        method: 'POST',
                                        headers: {'Content-Type': 'application/json'},
                                        body: JSON.stringify({recording: settings.recording})
                                    });
                                    document.getElementById('recordIcon').style.color = 
                                        settings.recording ? 'var(--danger)' : 'white';
                                }

                                function toggleFlash() {
                                    fetch('/toggleFlash', {method: 'POST'});
                                }

                                function loadRecordings() {
                                    fetch('/recordings').then(r => r.json()).then(recordings => {
                                        const html = recordings.map(rec => `
                                            <div class="recording-item">
                                                <span>${rec.date}</span>
                                                <span>${rec.size}</span>
                                                <button class="btn btn-icon" onclick="downloadRecording('${rec.id}')">
                                                    <span class="material-icons">download</span>
                                                </button>
                                            </div>
                                        `).join('');
                                        document.getElementById('recordingsList').innerHTML = html;
                                    });
                                }

                                // Load recordings initially and every minute
                                loadRecordings();
                                setInterval(loadRecordings, 60000);
                            </script>
                        </body>
                        </html>
                """
        }
}

