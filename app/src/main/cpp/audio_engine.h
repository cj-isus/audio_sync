#pragma once

#include <oboe/Oboe.h>
#include <atomic>
#include <cstdint>
#include <memory>

class RingBuffer;

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

    oboe::DataCallbackResult onAudioReady(
            oboe::AudioStream *oboeStream,
            void *audioData,
            int32_t numFrames) override;

    void onErrorAfterClose(oboe::AudioStream *oboeStream, oboe::Result error) override;

private:
    void setThreadAffinity();

    std::unique_ptr<RingBuffer> mRingBuffer;
    std::shared_ptr<oboe::AudioStream> mStream;
    std::atomic<bool> mIsPlaying{false};
    std::atomic<bool> mSineEnabled{false};
    double mSinePhase = 0.0;
    int32_t mSampleRate = 48000;
    int32_t mFramesPerBurst = 0;
    bool mThreadAffinitySet = false;
};
