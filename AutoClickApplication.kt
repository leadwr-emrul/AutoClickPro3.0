package com.autoclicker.pro

import android.app.Application
import android.content.Intent
import com.autoclicker.pro.utils.AppPreferences

class AutoClickApplication : Application() {

    lateinit var prefs: AppPreferences
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = AppPreferences(this)
    }
}
