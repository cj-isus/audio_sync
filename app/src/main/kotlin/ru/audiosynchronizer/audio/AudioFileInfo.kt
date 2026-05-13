package ru.audiosynchronizer.audio

import android.content.Context
import android.net.Uri
import java.security.MessageDigest

data class AudioFileInfo(
    val sampleRate: Int,
    val channels: Int,
    val durationMs: Long,
    val sha256: String,
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val size: Long,
    val totalFrames: Long
) {
    companion object {
        fun computeHash(context: Context, uri: Uri): String {
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
    }
}
