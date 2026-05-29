package com.autoclicker.pro.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.autoclicker.pro.R
import com.autoclicker.pro.model.LogEntry
import com.autoclicker.pro.model.LogLevel
import com.autoclicker.pro.ui.MainActivity
import com.autoclicker.pro.utils.EventBus

class AutomationForegroundService : Service() {

    companion object {
        const val CHANNEL_ID   = "auto_click_channel"
        const val CHANNEL_NAME = "Auto Click Service"
        const val NOTIF_ID     = 1001
        const val ACTION_START = "START_FOREGROUND"
        const val ACTION_STOP  = "STOP_FOREGROUND"

        fun start(context: Context) {
            val intent = Intent(context, AutomationForegroundService::class.java)
                .apply { action = ACTION_START }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AutomationForegroundService::class.java)
                .apply { action = ACTION_STOP }
            context.startService(intent)
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIF_ID, buildNotification("Running..."))
                acquireWakeLock()
                startDataReceiver()
                EventBus.postLog(LogEntry(
                    message = "Foreground service started",
                    level = LogLevel.SUCCESS,
                    tag = "FGService"
                ))
            }
            ACTION_STOP -> {
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        stopDataReceiver()
    }

    // ── Notification ──────────────────────────────────────────────────────────
    private fun buildNotification(status: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, AutomationForegroundService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Auto Click Active")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopIntent)
            .build()
    }

    fun updateNotification(status: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(status))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Auto Click Automation Service"
                setShowBadge(false)
            }
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    // ── Wake Lock ─────────────────────────────────────────────────────────────
    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AutoClick::WakeLock"
        ).apply {
            acquire(24 * 60 * 60 * 1000L) // 24 hours max
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    // ── Data Receiver ─────────────────────────────────────────────────────────
    private fun startDataReceiver() {
        val intent = Intent(this, DataReceiverService::class.java)
            .apply { action = DataReceiverService.ACTION_START }
        startService(intent)
    }

    private fun stopDataReceiver() {
        val intent = Intent(this, DataReceiverService::class.java)
            .apply { action = DataReceiverService.ACTION_STOP }
        startService(intent)
    }
}
