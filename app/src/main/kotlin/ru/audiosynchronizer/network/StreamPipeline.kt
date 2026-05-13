package ru.audiosynchronizer.network

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.audiosynchronizer.audio.AudioEngine
import ru.audiosynchronizer.audio.OpusDecoder
import ru.audiosynchronizer.protocol.Message
import ru.audiosynchronizer.protocol.MessageCodec
import ru.audiosynchronizer.protocol.WireChunk
import java.io.InputStream
import java.io.OutputStream

data class StreamState(
    val isStreaming: Boolean = false,
    val codec: String = "pcm",
    val bytesSent: Long = 0,
    val bytesReceived: Long = 0,
    val error: String? = null
)

class StreamPipeline(private val context: Context, private val engine: AudioEngine) {

    private val _state = MutableStateFlow(StreamState())
    val state: StateFlow<StreamState> = _state.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var senderJob: Job? = null
    private var receiverJob: Job? = null

    private val opusDecoder = OpusDecoder()

    companion object {
        private const val TAG = "StreamPipeline"
        private const val PCM_CHUNK_FRAMES = 480
        private const val PCM_CHUNK_SAMPLES = PCM_CHUNK_FRAMES * 2
    }

    fun startPcmSender(
        getOutputStream: () -> OutputStream?,
        readPcm: () -> FloatArray?
    ) {
        stopSender()
        _state.value = _state.value.copy(isStreaming = true, codec = WireChunk.CODEC_PCM)

        senderJob = scope.launch {
            var seq = 0L
            try {
                while (coroutineContext.isActive) {
                    val output = getOutputStream() ?: break
                    val pcm = readPcm() ?: break

                    val payload = ByteArray(pcm.size * 4)
                    val buf = java.nio.ByteBuffer.wrap(payload).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    val floatBuf = buf.asFloatBuffer()
                    floatBuf.put(pcm)

                    val chunk = WireChunk(
                        timestampUs = System.nanoTime() / 1000,
                        codec = WireChunk.CODEC_PCM,
                        payload = payload,
                        sequenceNumber = seq++
                    )
                    try {
                        MessageCodec.writeMessage(output, Message.WireChunkMsg(chunk))
                        _state.value = _state.value.copy(bytesSent = _state.value.bytesSent + payload.size)
                    } catch (e: Exception) {
                        Log.w(TAG, "Send error", e)
                    }
                }
            } catch (_: CancellationException) {
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun startPcmReceiver(inputStream: InputStream) {
        stopReceiver()
        opusDecoder.init()
        _state.value = _state.value.copy(isStreaming = true)

        receiverJob = scope.launch {
            try {
                while (coroutineContext.isActive) {
                    val msg = MessageCodec.readMessage(inputStream)
                    if (msg == null) break

                    if (msg is Message.WireChunkMsg) {
                        processChunk(msg.data)
                    }
                }
            } catch (_: CancellationException) {
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    private fun processChunk(chunk: WireChunk) {
        when (chunk.codec) {
            WireChunk.CODEC_PCM -> {
                val pcm = FloatArray(chunk.payload.size / 4)
                val buf = java.nio.ByteBuffer.wrap(chunk.payload).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                buf.asFloatBuffer().get(pcm)
                engine.writePcmData(pcm)
                _state.value = _state.value.copy(bytesReceived = _state.value.bytesReceived + chunk.payload.size)
            }
            WireChunk.CODEC_OPUS -> {
                opusDecoder.addChunk(chunk.sequenceNumber, chunk.payload)
                val pcm = opusDecoder.readDecodedPcm()
                if (pcm != null) {
                    engine.writePcmData(pcm)
                }
            }
        }
    }

    fun stopSender() {
        senderJob?.cancel()
        senderJob = null
    }

    fun stopReceiver() {
        receiverJob?.cancel()
        receiverJob = null
        opusDecoder.release()
    }

    fun stop() {
        stopSender()
        stopReceiver()
        _state.value = StreamState()
    }
}
