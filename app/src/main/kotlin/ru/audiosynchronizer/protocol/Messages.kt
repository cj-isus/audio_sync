package ru.audiosynchronizer.protocol

import kotlinx.serialization.Serializable

@Serializable
data class HelloMessage(
    val deviceName: String,
    val outputLatencyMs: Double = 0.0,
    val supportedCodecs: List<String> = listOf("pcm"),
    val protocolVersion: Int = 1
)

@Serializable
data class TimelineAnchorMessage(
    val mediaTimeUs: Long,
    val deviceTimeNs: Long,
    val sampleRate: Int,
    val playbackState: Int
) {
    companion object {
        const val STATE_STOPPED = 0
        const val STATE_PLAYING = 1
        const val STATE_PAUSED = 2
        const val STATE_SEEKING = 3
    }
}

@Serializable
data class ClockSyncMessage(
    val t1: Long,
    val t2: Long = 0L,
    val t3: Long = 0L
)

@Serializable
data class ControlMessage(
    val action: Int,
    val seekPositionUs: Long = 0L,
    val volume: Float = 1.0f
) {
    companion object {
        const val ACTION_PLAY = 0
        const val ACTION_PAUSE = 1
        const val ACTION_STOP = 2
        const val ACTION_SEEK = 3
        const val ACTION_SET_VOLUME = 4
    }
}

@Serializable
data class FileTransferMeta(
    val name: String,
    val size: Long,
    val sha256: String,
    val sampleRate: Int,
    val channels: Int,
    val durationMs: Long
)

@Serializable
data class HeartbeatMessage(
    val timestampNs: Long,
    val actualPlayoutTimeNs: Long = 0L,
    val scheduledPlayoutTimeNs: Long = 0L
)

@Serializable
sealed class Message {
    @Serializable
    data class Hello(val data: HelloMessage) : Message()

    @Serializable
    data class TimelineAnchor(val data: TimelineAnchorMessage) : Message()

    @Serializable
    data class ClockSync(val data: ClockSyncMessage) : Message()

    @Serializable
    data class Control(val data: ControlMessage) : Message()

    @Serializable
    data class FileMeta(val data: FileTransferMeta) : Message()

    @Serializable
    data class Heartbeat(val data: HeartbeatMessage) : Message()
}
