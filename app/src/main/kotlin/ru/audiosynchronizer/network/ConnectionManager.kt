package ru.audiosynchronizer.network

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ru.audiosynchronizer.protocol.*
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext

data class ConnectedClient(
    val id: Int,
    val socket: Socket,
    val output: OutputStream,
    val input: InputStream,
    val deviceName: String,
    val outputLatencyMs: Double
)

data class ConnectionState(
    val isServerRunning: Boolean = false,
    val isClientConnected: Boolean = false,
    val leaderIp: String = "",
    val connectedClients: List<String> = emptyList(),
    val error: String? = null
)

class ConnectionManager {

    private val _state = MutableStateFlow(ConnectionState())
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private var clientSocket: Socket? = null
    private var clientJob: Job? = null

    private val clients = ConcurrentHashMap<Int, ConnectedClient>()
    private val nextClientId = AtomicInteger(0)

    private var onMessageReceived: ((Message, InputStream?) -> Unit)? = null
    private var onClientConnected: ((ConnectedClient) -> Unit)? = null
    private var onDisconnected: (() -> Unit)? = null

    companion object {
        private const val TAG = "ConnectionManager"
        const val PORT = 1705
        private const val CONNECT_TIMEOUT_MS = 5000
    }

    fun setOnMessageReceived(listener: (Message, InputStream?) -> Unit) {
        onMessageReceived = listener
    }

    fun setOnClientConnected(listener: (ConnectedClient) -> Unit) {
        onClientConnected = listener
    }

    fun setOnDisconnected(listener: () -> Unit) {
        onDisconnected = listener
    }

    fun startServer() {
        stop()
        try {
            serverSocket = ServerSocket(PORT)
            _state.update { it.copy(isServerRunning = true, error = null) }
            Log.i(TAG, "Server started on port $PORT")

            serverJob = scope.launch {
                while (coroutineContext.isActive) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        val id = nextClientId.getAndIncrement()
                        Log.i(TAG, "Client connected: ${clientSocket.inetAddress}")

                        val input = clientSocket.getInputStream()
                        val output = clientSocket.getOutputStream()

                        scope.launch {
                            handleClient(id, clientSocket, input, output)
                        }
                    } catch (e: Exception) {
                        if (coroutineContext.isActive) {
                            Log.e(TAG, "Accept error", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start server", e)
            _state.update { it.copy(error = e.message) }
        }
    }

    private suspend fun handleClient(id: Int, socket: Socket, input: InputStream, output: OutputStream) {
        var deviceName = "Unknown"
        var latencyMs = 0.0

        try {
            val helloMsg = MessageCodec.readMessage(input)
            if (helloMsg is Message.Hello) {
                deviceName = helloMsg.data.deviceName
                latencyMs = helloMsg.data.outputLatencyMs
                Log.i(TAG, "Hello from $deviceName, latency=${latencyMs}ms")

                val response = Message.Hello(HelloMessage(
                    deviceName = android.os.Build.MODEL,
                    outputLatencyMs = 0.0
                ))
                MessageCodec.writeMessage(output, response)
            }

            val client = ConnectedClient(id, socket, output, input, deviceName, latencyMs)
            clients[id] = client
            onClientConnected?.invoke(client)
            updateClientList()

            while (coroutineContext.isActive) {
                val msg = MessageCodec.readMessage(input)
                if (msg == null) {
                    Log.i(TAG, "Client $deviceName disconnected")
                    break
                }
                onMessageReceived?.invoke(msg, null)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Client $deviceName error: ${e.message}")
        } finally {
            clients.remove(id)
            updateClientList()
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun updateClientList() {
        val names = clients.values.map { it.deviceName }
        _state.update { it.copy(connectedClients = names) }
    }

    fun getClientOutputs(): List<OutputStream> {
        return clients.values.map { it.output }
    }

    fun broadcast(msg: Message) {
        val data = MessageCodec.encode(msg)
        val snapshot = clients.values.toList()
        for (client in snapshot) {
            try {
                client.output.write(data)
                client.output.flush()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send to ${client.deviceName}", e)
            }
        }
    }

    fun connectToLeader(leaderIp: String) {
        stop()
        _state.update { it.copy(leaderIp = leaderIp) }

        clientJob = scope.launch {
            var socket: Socket? = null
            try {
                socket = Socket()
                socket.connect(InetSocketAddress(leaderIp, PORT), CONNECT_TIMEOUT_MS)
                clientSocket = socket
                val input = socket.getInputStream()
                val output = socket.getOutputStream()

                Log.i(TAG, "Connected to leader at $leaderIp:$PORT")

                val hello = Message.Hello(HelloMessage(deviceName = android.os.Build.MODEL))
                MessageCodec.writeMessage(output, hello)

                val response = MessageCodec.readMessage(input)
                if (response is Message.Hello) {
                    Log.i(TAG, "Leader hello: ${response.data.deviceName}")
                }

                _state.update { it.copy(isClientConnected = true, error = null) }

                while (coroutineContext.isActive) {
                    val msg = MessageCodec.readMessage(input)
                    if (msg == null) {
                        Log.i(TAG, "Leader disconnected")
                        break
                    }
                    onMessageReceived?.invoke(msg, input)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed", e)
                _state.update { it.copy(error = e.message) }
            } finally {
                _state.update { it.copy(isClientConnected = false) }
                onDisconnected?.invoke()
                try { socket?.close() } catch (_: Exception) {}
                clientSocket = null
            }
        }
    }

    fun getClientOutput(): OutputStream? {
        return clientSocket?.getOutputStream()
    }

    fun getClientInput(): InputStream? {
        return clientSocket?.getInputStream()
    }

    fun stop() {
        serverJob?.cancel()
        serverJob = null
        clientJob?.cancel()
        clientJob = null

        for (client in clients.values.toList()) {
            try { client.socket.close() } catch (_: Exception) {}
        }
        clients.clear()

        try { clientSocket?.close() } catch (_: Exception) {}
        clientSocket = null
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null

        _state.value = ConnectionState()
    }

    fun cancelScope() {
        scope.cancel()
    }
}
