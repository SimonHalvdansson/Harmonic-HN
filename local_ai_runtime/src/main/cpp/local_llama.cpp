#include <android/log.h>
#include <jni.h>

#include <cstdint>
#include <string>
#include <vector>

#include "harmonic_llama_engine.h"

namespace {

harmonic_llama_engine * engine = nullptr;

void android_log_callback(harmonic_llama_log_level level, const char * text, void *) {
    int priority = ANDROID_LOG_DEBUG;
    switch (level) {
        case HARMONIC_LLAMA_LOG_ERROR:
            priority = ANDROID_LOG_ERROR;
            break;
        case HARMONIC_LLAMA_LOG_WARN:
            priority = ANDROID_LOG_WARN;
            break;
        case HARMONIC_LLAMA_LOG_INFO:
            priority = ANDROID_LOG_INFO;
            break;
        case HARMONIC_LLAMA_LOG_DEBUG:
            break;
    }
    __android_log_write(priority, "LocalLlama", text);
}

harmonic_llama_engine * require_engine() {
    if (engine == nullptr) {
        engine = harmonic_llama_create();
    }
    return engine;
}

bool decode_utf8(const char * value, size_t size, std::vector<jchar> & output) {
    output.clear();
    size_t offset = 0;
    while (offset < size) {
        const auto first = static_cast<unsigned char>(value[offset]);
        uint32_t code_point;
        uint32_t minimum;
        size_t length;
        if ((first & 0x80U) == 0) {
            code_point = first;
            minimum = 0;
            length = 1;
        } else if ((first & 0xE0U) == 0xC0U) {
            code_point = first & 0x1FU;
            minimum = 0x80U;
            length = 2;
        } else if ((first & 0xF0U) == 0xE0U) {
            code_point = first & 0x0FU;
            minimum = 0x800U;
            length = 3;
        } else if ((first & 0xF8U) == 0xF0U) {
            code_point = first & 0x07U;
            minimum = 0x10000U;
            length = 4;
        } else {
            return false;
        }

        if (size - offset < length) {
            return false;
        }
        for (size_t index = 1; index < length; index++) {
            const auto continuation = static_cast<unsigned char>(value[offset + index]);
            if ((continuation & 0xC0U) != 0x80U) {
                return false;
            }
            code_point = (code_point << 6U) | (continuation & 0x3FU);
        }
        if (code_point < minimum || code_point > 0x10FFFFU ||
                (code_point >= 0xD800U && code_point <= 0xDFFFU)) {
            return false;
        }

        if (code_point <= 0xFFFFU) {
            output.push_back(static_cast<jchar>(code_point));
        } else {
            code_point -= 0x10000U;
            output.push_back(static_cast<jchar>(0xD800U + (code_point >> 10U)));
            output.push_back(static_cast<jchar>(0xDC00U + (code_point & 0x3FFU)));
        }
        offset += length;
    }
    return true;
}

}  // namespace

extern "C" JNIEXPORT void JNICALL
Java_com_simon_harmonichackernews_summary_local_GgufInference_nativeInitialize(
        JNIEnv *, jobject) {
    harmonic_llama_backend_initialize(android_log_callback, nullptr);
    require_engine();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_simon_harmonichackernews_summary_local_GgufInference_nativeLoad(
        JNIEnv * env, jobject, jstring model_path, jint context_tokens) {
    const char * path = env->GetStringUTFChars(model_path, nullptr);
    const int loaded = harmonic_llama_load(require_engine(), path, context_tokens);
    env->ReleaseStringUTFChars(model_path, path);
    return loaded ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_simon_harmonichackernews_summary_local_GgufInference_nativeStart(
        JNIEnv * env, jobject, jstring system_text, jstring user_text,
        jstring response_prefix, jint output_tokens) {
    const char * system_chars = env->GetStringUTFChars(system_text, nullptr);
    const char * user_chars = env->GetStringUTFChars(user_text, nullptr);
    const char * response_prefix_chars = env->GetStringUTFChars(response_prefix, nullptr);
    const int started = harmonic_llama_start(
            require_engine(), system_chars, user_chars, response_prefix_chars, output_tokens);
    env->ReleaseStringUTFChars(system_text, system_chars);
    env->ReleaseStringUTFChars(user_text, user_chars);
    env->ReleaseStringUTFChars(response_prefix, response_prefix_chars);
    return started ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_simon_harmonichackernews_summary_local_GgufInference_nativeNextToken(
        JNIEnv * env, jobject) {
    const char * utf8_piece = nullptr;
    size_t utf8_length = 0;
    const harmonic_llama_next_result result = harmonic_llama_next(
            require_engine(), &utf8_piece, &utf8_length);
    if (result != HARMONIC_LLAMA_NEXT_PIECE) {
        return nullptr;
    }

    std::vector<jchar> decoded;
    if (!decode_utf8(utf8_piece, utf8_length, decoded)) {
        __android_log_write(ANDROID_LOG_ERROR, "LocalLlama", "Invalid UTF-8 from core engine");
        return nullptr;
    }
    const jchar empty = 0;
    return env->NewString(
            decoded.empty() ? &empty : decoded.data(),
            static_cast<jsize>(decoded.size()));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_simon_harmonichackernews_summary_local_GgufInference_nativeLastError(
        JNIEnv * env, jobject) {
    return env->NewStringUTF(harmonic_llama_last_error(require_engine()));
}

extern "C" JNIEXPORT void JNICALL
Java_com_simon_harmonichackernews_summary_local_GgufInference_nativeClose(
        JNIEnv *, jobject) {
    harmonic_llama_close(require_engine());
}
