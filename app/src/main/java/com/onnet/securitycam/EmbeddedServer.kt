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
    private var currentFrame: ByteArray? = null
    private var lastFrameTimestamp: Long = 0

    override fun serveHttp(session: NanoHTTPD.IHTTPSession?): NanoHTTPD.Response {
        val uri = session?.uri ?: "/"
        return when (uri) {
            "/" -> newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "text/html", indexHtml)
            "/settings" -> handleSettings(session)
            "/info" -> getDeviceInfo()
            "/frame.jpg" -> getFrame()
            "/toggleCamera" -> toggleCamera()
            "/toggleFlash" -> toggleFlash()
            "/toggleRecording" -> toggleRecording()
            "/recordings" -> getRecordings()
            "/download" -> downloadRecording(session)
            else -> newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND, "text/plain", "Not found")
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