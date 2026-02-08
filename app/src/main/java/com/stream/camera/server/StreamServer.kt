package com.stream.camera.server

import android.content.Context
import android.util.Log
import com.stream.camera.MainActivity
import com.stream.camera.encoder.HLSEncoder
import com.stream.camera.camera.CameraManager
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Duration

/**
 * HLS Streaming Server
 * Handles HTTP requests and serves HLS stream segments
 */
class StreamServer(private val context: Context) {
    
    private var server: NettyApplicationEngine? = null
    private var cameraManager: CameraManager? = null
    private var hlsEncoder: HLSEncoder? = null
    private val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private val streamDir = File(context.cacheDir, "hls_stream").apply {
        if (!exists()) mkdirs()
    }
    
    companion object {
        private const val TAG = "StreamServer"
        private const val PORT = 8080
    }
    
    fun start() {
        try {
            // Initialize camera and encoder
            val lifecycleOwner = MainActivity.getInstance()
                ?: throw IllegalStateException("MainActivity must be available for camera streaming")
            
            cameraManager = CameraManager(context, lifecycleOwner)
            hlsEncoder = HLSEncoder(streamDir)
            
            // Start camera capture
            serverScope.launch(Dispatchers.Main) {
                cameraManager?.startCamera { videoData ->
                    hlsEncoder?.encodeFrame(videoData)
                }
            }
            
            // Start HTTP server
            server = embeddedServer(Netty, port = PORT) {
                install(ContentNegotiation) {
                    json(Json {
                        prettyPrint = true
                        isLenient = true
                    })
                }
                
                install(CORS) {
                    anyHost()
                    allowHeader(HttpHeaders.ContentType)
                    allowMethod(HttpMethod.Get)
                    allowMethod(HttpMethod.Post)
                    allowMethod(HttpMethod.Options)
                }
                
                install(WebSockets) {
                    pingPeriod = Duration.ofSeconds(15)
                    timeout = Duration.ofSeconds(15)
                    maxFrameSize = Long.MAX_VALUE
                    masking = false
                }
                
                routing {
                    // Serve HLS playlist
                    get("/stream.m3u8") {
                        val playlistFile = File(streamDir, "playlist.m3u8")
                        if (playlistFile.exists()) {
                            call.respondFile(playlistFile)
                        } else {
                            call.respondText(
                                "Playlist not ready",
                                status = HttpStatusCode.NotFound
                            )
                        }
                    }
                    
                    // Serve HLS segments
                    get("/segments/{filename}") {
                        val filename = call.parameters["filename"] ?: return@get call.respond(
                            HttpStatusCode.BadRequest
                        )
                        
                        val segmentFile = File(streamDir, filename)
                        if (segmentFile.exists()) {
                            call.respondFile(segmentFile)
                        } else {
                            call.respond(HttpStatusCode.NotFound)
                        }
                    }
                    
                    // Stream status API
                    get("/api/status") {
                        call.respond(
                            StreamStatus(
                                isStreaming = cameraManager?.isStreaming() == true,
                                currentCamera = cameraManager?.getCurrentCamera() ?: "back",
                                resolution = cameraManager?.getCurrentResolution() ?: "1920x1080",
                                bitrate = hlsEncoder?.getCurrentBitrate() ?: 2000,
                                fps = hlsEncoder?.getCurrentFps() ?: 30,
                                segmentCount = streamDir.listFiles()?.count { it.extension == "ts" } ?: 0
                            )
                        )
                    }
                    
                    // Switch camera
                    post("/api/camera/switch") {
                        try {
                            cameraManager?.switchCamera()
                            call.respond(mapOf("success" to true))
                        } catch (e: Exception) {
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                mapOf("error" to e.message)
                            )
                        }
                    }
                    
                    // Change quality
                    post("/api/quality/{level}") {
                        val level = call.parameters["level"] ?: return@post call.respond(
                            HttpStatusCode.BadRequest
                        )
                        
                        try {
                            val (width, height, bitrate) = when (level.lowercase()) {
                                "low" -> Triple(640, 480, 1000)
                                "medium" -> Triple(1280, 720, 2000)
                                "high" -> Triple(1920, 1080, 4000)
                                else -> return@post call.respond(
                                    HttpStatusCode.BadRequest,
                                    mapOf("error" to "Invalid quality level")
                                )
                            }
                            
                            cameraManager?.setResolution(width, height)
                            hlsEncoder?.setBitrate(bitrate)
                            
                            call.respond(mapOf("success" to true, "quality" to level))
                        } catch (e: Exception) {
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                mapOf("error" to e.message)
                            )
                        }
                    }
                    
                    // Web control panel
                    get("/control") {
                        call.respondText(getControlPanelHtml(), ContentType.Text.Html)
                    }
                    
                    // Root redirect
                    get("/") {
                        call.respondText(getHomepageHtml(), ContentType.Text.Html)
                    }
                }
            }.start(wait = false)
            
