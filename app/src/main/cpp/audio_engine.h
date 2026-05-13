#pragma once

#include <oboe/Oboe.h>
#include <atomic>
#include <cstdint>
#include <memory>

class RingBuffer;
class DriftCorrector;

class AudioEngine : public oboe::AudioStreamDataCallback,
                    public oboe::AudioStreamErrorCallback {
public:
    AudioEngine();
    ~AudioEngine() override;

    bool start();
    void stop();

    int32_t writePcmData(const float *data, int32_t numSamples);
    double getLatencyMs();
    bool isPlaying() const;

    void enableSine(bool enable);
    bool isSineEnabled() const;
    int32_t availableWrite() const;
    int32_t availableRead() const;
    void clearBuffer();

    void setClockOffset(int64_t offsetNs);
    void setDriftRate(double ppm);
    void setAnchor(int64_t mediaTimeUs, int64_t deviceTimeNs);
    void disableDriftCorrection();
    int64_t getAgeNs() const;

    oboe::DataCallbackResult onAudioReady(
            oboe::AudioStream *oboeStream,
            void *audioData,
            int32_t numFrames) override;

    void onErrorAfterClose(oboe::AudioStream *oboeStream, oboe::Result error) override;

private:
    void setThreadAffinity();
    void applyDriftCorrection(float *audioData, int32_t numFrames, int32_t samplesPerFrame);

    std::unique_ptr<RingBuffer> mRingBuffer;
    std::unique_ptr<DriftCorrector> mDriftCorrector;
    std::shared_ptr<oboe::AudioStream> mStream;
    std::atomic<bool> mIsPlaying{false};
    std::atomic<bool> mSineEnabled{false};
    double mSinePhase = 0.0;
    int32_t mSampleRate = 48000;
    int32_t mFramesPerBurst = 0;
    bool mThreadAffinitySet = false;
};
