#include "harmonic_llama_engine.h"

#include <algorithm>
#include <atomic>
#include <cstdint>
#include <cstring>
#include <mutex>
#include <new>
#include <string>
#include <thread>
#include <vector>

#include "llama.h"

namespace {

constexpr int kBatchSize = 512;
constexpr int kContextHeadroom = 8;

harmonic_llama_log_callback host_log_callback = nullptr;
void * host_log_user_data = nullptr;
std::once_flag backend_initialization;
std::atomic<bool> backend_ready{false};

harmonic_llama_log_level host_log_level(ggml_log_level level) {
    switch (level) {
        case GGML_LOG_LEVEL_ERROR:
            return HARMONIC_LLAMA_LOG_ERROR;
        case GGML_LOG_LEVEL_WARN:
            return HARMONIC_LLAMA_LOG_WARN;
        case GGML_LOG_LEVEL_INFO:
            return HARMONIC_LLAMA_LOG_INFO;
        default:
            return HARMONIC_LLAMA_LOG_DEBUG;
    }
}

void llama_log_callback(ggml_log_level level, const char * text, void *) {
    if (host_log_callback != nullptr) {
        try {
            host_log_callback(host_log_level(level), text, host_log_user_data);
        } catch (...) {
            // Logging must never unwind through the C callback boundary.
        }
    }
}

enum class Utf8Status {
    VALID,
    INCOMPLETE,
    INVALID,
};

Utf8Status validate_utf8(const std::string & value) {
    size_t offset = 0;
    while (offset < value.size()) {
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
            return Utf8Status::INVALID;
        }

        if (value.size() - offset < length) {
            return Utf8Status::INCOMPLETE;
        }
        for (size_t index = 1; index < length; index++) {
            const auto continuation = static_cast<unsigned char>(value[offset + index]);
            if ((continuation & 0xC0U) != 0x80U) {
                return Utf8Status::INVALID;
            }
            code_point = (code_point << 6U) | (continuation & 0x3FU);
        }
        if (code_point < minimum || code_point > 0x10FFFFU ||
                (code_point >= 0xD800U && code_point <= 0xDFFFU)) {
            return Utf8Status::INVALID;
        }
        offset += length;
    }
    return Utf8Status::VALID;
}

int inference_threads() {
    const unsigned int available = std::thread::hardware_concurrency();
    const int available_threads = available == 0 ? 4 : static_cast<int>(available);
    return std::max(2, std::min(4, available_threads - 2));
}

}  // namespace

struct harmonic_llama_engine {
    llama_model * model = nullptr;
    llama_context * context = nullptr;
    llama_sampler * sampler = nullptr;
    const llama_vocab * vocab = nullptr;
    int context_size = 0;
    int generated_tokens = 0;
    int max_generated_tokens = 0;
    std::string pending_utf8;
    std::string last_piece;
    std::string last_error;
    const char * fallback_error = nullptr;
};

