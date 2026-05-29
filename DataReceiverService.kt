package com.autoclicker.pro.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.autoclicker.pro.model.*
import com.autoclicker.pro.utils.AppPreferences
import com.autoclicker.pro.utils.EventBus
import com.google.gson.Gson
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URI
import javax.net.ssl.HttpsURLConnection

class DataReceiverService : Service() {

    companion object {
        const val TAG = "DataReceiverService"
        const val ACTION_START = "START_RECEIVER"
        const val ACTION_STOP  = "STOP_RECEIVER"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = Gson()
    private lateinit var prefs: AppPreferences

    private var wsJob: Job? = null
    private var pollJob: Job? = null
    private var telegramJob: Job? = null
    private var lastTelegramUpdateId: Long = 0L

    override fun onCreate() {
        super.onCreate()
        prefs = AppPreferences(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startReceiving()
            ACTION_STOP  -> stopReceiving()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Start Receiving ───────────────────────────────────────────────────────
    private fun startReceiving() {
        val profile = prefs.getActiveProfile() ?: return
        val config = profile.apiConfig

        log("Starting receiver: ${config.type}", LogLevel.INFO)
        EventBus.post(EventBus.Event.RECEIVER_STARTED, config.type)

        when (config.type) {
            ApiType.REST      -> startRestPolling(config)
            ApiType.WEBSOCKET -> startWebSocket(config)
            ApiType.TELEGRAM  -> startTelegramPolling(config)
            ApiType.FIREBASE  -> startFirebaseListener(config)
            ApiType.LOCAL_INTENT -> log("Local Intent mode - no polling needed", LogLevel.INFO)
        }
    }

    private fun stopReceiving() {
        wsJob?.cancel()
        pollJob?.cancel()
        telegramJob?.cancel()
        log("Receiver stopped", LogLevel.WARNING)
        EventBus.post(EventBus.Event.RECEIVER_STOPPED)
    }

    // ── REST API Polling ──────────────────────────────────────────────────────
    private fun startRestPolling(config: ApiConfig) {
        if (config.url.isBlank()) {
            log("REST URL not configured!", LogLevel.ERROR)
            return
        }
        pollJob = serviceScope.launch {
            log("REST polling started: ${config.url}", LogLevel.INFO)
            var retryCount = 0
            while (isActive) {
                try {
                    val response = httpGet(config.url, config.authToken)
                    if (response != null) {
                        retryCount = 0
                        parseAndDispatch(response)
                    }
                    delay(config.pollInterval)
                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    retryCount++
                    log("REST error ($retryCount): ${e.message}", LogLevel.ERROR)
                    if (config.autoReconnect) {
                        delay(minOf(retryCount * 1000L, 30_000L))
                    } else break
                }
            }
        }
    }

    // ── WebSocket ─────────────────────────────────────────────────────────────
    private fun startWebSocket(config: ApiConfig) {
        if (config.websocketUrl.isBlank()) {
            log("WebSocket URL not configured!", LogLevel.ERROR)
            return
        }
        wsJob = serviceScope.launch {
            log("WebSocket connecting: ${config.websocketUrl}", LogLevel.INFO)
            var retryCount = 0
            while (isActive) {
                try {
                    connectWebSocket(config.websocketUrl, config.authToken)
                    retryCount = 0
                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    retryCount++
                    log("WS error ($retryCount): ${e.message}", LogLevel.ERROR)
                    EventBus.post(EventBus.Event.CONNECTION_LOST)
                    if (config.autoReconnect) {
                        delay(minOf(retryCount * 2000L, 60_000L))
                        log("Reconnecting WebSocket...", LogLevel.INFO)
                    } else break
                }
            }
        }
    }

    private suspend fun connectWebSocket(url: String, token: String) {
        // Simple WebSocket using Java API (compatible with AndroidIDE)
        val uri = URI(url)
        val isSecure = url.startsWith("wss://")
        val host = uri.host
        val port = if (uri.port == -1) (if (isSecure) 443 else 80) else uri.port
        val path = if (uri.path.isNullOrEmpty()) "/" else uri.path

        val socket = if (isSecure) {
            javax.net.ssl.SSLSocketFactory.getDefault().createSocket(host, port) as javax.net.ssl.SSLSocket
        } else {
            java.net.Socket(host, port)
        }

        socket.use { sock ->
            val key = java.util.Base64.getEncoder().encodeToString(
                java.security.SecureRandom().generateSeed(16)
            )
            // WebSocket Handshake
            val handshake = buildString {
                append("GET $path HTTP/1.1\r\n")
                append("Host: $host:$port\r\n")
                append("Upgrade: websocket\r\n")
                append("Connection: Upgrade\r\n")
                append("Sec-WebSocket-Key: $key\r\n")
                append("Sec-WebSocket-Version: 13\r\n")
                if (token.isNotBlank()) append("Authorization: Bearer $token\r\n")
                append("\r\n")
            }
            val writer = sock.getOutputStream()
            writer.write(handshake.toByteArray())
            writer.flush()

            val reader = BufferedReader(InputStreamReader(sock.getInputStream()))
            // Read upgrade response
            var line = reader.readLine()
            while (line != null && line.isNotEmpty()) line = reader.readLine()

            log("WebSocket connected ✓", LogLevel.SUCCESS)
            EventBus.post(EventBus.Event.CONNECTED)

            // Read frames
            val inputStream = sock.getInputStream()
            val buffer = ByteArray(4096)
            while (!sock.isClosed) {
                if (inputStream.available() > 0) {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead > 0) {
                        val frame = decodeWebSocketFrame(buffer.copyOf(bytesRead))
                        if (frame != null) {
                            log("WS received: $frame", LogLevel.DEBUG)
                            parseAndDispatch(frame)
                        }
                    }
                }
                delay(50)
            }
        }
    }

    private fun decodeWebSocketFrame(data: ByteArray): String? {
        if (data.size < 2) return null
        val opcode = data[0].toInt() and 0x0F
        if (opcode != 1) return null // Only text frames
        var payloadLen = (data[1].toInt() and 0x7F)
        var offset = 2
        if (payloadLen == 126) {
            payloadLen = ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)
            offset = 4
        }
        return String(data, offset, minOf(payloadLen, data.size - offset))
    }

    // ── Telegram Bot Polling ──────────────────────────────────────────────────
    private fun startTelegramPolling(config: ApiConfig) {
        if (config.telegramToken.isBlank()) {
            log("Telegram token not configured!", LogLevel.ERROR)
            return
        }
        telegramJob = serviceScope.launch {
            log("Telegram polling started", LogLevel.INFO)
            while (isActive) {
                try {
                    fetchTelegramUpdates(config)
                    delay(config.pollInterval)
                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    log("Telegram error: ${e.message}", LogLevel.ERROR)
                    delay(5000)
                }
            }
        }
    }

    private suspend fun fetchTelegramUpdates(config: ApiConfig) {
        val url = "https://api.telegram.org/bot${config.telegramToken}/getUpdates?offset=${lastTelegramUpdateId + 1}&timeout=30"
        val response = httpGet(url) ?: return

        try {
            val json = gson.fromJson(response, Map::class.java)
            if (json["ok"] as? Boolean != true) return
            val results = json["result"] as? List<*> ?: return

            for (update in results) {
                val updateMap = update as? Map<*, *> ?: continue
                val updateId = (updateMap["update_id"] as? Double)?.toLong() ?: continue
                lastTelegramUpdateId = maxOf(lastTelegramUpdateId, updateId)

                // Parse message
                val message = updateMap["message"] as? Map<*, *> ?: continue
                val chatId = (message["chat"] as? Map<*, *>)?.get("id")?.toString() ?: continue
                val text = message["text"] as? String ?: continue

                // Filter by chat ID if configured
                if (config.telegramChatId.isNotBlank() && chatId != config.telegramChatId) continue

                log("Telegram message: $text", LogLevel.INFO)
                parseTelegramSignal(text)
            }
        } catch (e: Exception) {
            log("Telegram parse error: ${e.message}", LogLevel.ERROR)
        }
    }

    private fun parseTelegramSignal(text: String) {
        // Try JSON first
        try {
            val signal = gson.fromJson(text, AutoSignal::class.java)
            if (signal.signal.isNotBlank()) {
                dispatchSignal(signal)
                return
            }
        } catch (e: Exception) { /* not JSON */ }

        // Plain text commands
        val parts = text.trim().uppercase().split(" ")
        val signal = parts[0]
        val count = parts.getOrNull(1)?.toIntOrNull() ?: 1
        val delay = parts.getOrNull(2)?.toLongOrNull() ?: 500L
        dispatchSignal(AutoSignal(signal = signal, count = count, delay = delay))
    }

    // ── Firebase Realtime Database ────────────────────────────────────────────
    private fun startFirebaseListener(config: ApiConfig) {
        if (config.url.isBlank()) {
            log("Firebase URL not configured!", LogLevel.ERROR)
            return
        }
        // Firebase REST API polling (no SDK needed)
        pollJob = serviceScope.launch {
            val firebaseUrl = "${config.url.trimEnd('/')}/signal.json"
            log("Firebase polling: $firebaseUrl", LogLevel.INFO)
            var lastSignal = ""
            while (isActive) {
                try {
                    val response = httpGet(firebaseUrl, config.authToken) ?: continue
                    if (response != lastSignal && response != "null") {
                        lastSignal = response
                        parseAndDispatch(response.trim('"'))
                    }
                    delay(config.pollInterval)
                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    log("Firebase error: ${e.message}", LogLevel.ERROR)
                    delay(5000)
                }
            }
        }
    }

    // ── Parse & Dispatch ──────────────────────────────────────────────────────
    private fun parseAndDispatch(data: String) {
        if (data.isBlank()) return
        try {
            // Try as JSON
            val signal = gson.fromJson(data, AutoSignal::class.java)
            if (signal != null && signal.signal.isNotBlank()) {
                dispatchSignal(signal)
                return
            }
        } catch (e: Exception) { /* not JSON */ }

        // Try as plain signal string: "BIG" or "SMALL:10:500"
        val parts = data.trim().split(":")
        val sig = parts[0].uppercase()
        if (sig.isNotBlank()) {
            dispatchSignal(AutoSignal(
                signal = sig,
                count  = parts.getOrNull(1)?.toIntOrNull() ?: 1,
                delay  = parts.getOrNull(2)?.toLongOrNull() ?: 500L
            ))
        }
    }

    private fun dispatchSignal(signal: AutoSignal) {
        log("Dispatching: ${signal.signal} ×${signal.count} delay=${signal.delay}ms", LogLevel.SUCCESS)
        EventBus.post(EventBus.Event.SIGNAL_RECEIVED, signal)

        // Send to Accessibility Service
        val intent = Intent(AutoClickAccessibilityService.ACTION_PERFORM_CLICK).apply {
            putExtra(AutoClickAccessibilityService.EXTRA_SIGNAL, gson.toJson(signal))
        }
        val lbm = androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this)
        lbm.sendBroadcast(intent)

        // Also try direct call
        AutoClickAccessibilityService.instance?.processSignal(signal)
    }

    // ── HTTP GET ──────────────────────────────────────────────────────────────
    private suspend fun httpGet(urlStr: String, token: String = ""): String? =
        withContext(Dispatchers.IO) {
            try {
                val url = URL(urlStr)
                val conn = url.openConnection() as HttpURLConnection
                conn.apply {
                    requestMethod = "GET"
                    connectTimeout = 10_000
                    readTimeout    = 30_000
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                    if (token.isNotBlank()) setRequestProperty("Authorization", "Bearer $token")
                }
                val code = conn.responseCode
                if (code == 200) {
                    conn.inputStream.bufferedReader().readText()
                } else {
                    log("HTTP $code from $urlStr", LogLevel.WARNING)
                    null
                }
            } catch (e: Exception) {
                log("HTTP error: ${e.message}", LogLevel.ERROR)
                null
            }
        }

    // ── Cleanup ───────────────────────────────────────────────────────────────
    override fun onDestroy() {
        super.onDestroy()
        stopReceiving()
        serviceScope.cancel()
    }

    private fun log(msg: String, level: LogLevel = LogLevel.INFO) {
        Log.d(TAG, msg)
        EventBus.postLog(LogEntry(message = msg, level = level, tag = TAG))
    }
}
