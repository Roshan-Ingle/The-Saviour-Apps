package com.roshan.thesaviour.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.CancellationSignal
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.roshan.thesaviour.data.SaviourRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SosLocationAndSmsManager {

    @SuppressLint("MissingPermission")
    fun triggerEmergencySos(
        context: Context,
        reason: String,
        silentVibrate: Boolean = false
    ) {
        val appContext = context.applicationContext
        val repository = SaviourRepository(appContext)

        // Step 1: Turn on Mobile Data & GPS
        val toggleSummary = SystemToggleHelper.ensureDataAndGpsEnabled(appContext)

        if (!silentVibrate) {
            vibrateConfirmation(appContext)
        }

        val contacts = repository.getContacts()
        if (contacts.isEmpty()) {
            repository.setLastSosStatus("SOS triggered ($reason), but no contacts saved!")
            return
        }

        val hasLocationPerm = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasLocationPerm) {
            sendSmsToAll(
                context = appContext,
                reason = reason,
                location = null,
                extraNote = "Location permission not granted ($toggleSummary)"
            )
            return
        }

        // Step 2: Immediately check last known location so an instant alert goes out if needed,
        // and simultaneously request fresh high-accuracy GPS coordinates.
        val fusedClient = LocationServices.getFusedLocationProviderClient(appContext)
        val tokenSource = CancellationTokenSource()

        fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, tokenSource.token)
            .addOnSuccessListener { location: Location? ->
                val resolvedLocation = location ?: getBestLastKnownLocation(appContext)
                sendSmsToAll(
                    context = appContext,
                    reason = reason,
                    location = resolvedLocation,
                    extraNote = toggleSummary
                )
            }
            .addOnFailureListener {
                fetchFromPlatformLocationManager(appContext) { fallbackLoc ->
                    sendSmsToAll(
                        context = appContext,
                        reason = reason,
                        location = fallbackLoc,
                        extraNote = toggleSummary
                    )
                }
            }
    }

    @SuppressLint("MissingPermission")
    private fun fetchFromPlatformLocationManager(
        context: Context,
        onResult: (Location?) -> Unit
    ) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (lm == null) {
            onResult(null)
            return
        }
        val provider = when {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> LocationManager.PASSIVE_PROVIDER
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            lm.getCurrentLocation(
                provider,
                CancellationSignal(),
                context.mainExecutor
            ) { loc ->
                onResult(loc ?: getBestLastKnownLocation(context))
            }
        } else {
            onResult(getBestLastKnownLocation(context))
        }
    }

    @SuppressLint("MissingPermission")
    private fun getBestLastKnownLocation(context: Context): Location? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        var bestLocation: Location? = null
        for (provider in providers) {
            val loc = try {
                lm.getLastKnownLocation(provider)
            } catch (_: Exception) {
                null
            }
            if (loc != null && (bestLocation == null || loc.accuracy < bestLocation.accuracy)) {
                bestLocation = loc
            }
        }
        return bestLocation
    }

    private fun sendSmsToAll(
        context: Context,
        reason: String,
        location: Location?,
        extraNote: String
    ) {
        val repository = SaviourRepository(context)
        val contacts = repository.getContacts()
        val hasSmsPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        val timestamp = SimpleDateFormat("dd MMM HH:mm:ss", Locale.getDefault()).format(Date())
        val batteryPct = getBatteryPercentage(context)

        val mapsLink = if (location != null) {
            "https://maps.google.com/?q=${location.latitude},${location.longitude}"
        } else {
            "GPS locking... (Last tower/network active)"
        }

        val message = buildString {
            append("SOS ALERT [The Saviour]\n")
            append("Trigger: $reason\n")
            append("Location: $mapsLink\n")
            if (location != null) {
                append("Accuracy: ${location.accuracy.toInt()}m\n")
            }
            append("Battery: $batteryPct% | Time: $timestamp")
        }

        if (!hasSmsPerm) {
            repository.setLastSosStatus("SMS permission missing! ($extraNote)")
            return
        }

        val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }

        var sentCount = 0
        contacts.forEach { contact ->
            if (contact.phone.isNotBlank()) {
                try {
                    val parts = smsManager.divideMessage(message)
                    smsManager.sendMultipartTextMessage(
                        contact.phone,
                        null,
                        parts,
                        null,
                        null
                    )
                    sentCount++
                } catch (_: Exception) {
                }
            }
        }

        repository.setLastSosStatus(
            "Sent SOS ($reason) to $sentCount/${contacts.size} contacts at $timestamp"
        )
    }

    private fun getBatteryPercentage(context: Context): Int {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100) / scale else 100
    }

    private fun vibrateConfirmation(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 180, 100, 180), -1))
        } catch (_: Exception) {
        }
    }
}
