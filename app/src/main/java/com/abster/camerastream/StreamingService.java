package com.abster.camerastream;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.os.Binder;
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
import java.util.concurrent.atomic.AtomicReference;

import fi.iki.elonen.NanoHTTPD;

public class StreamingService extends Service {

    private static final String TAG = "StreamingService";
    private static final String CHANNEL_ID = "camera_stream_channel";
    private static final int NOTIFICATION_ID = 1;
    public static final int PORT = 8080;

    private WebServer webServer;

    // Thread-safe frame storage
    private final AtomicReference<byte[]> latestFrame = new AtomicReference<>(null);

    private volatile StreamQuality quality = StreamQuality.MEDIUM;

    // Analyzer is FINAL and ALWAYS non-null
    private final ImageAnalysis.Analyzer imageAnalyzer = imageProxy -> {
        try {
            processImage(imageProxy);
        } catch (Exception e) {
            Log.e(TAG, "Analyzer error", e);
        } finally {
            imageProxy.close();
        }
    };

    /* =======================
       Binder
       ======================= */

    public class LocalBinder extends Binder {
        StreamingService getService() {
            return StreamingService.this;
        }
    }

    private final IBinder binder = new LocalBinder();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    /* =======================
       Lifecycle
       ======================= */

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent != null && intent.hasExtra("quality")) {
            quality = StreamQuality.valueOf(intent.getStringExtra("quality"));
        }

        startForeground(NOTIFICATION_ID, createNotification());
        startWebServer();

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (webServer != null) {
            webServer.stop();
            webServer = null;
        }
        latestFrame.set(null);
    }

    /* =======================
       Camera Analyzer
       ======================= */

    public ImageAnalysis.Analyzer getImageAnalyzer() {
        return imageAnalyzer;
    }

    public void setQuality(StreamQuality newQuality) {
        quality = newQuality;
    }

    private void processImage(ImageProxy imageProxy) {

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

        YuvImage yuvImage = new YuvImage(
                nv21,
                ImageFormat.NV21,
                image.getWidth(),
                image.getHeight(),
                null
        );

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(
                new Rect(0, 0, image.getWidth(), image.getHeight()),
                quality.getJpegQuality(),
                out
        );

        latestFrame.set(out.toByteArray());
    }

    /* =======================
       Web Server
       ======================= */

    private void startWebServer() {
        try {
            if (webServer != null) {
                webServer.stop();
            }
            webServer = new WebServer(PORT);
            webServer.start();
            Log.d(TAG, "Web server started on port " + PORT);
        } catch (IOException e) {
            Log.e(TAG, "Web server error", e);
        }
    }

    private class WebServer extends NanoHTTPD {

        WebServer(int port) {
            super(port);
        }

        @Override
        public Response serve(IHTTPSession session) {
            try {
                switch (session.getUri()) {
                    case "/":
                    case "/index.html":
                        return serveIndex();
                    case "/stream.mjpeg":
                        return serveMJPEG();
                    case "/snapshot.jpg":
                        return serveSnapshot();
                    case "/status":
                        return serveStatus();
                }
            } catch (Exception e) {
                Log.e(TAG, "HTTP error", e);
            }

            return newFixedLengthResponse(Response.Status.NOT_FOUND,
                    MIME_PLAINTEXT, "Not Found");
        }

        private Response serveIndex() {
            return newFixedLengthResponse(Response.Status.OK,
                    "text/html", getHTMLContent());
        }

        private Response serveSnapshot() {
            byte[] frame = latestFrame.get();
            if (frame == null) {
                return newFixedLengthResponse(Response.Status.SERVICE_UNAVAILABLE,
                        MIME_PLAINTEXT, "No frame");
            }

            return newFixedLengthResponse(Response.Status.OK,
                    "image/jpeg",
                    new ByteArrayInputStream(frame),
                    frame.length);
        }

        private Response serveMJPEG() {
            return newChunkedResponse(Response.Status.OK,
                    "multipart/x-mixed-replace; boundary=frame",
                    new MJPEGInputStream());
        }

        private Response serveStatus() {
            String json = String.format(
                    "{\"streaming\":%b,\"quality\":\"%s\"}",
                    latestFrame.get() != null,
                    quality.name()
            );
            return newFixedLengthResponse(Response.Status.OK,
                    "application/json", json);
        }
    }

    private class MJPEGInputStream extends InputStream {

        private boolean running = true;

        @Override
        public int read() {
            return -1;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (!running) return -1;

            byte[] frame = latestFrame.get();
            if (frame == null) return 0;

            String header =
                    "--frame\r\n" +
                    "Content-Type: image/jpeg\r\n" +
                    "Content-Length: " + frame.length + "\r\n\r\n";

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(header.getBytes());
            baos.write(frame);
            baos.write("\r\n".getBytes());

            byte[] data = baos.toByteArray();
            int count = Math.min(data.length, length);
            System.arraycopy(data, 0, buffer, offset, count);

            try {
                Thread.sleep(33); // ~30 FPS
            } catch (InterruptedException ignored) {}

            return count;
        }

        @Override
        public void close() {
            running = false;
        }
    }

    /* =======================
       Notification
       ======================= */

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Camera Stream",
                    NotificationManager.IMPORTANCE_LOW
            );

            NotificationManager manager =
                    getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Camera Streaming")
                .setContentText("Running on port " + PORT)
                .setSmallIcon(R.drawable.ic_videocam)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    /* =======================
       HTML
       ======================= */

    private String getHTMLContent() {
        return "<!DOCTYPE html><html><body style='margin:0;background:#000'>" +
               "<img src='/stream.mjpeg' style='width:100%;height:auto'/>" +
               "</body></html>";
    }
}