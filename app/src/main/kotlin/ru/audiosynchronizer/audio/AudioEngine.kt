package ru.audiosynchronizer.audio

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PlaybackState {
    STOPPED, PLAYING, PAUSED
}

class AudioEngine(private val context: Context) {

    @Volatile
    private var enginePtr: Long = 0L

    private val fillerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var fillerJob: Job? = null

    @Volatile
    private var currentSource: AudioSource? = null
    private val sourceLock = Any()

    private val _playbackState = MutableStateFlow(PlaybackState.STOPPED)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _currentInfo = MutableStateFlow<AudioFileInfo?>(null)
    val currentInfo: StateFlow<AudioFileInfo?> = _currentInfo.asStateFlow()

    private val _positionFrames = MutableStateFlow(0L)
    val positionFrames: StateFlow<Long> = _positionFrames.asStateFlow()

    private val _latencyMs = MutableStateFlow(-1.0)
    val latencyMs: StateFlow<Double> = _latencyMs.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    companion object {
        private const val TARGET_SAMPLE_RATE = 48000
        private const val TARGET_CHANNELS = 2
        private const val FRAMES_PER_CHUNK = 480
        private const val MIN_FREE_FRAMES = 240

        init {
            System.loadLibrary("audioengine")
        }

        @JvmStatic
        private external fun nativeCreate(): Long

        @JvmStatic
        private external fun nativeDestroy(enginePtr: Long)

        @JvmStatic
        private external fun nativeStart(enginePtr: Long): Boolean

        @JvmStatic
        private external fun nativeStop(enginePtr: Long)

        @JvmStatic
        private external fun nativeGetLatencyMs(enginePtr: Long): Double

        @JvmStatic
        private external fun nativeWriteBuffer(
            enginePtr: Long, data: FloatArray, offset: Int, size: Int
        ): Int

        @JvmStatic
        private external fun nativeEnableSine(enginePtr: Long, enable: Boolean)

        @JvmStatic
        private external fun nativeIsSineEnabled(enginePtr: Long): Boolean

        @JvmStatic
        private external fun nativeAvailableWrite(enginePtr: Long): Int

        @JvmStatic
        private external fun nativeAvailableRead(enginePtr: Long): Int

        @JvmStatic
        private external fun nativeClearBuffer(enginePtr: Long)

        @JvmStatic
        private external fun nativeSetClockOffset(enginePtr: Long, offsetNs: Long)

        @JvmStatic
        private external fun nativeSetDriftRate(enginePtr: Long, ppm: Double)

        @JvmStatic
        private external fun nativeSetAnchor(enginePtr: Long, mediaTimeUs: Long, deviceTimeNs: Long)

        @JvmStatic
        private external fun nativeDisableDriftCorrection(enginePtr: Long)

        @JvmStatic
        private external fun nativeGetAgeNs(enginePtr: Long): Long
    }

    init {
        enginePtr = nativeCreate()
    }

    fun start(): Boolean {
        val ptr = enginePtr
        if (ptr == 0L) return false
        return nativeStart(ptr)
    }

    fun stop() {
        val ptr = enginePtr
        if (ptr != 0L) nativeStop(ptr)
    }

    fun getLatencyMs(): Double {
        val ptr = enginePtr
        if (ptr == 0L) return -1.0
        val lat = nativeGetLatencyMs(ptr)
        if (lat >= 0) _latencyMs.value = lat
        return lat
    }

    fun writePcmData(data: FloatArray): Int {
        val ptr = enginePtr
        if (ptr == 0L) return 0
        return nativeWriteBuffer(ptr, data, 0, data.size)
    }

    fun availableWrite(): Int {
        val ptr = enginePtr
        if (ptr == 0L) return 0
        return nativeAvailableWrite(ptr)
    }

    fun availableRead(): Int {
        val ptr = enginePtr
        if (ptr == 0L) return 0
        return nativeAvailableRead(ptr)
    }

    fun clearBuffer() {
        val ptr = enginePtr
        if (ptr != 0L) nativeClearBuffer(ptr)
    }

    fun enableSine(enable: Boolean) {
        val ptr = enginePtr
        if (ptr != 0L) nativeEnableSine(ptr, enable)
    }

    fun isSineEnabled(): Boolean {
        val ptr = enginePtr
        if (ptr == 0L) return false
        return nativeIsSineEnabled(ptr)
    }

