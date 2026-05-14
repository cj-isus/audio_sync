package ru.audiosynchronizer.audio

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

object AudioSourceFactory {

    private const val TAG = "AudioSourceFactory"

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
                    Log.w(TAG, "MediaCodec failed for $extension, falling back to PcmFileReader", e)
                    PcmFileReader(context, uri, targetSampleRate, targetChannels)
                }
            }
            else -> {
                try {
                    val cachedFile = copyToCache(context, uri)
                    val cachedUri = Uri.fromFile(cachedFile)
                    MediaCodecDecoder(context, cachedUri, targetSampleRate, targetChannels)
                } catch (e: Exception) {
                    throw IllegalArgumentException("Unsupported audio format: $extension", e)
                }
            }
        }
    }

    private fun getFileName(context: Context, uri: Uri): String {
        val raw = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIdx >= 0) cursor.getString(nameIdx) else uri.lastPathSegment ?: "unknown"
        } ?: uri.lastPathSegment ?: "unknown"
        return sanitizeFileName(raw)
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }

    @Synchronized
    private fun copyToCache(context: Context, uri: Uri): File {
        val fileName = getFileName(context, uri)
        val cacheDir = File(context.cacheDir, "audio_cache")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val outFile = File(cacheDir, fileName)

        if (outFile.exists() && outFile.length() > 0 && verifyCacheFile(context, uri, outFile)) {
            return outFile
        }

        val tmpFile = File(cacheDir, "$fileName.tmp")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tmpFile).use { output ->
                    val buf = ByteArray(8192)
                    var n: Int
                    while (input.read(buf).also { n = it } > 0) {
                        output.write(buf, 0, n)
                    }
                }
            } ?: throw IllegalArgumentException("Cannot open URI: $uri")

            if (outFile.exists()) outFile.delete()
            if (!tmpFile.renameTo(outFile)) {
                tmpFile.copyTo(outFile, overwrite = true)
                tmpFile.delete()
            }
        } catch (e: Exception) {
            tmpFile.delete()
            throw e
        }

        return outFile
    }

    private fun verifyCacheFile(context: Context, uri: Uri, cachedFile: File): Boolean {
        return try {
            val uriHash = computeHash(context, uri)
            val fileHash = computeFileHash(cachedFile)
            uriHash.isNotEmpty() && uriHash == fileHash
        } catch (_: Exception) {
            false
        }
    }

    private fun computeHash(context: Context, uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buf = ByteArray(8192)
            var n: Int
            while (input.read(buf).also { n = it } > 0) {
                digest.update(buf, 0, n)
            }
        } ?: return ""
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun computeFileHash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            var n: Int
            while (input.read(buf).also { n = it } > 0) {
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun clearCache(context: Context) {
        val cacheDir = File(context.cacheDir, "audio_cache")
        if (cacheDir.exists()) cacheDir.deleteRecursively()
    }
}
