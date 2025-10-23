package com.onnet.securitycam.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.text.format.Formatter
import androidx.core.content.ContextCompat
import timber.log.Timber

object Utils {
    fun hasPermissions(context: Context): Boolean {
        val camera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        val audio = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
        return camera == PackageManager.PERMISSION_GRANTED && audio == PackageManager.PERMISSION_GRANTED
    }

    @Suppress("DEPRECATION")
    fun getLocalIpAddress(context: Context): String {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ip = wifiManager.connectionInfo.ipAddress
            if (ip != 0) Formatter.formatIpAddress(ip) else "127.0.0.1"
        } catch (e: Exception) {
            Timber.e(e, "Failed to get IP")
            "127.0.0.1"
        }
    }
}
