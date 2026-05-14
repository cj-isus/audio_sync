package ru.audiosynchronizer.sync

class MedianFilter(private val capacity: Int = 200) {

    private val buffer = DoubleArray(capacity)
    private var count: Int = 0
    private var writeIndex: Int = 0

    @Synchronized
    fun add(value: Double) {
        buffer[writeIndex] = value
        writeIndex = (writeIndex + 1) % capacity
        if (count < capacity) count++
    }

    @Synchronized
    fun median(): Double {
        if (count == 0) return 0.0
        val sorted = buffer.copyOf(count).sorted()
        val trim = count / 4
        if (trim == 0) return sorted[count / 2]
        val midRange = sorted.subList(trim, count - trim)
        if (midRange.isEmpty()) return sorted[count / 2]
        return midRange.sum() / midRange.size
    }

    @Synchronized
    fun clear() {
        count = 0
        writeIndex = 0
    }

    @Synchronized
    fun size(): Int = count

    @Synchronized
    fun isFull(): Boolean = count >= capacity
}
