package com.autoclicker.pro.ui

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.autoclicker.pro.R
import com.autoclicker.pro.model.*
import com.autoclicker.pro.overlay.FloatingOverlayService
import com.autoclicker.pro.service.AutoClickAccessibilityService
import com.autoclicker.pro.service.AutomationForegroundService
import com.autoclicker.pro.utils.AppPreferences
import com.autoclicker.pro.utils.EventBus
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: AppPreferences
    private val logList = mutableListOf<LogEntry>()
    private lateinit var logAdapter: LogAdapter

    // UI Views
    private lateinit var tvServiceStatus: TextView
    private lateinit var tvConnectionStatus: TextView
    private lateinit var tvAutomationStatus: TextView
    private lateinit var btnStartStop: Button
    private lateinit var btnOpenAccessibility: Button
    private lateinit var btnOverlay: Button
    private lateinit var rvLogs: RecyclerView
    private lateinit var spinnerProfile: Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = AppPreferences(this)
        initViews()
        setupObservers()
        updateServiceStatus()
        loadProfiles()
        checkPermissions()
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
    }

    // ── Init ──────────────────────────────────────────────────────────────────
    private fun initViews() {
        tvServiceStatus     = findViewById(R.id.tv_service_status)
        tvConnectionStatus  = findViewById(R.id.tv_connection_status)
        tvAutomationStatus  = findViewById(R.id.tv_automation_status)
        btnStartStop        = findViewById(R.id.btn_start_stop)
        btnOpenAccessibility= findViewById(R.id.btn_open_accessibility)
        btnOverlay          = findViewById(R.id.btn_overlay)
        rvLogs              = findViewById(R.id.rv_logs)
        spinnerProfile      = findViewById(R.id.spinner_profile)

        // Log RecyclerView
        logAdapter = LogAdapter(logList)
        rvLogs.apply {
            adapter = logAdapter
            layoutManager = LinearLayoutManager(this@MainActivity).also {
                it.stackFromEnd = true
            }
        }

        // Buttons
        btnStartStop.setOnClickListener { toggleAutomation() }
        btnOpenAccessibility.setOnClickListener { openAccessibilitySettings() }
        btnOverlay.setOnClickListener { toggleOverlay() }

        findViewById<Button>(R.id.btn_positions).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        findViewById<Button>(R.id.btn_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<Button>(R.id.btn_clear_log).setOnClickListener {
            logList.clear()
            logAdapter.notifyDataSetChanged()
        }

        // Test signal button
        findViewById<Button>(R.id.btn_test)?.setOnClickListener {
            showTestSignalDialog()
        }
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            EventBus.logs.collectLatest { entry ->
                runOnUiThread { addLog(entry) }
            }
        }
        lifecycleScope.launch {
            EventBus.events.collectLatest { event ->
                runOnUiThread { handleEvent(event) }
            }
        }
        lifecycleScope.launch {
            EventBus.connectionStatus.collectLatest { connected ->
                runOnUiThread {
                    tvConnectionStatus.text = if (connected) "● Connected" else "○ Disconnected"
                    tvConnectionStatus.setTextColor(
                        if (connected) getColor(android.R.color.holo_green_light)
                        else getColor(android.R.color.holo_red_light)
                    )
                }
            }
        }
    }

    // ── Event Handler ─────────────────────────────────────────────────────────
    private fun handleEvent(event: EventBus.AppEvent) {
        when (event.event) {
            EventBus.Event.SERVICE_CONNECTED -> {
                updateServiceStatus()
                addLog(LogEntry(message = "Accessibility Service Connected ✓", level = LogLevel.SUCCESS))
            }
            EventBus.Event.SERVICE_DISCONNECTED -> {
                updateServiceStatus()
            }
            EventBus.Event.AUTOMATION_ENABLED -> {
                tvAutomationStatus.text = "● Running"
                tvAutomationStatus.setTextColor(getColor(android.R.color.holo_green_light))
                btnStartStop.text = "STOP"
                btnStartStop.setBackgroundColor(getColor(android.R.color.holo_red_dark))
            }
            EventBus.Event.AUTOMATION_DISABLED -> {
                tvAutomationStatus.text = "○ Stopped"
                tvAutomationStatus.setTextColor(getColor(android.R.color.holo_red_light))
                btnStartStop.text = "START"
                btnStartStop.setBackgroundColor(getColor(android.R.color.holo_green_dark))
            }
            EventBus.Event.SIGNAL_RECEIVED -> {
                val sig = event.data as? AutoSignal
                addLog(LogEntry(
                    message = "Signal: ${sig?.signal} ×${sig?.count}",
                    level = LogLevel.SUCCESS,
                    tag = "Signal"
                ))
            }
            else -> {}
        }
    }

    // ── Automation Control ────────────────────────────────────────────────────
    private fun toggleAutomation() {
        val service = AutoClickAccessibilityService.instance
        if (service == null) {
            showAccessibilityDialog()
            return
        }
        if (service.isActive()) {
            service.stopAllAutomation()
            AutomationForegroundService.stop(this)
        } else {
            service.startAutomation()
            AutomationForegroundService.start(this)
        }
    }

    // ── Overlay ───────────────────────────────────────────────────────────────
    private fun toggleOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission()
            return
        }
        FloatingOverlayService.start(this)
        addLog(LogEntry(message = "Floating overlay shown", level = LogLevel.INFO))
    }

    // ── Profile Spinner ───────────────────────────────────────────────────────
    private fun loadProfiles() {
        val profiles = prefs.getAllProfiles()
        val names = profiles.map { it.name }.toTypedArray()
        spinnerProfile.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)

        val activeIdx = profiles.indexOfFirst { it.id == prefs.getActiveProfileId() }
        if (activeIdx >= 0) spinnerProfile.setSelection(activeIdx)

        spinnerProfile.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                prefs.setActiveProfile(profiles[pos].id)
                addLog(LogEntry(message = "Profile: ${profiles[pos].name}", level = LogLevel.INFO))
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    // ── Permissions ───────────────────────────────────────────────────────────
    private fun checkPermissions() {
        if (!Settings.canDrawOverlays(this)) {
            addLog(LogEntry(message = "⚠ Overlay permission needed", level = LogLevel.WARNING))
        }
        if (!isAccessibilityEnabled()) {
            addLog(LogEntry(message = "⚠ Accessibility Service not enabled", level = LogLevel.WARNING))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(android.os.PowerManager::class.java)
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                addLog(LogEntry(message = "⚠ Battery optimization active - may affect background", level = LogLevel.WARNING))
            }
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        return AutoClickAccessibilityService.isRunning()
    }

    private fun updateServiceStatus() {
        val enabled = isAccessibilityEnabled()
        tvServiceStatus.text = if (enabled) "● Service ON" else "○ Service OFF"
        tvServiceStatus.setTextColor(
            if (enabled) getColor(android.R.color.holo_green_light)
            else getColor(android.R.color.holo_red_light)
        )
        btnOpenAccessibility.text = if (enabled) "Service Settings" else "Enable Service"
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun showAccessibilityDialog() {
        AlertDialog.Builder(this)
            .setTitle("Accessibility Service Required")
            .setMessage("Enable Auto Click Accessibility Service to perform automatic clicks.")
            .setPositiveButton("Open Settings") { _, _ -> openAccessibilitySettings() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Test Signal ───────────────────────────────────────────────────────────
    private fun showTestSignalDialog() {
        val signals = arrayOf("BIG", "SMALL", "Custom JSON")
        AlertDialog.Builder(this)
            .setTitle("Test Signal")
            .setItems(signals) { _, idx ->
                when (idx) {
                    0 -> sendTestSignal(AutoSignal("BIG", 5, 500))
                    1 -> sendTestSignal(AutoSignal("SMALL", 3, 300))
                    2 -> showCustomSignalInput()
                }
            }.show()
    }

    private fun showCustomSignalInput() {
        val input = EditText(this).apply {
            hint = """{"signal":"BIG","count":10,"delay":1000}"""
            setText("""{"signal":"BIG","count":5,"delay":500}""")
        }
        AlertDialog.Builder(this)
            .setTitle("Custom JSON Signal")
            .setView(input)
            .setPositiveButton("Send") { _, _ ->
                try {
                    val signal = com.google.gson.Gson().fromJson(input.text.toString(), AutoSignal::class.java)
                    sendTestSignal(signal)
                } catch (e: Exception) {
                    Toast.makeText(this, "Invalid JSON", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendTestSignal(signal: AutoSignal) {
        addLog(LogEntry(message = "Test: ${signal.signal} ×${signal.count}", level = LogLevel.INFO))
        AutoClickAccessibilityService.instance?.processSignal(signal)
            ?: Toast.makeText(this, "Enable Accessibility Service first", Toast.LENGTH_SHORT).show()
    }

    // ── Log ───────────────────────────────────────────────────────────────────
    private fun addLog(entry: LogEntry) {
        logList.add(entry)
        if (logList.size > prefs.maxLogSize) logList.removeAt(0)
        logAdapter.notifyItemInserted(logList.size - 1)
        rvLogs.scrollToPosition(logList.size - 1)
    }

    // ── Log Adapter ───────────────────────────────────────────────────────────
    inner class LogAdapter(private val items: List<LogEntry>) :
        RecyclerView.Adapter<LogAdapter.VH>() {

        inner class VH(v: android.view.View) : RecyclerView.ViewHolder(v) {
            val tvLog: TextView = v.findViewById(R.id.tv_log_entry)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, type: Int): VH {
            val v = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_log, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, pos: Int) {
            val entry = items[pos]
            val color = when (entry.level) {
                LogLevel.SUCCESS -> 0xFF00C853.toInt()
                LogLevel.ERROR   -> 0xFFFF1744.toInt()
                LogLevel.WARNING -> 0xFFFFAB00.toInt()
                LogLevel.DEBUG   -> 0xFF607D8B.toInt()
                LogLevel.INFO    -> 0xFFB0BEC5.toInt()
            }
            holder.tvLog.text = "${entry.formattedTime()} [${entry.tag}] ${entry.message}"
            holder.tvLog.setTextColor(color)
        }

        override fun getItemCount() = items.size
    }
}
