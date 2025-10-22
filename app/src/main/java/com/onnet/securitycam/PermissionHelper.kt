package com.onnet.securitycam

import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object PermissionHelper {
    const val REQUEST_PERMISSIONS = 1001
    val REQUIRED = arrayOf(
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
        android.Manifest.permission.INTERNET,
        android.Manifest.permission.ACCESS_NETWORK_STATE,
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    )

    fun hasAll(activity: Activity): Boolean {
        return REQUIRED.all { ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED }
    }

    fun request(activity: Activity) {
        ActivityCompat.requestPermissions(activity, REQUIRED, REQUEST_PERMISSIONS)
    }
}
