package com.example.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = context.getSharedPreferences(GestureAccessibilityService.PREFS, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(GestureAccessibilityService.KEY_GESTURES, false)
        if (enabled) {
            GestureForegroundService.start(context)
        }
    }
}
