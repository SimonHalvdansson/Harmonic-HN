cmake_minimum_required(VERSION 3.22.1)

project(local-llama LANGUAGES C CXX)

option(HARMONIC_BUILD_DESKTOP "Build the desktop local-AI shared library" OFF)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)

set(LLAMA_CPP_SOURCE_DIR "${CMAKE_CURRENT_LIST_DIR}/third_party/llama.cpp")
add_subdirectory("${LLAMA_CPP_SOURCE_DIR}" llama.cpp-build EXCLUDE_FROM_ALL)

add_library(harmonic-llama-core STATIC harmonic_llama_engine.cpp)
target_include_directories(harmonic-llama-core PUBLIC
        "${CMAKE_CURRENT_LIST_DIR}"
        "${LLAMA_CPP_SOURCE_DIR}/include"
        "${LLAMA_CPP_SOURCE_DIR}/ggml/include")
target_link_libraries(harmonic-llama-core PUBLIC llama)

if(ANDROID)
    add_library(local-llama SHARED local_llama.cpp)
    target_link_libraries(local-llama PRIVATE harmonic-llama-core android log)
endif()

if(HARMONIC_BUILD_DESKTOP)
    add_library(harmonic-local-ai SHARED harmonic_llama_engine.cpp)
    target_compile_definitions(harmonic-local-ai PRIVATE HARMONIC_LLAMA_BUILD_SHARED=1)
    target_include_directories(harmonic-local-ai PRIVATE
            "${CMAKE_CURRENT_LIST_DIR}"
            "${LLAMA_CPP_SOURCE_DIR}/include"
            "${LLAMA_CPP_SOURCE_DIR}/ggml/include")
    target_link_libraries(harmonic-local-ai PRIVATE llama)
    set_target_properties(harmonic-local-ai PROPERTIES
            CXX_VISIBILITY_PRESET hidden
            VISIBILITY_INLINES_HIDDEN YES)
endif()
