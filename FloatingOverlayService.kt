package com.autoclicker.pro.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.*
import android.view.WindowManager.LayoutParams
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.autoclicker.pro.model.LogLevel
import com.autoclicker.pro.model.LogEntry
import com.autoclicker.pro.service.AutoClickAccessibilityService
import com.autoclicker.pro.service.AutomationForegroundService
import com.autoclicker.pro.utils.EventBus
import com.autoclicker.pro.R

class FloatingOverlayService : Service() {

    companion object {
        fun start(context: Context) {
            context.startService(Intent(context, FloatingOverlayService::class.java))
        }
        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingOverlayService::class.java))
        }
    }

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var coordinatePickerView: View? = null
    private var isExpanded = false
    private var isPickingCoordinate = false
    private var pickerCallback: ((Float, Float) -> Unit)? = null

    private var lastX = 0
    private var lastY = 0
    private var initialX = 0
    private var initialY = 0
    private var touchX = 0f
    private var touchY = 0f

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createFloatingView()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        floatingView?.let { windowManager.removeView(it) }
        coordinatePickerView?.let { windowManager.removeView(it) }
    }

    // ── Create Floating View ──────────────────────────────────────────────────
    private fun createFloatingView() {
        val inflater = LayoutInflater.from(this)
        floatingView = inflater.inflate(R.layout.floating_overlay, null)

        val params = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                LayoutParams.TYPE_PHONE,
            LayoutParams.FLAG_NOT_FOCUSABLE or LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 200
        }

        setupDragAndClick(floatingView!!, params)
        setupButtons()
        windowManager.addView(floatingView, params)
        log("Floating overlay created")
    }

    // ── Drag & Click Handling ─────────────────────────────────────────────────
    private fun setupDragAndClick(view: View, params: LayoutParams) {
        val dragHandle = view.findViewById<View>(R.id.drag_handle)

        dragHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - touchX).toInt()
                    params.y = initialY + (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(view, params)
                    true
                }
                else -> false
            }
        }
    }

    // ── Button Setup ──────────────────────────────────────────────────────────
    private fun setupButtons() {
        val view = floatingView ?: return

        // Toggle expand
        view.findViewById<View>(R.id.btn_toggle)?.setOnClickListener {
            toggleExpand()
        }

        // Start button
        view.findViewById<View>(R.id.btn_start)?.setOnClickListener {
            startAutomation()
        }

        // Stop button
        view.findViewById<View>(R.id.btn_stop)?.setOnClickListener {
            stopAutomation()
        }

        // Pick coordinate
        view.findViewById<View>(R.id.btn_pick)?.setOnClickListener {
            startCoordinatePicker { x, y ->
                EventBus.post(EventBus.Event.COORDINATE_PICKED, Pair(x, y))
                updateStatus("Picked: ${x.toInt()}, ${y.toInt()}")
            }
        }

        // Close button
        view.findViewById<View>(R.id.btn_close)?.setOnClickListener {
            stop(this)
        }
    }

    private fun toggleExpand() {
        isExpanded = !isExpanded
        floatingView?.apply {
            val panel = findViewById<LinearLayout>(R.id.expanded_panel)
            panel?.visibility = if (isExpanded) View.VISIBLE else View.GONE
        }
    }

    private fun startAutomation() {
        val service = AutoClickAccessibilityService.instance
        if (service == null) {
            updateStatus("Enable Accessibility!")
            return
        }
        service.startAutomation()
        AutomationForegroundService.start(this)
        updateStatus("Running ▶")
        updateButtonState(true)
    }

    private fun stopAutomation() {
        AutoClickAccessibilityService.instance?.stopAllAutomation()
        AutomationForegroundService.stop(this)
        updateStatus("Stopped ■")
        updateButtonState(false)
    }

    fun updateStatus(text: String) {
        floatingView?.post {
            floatingView?.findViewById<TextView>(R.id.tv_status)?.text = text
        }
    }

    private fun updateButtonState(isRunning: Boolean) {
        floatingView?.post {
            floatingView?.apply {
                findViewById<View>(R.id.btn_start)?.alpha = if (isRunning) 0.4f else 1f
                findViewById<View>(R.id.btn_stop)?.alpha  = if (isRunning) 1f else 0.4f
            }
        }
    }

    // ── Coordinate Picker ─────────────────────────────────────────────────────
    fun startCoordinatePicker(callback: (Float, Float) -> Unit) {
        if (isPickingCoordinate) return
        isPickingCoordinate = true
        pickerCallback = callback

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getMetrics(metrics)

        val pickerView = View(this).apply {
            setBackgroundColor(0x44FF0000.toInt())
        }

        val params = LayoutParams(
            metrics.widthPixels,
            metrics.heightPixels,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                LayoutParams.TYPE_PHONE,
            LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0; y = 0
        }

        pickerView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val x = event.rawX
                val y = event.rawY
                callback(x, y)
                cancelCoordinatePicker()
                updateStatus("Picked: ${x.toInt()},${y.toInt()}")
            }
            true
        }

        coordinatePickerView = pickerView
        windowManager.addView(pickerView, params)
        updateStatus("Tap to pick point...")
    }

    private fun cancelCoordinatePicker() {
        isPickingCoordinate = false
        pickerCallback = null
        coordinatePickerView?.let {
            windowManager.removeView(it)
            coordinatePickerView = null
        }
    }

    private fun log(msg: String) {
        EventBus.postLog(LogEntry(message = msg, level = LogLevel.DEBUG, tag = "Overlay"))
    }
}
