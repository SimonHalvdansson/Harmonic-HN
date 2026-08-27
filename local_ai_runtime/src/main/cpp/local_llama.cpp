#include <android/log.h>
#include <jni.h>

#include <cstdint>
#include <exception>
#include <new>
#include <string>
#include <vector>

#include "harmonic_llama_engine.h"

namespace {

harmonic_llama_engine * engine = nullptr;

void throw_java_exception(JNIEnv * env, const char * class_name, const char * message) noexcept {
    if (env == nullptr || env->ExceptionCheck()) {
        return;
    }
    jclass exception_class = env->FindClass(class_name);
    if (exception_class != nullptr) {
        env->ThrowNew(exception_class, message);
        env->DeleteLocalRef(exception_class);
    }
}

template<typename Result, typename Callback>
Result guard_jni(JNIEnv * env, Result fallback, Callback && callback) noexcept {
    try {
        return callback();
    } catch (const std::bad_alloc &) {
        throw_java_exception(env, "java/lang/OutOfMemoryError", "Local inference ran out of memory");
    } catch (const std::exception & error) {
        throw_java_exception(env, "java/lang/RuntimeException", error.what());
    } catch (...) {
        throw_java_exception(env, "java/lang/RuntimeException", "Local inference failed unexpectedly");
    }
    return fallback;
}

template<typename Callback>
void guard_jni_void(JNIEnv * env, Callback && callback) noexcept {
    guard_jni<int>(env, 0, [&callback] {
        callback();
        return 1;
    });
}

class ScopedUtfChars final {
public:
    ScopedUtfChars(JNIEnv * env, jstring value) : env_(env), value_(value) {
        if (env_ != nullptr && value_ != nullptr) {
            chars_ = env_->GetStringUTFChars(value_, nullptr);
        }
    }

    ~ScopedUtfChars() {
        if (chars_ != nullptr) {
            env_->ReleaseStringUTFChars(value_, chars_);
        }
    }

    const char * get() const {
        return chars_;
    }

private:
    JNIEnv * env_;
    jstring value_;
    const char * chars_ = nullptr;
};

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
    __android_log_write(priority, "LocalLlama", text == nullptr ? "" : text);
}

harmonic_llama_engine * require_engine(JNIEnv * env) {
    if (engine == nullptr) {
        engine = harmonic_llama_create();
    }
    if (engine == nullptr) {
        throw_java_exception(env, "java/lang/OutOfMemoryError", "Could not allocate local inference");
    }
    return engine;
}

bool require_string(JNIEnv * env, jstring value, const char * name) {
    if (value != nullptr) {
        return true;
    }
    throw_java_exception(env, "java/lang/IllegalArgumentException", name);
    return false;
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
        JNIEnv * env, jobject) {
    guard_jni_void(env, [env] {
        harmonic_llama_backend_initialize(android_log_callback, nullptr);
        require_engine(env);
    });
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_simon_harmonichackernews_summary_local_GgufInference_nativeLoad(
        JNIEnv * env, jobject, jstring model_path, jint context_tokens) {
    return guard_jni<jboolean>(env, JNI_FALSE, [env, model_path, context_tokens] {
        if (!require_string(env, model_path, "The local model path is required")) {
            return JNI_FALSE;
        }
        ScopedUtfChars path(env, model_path);
        if (path.get() == nullptr) {
            return JNI_FALSE;
        }
        harmonic_llama_engine * current_engine = require_engine(env);
        if (current_engine == nullptr) {
            return JNI_FALSE;
        }
        return harmonic_llama_load(current_engine, path.get(), context_tokens)
                ? JNI_TRUE : JNI_FALSE;
    });
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_simon_harmonichackernews_summary_local_GgufInference_nativeStart(
        JNIEnv * env, jobject, jstring system_text, jstring user_text,
        jstring response_prefix, jint output_tokens) {
    return guard_jni<jboolean>(env, JNI_FALSE, [=] {
        if (!require_string(env, system_text, "The local summary instruction is required") ||
                !require_string(env, user_text, "The local summary input is required") ||
                !require_string(env, response_prefix, "The local summary prefix is required")) {
            return JNI_FALSE;
        }
        ScopedUtfChars system_chars(env, system_text);
        if (system_chars.get() == nullptr) {
            return JNI_FALSE;
        }
        ScopedUtfChars user_chars(env, user_text);
        if (user_chars.get() == nullptr) {
            return JNI_FALSE;
        }
        ScopedUtfChars prefix_chars(env, response_prefix);
        if (prefix_chars.get() == nullptr) {
            return JNI_FALSE;
        }
        harmonic_llama_engine * current_engine = require_engine(env);
        if (current_engine == nullptr) {
            return JNI_FALSE;
        }
        return harmonic_llama_start(
                current_engine,
                system_chars.get(),
                user_chars.get(),
                prefix_chars.get(),
                output_tokens) ? JNI_TRUE : JNI_FALSE;
    });
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_simon_harmonichackernews_summary_local_GgufInference_nativeNextToken(
        JNIEnv * env, jobject) {
    return guard_jni<jstring>(env, nullptr, [env] {
        harmonic_llama_engine * current_engine = require_engine(env);
        if (current_engine == nullptr) {
            return static_cast<jstring>(nullptr);
        }
        const char * utf8_piece = nullptr;
        size_t utf8_length = 0;
        const harmonic_llama_next_result result = harmonic_llama_next(
                current_engine, &utf8_piece, &utf8_length);
        if (result != HARMONIC_LLAMA_NEXT_PIECE) {
            return static_cast<jstring>(nullptr);
        }

        std::vector<jchar> decoded;
        if (!decode_utf8(utf8_piece, utf8_length, decoded)) {
            __android_log_write(ANDROID_LOG_ERROR, "LocalLlama", "Invalid UTF-8 from core engine");
            return static_cast<jstring>(nullptr);
        }
        const jchar empty = 0;
        return env->NewString(
                decoded.empty() ? &empty : decoded.data(),
                static_cast<jsize>(decoded.size()));
    });
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_simon_harmonichackernews_summary_local_GgufInference_nativeLastError(
        JNIEnv * env, jobject) {
    return guard_jni<jstring>(env, nullptr, [env] {
        return env->NewStringUTF(harmonic_llama_last_error(engine));
    });
}

extern "C" JNIEXPORT void JNICALL
Java_com_simon_harmonichackernews_summary_local_GgufInference_nativeClose(
        JNIEnv * env, jobject) {
    guard_jni_void(env, [] {
        if (engine != nullptr) {
            harmonic_llama_close(engine);
        }
    });
}
