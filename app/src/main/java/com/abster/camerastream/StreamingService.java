package com.abster.camerastream;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.core.app.NotificationCompat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

public class StreamingService extends Service {
    private static final String TAG = "StreamingService";
    private static final String CHANNEL_ID = "camera_stream_channel";
    private static final int NOTIFICATION_ID = 1;
    public static final int PORT = 8080;

    private WebServer webServer;
    private static volatile byte[] latestFrame = null;
    private static ImageAnalysis.Analyzer imageAnalyzer;
    private StreamQuality quality = StreamQuality.MEDIUM;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        
        imageAnalyzer = new ImageAnalysis.Analyzer() {
            @Override
            public void analyze(@androidx.annotation.NonNull ImageProxy imageProxy) {
                processImage(imageProxy);
                imageProxy.close();
            }
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra("quality")) {
            String qualityName = intent.getStringExtra("quality");
            quality = StreamQuality.valueOf(qualityName);
        }

        startForeground(NOTIFICATION_ID, createNotification());
        startWebServer();
        
        return START_STICKY;
    }

    private void startWebServer() {
        try {
            if (webServer != null) {
                webServer.stop();
            }
            webServer = new WebServer(PORT);
            webServer.start();
            Log.d(TAG, "Web server started on port " + PORT);
        } catch (IOException e) {
            Log.e(TAG, "Error starting web server", e);
        }
    }

    private void processImage(ImageProxy imageProxy) {
        try {
            Image image = imageProxy.getImage();
            if (image == null) return;

            ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
            ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
            ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            byte[] nv21 = new byte[ySize + uSize + vSize];

            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);

            YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21,
                    image.getWidth(), image.getHeight(), null);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(
                    new Rect(0, 0, image.getWidth(), image.getHeight()),
                    quality.getJpegQuality(),
                    out
            );

            latestFrame = out.toByteArray();
        } catch (Exception e) {
            Log.e(TAG, "Error processing image", e);
        }
    }

    public static ImageAnalysis.Analyzer getImageAnalyzer() {
        return imageAnalyzer;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Camera Stream Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Streaming camera feed");
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Camera Streaming Active")
                .setContentText("Streaming on port " + PORT)
                .setSmallIcon(R.drawable.ic_videocam)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (webServer != null) {
            webServer.stop();
        }
        latestFrame = null;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private class WebServer extends NanoHTTPD {
        public WebServer(int port) {
            super(port);
        }

        @Override
        public Response serve(IHTTPSession session) {
            String uri = session.getUri();
            
            try {
                if (uri.equals("/") || uri.equals("/index.html")) {
                    return serveIndexPage(session);
                } else if (uri.equals("/stream.mjpeg")) {
                    return serveMJPEGStream();
                } else if (uri.equals("/snapshot.jpg")) {
                    return serveSnapshot();
                } else if (uri.equals("/status")) {
                    return serveStatus();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error serving request", e);
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR,
                        MIME_PLAINTEXT, "Internal Server Error");
            }

            return newFixedLengthResponse(Response.Status.NOT_FOUND,
                    MIME_PLAINTEXT, "Not Found");
        }

        private Response serveIndexPage(IHTTPSession session) {
            String html = getHTMLContent();
            return newFixedLengthResponse(Response.Status.OK, "text/html", html);
        }

        private Response serveMJPEGStream() {
            return newChunkedResponse(Response.Status.OK,
                    "multipart/x-mixed-replace; boundary=frame",
                    new InputStream() {
                        private boolean running = true;

                        @Override
                        public int read() throws IOException {
                            return -1;
                        }

                        @Override
                        public int read(byte[] b, int off, int len) throws IOException {
                            if (!running || latestFrame == null) {
                                return -1;
                            }

                            try {
                                String boundary = "--frame\r\nContent-Type: image/jpeg\r\n" +
                                        "Content-Length: " + latestFrame.length + "\r\n\r\n";
                                byte[] boundaryBytes = boundary.getBytes();
                                byte[] endBytes = "\r\n".getBytes();

                                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                baos.write(boundaryBytes);
                                baos.write(latestFrame);
                                baos.write(endBytes);

                                byte[] frameData = baos.toByteArray();
                                int bytesToCopy = Math.min(frameData.length, len);
                                System.arraycopy(frameData, 0, b, off, bytesToCopy);

                                Thread.sleep(33); // ~30 FPS
                                return bytesToCopy;
                            } catch (InterruptedException e) {
                                running = false;
                                return -1;
                            }
                        }

                        @Override
                        public void close() throws IOException {
                            running = false;
                            super.close();
                        }
                    });
        }

        private Response serveSnapshot() {
            if (latestFrame != null) {
                return newFixedLengthResponse(Response.Status.OK, "image/jpeg",
                        new ByteArrayInputStream(latestFrame), latestFrame.length);
            }
            return newFixedLengthResponse(Response.Status.SERVICE_UNAVAILABLE,
                    MIME_PLAINTEXT, "No frame available");
        }

        private Response serveStatus() {
            String json = String.format(
                    "{\"streaming\":%b,\"quality\":\"%s\",\"resolution\":\"%s\"}",
                    latestFrame != null,
                    quality.name(),
                    quality.getLabel()
            );
            return newFixedLengthResponse(Response.Status.OK,
                    "application/json", json);
        }

        private String getHTMLContent() {
            return "<!DOCTYPE html>\n" +
                    "<html lang=\"en\">\n" +
                    "<head>\n" +
                    "    <meta charset=\"UTF-8\">\n" +
                    "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                    "    <title>Live Camera Stream</title>\n" +
                    "    <style>\n" +
                    "        * {\n" +
                    "            margin: 0;\n" +
                    "            padding: 0;\n" +
                    "            box-sizing: border-box;\n" +
                    "        }\n" +
                    "        body {\n" +
                    "            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;\n" +
                    "            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);\n" +
                    "            min-height: 100vh;\n" +
                    "            display: flex;\n" +
                    "            flex-direction: column;\n" +
                    "            align-items: center;\n" +
                    "            padding: 20px;\n" +
                    "        }\n" +
                    "        .container {\n" +
                    "            max-width: 1200px;\n" +
                    "            width: 100%;\n" +
                    "        }\n" +
                    "        header {\n" +
                    "            text-align: center;\n" +
                    "            color: white;\n" +
                    "            margin-bottom: 30px;\n" +
                    "        }\n" +
                    "        h1 {\n" +
                    "            font-size: 2.5em;\n" +
                    "            margin-bottom: 10px;\n" +
                    "            text-shadow: 2px 2px 4px rgba(0,0,0,0.3);\n" +
                    "        }\n" +
                    "        .status-badge {\n" +
                    "            display: inline-block;\n" +
                    "            padding: 8px 20px;\n" +
                    "            background: rgba(255,255,255,0.2);\n" +
                    "            border-radius: 20px;\n" +
                    "            backdrop-filter: blur(10px);\n" +
                    "        }\n" +
                    "        .status-dot {\n" +
                    "            display: inline-block;\n" +
                    "            width: 10px;\n" +
                    "            height: 10px;\n" +
                    "            background: #4ade80;\n" +
                    "            border-radius: 50%;\n" +
                    "            margin-right: 8px;\n" +
                    "            animation: pulse 2s infinite;\n" +
                    "        }\n" +
                    "        @keyframes pulse {\n" +
                    "            0%, 100% { opacity: 1; }\n" +
                    "            50% { opacity: 0.5; }\n" +
                    "        }\n" +
                    "        .video-container {\n" +
                    "            background: white;\n" +
                    "            border-radius: 20px;\n" +
                    "            box-shadow: 0 20px 60px rgba(0,0,0,0.3);\n" +
                    "            padding: 20px;\n" +
                    "            margin-bottom: 20px;\n" +
                    "        }\n" +
                    "        .video-wrapper {\n" +
                    "            position: relative;\n" +
                    "            width: 100%;\n" +
                    "            padding-bottom: 56.25%;\n" +
                    "            background: #000;\n" +
                    "            border-radius: 12px;\n" +
                    "            overflow: hidden;\n" +
                    "        }\n" +
                    "        #stream {\n" +
                    "            position: absolute;\n" +
                    "            top: 0;\n" +
                    "            left: 0;\n" +
                    "            width: 100%;\n" +
                    "            height: 100%;\n" +
                    "            object-fit: contain;\n" +
                    "        }\n" +
                    "        .controls {\n" +
                    "            display: flex;\n" +
                    "            gap: 15px;\n" +
                    "            margin-top: 20px;\n" +
                    "            flex-wrap: wrap;\n" +
                    "            justify-content: center;\n" +
                    "        }\n" +
                    "        button {\n" +
                    "            padding: 12px 30px;\n" +
                    "            border: none;\n" +
                    "            border-radius: 25px;\n" +
                    "            font-size: 16px;\n" +
                    "            font-weight: 600;\n" +
                    "            cursor: pointer;\n" +
                    "            transition: all 0.3s;\n" +
                    "            box-shadow: 0 4px 15px rgba(0,0,0,0.2);\n" +
                    "        }\n" +
                    "        .btn-primary {\n" +
                    "            background: #667eea;\n" +
                    "            color: white;\n" +
                    "        }\n" +
                    "        .btn-primary:hover {\n" +
                    "            background: #5568d3;\n" +
                    "            transform: translateY(-2px);\n" +
                    "            box-shadow: 0 6px 20px rgba(0,0,0,0.3);\n" +
                    "        }\n" +
                    "        .btn-secondary {\n" +
                    "            background: #10b981;\n" +
                    "            color: white;\n" +
                    "        }\n" +
                    "        .btn-secondary:hover {\n" +
                    "            background: #059669;\n" +
                    "            transform: translateY(-2px);\n" +
                    "        }\n" +
                    "        .quality-selector {\n" +
                    "            display: flex;\n" +
                    "            gap: 10px;\n" +
                    "            background: white;\n" +
                    "            padding: 15px;\n" +
                    "            border-radius: 20px;\n" +
                    "            box-shadow: 0 10px 40px rgba(0,0,0,0.2);\n" +
                    "        }\n" +
                    "        .quality-btn {\n" +
                    "            padding: 10px 20px;\n" +
                    "            background: #f3f4f6;\n" +
                    "            color: #374151;\n" +
                    "        }\n" +
                    "        .quality-btn.active {\n" +
                    "            background: #667eea;\n" +
                    "            color: white;\n" +
                    "        }\n" +
                    "        .info-panel {\n" +
                    "            background: white;\n" +
                    "            border-radius: 20px;\n" +
                    "            padding: 20px;\n" +
                    "            box-shadow: 0 10px 40px rgba(0,0,0,0.2);\n" +
                    "        }\n" +
                    "        .info-item {\n" +
                    "            display: flex;\n" +
                    "            justify-content: space-between;\n" +
                    "            padding: 10px 0;\n" +
                    "            border-bottom: 1px solid #e5e7eb;\n" +
                    "        }\n" +
                    "        .info-item:last-child {\n" +
                    "            border-bottom: none;\n" +
                    "        }\n" +
                    "        .info-label {\n" +
                    "            font-weight: 600;\n" +
                    "            color: #6b7280;\n" +
                    "        }\n" +
                    "        .info-value {\n" +
                    "            color: #111827;\n" +
                    "        }\n" +
                    "        @media (max-width: 768px) {\n" +
                    "            h1 { font-size: 1.8em; }\n" +
                    "            .controls { flex-direction: column; }\n" +
                    "            button { width: 100%; }\n" +
                    "        }\n" +
                    "    </style>\n" +
                    "</head>\n" +
                    "<body>\n" +
                    "    <div class=\"container\">\n" +
                    "        <header>\n" +
                    "            <h1>📹 Live Camera Stream</h1>\n" +
                    "            <div class=\"status-badge\">\n" +
                    "                <span class=\"status-dot\"></span>\n" +
                    "                <span id=\"status-text\">Live</span>\n" +
                    "            </div>\n" +
                    "        </header>\n" +
                    "\n" +
                    "        <div class=\"video-container\">\n" +
                    "            <div class=\"video-wrapper\">\n" +
                    "                <img id=\"stream\" src=\"/stream.mjpeg\" alt=\"Live Stream\">\n" +
                    "            </div>\n" +
                    "            <div class=\"controls\">\n" +
                    "                <button class=\"btn-primary\" onclick=\"refreshStream()\">🔄 Refresh Stream</button>\n" +
                    "                <button class=\"btn-secondary\" onclick=\"takeSnapshot()\">📸 Take Snapshot</button>\n" +
                    "                <button class=\"btn-primary\" onclick=\"toggleFullscreen()\">⛶ Fullscreen</button>\n" +
                    "            </div>\n" +
                    "        </div>\n" +
                    "\n" +
                    "        <div class=\"info-panel\">\n" +
                    "            <div class=\"info-item\">\n" +
                    "                <span class=\"info-label\">Stream URL:</span>\n" +
                    "                <span class=\"info-value\" id=\"stream-url\">Loading...</span>\n" +
                    "            </div>\n" +
                    "            <div class=\"info-item\">\n" +
                    "                <span class=\"info-label\">Quality:</span>\n" +
                    "                <span class=\"info-value\" id=\"quality\">Loading...</span>\n" +
                    "            </div>\n" +
                    "            <div class=\"info-item\">\n" +
                    "                <span class=\"info-label\">Resolution:</span>\n" +
                    "                <span class=\"info-value\" id=\"resolution\">Loading...</span>\n" +
                    "            </div>\n" +
                    "        </div>\n" +
                    "    </div>\n" +
                    "\n" +
                    "    <script>\n" +
                    "        function refreshStream() {\n" +
                    "            const stream = document.getElementById('stream');\n" +
                    "            stream.src = '/stream.mjpeg?' + new Date().getTime();\n" +
                    "        }\n" +
                    "\n" +
                    "        function takeSnapshot() {\n" +
                    "            window.open('/snapshot.jpg', '_blank');\n" +
                    "        }\n" +
                    "\n" +
                    "        function toggleFullscreen() {\n" +
                    "            const stream = document.getElementById('stream');\n" +
                    "            if (!document.fullscreenElement) {\n" +
                    "                stream.requestFullscreen().catch(err => {\n" +
                    "                    alert('Error attempting to enable fullscreen: ' + err.message);\n" +
                    "                });\n" +
                    "            } else {\n" +
                    "                document.exitFullscreen();\n" +
                    "            }\n" +
                    "        }\n" +
                    "\n" +
                    "        async function updateStatus() {\n" +
                    "            try {\n" +
                    "                const response = await fetch('/status');\n" +
                    "                const data = await response.json();\n" +
                    "                document.getElementById('quality').textContent = data.quality;\n" +
                    "                document.getElementById('resolution').textContent = data.resolution;\n" +
                    "                document.getElementById('stream-url').textContent = window.location.href;\n" +
                    "            } catch (error) {\n" +
                    "                console.error('Error fetching status:', error);\n" +
                    "            }\n" +
                    "        }\n" +
                    "\n" +
                    "        // Update status on load\n" +
                    "        updateStatus();\n" +
                    "        \n" +
                    "        // Refresh status every 5 seconds\n" +
                    "        setInterval(updateStatus, 5000);\n" +
                    "    </script>\n" +
                    "</body>\n" +
                    "</html>";
        }
    }
}
