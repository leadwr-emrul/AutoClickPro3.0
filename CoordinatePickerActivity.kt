package com.autoclicker.pro.ui

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.autoclicker.pro.utils.EventBus

/**
 * Transparent full-screen activity for picking screen coordinates.
 * User taps the screen to select a coordinate, which is broadcast via EventBus.
 */
class CoordinatePickerActivity : Activity() {

    private var positionKey: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        positionKey = intent.getStringExtra("position_key") ?: ""

        val hint = TextView(this).apply {
            text = "টার্গেট স্থানে ট্যাপ করুন\nTap where you want to click"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 18f
            setPadding(32, 32, 32, 32)
            gravity = Gravity.CENTER
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0x88000000.toInt())
            addView(hint)
        }

        setContentView(layout)

        layout.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val x = event.rawX.toInt()
                val y = event.rawY.toInt()
                // Post coordinate picked event with key and coords as pair
                EventBus.post(EventBus.Event.COORDINATE_PICKED, "$positionKey:$x:$y")
                Toast.makeText(this, "Position saved: ($x, $y)", Toast.LENGTH_SHORT).show()
                finish()
            }
            true
        }
    }
}
