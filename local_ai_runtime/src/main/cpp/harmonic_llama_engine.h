#pragma once

#include <stddef.h>

#if defined(_WIN32) && defined(HARMONIC_LLAMA_BUILD_SHARED)
#define HARMONIC_LLAMA_API __declspec(dllexport)
#elif defined(__GNUC__) && defined(HARMONIC_LLAMA_BUILD_SHARED)
#define HARMONIC_LLAMA_API __attribute__((visibility("default")))
#else
#define HARMONIC_LLAMA_API
#endif

#ifdef __cplusplus
extern "C" {
#endif

typedef struct harmonic_llama_engine harmonic_llama_engine;

typedef enum harmonic_llama_log_level {
    HARMONIC_LLAMA_LOG_ERROR,
    HARMONIC_LLAMA_LOG_WARN,
    HARMONIC_LLAMA_LOG_INFO,
    HARMONIC_LLAMA_LOG_DEBUG,
} harmonic_llama_log_level;

typedef void (*harmonic_llama_log_callback)(
        harmonic_llama_log_level level,
        const char * text,
        void * user_data);

typedef enum harmonic_llama_next_result {
    HARMONIC_LLAMA_NEXT_ERROR = -1,
    HARMONIC_LLAMA_NEXT_END = 0,
    HARMONIC_LLAMA_NEXT_PIECE = 1,
} harmonic_llama_next_result;

/** Initializes llama.cpp once per process and installs the host's optional log sink. */
HARMONIC_LLAMA_API void harmonic_llama_backend_initialize(
        harmonic_llama_log_callback callback,
        void * user_data);

/** Creates an independent inference session. The caller owns the returned handle. */
HARMONIC_LLAMA_API harmonic_llama_engine * harmonic_llama_create(void);

HARMONIC_LLAMA_API void harmonic_llama_destroy(harmonic_llama_engine * engine);

HARMONIC_LLAMA_API int harmonic_llama_load(
        harmonic_llama_engine * engine,
        const char * model_path,
        int context_tokens);

HARMONIC_LLAMA_API int harmonic_llama_start(
        harmonic_llama_engine * engine,
        const char * system_prompt,
        const char * user_prompt,
        const char * response_prefix,
        int output_tokens);

/**
 * Produces the next complete UTF-8 piece. The returned pointer remains valid until the next call
 * involving this engine and must not be freed by the caller.
 */
HARMONIC_LLAMA_API harmonic_llama_next_result harmonic_llama_next(
        harmonic_llama_engine * engine,
        const char ** utf8_piece,
        size_t * utf8_length);

/**
 * Copy-based variant for desktop FFI callers. Returns the copied UTF-8 byte count, zero at end,
 * or -1 on error. The buffer is always NUL-terminated when capacity is positive.
 */
HARMONIC_LLAMA_API int harmonic_llama_next_utf8(
        harmonic_llama_engine * engine,
        char * buffer,
        int capacity);

HARMONIC_LLAMA_API const char * harmonic_llama_last_error(
        const harmonic_llama_engine * engine);

HARMONIC_LLAMA_API void harmonic_llama_close(harmonic_llama_engine * engine);

#ifdef __cplusplus
}
#endif
