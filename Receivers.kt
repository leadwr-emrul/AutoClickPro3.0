package com.autoclicker.pro.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.autoclicker.pro.model.AutoSignal
import com.autoclicker.pro.service.AutoClickAccessibilityService
import com.autoclicker.pro.utils.AppPreferences
import com.google.gson.Gson

// ─── Boot Receiver ────────────────────────────────────────────────────────────
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {

            val prefs = AppPreferences(context)
            if (prefs.autoStartEnabled) {
                com.autoclicker.pro.service.AutomationForegroundService.start(context)
            }
        }
    }
}

// ─── Local Intent Signal Receiver ────────────────────────────────────────────
// External apps can send signals via:
// adb shell am broadcast -a com.autoclicker.pro.SIGNAL --es signal_json '{"signal":"BIG","count":5,"delay":500}'
class SignalReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.autoclicker.pro.SIGNAL") return

        val gson = Gson()
        val signal: AutoSignal? = when {
            intent.hasExtra("signal_json") -> {
                try {
                    gson.fromJson(intent.getStringExtra("signal_json"), AutoSignal::class.java)
                } catch (e: Exception) { null }
            }
            intent.hasExtra("signal") -> {
                AutoSignal(
                    signal = intent.getStringExtra("signal") ?: return,
                    count  = intent.getIntExtra("count", 1),
                    delay  = intent.getLongExtra("delay", 500L)
                )
            }
            else -> null
        }

        signal?.let {
            AutoClickAccessibilityService.instance?.processSignal(it)
        }
    }
}