            Log.d(TAG, "Stream server started on port $PORT")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start server", e)
            throw e
        }
    }
    
    fun stop() {
        try {
            server?.stop(1000, 2000)
            cameraManager?.stopCamera()
            hlsEncoder?.stop()
            
            // Clean up stream files
            streamDir.listFiles()?.forEach { it.delete() }
            
            Log.d(TAG, "Stream server stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping server", e)
        }
    }
    
    private fun getHomepageHtml(): String = """
        <!DOCTYPE html>
        <html>
        <head>
            <title>Camera Stream - ABSTER</title>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
                * { margin: 0; padding: 0; box-sizing: border-box; }
                body {
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                    min-height: 100vh;
                    display: flex;
                    justify-content: center;
                    align-items: center;
                    padding: 20px;
                }
                .container {
                    background: white;
                    border-radius: 20px;
                    padding: 40px;
                    max-width: 1200px;
                    width: 100%;
                    box-shadow: 0 20px 60px rgba(0,0,0,0.3);
                }
                h1 {
                    color: #333;
                    text-align: center;
                    margin-bottom: 10px;
                    font-size: 2.5em;
                }
                .subtitle {
                    text-align: center;
                    color: #666;
                    margin-bottom: 40px;
                    font-size: 1.1em;
                }
                .video-container {
                    position: relative;
                    width: 100%;
                    padding-bottom: 56.25%;
                    background: #000;
                    border-radius: 10px;
                    overflow: hidden;
                    margin-bottom: 30px;
                }
                video {
                    position: absolute;
                    top: 0;
                    left: 0;
                    width: 100%;
                    height: 100%;
                }
                .controls {
                    display: flex;
                    gap: 15px;
                    justify-content: center;
                    flex-wrap: wrap;
                }
                .btn {
                    padding: 15px 30px;
                    border: none;
                    border-radius: 10px;
                    font-size: 16px;
                    font-weight: 600;
                    cursor: pointer;
                    transition: all 0.3s;
                    text-decoration: none;
                    display: inline-block;
                }
                .btn-primary {
                    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                    color: white;
                }
                .btn-primary:hover {
                    transform: translateY(-2px);
                    box-shadow: 0 5px 20px rgba(102, 126, 234, 0.4);
                }
                .status {
                    text-align: center;
                    margin-top: 20px;
                    padding: 15px;
                    background: #f0f0f0;
                    border-radius: 10px;
                }
                .status-indicator {
                    display: inline-block;
                    width: 12px;
                    height: 12px;
                    border-radius: 50%;
                    background: #4CAF50;
                    margin-right: 8px;
                    animation: pulse 2s infinite;
                }
                @keyframes pulse {
                    0%, 100% { opacity: 1; }
                    50% { opacity: 0.5; }
                }
            </style>
            <script src="https://cdn.jsdelivr.net/npm/hls.js@latest"></script>
        </head>
        <body>
            <div class="container">
                <h1>📹 Camera Stream</h1>
                <p class="subtitle">Created by ABSTER</p>
                
                <div class="video-container">
                    <video id="video" controls autoplay muted></video>
                </div>
                
                <div class="controls">
                    <a href="/control" class="btn btn-primary">Open Control Panel</a>
                </div>
                
                <div class="status">
                    <span class="status-indicator"></span>
                    <span id="status">Initializing stream...</span>
                </div>
            </div>
            
            <script>
                const video = document.getElementById('video');
                const status = document.getElementById('status');
                
                if (Hls.isSupported()) {
                    const hls = new Hls({
                        enableWorker: true,
                        lowLatencyMode: true,
                        backBufferLength: 90
                    });
                    
                    hls.loadSource('/stream.m3u8');
                    hls.attachMedia(video);
                    
                    hls.on(Hls.Events.MANIFEST_PARSED, function() {
                        video.play();
                        status.textContent = 'Stream connected';
                    });
                    
                    hls.on(Hls.Events.ERROR, function(event, data) {
                        if (data.fatal) {
                            status.textContent = 'Stream error: ' + data.type;
                        }
                    });
                } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
                    video.src = '/stream.m3u8';
                    video.addEventListener('loadedmetadata', function() {
                        video.play();
                        status.textContent = 'Stream connected';
                    });
                }
            </script>
        </body>
        </html>
    """.trimIndent()
    
    private fun getControlPanelHtml(): String = """
        <!DOCTYPE html>
        <html>
        <head>
            <title>Stream Control Panel - ABSTER</title>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
                * { margin: 0; padding: 0; box-sizing: border-box; }
                body {
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                    background: linear-gradient(135deg, #1e3c72 0%, #2a5298 100%);
                    min-height: 100vh;
                    padding: 20px;
                }
                .dashboard {
                    max-width: 1400px;
                    margin: 0 auto;
                }
                h1 {
                    color: white;
                    margin-bottom: 10px;
                    font-size: 2.5em;
                }
                .subtitle {
                    color: rgba(255,255,255,0.8);
                    margin-bottom: 30px;
                    font-size: 1.1em;
                }
                .grid {
                    display: grid;
                    grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
                    gap: 20px;
                    margin-bottom: 20px;
                }
                .card {
                    background: white;
                    border-radius: 15px;
                    padding: 25px;
                    box-shadow: 0 10px 30px rgba(0,0,0,0.2);
                }
                .card h2 {
                    color: #333;
                    margin-bottom: 20px;
                    font-size: 1.5em;
                    display: flex;
                    align-items: center;
                    gap: 10px;
                }
                .stat {
                    display: flex;
                    justify-content: space-between;
                    margin-bottom: 15px;
                    padding-bottom: 15px;
                    border-bottom: 1px solid #eee;
                }
                .stat:last-child {
                    border-bottom: none;
                    margin-bottom: 0;
                    padding-bottom: 0;
                }
                .stat-label {
                    color: #666;
                    font-weight: 500;
                }
                .stat-value {
                    color: #333;
                    font-weight: 600;
                }
                .btn {
                    width: 100%;
                    padding: 15px;
                    border: none;
                    border-radius: 10px;
                    font-size: 16px;
                    font-weight: 600;
                    cursor: pointer;
                    transition: all 0.3s;
                    margin-bottom: 10px;
                }
                .btn-primary {
                    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                    color: white;
                }
                .btn-secondary {
                    background: #f0f0f0;
                    color: #333;
                }
                .btn:hover {
                    transform: translateY(-2px);
                    box-shadow: 0 5px 20px rgba(0,0,0,0.2);
                }
                .quality-btns {
                    display: flex;
                    gap: 10px;
                }
                .quality-btns .btn {
                    flex: 1;
                    margin: 0;
                }
                .alert {
                    padding: 15px;
                    border-radius: 10px;
                    margin-bottom: 20px;
                    display: none;
                }
                .alert-success {
                    background: #d4edda;
                    color: #155724;
                }
                .alert-error {
                    background: #f8d7da;
                    color: #721c24;
                }
            </style>
        </head>
        <body>
            <div class="dashboard">
                <h1>🎛️ Stream Control Panel</h1>
                <p class="subtitle">Created by ABSTER</p>
                
                <div id="alert" class="alert"></div>
                
                <div class="grid">
                    <div class="card">
                        <h2>📊 Stream Status</h2>
                        <div class="stat">
                            <span class="stat-label">Status</span>
                            <span class="stat-value" id="streaming-status">Loading...</span>
                        </div>
                        <div class="stat">
                            <span class="stat-label">Current Camera</span>
                            <span class="stat-value" id="camera">-</span>
                        </div>
                        <div class="stat">
                            <span class="stat-label">Resolution</span>
                            <span class="stat-value" id="resolution">-</span>
                        </div>
                        <div class="stat">
                            <span class="stat-label">Bitrate</span>
                            <span class="stat-value" id="bitrate">-</span>
                        </div>
                        <div class="stat">
                            <span class="stat-label">FPS</span>
                            <span class="stat-value" id="fps">-</span>
                        </div>
                        <div class="stat">
                            <span class="stat-label">Segments</span>
                            <span class="stat-value" id="segments">-</span>
                        </div>
                    </div>
                    
                    <div class="card">
                        <h2>📷 Camera Control</h2>
                        <button class="btn btn-primary" onclick="switchCamera()">
                            🔄 Switch Camera
                        </button>
                        <button class="btn btn-secondary" onclick="window.location.href='/'">
                            📺 View Stream
                        </button>
                    </div>
                    
                    <div class="card">
                        <h2>⚙️ Quality Settings</h2>
                        <div class="quality-btns">
                            <button class="btn btn-secondary" onclick="setQuality('low')">
                                Low<br>480p
                            </button>
                            <button class="btn btn-secondary" onclick="setQuality('medium')">
                                Medium<br>720p
                            </button>
                            <button class="btn btn-secondary" onclick="setQuality('high')">
                                High<br>1080p
                            </button>
                        </div>
                    </div>
                </div>
            </div>
            
            <script>
                function showAlert(message, type) {
                    const alert = document.getElementById('alert');
                    alert.textContent = message;
                    alert.className = 'alert alert-' + type;
                    alert.style.display = 'block';
                    setTimeout(() => alert.style.display = 'none', 3000);
                }
                
                async function updateStatus() {
                    try {
                        const response = await fetch('/api/status');
                        const data = await response.json();
                        
                        document.getElementById('streaming-status').textContent = 
                            data.isStreaming ? '🟢 Live' : '🔴 Offline';
                        document.getElementById('camera').textContent = 
                            data.currentCamera.charAt(0).toUpperCase() + data.currentCamera.slice(1);
                        document.getElementById('resolution').textContent = data.resolution;
                        document.getElementById('bitrate').textContent = data.bitrate + ' kbps';
                        document.getElementById('fps').textContent = data.fps + ' fps';
                        document.getElementById('segments').textContent = data.segmentCount;
                    } catch (error) {
                        console.error('Failed to update status:', error);
                    }
                }
                
                async function switchCamera() {
                    try {
                        const response = await fetch('/api/camera/switch', { method: 'POST' });
                        const data = await response.json();
                        
                        if (data.success) {
                            showAlert('Camera switched successfully', 'success');
                            updateStatus();
                        }
                    } catch (error) {
                        showAlert('Failed to switch camera: ' + error.message, 'error');
                    }
                }
                
                async function setQuality(level) {
                    try {
                        const response = await fetch('/api/quality/' + level, { method: 'POST' });
                        const data = await response.json();
                        
                        if (data.success) {
                            showAlert('Quality set to ' + level, 'success');
                            updateStatus();
                        }
                    } catch (error) {
                        showAlert('Failed to change quality: ' + error.message, 'error');
                    }
                }
                
                updateStatus();
                setInterval(updateStatus, 2000);
            </script>
        </body>
        </html>
    """.trimIndent()
}

@Serializable
data class StreamStatus(
    val isStreaming: Boolean,
    val currentCamera: String,
    val resolution: String,
    val bitrate: Int,
    val fps: Int,
    val segmentCount: Int
)
