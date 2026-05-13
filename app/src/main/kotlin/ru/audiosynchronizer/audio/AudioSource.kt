package ru.audiosynchronizer.audio

interface AudioSource : AutoCloseable {
    val info: AudioFileInfo
    val sampleRate: Int
    val channels: Int
    val totalFrames: Long
    fun getPositionFrames(): Long

    fun readNext(frames: Int): FloatArray?
    fun seekToFrame(frameIndex: Long)
    fun isAtEnd(): Boolean
}
