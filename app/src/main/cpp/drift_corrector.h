#pragma once

#include <cstdint>
#include <cstring>
#include <algorithm>
#include <atomic>

class DriftCorrector {
public:
    DriftCorrector()
        : mClockOffsetNs(0)
        , mDriftRatePpmX1M(0)
        , mAnchorMediaTimeUs(0)
        , mAnchorDeviceTimeNs(0)
        , mLastSyncTimeNs(0)
        , mCurrentAgeNs(0)
        , mCorrectionEnabled(false)
        , mMiniCount(0)
        , mShortCount(0)
        , mLongCount(0) {
        std::memset(mMiniBuffer, 0, sizeof(mMiniBuffer));
        std::memset(mShortBuffer, 0, sizeof(mShortBuffer));
        std::memset(mLongBuffer, 0, sizeof(mLongBuffer));
    }

    void setClockOffset(int64_t offsetNs) {
        mClockOffsetNs.store(offsetNs, std::memory_order_release);
    }

    void setDriftRate(double ppm) {
        mDriftRatePpmX1M.store(static_cast<int64_t>(ppm * 1e6), std::memory_order_release);
    }

    void setAnchor(int64_t mediaTimeUs, int64_t deviceTimeNs) {
        mAnchorMediaTimeUs.store(mediaTimeUs, std::memory_order_release);
        mAnchorDeviceTimeNs.store(deviceTimeNs, std::memory_order_release);
        mLastSyncTimeNs.store(deviceTimeNs, std::memory_order_release);
        mCorrectionEnabled.store(true, std::memory_order_release);
    }

    void disable() {
        mCorrectionEnabled.store(false, std::memory_order_release);
    }

    void computeAge(int64_t localNowNs) {
        if (!mCorrectionEnabled.load(std::memory_order_acquire)) {
            mCurrentAgeNs.store(0, std::memory_order_release);
            return;
        }

        int64_t offset = mClockOffsetNs.load(std::memory_order_acquire);
        int64_t driftX1M = mDriftRatePpmX1M.load(std::memory_order_acquire);
        int64_t lastSync = mLastSyncTimeNs.load(std::memory_order_acquire);

        int64_t driftNs = static_cast<int64_t>(
            static_cast<double>(driftX1M) * static_cast<double>(localNowNs - lastSync) / 1e6);
        int64_t serverNowNs = localNowNs + offset + driftNs;

        int64_t anchorDevNs = mAnchorDeviceTimeNs.load(std::memory_order_acquire);
        int64_t anchorMediaUs = mAnchorMediaTimeUs.load(std::memory_order_acquire);

        int64_t scheduledTimeNs = anchorMediaUs * 1000LL + anchorDevNs;
        int64_t age = serverNowNs - scheduledTimeNs;
        mCurrentAgeNs.store(age, std::memory_order_release);

        addAge(age);
    }

    int64_t getAgeNs() const {
        return mCurrentAgeNs.load(std::memory_order_acquire);
    }

    int32_t computeFramesCorrection(int32_t numFrames, int32_t sampleRate) {
        if (!mCorrectionEnabled.load(std::memory_order_acquire)) return 0;

        double miniMed = medianOf(mMiniBuffer, mMiniCount, kMiniSize);
        double shortMed = medianOf(mShortBuffer, mShortCount, kShortSize);
        double longMed = medianOf(mLongBuffer, mLongCount, kLongSize);

        int32_t maxCorr = numFrames / 4;
        if (maxCorr < 1) maxCorr = 1;

        if (std::abs(longMed) > 2e6 && std::abs(mCurrentAgeNs.load()) > 500e3) {
            int32_t corr = static_cast<int32_t>(longMed * sampleRate / 1e9);
            if (corr > maxCorr) corr = maxCorr;
            if (corr < -maxCorr) corr = -maxCorr;
            resetBuffers();
            return corr;
        }

        if (std::abs(shortMed) > 100e3 && std::abs(miniMed) > 50e3) {
            double rate = 0.0005;
            if (shortMed < 0) rate = -rate;
            double ageCorrNs = shortMed * rate;
            int32_t corr = static_cast<int32_t>(ageCorrNs * sampleRate / 1e9);
            if (corr > maxCorr) corr = maxCorr;
            if (corr < -maxCorr) corr = -maxCorr;
            if (corr == 0 && std::abs(shortMed) > 100e3) {
                corr = (shortMed > 0) ? 1 : -1;
            }
            return corr;
        }

        return 0;
    }

private:
    static constexpr int32_t kMiniSize = 20;
    static constexpr int32_t kShortSize = 100;
    static constexpr int32_t kLongSize = 500;

    std::atomic<int64_t> mClockOffsetNs;
    std::atomic<int64_t> mDriftRatePpmX1M;
    std::atomic<int64_t> mAnchorMediaTimeUs;
    std::atomic<int64_t> mAnchorDeviceTimeNs;
    std::atomic<int64_t> mLastSyncTimeNs;
    std::atomic<int64_t> mCurrentAgeNs;
    std::atomic<bool> mCorrectionEnabled;

    int64_t mMiniBuffer[kMiniSize];
    int64_t mShortBuffer[kShortSize];
    int64_t mLongBuffer[kLongSize];
    int32_t mMiniCount;
    int32_t mShortCount;
    int32_t mLongCount;
    int32_t mMiniIdx = 0;
    int32_t mShortIdx = 0;
    int32_t mLongIdx = 0;

    void addAge(int64_t age) {
        mMiniBuffer[mMiniIdx] = age;
        mMiniIdx = (mMiniIdx + 1) % kMiniSize;
        if (mMiniCount < kMiniSize) mMiniCount++;

        mShortBuffer[mShortIdx] = age;
        mShortIdx = (mShortIdx + 1) % kShortSize;
        if (mShortCount < kShortSize) mShortCount++;

        mLongBuffer[mLongIdx] = age;
        mLongIdx = (mLongIdx + 1) % kLongSize;
        if (mLongCount < kLongSize) mLongCount++;
    }

    static double medianOf(const int64_t *buf, int32_t count, int32_t) {
        if (count == 0) return 0.0;
        int64_t sorted[512];
        if (count > 512) count = 512;
        std::memcpy(sorted, buf, count * sizeof(int64_t));
        insertionSort(sorted, count);
        int32_t trim = count / 4;
        if (trim == 0) return static_cast<double>(sorted[count / 2]);
        double sum = 0;
        for (int32_t i = trim; i < count - trim; i++) {
            sum += sorted[i];
        }
        return sum / (count - 2 * trim);
    }

    static void insertionSort(int64_t *arr, int32_t n) {
        for (int32_t i = 1; i < n; i++) {
            int64_t key = arr[i];
            int32_t j = i - 1;
            while (j >= 0 && arr[j] > key) {
                arr[j + 1] = arr[j];
                j--;
            }
            arr[j + 1] = key;
        }
    }

    void resetBuffers() {
        mMiniCount = 0;
        mShortCount = 0;
        mLongCount = 0;
        mMiniIdx = 0;
        mShortIdx = 0;
        mLongIdx = 0;
    }
};
