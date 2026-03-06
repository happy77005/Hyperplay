#include "engine/AudioEngine.h"
#include "engine/UsbNativeStreamer.h"
#include <android/log.h>
#include <jni.h>
#include <string>
#include <vector>

#define TAG "HyperPlayNative"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)

using namespace hyperplay;

static AudioEngine *gAudioEngine = nullptr;
static UsbNativeStreamer *gUsbStreamer =
    nullptr; // defined again below, forward ref
static JavaVM *gJavaVM = nullptr;
static jobject gEngineObject = nullptr;

jint JNI_OnLoad(JavaVM *vm, void *reserved) {
  gJavaVM = vm;
  return JNI_VERSION_1_6;
}

// Helper to call void method with args
void callJavaBitPerfect(bool isBitPerfect, int sampleRate) {
  if (!gJavaVM || !gEngineObject)
    return;

  JNIEnv *env;
  bool isAttached = false;
  if (gJavaVM->GetEnv((void **)&env, JNI_VERSION_1_6) == JNI_EDETACHED) {
    if (gJavaVM->AttachCurrentThread(&env, nullptr) != 0)
      return;
    isAttached = true;
  }

  jclass clazz = env->GetObjectClass(gEngineObject);
  jmethodID methodId = env->GetMethodID(clazz, "onNativeBitPerfect", "(ZI)V");
  if (methodId) {
    env->CallVoidMethod(gEngineObject, methodId, (jboolean)isBitPerfect,
                        (jint)sampleRate);
  }

  if (isAttached)
    gJavaVM->DetachCurrentThread();
}

void callJavaError(int what, int extra) {
  if (!gJavaVM || !gEngineObject)
    return;

  JNIEnv *env;
  bool isAttached = false;
  if (gJavaVM->GetEnv((void **)&env, JNI_VERSION_1_6) == JNI_EDETACHED) {
    if (gJavaVM->AttachCurrentThread(&env, nullptr) != 0)
      return;
    isAttached = true;
  }

  jclass clazz = env->GetObjectClass(gEngineObject);
  jmethodID methodId = env->GetMethodID(clazz, "onNativeError", "(II)V");
  if (methodId) {
    env->CallVoidMethod(gEngineObject, methodId, (jint)what, (jint)extra);
  }

  if (isAttached)
    gJavaVM->DetachCurrentThread();
}

