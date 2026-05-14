package ru.audiosynchronizer.sync

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.audiosynchronizer.protocol.TimelineAnchorMessage
import java.util.concurrent.ConcurrentHashMap

data class ClientLatencyInfo(
    val deviceName: String,
    val outputLatencyMs: Double,
    val deviationNs: Long = 0L
)

class LatencyCompensator {

    private val clientMap = ConcurrentHashMap<Int, ClientLatencyInfo>()

    private val _clientLatencies = MutableStateFlow<Map<Int, ClientLatencyInfo>>(emptyMap())
    val clientLatencies: StateFlow<Map<Int, ClientLatencyInfo>> = _clientLatencies.asStateFlow()

    @Volatile
    private var _maxLatencyMs = 0.0
    val maxLatencyMs: Double get() = _maxLatencyMs

    @Volatile
    private var _localLatencyMs = 0.0
    val localLatencyMs: Double get() = _localLatencyMs

    companion object {
        private const val TAG = "LatencyCompensator"
    }

    @Synchronized
    fun setLocalLatency(latencyMs: Double) {
        _localLatencyMs = latencyMs
        recalcMax()
    }

    @Synchronized
    fun updateClientLatency(clientId: Int, info: ClientLatencyInfo) {
        clientMap[clientId] = info
        _clientLatencies.value = clientMap.toMap()
        recalcMax()
    }

    @Synchronized
    fun removeClient(clientId: Int) {
        clientMap.remove(clientId)
        _clientLatencies.value = clientMap.toMap()
        recalcMax()
    }

    fun compensateAnchor(anchor: TimelineAnchorMessage, clientId: Int): TimelineAnchorMessage {
        val clientLatency = clientMap[clientId]?.outputLatencyMs ?: return anchor
        val maxLatency = _maxLatencyMs

        val compensationUs = ((maxLatency - clientLatency) * 1000).toLong()
        val adjustedMediaTimeUs = anchor.mediaTimeUs - compensationUs

        Log.d(TAG, "Compensate for client $clientId: latency=${clientLatency}ms, " +
                "max=${maxLatency}ms, compensation=${compensationUs}us")

        return anchor.copy(mediaTimeUs = adjustedMediaTimeUs)
    }

    @Synchronized
    fun updateDeviation(clientId: Int, deviationNs: Long) {
        val existing = clientMap[clientId] ?: return
        clientMap[clientId] = existing.copy(deviationNs = deviationNs)
        _clientLatencies.value = clientMap.toMap()
    }

    private fun recalcMax() {
        val all = mutableListOf(_localLatencyMs)
        clientMap.values.forEach { all.add(it.outputLatencyMs) }
        _maxLatencyMs = all.maxOrNull() ?: 0.0
    }
}
