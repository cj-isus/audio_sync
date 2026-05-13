#pragma once

#include <atomic>
#include <cstdint>
#include <cstring>
#include <vector>

class RingBuffer {
public:
    RingBuffer(int32_t capacityInFrames, int32_t channelCount)
        : mChannelCount(channelCount),
          mCapacity(capacityInFrames * channelCount),
          mBuffer(mCapacity, 0.0f),
          mReadIndex(0),
          mWriteIndex(0) {}

    int32_t write(const float *data, int32_t frames) {
        int32_t samplesToWrite = frames * mChannelCount;
        int32_t available = availableWrite() * mChannelCount;
        int32_t actualSamples = (samplesToWrite < available) ? samplesToWrite : available;

        for (int32_t i = 0; i < actualSamples; i++) {
            mBuffer[mWriteIndex.load(std::memory_order_relaxed)] = data[i];
            mWriteIndex.store((mWriteIndex.load(std::memory_order_relaxed) + 1) % mCapacity,
                              std::memory_order_release);
        }
        return actualSamples / mChannelCount;
    }

    int32_t read(float *data, int32_t frames) {
        int32_t samplesToRead = frames * mChannelCount;
        int32_t available = availableRead() * mChannelCount;
        int32_t actualSamples = (samplesToRead < available) ? samplesToRead : available;

        for (int32_t i = 0; i < actualSamples; i++) {
            data[i] = mBuffer[mReadIndex.load(std::memory_order_relaxed)];
            mReadIndex.store((mReadIndex.load(std::memory_order_relaxed) + 1) % mCapacity,
                             std::memory_order_release);
        }
        return actualSamples / mChannelCount;
    }

    int32_t availableRead() const {
        int32_t w = mWriteIndex.load(std::memory_order_acquire);
        int32_t r = mReadIndex.load(std::memory_order_acquire);
        int32_t diff = w - r;
        if (diff < 0) diff += mCapacity;
        return diff / mChannelCount;
    }

    int32_t availableWrite() const {
        return (mCapacity / mChannelCount) - availableRead() - 1;
    }

    int32_t capacity() const {
        return mCapacity / mChannelCount;
    }

    void clear() {
        mReadIndex.store(0, std::memory_order_relaxed);
        mWriteIndex.store(0, std::memory_order_relaxed);
        std::fill(mBuffer.begin(), mBuffer.end(), 0.0f);
    }

private:
    const int32_t mChannelCount;
    const int32_t mCapacity;
    std::vector<float> mBuffer;
    std::atomic<int32_t> mReadIndex;
    std::atomic<int32_t> mWriteIndex;
};
