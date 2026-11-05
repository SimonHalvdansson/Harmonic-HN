cmake_minimum_required(VERSION 3.22.1)
project(harmonic_ai_compatibility LANGUAGES C)

# Bundletool requires native feature modules to cover the base module's ABIs.
# LiteRT-LM has no 32-bit binaries. This marker preserves app installation on
# those devices; it is never loaded and does not implement an inference engine.
# Runtime capability checks prevent model downloads and inference on 32-bit.
add_library(harmonic_ai_compatibility SHARED compatibility.c)