void callJavaVoidMethod(const char *methodName) {
  if (!gJavaVM || !gEngineObject)
    return;

  JNIEnv *env;
  bool isAttached = false;
  jint res = gJavaVM->GetEnv((void **)&env, JNI_VERSION_1_6);

  if (res == JNI_EDETACHED) {
    if (gJavaVM->AttachCurrentThread(&env, nullptr) != 0) {
      return;
    }
    isAttached = true;
  }

  jclass clazz = env->GetObjectClass(gEngineObject);
  jmethodID methodId = env->GetMethodID(clazz, methodName, "()V");
  if (methodId) {
    env->CallVoidMethod(gEngineObject, methodId);
  }

  if (isAttached) {
    gJavaVM->DetachCurrentThread();
  }
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_first_engine_NativeHiResEngine_initEngine(JNIEnv *env,
                                                           jobject thiz) {
  if (gAudioEngine == nullptr) {
    gAudioEngine = new AudioEngine();
    gEngineObject = env->NewGlobalRef(thiz);

    gAudioEngine->setCompletionCallback(
        []() { callJavaVoidMethod("onNativeCompletion"); });
    gAudioEngine->setBitPerfectCallback(callJavaBitPerfect);
    gAudioEngine->setErrorCallback(callJavaError);
  }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_first_engine_NativeHiResEngine_nativeSetDataSource(
    JNIEnv *env, jobject thiz, jstring uri, jboolean bit_perfect,
    jint device_id) {
  if (gAudioEngine == nullptr)
    return JNI_FALSE;

  const char *uriStr = env->GetStringUTFChars(uri, nullptr);
  bool result =
      gAudioEngine->load(uriStr, (bool)bit_perfect, (int32_t)device_id);
  env->ReleaseStringUTFChars(uri, uriStr);

  if (result) {
    // Notify prepared immediately as load is synchronous for now
    jclass clazz = env->GetObjectClass(thiz);
    jmethodID methodId = env->GetMethodID(clazz, "onNativePrepared", "()V");
    if (methodId) {
      env->CallVoidMethod(thiz, methodId);
    }
  }

  return result ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_first_engine_NativeHiResEngine_nativePlay(JNIEnv *env,
                                                           jobject thiz) {
  if (gAudioEngine)
    gAudioEngine->play();
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_first_engine_NativeHiResEngine_nativePause(JNIEnv *env,
                                                            jobject thiz) {
  if (gAudioEngine)
    gAudioEngine->pause();
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_first_engine_NativeHiResEngine_nativeResume(JNIEnv *env,
                                                             jobject thiz) {
  if (gAudioEngine)
    gAudioEngine->resume();
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_first_engine_NativeHiResEngine_nativeStop(JNIEnv *env,
                                                           jobject thiz) {
  if (gAudioEngine)
    gAudioEngine->stop();
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_first_engine_NativeHiResEngine_nativeSeekTo(JNIEnv *env,
                                                             jobject thiz,
                                                             jint position_ms) {
  if (gAudioEngine) {
    gAudioEngine->seekTo(position_ms);
    // Reset USB streamer's frame counter so position tracks correctly after
    // seek
    if (gUsbStreamer && gUsbStreamer->isRunning()) {
      int rate = gUsbStreamer->getSampleRate();
      if (rate <= 0)
        rate = 48000;
      uint64_t seekFrames = (uint64_t)position_ms * rate / 1000;
      gUsbStreamer->resetFramesStreamed(seekFrames);
    }
  }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_first_engine_NativeHiResEngine_nativeIsPlaying(JNIEnv *env,
                                                                jobject thiz) {
  return gAudioEngine ? (gAudioEngine->isPlaying() ? JNI_TRUE : JNI_FALSE)
                      : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_first_engine_NativeHiResEngine_nativeGetCurrentPosition(
    JNIEnv *env, jobject thiz) {
  // In USB bypass mode, AudioEngine's counter isn't updated (Oboe is closed).
  // Use UsbNativeStreamer's own frame counter instead.
  if (gUsbStreamer && gUsbStreamer->isRunning()) {
    int rate = gUsbStreamer->getSampleRate();
    if (rate <= 0)
      rate = 48000;
    return (jint)(gUsbStreamer->getFramesStreamed() * 1000 / rate);
  }
  return gAudioEngine ? gAudioEngine->getCurrentPosition() : 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_first_engine_NativeHiResEngine_nativeGetDuration(
    JNIEnv *env, jobject thiz) {
  return gAudioEngine ? gAudioEngine->getDuration() : 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_first_engine_NativeHiResEngine_nativeRelease(JNIEnv *env,
                                                              jobject thiz) {
  if (gEngineObject) {
    env->DeleteGlobalRef(gEngineObject);
    gEngineObject = nullptr;
  }
  delete gAudioEngine;
  gAudioEngine = nullptr;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_first_engine_NativeHiResEngine_nativeSetPlaybackSpeed(
    JNIEnv *env, jobject thiz, jfloat speed) {
  if (gAudioEngine)
    gAudioEngine->setPlaybackSpeed(speed);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_first_engine_NativeHiResEngine_nativeGetSampleRate(
    JNIEnv *env, jobject thiz) {
  return gAudioEngine ? gAudioEngine->getSampleRate() : 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_first_engine_NativeHiResEngine_nativeGetChannelCount(
    JNIEnv *env, jobject thiz) {
  return gAudioEngine ? gAudioEngine->getChannelCount() : 0;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_first_engine_NativeHiResEngine_nativeGetAudioBackend(
    JNIEnv *env, jobject thiz) {
  if (!gAudioEngine)
    return env->NewStringUTF("None");
  return env->NewStringUTF(gAudioEngine->getAudioApiName());
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_example_first_engine_NativeHiResEngine_nativeProbeSampleRates(
    JNIEnv *env, jobject thiz, jint device_id) {
  if (!gAudioEngine)
    return nullptr;

  std::vector<int32_t> rates = gAudioEngine->getSupportedSampleRates(device_id);
  jintArray result = env->NewIntArray(rates.size());
  if (result == nullptr)
    return nullptr;

  env->SetIntArrayRegion(result, 0, rates.size(), rates.data());
  return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_first_engine_NativeHiResEngine_nativeSetOutputSampleRate(
    JNIEnv *env, jobject thiz, jint sample_rate) {
  if (gAudioEngine)
    gAudioEngine->setOutputSampleRate(sample_rate);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_first_engine_NativeHiResEngine_nativeSetExclusiveMode(
    JNIEnv *env, jobject thiz, jboolean exclusive) {
  if (gAudioEngine)
    gAudioEngine->setExclusiveMode((bool)exclusive);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_first_engine_NativeHiResEngine_nativeSetDeviceId(
    JNIEnv *env, jobject thiz, jint device_id) {
  if (gAudioEngine)
    gAudioEngine->setDeviceId(device_id);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_first_engine_NativeHiResEngine_nativeSetEqBand(
    JNIEnv *env, jobject thiz, jint band, jfloat gain_db) {
  if (gAudioEngine)
    gAudioEngine->getDspPipeline().getEq()->setBandGain((int)band, gain_db);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_first_engine_NativeHiResEngine_nativeSetDspParameter(
    JNIEnv *env, jobject thiz, jint param_id, jfloat value) {
  LOGD("nativeSetDspParameter called: param=%d, value=%.2f", param_id, value);
  if (!gAudioEngine) {
    LOGD("nativeSetDspParameter: Engine is null!");
    return;
  }

  auto &dsp = gAudioEngine->getDspPipeline();
  switch (param_id) {
  case 1: // DSP_PARAM_PREAMP
    dsp.getPreAmp()->setGainDb(value);
    break;
  case 2: // DSP_PARAM_POSTAMP
    dsp.getPostAmp()->setGainDb(value);
    break;
  case 3: // DSP_PARAM_STEREO_WIDTH
    dsp.getStereo()->setWidth(value);
    break;
  case 4: // DSP_PARAM_CROSSFEED
    dsp.getStereo()->setCrossfeed(value);
    break;
  case 5: // DSP_PARAM_RESONANCE_ENABLE
    dsp.getResonance()->setEnabled(value > 0.5f);
    break;
  }
}

// ─── USB Streaming JNI exports
// ──────────────────────────────────────────────── Class:
// com.example.first.usb.NativeHiResEngineUsbBridge

// gUsbStreamer declared at top of file (needed by nativeGetCurrentPosition)
// Global refs — released in stopUsbStreamer
static jobject gUsbKotlinObj = nullptr;

extern "C" JNIEXPORT void JNICALL
Java_com_example_first_usb_NativeHiResEngineUsbBridge_startUsbStreamer(
    JNIEnv *env, jclass /* clazz */, jint fd, jint endpointAddr,
    jint maxPacketSize, jboolean isIso, jint chunkMs, jint sampleRate,
    jint channels, jint bitDepth, jint bitFormatOrd, jboolean bitPerfect,
    jobject streamer) {
  if (gUsbStreamer) {
    gUsbStreamer->stop();
    delete gUsbStreamer;
  }
  gUsbStreamer = new hyperplay::UsbNativeStreamer();

  // Make global refs (survive JNI frame)
  if (gUsbKotlinObj)
    env->DeleteGlobalRef(gUsbKotlinObj);

  gUsbKotlinObj = env->NewGlobalRef(streamer);

  gUsbStreamer->start(
      gJavaVM, fd, endpointAddr, maxPacketSize, (bool)isIso, (int)chunkMs,
      (int)sampleRate, channels, bitDepth, bitFormatOrd, (bool)bitPerfect,
      gAudioEngine ? gAudioEngine->getRingBuffer() : nullptr, gUsbKotlinObj);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_first_usb_NativeHiResEngineUsbBridge_stopUsbStreamer(
    JNIEnv *env, jclass /* clazz */) {
  if (gUsbStreamer) {
    gUsbStreamer->stop();
    delete gUsbStreamer;
    gUsbStreamer = nullptr;
  }
  LOGD("[USB] stopUsbStreamer: streamer stopped, global refs released");
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_first_usb_NativeHiResEngineUsbBridge_setUsbPaused(
    JNIEnv *env, jclass /* clazz */, jboolean paused) {
  if (gUsbStreamer)
    gUsbStreamer->setPaused(paused);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_first_usb_NativeHiResEngineUsbBridge_setUsbVolume(
    JNIEnv *env, jclass /* clazz */, jfloat volume) {
  if (gUsbStreamer) {
    LOGD("[USB] JNI setUsbVolume: %.6f", (float)volume);
    gUsbStreamer->setVolume(volume);
  }
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_first_usb_NativeHiResEngineUsbBridge_setHwVolumeActive(
    JNIEnv *env, jclass /* clazz */, jboolean active) {
  if (gUsbStreamer)
    gUsbStreamer->setHwVolumeActive(active);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_first_usb_NativeHiResEngineUsbBridge_getRingBufferFillFrames(
    JNIEnv *env, jclass /* clazz */) {
  if (!gAudioEngine)
    return 0;
  return gAudioEngine->getRingBufferFillFrames();
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_first_usb_NativeHiResEngineUsbBridge_setUsbOutputFormat(
    JNIEnv *env, jclass /* clazz */, jint sampleRate, jint bitDepth,
    jint channels, jint bitFormatOrd) {
  if (gAudioEngine) {
    gAudioEngine->setOutputFormat(sampleRate, bitDepth, channels, bitFormatOrd);
  }
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_first_usb_NativeHiResEngineUsbBridge_setUsbOutputMode(
    JNIEnv *env, jclass /* clazz */, jboolean enabled) {
  if (gAudioEngine) {
    gAudioEngine->setUsbOutputMode(enabled);
  }
}
