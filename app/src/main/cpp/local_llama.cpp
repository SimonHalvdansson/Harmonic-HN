#include <android/log.h>
#include <jni.h>
#include <unistd.h>

#include <algorithm>
#include <cstdarg>
#include <string>
#include <vector>

#include "llama.h"

namespace {

constexpr int kBatchSize = 512;
constexpr int kContextHeadroom = 8;

llama_model * model = nullptr;
llama_context * context = nullptr;
llama_sampler * sampler = nullptr;
const llama_vocab * vocab = nullptr;
int context_size = 0;
int generated_tokens = 0;
int max_generated_tokens = 0;
std::string pending_utf8;
std::string last_error;
bool backend_initialized = false;

void log_callback(ggml_log_level level, const char * text, void *) {
    int priority = ANDROID_LOG_DEBUG;
    if (level == GGML_LOG_LEVEL_ERROR) {
        priority = ANDROID_LOG_ERROR;
    } else if (level == GGML_LOG_LEVEL_WARN) {
        priority = ANDROID_LOG_WARN;
    } else if (level == GGML_LOG_LEVEL_INFO) {
        priority = ANDROID_LOG_INFO;
    }
    __android_log_write(priority, "LocalLlama", text);
}

void set_error(const std::string & message) {
    last_error = message;
    __android_log_print(ANDROID_LOG_ERROR, "LocalLlama", "%s", message.c_str());
}

void release_model() {
    if (sampler != nullptr) {
        llama_sampler_free(sampler);
        sampler = nullptr;
    }
    if (context != nullptr) {
        llama_free(context);
        context = nullptr;
    }
    if (model != nullptr) {
        llama_model_free(model);
        model = nullptr;
    }
    vocab = nullptr;
    context_size = 0;
    generated_tokens = 0;
    max_generated_tokens = 0;
    pending_utf8.clear();
}

bool is_valid_utf8(const std::string & value) {
    const auto * bytes = reinterpret_cast<const unsigned char *>(value.c_str());
    while (*bytes != 0) {
        int length;
        if ((*bytes & 0x80) == 0) {
            length = 1;
        } else if ((*bytes & 0xE0) == 0xC0) {
            length = 2;
        } else if ((*bytes & 0xF0) == 0xE0) {
            length = 3;
        } else if ((*bytes & 0xF8) == 0xF0) {
            length = 4;
        } else {
            return false;
        }
        bytes++;
        for (int i = 1; i < length; i++, bytes++) {
            if ((*bytes & 0xC0) != 0x80) {
                return false;
            }
        }
    }
    return true;
}

std::string apply_chat_template(const std::string & system_prompt,
                                const std::string & user_prompt) {
    const char * chat_template = llama_model_chat_template(model, nullptr);
    if (chat_template == nullptr) {
        return "System: " + system_prompt + "\nUser: " + user_prompt + "\nAssistant:";
    }

    const llama_chat_message messages[] = {
            {"system", system_prompt.c_str()},
            {"user", user_prompt.c_str()},
    };
    int required = llama_chat_apply_template(
            chat_template, messages, 2, true, nullptr, 0);
    if (required <= 0) {
        return "System: " + system_prompt + "\nUser: " + user_prompt + "\nAssistant:";
    }
    std::vector<char> buffer(static_cast<size_t>(required) + 1);
    int written = llama_chat_apply_template(
            chat_template, messages, 2, true, buffer.data(), required + 1);
    if (written <= 0) {
        return "System: " + system_prompt + "\nUser: " + user_prompt + "\nAssistant:";
    }
    return std::string(buffer.data(), static_cast<size_t>(written));
}

std::vector<llama_token> tokenize(const std::string & text) {
    int count = llama_tokenize(
            vocab, text.c_str(), static_cast<int32_t>(text.size()),
            nullptr, 0, true, true);
    if (count == INT32_MIN) {
        return {};
    }
    if (count < 0) {
        count = -count;
    }
    std::vector<llama_token> tokens(static_cast<size_t>(count));
    count = llama_tokenize(
            vocab, text.c_str(), static_cast<int32_t>(text.size()),
            tokens.data(), count, true, true);
    if (count < 0) {
        return {};
    }
    tokens.resize(static_cast<size_t>(count));
    return tokens;
}

bool decode_prompt(const std::vector<llama_token> & tokens) {
    for (size_t offset = 0; offset < tokens.size(); offset += kBatchSize) {
        int count = static_cast<int>(std::min(
                static_cast<size_t>(kBatchSize), tokens.size() - offset));
        llama_batch batch = llama_batch_get_one(
                const_cast<llama_token *>(tokens.data() + offset), count);
        if (llama_decode(context, batch) != 0) {
            set_error("Failed to process the summary input");
            return false;
        }
    }
    return true;
}

std::string token_to_piece(llama_token token) {
    std::vector<char> buffer(128);
    int count = llama_token_to_piece(
            vocab, token, buffer.data(), static_cast<int32_t>(buffer.size()), 0, true);
    if (count < 0) {
        buffer.resize(static_cast<size_t>(-count));
        count = llama_token_to_piece(
                vocab, token, buffer.data(), static_cast<int32_t>(buffer.size()), 0, true);
    }
    if (count <= 0) {
        return {};
    }
    return std::string(buffer.data(), static_cast<size_t>(count));
}

}  // namespace

