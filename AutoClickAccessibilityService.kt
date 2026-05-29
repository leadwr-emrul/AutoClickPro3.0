package com.autoclicker.pro.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.autoclicker.pro.model.*
import com.autoclicker.pro.utils.AppPreferences
import com.autoclicker.pro.utils.EventBus
import kotlinx.coroutines.*

class AutoClickAccessibilityService : AccessibilityService() {

    companion object {
        const val TAG = "AutoClickService"
        const val ACTION_PERFORM_CLICK = "com.autoclicker.pro.PERFORM_CLICK"
        const val ACTION_STOP_ALL    = "com.autoclicker.pro.STOP_ALL"
        const val EXTRA_SIGNAL       = "signal_data"

        @Volatile var instance: AutoClickAccessibilityService? = null
        fun isRunning() = instance != null
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isAutomationActive = false
    private var currentJob: Job? = null
    private lateinit var prefs: AppPreferences

    // ── Broadcast Receiver ────────────────────────────────────────────────────
    private val signalReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_PERFORM_CLICK -> {
                    val json = intent.getStringExtra(EXTRA_SIGNAL) ?: return
                    try {
                        val signal = com.google.gson.Gson().fromJson(json, AutoSignal::class.java)
                        processSignal(signal)
                    } catch (e: Exception) {
                        log("Signal parse error: ${e.message}", LogLevel.ERROR)
                    }
                }
                ACTION_STOP_ALL -> stopAllAutomation()
            }
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        prefs = AppPreferences(this)
        registerReceiver()
        log("Accessibility Service Connected ✓", LogLevel.SUCCESS)
        EventBus.post(EventBus.Event.SERVICE_CONNECTED)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Handle window content changes for node detection
        event?.let {
            if (it.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                it.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                EventBus.post(EventBus.Event.WINDOW_CHANGED, it.packageName?.toString())
            }
        }
    }

    override fun onInterrupt() {
        log("Service Interrupted", LogLevel.WARNING)
        stopAllAutomation()
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        unregisterReceiverSafe()
        serviceScope.cancel()
        EventBus.post(EventBus.Event.SERVICE_DISCONNECTED)
        log("Accessibility Service Destroyed", LogLevel.WARNING)
    }

    // ── Signal Processing ─────────────────────────────────────────────────────
    fun processSignal(signal: AutoSignal) {
        if (!isAutomationActive) {
            log("Automation not active, ignoring signal: ${signal.signal}", LogLevel.WARNING)
            return
        }
        currentJob?.cancel()
        currentJob = serviceScope.launch {
            performAutomation(signal)
        }
    }

    private suspend fun performAutomation(signal: AutoSignal) {
        val profile = prefs.getActiveProfile() ?: run {
            log("No active profile found!", LogLevel.ERROR)
            return
        }
        val delays = profile.delays
        log("Processing signal: ${signal.signal} × ${signal.count}", LogLevel.INFO)
        EventBus.post(EventBus.Event.AUTOMATION_START, signal)

        try {
            // Step 1: Signal delay
            delay(delays.signalProcessDelay)

            // Step 2: First action based on signal
            when (signal.signal.uppercase()) {
                "BIG" -> {
                    profile.getBigPosition()?.let { pos ->
                        delay(delays.firstClickDelay)
                        performGestureClick(pos.x, pos.y)
                        log("Clicked BIG at (${pos.x}, ${pos.y})", LogLevel.SUCCESS)
                    } ?: log("BIG position not set!", LogLevel.ERROR)
                }
                "SMALL" -> {
                    profile.getSmallPosition()?.let { pos ->
                        delay(delays.firstClickDelay)
                        performGestureClick(pos.x, pos.y)
                        log("Clicked SMALL at (${pos.x}, ${pos.y})", LogLevel.SUCCESS)
                    } ?: log("SMALL position not set!", LogLevel.ERROR)
                }
                else -> {
                    // Try to find by custom signal name
                    profile.positions[signal.signal.uppercase()]?.let { pos ->
                        delay(delays.firstClickDelay)
                        performGestureClick(pos.x, pos.y)
                        log("Clicked ${signal.signal} at (${pos.x}, ${pos.y})", LogLevel.SUCCESS)
                    } ?: log("Unknown signal: ${signal.signal}", LogLevel.WARNING)
                }
            }

            // Step 3: Number/Count clicks
            val count = if (signal.count > 0) signal.count else 1
            val numberPos = profile.getNumberPosition()
            if (numberPos != null && count > 0) {
                repeat(count) { i ->
                    delay(delays.multiClickDelay)
                    performGestureClick(numberPos.x, numberPos.y)
                    log("Number click ${i + 1}/$count", LogLevel.DEBUG)
                    EventBus.post(EventBus.Event.CLICK_PROGRESS, i + 1)
                }
            }

            // Step 4: Signal delay
            val signalDelay = if (signal.delay > 0) signal.delay else delays.finalClickDelay
            log("Waiting ${signalDelay}ms before confirm...", LogLevel.DEBUG)
            delay(signalDelay)

            // Step 5: Confirm click
            profile.getConfirmPosition()?.let { pos ->
                performGestureClick(pos.x, pos.y)
                log("Clicked CONFIRM at (${pos.x}, ${pos.y})", LogLevel.SUCCESS)
            } ?: log("CONFIRM position not set!", LogLevel.WARNING)

            log("Automation complete ✓", LogLevel.SUCCESS)
            EventBus.post(EventBus.Event.AUTOMATION_COMPLETE, signal)

        } catch (e: CancellationException) {
            log("Automation cancelled", LogLevel.WARNING)
        } catch (e: Exception) {
            log("Automation error: ${e.message}", LogLevel.ERROR)
            EventBus.post(EventBus.Event.AUTOMATION_ERROR, e.message)
        }
    }

    // ── Gesture Click ─────────────────────────────────────────────────────────
    fun performGestureClick(x: Float, y: Float, duration: Long = 100L): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(path, 0, duration)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) {
                    log("Gesture completed at ($x, $y)", LogLevel.DEBUG)
                }
                override fun onCancelled(gestureDescription: GestureDescription) {
                    log("Gesture cancelled at ($x, $y)", LogLevel.WARNING)
                }
            }, null)
        } else {
            // Fallback for older Android: find node at position
            clickNodeAt(x, y)
        }
    }

    fun performLongPress(x: Float, y: Float, duration: Long = 1000L): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(path, 0, duration)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            return dispatchGesture(gesture, null, null)
        }
        return false
    }

    fun performSwipe(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long = 300L): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = Path().apply {
                moveTo(x1, y1)
                lineTo(x2, y2)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, duration)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            return dispatchGesture(gesture, null, null)
        }
        return false
    }

    // ── Node-based Click (Fallback) ───────────────────────────────────────────
    private fun clickNodeAt(x: Float, y: Float): Boolean {
        val root = rootInActiveWindow ?: return false
        return findAndClickNode(root, x.toInt(), y.toInt())
    }

    private fun findAndClickNode(node: AccessibilityNodeInfo, x: Int, y: Int): Boolean {
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        if (rect.contains(x, y)) {
            if (node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findAndClickNode(child, x, y)) return true
        }
        return false
    }

    // ── Node Text Click ───────────────────────────────────────────────────────
    fun clickNodeByText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(text)
        if (nodes.isNullOrEmpty()) return false
        nodes.firstOrNull()?.let {
            it.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            log("Clicked node with text: $text", LogLevel.SUCCESS)
            return true
        }
        return false
    }

    fun clickNodeById(viewId: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
        if (nodes.isNullOrEmpty()) return false
        nodes.firstOrNull()?.let {
            it.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            log("Clicked node by ID: $viewId", LogLevel.SUCCESS)
            return true
        }
        return false
    }

    fun typeText(viewId: String, text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
        if (nodes.isNullOrEmpty()) return false
        nodes.firstOrNull()?.let {
            val args = Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            it.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            return true
        }
        return false
    }

    // ── Automation Control ────────────────────────────────────────────────────
    fun startAutomation() {
        isAutomationActive = true
        log("Automation STARTED", LogLevel.SUCCESS)
        EventBus.post(EventBus.Event.AUTOMATION_ENABLED)
    }

    fun stopAllAutomation() {
        isAutomationActive = false
        currentJob?.cancel()
        log("Automation STOPPED", LogLevel.WARNING)
        EventBus.post(EventBus.Event.AUTOMATION_DISABLED)
    }

    fun isActive() = isAutomationActive

    // ── Helper ────────────────────────────────────────────────────────────────
    private fun registerReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_PERFORM_CLICK)
            addAction(ACTION_STOP_ALL)
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(signalReceiver, filter)
    }

    private fun unregisterReceiverSafe() {
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(signalReceiver)
        } catch (e: Exception) { /* ignore */ }
    }

    private fun log(msg: String, level: LogLevel = LogLevel.INFO) {
        Log.d(TAG, msg)
        EventBus.postLog(LogEntry(message = msg, level = level, tag = TAG))
    }
}
