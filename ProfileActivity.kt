package com.autoclicker.pro.ui

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.autoclicker.pro.R
import com.autoclicker.pro.model.*
import com.autoclicker.pro.overlay.FloatingOverlayService
import com.autoclicker.pro.utils.AppPreferences
import com.autoclicker.pro.utils.EventBus
import kotlinx.coroutines.*

class ProfileActivity : AppCompatActivity() {

    private lateinit var prefs: AppPreferences
    private lateinit var profile: ClickProfile
    private val scope = CoroutineScope(Dispatchers.Main)

    // Position fields
    private lateinit var etBigX: EditText
    private lateinit var etBigY: EditText
    private lateinit var etSmallX: EditText
    private lateinit var etSmallY: EditText
    private lateinit var etNumberX: EditText
    private lateinit var etNumberY: EditText
    private lateinit var etConfirmX: EditText
    private lateinit var etConfirmY: EditText

    // Delay fields
    private lateinit var etFirstDelay: EditText
    private lateinit var etMultiDelay: EditText
    private lateinit var etFinalDelay: EditText

    // API Config fields
    private lateinit var spinnerApiType: Spinner
    private lateinit var etApiUrl: EditText
    private lateinit var etTelegramToken: EditText
    private lateinit var etTelegramChatId: EditText
    private lateinit var etWsUrl: EditText
    private lateinit var etPollInterval: EditText
    private lateinit var etAuthToken: EditText
    private lateinit var cbAutoReconnect: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        prefs = AppPreferences(this)
        profile = prefs.getActiveProfile() ?: ClickProfile()

