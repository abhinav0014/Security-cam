package com.onnet.securitycam

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream

class StreamServer(
    port: Int,
    private val hlsDir: File,
    private val preferences: PreferencesManager
) : NanoHTTPD(port) {

    private val TAG = "StreamServer"
    private var connectionCount = 0

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        Log.d(TAG, "Request: $uri from ${session.remoteIpAddress}")

        return when {
            uri == "/" || uri == "/index.html" -> serveWebInterface()
            uri == "/stream.m3u8" -> servePlaylist()
            uri.startsWith("/segment_") && uri.endsWith(".ts") -> serveSegment(uri)
            uri == "/snapshot.jpg" -> serveSnapshot()
            uri == "/status" -> serveStatus()
            uri == "/settings" && session.method == Method.POST -> updateSettings(session)
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
        }
    }

    private fun serveWebInterface(): Response {
        val html = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Security Camera Stream</title>
    <script src="https://cdn.jsdelivr.net/npm/hls.js@latest"></script>
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }
        body {
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: #fff;
            min-height: 100vh;
            display: flex;
            flex-direction: column;
            align-items: center;
            padding: 20px;
        }
        .container {
            max-width: 1200px;
            width: 100%;
        }
        h1 {
            text-align: center;
            margin-bottom: 30px;
            font-size: 2.5rem;
            text-shadow: 2px 2px 4px rgba(0,0,0,0.3);
        }
        .video-container {
            background: rgba(0,0,0,0.3);
            border-radius: 15px;
            padding: 20px;
            margin-bottom: 20px;
            box-shadow: 0 8px 32px rgba(0,0,0,0.3);
        }
        video {
            width: 100%;
            border-radius: 10px;
            background: #000;
        }
        .controls {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 15px;
            margin-top: 20px;
        }
        .info-card {
            background: rgba(255,255,255,0.1);
            backdrop-filter: blur(10px);
            border-radius: 10px;
            padding: 15px;
            border: 1px solid rgba(255,255,255,0.2);
        }
        .info-card h3 {
            margin-bottom: 10px;
            font-size: 1.1rem;
            color: #ffd700;
        }
        .info-card p {
            margin: 5px 0;
            font-size: 0.9rem;
        }
        .status {
            display: inline-block;
            padding: 5px 15px;
            border-radius: 20px;
            font-weight: bold;
            font-size: 0.85rem;
        }
        .status.connected {
            background: #10b981;
        }
        .status.disconnected {
            background: #ef4444;
        }
        button {
            padding: 12px 24px;
            border: none;
            border-radius: 8px;
            background: #10b981;
            color: white;
            font-size: 1rem;
            font-weight: 600;
            cursor: pointer;
            transition: all 0.3s;
            box-shadow: 0 4px 15px rgba(16,185,129,0.3);
        }
        button:hover {
            background: #059669;
            transform: translateY(-2px);
            box-shadow: 0 6px 20px rgba(16,185,129,0.4);
        }
        button:active {
            transform: translateY(0);
        }
        .error {
            background: rgba(239,68,68,0.2);
            border: 1px solid #ef4444;
            border-radius: 8px;
            padding: 15px;
            margin-top: 15px;
            display: none;
        }
        @media (max-width: 768px) {
            h1 {
                font-size: 1.8rem;
            }
            .controls {
                grid-template-columns: 1fr;
            }
        }
    </style>
