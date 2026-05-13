package ru.audiosynchronizer.sync

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.audiosynchronizer.protocol.TimelineAnchorMessage

data class ClientLatencyInfo(
    val deviceName: String,
    val outputLatencyMs: Double,
    val deviationNs: Long = 0L
)

class LatencyCompensator {

    private val _clientLatencies = MutableStateFlow<Map<Int, ClientLatencyInfo>>(emptyMap())
    val clientLatencies: StateFlow<Map<Int, ClientLatencyInfo>> = _clientLatencies.asStateFlow()

    private val _maxLatencyMs = MutableStateFlow(0.0)
    val maxLatencyMs: StateFlow<Double> = _maxLatencyMs.asStateFlow()

    private val _localLatencyMs = MutableStateFlow(0.0)
    val localLatencyMs: StateFlow<Double> = _localLatencyMs.asStateFlow()

    companion object {
        private const val TAG = "LatencyCompensator"
    }

    fun setLocalLatency(latencyMs: Double) {
        _localLatencyMs.value = latencyMs
        recalcMax()
    }

    fun updateClientLatency(clientId: Int, info: ClientLatencyInfo) {
        val map = _clientLatencies.value.toMutableMap()
        map[clientId] = info
        _clientLatencies.value = map
        recalcMax()
    }

    fun removeClient(clientId: Int) {
        val map = _clientLatencies.value.toMutableMap()
        map.remove(clientId)
        _clientLatencies.value = map
        recalcMax()
    }

    fun compensateAnchor(anchor: TimelineAnchorMessage, clientId: Int): TimelineAnchorMessage {
        val clientLatency = _clientLatencies.value[clientId]?.outputLatencyMs ?: return anchor
        val maxLatency = _maxLatencyMs.value

        val compensationUs = ((maxLatency - clientLatency) * 1000).toLong()
        val adjustedMediaTimeUs = anchor.mediaTimeUs - compensationUs

        Log.d(TAG, "Compensate for client $clientId: latency=${clientLatency}ms, " +
                "max=${maxLatency}ms, compensation=${compensationUs}us")

        return anchor.copy(mediaTimeUs = adjustedMediaTimeUs)
    }

    fun updateDeviation(clientId: Int, deviationNs: Long) {
        val map = _clientLatencies.value.toMutableMap()
        val existing = map[clientId] ?: return
        map[clientId] = existing.copy(deviationNs = deviationNs)
        _clientLatencies.value = map
    }

    private fun recalcMax() {
        val all = mutableListOf(_localLatencyMs.value)
        _clientLatencies.value.values.forEach { all.add(it.outputLatencyMs) }
        _maxLatencyMs.value = all.maxOrNull() ?: 0.0
    }
}
