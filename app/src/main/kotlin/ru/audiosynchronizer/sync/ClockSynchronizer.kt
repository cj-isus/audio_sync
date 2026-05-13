package ru.audiosynchronizer.sync

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.coroutines.coroutineContext

data class ClockSyncState(
    val offsetMs: Double = 0.0,
    val driftPpm: Double = 0.0,
    val rttMs: Double = 0.0,
    val sampleCount: Int = 0,
    val isStable: Boolean = false,
    val isSyncing: Boolean = false,
    val role: ClockRole = ClockRole.IDLE,
    val error: String? = null
)

enum class ClockRole { IDLE, LEADER, FOLLOWER }

class ClockSynchronizer {

    private val kalman = KalmanFilter()
    private val medianOffset = MedianFilter(200)
    private val medianRtt = MedianFilter(200)

    private val _state = MutableStateFlow(ClockSyncState())
    val state: StateFlow<ClockSyncState> = _state.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var syncJob: Job? = null
    private var serverSocket: DatagramSocket? = null

    companion object {
        private const val TAG = "ClockSync"
        private const val PORT = 1704
        private const val PACKET_SIZE = 64
        private const val INITIAL_INTERVAL_MS = 100L
        private const val INITIAL_COUNT = 50
        private const val STEADY_INTERVAL_MS = 1000L
        private const val MAX_RTT_MS = 10.0
    }

    fun startLeader() {
        stop()
        _state.value = _state.value.copy(role = ClockRole.LEADER, isSyncing = true, error = null)
        syncJob = scope.launch { leaderLoop() }
    }

    fun startFollower(leaderIp: String) {
        stop()
        _state.value = _state.value.copy(role = ClockRole.FOLLOWER, isSyncing = true, error = null)
        syncJob = scope.launch { followerLoop(leaderIp) }
    }

    fun stop() {
        syncJob?.cancel()
        syncJob = null
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        _state.value = _state.value.copy(isSyncing = false)
    }

    fun reset() {
        kalman.reset()
        medianOffset.clear()
        medianRtt.clear()
        _state.value = ClockSyncState()
    }

    fun getOffsetMs(): Double {
        return if (kalman.isStable()) kalman.getOffset() else medianOffset.median()
    }

    fun getDriftPpm(): Double {
        return if (kalman.isStable()) kalman.getDriftRate() else 0.0
    }

    fun getOffsetNs(): Long {
        return (getOffsetMs() * 1_000_000).toLong()
    }

    private suspend fun leaderLoop() {
        try {
            serverSocket = DatagramSocket(PORT)
            val buf = ByteArray(PACKET_SIZE)
            val receivePacket = DatagramPacket(buf, buf.size)

            Log.i(TAG, "Leader started on port $PORT")

            while (coroutineContext.isActive) {
                try {
                    serverSocket?.soTimeout = 5000
                    serverSocket?.receive(receivePacket)
                } catch (e: java.net.SocketTimeoutException) {
                    continue
                }

                if (!coroutineContext.isActive) break

                val t2 = System.nanoTime()
                val payload = String(receivePacket.data, 0, receivePacket.length, Charsets.UTF_8)
                val t1 = payload.toLongOrNull() ?: continue

                val t3 = System.nanoTime()
                val response = "$t1,$t2,$t3"
                val responseBytes = response.toByteArray(Charsets.UTF_8)
                val sendPacket = DatagramPacket(
                    responseBytes, responseBytes.size,
                    receivePacket.address, receivePacket.port
                )
                serverSocket?.send(sendPacket)
            }
        } catch (e: CancellationException) {
            // expected
        } catch (e: Exception) {
            Log.e(TAG, "Leader error", e)
            _state.value = _state.value.copy(error = e.message)
        }
    }

    private suspend fun followerLoop(leaderIp: String) {
        try {
            val leaderAddress = InetAddress.getByName(leaderIp)
            val socket = DatagramSocket()
            socket.soTimeout = 3000

            val buf = ByteArray(PACKET_SIZE)
            val receivePacket = DatagramPacket(buf, buf.size)

            var initialCount = 0

            while (coroutineContext.isActive) {
                val t1 = System.nanoTime()
                val sendPayload = t1.toString()
                val sendBytes = sendPayload.toByteArray(Charsets.UTF_8)
                val sendPacket = DatagramPacket(sendBytes, sendBytes.size, leaderAddress, PORT)
                socket.send(sendPacket)

                try {
                    socket.receive(receivePacket)
                } catch (e: java.net.SocketTimeoutException) {
                    Log.w(TAG, "Request timed out")
                    _state.value = _state.value.copy(error = "Timeout")
                    delay(STEADY_INTERVAL_MS)
                    continue
                }

                val t4 = System.nanoTime()
                val response = String(receivePacket.data, 0, receivePacket.length, Charsets.UTF_8)
                val parts = response.split(",")
                if (parts.size != 3) continue

                val t1r = parts[0].toLongOrNull() ?: continue
                val t2r = parts[1].toLongOrNull() ?: continue
                val t3r = parts[2].toLongOrNull() ?: continue

                val rttNs = (t4 - t1r) - (t3r - t2r)
                val rttMs = rttNs / 1e6

                if (rttMs > MAX_RTT_MS) {
                    Log.w(TAG, "High RTT: ${"%.2f".format(rttMs)} ms, discarding")
                    _state.value = _state.value.copy(error = "High RTT: ${"%.2f".format(rttMs)}ms")
                    val delayMs = if (initialCount < INITIAL_COUNT) INITIAL_INTERVAL_MS else STEADY_INTERVAL_MS
                    delay(delayMs)
                    continue
                }

                val offsetNs = ((t2r - t1r) + (t3r - t4)) / 2.0
                val offsetMs = offsetNs / 1e6

                kalman.update(offsetNs, rttNs.toDouble())
                medianOffset.add(offsetMs)
                medianRtt.add(rttMs)

                _state.value = _state.value.copy(
                    offsetMs = if (kalman.isStable()) kalman.getOffset() / 1e6 else medianOffset.median(),
                    driftPpm = if (kalman.isStable()) kalman.getDriftRate() else 0.0,
                    rttMs = medianRtt.median(),
                    sampleCount = kalman.getSampleCount(),
                    isStable = kalman.isStable(),
                    error = null
                )

                initialCount++
                val delayMs = if (initialCount < INITIAL_COUNT) INITIAL_INTERVAL_MS else STEADY_INTERVAL_MS
                delay(delayMs)
            }

            socket.close()
        } catch (e: CancellationException) {
            // expected
        } catch (e: Exception) {
            Log.e(TAG, "Follower error", e)
            _state.value = _state.value.copy(error = e.message)
        }
    }
}
