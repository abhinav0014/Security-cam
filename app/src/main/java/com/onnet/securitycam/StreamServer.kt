package com.onnet.securitycam

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream

class StreamServer(
    port: Int,
    private val hlsDir: File,
    private val preferences: PreferencesManager,
    private val context: Context
) : NanoHTTPD(port) {

    private val TAG = "StreamServer"
    private var connectionCount = 0

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        Log.d(TAG, "Request: $uri from ${session.remoteIpAddress}")
        return when {
            uri == "/" || uri == "/index.html" -> serveWebInterface(session)
            uri.startsWith("/segment_") && (uri.endsWith(".ts") || uri.endsWith(".mp4")) -> serveSegment(uri)
            uri == "/stream.m3u8" -> servePlaylist()
            uri == "/snapshot.jpg" -> serveSnapshot()
            uri == "/status" -> serveStatus()
            uri == "/settings" && session.method == Method.POST -> updateSettings(session)
            uri == "/ws-info" -> serveWebSocketInfo()
            uri == "/ws-client" -> serveWebSocketClient()
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        }
    }

    private fun serveWebInterface(session: IHTTPSession): Response {
        val hostHeader = session.headers["host"] ?: "localhost:${preferences.getPort()}"
        val wsUrl = "ws://" + hostHeader.substringBefore(":") + ":" + preferences.getWebSocketPort() + "/stream"
        val protocol = preferences.getStreamProtocol()
        
        val html = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Security Cam Stream</title>
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
            <h1>Security Cam Stream</h1>
            <div class="info-card">
                <h3>Stream Types Available</h3>
                ${if (protocol == "HLS" || protocol == "Both") """
                <p><strong>HLS:</strong> <a href="/stream.m3u8">/stream.m3u8</a> in any HLS player</p>
                """ else ""}
                ${if (protocol == "WebSocket" || protocol == "Both") """
                <p><strong>WebSocket:</strong> <a href="$wsUrl" style="font-family: monospace;">$wsUrl</a></p>
                """ else ""}
                <p><em>Current Protocol: $protocol</em></p>
            </div>

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
        val fileName = uri.removePrefix("/")
        val file = File(hlsDir, fileName)
        if (file.exists()) {
            val mimeType = when {
                fileName.endsWith(".mp4") -> "video/mp4"
                fileName.endsWith(".ts") -> "video/mp2t"
                else -> "application/octet-stream"
            }
            try {
                val response = newChunkedResponse(
                    Response.Status.OK,
                    mimeType,
                    FileInputStream(file)
                )
                addCORSHeaders(response)
                response.addHeader("Cache-Control", "max-age=3600")
                response.addHeader("Accept-Ranges", "bytes")
                Log.d(TAG, "Serving segment: $fileName, mimeType=$mimeType, size=${file.length()}")
                return response
            } catch (e: Exception) {
                Log.e(TAG, "Error serving segment: $fileName", e)
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error serving segment")
            }
        } else {
            Log.w(TAG, "Segment not found: $fileName")
            return newFixedLengthResponse(
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
        "hlsEnabled": ${preferences.isHlsEnabled()},
        "segmentFormat": "mp4",
        "webSocketEnabled": ${preferences.getStreamProtocol() != "HLS"},
        "webSocketPort": ${preferences.getWebSocketPort()},
        "streamProtocol": "${preferences.getStreamProtocol()}"
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

    private fun serveWebSocketClient(): Response {
        try {
            val inputStream = context.resources.openRawResource(R.raw.client)
            return newChunkedResponse(Response.Status.OK, "text/html", inputStream)
        } catch (e: Exception) {
            Log.e(TAG, "Error serving WebSocket client: ${e.message}", e)
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error serving WebSocket client")
        }
    }

    private fun serveWebSocketInfo(): Response {
        val json = """
        {
            "enabled": ${preferences.getStreamProtocol() != "HLS"},
            "port": ${preferences.getWebSocketPort()},
            "endpoint": "/stream",
            "protocol": "binary",
            "format": "H.264 Annex-B + ADTS AAC",
            "frameTypes": {
                "video": 1,
                "audio": 2,
                "video_config": 3,
                "audio_config": 4
            },
            "flags": {
                "keyframe": 1
            },
            "headerSize": 14,
            "headerFormat": "1 byte type + 1 byte flags + 8 bytes timestamp + 4 bytes length"
        }
        """.trimIndent()
        val response = newFixedLengthResponse(Response.Status.OK, "application/json", json)
        addCORSHeaders(response)
        return response
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