package com.roshan.thesaviour.util

import android.content.Context
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import com.roshan.thesaviour.service.SaviourAccessibilityService

object SystemToggleHelper {

    /**
     * Attempts to force-enable GPS (High Accuracy Location) and Mobile Data / Network connectivity
     * immediately when Triple Power Button SOS or Fake Switch-Off is triggered.
     */
    fun ensureDataAndGpsEnabled(context: Context): String {
        val results = mutableListOf<String>()

        val gpsEnabled = enableGps(context)
        results.add(if (gpsEnabled) "GPS: ON" else "GPS: Requested via Quick Settings")

        val dataEnabled = enableMobileData(context)
        results.add(if (dataEnabled) "Data: ON" else "Data: Requested via Quick Settings")

        // If either GPS or Mobile Data is still off, instruct our AccessibilityService
        // to auto-toggle Quick Settings tiles silently in the background.
        if (!isLocationEnabled(context) || !isNetworkConnected(context)) {
            SaviourAccessibilityService.instance?.autoEnableQuickSettingsTiles(
                needGps = !isLocationEnabled(context),
                needData = !isNetworkConnected(context)
            )
        }

        return results.joinToString(" | ")
    }

    fun isLocationEnabled(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lm.isLocationEnabled
        } else {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    fun isNetworkConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    @Suppress("DEPRECATION")
    private fun enableGps(context: Context): Boolean {
        if (isLocationEnabled(context)) return true
        return try {
            Settings.Secure.putInt(
                context.contentResolver,
                Settings.Secure.LOCATION_MODE,
                Settings.Secure.LOCATION_MODE_HIGH_ACCURACY
            )
            isLocationEnabled(context)
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun enableMobileData(context: Context): Boolean {
        if (isNetworkConnected(context)) return true
        return try {
            Settings.Global.putInt(context.contentResolver, "mobile_data", 1)
            Runtime.getRuntime().exec("svc data enable")
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifiManager?.isWifiEnabled = true
            isNetworkConnected(context)
        } catch (_: Exception) {
            false
        }
    }
}
