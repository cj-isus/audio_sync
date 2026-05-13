package ru.audiosynchronizer.sync

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.audiosynchronizer.protocol.Message
import ru.audiosynchronizer.protocol.MessageCodec
import ru.audiosynchronizer.protocol.TimelineAnchorMessage
import java.io.OutputStream
import java.net.Socket

class TimelineManager(
    private val session: SyncSession,
    private val clockSync: ClockSynchronizer
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var anchorJob: Job? = null

    private val _currentPositionUs = MutableStateFlow(0L)
    val currentPositionUs: StateFlow<Long> = _currentPositionUs.asStateFlow()

    private var lastAnchor: TimelineAnchorMessage? = null
    private var lastSyncTimeNs: Long = 0L

    companion object {
        private const val TAG = "TimelineManager"
        private const val ANCHOR_INTERVAL_MS = 200L
    }

    fun startLeaderBroadcast(
        getOutputStream: () -> OutputStream?,
        getMediaTimeUs: () -> Long,
        getPlaybackState: () -> Int
    ) {
        stop()
        anchorJob = scope.launch {
            while (coroutineContext.isActive) {
                val output = getOutputStream() ?: break
                val anchor = TimelineAnchorMessage(
                    mediaTimeUs = getMediaTimeUs(),
                    deviceTimeNs = System.nanoTime(),
                    sampleRate = 48000,
                    playbackState = getPlaybackState()
                )
                try {
                    MessageCodec.writeMessage(output, Message.TimelineAnchor(anchor))
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to send anchor", e)
                }
                delay(ANCHOR_INTERVAL_MS)
            }
        }
    }

    fun processAnchor(anchor: TimelineAnchorMessage) {
        lastAnchor = anchor
        lastSyncTimeNs = System.nanoTime()
        session.updateAnchor(anchor)
    }

    fun computeFollowerPositionUs(): Long {
        val anchor = lastAnchor ?: return 0L
        val offsetNs = clockSync.getOffsetNs()
        val driftPpm = clockSync.getDriftPpm()

        val myNowNs = System.nanoTime()
        val serverNowNs = myNowNs + offsetNs + (driftPpm * (myNowNs - lastSyncTimeNs) / 1e6).toLong()

        val elapsedServerNs = serverNowNs - anchor.deviceTimeNs
        val positionUs = anchor.mediaTimeUs + (elapsedServerNs / 1000)

        _currentPositionUs.value = positionUs
        return positionUs
    }

    fun sendControl(output: OutputStream, action: Int, seekPositionUs: Long = 0L) {
        val ctrl = ru.audiosynchronizer.protocol.ControlMessage(
            action = action,
            seekPositionUs = seekPositionUs
        )
        MessageCodec.writeMessage(output, Message.Control(ctrl))
    }

    fun stop() {
        anchorJob?.cancel()
        anchorJob = null
        lastAnchor = null
    }
}
