package ru.audiosynchronizer.sync

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.audiosynchronizer.protocol.ControlMessage
import ru.audiosynchronizer.protocol.Message
import ru.audiosynchronizer.protocol.MessageCodec
import ru.audiosynchronizer.protocol.TimelineAnchorMessage
import java.io.OutputStream

class TimelineManager(
    private val session: SyncSession,
    private val clockSync: ClockSynchronizer,
    private val latencyCompensator: LatencyCompensator? = null
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var anchorJob: Job? = null

    private val _currentPositionUs = MutableStateFlow(0L)
    val currentPositionUs: StateFlow<Long> = _currentPositionUs.asStateFlow()

    @Volatile
    private var lastAnchor: TimelineAnchorMessage? = null
    @Volatile
    private var lastSyncTimeNs: Long = 0L

    companion object {
        private const val TAG = "TimelineManager"
        private const val ANCHOR_INTERVAL_MS = 200L
        private const val FEEDBACK_INTERVAL_MS = 2000L
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

    fun startLeaderBroadcastPerClient(
        clients: Map<Int, OutputStream>,
        getMediaTimeUs: () -> Long,
        getPlaybackState: () -> Int
    ) {
        stop()
        anchorJob = scope.launch {
            while (coroutineContext.isActive) {
                val baseAnchor = TimelineAnchorMessage(
                    mediaTimeUs = getMediaTimeUs(),
                    deviceTimeNs = System.nanoTime(),
                    sampleRate = 48000,
                    playbackState = getPlaybackState()
                )

                val snapshot = clients.entries.toList()
                for ((clientId, output) in snapshot) {
                    val anchor = latencyCompensator?.compensateAnchor(baseAnchor, clientId) ?: baseAnchor
                    try {
                        MessageCodec.writeMessage(output, Message.TimelineAnchor(anchor))
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to send anchor to client $clientId", e)
                    }
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
        val dtNs = myNowNs - lastSyncTimeNs
        val driftCorrectionNs = (driftPpm * dtNs / 1e6).toLong()
        val serverNowNs = myNowNs + offsetNs + driftCorrectionNs

        val elapsedServerNs = serverNowNs - anchor.deviceTimeNs
        val positionUs = anchor.mediaTimeUs + (elapsedServerNs / 1000)

        _currentPositionUs.value = positionUs
        return positionUs
    }

    fun sendFeedback(output: OutputStream, actualPlayoutTimeNs: Long, scheduledPlayoutTimeNs: Long) {
        val heartbeat = ru.audiosynchronizer.protocol.HeartbeatMessage(
            timestampNs = System.nanoTime(),
            actualPlayoutTimeNs = actualPlayoutTimeNs,
            scheduledPlayoutTimeNs = scheduledPlayoutTimeNs
        )
        try {
            MessageCodec.writeMessage(output, Message.Heartbeat(heartbeat))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send feedback", e)
        }
    }

    fun processFeedback(clientId: Int, actualNs: Long, scheduledNs: Long) {
        val deviationNs = actualNs - scheduledNs
        latencyCompensator?.updateDeviation(clientId, deviationNs)

        if (kotlin.math.abs(deviationNs) > 1_000_000L) {
            Log.w(TAG, "Client $clientId deviation: ${deviationNs / 1e6}ms")
        }
    }

    fun sendControl(output: OutputStream, action: Int, seekPositionUs: Long = 0L) {
        try {
            val ctrl = ControlMessage(action = action, seekPositionUs = seekPositionUs)
            MessageCodec.writeMessage(output, Message.Control(ctrl))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send control", e)
        }
    }

    fun stop() {
        anchorJob?.cancel()
        anchorJob = null
        lastAnchor = null
    }

    fun cancelScope() {
        stop()
        scope.cancel()
    }
}
