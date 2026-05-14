#include "audio_engine.h"
#include "ring_buffer.h"
#include "drift_corrector.h"

#include <oboe/Oboe.h>
#include <android/log.h>
#include <sched.h>
#include <unistd.h>
#include <cstring>
#include <cmath>
#include <chrono>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "AudioEngine", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "AudioEngine", __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, "AudioEngine", __VA_ARGS__)

static constexpr int32_t kSampleRate = 48000;
static constexpr int32_t kChannelCount = 2;
static constexpr int32_t kRingBufferFrames = 16384;
static constexpr float kSineAmplitude = 0.3f;
static constexpr int32_t kMaxRestartAttempts = 3;
static constexpr int32_t kRestartDelayMs = 20;

AudioEngine::AudioEngine()
    : mRingBuffer(std::make_unique<RingBuffer>(kRingBufferFrames, kChannelCount))
    , mDriftCorrector(std::make_unique<DriftCorrector>()) {}

AudioEngine::~AudioEngine() { stop(); }

bool AudioEngine::start() {
    if (mStream && mIsPlaying.load(std::memory_order_acquire)) return true;

    oboe::AudioStreamBuilder builder;
    builder.setSharingMode(oboe::SharingMode::Exclusive)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setFormat(oboe::AudioFormat::Float)
            ->setSampleRate(kSampleRate)
            ->setChannelCount(kChannelCount)
            ->setDataCallback(this)
            ->setErrorCallback(this)
            ->setDirection(oboe::Direction::Output);

    oboe::Result result = builder.openStream(mStream);
    if (result != oboe::Result::OK) {
        LOGE("Failed to open stream: %s", oboe::convertToText(result));
        return false;
    }

    LOGI("Stream opened: rate=%d, channels=%d, format=%s, api=%s, burst=%d",
         mStream->getSampleRate(),
         mStream->getChannelCount(),
         oboe::convertToText(mStream->getFormat()),
         oboe::convertToText(mStream->getAudioApi()),
         mStream->getFramesPerBurst());

    mSampleRate.store(mStream->getSampleRate(), std::memory_order_release);
    mFramesPerBurst.store(mStream->getFramesPerBurst(), std::memory_order_release);

    result = mStream->requestStart();
    if (result != oboe::Result::OK) {
        LOGE("Failed to start stream: %s", oboe::convertToText(result));
        mStream->close();
        mStream.reset();
        return false;
    }

    mIsPlaying.store(true, std::memory_order_release);
    mThreadAffinitySet = false;
    LOGI("Audio engine started");
    return true;
}

void AudioEngine::stop() {
    if (mStream) {
        mStream->requestStop();
        mStream->close();
        mStream.reset();
    }
    mIsPlaying.store(false, std::memory_order_release);
    mSinePhase = 0.0f;
    mRingBuffer->clear();
    mDriftCorrector->disable();
    LOGI("Audio engine stopped");
}

int32_t AudioEngine::writePcmData(const float *data, int32_t numSamples) {
    int32_t aligned = (numSamples / kChannelCount) * kChannelCount;
    if (aligned <= 0) return 0;
    return mRingBuffer->write(data, aligned / kChannelCount);
}

double AudioEngine::getLatencyMs() {
    if (!mStream) return -1.0;
    auto result = mStream->calculateLatencyMillis();
    if (result) return result.value();
    LOGW("Latency calculation failed: %s", oboe::convertToText(result.error()));
    return -1.0;
}

bool AudioEngine::isPlaying() const { return mIsPlaying.load(std::memory_order_acquire); }

void AudioEngine::enableSine(bool enable) {
    mSineEnabled.store(enable, std::memory_order_release);
}

bool AudioEngine::isSineEnabled() const {
    return mSineEnabled.load(std::memory_order_acquire);
}

int32_t AudioEngine::availableWrite() const { return mRingBuffer->availableWrite(); }
int32_t AudioEngine::availableRead() const { return mRingBuffer->availableRead(); }
void AudioEngine::clearBuffer() { mRingBuffer->clear(); }

void AudioEngine::setClockOffset(int64_t offsetNs) {
    mDriftCorrector->setClockOffset(offsetNs);
}

void AudioEngine::setDriftRate(double ppm) {
    mDriftCorrector->setDriftRate(ppm);
}

void AudioEngine::setAnchor(int64_t mediaTimeUs, int64_t deviceTimeNs) {
    mDriftCorrector->setAnchor(mediaTimeUs, deviceTimeNs);
}

void AudioEngine::disableDriftCorrection() {
    mDriftCorrector->disable();
}

int64_t AudioEngine::getAgeNs() const {
    return mDriftCorrector->getAgeNs();
}