        supportActionBar?.title = "Profile: ${profile.name}"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        initViews()
        loadProfileData()
        setupButtons()
    }

    private fun initViews() {
        etBigX    = findViewById(R.id.et_big_x)
        etBigY    = findViewById(R.id.et_big_y)
        etSmallX  = findViewById(R.id.et_small_x)
        etSmallY  = findViewById(R.id.et_small_y)
        etNumberX = findViewById(R.id.et_number_x)
        etNumberY = findViewById(R.id.et_number_y)
        etConfirmX= findViewById(R.id.et_confirm_x)
        etConfirmY= findViewById(R.id.et_confirm_y)

        etFirstDelay = findViewById(R.id.et_first_delay)
        etMultiDelay = findViewById(R.id.et_multi_delay)
        etFinalDelay = findViewById(R.id.et_final_delay)

        spinnerApiType     = findViewById(R.id.spinner_api_type)
        etApiUrl           = findViewById(R.id.et_api_url)
        etTelegramToken    = findViewById(R.id.et_telegram_token)
        etTelegramChatId   = findViewById(R.id.et_telegram_chat_id)
        etWsUrl            = findViewById(R.id.et_ws_url)
        etPollInterval     = findViewById(R.id.et_poll_interval)
        etAuthToken        = findViewById(R.id.et_auth_token)
        cbAutoReconnect    = findViewById(R.id.cb_auto_reconnect)

        val types = ApiType.values().map { it.name }.toTypedArray()
        spinnerApiType.adapter = ArrayAdapter(this,
            android.R.layout.simple_spinner_dropdown_item, types)
    }

    private fun loadProfileData() {
        // Positions
        fun setPos(key: String, xField: EditText, yField: EditText) {
            profile.positions[key]?.let {
                xField.setText(it.x.toInt().toString())
                yField.setText(it.y.toInt().toString())
            }
        }
        setPos("BIG",     etBigX,    etBigY)
        setPos("SMALL",   etSmallX,  etSmallY)
        setPos("NUMBER",  etNumberX, etNumberY)
        setPos("CONFIRM", etConfirmX, etConfirmY)

        // Delays
        etFirstDelay.setText(profile.delays.firstClickDelay.toString())
        etMultiDelay.setText(profile.delays.multiClickDelay.toString())
        etFinalDelay.setText(profile.delays.finalClickDelay.toString())

        // API Config
        val apiIdx = ApiType.values().indexOf(profile.apiConfig.type)
        spinnerApiType.setSelection(maxOf(0, apiIdx))
        etApiUrl.setText(profile.apiConfig.url)
        etTelegramToken.setText(profile.apiConfig.telegramToken)
        etTelegramChatId.setText(profile.apiConfig.telegramChatId)
        etWsUrl.setText(profile.apiConfig.websocketUrl)
        etPollInterval.setText(profile.apiConfig.pollInterval.toString())
        etAuthToken.setText(profile.apiConfig.authToken)
        cbAutoReconnect.isChecked = profile.apiConfig.autoReconnect
    }

    private fun setupButtons() {
        // Pick buttons
        setupPickButton(R.id.btn_pick_big, etBigX, etBigY, "BIG")
        setupPickButton(R.id.btn_pick_small, etSmallX, etSmallY, "SMALL")
        setupPickButton(R.id.btn_pick_number, etNumberX, etNumberY, "NUMBER")
        setupPickButton(R.id.btn_pick_confirm, etConfirmX, etConfirmY, "CONFIRM")

        // Save
        findViewById<Button>(R.id.btn_save).setOnClickListener {
            saveProfile()
            Toast.makeText(this, "Saved ✓", Toast.LENGTH_SHORT).show()
        }

        // Export
        findViewById<Button>(R.id.btn_export).setOnClickListener {
            val json = prefs.exportProfiles()
            showShareDialog(json)
        }

        // Import
        findViewById<Button>(R.id.btn_import).setOnClickListener {
            showImportDialog()
        }

        // New profile
        findViewById<Button>(R.id.btn_new_profile).setOnClickListener {
            showNewProfileDialog()
        }
    }

    private fun setupPickButton(btnId: Int, xField: EditText, yField: EditText, label: String) {
        findViewById<Button>(btnId)?.setOnClickListener {
            FloatingOverlayService.start(this)
            Toast.makeText(this, "Tap anywhere to pick $label position", Toast.LENGTH_SHORT).show()

            scope.launch {
                EventBus.events.collect { event ->
                    if (event.event == EventBus.Event.COORDINATE_PICKED) {
                        @Suppress("UNCHECKED_CAST")
                        val pair = event.data as? Pair<Float, Float> ?: return@collect
                        xField.setText(pair.first.toInt().toString())
                        yField.setText(pair.second.toInt().toString())
                        cancel()
                    }
                }
            }
        }
    }

    private fun saveProfile() {
        fun getPos(key: String, xField: EditText, yField: EditText, name: String): ClickPosition {
            return ClickPosition(
                id = key, name = name,
                x = xField.text.toString().toFloatOrNull() ?: 0f,
                y = yField.text.toString().toFloatOrNull() ?: 0f
            )
        }

        val updatedProfile = profile.copy(
            positions = mutableMapOf(
                "BIG"     to getPos("BIG",     etBigX,    etBigY,    "BIG Button"),
                "SMALL"   to getPos("SMALL",   etSmallX,  etSmallY,  "SMALL Button"),
                "NUMBER"  to getPos("NUMBER",  etNumberX, etNumberY, "Number Button"),
                "CONFIRM" to getPos("CONFIRM", etConfirmX, etConfirmY,"Confirm Button")
            ),
            delays = DelayConfig(
                firstClickDelay = etFirstDelay.text.toString().toLongOrNull() ?: 300L,
                multiClickDelay = etMultiDelay.text.toString().toLongOrNull() ?: 200L,
                finalClickDelay = etFinalDelay.text.toString().toLongOrNull() ?: 500L
            ),
            apiConfig = ApiConfig(
                type          = ApiType.values()[spinnerApiType.selectedItemPosition],
                url           = etApiUrl.text.toString(),
                telegramToken = etTelegramToken.text.toString(),
                telegramChatId= etTelegramChatId.text.toString(),
                websocketUrl  = etWsUrl.text.toString(),
                pollInterval  = etPollInterval.text.toString().toLongOrNull() ?: 2000L,
                authToken     = etAuthToken.text.toString(),
                autoReconnect = cbAutoReconnect.isChecked
            )
        )
        prefs.saveProfile(updatedProfile)
        prefs.setActiveProfile(updatedProfile.id)
        profile = updatedProfile
        EventBus.post(EventBus.Event.PROFILE_CHANGED)
    }

    private fun showNewProfileDialog() {
        val input = EditText(this).apply { hint = "Profile name" }
        AlertDialog.Builder(this)
            .setTitle("New Profile")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().ifBlank { "Profile" }
                val newProfile = ClickProfile(name = name)
                prefs.saveProfile(newProfile)
                prefs.setActiveProfile(newProfile.id)
                profile = newProfile
                loadProfileData()
                supportActionBar?.title = "Profile: $name"
                Toast.makeText(this, "Created: $name", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showShareDialog(json: String) {
        AlertDialog.Builder(this)
            .setTitle("Export Profiles")
            .setMessage(json)
            .setPositiveButton("Copy") { _, _ ->
                val clipboard = getSystemService(android.content.ClipboardManager::class.java)
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("profiles", json))
                Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showImportDialog() {
        val input = EditText(this).apply { hint = "Paste JSON here" }
        AlertDialog.Builder(this)
            .setTitle("Import Profiles")
            .setView(input)
            .setPositiveButton("Import") { _, _ ->
                val success = prefs.importProfiles(input.text.toString())
                Toast.makeText(this,
                    if (success) "Imported ✓" else "Invalid JSON",
                    Toast.LENGTH_SHORT).show()
                if (success) loadProfileData()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
