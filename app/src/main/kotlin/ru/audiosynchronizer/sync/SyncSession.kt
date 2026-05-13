package ru.audiosynchronizer.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.audiosynchronizer.protocol.TimelineAnchorMessage

enum class SessionState {
    DISCONNECTED,
    CONNECTING,
    CLOCK_SYNCING,
    FILE_TRANSFER,
    READY,
    PLAYING,
    PAUSED
}

data class SyncSessionState(
    val state: SessionState = SessionState.DISCONNECTED,
    val isLeader: Boolean = false,
    val connectedDevices: Int = 0,
    val deviceName: String = "",
    val fileTransferProgress: Float = 0f,
    val error: String? = null
)

class SyncSession {
    private val _sessionState = MutableStateFlow(SyncSessionState())
    val sessionState: StateFlow<SyncSessionState> = _sessionState.asStateFlow()

    private var _lastAnchor: TimelineAnchorMessage? = null
    val lastAnchor: TimelineAnchorMessage? get() = _lastAnchor

    fun setLeader(isLeader: Boolean) {
        _sessionState.value = _sessionState.value.copy(isLeader = isLeader)
    }

    fun setState(state: SessionState) {
        _sessionState.value = _sessionState.value.copy(state = state, error = null)
    }

    fun setError(error: String?) {
        _sessionState.value = _sessionState.value.copy(error = error)
    }

    fun setConnectedDevices(count: Int) {
        _sessionState.value = _sessionState.value.copy(connectedDevices = count)
    }

    fun updateAnchor(anchor: TimelineAnchorMessage) {
        _lastAnchor = anchor
        when (anchor.playbackState) {
            TimelineAnchorMessage.STATE_PLAYING -> setState(SessionState.PLAYING)
            TimelineAnchorMessage.STATE_PAUSED -> setState(SessionState.PAUSED)
            TimelineAnchorMessage.STATE_STOPPED -> setState(SessionState.READY)
            TimelineAnchorMessage.STATE_SEEKING -> { /* handled by TimelineManager */ }
        }
    }

    fun setFileTransferProgress(progress: Float) {
        _sessionState.value = _sessionState.value.copy(fileTransferProgress = progress)
    }

    fun reset() {
        _lastAnchor = null
        _sessionState.value = SyncSessionState()
    }
}
