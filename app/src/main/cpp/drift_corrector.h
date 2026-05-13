#pragma once

#include <cstdint>
#include <cmath>
#include <vector>
#include <algorithm>
#include <atomic>

class DriftCorrector {
public:
    DriftCorrector()
        : mClockOffsetNs(0)
        , mDriftRatePpm(0.0)
        , mAnchorMediaTimeUs(0)
        , mAnchorDeviceTimeNs(0)
        , mLastSyncTimeNs(0)
        , mCurrentAgeNs(0)
        , mCorrectionEnabled(false)
        , mMiniBuffer(20)
        , mShortBuffer(100)
        , mLongBuffer(500)
        , mMiniIdx(0)
        , mShortIdx(0)
        , mLongIdx(0)
        , mMiniCount(0)
        , mShortCount(0)
        , mLongCount(0) {}

    void setClockOffset(int64_t offsetNs) {
        mClockOffsetNs.store(offsetNs, std::memory_order_release);
    }

    void setDriftRate(double ppm) {
        mDriftRatePpm.store(ppm, std::memory_order_release);
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
        double drift = mDriftRatePpm.load(std::memory_order_acquire);
        int64_t lastSync = mLastSyncTimeNs.load(std::memory_order_acquire);

        int64_t serverNowNs = localNowNs + offset +
            static_cast<int64_t>(drift * (localNowNs - lastSync) / 1e6);

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

        double miniMedian = getMiniMedian();
        double shortMedian = getShortMedian();
        double longMedian = getLongMedian();

        if (std::abs(longMedian) > 2e6 && std::abs(mCurrentAgeNs.load()) > 500e3) {
            return computeHardCorrection(numFrames, sampleRate, longMedian);
        }

        if (std::abs(shortMedian) > 100e3 && std::abs(miniMedian) > 50e3) {
            return computeSoftCorrection(numFrames, sampleRate, shortMedian);
        }

        return 0;
    }

    void resetBuffers() {
        mMiniCount = 0;
        mShortCount = 0;
        mLongCount = 0;
        mMiniIdx = 0;
        mShortIdx = 0;
        mLongIdx = 0;
    }

private:
    std::atomic<int64_t> mClockOffsetNs;
    std::atomic<double> mDriftRatePpm;
    std::atomic<int64_t> mAnchorMediaTimeUs;
    std::atomic<int64_t> mAnchorDeviceTimeNs;
    std::atomic<int64_t> mLastSyncTimeNs;
    std::atomic<int64_t> mCurrentAgeNs;
    std::atomic<bool> mCorrectionEnabled;

    std::vector<int64_t> mMiniBuffer;
    std::vector<int64_t> mShortBuffer;
    std::vector<int64_t> mLongBuffer;
    int32_t mMiniIdx, mShortIdx, mLongIdx;
    int32_t mMiniCount, mShortCount, mLongCount;

    void addAge(int64_t age) {
        mMiniBuffer[mMiniIdx] = age;
        mMiniIdx = (mMiniIdx + 1) % 20;
        if (mMiniCount < 20) mMiniCount++;

        mShortBuffer[mShortIdx] = age;
        mShortIdx = (mShortIdx + 1) % 100;
        if (mShortCount < 100) mShortCount++;

        mLongBuffer[mLongIdx] = age;
        mLongIdx = (mLongIdx + 1) % 500;
        if (mLongCount < 500) mLongCount++;
    }

    double getMiniMedian() const {
        return medianOf(mMiniBuffer, mMiniCount);
    }

    double getShortMedian() const {
        return medianOf(mShortBuffer, mShortCount);
    }

    double getLongMedian() const {
        return medianOf(mLongBuffer, mLongCount);
    }

    static double medianOf(const std::vector<int64_t>& buf, int32_t count) {
        if (count == 0) return 0.0;
        std::vector<int64_t> sorted(buf.begin(), buf.begin() + count);
        std::sort(sorted.begin(), sorted.end());
        int32_t trim = count / 4;
        if (trim == 0) return static_cast<double>(sorted[count / 2]);
        double sum = 0;
        for (int32_t i = trim; i < count - trim; i++) {
            sum += sorted[i];
        }
        return sum / (count - 2 * trim);
    }

    int32_t computeSoftCorrection(int32_t numFrames, int32_t sampleRate, double shortMedian) {
        double correctionRate = 0.0005;
        if (shortMedian < 0) correctionRate = -correctionRate;

        double ageCorrectionNs = shortMedian * correctionRate;
        int32_t frameCorrection = static_cast<int32_t>(
            ageCorrectionNs * sampleRate / 1e9
        );

        if (frameCorrection > numFrames / 10) frameCorrection = numFrames / 10;
        if (frameCorrection < -(numFrames / 10)) frameCorrection = -(numFrames / 10);

        return frameCorrection;
    }

    int32_t computeHardCorrection(int32_t numFrames, int32_t sampleRate, double longMedian) {
        int32_t correction = static_cast<int32_t>(
            longMedian * sampleRate / 1e9
        );

        if (std::abs(longMedian) > 500e6) {
            return correction;
        }

        int32_t maxCorrection = numFrames / 4;
        if (correction > maxCorrection) correction = maxCorrection;
        if (correction < -maxCorrection) correction = -maxCorrection;

        resetBuffers();
        return correction;
    }
};
