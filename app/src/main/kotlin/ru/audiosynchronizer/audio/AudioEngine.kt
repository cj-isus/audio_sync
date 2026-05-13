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

    private var enginePtr: Long = 0L

    private val fillerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var fillerJob: Job? = null
    private var currentSource: AudioSource? = null

    private val _playbackState = MutableStateFlow(PlaybackState.STOPPED)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _currentInfo = MutableStateFlow<AudioFileInfo?>(null)
    val currentInfo: StateFlow<AudioFileInfo?> = _currentInfo.asStateFlow()

    private val _positionFrames = MutableStateFlow(0L)
    val positionFrames: StateFlow<Long> = _positionFrames.asStateFlow()

    private val _latencyMs = MutableStateFlow(-1.0)
    val latencyMs: StateFlow<Double> = _latencyMs.asStateFlow()

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
        if (enginePtr == 0L) return false
        return nativeStart(enginePtr)
    }

    fun stop() {
        if (enginePtr != 0L) nativeStop(enginePtr)
    }

    fun getLatencyMs(): Double {
        if (enginePtr == 0L) return -1.0
        val lat = nativeGetLatencyMs(enginePtr)
        if (lat >= 0) _latencyMs.value = lat
        return lat
    }

    fun writePcmData(data: FloatArray): Int {
        if (enginePtr == 0L) return 0
        return nativeWriteBuffer(enginePtr, data, 0, data.size)
    }

    fun availableWrite(): Int {
        if (enginePtr == 0L) return 0
        return nativeAvailableWrite(enginePtr)
    }

    fun availableRead(): Int {
        if (enginePtr == 0L) return 0
        return nativeAvailableRead(enginePtr)
    }

    fun clearBuffer() {
        if (enginePtr != 0L) nativeClearBuffer(enginePtr)
    }

    fun setClockOffset(offsetNs: Long) {
        if (enginePtr != 0L) nativeSetClockOffset(enginePtr, offsetNs)
    }

    fun setDriftRate(ppm: Double) {
        if (enginePtr != 0L) nativeSetDriftRate(enginePtr, ppm)
    }

    fun setAnchor(mediaTimeUs: Long, deviceTimeNs: Long) {
        if (enginePtr != 0L) nativeSetAnchor(enginePtr, mediaTimeUs, deviceTimeNs)
    }

    fun disableDriftCorrection() {
        if (enginePtr != 0L) nativeDisableDriftCorrection(enginePtr)
    }

    fun getAgeNs(): Long {
        if (enginePtr == 0L) return 0L
        return nativeGetAgeNs(enginePtr)
    }

    fun enableSine(enable: Boolean) {
        if (enginePtr != 0L) nativeEnableSine(enginePtr, enable)
    }

    fun isSineEnabled(): Boolean {
        if (enginePtr == 0L) return false
        return nativeIsSineEnabled(enginePtr)
    }

    fun playFile(uri: Uri): Boolean {
        stopPlayback()

        val source = try {
            AudioSourceFactory.create(context, uri, TARGET_SAMPLE_RATE, TARGET_CHANNELS)
        } catch (e: Exception) {
            return false
        }

        currentSource = source
        _currentInfo.value = source.info

        if (!start()) return false

        clearBuffer()
        _playbackState.value = PlaybackState.PLAYING

        fillerJob = fillerScope.launch {
            try {
                while (isActive && _playbackState.value == PlaybackState.PLAYING) {
                    while (availableWrite() < MIN_FREE_FRAMES) {
                        if (!isActive || _playbackState.value != PlaybackState.PLAYING) return@launch
                        delay(5)
                    }

                    val chunk = source.readNext(FRAMES_PER_CHUNK)
                    if (chunk == null) {
                        while (isActive && availableRead() > 0) {
                            delay(50)
                        }
                        delay(200)
                        _playbackState.value = PlaybackState.STOPPED
                        break
                    }

                    nativeWriteBuffer(enginePtr, chunk, 0, chunk.size)
                    _positionFrames.value = source.getPositionFrames()
                }
            } catch (e: CancellationException) {
                // expected on stop/pause
            } catch (e: Exception) {
                _playbackState.value = PlaybackState.STOPPED
            }
        }
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
        val source = currentSource ?: return

        _playbackState.value = PlaybackState.PLAYING

        fillerJob = fillerScope.launch {
            try {
                while (isActive && _playbackState.value == PlaybackState.PLAYING) {
                    while (availableWrite() < MIN_FREE_FRAMES) {
                        if (!isActive || _playbackState.value != PlaybackState.PLAYING) return@launch
                        delay(5)
                    }

                    val chunk = source.readNext(FRAMES_PER_CHUNK)
                    if (chunk == null) {
                        while (isActive && availableRead() > 0) delay(50)
                        delay(200)
                        _playbackState.value = PlaybackState.STOPPED
                        break
                    }

                    nativeWriteBuffer(enginePtr, chunk, 0, chunk.size)
                    _positionFrames.value = source.getPositionFrames()
                }
            } catch (_: CancellationException) {
            } catch (e: Exception) {
                _playbackState.value = PlaybackState.STOPPED
            }
        }
    }

    fun seekToMediaTimeUs(timeUs: Long) {
        val source = currentSource ?: return
        val info = source.info
        val frameIndex = (timeUs * info.sampleRate) / 1_000_000L

        fillerJob?.cancel()
        fillerJob = null
        clearBuffer()

        source.seekToFrame(frameIndex.coerceIn(0, info.totalFrames))
        _positionFrames.value = source.getPositionFrames()

        if (_playbackState.value == PlaybackState.PLAYING) {
            startFiller(source)
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

                    val chunk = source.readNext(FRAMES_PER_CHUNK)
                    if (chunk == null) {
                        while (isActive && availableRead() > 0) delay(50)
                        delay(200)
                        _playbackState.value = PlaybackState.STOPPED
                        break
                    }

                    nativeWriteBuffer(enginePtr, chunk, 0, chunk.size)
                    _positionFrames.value = source.getPositionFrames()
                }
            } catch (_: CancellationException) {
            } catch (e: Exception) {
                _playbackState.value = PlaybackState.STOPPED
            }
        }
    }

    fun stopPlayback() {
        fillerJob?.cancel()
        fillerJob = null
        currentSource?.close()
        currentSource = null
        stop()
        _playbackState.value = PlaybackState.STOPPED
        _positionFrames.value = 0L
        _currentInfo.value = null
    }

    fun close() {
        stopPlayback()
        if (enginePtr != 0L) {
            nativeDestroy(enginePtr)
            enginePtr = 0L
        }
    }
}
