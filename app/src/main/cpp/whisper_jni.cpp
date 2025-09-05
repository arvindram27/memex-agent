#include <jni.h>
#include <string>
#include <android/log.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <vector>
#include <sstream>
#include <cstdio>
#include <cstdlib>
#include "whisper.h"

#define LOG_TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

// Helper function to convert jstring to std::string
std::string jstring2string(JNIEnv *env, jstring jStr) {
    if (!jStr) return "";
    
    const jclass stringClass = env->GetObjectClass(jStr);
    const jmethodID getBytes = env->GetMethodID(stringClass, "getBytes", "(Ljava/lang/String;)[B");
    const jbyteArray stringJbytes = (jbyteArray) env->CallObjectMethod(jStr, getBytes, env->NewStringUTF("UTF-8"));
    
    size_t length = (size_t) env->GetArrayLength(stringJbytes);
    jbyte* pBytes = env->GetByteArrayElements(stringJbytes, NULL);
    
    std::string ret = std::string((char *)pBytes, length);
    env->ReleaseByteArrayElements(stringJbytes, pBytes, JNI_ABORT);
    
    env->DeleteLocalRef(stringJbytes);
    env->DeleteLocalRef(stringClass);
    return ret;
}

// Read audio file (WAV format expected)
std::vector<float> read_wav(const std::string& fname) {
    std::vector<float> pcmf32;
    
    FILE* file = fopen(fname.c_str(), "rb");
    if (!file) {
        LOGE("Failed to open audio file: %s", fname.c_str());
        return pcmf32;
    }
    
    // Skip WAV header (44 bytes for standard WAV)
    fseek(file, 44, SEEK_SET);
    
    // Read PCM data
    int16_t sample;
    while (fread(&sample, sizeof(int16_t), 1, file) == 1) {
        pcmf32.push_back(sample / 32768.0f);
    }
    
    fclose(file);
    LOGI("Read %zu samples from %s", pcmf32.size(), fname.c_str());
    return pcmf32;
}

