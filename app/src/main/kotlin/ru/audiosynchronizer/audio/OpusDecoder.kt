package ru.audiosynchronizer.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import java.util.concurrent.ConcurrentSkipListMap

class OpusDecoder {

    @Volatile
    private var decoder: MediaCodec? = null
    @Volatile
    private var isStarted = false
    @Volatile
    private var inputEos = false
    @Volatile
    private var outputEos = false

    private val jitterBuffer = ConcurrentSkipListMap<Long, ByteArray>()
    @Volatile
    private var nextSequence = 0L
    private var jitterBufferSize = 4
    private val decoderLock = Any()

    companion object {
        private const val TAG = "OpusDecoder"
        private const val SAMPLE_RATE = 48000
        private const val CHANNELS = 2
        private const val FRAME_SIZE = 240
        private const val MIME = "audio/opus"
        private const val MAX_JITTER_ENTRIES = 100
    }

    fun init() {
        synchronized(decoderLock) {
            releaseInternal()
            inputEos = false
            outputEos = false
            nextSequence = 0L
            jitterBuffer.clear()
            try {
                val dec = MediaCodec.createDecoderByType(MIME)
                val format = MediaFormat()
                format.setString(MediaFormat.KEY_MIME, MIME)
                format.setInteger(MediaFormat.KEY_SAMPLE_RATE, SAMPLE_RATE)
                format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, CHANNELS)
                format.setInteger(MediaFormat.KEY_BIT_RATE, 128000)

                dec.configure(format, null, null, 0)
                dec.start()
                decoder = dec
                isStarted = true
                Log.i(TAG, "Opus decoder started")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to init Opus decoder", e)
                isStarted = false
            }
        }
    }

    fun addChunk(sequenceNumber: Long, payload: ByteArray) {
        jitterBuffer[sequenceNumber] = payload

        while (jitterBuffer.size > MAX_JITTER_ENTRIES) {
            jitterBuffer.pollFirstEntry()
        }
    }

    fun readDecodedPcm(): FloatArray? {
        synchronized(decoderLock) {
            val dec = decoder ?: return null
            if (outputEos) return null

            if (!feedInput(dec)) {
                if (inputEos && !outputEos) {
                    // drain remaining output
                } else if (jitterBuffer.isEmpty()) {
                    return null
                }
            }

            return drainOutput(dec)
        }
    }

    private fun feedInput(dec: MediaCodec): Boolean {
        val entry = jitterBuffer.pollFirstEntry() ?: return false
        val seq = entry.key
        val payload = entry.value

        try {
            val inputBufIdx = dec.dequeueInputBuffer(1000)
            if (inputBufIdx < 0) {
                jitterBuffer[seq] = payload
                return false
            }

            val inputBuf = dec.getInputBuffer(inputBufIdx) ?: return false
            inputBuf.clear()
            if (payload.size > inputBuf.remaining()) {
                Log.w(TAG, "Payload too large for input buffer: ${payload.size}")
                return false
            }
            inputBuf.put(payload)

            val presentationUs = seq * FRAME_SIZE * 1_000_000L / SAMPLE_RATE
            dec.queueInputBuffer(inputBufIdx, 0, payload.size, presentationUs, 0)
            return true
        } catch (e: Exception) {
            Log.w(TAG, "feedInput error", e)
            return false
        }
    }

    private fun drainOutput(dec: MediaCodec): FloatArray? {
        val info = MediaCodec.BufferInfo()
        return try {
            val outputBufIdx = dec.dequeueOutputBuffer(info, 1000)

            if (outputBufIdx < 0) return null

            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                outputEos = true
                dec.releaseOutputBuffer(outputBufIdx, false)
                return null
            }

            val outputBuf = dec.getOutputBuffer(outputBufIdx)
            val pcm = if (outputBuf != null && info.size > 0) {
                convertToFloat(outputBuf, info)
            } else null

            dec.releaseOutputBuffer(outputBufIdx, false)
            pcm
        } catch (e: Exception) {
            Log.w(TAG, "drainOutput error", e)
            null
        }
    }

    private fun convertToFloat(buf: java.nio.ByteBuffer, info: MediaCodec.BufferInfo): FloatArray {
        buf.position(info.offset)
        buf.limit(info.offset + info.size)
        val shortBuf = buf.order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val shorts = ShortArray(shortBuf.remaining())
        shortBuf.get(shorts)
        return FloatArray(shorts.size) { shorts[it] / 32768f }
    }

    fun setJitterBufferSize(size: Int) {
        jitterBufferSize = size.coerceIn(2, 10)
    }

    fun release() {
        synchronized(decoderLock) {
            releaseInternal()
        }
        jitterBuffer.clear()
        isStarted = false
    }

    private fun releaseInternal() {
        try { decoder?.stop() } catch (_: Exception) {}
        try { decoder?.release() } catch (_: Exception) {}
        decoder = null
    }
}
