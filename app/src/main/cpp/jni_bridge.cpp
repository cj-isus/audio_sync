#include "audio_engine.h"

#include <jni.h>

extern "C" {

JNIEXPORT jlong JNICALL
Java_ru_audiosynchronizer_audio_AudioEngine_nativeCreate(JNIEnv *, jobject) {
    auto *engine = new AudioEngine();
    return reinterpret_cast<jlong>(engine);
}

JNIEXPORT void JNICALL
Java_ru_audiosynchronizer_audio_AudioEngine_nativeDestroy(JNIEnv *, jobject, jlong enginePtr) {
    auto *engine = reinterpret_cast<AudioEngine *>(enginePtr);
    if (engine) {
        engine->stop();
        delete engine;
    }
}

JNIEXPORT jboolean JNICALL
Java_ru_audiosynchronizer_audio_AudioEngine_nativeStart(JNIEnv *, jobject, jlong enginePtr) {
    auto *engine = reinterpret_cast<AudioEngine *>(enginePtr);
    if (!engine) return JNI_FALSE;
    return engine->start() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_ru_audiosynchronizer_audio_AudioEngine_nativeStop(JNIEnv *, jobject, jlong enginePtr) {
    auto *engine = reinterpret_cast<AudioEngine *>(enginePtr);
    if (engine) engine->stop();
}

JNIEXPORT jdouble JNICALL
Java_ru_audiosynchronizer_audio_AudioEngine_nativeGetLatencyMs(JNIEnv *, jobject, jlong enginePtr) {
    auto *engine = reinterpret_cast<AudioEngine *>(enginePtr);
    if (!engine) return -1.0;
    return engine->getLatencyMs();
}

JNIEXPORT jint JNICALL
Java_ru_audiosynchronizer_audio_AudioEngine_nativeWriteBuffer(
        JNIEnv *env, jobject, jlong enginePtr, jfloatArray data, jint offset, jint size) {
    auto *engine = reinterpret_cast<AudioEngine *>(enginePtr);
    if (!engine) return 0;

    jfloat *elements = env->GetFloatArrayElements(data, nullptr);
    if (!elements) return 0;

    int32_t written = engine->writePcmData(elements + offset, size);

    env->ReleaseFloatArrayElements(data, elements, JNI_ABORT);
    return written;
}

JNIEXPORT void JNICALL
Java_ru_audiosynchronizer_audio_AudioEngine_nativeEnableSine(
        JNIEnv *, jobject, jlong enginePtr, jboolean enable) {
    auto *engine = reinterpret_cast<AudioEngine *>(enginePtr);
    if (engine) engine->enableSine(enable == JNI_TRUE);
}

JNIEXPORT jboolean JNICALL
Java_ru_audiosynchronizer_audio_AudioEngine_nativeIsSineEnabled(JNIEnv *, jobject, jlong enginePtr) {
    auto *engine = reinterpret_cast<AudioEngine *>(enginePtr);
    if (!engine) return JNI_FALSE;
    return engine->isSineEnabled() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_ru_audiosynchronizer_audio_AudioEngine_nativeAvailableWrite(JNIEnv *, jobject, jlong enginePtr) {
    auto *engine = reinterpret_cast<AudioEngine *>(enginePtr);
    if (!engine) return 0;
    return engine->availableWrite();
}

JNIEXPORT jint JNICALL
Java_ru_audiosynchronizer_audio_AudioEngine_nativeAvailableRead(JNIEnv *, jobject, jlong enginePtr) {
    auto *engine = reinterpret_cast<AudioEngine *>(enginePtr);
    if (!engine) return 0;
    return engine->availableRead();
}

JNIEXPORT void JNICALL
Java_ru_audiosynchronizer_audio_AudioEngine_nativeClearBuffer(JNIEnv *, jobject, jlong enginePtr) {
    auto *engine = reinterpret_cast<AudioEngine *>(enginePtr);
    if (engine) engine->clearBuffer();
}

} // extern "C"