JNIEXPORT jlong JNICALL
Java_com_memexos_app_whisper_WhisperService_initContext(
        JNIEnv *env,
        jobject /* this */,
        jstring modelPath) {
    
    std::string model_path = jstring2string(env, modelPath);
    LOGI("Initializing Whisper context with model: %s", model_path.c_str());
    
    struct whisper_context_params cparams = whisper_context_default_params();
    struct whisper_context * ctx = whisper_init_from_file_with_params(model_path.c_str(), cparams);
    
    if (ctx == nullptr) {
        LOGE("Failed to load model from: %s", model_path.c_str());
        return 0L;
    }
    
    LOGI("Whisper context initialized successfully");
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jlong JNICALL
Java_com_memexos_app_whisper_WhisperService_initContextFromAsset(
        JNIEnv *env,
        jobject /* this */,
        jobject assetManager,
        jstring assetPath) {
    
    std::string asset_path = jstring2string(env, assetPath);
    LOGI("Initializing Whisper context from asset: %s", asset_path.c_str());
    
    // Get the native asset manager
    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);
    if (mgr == nullptr) {
        LOGE("Failed to get native asset manager");
        return 0L;
    }
    
    // Open the asset file
    AAsset* asset = AAssetManager_open(mgr, asset_path.c_str(), AASSET_MODE_BUFFER);
    if (asset == nullptr) {
        LOGE("Failed to open asset: %s", asset_path.c_str());
        return 0L;
    }
    
    // Get asset size and data
    off_t asset_size = AAsset_getLength(asset);
    const void* asset_data = AAsset_getBuffer(asset);
    
    if (asset_data == nullptr || asset_size <= 0) {
        LOGE("Failed to read asset data: %s", asset_path.c_str());
        AAsset_close(asset);
        return 0L;
    }
    
    LOGI("Asset loaded successfully: %s (size: %ld bytes)", asset_path.c_str(), asset_size);
    
    // Initialize whisper context from buffer (cast away const)
    struct whisper_context_params cparams = whisper_context_default_params();
    struct whisper_context * ctx = whisper_init_from_buffer_with_params(
        const_cast<void*>(asset_data), asset_size, cparams);
    
    // Close the asset
    AAsset_close(asset);
    
    if (ctx == nullptr) {
        LOGE("Failed to initialize Whisper context from asset: %s", asset_path.c_str());
        return 0L;
    }
    
    LOGI("Whisper context initialized successfully from asset");
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT void JNICALL
Java_com_memexos_app_whisper_WhisperService_freeContext(
        JNIEnv *env,
        jobject /* this */,
        jlong contextPtr) {
    
    if (contextPtr != 0) {
        struct whisper_context * ctx = reinterpret_cast<struct whisper_context *>(contextPtr);
        whisper_free(ctx);
        LOGI("Whisper context freed");
    }
}

JNIEXPORT void JNICALL
Java_com_memexos_app_whisper_WhisperService_fullTranscribe(
        JNIEnv *env,
        jobject /* this */,
        jlong contextPtr,
        jint numThreads,
        jfloatArray audioData) {
    
    if (contextPtr == 0) {
        LOGE("Invalid context pointer");
        return;
    }
    
    struct whisper_context * ctx = reinterpret_cast<struct whisper_context *>(contextPtr);
    
    // Get audio data from Java array
    jfloat* audio = env->GetFloatArrayElements(audioData, nullptr);
    jsize audioLength = env->GetArrayLength(audioData);
    
    LOGI("Processing %d audio samples with %d threads", audioLength, numThreads);
    
    // Configure whisper parameters
    whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    wparams.print_progress   = false;
    wparams.print_special    = false;
    wparams.print_realtime   = false;
    wparams.print_timestamps = false;
    wparams.translate        = false;
    wparams.language         = "en";
    wparams.n_threads        = numThreads;
    wparams.offset_ms        = 0;
    wparams.duration_ms      = 0;
    wparams.single_segment   = false;
    wparams.max_tokens       = 0;
    wparams.audio_ctx        = 0;
    
    // Process audio
    int result = whisper_full(ctx, wparams, audio, audioLength);
    
    // Release audio data
    env->ReleaseFloatArrayElements(audioData, audio, JNI_ABORT);
    
    if (result != 0) {
        LOGE("Failed to process audio, error code: %d", result);
    } else {
        LOGI("Audio processing completed successfully");
    }
}

JNIEXPORT jint JNICALL
Java_com_memexos_app_whisper_WhisperService_getTextSegmentCount(
        JNIEnv *env,
        jobject /* this */,
        jlong contextPtr) {
    
    if (contextPtr == 0) {
        LOGE("Invalid context pointer");
        return 0;
    }
    
    struct whisper_context * ctx = reinterpret_cast<struct whisper_context *>(contextPtr);
    return whisper_full_n_segments(ctx);
}

JNIEXPORT jstring JNICALL
Java_com_memexos_app_whisper_WhisperService_getTextSegment(
        JNIEnv *env,
        jobject /* this */,
        jlong contextPtr,
        jint index) {
    
    if (contextPtr == 0) {
        LOGE("Invalid context pointer");
        return env->NewStringUTF("");
    }
    
    struct whisper_context * ctx = reinterpret_cast<struct whisper_context *>(contextPtr);
    const char * text = whisper_full_get_segment_text(ctx, index);
    
    return env->NewStringUTF(text ? text : "");
}

// Legacy method for backward compatibility
JNIEXPORT jstring JNICALL
Java_com_example_memexos_WhisperWrapper_nativeTranscribe(
        JNIEnv *env,
        jobject /* this */,
        jstring audioPath,
        jstring modelPath) {
    
    // Convert Java strings to C++ strings
    std::string audio_path = jstring2string(env, audioPath);
    std::string model_path = jstring2string(env, modelPath);
    
    LOGI("Starting transcription - Audio: %s, Model: %s", audio_path.c_str(), model_path.c_str());
    
    // Initialize whisper context with model
    struct whisper_context_params cparams = whisper_context_default_params();
    struct whisper_context * ctx = whisper_init_from_file_with_params(model_path.c_str(), cparams);
    
    if (ctx == nullptr) {
        LOGE("Failed to load model from: %s", model_path.c_str());
        return env->NewStringUTF("Error: Failed to load model");
    }
    
    // Read audio file
    std::vector<float> pcmf32 = read_wav(audio_path);
    if (pcmf32.empty()) {
        whisper_free(ctx);
        return env->NewStringUTF("Error: Failed to read audio file");
    }
    
    // Whisper parameters
    whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    
    // Configure parameters for better accuracy
    wparams.print_progress   = false;
    wparams.print_special    = false;
    wparams.print_realtime   = false;
    wparams.print_timestamps = false;
    wparams.translate        = false;
    wparams.language         = "en";
    wparams.n_threads        = 4;  // Adjust based on device capabilities
    wparams.offset_ms        = 0;
    wparams.duration_ms      = 0;
    wparams.single_segment   = false;
    wparams.max_tokens       = 0;
    wparams.audio_ctx        = 0;
    
    // Process audio
    LOGI("Processing %zu samples...", pcmf32.size());
    if (whisper_full(ctx, wparams, pcmf32.data(), pcmf32.size()) != 0) {
        LOGE("Failed to process audio");
        whisper_free(ctx);
        return env->NewStringUTF("Error: Failed to process audio");
    }
    
    // Get results
    const int n_segments = whisper_full_n_segments(ctx);
    LOGI("Found %d segments", n_segments);
    
    std::stringstream result;
    for (int i = 0; i < n_segments; ++i) {
        const char * text = whisper_full_get_segment_text(ctx, i);
        result << text;
        if (i < n_segments - 1) {
            result << " ";
        }
    }
    
    // Clean up
    whisper_free(ctx);
    
    std::string transcription = result.str();
    LOGI("Transcription complete: %s", transcription.c_str());
    
    return env->NewStringUTF(transcription.c_str());
}

// Initialize function - can be used for one-time setup
JNIEXPORT void JNICALL
Java_com_example_memexos_WhisperWrapper_nativeInit(
        JNIEnv *env,
        jobject /* this */) {
    LOGI("WhisperJNI initialized (legacy)");
}

// Cleanup function
JNIEXPORT void JNICALL
Java_com_example_memexos_WhisperWrapper_nativeCleanup(
        JNIEnv *env,
        jobject /* this */) {
    LOGI("WhisperJNI cleanup (legacy)");
}

} // extern "C"
