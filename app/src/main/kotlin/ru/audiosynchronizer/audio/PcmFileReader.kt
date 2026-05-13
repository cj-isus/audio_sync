package ru.audiosynchronizer.audio

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.DataInputStream
import java.io.InputStream

class PcmFileReader(
    private val context: Context,
    private val uri: Uri,
    private val targetSampleRate: Int = 48000,
    private val targetChannels: Int = 2
) : AudioSource {

    private var inputStream: InputStream? = null
    private var dataStream: DataInputStream? = null

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

    private var wavSampleRate = 0
    private var wavChannels = 0
    private var wavBitsPerSample = 0
    private var wavIsFloat = false
    private var wavDataSize = 0L
    private var wavDataOffset = 0L

    private val resampleRatio: Double
        get() = targetSampleRate.toDouble() / wavSampleRate.toDouble()

    init {
        parseHeader()
    }

    private fun parseHeader() {
        val ins = context.contentResolver.openInputStream(uri) ?: throw IllegalArgumentException("Cannot open URI: $uri")
        inputStream = ins
        val dis = DataInputStream(ins)
        dataStream = dis

        val riff = ByteArray(4)
        dis.readFully(riff)
        require(String(riff) == "RIFF") { "Not a WAV file: missing RIFF header" }

        dis.readUnsignedIntLE()

        val wave = ByteArray(4)
        dis.readFully(wave)
        require(String(wave) == "WAVE") { "Not a WAV file: missing WAVE marker" }

        var foundFmt = false
        var foundData = false

        while (!foundData) {
            val chunkId = ByteArray(4)
            dis.readFully(chunkId)
            val chunkSize = dis.readUnsignedIntLE()

            when (String(chunkId)) {
                "fmt " -> {
                    val audioFormat = dis.readUnsignedShortLE()
                    wavChannels = dis.readUnsignedShortLE()
                    wavSampleRate = dis.readUnsignedIntLE().toInt()
                    dis.readUnsignedIntLE()
                    dis.readUnsignedShortLE()
                    wavBitsPerSample = dis.readUnsignedShortLE()

                    wavIsFloat = audioFormat == 0x0003
                    if (audioFormat != 0x0001 && audioFormat != 0x0003) {
                        throw IllegalArgumentException("Unsupported WAV format: 0x${audioFormat.toString(16)}")
                    }
                    if (chunkSize > 16) dis.skipBytes((chunkSize - 16).toInt())
                    foundFmt = true
                }
                "data" -> {
                    wavDataSize = chunkSize
                    wavDataOffset = 0
                    foundData = true
                }
                else -> {
                    dis.skipBytes(chunkSize.toInt().coerceAtMost(Int.MAX_VALUE))
                }
            }
        }

        val bytesPerSample = wavBitsPerSample / 8
        val sourceTotalFrames = if (bytesPerSample > 0 && wavChannels > 0) {
            wavDataSize / (bytesPerSample * wavChannels)
        } else 0L

        val effectiveSampleRate = if (wavSampleRate > 0) wavSampleRate else targetSampleRate
        val effectiveChannels = if (wavChannels > 0) wavChannels else targetChannels

        val durationMs = if (effectiveSampleRate > 0) {
            (sourceTotalFrames * 1000L / effectiveSampleRate)
        } else 0L

        val displayName = queryDisplayName()
        val size = querySize()

        info = AudioFileInfo(
            sampleRate = effectiveSampleRate,
            channels = effectiveChannels,
            durationMs = durationMs,
            sha256 = AudioFileInfo.computeHash(context, uri),
            uri = uri,
            displayName = displayName,
            mimeType = "audio/wav",
            size = size,
            totalFrames = sourceTotalFrames
        )
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

    override fun readNext(frames: Int): FloatArray? {
        val dis = dataStream ?: return null
        val bytesPerSample = wavBitsPerSample / 8
        val sourceFrames = if (resampleRatio != 1.0) {
            (frames / resampleRatio).toInt().coerceAtLeast(1)
        } else {
            frames
        }

        val samplesToRead = sourceFrames * wavChannels
        val raw = readRawSamples(dis, samplesToRead, bytesPerSample)
        if (raw.isEmpty()) return null

        val actualFrames = raw.size / wavChannels

        var pcm: FloatArray
        if (wavChannels == 1 && targetChannels == 2) {
            val mono = if (raw.size < samplesToRead) {
                raw.copyOf(samplesToRead).also { arr ->
                    for (i in raw.size until samplesToRead) arr[i] = 0f
                }
            } else raw
            pcm = Resampler.resampleMonoToStereo(mono)
        } else {
            pcm = raw
        }

        if (wavSampleRate != targetSampleRate && wavSampleRate > 0) {
            val outCh = if (wavChannels == 1 && targetChannels == 2) 2 else wavChannels
            pcm = Resampler.resampleWithRatio(pcm, outCh, wavSampleRate, targetSampleRate)
        }

        _positionFrames += actualFrames
        return pcm
    }

    private fun readRawSamples(dis: DataInputStream, count: Int, bytesPerSample: Int): FloatArray {
        val result = FloatArray(count)
        for (i in 0 until count) {
            try {
                result[i] = when {
                    wavIsFloat && bytesPerSample == 4 -> dis.readFloat()
                    !wavIsFloat && bytesPerSample == 2 -> dis.readShort() / 32768f
                    !wavIsFloat && bytesPerSample == 3 -> readInt24(dis) / 8388608f
                    !wavIsFloat && bytesPerSample == 1 -> (dis.readUnsignedByte() - 128) / 128f
                    else -> {
                        if (i == 0) return FloatArray(0)
                        return result.copyOf(i)
                    }
                }
            } catch (e: java.io.EOFException) {
                return result.copyOf(i)
            } catch (e: java.io.IOException) {
                return result.copyOf(i)
            }
        }
        return result
    }

    private fun readInt24(dis: DataInputStream): Int {
        val b0 = dis.readUnsignedByte()
        val b1 = dis.readUnsignedByte()
        val b2 = dis.readUnsignedByte()
        var value = (b2 shl 16) or (b1 shl 8) or b0
        if (value and 0x800000 != 0) value = value or 0xFF000000.toInt()
        return value
    }

    override fun seekToFrame(frameIndex: Long) {
        close()
        val ins = context.contentResolver.openInputStream(uri) ?: return
        inputStream = ins
        val dis = DataInputStream(ins)
        dataStream = dis

        val riff = ByteArray(4)
        dis.readFully(riff)
        dis.readUnsignedIntLE()
        val wave = ByteArray(4)
        dis.readFully(wave)

        var foundData = false
        while (!foundData) {
            val chunkId = ByteArray(4)
            dis.readFully(chunkId)
            val chunkSize = dis.readUnsignedIntLE()
            if (String(chunkId) == "data") {
                foundData = true
            } else {
                dis.skipBytes(chunkSize.toInt().coerceAtMost(Int.MAX_VALUE))
            }
        }

        val bytesPerSample = wavBitsPerSample / 8
        val skipBytes = frameIndex * bytesPerSample * wavChannels
        var remaining = skipBytes
        while (remaining > 0) {
            val skipped = dis.skip(remaining.coerceAtMost(Int.MAX_VALUE.toLong()))
            if (skipped <= 0) break
            remaining -= skipped
        }
        _positionFrames = frameIndex
    }

    override fun isAtEnd(): Boolean {
        return try {
            dataStream?.let { it.available() <= 0 } ?: true
        } catch (e: java.io.IOException) {
            true
        }
    }

    override fun close() {
        try { dataStream?.close() } catch (_: Exception) {}
        try { inputStream?.close() } catch (_: Exception) {}
        dataStream = null
        inputStream = null
    }

    private fun DataInputStream.readUnsignedShortLE(): Int {
        val lo = readUnsignedByte()
        val hi = readUnsignedByte()
        return (hi shl 8) or lo
    }

    private fun DataInputStream.readUnsignedIntLE(): Long {
        val b0 = readUnsignedByte().toLong()
        val b1 = readUnsignedByte().toLong()
        val b2 = readUnsignedByte().toLong()
        val b3 = readUnsignedByte().toLong()
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }
}
