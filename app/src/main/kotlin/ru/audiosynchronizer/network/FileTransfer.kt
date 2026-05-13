package ru.audiosynchronizer.network

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.audiosynchronizer.protocol.FileTransferMeta
import ru.audiosynchronizer.protocol.Message
import ru.audiosynchronizer.protocol.MessageCodec
import java.io.*
import java.net.Socket
import java.security.MessageDigest

data class FileTransferProgress(
    val fileName: String = "",
    val bytesTransferred: Long = 0,
    val totalBytes: Long = 0,
    val isComplete: Boolean = false,
    val isFailed: Boolean = false,
    val error: String? = null
) {
    val progress: Float get() = if (totalBytes > 0) (bytesTransferred.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
}

class FileTransfer(private val context: Context) {

    private val _progress = MutableStateFlow(FileTransferProgress())
    val progress: StateFlow<FileTransferProgress> = _progress.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var transferJob: Job? = null

    companion object {
        private const val TAG = "FileTransfer"
        private const val CHUNK_SIZE = 64 * 1024
    }

    fun sendFile(outputStream: OutputStream, filePath: String, sha256: String) {
        transferJob?.cancel()
        transferJob = scope.launch {
            try {
                val file = File(filePath)
                if (!file.exists()) {
                    _progress.value = FileTransferProgress(isFailed = true, error = "File not found")
                    return@launch
                }

                val meta = FileTransferMeta(
                    name = file.name,
                    size = file.length(),
                    sha256 = sha256,
                    sampleRate = 48000,
                    channels = 2,
                    durationMs = 0L
                )
                MessageCodec.writeMessage(outputStream, Message.FileMeta(meta))
                _progress.value = FileTransferProgress(
                    fileName = file.name,
                    totalBytes = file.length()
                )

                val buf = ByteArray(CHUNK_SIZE)
                var transferred = 0L
                FileInputStream(file).use { input ->
                    while (coroutineContext.isActive) {
                        val n = input.read(buf)
                        if (n < 0) break
                        outputStream.write(buf, 0, n)
                        outputStream.flush()
                        transferred += n
                        _progress.value = _progress.value.copy(bytesTransferred = transferred)
                    }
                }

                _progress.value = _progress.value.copy(isComplete = true)
                Log.i(TAG, "File sent: ${file.name}, $transferred bytes")
            } catch (e: CancellationException) {
                // expected
            } catch (e: Exception) {
                Log.e(TAG, "Send failed", e)
                _progress.value = FileTransferProgress(isFailed = true, error = e.message)
            }
        }
    }

    fun receiveFile(inputStream: InputStream, meta: FileTransferMeta): File? {
        val cacheDir = File(context.cacheDir, "audio_sync")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val outFile = File(cacheDir, meta.name)
        val tmpFile = File(cacheDir, "${meta.name}.tmp")

        return try {
            _progress.value = FileTransferProgress(
                fileName = meta.name,
                totalBytes = meta.size
            )

            val digest = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(CHUNK_SIZE)
            var transferred = 0L

            FileOutputStream(tmpFile).use { output ->
                while (transferred < meta.size) {
                    val remaining = (meta.size - transferred).coerceAtMost(CHUNK_SIZE.toLong()).toInt()
                    val n = inputStream.read(buf, 0, remaining)
                    if (n < 0) break
                    output.write(buf, 0, n)
                    digest.update(buf, 0, n)
                    transferred += n
                    _progress.value = _progress.value.copy(bytesTransferred = transferred)
                }
            }

            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            if (hash == meta.sha256) {
                tmpFile.renameTo(outFile)
                _progress.value = _progress.value.copy(isComplete = true)
                Log.i(TAG, "File received and verified: ${meta.name}")
                outFile
            } else {
                tmpFile.delete()
                _progress.value = FileTransferProgress(isFailed = true, error = "SHA-256 mismatch")
                Log.e(TAG, "SHA-256 mismatch for ${meta.name}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Receive failed", e)
            tmpFile.delete()
            _progress.value = FileTransferProgress(isFailed = true, error = e.message)
            null
        }
    }

    fun cancel() {
        transferJob?.cancel()
        transferJob = null
    }
}
