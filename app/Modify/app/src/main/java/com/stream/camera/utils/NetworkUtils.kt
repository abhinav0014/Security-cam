package com.stream.camera.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.util.Log
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * Network Utilities
 * Helper functions for network operations
 */
object NetworkUtils {
    
    private const val TAG = "NetworkUtils"
    
    /**
     * Get the local IP address of the device
     */
    fun getLocalIpAddress(context: Context): String? {
        try {
            // Try to get WiFi IP first
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifiManager?.connectionInfo?.let { wifiInfo ->
                val ipAddress = wifiInfo.ipAddress
                if (ipAddress != 0) {
                    return formatIpAddress(ipAddress)
                }
            }
            
            // Fallback to NetworkInterface enumeration
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    
                    if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                        val hostAddress = address.hostAddress
                        if (hostAddress != null && !hostAddress.startsWith("0.")) {
                            return hostAddress
                        }
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting IP address", e)
        }
        
        return null
    }
    
    /**
     * Format IP address from integer to string
     */
    private fun formatIpAddress(ipAddress: Int): String {
        return String.format(
            "%d.%d.%d.%d",
            ipAddress and 0xff,
            ipAddress shr 8 and 0xff,
            ipAddress shr 16 and 0xff,
            ipAddress shr 24 and 0xff
        )
    }
    
    /**
     * Check if device is connected to WiFi
     */
    fun isConnectedToWifi(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
    
    /**
     * Check if device has internet connection
     */
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
    
    /**
     * Get WiFi SSID
     */
    fun getWifiSsid(context: Context): String? {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        return wifiManager?.connectionInfo?.ssid?.replace("\"", "")
    }
}
