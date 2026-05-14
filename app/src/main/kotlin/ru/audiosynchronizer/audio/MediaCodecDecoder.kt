package ru.audiosynchronizer.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns

class MediaCodecDecoder(
    private val context: Context,
    private val uri: Uri,
    private val targetSampleRate: Int = 48000,
    private val targetChannels: Int = 2
) : AudioSource {

    override lateinit var info: AudioFileInfo
        private set

    override val sampleRate: Int
        get() = info.sampleRate

    override val channels: Int
        get() = info.channels

    override val totalFrames: Long
        get() = info.totalFrames

    private var _positionFrames: Long = 0
    override fun getPositionFrames(): Long = _positionFrames

    private val extractor = MediaExtractor()
    private var decoder: MediaCodec? = null
    private var sourceSampleRate = 0
    private var sourceChannels = 0
    private var isEos = false
    private var isDecoderStarted = false
    private var inputEos = false
    private var trackIndex = -1

    private val resampleRatio: Double
        get() = if (sourceSampleRate > 0) targetSampleRate.toDouble() / sourceSampleRate.toDouble() else 1.0

    init {
        setup()
    }

    private fun setup() {
        when (uri.scheme) {
            "file" -> {
                extractor.setDataSource(uri.path ?: throw IllegalArgumentException("Invalid file URI: $uri"))
            }
            else -> {
                val afd = context.contentResolver.openAssetFileDescriptor(uri, "r")
                    ?: throw IllegalArgumentException("Cannot open URI: $uri")
                try {
                    extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                } finally {
                    try { afd.close() } catch (_: Exception) {}
                }
            }
        }

        trackIndex = findAudioTrack()
        if (trackIndex < 0) throw IllegalArgumentException("No audio track found in file")

        extractor.selectTrack(trackIndex)
        val format = extractor.getTrackFormat(trackIndex)

        sourceSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        sourceChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
            format.getLong(MediaFormat.KEY_DURATION)
        } else 0L

        val durationMs = durationUs / 1000
        val totalFrames = if (sourceSampleRate > 0) (durationUs * sourceSampleRate / 1_000_000) else 0L

        val displayName = queryDisplayName()
        val size = querySize()

        info = AudioFileInfo(
            sampleRate = sourceSampleRate,
            channels = sourceChannels,
            durationMs = durationMs,
            sha256 = AudioFileInfo.computeHash(context, uri),
            uri = uri,
            displayName = displayName,
            mimeType = format.getString(MediaFormat.KEY_MIME) ?: "audio/*",
            size = size,
            totalFrames = totalFrames
        )
    }

    private fun findAudioTrack(): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) return i
        }
        return -1
    }

    private fun ensureDecoderStarted() {
        if (isDecoderStarted) return
        val format = extractor.getTrackFormat(trackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: throw IllegalStateException("No MIME type")

        decoder = MediaCodec.createDecoderByType(mime)
        decoder?.configure(format, null, null, 0)
        decoder?.start()
        isDecoderStarted = true
    }

    override fun readNext(frames: Int): FloatArray? {
        ensureDecoderStarted()
        if (isEos) return null

        val dec = decoder ?: return null
        val buffer = mutableListOf<Float>()

        while (buffer.size < frames * targetChannels) {
            if (!inputEos) {
                val inputBufIdx = dec.dequeueInputBuffer(10_000)
                if (inputBufIdx >= 0) {
                    val inputBuf = dec.getInputBuffer(inputBufIdx) ?: continue
                    val sampleSize = extractor.readSampleData(inputBuf, 0)
                    if (sampleSize < 0) {
                        dec.queueInputBuffer(inputBufIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputEos = true
                    } else {
                        val presentationTimeUs = extractor.sampleTime
                        dec.queueInputBuffer(inputBufIdx, 0, sampleSize, presentationTimeUs, 0)
                        extractor.advance()
                    }
                }
            }

            val bufInfo = MediaCodec.BufferInfo()
            val outputBufIdx = dec.dequeueOutputBuffer(bufInfo, 10_000)
            if (outputBufIdx >= 0) {
                if (bufInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    isEos = true
                    dec.releaseOutputBuffer(outputBufIdx, false)
                    break
                }

                val outputBuf = dec.getOutputBuffer(outputBufIdx)
                if (outputBuf != null && bufInfo.size > 0) {
                    val pcm = convertOutputToFloat(dec, outputBuf, bufInfo)
                    buffer.addAll(pcm.toList())
                }
                dec.releaseOutputBuffer(outputBufIdx, false)
            } else if (outputBufIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val newFormat = dec.outputFormat
                if (newFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                    sourceSampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                }
                if (newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                    sourceChannels = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                }
            }
        }

        if (buffer.isEmpty()) return null

        var pcm = buffer.toFloatArray()

        if (sourceChannels == 1 && targetChannels == 2) {
            pcm = Resampler.resampleMonoToStereo(pcm)
        }

        if (sourceSampleRate != targetSampleRate && sourceSampleRate > 0) {
            val outCh = if (sourceChannels == 1 && targetChannels == 2) 2 else sourceChannels
            pcm = Resampler.resampleWithRatio(pcm, outCh, sourceSampleRate, targetSampleRate)
        }

        val neededSamples = frames * targetChannels
        val result = if (pcm.size > neededSamples) {
            pcm.copyOf(neededSamples)
        } else {
            pcm
        }

        _positionFrames += result.size / targetChannels
        return result
    }

    private fun convertOutputToFloat(
        dec: MediaCodec,
        buf: java.nio.ByteBuffer,
        info: MediaCodec.BufferInfo
    ): FloatArray {
        buf.position(info.offset)
        buf.limit(info.offset + info.size)

        val outputFormat = dec.outputFormat
        val isPcmFloat = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING) == 4
        } else false

        return if (isPcmFloat) {
            val floatBuf = buf.order(java.nio.ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
            val arr = FloatArray(floatBuf.remaining())
            floatBuf.get(arr)
            arr
        } else {
            val shortBuf = buf.order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            val shortArr = ShortArray(shortBuf.remaining())
            shortBuf.get(shortArr)
            FloatArray(shortArr.size) { shortArr[it] / 32768f }
        }
    }

    override fun seekToFrame(frameIndex: Long) {
        val dec = decoder
        if (dec != null && isDecoderStarted) {
            try { dec.stop() } catch (_: Exception) {}
            try { dec.release() } catch (_: Exception) {}
        }
        decoder = null
        isDecoderStarted = false
        isEos = false
        inputEos = false

        extractor.selectTrack(trackIndex)
        val timeUs = frameIndex * 1_000_000L / sourceSampleRate.coerceAtLeast(1)
        extractor.seekTo(timeUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

        val format = extractor.getTrackFormat(trackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return
        val newDecoder = MediaCodec.createDecoderByType(mime)
        newDecoder.configure(format, null, null, 0)
        newDecoder.start()
        decoder = newDecoder
        isDecoderStarted = true
        _positionFrames = frameIndex
    }

    override fun isAtEnd(): Boolean = isEos

    override fun close() {
        try { decoder?.stop() } catch (_: Exception) {}
        try { decoder?.release() } catch (_: Exception) {}
        try { extractor.release() } catch (_: Exception) {}
        decoder = null
        isDecoderStarted = false
    }

    private fun queryDisplayName(): String {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIdx >= 0) cursor.getString(nameIdx) else uri.lastPathSegment ?: "unknown"
        } ?: uri.lastPathSegment ?: "unknown"
    }

    private fun querySize(): Long {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst() && sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L
        } ?: 0L
    }
}