oboe::DataCallbackResult AudioEngine::onAudioReady(
        oboe::AudioStream *oboeStream,
        void *audioData,
        int32_t numFrames) {

    if (!mThreadAffinitySet) {
        setThreadAffinity();
        mThreadAffinitySet = true;
    }

    auto now = std::chrono::steady_clock::now();
    int64_t localNowNs = std::chrono::duration_cast<std::chrono::nanoseconds>(
        now.time_since_epoch()).count();
    mDriftCorrector->computeAge(localNowNs);

    auto *output = static_cast<float *>(audioData);
    int32_t samplesPerFrame = oboeStream->getChannelCount();
    int32_t rate = mSampleRate.load(std::memory_order_acquire);

    int32_t framesCorrection = mDriftCorrector->computeFramesCorrection(numFrames, rate);

    int32_t clampedCorrection = framesCorrection;
    if (clampedCorrection > numFrames / 4) clampedCorrection = numFrames / 4;
    if (clampedCorrection < -(numFrames / 4)) clampedCorrection = -(numFrames / 4);

    int32_t framesRead;
    if (clampedCorrection > 0) {
        framesRead = applySoftCorrection(output, numFrames, clampedCorrection, samplesPerFrame);
    } else if (clampedCorrection < 0) {
        int32_t readFrames = numFrames + clampedCorrection;
        if (readFrames <= 0) readFrames = 1;
        framesRead = mRingBuffer->read(output, readFrames);
        if (framesRead < numFrames) {
            int32_t startSample = framesRead * samplesPerFrame;
            int32_t totalSamples = numFrames * samplesPerFrame;
            if (startSample < totalSamples) {
                std::memset(&output[startSample], 0,
                            (totalSamples - startSample) * sizeof(float));
            }
        }
    } else {
        framesRead = mRingBuffer->read(output, numFrames);
        if (framesRead < numFrames) {
            int32_t startSample = framesRead * samplesPerFrame;
            int32_t totalSamples = numFrames * samplesPerFrame;
            if (startSample < totalSamples) {
                std::memset(&output[startSample], 0,
                            (totalSamples - startSample) * sizeof(float));
            }
        }
    }

    if (mSineEnabled.load(std::memory_order_acquire)) {
        float phaseIncrement = 440.0f * 2.0f * static_cast<float>(M_PI) / static_cast<float>(rate);
        for (int32_t i = 0; i < numFrames; i++) {
            float sample = kSineAmplitude * sinf(mSinePhase);
            for (int32_t ch = 0; ch < samplesPerFrame; ch++) {
                output[i * samplesPerFrame + ch] += sample;
            }
            mSinePhase += phaseIncrement;
            if (mSinePhase >= 2.0f * static_cast<float>(M_PI)) mSinePhase -= 2.0f * static_cast<float>(M_PI);
        }
    }

    return oboe::DataCallbackResult::Continue;
}

int32_t AudioEngine::applySoftCorrection(float *output, int32_t numFrames,
                                          int32_t correction, int32_t samplesPerFrame) {
    int32_t totalReadFrames = numFrames + correction;
    if (totalReadFrames > numFrames * 2) totalReadFrames = numFrames * 2;

    auto *tempBuf = static_cast<float *>(alloca(totalReadFrames * samplesPerFrame * sizeof(float)));
    int32_t framesRead = mRingBuffer->read(tempBuf, totalReadFrames);

    if (framesRead <= numFrames) {
        std::memcpy(output, tempBuf, framesRead * samplesPerFrame * sizeof(float));
        if (framesRead < numFrames) {
            std::memset(output + framesRead * samplesPerFrame, 0,
                        (numFrames - framesRead) * samplesPerFrame * sizeof(float));
        }
        return framesRead;
    }

    float ratio = static_cast<float>(numFrames) / static_cast<float>(framesRead);
    for (int32_t outFrame = 0; outFrame < numFrames; outFrame++) {
        float srcFrame = outFrame / ratio;
        int32_t srcIdx = static_cast<int32_t>(srcFrame);
        float frac = srcFrame - static_cast<float>(srcIdx);
        if (srcIdx >= framesRead - 1) {
            srcIdx = framesRead - 1;
            frac = 0.0f;
        }
        for (int32_t ch = 0; ch < samplesPerFrame; ch++) {
            float s0 = tempBuf[srcIdx * samplesPerFrame + ch];
            float s1 = tempBuf[(srcIdx + 1) * samplesPerFrame + ch];
            output[outFrame * samplesPerFrame + ch] = s0 * (1.0f - frac) + s1 * frac;
        }
    }
    return numFrames;
}

void AudioEngine::onErrorAfterClose(oboe::AudioStream *oboeStream, oboe::Result error) {
    LOGE("Error after close: %s", oboe::convertToText(error));
    if (error == oboe::Result::ErrorDisconnected) {
        LOGI("Attempting restart after disconnect");
        mIsPlaying.store(false, std::memory_order_release);
        int32_t attempt = 0;
        while (attempt < kMaxRestartAttempts) {
            usleep(kRestartDelayMs * 1000);
            if (start()) {
                LOGI("Restart succeeded on attempt %d", attempt + 1);
                return;
            }
            attempt++;
        }
        LOGE("Restart failed after %d attempts", kMaxRestartAttempts);
    }
}

void AudioEngine::setThreadAffinity() {
    pid_t tid = gettid();
    cpu_set_t cpuSet;
    CPU_ZERO(&cpuSet);
    CPU_SET(sched_getcpu(), &cpuSet);
    if (sched_setaffinity(tid, sizeof(cpu_set_t), &cpuSet) != 0) {
        LOGW("Failed to set thread affinity");
    }
}
