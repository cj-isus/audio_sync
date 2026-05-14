#pragma once

#include <atomic>
#include <cstdint>
#include <cstring>
#include <vector>

class RingBuffer {
public:
    RingBuffer(int32_t capacityInFrames, int32_t channelCount)
        : mChannelCount(channelCount),
          mCapacity(nextPow2(capacityInFrames)),
          mMask(mCapacity - 1),
          mBuffer(mCapacity * channelCount, 0.0f),
          mReadIndex(0),
          mWriteIndex(0) {}

    int32_t write(const float *data, int32_t frames) {
        int32_t avail = availableWrite();
        int32_t actual = (frames < avail) ? frames : avail;
        if (actual <= 0) return 0;

        int32_t wIdx = mWriteIndex.load(std::memory_order_relaxed);
        int32_t firstChunk = mCapacity - wIdx;
        if (firstChunk > actual) firstChunk = actual;

        copyFrames(data, 0, mBuffer.data(), wIdx, firstChunk);
        if (actual > firstChunk) {
            copyFrames(data, firstChunk, mBuffer.data(), 0, actual - firstChunk);
        }

        mWriteIndex.store((wIdx + actual) & mMask, std::memory_order_release);
        return actual;
    }

    int32_t read(float *data, int32_t frames) {
        int32_t avail = availableRead();
        int32_t actual = (frames < avail) ? frames : avail;
        if (actual <= 0) return 0;

        int32_t rIdx = mReadIndex.load(std::memory_order_relaxed);
        int32_t firstChunk = mCapacity - rIdx;
        if (firstChunk > actual) firstChunk = actual;

        copyFrames(mBuffer.data(), rIdx, data, 0, firstChunk);
        if (actual > firstChunk) {
            copyFrames(mBuffer.data(), 0, data, firstChunk, actual - firstChunk);
        }

        mReadIndex.store((rIdx + actual) & mMask, std::memory_order_release);
        return actual;
    }

    int32_t availableRead() const {
        int32_t w = mWriteIndex.load(std::memory_order_acquire);
        int32_t r = mReadIndex.load(std::memory_order_acquire);
        int32_t diff = w - r;
        if (diff < 0) diff += mCapacity;
        return diff;
    }

    int32_t availableWrite() const {
        int32_t avail = mCapacity - availableRead() - 1;
        return (avail >= 0) ? avail : 0;
    }

    int32_t capacity() const { return mCapacity; }

    void clear() {
        mReadIndex.store(0, std::memory_order_relaxed);
        mWriteIndex.store(0, std::memory_order_relaxed);
        std::fill(mBuffer.begin(), mBuffer.end(), 0.0f);
        std::atomic_thread_fence(std::memory_order_release);
    }

private:
    const int32_t mChannelCount;
    const int32_t mCapacity;
    const int32_t mMask;
    std::vector<float> mBuffer;
    std::atomic<int32_t> mReadIndex;
    std::atomic<int32_t> mWriteIndex;

    void copyFrames(const float *src, int32_t srcFrame,
                    float *dst, int32_t dstFrame,
                    int32_t count) {
        int32_t srcOff = srcFrame * mChannelCount;
        int32_t dstOff = dstFrame * mChannelCount;
        int32_t samples = count * mChannelCount;
        std::memcpy(dst + dstOff, src + srcOff, samples * sizeof(float));
    }

    static int32_t nextPow2(int32_t v) {
        if (v <= 0) return 1;
        v--;
        v |= v >> 1;
        v |= v >> 2;
        v |= v >> 4;
        v |= v >> 8;
        v |= v >> 16;
        return v + 1;
    }
};
