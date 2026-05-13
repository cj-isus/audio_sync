package ru.audiosynchronizer.audio

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object AudioSourceFactory {

    private val WAV_EXTENSIONS = setOf("wav", "wave", "pcm")
    private val MEDIA_CODEC_EXTENSIONS = setOf("mp3", "flac", "ogg", "aac", "m4a", "opus", "wma", "3gp", "amr")

    fun create(
        context: Context,
        uri: Uri,
        targetSampleRate: Int = 48000,
        targetChannels: Int = 2
    ): AudioSource {
        val fileName = getFileName(context, uri)
        val extension = fileName.substringAfterLast('.', "").lowercase()

        return when {
            extension in WAV_EXTENSIONS -> {
                PcmFileReader(context, uri, targetSampleRate, targetChannels)
            }
            extension in MEDIA_CODEC_EXTENSIONS -> {
                val cachedFile = copyToCache(context, uri)
                val cachedUri = Uri.fromFile(cachedFile)
                try {
                    MediaCodecDecoder(context, cachedUri, targetSampleRate, targetChannels)
                } catch (e: Exception) {
                    PcmFileReader(context, uri, targetSampleRate, targetChannels)
                }
            }
            else -> {
                try {
                    val cachedFile = copyToCache(context, uri)
                    val cachedUri = Uri.fromFile(cachedFile)
                    MediaCodecDecoder(context, cachedUri, targetSampleRate, targetChannels)
                } catch (e: Exception) {
                    throw IllegalArgumentException("Unsupported audio format: $extension")
                }
            }
        }
    }

    private fun getFileName(context: Context, uri: Uri): String {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIdx >= 0) cursor.getString(nameIdx) else uri.lastPathSegment ?: "unknown"
        } ?: uri.lastPathSegment ?: "unknown"
    }

    private fun copyToCache(context: Context, uri: Uri): File {
        val fileName = getFileName(context, uri)
        val cacheDir = File(context.cacheDir, "audio_cache")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val outFile = File(cacheDir, fileName)

        if (outFile.exists() && outFile.length() > 0) return outFile

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(outFile).use { output ->
                val buf = ByteArray(8192)
                var n: Int
                while (input.read(buf).also { n = it } > 0) {
                    output.write(buf, 0, n)
                }
            }
        } ?: throw IllegalArgumentException("Cannot open URI: $uri")

        return outFile
    }

    fun clearCache(context: Context) {
        val cacheDir = File(context.cacheDir, "audio_cache")
        if (cacheDir.exists()) cacheDir.deleteRecursively()
    }
}
