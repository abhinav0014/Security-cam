package com.onnet.securitycam

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object PermissionHelper {
    const val REQUEST_PERMISSIONS = 1001

    private val REQUIRED_BASE = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.WAKE_LOCK
    )

    private val REQUIRED_LOCATION = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private val REQUIRED_STORAGE_LEGACY = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    private val REQUIRED_STORAGE_MODERN = arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO
    )

    val REQUIRED: Array<String>
        get() {
            val permissions = mutableListOf<String>()
            permissions.addAll(REQUIRED_BASE)
            permissions.addAll(REQUIRED_LOCATION)
            
            // Storage permissions based on Android version
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions.addAll(REQUIRED_STORAGE_MODERN)
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                permissions.addAll(REQUIRED_STORAGE_LEGACY)
            }
            
            return permissions.toTypedArray()
        }

    fun hasAll(activity: Activity): Boolean {
        return REQUIRED.all { 
            ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED 
        }
    }

    fun request(activity: Activity) {
        ActivityCompat.requestPermissions(activity, REQUIRED, REQUEST_PERMISSIONS)
    }

    fun shouldShowRationale(activity: Activity): Boolean {
        return REQUIRED.any { 
            ActivityCompat.shouldShowRequestPermissionRationale(activity, it) 
        }
    }

    fun getDeniedPermissions(activity: Activity): List<String> {
        return REQUIRED.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }
    }
}