package com.roshan.thesaviour.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.roshan.thesaviour.data.SaviourRepository
import com.roshan.thesaviour.service.SaviourForegroundService

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val repo = SaviourRepository(context)
        repo.setFakeShutdownActive(false)
        if (repo.isProtectionEnabled()) {
            SaviourForegroundService.startService(context)
        }
    }
}
