package ru.audiosynchronizer.audio

class AudioEngine {

    private var enginePtr: Long = 0L

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
        return nativeGetLatencyMs(enginePtr)
    }

    fun writePcmData(data: FloatArray): Int {
        if (enginePtr == 0L) return 0
        return nativeWriteBuffer(enginePtr, data, 0, data.size)
    }

    fun availableWrite(): Int {
        if (enginePtr == 0L) return 0
        return nativeAvailableWrite(enginePtr)
    }

    fun enableSine(enable: Boolean) {
        if (enginePtr != 0L) nativeEnableSine(enginePtr, enable)
    }

    fun isSineEnabled(): Boolean {
        if (enginePtr == 0L) return false
        return nativeIsSineEnabled(enginePtr)
    }

    fun close() {
        if (enginePtr != 0L) {
            nativeDestroy(enginePtr)
            enginePtr = 0L
        }
    }

    companion object {
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
    }
}