namespace {

void log_error(const char * message) noexcept {
    if (host_log_callback != nullptr && message != nullptr) {
        try {
            host_log_callback(HARMONIC_LLAMA_LOG_ERROR, message, host_log_user_data);
        } catch (...) {
            // Logging must never unwind through the C callback boundary.
        }
    }
}

void set_error(harmonic_llama_engine * engine, const char * message) noexcept {
    if (engine == nullptr) {
        log_error(message);
        return;
    }
    engine->fallback_error = message;
    try {
        engine->last_error = message;
    } catch (...) {
        engine->last_error.clear();
    }
    log_error(message);
}

void clear_error(harmonic_llama_engine * engine) noexcept {
    engine->last_error.clear();
    engine->fallback_error = nullptr;
}

void set_unexpected_error(harmonic_llama_engine * engine, const char * operation) noexcept {
    set_error(engine, operation);
}

void release_model(harmonic_llama_engine * engine) {
    if (engine->sampler != nullptr) {
        llama_sampler_free(engine->sampler);
        engine->sampler = nullptr;
    }
    if (engine->context != nullptr) {
        llama_free(engine->context);
        engine->context = nullptr;
    }
    if (engine->model != nullptr) {
        llama_model_free(engine->model);
        engine->model = nullptr;
    }
    engine->vocab = nullptr;
    engine->context_size = 0;
    engine->generated_tokens = 0;
    engine->max_generated_tokens = 0;
    engine->pending_utf8.clear();
    engine->last_piece.clear();
}

std::string apply_chat_template(
        harmonic_llama_engine * engine,
        const std::string & system_prompt,
        const std::string & user_prompt) {
    const char * chat_template = llama_model_chat_template(engine->model, nullptr);
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

std::vector<llama_token> tokenize(
        harmonic_llama_engine * engine,
        const std::string & text) {
    int count = llama_tokenize(
            engine->vocab, text.c_str(), static_cast<int32_t>(text.size()),
            nullptr, 0, true, true);
    if (count == INT32_MIN) {
        return {};
    }
    if (count < 0) {
        count = -count;
    }
    std::vector<llama_token> tokens(static_cast<size_t>(count));
    count = llama_tokenize(
            engine->vocab, text.c_str(), static_cast<int32_t>(text.size()),
            tokens.data(), count, true, true);
    if (count < 0) {
        return {};
    }
    tokens.resize(static_cast<size_t>(count));
    return tokens;
}

bool decode_prompt(
        harmonic_llama_engine * engine,
        const std::vector<llama_token> & tokens) {
    for (size_t offset = 0; offset < tokens.size(); offset += kBatchSize) {
        int count = static_cast<int>(std::min(
                static_cast<size_t>(kBatchSize), tokens.size() - offset));
        llama_batch batch = llama_batch_get_one(
                const_cast<llama_token *>(tokens.data() + offset), count);
        if (llama_decode(engine->context, batch) != 0) {
            set_error(engine, "Failed to process the summary input");
            return false;
        }
    }
    return true;
}

std::string token_to_piece(harmonic_llama_engine * engine, llama_token token) {
    std::vector<char> buffer(128);
    int count = llama_token_to_piece(
            engine->vocab, token, buffer.data(), static_cast<int32_t>(buffer.size()), 0, true);
    if (count < 0) {
        buffer.resize(static_cast<size_t>(-count));
        count = llama_token_to_piece(
                engine->vocab, token, buffer.data(), static_cast<int32_t>(buffer.size()), 0, true);
    }
    if (count <= 0) {
        return {};
    }
    return std::string(buffer.data(), static_cast<size_t>(count));
}

}  // namespace

extern "C" void harmonic_llama_backend_initialize(
        harmonic_llama_log_callback callback,
        void * user_data) {
    try {
        std::call_once(backend_initialization, [callback, user_data] {
            host_log_callback = callback;
            host_log_user_data = user_data;
            llama_log_set(llama_log_callback, nullptr);
            llama_backend_init();
            backend_ready.store(true, std::memory_order_release);
        });
    } catch (...) {
        log_error("Could not initialize the local inference backend");
    }
}

extern "C" harmonic_llama_engine * harmonic_llama_create(void) {
    try {
        return new (std::nothrow) harmonic_llama_engine();
    } catch (...) {
        log_error("Could not allocate the local inference engine");
        return nullptr;
    }
}

extern "C" void harmonic_llama_destroy(harmonic_llama_engine * engine) {
    if (engine == nullptr) {
        return;
    }
    try {
        release_model(engine);
        delete engine;
    } catch (...) {
        log_error("Could not cleanly destroy the local inference engine");
    }
}

extern "C" int harmonic_llama_load(
        harmonic_llama_engine * engine,
        const char * model_path,
        int context_tokens) {
    try {
        if (engine == nullptr || model_path == nullptr || context_tokens <= 0) {
            return 0;
        }
        if (!backend_ready.load(std::memory_order_acquire)) {
            set_error(engine, "The local inference backend is unavailable");
            return 0;
        }
        release_model(engine);
        clear_error(engine);

        llama_model_params model_params = llama_model_default_params();
        engine->model = llama_model_load_from_file(model_path, model_params);
        if (engine->model == nullptr) {
            set_error(engine, "Could not load the GGUF model");
            return 0;
        }

        llama_context_params context_params = llama_context_default_params();
        context_params.n_ctx = static_cast<uint32_t>(context_tokens);
        context_params.n_batch = kBatchSize;
        context_params.n_ubatch = kBatchSize;
        const int threads = inference_threads();
        context_params.n_threads = threads;
        context_params.n_threads_batch = threads;
        engine->context = llama_init_from_model(engine->model, context_params);
        if (engine->context == nullptr) {
            set_error(engine, "Could not allocate the GGUF model context");
            release_model(engine);
            return 0;
        }

        engine->vocab = llama_model_get_vocab(engine->model);
        llama_sampler_chain_params sampler_params = llama_sampler_chain_default_params();
        engine->sampler = llama_sampler_chain_init(sampler_params);
        if (engine->sampler == nullptr) {
            set_error(engine, "Could not allocate the GGUF sampler");
            release_model(engine);
            return 0;
        }
        const auto add_sampler = [engine](llama_sampler * sampler) {
            if (sampler == nullptr) {
                return false;
            }
            llama_sampler_chain_add(engine->sampler, sampler);
            return true;
        };
        if (!add_sampler(llama_sampler_init_top_k(20)) ||
                !add_sampler(llama_sampler_init_top_p(0.9f, 1)) ||
                !add_sampler(llama_sampler_init_temp(0.3f)) ||
                !add_sampler(llama_sampler_init_dist(LLAMA_DEFAULT_SEED))) {
            set_error(engine, "Could not allocate the GGUF sampler");
            release_model(engine);
            return 0;
        }
        engine->context_size = context_tokens;
        return 1;
    } catch (const std::bad_alloc &) {
        release_model(engine);
        set_error(engine, "Not enough memory to load the local model");
        return 0;
    } catch (...) {
        release_model(engine);
        set_unexpected_error(engine, "Local model loading failed unexpectedly");
        return 0;
    }
}

extern "C" int harmonic_llama_start(
        harmonic_llama_engine * engine,
        const char * system_prompt,
        const char * user_prompt,
        const char * response_prefix,
        int output_tokens) {
    try {
        if (engine == nullptr || engine->model == nullptr || engine->context == nullptr ||
                engine->sampler == nullptr) {
            if (engine != nullptr) {
                set_error(engine, "No GGUF model is loaded");
            }
            return 0;
        }
        if (system_prompt == nullptr || user_prompt == nullptr || response_prefix == nullptr ||
                output_tokens <= 0) {
            set_error(engine, "Invalid GGUF generation request");
            return 0;
        }
        clear_error(engine);
        std::string prompt = apply_chat_template(engine, system_prompt, user_prompt);
        prompt += response_prefix;

        std::vector<llama_token> tokens = tokenize(engine, prompt);
        if (tokens.empty()) {
            set_error(engine, "Could not tokenize the summary input");
            return 0;
        }
        if (static_cast<int>(tokens.size()) + output_tokens + kContextHeadroom >
                engine->context_size) {
            set_error(engine, "The summary input is too long for this model context");
            return 0;
        }
        llama_memory_clear(llama_get_memory(engine->context), true);
        llama_sampler_reset(engine->sampler);
        if (!decode_prompt(engine, tokens)) {
            return 0;
        }
        engine->generated_tokens = 0;
        engine->max_generated_tokens = output_tokens;
        engine->pending_utf8.clear();
        engine->last_piece.clear();
        return 1;
    } catch (const std::bad_alloc &) {
        set_error(engine, "Not enough memory to start local summarization");
        return 0;
    } catch (...) {
        set_unexpected_error(engine, "Local summarization failed to start");
        return 0;
    }
}

extern "C" harmonic_llama_next_result harmonic_llama_next(
        harmonic_llama_engine * engine,
        const char ** utf8_piece,
        size_t * utf8_length) {
    if (utf8_piece != nullptr) {
        *utf8_piece = nullptr;
    }
    if (utf8_length != nullptr) {
        *utf8_length = 0;
    }
    try {
        if (engine == nullptr || utf8_piece == nullptr || utf8_length == nullptr ||
                engine->context == nullptr || engine->sampler == nullptr) {
            if (engine != nullptr) {
                set_error(engine, "No GGUF generation is active");
            }
            return HARMONIC_LLAMA_NEXT_ERROR;
        }

        while (engine->generated_tokens < engine->max_generated_tokens) {
            llama_token token = llama_sampler_sample(engine->sampler, engine->context, -1);
            if (llama_vocab_is_eog(engine->vocab, token)) {
                if (!engine->pending_utf8.empty()) {
                    set_error(engine, "GGUF model ended with incomplete UTF-8");
                    return HARMONIC_LLAMA_NEXT_ERROR;
                }
                return HARMONIC_LLAMA_NEXT_END;
            }
            llama_batch batch = llama_batch_get_one(&token, 1);
            if (llama_decode(engine->context, batch) != 0) {
                set_error(engine, "GGUF generation failed");
                return HARMONIC_LLAMA_NEXT_ERROR;
            }
            engine->generated_tokens++;
            engine->pending_utf8 += token_to_piece(engine, token);
            switch (validate_utf8(engine->pending_utf8)) {
                case Utf8Status::INCOMPLETE:
                    continue;
                case Utf8Status::INVALID:
                    set_error(engine, "GGUF model produced invalid UTF-8");
                    return HARMONIC_LLAMA_NEXT_ERROR;
                case Utf8Status::VALID:
                    engine->last_piece = engine->pending_utf8;
                    engine->pending_utf8.clear();
                    *utf8_piece = engine->last_piece.data();
                    *utf8_length = engine->last_piece.size();
                    return HARMONIC_LLAMA_NEXT_PIECE;
            }
        }

        if (!engine->pending_utf8.empty()) {
            set_error(engine, "GGUF model ended with incomplete UTF-8");
            return HARMONIC_LLAMA_NEXT_ERROR;
        }
        return HARMONIC_LLAMA_NEXT_END;
    } catch (const std::bad_alloc &) {
        set_error(engine, "Not enough memory to continue local summarization");
        return HARMONIC_LLAMA_NEXT_ERROR;
    } catch (...) {
        set_unexpected_error(engine, "Local summarization failed unexpectedly");
        return HARMONIC_LLAMA_NEXT_ERROR;
    }
}

extern "C" const char * harmonic_llama_last_error(const harmonic_llama_engine * engine) {
    if (engine == nullptr) {
        return "GGUF inference engine is unavailable";
    }
    if (!engine->last_error.empty()) {
        return engine->last_error.c_str();
    }
    return engine->fallback_error == nullptr ? "" : engine->fallback_error;
}

extern "C" int harmonic_llama_next_utf8(
        harmonic_llama_engine * engine,
        char * buffer,
        int capacity) {
    if (buffer == nullptr || capacity <= 0) {
        if (engine != nullptr) {
            set_error(engine, "Invalid GGUF output buffer");
        }
        return -1;
    }
    buffer[0] = '\0';
    const char * utf8_piece = nullptr;
    size_t utf8_length = 0;
    const harmonic_llama_next_result result = harmonic_llama_next(
            engine, &utf8_piece, &utf8_length);
    if (result == HARMONIC_LLAMA_NEXT_END) {
        return 0;
    }
    if (result == HARMONIC_LLAMA_NEXT_ERROR) {
        return -1;
    }
    if (utf8_length >= static_cast<size_t>(capacity)) {
        set_error(engine, "GGUF output piece exceeded the desktop bridge buffer");
        return -1;
    }
    std::memcpy(buffer, utf8_piece, utf8_length);
    buffer[utf8_length] = '\0';
    return static_cast<int>(utf8_length);
}

extern "C" void harmonic_llama_close(harmonic_llama_engine * engine) {
    if (engine != nullptr) {
        try {
            release_model(engine);
        } catch (...) {
            set_unexpected_error(engine, "Could not cleanly close the local inference engine");
        }
    }
}
