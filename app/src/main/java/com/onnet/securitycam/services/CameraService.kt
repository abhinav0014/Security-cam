package com.onnet.securitycam.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.onnet.securitycam.EmbeddedServer
import com.onnet.securitycam.MainActivity
import com.onnet.securitycam.R
import com.onnet.securitycam.config.SettingsManager
import com.onnet.securitycam.features.CameraProcessor

class CameraService : Service() {
    private var cameraProcessor: CameraProcessor? = null
    private lateinit var settingsManager: SettingsManager
    private var server: EmbeddedServer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        settingsManager = SettingsManager.getInstance(this)
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, createNotification())
                startCamera()
            }
            ACTION_STOP -> stopCamera()
        }
        return START_STICKY
    }

    private fun startCamera() {
        if (cameraProcessor == null) {
            cameraProcessor = CameraProcessor(this) { frame ->
                server?.updateFrame(frame)
            }
            cameraProcessor?.start(settingsManager.cameraSettings)
        }

        if (server == null) {
            server = EmbeddedServer(port = 8080, context = applicationContext).apply {
                try {
                    start(30000)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun stopCamera() {
        cameraProcessor?.stop()
        cameraProcessor = null
        server?.stop()
        server = null
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Security Camera Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Running security camera service"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Security Camera Active")
            .setContentText("Streaming camera feed...")
            .setSmallIcon(R.drawable.ic_camera_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SecurityCam::CameraServiceWakeLock"
        ).apply {
            acquire(10 * 60 * 60 * 1000L) // 10 hours
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopCamera()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "security_cam_channel"
        const val ACTION_START = "com.onnet.securitycam.action.START"
        const val ACTION_STOP = "com.onnet.securitycam.action.STOP"
    }
}