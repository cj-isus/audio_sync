package ru.audiosynchronizer.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import java.util.concurrent.ConcurrentSkipListMap

class OpusDecoder {

    private var decoder: MediaCodec? = null
    private var isStarted = false
    private var inputEos = false
    private var outputEos = false

    private val jitterBuffer = ConcurrentSkipListMap<Long, ByteArray>()
    private var nextSequence = 0L
    private var jitterBufferSize = 4

    companion object {
        private const val TAG = "OpusDecoder"
        private const val SAMPLE_RATE = 48000
        private const val CHANNELS = 2
        private const val FRAME_SIZE = 240
        private const val MIME = "audio/opus"
    }

    fun init() {
        try {
            decoder = MediaCodec.createDecoderByType(MIME)
            val format = MediaFormat()
            format.setString(MediaFormat.KEY_MIME, MIME)
            format.setInteger(MediaFormat.KEY_SAMPLE_RATE, SAMPLE_RATE)
            format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, CHANNELS)
            format.setInteger(MediaFormat.KEY_BIT_RATE, 128000)

            decoder?.configure(format, null, null, 0)
            decoder?.start()
            isStarted = true
            Log.i(TAG, "Opus decoder started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init Opus decoder", e)
        }
    }

    fun addChunk(sequenceNumber: Long, payload: ByteArray) {
        jitterBuffer[sequenceNumber] = payload

        while (jitterBuffer.size > jitterBufferSize * 2) {
            jitterBuffer.pollFirstEntry()
        }
    }

    fun readDecodedPcm(): FloatArray? {
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

    private fun feedInput(dec: MediaCodec): Boolean {
        val entry = jitterBuffer.pollFirstEntry() ?: return false
        val seq = entry.key
        val payload = entry.value

        val inputBufIdx = dec.dequeueInputBuffer(1000)
        if (inputBufIdx < 0) {
            jitterBuffer[seq] = payload
            return false
        }

        val inputBuf = dec.getInputBuffer(inputBufIdx) ?: return false
        inputBuf.clear()
        inputBuf.put(payload)

        val presentationUs = seq * FRAME_SIZE * 1_000_000L / SAMPLE_RATE
        dec.queueInputBuffer(inputBufIdx, 0, payload.size, presentationUs, 0)
        return true
    }

    private fun drainOutput(dec: MediaCodec): FloatArray? {
        val info = MediaCodec.BufferInfo()
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
        return pcm
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
        try { decoder?.stop() } catch (_: Exception) {}
        try { decoder?.release() } catch (_: Exception) {}
        decoder = null
        isStarted = false
        jitterBuffer.clear()
    }
}
