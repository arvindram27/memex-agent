# CMakeLists.txt for Whisper.cpp Android integration
cmake_minimum_required(VERSION 3.22.1)
project("memexos")

# Set C++ standard
set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Configure Whisper.cpp build options
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "whisper: build tests" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "whisper: build examples" FORCE)
set(WHISPER_SUPPORT_SDL2 OFF CACHE BOOL "whisper: support SDL2" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "whisper: build shared libs" FORCE)
set(WHISPER_NO_ACCELERATE ON CACHE BOOL "whisper: disable Accelerate" FORCE)
set(WHISPER_NO_AVX ON CACHE BOOL "whisper: disable AVX" FORCE)
set(WHISPER_NO_AVX2 ON CACHE BOOL "whisper: disable AVX2" FORCE)
set(WHISPER_NO_FMA ON CACHE BOOL "whisper: disable FMA" FORCE)
set(WHISPER_NO_F16C ON CACHE BOOL "whisper: disable F16C" FORCE)

# Add whisper subdirectory
add_subdirectory(whisper)

# Find required libraries
find_library(log-lib log)
find_library(android-lib android)

# Create JNI wrapper library
add_library(memexos_native SHARED
    whisper_jni.cpp)

# Link libraries
target_link_libraries(memexos_native
    whisper
    ${log-lib}
    ${android-lib})

# Include directories
target_include_directories(memexos_native PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/whisper
    ${CMAKE_CURRENT_SOURCE_DIR}/whisper/include)

# Compiler flags for optimization
if(${CMAKE_BUILD_TYPE} MATCHES "Release")
    target_compile_options(memexos_native PRIVATE
        -O3
        -ffast-math
        -funroll-loops)
endif()

# Platform-specific optimizations
if(${ANDROID_ABI} STREQUAL "arm64-v8a")
    target_compile_options(memexos_native PRIVATE -march=armv8-a)
elseif(${ANDROID_ABI} STREQUAL "x86_64")
    target_compile_options(memexos_native PRIVATE
        -march=x86-64
        -msse4.2)
endif()
