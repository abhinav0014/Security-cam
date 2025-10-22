package com.onnet.securitycam.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.onnet.securitycam.MainActivity
import com.onnet.securitycam.R
import com.onnet.securitycam.config.SettingsManager
import com.onnet.securitycam.features.CameraProcessor

class CameraService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    private var cameraProcessor: CameraProcessor? = null
    private val settingsManager by lazy { SettingsManager.getInstance(this) }
    private var isServiceRunning = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startService()
            ACTION_STOP -> stopService()
        }
        return START_STICKY
    }

    private fun startService() {
        if (isServiceRunning) return
        isServiceRunning = true

        // Create notification
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        // Acquire wake lock
        acquireWakeLock()

        // Start camera processing
        cameraProcessor = CameraProcessor(this) { frame ->
            // Process frames
        }.apply {
            start(settingsManager.cameraSettings)
        }
    }

    private fun stopService() {
        isServiceRunning = false
        releaseWakeLock()
        cameraProcessor?.release()
        stopForeground(true)
        stopSelf()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Camera Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps the camera service running"
            setShowBadge(false)
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Security Camera Active")
            .setContentText("Tap to open camera controls")
            .setSmallIcon(R.drawable.ic_camera_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun acquireWakeLock() {
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
            newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SecurityCam::CameraServiceLock").apply {
                acquire(10*60*1000L) // 10 minutes
            }
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
        stopService()
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "camera_service_channel"
        private const val ACTION_START = "com.onnet.securitycam.action.START"
        private const val ACTION_STOP = "com.onnet.securitycam.action.STOP"

        fun startService(context: Context) {
            val intent = Intent(context, CameraService::class.java).apply {
                action = ACTION_START
            }
            context.startForegroundService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, CameraService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}