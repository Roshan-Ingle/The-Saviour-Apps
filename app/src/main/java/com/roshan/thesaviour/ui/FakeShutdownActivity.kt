package com.roshan.thesaviour.ui

import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roshan.thesaviour.data.SaviourRepository
import kotlinx.coroutines.delay

class FakeShutdownActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        val lp = window.attributes
        lp.screenBrightness = 0.01f
        window.attributes = lp

        hideSystemBars()

        setContent {
            var isShuttingDownAnim by remember { mutableStateOf(true) }
            var tapCount by remember { mutableIntStateOf(0) }
            var firstTapTime by remember { mutableLongStateOf(0L) }

            BackHandler(enabled = true) {
                // Block back button during fake switch-off
            }

            LaunchedEffect(Unit) {
                delay(2400L)
                isShuttingDownAnim = false
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            // Secret owner exit: 5 taps in top-left corner within 2.5 seconds
                            if (offset.x < 300f && offset.y < 300f) {
                                val now = SystemClock.elapsedRealtime()
                                if (now - firstTapTime > 2500L) {
                                    tapCount = 1
                                    firstTapTime = now
                                } else {
                                    tapCount++
                                }
                                if (tapCount >= 5) {
                                    SaviourRepository(this@FakeShutdownActivity).setFakeShutdownActive(false)
                                    finish()
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isShuttingDownAnim) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "Shutting down...",
                            color = Color.White,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                    or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                )
        }
    }
}
