package com.onnet.securitycam.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.onnet.securitycam.MainActivity
import com.onnet.securitycam.R
import timber.log.Timber

/**
 * Lightweight skeleton streaming service. The full RTSP/RTMP integration
 * requires adding the pedroSG94 library which may not be available in this environment.
 * This service provides start/stop lifecycle and a foreground notification.
 */
class StreamingService : Service() {
    companion object {
        private const val TAG = "StreamingService"
        private const val CHANNEL_ID = "StreamingServiceChannel"
        private const val NOTIF_ID = 4242
    }

    private var streamUrl: String = ""

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra("stream_url")
        url?.let { startRtsp(it) }

        val notification = buildForegroundNotification()
        startForeground(NOTIF_ID, notification)
        return START_STICKY
    }

    private fun startRtsp(url: String) {
        try {
            streamUrl = url ?: ""
            // RTSP integration removed in this environment. Keep skeleton to later attach a
            // streaming implementation (MediaCodec/Socket or third-party library).
            Timber.i("Streaming requested to: $streamUrl")
        } catch (e: Exception) {
            Timber.e(e, "Error starting streaming")
        }
    }

    private fun stopRtsp() {
        // no-op for skeleton
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRtsp()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Streaming Service", NotificationManager.IMPORTANCE_LOW)
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SecurityCam Streaming")
            .setContentText("Streaming live to $streamUrl")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pending)
            .build()
    }
}
