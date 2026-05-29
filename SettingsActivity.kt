package com.autoclicker.pro.ui

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.autoclicker.pro.R
import com.autoclicker.pro.utils.AppPreferences

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.title = "Settings"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        prefs = AppPreferences(this)

        val cbOverlay  = findViewById<CheckBox>(R.id.cb_overlay)
        val cbAutostart= findViewById<CheckBox>(R.id.cb_autostart)
        val cbDarkMode = findViewById<CheckBox>(R.id.cb_dark_mode)
        val sbLogSize  = findViewById<SeekBar>(R.id.sb_log_size)
        val tvLogSize  = findViewById<TextView>(R.id.tv_log_size)

        cbOverlay.isChecked   = prefs.overlayEnabled
        cbAutostart.isChecked = prefs.autoStartEnabled
        cbDarkMode.isChecked  = prefs.darkMode
        sbLogSize.progress    = prefs.maxLogSize
        tvLogSize.text        = "Max logs: ${prefs.maxLogSize}"

        cbOverlay.setOnCheckedChangeListener  { _, v -> prefs.overlayEnabled   = v }
        cbAutostart.setOnCheckedChangeListener{ _, v -> prefs.autoStartEnabled = v }
        cbDarkMode.setOnCheckedChangeListener { _, v -> prefs.darkMode         = v }
        sbLogSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, v: Int, u: Boolean) {
                prefs.maxLogSize = v
                tvLogSize.text = "Max logs: $v"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}

// ─── Transparent Coordinate Picker ───────────────────────────────────────────
class CoordinatePickerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Full screen transparent touch capture
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
            android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        setContentView(android.R.layout.activity_list_item)
        window.decorView.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                setResult(RESULT_OK, android.content.Intent().apply {
                    putExtra("x", event.rawX)
                    putExtra("y", event.rawY)
                })
                finish()
            }
            true
        }
    }
}
