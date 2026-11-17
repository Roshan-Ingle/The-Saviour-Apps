package com.roshan.thesaviour.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.roshan.thesaviour.MainActivity
import com.roshan.thesaviour.R
import com.roshan.thesaviour.data.SaviourRepository
import com.roshan.thesaviour.util.SosLocationAndSmsManager

class SaviourForegroundService : Service() {

    private val powerTapTimestamps = ArrayDeque<Long>()

    private val screenToggleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            if (action == Intent.ACTION_SCREEN_ON || action == Intent.ACTION_SCREEN_OFF) {
                val repo = SaviourRepository(context)
                if (!repo.isProtectionEnabled()) return
                registerPowerButtonTap(context)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        try {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Throwable) {
        }

        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
            registerReceiver(screenToggleReceiver, filter)
        } catch (_: Throwable) {
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TRIGGER_TEST_SOS -> {
                SosLocationAndSmsManager.triggerEmergencySos(
                    context = this,
                    reason = "Manual SOS Test"
                )
            }
        }
        return START_STICKY
    }

    private fun registerPowerButtonTap(context: Context) {
        val now = SystemClock.elapsedRealtime()
        powerTapTimestamps.addLast(now)

        while (powerTapTimestamps.isNotEmpty() && now - powerTapTimestamps.first() > TRIPLE_TAP_WINDOW_MS) {
            powerTapTimestamps.removeFirst()
        }

        if (powerTapTimestamps.size >= 3) {
            powerTapTimestamps.clear()
            SosLocationAndSmsManager.triggerEmergencySos(
                context = context,
                reason = "Triple Power Button Tap"
            )
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(screenToggleReceiver)
        } catch (_: Throwable) {
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "The Saviour Active Protection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors triple power button SOS and fake switch-off protection"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_saviour_shield)
            .setContentTitle("The Saviour Guard Active")
            .setContentText("Triple power tap SOS & Fake Switch-Off protection ready")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "saviour_guard_channel"
        private const val NOTIFICATION_ID = 1001
        private const val TRIPLE_TAP_WINDOW_MS = 2400L

        const val ACTION_TRIGGER_TEST_SOS = "com.roshan.thesaviour.ACTION_TRIGGER_TEST_SOS"

        fun startService(context: Context) {
            try {
                val intent = Intent(context, SaviourForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Throwable) {
            }
        }
    }
}
