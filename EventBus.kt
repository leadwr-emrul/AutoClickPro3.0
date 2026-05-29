package com.autoclicker.pro.utils

import com.autoclicker.pro.model.LogEntry
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

object EventBus {

    enum class Event {
        SERVICE_CONNECTED, SERVICE_DISCONNECTED,
        AUTOMATION_START, AUTOMATION_COMPLETE, AUTOMATION_ERROR,
        AUTOMATION_ENABLED, AUTOMATION_DISABLED,
        CLICK_PROGRESS,
        SIGNAL_RECEIVED,
        RECEIVER_STARTED, RECEIVER_STOPPED,
        CONNECTED, CONNECTION_LOST,
        COORDINATE_PICKED,
        WINDOW_CHANGED,
        PROFILE_CHANGED
    }

    data class AppEvent(val event: Event, val data: Any? = null)

    private val _events = MutableSharedFlow<AppEvent>(extraBufferCapacity = 64)
    val events = _events.asSharedFlow()

    private val _logs = MutableSharedFlow<LogEntry>(extraBufferCapacity = 256)
    val logs = _logs.asSharedFlow()

    private val _connectionStatus = MutableStateFlow(false)
    val connectionStatus = _connectionStatus.asStateFlow()

    private val _automationStatus = MutableStateFlow(false)
    val automationStatus = _automationStatus.asStateFlow()

    fun post(event: Event, data: Any? = null) {
        _events.tryEmit(AppEvent(event, data))
        when (event) {
            Event.CONNECTED          -> _connectionStatus.value = true
            Event.CONNECTION_LOST    -> _connectionStatus.value = false
            Event.AUTOMATION_ENABLED -> _automationStatus.value = true
            Event.AUTOMATION_DISABLED-> _automationStatus.value = false
            Event.SERVICE_DISCONNECTED -> _automationStatus.value = false
            else -> {}
        }
    }

    fun postLog(entry: LogEntry) {
        _logs.tryEmit(entry)
    }
}
