#include "harmonic_llama_engine.h"
#include <string>
struct harmonic_llama_engine {};
static std::string captured;
static std::string hex(const char * text) {
    std::string result;
    for (const unsigned char * p = reinterpret_cast<const unsigned char *>(text); *p; ++p) {
        result += "0123456789abcdef"[*p >> 4];
        result += "0123456789abcdef"[*p & 15];
    }
    return result;
}
extern "C" void harmonic_llama_backend_initialize(harmonic_llama_log_callback, void *) {}
extern "C" harmonic_llama_engine * harmonic_llama_create() { static harmonic_llama_engine engine; return &engine; }
extern "C" int harmonic_llama_load(harmonic_llama_engine *, const char * path, int) { captured = hex(path); return 1; }
extern "C" int harmonic_llama_start(harmonic_llama_engine *, const char * system, const char * user, const char * prefix, int) {
    captured = hex(system) + ":" + hex(user) + ":" + hex(prefix);
    return 1;
}
extern "C" harmonic_llama_next_result harmonic_llama_next(harmonic_llama_engine *, const char **, size_t *) { return HARMONIC_LLAMA_NEXT_END; }
extern "C" void harmonic_llama_cancel(harmonic_llama_engine *) {}
extern "C" void harmonic_llama_reset_cancellation(harmonic_llama_engine *) {}
extern "C" const char * harmonic_llama_last_error(const harmonic_llama_engine *) { return captured.c_str(); }
extern "C" void harmonic_llama_close(harmonic_llama_engine *) {}
