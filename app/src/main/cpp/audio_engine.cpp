#include "audio_engine.h"
#include "ring_buffer.h"

#include <oboe/Oboe.h>
#include <android/log.h>
#include <sched.h>
#include <unistd.h>
#include <cstring>
#include <cmath>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "AudioEngine", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "AudioEngine", __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, "AudioEngine", __VA_ARGS__)

static constexpr int32_t kSampleRate = 48000;
static constexpr int32_t kChannelCount = 2;
static constexpr int32_t kRingBufferFrames = 9600; // 200ms at 48kHz
static constexpr float kSineAmplitude = 0.3f;
static constexpr int32_t kMaxRestartAttempts = 3;
static constexpr int32_t kRestartDelayMs = 20;

AudioEngine::AudioEngine()
    : mRingBuffer(std::make_unique<RingBuffer>(kRingBufferFrames, kChannelCount)) {}

AudioEngine::~AudioEngine() { stop(); }

bool AudioEngine::start() {
    if (mStream && mIsPlaying) return true;

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

    mSampleRate = mStream->getSampleRate();
    mFramesPerBurst = mStream->getFramesPerBurst();

    result = mStream->requestStart();
    if (result != oboe::Result::OK) {
        LOGE("Failed to start stream: %s", oboe::convertToText(result));
        mStream->close();
        mStream.reset();
        return false;
    }

    mIsPlaying = true;
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
    mIsPlaying = false;
    mSinePhase = 0.0;
    mRingBuffer->clear();
    LOGI("Audio engine stopped");
}

int32_t AudioEngine::writePcmData(const float *data, int32_t numSamples) {
    return mRingBuffer->write(data, numSamples / kChannelCount);
}

double AudioEngine::getLatencyMs() {
    if (!mStream) return -1.0;
    auto result = mStream->calculateLatencyMillis();
    if (result) return result.value();
    LOGW("Latency calculation failed: %s", oboe::convertToText(result.error()));
    return -1.0;
}

bool AudioEngine::isPlaying() const { return mIsPlaying; }

void AudioEngine::enableSine(bool enable) {
    mSineEnabled.store(enable, std::memory_order_release);
    if (!enable) mSinePhase = 0.0;
}

bool AudioEngine::isSineEnabled() const {
    return mSineEnabled.load(std::memory_order_acquire);
}

int32_t AudioEngine::availableWrite() const { return mRingBuffer->availableWrite(); }

oboe::DataCallbackResult AudioEngine::onAudioReady(
        oboe::AudioStream *oboeStream,
        void *audioData,
        int32_t numFrames) {

    if (!mThreadAffinitySet) {
        setThreadAffinity();
        mThreadAffinitySet = true;
    }

    auto *output = static_cast<float *>(audioData);
    int32_t samplesPerFrame = oboeStream->getChannelCount();

    int32_t framesRead = mRingBuffer->read(output, numFrames);

    if (framesRead < numFrames) {
        int32_t startSample = framesRead * samplesPerFrame;
        int32_t totalSamples = numFrames * samplesPerFrame;
        std::memset(&output[startSample], 0,
                    (totalSamples - startSample) * sizeof(float));
    }

    if (mSineEnabled.load(std::memory_order_acquire)) {
        double phaseIncrement = 440.0 * 2.0 * M_PI / mSampleRate;
        for (int32_t i = 0; i < numFrames; i++) {
            float sample = kSineAmplitude * static_cast<float>(std::sin(mSinePhase));
            for (int32_t ch = 0; ch < samplesPerFrame; ch++) {
                output[i * samplesPerFrame + ch] += sample;
            }
            mSinePhase += phaseIncrement;
            if (mSinePhase >= 2.0 * M_PI) mSinePhase -= 2.0 * M_PI;
        }
    }

    return oboe::DataCallbackResult::Continue;
}

void AudioEngine::onErrorAfterClose(oboe::AudioStream *oboeStream, oboe::Result error) {
    LOGE("Error after close: %s", oboe::convertToText(error));
    if (error == oboe::Result::ErrorDisconnected) {
        LOGI("Attempting restart after disconnect");
        mIsPlaying = false;
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
