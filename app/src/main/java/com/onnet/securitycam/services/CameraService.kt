package com.onnet.securitycam.services

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.onnet.securitycam.MainActivity
import com.onnet.securitycam.R
import com.onnet.securitycam.config.SettingsManager
import com.onnet.securitycam.features.CameraProcessor

class CameraService : Service() {
    private var cameraProcessor: CameraProcessor? = null
    private lateinit var settingsManager: SettingsManager
    private var server: EmbeddedServer? = null

    override fun onCreate() {
        super.onCreate()
        settingsManager = SettingsManager.getInstance(this)
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startCamera()
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
        stopForeground(true)
        stopSelf()
    }

    private fun createNotification(): Notification {
        val channelId = "security_cam_channel"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Security Camera Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Running security camera service"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Security Camera Active")
            .setContentText("Streaming camera feed...")
            .setSmallIcon(R.drawable.ic_camera_notification)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopCamera()
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.onnet.securitycam.action.START"
        const val ACTION_STOP = "com.onnet.securitycam.action.STOP"
    }
}