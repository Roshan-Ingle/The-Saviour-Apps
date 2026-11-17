package com.roshan.thesaviour.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.roshan.thesaviour.data.SaviourRepository
import com.roshan.thesaviour.ui.FakeShutdownActivity
import com.roshan.thesaviour.util.SosLocationAndSmsManager

class SaviourAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var fakeOverlayView: View? = null
    private val cornerTapTimestamps = ArrayDeque<Long>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val repo = SaviourRepository(this)
        if (!repo.isProtectionEnabled() || !repo.isFakeShutdownEnabled()) return
        if (repo.isFakeShutdownActive()) return

        val pkgName = event.packageName?.toString() ?: ""
        val clsName = event.className?.toString() ?: ""

        // Inspect SystemUI or GlobalActions power dialog for Power Off / Restart menu
        if (pkgName.contains("systemui", ignoreCase = true) ||
            pkgName == "android" ||
            clsName.contains("GlobalActions", ignoreCase = true)
        ) {
            val rootNode = rootInActiveWindow ?: event.source ?: return
            if (containsPowerMenuKeywords(rootNode)) {
                interceptPowerMenuAndTriggerFakeShutdown()
            }
        }
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return false
        val repo = SaviourRepository(this)
        if (repo.isFakeShutdownActive()) {
            // Swallow volume and navigation keys while in Fake Switch-Off mode so phone appears dead
            return true
        }
        return super.onKeyEvent(event)
    }

    private fun containsPowerMenuKeywords(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val powerKeywords = listOf(
            "power off",
            "shut down",
            "switch off",
            "slide to power off",
            "tap to power off",
            "emergency call",
            "restart",
            "reboot"
        )

        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""

        // Require at least one explicit power-off phrase in SystemUI dialog
        if (powerKeywords.any { keyword -> text == keyword || desc == keyword || text.contains("power off") || desc.contains("power off") }) {
            return true
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (containsPowerMenuKeywords(child)) return true
        }
        return false
    }

    fun interceptPowerMenuAndTriggerFakeShutdown() {
        val repo = SaviourRepository(this)
        if (repo.isFakeShutdownActive()) return
        repo.setFakeShutdownActive(true)

        // 1. Dismiss the real system power menu immediately
        performGlobalAction(GLOBAL_ACTION_BACK)
        performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)

        // 2. Display Realistic Fake Switch-Off Screen (Window Overlay or Fullscreen Activity fallback)
        mainHandler.post {
            if (Settings.canDrawOverlays(this)) {
                showFakeShutdownOverlay()
            } else {
                val intent = Intent(this, FakeShutdownActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                startActivity(intent)
            }
        }

        // 3. Silently turn on Mobile Data & GPS and send emergency live location SMS to saved contacts
        SosLocationAndSmsManager.triggerEmergencySos(
            context = this,
            reason = "Unauthorized Switch-Off Attempt (Fake Shutdown Active)",
            silentVibrate = true
        )
    }

    fun showFakeShutdownOverlay() {
        if (fakeOverlayView != null) return
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val rootLayout = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            keepScreenOn = true
        }

        // Center "Shutting down..." spinner container shown for the first 2.5 seconds
        val shuttingDownBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val progress = ProgressBar(context).apply {
                isIndeterminate = true
            }
            val label = TextView(context).apply {
                text = "Shutting down..."
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                setPadding(0, 32, 0, 0)
            }
            addView(progress)
            addView(label)
        }

        val centerParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        )
        rootLayout.addView(shuttingDownBox, centerParams)

        // Secret owner unlock: Tap top-left corner 5 times rapidly within 2.5 seconds to exit Fake Shutdown
        rootLayout.setOnTouchListener { _, motionEvent ->
            if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                if (motionEvent.x < 260f && motionEvent.y < 260f) {
                    val now = SystemClock.elapsedRealtime()
                    cornerTapTimestamps.addLast(now)
                    while (cornerTapTimestamps.isNotEmpty() && now - cornerTapTimestamps.first() > 2500L) {
                        cornerTapTimestamps.removeFirst()
                    }
                    if (cornerTapTimestamps.size >= 5) {
                        cornerTapTimestamps.clear()
                        dismissFakeShutdownOverlay()
                    }
                }
            }
            true
        }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.OPAQUE
        ).apply {
            screenBrightness = 0.01f
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        try {
            wm.addView(rootLayout, params)
            fakeOverlayView = rootLayout

            // Realistic shutdown vibration after 2.4 seconds, then pitch-black dead screen
            mainHandler.postDelayed({
                shuttingDownBox.visibility = View.GONE
                vibrateShortShutdownPulse()
            }, 2400L)
        } catch (_: Exception) {
        }
    }

    fun dismissFakeShutdownOverlay() {
        val repo = SaviourRepository(this)
        repo.setFakeShutdownActive(false)
        val view = fakeOverlayView ?: return
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        try {
            wm.removeView(view)
        } catch (_: Exception) {
        }
        fakeOverlayView = null
    }

    /**
     * Fallback helper that opens Quick Settings briefly and clicks Location / Mobile Data tiles
     * if WRITE_SECURE_SETTINGS was not granted via ADB.
     */
    fun autoEnableQuickSettingsTiles(needGps: Boolean, needData: Boolean) {
        if (!needGps && !needData) return
        performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
        mainHandler.postDelayed({
            val root = rootInActiveWindow
            if (root != null) {
                if (needGps) {
                    clickTileByKeywords(root, listOf("Location", "GPS"))
                }
                if (needData) {
                    clickTileByKeywords(root, listOf("Mobile data", "Data network", "Internet"))
                }
            }
            mainHandler.postDelayed({
                performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
            }, 700L)
        }, 650L)
    }

    private fun clickTileByKeywords(node: AccessibilityNodeInfo?, keywords: List<String>): Boolean {
        if (node == null) return false
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        if (keywords.any { kw -> text.contains(kw, ignoreCase = true) || desc.contains(kw, ignoreCase = true) }) {
            var clickable: AccessibilityNodeInfo? = node
            while (clickable != null && !clickable.isClickable) {
                clickable = clickable.parent
            }
            if (clickable != null) {
                clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
        }
        for (i in 0 until node.childCount) {
            if (clickTileByKeywords(node.getChild(i), keywords)) return true
        }
        return false
    }

    private fun vibrateShortShutdownPulse() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.vibrate(VibrationEffect.createOneShot(160L, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (instance == this) {
            instance = null
        }
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: SaviourAccessibilityService? = null
            private set

        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabledServices.contains(context.packageName, ignoreCase = true)
        }
    }
}