</head>
<body>
    <div class="container">
        <h1>🎥 Security Camera Stream</h1>
        
        <div class="video-container">
            <video id="video" controls autoplay muted playsinline></video>
            <div id="error" class="error">
                <strong>⚠️ Error:</strong> <span id="error-message"></span>
            </div>
        </div>

        <div class="controls">
            <div class="info-card">
                <h3>📊 Status</h3>
                <p>Connection: <span id="status" class="status disconnected">Disconnected</span></p>
                <p>Resolution: <span id="resolution">Loading...</span></p>
                <p>FPS: <span id="fps">Loading...</span></p>
                <p>Bitrate: <span id="bitrate">Loading...</span></p>
            </div>

            <div class="info-card">
                <h3>⚙️ Actions</h3>
                <button onclick="toggleFullscreen()">📺 Fullscreen</button>
                <button onclick="takeSnapshot()" style="margin-top: 10px; background: #3b82f6;">📸 Snapshot</button>
            </div>

            <div class="info-card">
                <h3>ℹ️ Information</h3>
                <p>Stream Type: HLS</p>
                <p>Latency: ~6-10 seconds</p>
                <p>Browser: <span id="browser">Detecting...</span></p>
            </div>
        </div>
    </div>

    <script>
        const video = document.getElementById('video');
        const statusEl = document.getElementById('status');
        const errorEl = document.getElementById('error');
        const errorMsgEl = document.getElementById('error-message');

        // Initialize HLS
        if (Hls.isSupported()) {
            const hls = new Hls({
                enableWorker: true,
                lowLatencyMode: true,
                backBufferLength: 90
            });

            hls.loadSource('/stream.m3u8');
            hls.attachMedia(video);

            hls.on(Hls.Events.MANIFEST_PARSED, () => {
                statusEl.textContent = 'Connected';
                statusEl.className = 'status connected';
                video.play();
                loadStatus();
            });

            hls.on(Hls.Events.ERROR, (event, data) => {
                if (data.fatal) {
                    statusEl.textContent = 'Disconnected';
                    statusEl.className = 'status disconnected';
                    showError('Stream error: ' + data.type);
                    
                    setTimeout(() => {
                        hls.loadSource('/stream.m3u8');
                    }, 3000);
                }
            });
        } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
            // Native HLS support (Safari)
            video.src = '/stream.m3u8';
            video.addEventListener('loadedmetadata', () => {
                statusEl.textContent = 'Connected';
                statusEl.className = 'status connected';
                loadStatus();
            });
        } else {
            showError('HLS is not supported in your browser');
        }

        // Load status
        async function loadStatus() {
            try {
                const response = await fetch('/status');
                const data = await response.json();
                
                document.getElementById('resolution').textContent = data.resolution;
                document.getElementById('fps').textContent = data.fps + ' fps';
                document.getElementById('bitrate').textContent = data.bitrate;
            } catch (err) {
                console.error('Failed to load status:', err);
            }
        }

        function showError(message) {
            errorMsgEl.textContent = message;
            errorEl.style.display = 'block';
            setTimeout(() => {
                errorEl.style.display = 'none';
            }, 5000);
        }

        function toggleFullscreen() {
            if (!document.fullscreenElement) {
                video.requestFullscreen().catch(err => {
                    showError('Fullscreen failed: ' + err.message);
                });
            } else {
                document.exitFullscreen();
            }
        }

        function takeSnapshot() {
            window.open('/snapshot.jpg', '_blank');
        }

        // Detect browser
        const ua = navigator.userAgent;
        let browser = 'Unknown';
        if (ua.includes('Chrome')) browser = 'Chrome';
        else if (ua.includes('Firefox')) browser = 'Firefox';
        else if (ua.includes('Safari')) browser = 'Safari';
        else if (ua.includes('Edge')) browser = 'Edge';
        document.getElementById('browser').textContent = browser;

        // Auto-refresh status
        setInterval(loadStatus, 5000);
    </script>
</body>
</html>
        """.trimIndent()

        return newFixedLengthResponse(Response.Status.OK, "text/html", html)
    }

    private fun servePlaylist(): Response {
        val playlistFile = File(hlsDir, "stream.m3u8")
        return if (playlistFile.exists()) {
            try {
                val response = newChunkedResponse(
                    Response.Status.OK,
                    "application/vnd.apple.mpegurl",
                    FileInputStream(playlistFile)
                )
                addCORSHeaders(response)
                response
            } catch (e: Exception) {
                Log.e(TAG, "Error serving playlist: ${e.message}", e)
                newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR,
                    MIME_PLAINTEXT,
                    "Error serving playlist"
                )
            }
        } else {
            newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                MIME_PLAINTEXT,
                "Playlist not found"
            )
        }
    }

    private fun serveSegment(uri: String): Response {
        val fileName = uri.substring(1) // Remove leading slash
        val segmentFile = File(hlsDir, fileName)
        
        return if (segmentFile.exists()) {
            try {
                val response = newChunkedResponse(
                    Response.Status.OK,
                    "video/mp2t",
                    FileInputStream(segmentFile)
                )
                addCORSHeaders(response)
                response
            } catch (e: Exception) {
                Log.e(TAG, "Error serving segment: ${e.message}", e)
                newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR,
                    MIME_PLAINTEXT,
                    "Error serving segment"
                )
            }
        } else {
            newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                MIME_PLAINTEXT,
                "Segment not found"
            )
        }
    }

    private fun serveSnapshot(): Response {
        // TODO: Implement snapshot capture
        // For now, return placeholder
        return newFixedLengthResponse(
            Response.Status.NOT_IMPLEMENTED,
            MIME_PLAINTEXT,
            "Snapshot feature coming soon"
        )
    }

    private fun serveStatus(): Response {
        val json = """
        {
            "status": "streaming",
            "resolution": "${preferences.getResolution()}",
            "fps": ${preferences.getFps()},
            "bitrate": "${Utils.formatBitrate(preferences.getBitrate())}",
            "port": ${preferences.getPort()},
            "audioEnabled": ${preferences.isAudioEnabled()},
            "hlsEnabled": ${preferences.isHlsEnabled()}
        }
        """.trimIndent()

        val response = newFixedLengthResponse(Response.Status.OK, "application/json", json)
        addCORSHeaders(response)
        return response
    }

    private fun updateSettings(session: IHTTPSession): Response {
        // TODO: Implement settings update via POST
        return newFixedLengthResponse(
            Response.Status.NOT_IMPLEMENTED,
            MIME_PLAINTEXT,
            "Settings update coming soon"
        )
    }

    private fun addCORSHeaders(response: Response) {
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        response.addHeader("Access-Control-Allow-Headers", "Content-Type")
    }

    override fun start() {
        super.start()
        Log.d(TAG, "Stream server started on port $listeningPort")
    }

    override fun stop() {
        super.stop()
        Log.d(TAG, "Stream server stopped")
    }
}