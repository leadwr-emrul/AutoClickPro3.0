package com.autoclicker.pro.model

import com.google.gson.annotations.SerializedName

// ─── Signal Model ───────────────────────────────────────────────────────────
data class AutoSignal(
    @SerializedName("signal") val signal: String = "",
    @SerializedName("count")  val count: Int = 1,
    @SerializedName("delay")  val delay: Long = 500L,
    @SerializedName("target") val target: String? = null,
    @SerializedName("action") val action: String? = null,
    @SerializedName("extra")  val extra: Map<String, Any>? = null
)

// ─── Click Position ──────────────────────────────────────────────────────────
data class ClickPosition(
    val id: String = "",
    val name: String = "",
    val x: Float = 0f,
    val y: Float = 0f,
    val description: String = "",
    val enabled: Boolean = true
)

// ─── Click Profile ───────────────────────────────────────────────────────────
data class ClickProfile(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "Profile",
    val positions: MutableMap<String, ClickPosition> = mutableMapOf(),
    val delays: DelayConfig = DelayConfig(),
    val apiConfig: ApiConfig = ApiConfig(),
    val createdAt: Long = System.currentTimeMillis(),
    var isActive: Boolean = false
) {
    // Default positions
    fun getBigPosition(): ClickPosition? = positions["BIG"]
    fun getSmallPosition(): ClickPosition? = positions["SMALL"]
    fun getConfirmPosition(): ClickPosition? = positions["CONFIRM"]
    fun getNumberPosition(): ClickPosition? = positions["NUMBER"]
}

// ─── Delay Config ────────────────────────────────────────────────────────────
data class DelayConfig(
    val firstClickDelay: Long = 300L,
    val multiClickDelay: Long = 200L,
    val finalClickDelay: Long = 500L,
    val signalProcessDelay: Long = 100L,
    val retryDelay: Long = 1000L,
    val maxRetries: Int = 3
)

// ─── API Config ──────────────────────────────────────────────────────────────
data class ApiConfig(
    val type: ApiType = ApiType.REST,
    val url: String = "",
    val telegramToken: String = "",
    val telegramChatId: String = "",
    val websocketUrl: String = "",
    val pollInterval: Long = 2000L,
    val authToken: String = "",
    val autoReconnect: Boolean = true
)

enum class ApiType {
    REST, WEBSOCKET, TELEGRAM, FIREBASE, LOCAL_INTENT
}

// ─── Log Entry ───────────────────────────────────────────────────────────────
data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel = LogLevel.INFO,
    val message: String = "",
    val tag: String = "APP"
) {
    fun formattedTime(): String {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }
}

enum class LogLevel { DEBUG, INFO, SUCCESS, WARNING, ERROR }

// ─── Connection Status ───────────────────────────────────────────────────────
data class ConnectionStatus(
    val isConnected: Boolean = false,
    val type: ApiType = ApiType.REST,
    val message: String = "",
    val lastSignalTime: Long = 0L
)

// ─── Automation State ────────────────────────────────────────────────────────
data class AutomationState(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val currentSignal: AutoSignal? = null,
    val clickCount: Int = 0,
    val totalClicks: Int = 0,
    val lastError: String? = null
)

// ─── Gesture Action ──────────────────────────────────────────────────────────
data class GestureAction(
    val type: GestureType = GestureType.TAP,
    val x: Float = 0f,
    val y: Float = 0f,
    val duration: Long = 100L,
    val repeat: Int = 1,
    val delayBetween: Long = 200L
)

enum class GestureType { TAP, LONG_PRESS, SWIPE, SCROLL }
