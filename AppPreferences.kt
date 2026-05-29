package com.autoclicker.pro.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.autoclicker.pro.model.*

class AppPreferences(context: Context) {

    companion object {
        private const val PREF_NAME     = "auto_click_prefs"
        private const val KEY_PROFILES  = "profiles"
        private const val KEY_ACTIVE_ID = "active_profile_id"
        private const val KEY_LOG_SIZE  = "max_log_size"
        private const val KEY_OVERLAY   = "overlay_enabled"
        private const val KEY_AUTOSTART = "autostart_enabled"
        private const val KEY_DARK_MODE = "dark_mode"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    // ── Profiles ──────────────────────────────────────────────────────────────
    fun getAllProfiles(): MutableList<ClickProfile> {
        val json = prefs.getString(KEY_PROFILES, null) ?: return mutableListOf(defaultProfile())
        return try {
            val type = object : TypeToken<MutableList<ClickProfile>>() {}.type
            gson.fromJson(json, type) ?: mutableListOf(defaultProfile())
        } catch (e: Exception) {
            mutableListOf(defaultProfile())
        }
    }

    fun saveProfile(profile: ClickProfile) {
        val profiles = getAllProfiles()
        val idx = profiles.indexOfFirst { it.id == profile.id }
        if (idx >= 0) profiles[idx] = profile else profiles.add(profile)
        prefs.edit().putString(KEY_PROFILES, gson.toJson(profiles)).apply()
    }

    fun deleteProfile(id: String) {
        val profiles = getAllProfiles().filter { it.id != id }.toMutableList()
        prefs.edit().putString(KEY_PROFILES, gson.toJson(profiles)).apply()
        if (getActiveProfileId() == id) {
            setActiveProfile(profiles.firstOrNull()?.id ?: "")
        }
    }

    fun getActiveProfile(): ClickProfile? {
        val id = getActiveProfileId()
        return getAllProfiles().firstOrNull { it.id == id }
            ?: getAllProfiles().firstOrNull()
    }

    fun setActiveProfile(id: String) {
        prefs.edit().putString(KEY_ACTIVE_ID, id).apply()
    }

    fun getActiveProfileId(): String =
        prefs.getString(KEY_ACTIVE_ID, "") ?: ""

    fun exportProfiles(): String = gson.toJson(getAllProfiles())

    fun importProfiles(json: String): Boolean {
        return try {
            val type = object : TypeToken<MutableList<ClickProfile>>() {}.type
            val profiles: MutableList<ClickProfile> = gson.fromJson(json, type)
            prefs.edit().putString(KEY_PROFILES, gson.toJson(profiles)).apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    // ── Settings ──────────────────────────────────────────────────────────────
    var maxLogSize: Int
        get() = prefs.getInt(KEY_LOG_SIZE, 500)
        set(v) = prefs.edit().putInt(KEY_LOG_SIZE, v).apply()

    var overlayEnabled: Boolean
        get() = prefs.getBoolean(KEY_OVERLAY, true)
        set(v) = prefs.edit().putBoolean(KEY_OVERLAY, v).apply()

    var autoStartEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTOSTART, false)
        set(v) = prefs.edit().putBoolean(KEY_AUTOSTART, v).apply()

    var darkMode: Boolean
        get() = prefs.getBoolean(KEY_DARK_MODE, true)
        set(v) = prefs.edit().putBoolean(KEY_DARK_MODE, v).apply()

    // ── Default Profile ───────────────────────────────────────────────────────
    private fun defaultProfile(): ClickProfile = ClickProfile(
        name = "Default Profile",
        positions = mutableMapOf(
            "BIG"     to ClickPosition("BIG",     "BIG Button",     0f, 0f),
            "SMALL"   to ClickPosition("SMALL",   "SMALL Button",   0f, 0f),
            "NUMBER"  to ClickPosition("NUMBER",  "Number Button",  0f, 0f),
            "CONFIRM" to ClickPosition("CONFIRM", "Confirm Button", 0f, 0f)
        )
    )
}
