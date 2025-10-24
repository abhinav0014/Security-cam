package com.onnet.securitycam

import android.content.Context
import com.google.gson.Gson
import com.onnet.securitycam.config.CameraSettings
import com.onnet.securitycam.config.SettingsManager
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
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
    private val mjpegStreamer = MjpegStreamer()
    private var currentFrame: ByteArray? = null
    private var lastFrameTimestamp: Long = 0

    override fun serveHttp(session: NanoHTTPD.IHTTPSession?): NanoHTTPD.Response {
        val uri = session?.uri ?: "/"
        
        // Handle MJPEG video stream
        if (uri == "/stream.mjpeg" || uri == "/video.mjpeg") {
            return handleMjpegStream(session)
        }
        
        // Add CORS headers to all responses
        val response = when {
            uri == "/" -> 
                newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "text/html", indexHtml)
            uri == "/settings" -> 
                handleSettings(session)
            uri == "/info" -> 
                getDeviceInfo()
            uri == "/frame.jpg" -> 
                getFrame()
            uri == "/toggleCamera" -> 
                toggleCamera()
            uri == "/toggleFlash" -> 
                toggleFlash()
            uri == "/toggleRecording" -> 
                toggleRecording()
            uri == "/recordings" && session?.method == NanoHTTPD.Method.GET -> 
                getRecordings()
            uri.startsWith("/recordings/") && session?.method == NanoHTTPD.Method.DELETE ->
                deleteRecording(uri.substringAfterLast("/"))
            uri == "/download" -> 
                downloadRecording(session)
            else -> 
                newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND, "text/plain", "Not found")
        }
        
        // Add CORS headers
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS")
        response.addHeader("Access-Control-Allow-Headers", "Content-Type")
        
        // Handle preflight requests
        if (session?.method == NanoHTTPD.Method.OPTIONS) {
            return response
        }
        
        return response
    }

    /**
     * Handle MJPEG streaming - returns a continuous stream of JPEG frames
     */
    private fun handleMjpegStream(session: NanoHTTPD.IHTTPSession?): NanoHTTPD.Response {
        return object : NanoHTTPD.Response(NanoHTTPD.Response.Status.OK, MjpegStreamer.Companion.CONTENT_TYPE, null as java.io.InputStream?) {
            override fun send(outputStream: java.io.OutputStream?) {
                if (outputStream == null) return
                
                try {
                    // Register client with MJPEG streamer
                    val clientId = mjpegStreamer.addClient(outputStream)
                    
                    // Keep connection alive - the MjpegStreamer will handle frame sending
                    // This thread just monitors the connection
                    while (!outputStream.toString().contains("closed")) {
                        Thread.sleep(1000)
                    }
                } catch (e: Exception) {
                    // Connection closed or error occurred
                } finally {
                    try {
                        outputStream.close()
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }
        }.apply {
            addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
            addHeader("Pragma", "no-cache")
            addHeader("Expires", "0")
            addHeader("Connection", "close")
            addHeader("Access-Control-Allow-Origin", "*")
        }
    }

    private fun handleSettings(session: NanoHTTPD.IHTTPSession?): NanoHTTPD.Response {
        return when (session?.method) {
            NanoHTTPD.Method.GET -> {
                val settings = settingsManager.cameraSettings
                newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", gson.toJson(settings))
            }
            NanoHTTPD.Method.POST -> {
                val files = mutableMapOf<String, String>()
                try {
                    session.parseBody(files)
                    val jsonBody = files["postData"]
                    val settings = gson.fromJson(jsonBody, CameraSettings::class.java)
                    settingsManager.saveSettings(settings)
                    newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", """{"status":"success"}""")
                } catch (e: Exception) {
                    newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, "application/json", """{"error":"${e.message}"}""")
                }
            }
            else -> newFixedLengthResponse(NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED, "text/plain", "Method not allowed")
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
            put("active_viewers", mjpegStreamer.getClientCount())
        }
        return newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", info.toString())
    }

    private fun getFrame(): NanoHTTPD.Response {
        val frame = currentFrame
        return if (frame != null && System.currentTimeMillis() - lastFrameTimestamp < 5000) {
            newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "image/jpeg", ByteArrayInputStream(frame), frame.size.toLong())
        } else {
            newFixedLengthResponse(NanoHTTPD.Response.Status.NO_CONTENT, "text/plain", "No recent frame available")
        }
    }

    private fun toggleCamera(): NanoHTTPD.Response {
        val settings = settingsManager.cameraSettings
        settings.useBackCamera = !settings.useBackCamera
        settingsManager.saveSettings(settings)
        return newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", """{"status":"success","useBackCamera":${settings.useBackCamera}}""")
    }

    private fun toggleFlash(): NanoHTTPD.Response {
        val settings = settingsManager.cameraSettings
        settings.flashEnabled = !settings.flashEnabled
        settingsManager.saveSettings(settings)
        return newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", """{"status":"success","flashEnabled":${settings.flashEnabled}}""")
    }

    private fun toggleRecording(): NanoHTTPD.Response {
        val settings = settingsManager.cameraSettings
        settings.recording = settings.recording.copy(enabled = !settings.recording.enabled)
        settingsManager.saveSettings(settings)
        return newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", """{"status":"success","recording":${settings.recording.enabled}}""")
    }

    private fun getRecordings(): NanoHTTPD.Response {
        val recordingsDir = settingsManager.getDefaultRecordingDirectory()
        val recordings = recordingsDir.listFiles()
            ?.filter { it.isFile && it.extension == "mp4" }
            ?.sortedByDescending { it.lastModified() }
            ?.map { file ->
                JSONObject().apply {
                    put("id", file.name)
                    put("name", file.name)
                    put("date", file.lastModified())
                    put("size", file.length())
                }
            } ?: emptyList()
        
        return newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK, 
            "application/json", 
            JSONObject().put("recordings", recordings).toString()
        )
    }

    private fun downloadRecording(session: NanoHTTPD.IHTTPSession?): NanoHTTPD.Response {
        val id = session?.parameters?.get("id")?.firstOrNull() 
            ?: return newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, "text/plain", "Recording ID required")

        val file = File(settingsManager.getDefaultRecordingDirectory(), id)
        return if (file.exists() && file.isFile) {
            newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK,
                "video/mp4",
                file.inputStream(),
                file.length()
            )
        } else {
            newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND, "text/plain", "Recording not found")
        }
    }

    private fun deleteRecording(id: String): NanoHTTPD.Response {
        val file = File(settingsManager.getDefaultRecordingDirectory(), id)
        return if (file.exists() && file.isFile) {
            if (file.delete()) {
                newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", """{"status":"success"}""")
            } else {
                newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, "application/json", """{"error":"Failed to delete file"}""")
            }
        } else {
            newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND, "application/json", """{"error":"Recording not found"}""")
        }
    }

    fun updateFrame(jpegData: ByteArray) {
        currentFrame = jpegData
        lastFrameTimestamp = System.currentTimeMillis()
        
        // Update MJPEG stream
        mjpegStreamer.updateFrame(jpegData)
        
        // Notify motion detection
        notifyMotionDetection()
    }

    private fun notifyMotionDetection() {
        if (settingsManager.cameraSettings.enhancement.motionDetection) {
            val message = """{"type":"motion","timestamp":${System.currentTimeMillis()}}"""
            broadcastMessage(message)
        }
    }

    private fun broadcastMessage(message: String) {
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

    private fun broadcastStatus() {
        val status = JSONObject().apply {
            put("type", "status")
            put("clients", activeClients.size)
            put("viewers", mjpegStreamer.getClientCount())
            put("recording", settingsManager.cameraSettings.recording.enabled)
            put("storage", getStorageStatus())
        }
        broadcastMessage(status.toString())
    }

    private fun getStorageStatus(): JSONObject {
        val total = settingsManager.cameraSettings.recording.maxStorageSize
        val used = settingsManager.getStorageUsage()
        return JSONObject().apply {
            put("used", used)
            put("total", total)
            put("percent", if (total > 0) (used.toFloat() / total * 100).toInt() else 0)
        }
    }

    override fun openWebSocket(iHTTPSession: NanoHTTPD.IHTTPSession?): NanoWSD.WebSocket {
        return object : WebSocket(iHTTPSession) {
            override fun onOpen() {
                synchronized(activeClients) {
                    activeClients.add(this)
                    broadcastStatus()
                }
            }

            override fun onClose(code: WebSocketFrame.CloseCode, reason: String, initiatedByRemote: Boolean) {
                synchronized(activeClients) {
                    activeClients.remove(this)
                    broadcastStatus()
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
                            "ping" -> {
                                send("""{"type":"pong","timestamp":${System.currentTimeMillis()}}""")
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            override fun onPong(message: WebSocketFrame?) {}

            override fun onException(exception: IOException) {
                exception.printStackTrace()
                synchronized(activeClients) {
                    activeClients.remove(this)
                }
            }
        }
    }

    override fun stop() {
        mjpegStreamer.closeAll()
        super.stop()
    }

    companion object {
        private const val indexHtml = """
<!doctype html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <title>SecurityCam Live Stream</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { 
            margin: 0; 
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Arial, sans-serif; 
            background: #0a0e14; 
            color: #e6f0f8; 
        }
        .container { 
            max-width: 1400px; 
            margin: 0 auto; 
            padding: 20px; 
        }
        h1 { 
            text-align: center; 
            margin-bottom: 10px;
            font-size: 2em;
            color: #90caf9;
        }
        .status-bar {
            display: flex;
            justify-content: center;
            align-items: center;
            gap: 20px;
            margin-bottom: 20px;
            padding: 10px;
            background: rgba(144, 202, 249, 0.1);
            border-radius: 8px;
        }
        .status-indicator {
            display: flex;
            align-items: center;
            gap: 8px;
        }
        .status-dot {
            width: 12px;
            height: 12px;
            border-radius: 50%;
            background: #4caf50;
            animation: pulse 2s infinite;
        }
        @keyframes pulse {
            0%, 100% { opacity: 1; }
            50% { opacity: 0.5; }
        }
        .stream-container {
            position: relative;
            width: 100%;
            max-width: 1200px;
            margin: 0 auto 20px;
            background: #000;
            border-radius: 12px;
            overflow: hidden;
            box-shadow: 0 8px 32px rgba(0, 0, 0, 0.4);
        }
        .stream { 
            width: 100%; 
            height: auto;
            display: block;
        }
        .stream-overlay {
            position: absolute;
            top: 10px;
            right: 10px;
            background: rgba(0, 0, 0, 0.7);
            padding: 8px 12px;
            border-radius: 6px;
            font-size: 0.9em;
            color: #4caf50;
        }
        .controls { 
            text-align: center; 
            margin: 20px 0;
            display: flex;
            justify-content: center;
            flex-wrap: wrap;
            gap: 10px;
        }
        button { 
            padding: 12px 24px; 
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); 
            color: #fff; 
            border: none; 
            border-radius: 8px; 
            cursor: pointer;
            font-size: 1em;
            font-weight: 600;
            transition: all 0.3s ease;
            box-shadow: 0 4px 15px rgba(102, 126, 234, 0.4);
        }
        button:hover { 
            transform: translateY(-2px);
            box-shadow: 0 6px 20px rgba(102, 126, 234, 0.6);
        }
        button:active {
            transform: translateY(0);
        }
        .info-panel {
            background: rgba(144, 202, 249, 0.05);
            border: 1px solid rgba(144, 202, 249, 0.2);
            border-radius: 8px;
            padding: 20px;
            margin-top: 20px;
        }
        .info-panel h3 {
            color: #90caf9;
            margin-bottom: 10px;
        }
        .info-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 15px;
            margin-top: 15px;
        }
        .info-item {
            background: rgba(0, 0, 0, 0.3);
            padding: 12px;
            border-radius: 6px;
        }
        .info-label {
            color: #64b5f6;
            font-size: 0.85em;
            margin-bottom: 5px;
        }
        .info-value {
            font-size: 1.1em;
            font-weight: 600;
        }
    </style>
</head>
<body>
    <div class="container">
        <h1>🎥 SecurityCam Live Stream</h1>
        
        <div class="status-bar">
            <div class="status-indicator">
                <div class="status-dot"></div>
                <span>Live</span>
            </div>
            <div id="viewerCount">Viewers: 0</div>
        </div>

        <div class="stream-container">
            <img id="stream" class="stream" src="/stream.mjpeg" alt="Camera Stream">
            <div class="stream-overlay">LIVE</div>
        </div>

        <div class="controls">
            <button onclick="toggleCamera()">🔄 Switch Camera</button>
            <button onclick="toggleFlash()">💡 Toggle Flash</button>
            <button onclick="toggleRecording()">⏺️ Record</button>
            <button onclick="refreshStream()">🔄 Refresh</button>
        </div>

        <div class="info-panel">
            <h3>Device Information</h3>
            <div class="info-grid" id="deviceInfo">
                <div class="info-item">
                    <div class="info-label">Loading...</div>
                    <div class="info-value">Please wait</div>
                </div>
            </div>
        </div>
    </div>
    <script>
        function toggleCamera() { 
            fetch('/toggleCamera', {method: 'POST'})
                .then(() => setTimeout(refreshStream, 500)); 
        }
        function toggleFlash() { 
            fetch('/toggleFlash', {method: 'POST'}); 
        }
        function toggleRecording() { 
            fetch('/toggleRecording', {method: 'POST'}); 
        }
        function refreshStream() {
            const img = document.getElementById('stream');
            img.src = '/stream.mjpeg?t=' + Date.now();
        }
        
        // Load device info
        function loadDeviceInfo() {
            fetch('/info')
                .then(r => r.json())
                .then(data => {
                    const grid = document.getElementById('deviceInfo');
                    grid.innerHTML = `
                        <div class="info-item">
                            <div class="info-label">Device</div>
                            <div class="info-value">${data.device}</div>
                        </div>
                        <div class="info-item">
                            <div class="info-label">Manufacturer</div>
                            <div class="info-value">${data.manufacturer}</div>
                        </div>
                        <div class="info-item">
                            <div class="info-label">Android Version</div>
                            <div class="info-value">${data.android_version}</div>
                        </div>
                        <div class="info-item">
                                      getFrame()
            uri == "/toggleCamera" -> 
                toggleCamera()
            uri == "/toggleFlash" -> 
                toggleFlash()
            uri == "/toggleRecording" -> 
                toggleRecording()
            uri == "/recordings" && session?.method == NanoHTTPD.Method.GET -> 
                getRecordings()
            uri.startsWith("/recordings/") && session?.method == NanoHTTPD.Method.DELETE ->
                deleteRecording(uri.substringAfterLast("/"))
            uri == "/download" -> 
                downloadRecording(session)
            else -> 
                newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND, "text/plain", "Not found")
        }
        
        // Add CORS headers
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS")
        response.addHeader("Access-Control-Allow-Headers", "Content-Type")
        
        // Handle preflight requests
        if (session?.method == NanoHTTPD.Method.OPTIONS) {
            return response
        }
        
        return response
    }

    private fun handleSettings(session: NanoHTTPD.IHTTPSession?): NanoHTTPD.Response {
        return when (session?.method) {
            NanoHTTPD.Method.GET -> {
                val settings = settingsManager.cameraSettings
                newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", gson.toJson(settings))
            }
            NanoHTTPD.Method.POST -> {
                val files = mutableMapOf<String, String>()
                try {
                    session.parseBody(files)
                    val jsonBody = files["postData"]
                    val settings = gson.fromJson(jsonBody, CameraSettings::class.java)
                    settingsManager.saveSettings(settings)
                    newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", """{"status":"success"}""")
                } catch (e: Exception) {
                    newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, "application/json", """{"error":"${e.message}"}""")
                }
            }
            else -> newFixedLengthResponse(NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED, "text/plain", "Method not allowed")
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
        return newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", info.toString())
    }

    private fun getFrame(): NanoHTTPD.Response {
        val frame = currentFrame
        return if (frame != null && System.currentTimeMillis() - lastFrameTimestamp < 5000) {
            newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "image/jpeg", ByteArrayInputStream(frame), frame.size.toLong())
        } else {
            newFixedLengthResponse(NanoHTTPD.Response.Status.NO_CONTENT, "text/plain", "No recent frame available")
        }
    }

    private fun toggleCamera(): NanoHTTPD.Response {
        val settings = settingsManager.cameraSettings
        settings.useBackCamera = !settings.useBackCamera
        settingsManager.saveSettings(settings)
        return newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", """{"status":"success","useBackCamera":${settings.useBackCamera}}""")
    }

    private fun toggleFlash(): NanoHTTPD.Response {
        val settings = settingsManager.cameraSettings
        settings.flashEnabled = !settings.flashEnabled
        settingsManager.saveSettings(settings)
        return newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", """{"status":"success","flashEnabled":${settings.flashEnabled}}""")
    }

    private fun toggleRecording(): NanoHTTPD.Response {
        val settings = settingsManager.cameraSettings
        settings.recording = settings.recording.copy(enabled = !settings.recording.enabled)
        settingsManager.saveSettings(settings)
        return newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", """{"status":"success","recording":${settings.recording.enabled}}""")
    }

    private fun getRecordings(): NanoHTTPD.Response {
        val recordingsDir = settingsManager.getDefaultRecordingDirectory()
        val recordings = recordingsDir.listFiles()
            ?.filter { it.isFile && it.extension == "mp4" }
            ?.sortedByDescending { it.lastModified() }
            ?.map { file ->
                JSONObject().apply {
                    put("id", file.name)
                    put("name", file.name)
                    put("date", file.lastModified())
                    put("size", file.length())
                }
            } ?: emptyList()
        
        return newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK, 
            "application/json", 
            JSONObject().put("recordings", recordings).toString()
        )
    }

    private fun downloadRecording(session: NanoHTTPD.IHTTPSession?): NanoHTTPD.Response {
        val id = session?.parameters?.get("id")?.firstOrNull() 
            ?: return newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, "text/plain", "Recording ID required")

        val file = File(settingsManager.getDefaultRecordingDirectory(), id)
        return if (file.exists() && file.isFile) {
            newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK,
                "video/mp4",
                file.inputStream(),
                file.length()
            )
        } else {
            newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND, "text/plain", "Recording not found")
        }
    }

    private fun deleteRecording(id: String): NanoHTTPD.Response {
        val file = File(settingsManager.getDefaultRecordingDirectory(), id)
        return if (file.exists() && file.isFile) {
            if (file.delete()) {
                newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", """{"status":"success"}""")
            } else {
                newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, "application/json", """{"error":"Failed to delete file"}""")
            }
        } else {
            newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND, "application/json", """{"error":"Recording not found"}""")
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
            broadcastMessage(message)
        }
    }

    private fun broadcastMessage(message: String) {
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

    private fun broadcastStatus() {
        val status = JSONObject().apply {
            put("type", "status")
            put("clients", activeClients.size)
            put("recording", settingsManager.cameraSettings.recording.enabled)
            put("storage", getStorageStatus())
        }
        broadcastMessage(status.toString())
    }

    private fun getStorageStatus(): JSONObject {
        val total = settingsManager.cameraSettings.recording.maxStorageSize
        val used = settingsManager.getStorageUsage()
        return JSONObject().apply {
            put("used", used)
            put("total", total)
            put("percent", if (total > 0) (used.toFloat() / total * 100).toInt() else 0)
        }
    }

    override fun openWebSocket(iHTTPSession: NanoHTTPD.IHTTPSession?): NanoWSD.WebSocket {
        return object : WebSocket(iHTTPSession) {
            override fun onOpen() {
                synchronized(activeClients) {
                    activeClients.add(this)
                    broadcastStatus()
                }
            }

            override fun onClose(code: WebSocketFrame.CloseCode, reason: String, initiatedByRemote: Boolean) {
                synchronized(activeClients) {
                    activeClients.remove(this)
                    broadcastStatus()
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
                            "ping" -> {
                                send("""{"type":"pong","timestamp":${System.currentTimeMillis()}}""")
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            override fun onPong(message: WebSocketFrame?) {}

            override fun onException(exception: IOException) {
                exception.printStackTrace()
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
    <title>SecurityCam</title>
    <style>
        body { margin: 0; font-family: Arial, sans-serif; background: #1a1a1a; color: #fff; }
        .container { max-width: 1200px; margin: 0 auto; padding: 20px; }
        h1 { text-align: center; }
        .stream { width: 100%; max-width: 800px; display: block; margin: 20px auto; border-radius: 8px; }
        .controls { text-align: center; margin: 20px 0; }
        button { padding: 10px 20px; margin: 5px; background: #007bff; color: #fff; border: none; border-radius: 5px; cursor: pointer; }
        button:hover { background: #0056b3; }
    </style>
</head>
<body>
    <div class="container">
        <h1>SecurityCam Stream</h1>
        <img id="stream" class="stream" src="/frame.jpg" alt="Camera Stream">
        <div class="controls">
            <button onclick="toggleCamera()">Switch Camera</button>
            <button onclick="toggleFlash()">Toggle Flash</button>
            <button onclick="toggleRecording()">Record</button>
        </div>
    </div>
    <script>
        setInterval(() => {
            document.getElementById('stream').src = '/frame.jpg?t=' + Date.now();
        }, 200);
        
        function toggleCamera() { fetch('/toggleCamera', {method: 'POST'}); }
        function toggleFlash() { fetch('/toggleFlash', {method: 'POST'}); }
        function toggleRecording() { fetch('/toggleRecording', {method: 'POST'}); }
    </script>
</body>
</html>
        """
    }
}