    fun playFile(uri: Uri): Boolean {
        stopPlayback()
        val source = try {
            AudioSourceFactory.create(context, uri, TARGET_SAMPLE_RATE, TARGET_CHANNELS)
        } catch (e: Exception) {
            _lastError.value = e.message
            return false
        }
        synchronized(sourceLock) {
            currentSource = source
        }
        _currentInfo.value = source.info
        _lastError.value = null

        if (!start()) {
            _lastError.value = "Failed to start audio engine"
            return false
        }

        clearBuffer()
        _playbackState.value = PlaybackState.PLAYING
        startFiller(source)
        return true
    }

    fun pause() {
        if (_playbackState.value != PlaybackState.PLAYING) return
        fillerJob?.cancel()
        fillerJob = null
        _playbackState.value = PlaybackState.PAUSED
    }

    fun resume() {
        if (_playbackState.value != PlaybackState.PAUSED) return
        val source: AudioSource?
        synchronized(sourceLock) {
            source = currentSource
        }
        if (source == null) return
        _playbackState.value = PlaybackState.PLAYING
        startFiller(source)
    }

    fun seekToMediaTimeUs(timeUs: Long) {
        val source: AudioSource?
        synchronized(sourceLock) {
            source = currentSource
        }
        val src = source ?: return
        val info = src.info
        val frameIndex = (timeUs.toDouble() * info.sampleRate / 1_000_000.0).toLong()

        fillerJob?.cancel()
        fillerJob = null
        clearBuffer()

        src.seekToFrame(frameIndex.coerceIn(0, info.totalFrames))
        _positionFrames.value = src.getPositionFrames()

        if (_playbackState.value == PlaybackState.PLAYING) {
            startFiller(src)
        }
    }

    fun getMediaTimeUs(): Long {
        val info = _currentInfo.value ?: return 0L
        return (_positionFrames.value * 1_000_000L) / info.sampleRate
    }

    private fun startFiller(source: AudioSource) {
        fillerJob = fillerScope.launch {
            try {
                while (isActive && _playbackState.value == PlaybackState.PLAYING) {
                    while (availableWrite() < MIN_FREE_FRAMES) {
                        if (!isActive || _playbackState.value != PlaybackState.PLAYING) return@launch
                        delay(5)
                    }
                    val ptr = enginePtr
                    if (ptr == 0L) break

                    val chunk = source.readNext(FRAMES_PER_CHUNK)
                    if (chunk == null) {
                        while (isActive && availableRead() > 0) delay(50)
                        delay(200)
                        _playbackState.value = PlaybackState.STOPPED
                        break
                    }

                    nativeWriteBuffer(ptr, chunk, 0, chunk.size)
                    _positionFrames.value = source.getPositionFrames()
                }
            } catch (_: CancellationException) {
            } catch (e: Exception) {
                _lastError.value = e.message
                _playbackState.value = PlaybackState.STOPPED
            }
        }
    }

    fun stopPlayback() {
        fillerJob?.cancel()
        fillerJob = null
        synchronized(sourceLock) {
            currentSource?.close()
            currentSource = null
        }
        stop()
        _playbackState.value = PlaybackState.STOPPED
        _positionFrames.value = 0L
        _currentInfo.value = null
    }

    fun setClockOffset(offsetNs: Long) {
        val ptr = enginePtr
        if (ptr != 0L) nativeSetClockOffset(ptr, offsetNs)
    }

    fun setDriftRate(ppm: Double) {
        val ptr = enginePtr
        if (ptr != 0L) nativeSetDriftRate(ptr, ppm)
    }

    fun setAnchor(mediaTimeUs: Long, deviceTimeNs: Long) {
        val ptr = enginePtr
        if (ptr != 0L) nativeSetAnchor(ptr, mediaTimeUs, deviceTimeNs)
    }

    fun disableDriftCorrection() {
        val ptr = enginePtr
        if (ptr != 0L) nativeDisableDriftCorrection(ptr)
    }

    fun getAgeNs(): Long {
        val ptr = enginePtr
        if (ptr == 0L) return 0L
        return nativeGetAgeNs(ptr)
    }

    fun close() {
        stopPlayback()
        fillerScope.cancel()
        val ptr = enginePtr
        if (ptr != 0L) {
            nativeDestroy(ptr)
            enginePtr = 0L
        }
    }
}