extern "C" JNIEXPORT void JNICALL
Java_com_simon_harmonichackernews_summary_local_GgufInference_nativeInitialize(
        JNIEnv *, jobject) {
    if (backend_initialized) {
        return;
    }
    llama_log_set(log_callback, nullptr);
    llama_backend_init();
    backend_initialized = true;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_simon_harmonichackernews_summary_local_GgufInference_nativeLoad(
        JNIEnv * env, jobject, jstring model_path, jint requested_context_size) {
    release_model();
    last_error.clear();

    const char * path = env->GetStringUTFChars(model_path, nullptr);
    llama_model_params model_params = llama_model_default_params();
    model = llama_model_load_from_file(path, model_params);
    env->ReleaseStringUTFChars(model_path, path);
    if (model == nullptr) {
        set_error("Could not load the GGUF model");
        return JNI_FALSE;
    }

    llama_context_params context_params = llama_context_default_params();
    context_params.n_ctx = static_cast<uint32_t>(requested_context_size);
    context_params.n_batch = kBatchSize;
    context_params.n_ubatch = kBatchSize;
    int available_threads = static_cast<int>(sysconf(_SC_NPROCESSORS_ONLN));
    int threads = std::max(2, std::min(4, available_threads - 2));
    context_params.n_threads = threads;
    context_params.n_threads_batch = threads;
    context = llama_init_from_model(model, context_params);
    if (context == nullptr) {
        set_error("Could not allocate the GGUF model context");
        release_model();
        return JNI_FALSE;
    }

    vocab = llama_model_get_vocab(model);
    llama_sampler_chain_params sampler_params = llama_sampler_chain_default_params();
    sampler = llama_sampler_chain_init(sampler_params);
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(20));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.3f));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    context_size = requested_context_size;
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_simon_harmonichackernews_summary_local_GgufInference_nativeStart(
        JNIEnv * env, jobject, jstring system_text, jstring user_text,
        jstring response_prefix, jint output_tokens) {
    if (model == nullptr || context == nullptr) {
        set_error("No GGUF model is loaded");
        return JNI_FALSE;
    }
    const char * system_chars = env->GetStringUTFChars(system_text, nullptr);
    const char * user_chars = env->GetStringUTFChars(user_text, nullptr);
    const char * response_prefix_chars = env->GetStringUTFChars(response_prefix, nullptr);
    std::string prompt = apply_chat_template(system_chars, user_chars);
    prompt += response_prefix_chars;
    env->ReleaseStringUTFChars(system_text, system_chars);
    env->ReleaseStringUTFChars(user_text, user_chars);
    env->ReleaseStringUTFChars(response_prefix, response_prefix_chars);

    std::vector<llama_token> tokens = tokenize(prompt);
    if (tokens.empty()) {
        set_error("Could not tokenize the summary input");
        return JNI_FALSE;
    }
    if (static_cast<int>(tokens.size()) + output_tokens + kContextHeadroom > context_size) {
        set_error("The summary input is too long for this model context");
        return JNI_FALSE;
    }
    llama_memory_clear(llama_get_memory(context), true);
    llama_sampler_reset(sampler);
    if (!decode_prompt(tokens)) {
        return JNI_FALSE;
    }
    generated_tokens = 0;
    max_generated_tokens = output_tokens;
    pending_utf8.clear();
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_simon_harmonichackernews_summary_local_GgufInference_nativeNextToken(
        JNIEnv * env, jobject) {
    if (context == nullptr || sampler == nullptr || generated_tokens >= max_generated_tokens) {
        return nullptr;
    }

    llama_token token = llama_sampler_sample(sampler, context, -1);
    if (llama_vocab_is_eog(vocab, token)) {
        return nullptr;
    }
    llama_batch batch = llama_batch_get_one(&token, 1);
    if (llama_decode(context, batch) != 0) {
        set_error("GGUF generation failed");
        return nullptr;
    }
    generated_tokens++;
    pending_utf8 += token_to_piece(token);
    if (!is_valid_utf8(pending_utf8)) {
        return env->NewStringUTF("");
    }
    jstring result = env->NewStringUTF(pending_utf8.c_str());
    pending_utf8.clear();
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_simon_harmonichackernews_summary_local_GgufInference_nativeLastError(
        JNIEnv * env, jobject) {
    return env->NewStringUTF(last_error.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_simon_harmonichackernews_summary_local_GgufInference_nativeClose(
        JNIEnv *, jobject) {
    release_model();
}
