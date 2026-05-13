package ru.audiosynchronizer.protocol

import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

object MessageCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private const val HEADER_SIZE = 5

    fun encode(msg: Message): ByteArray {
        val type: Byte = when (msg) {
            is Message.Hello -> 0x01
            is Message.TimelineAnchor -> 0x02
            is Message.ClockSync -> 0x03
            is Message.Control -> 0x04
            is Message.FileMeta -> 0x05
            is Message.Heartbeat -> 0x06
        }
        val payload = json.encodeToString(msg).toByteArray(Charsets.UTF_8)
        val buf = ByteBuffer.allocate(HEADER_SIZE + payload.size)
        buf.put(type)
        buf.putShort(payload.size.toShort())
        buf.putShort(0)
        buf.put(payload)
        return buf.array()
    }

    fun decode(data: ByteArray, offset: Int = 0, length: Int = data.size): Message? {
        if (length < HEADER_SIZE) return null
        val buf = ByteBuffer.wrap(data, offset, length)
        val type = buf.get()
        val payloadLen = buf.short.toInt() and 0xFFFF
        buf.short

        if (length < HEADER_SIZE + payloadLen) return null

        val payloadBytes = ByteArray(payloadLen)
        buf.get(payloadBytes)
        val payloadJson = String(payloadBytes, Charsets.UTF_8)

        return try {
            when (type) {
                0x01.toByte() -> json.decodeFromString<Message.Hello>(payloadJson)
                0x02.toByte() -> json.decodeFromString<Message.TimelineAnchor>(payloadJson)
                0x03.toByte() -> json.decodeFromString<Message.ClockSync>(payloadJson)
                0x04.toByte() -> json.decodeFromString<Message.Control>(payloadJson)
                0x05.toByte() -> json.decodeFromString<Message.FileMeta>(payloadJson)
                0x06.toByte() -> json.decodeFromString<Message.Heartbeat>(payloadJson)
                else -> null
            }
        } catch (e: SerializationException) {
            null
        }
    }

    fun readMessage(input: InputStream): Message? {
        val header = ByteArray(HEADER_SIZE)
        var totalRead = 0
        while (totalRead < HEADER_SIZE) {
            val n = input.read(header, totalRead, HEADER_SIZE - totalRead)
            if (n < 0) return null
            totalRead += n
        }

        val buf = ByteBuffer.wrap(header)
        val type = buf.get()
        val payloadLen = buf.short.toInt() and 0xFFFF
        buf.short

        if (payloadLen <= 0 || payloadLen > 65536) return null

        val payload = ByteArray(payloadLen)
        totalRead = 0
        while (totalRead < payloadLen) {
            val n = input.read(payload, totalRead, payloadLen - totalRead)
            if (n < 0) return null
            totalRead += n
        }

        val payloadJson = String(payload, Charsets.UTF_8)
        return try {
            when (type) {
                0x01.toByte() -> json.decodeFromString<Message.Hello>(payloadJson)
                0x02.toByte() -> json.decodeFromString<Message.TimelineAnchor>(payloadJson)
                0x03.toByte() -> json.decodeFromString<Message.ClockSync>(payloadJson)
                0x04.toByte() -> json.decodeFromString<Message.Control>(payloadJson)
                0x05.toByte() -> json.decodeFromString<Message.FileMeta>(payloadJson)
                0x06.toByte() -> json.decodeFromString<Message.Heartbeat>(payloadJson)
                else -> null
            }
        } catch (e: SerializationException) {
            null
        }
    }

    fun writeMessage(output: OutputStream, msg: Message) {
        output.write(encode(msg))
        output.flush()
    }
}
