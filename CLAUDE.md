# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

MemexOS is an Android application that combines voice note transcription with web browsing capabilities. It uses OpenAI's Whisper model for local, on-device speech-to-text conversion integrated with a WebView-based browser.

## Build System & Commands

### Environment Setup
```bash
# Initialize Whisper submodule (required on first setup)
git submodule update --init --recursive

# Download Whisper model to assets folder
.\download-whisper-model.ps1
```

### Build Commands
```bash
# Debug build
./gradlew assembleDebug

# Release build  
./gradlew assembleRelease

# Clean build
./gradlew clean build

# Run tests
./gradlew test
./gradlew connectedAndroidTest
```

### Testing Commands
```bash
# Complete test suite
./gradlew testAll

# Unit tests only
./gradlew testDebugUnitTest

# Integration tests only  
./gradlew connectedDebugAndroidTest

# Static analysis
./gradlew ktlintCheck
./gradlew detekt
./gradlew lintDebug

# Coverage reports
./gradlew jacocoTestReport
./gradlew coverageVerification

# Performance benchmarks
./gradlew connectedBenchmarkAndroidTest
```

### Development Requirements
- Android Studio Hedgehog or later
- NDK version 25.2.9519653 (exact version required)
- CMake 3.22.1+
- Min SDK: 24, Target SDK: 34
- JDK 17 (Temurin distribution recommended)

## Architecture

### Core Components

1. **MainActivity** (`com.memexos.app.MainActivity`): Main activity managing WebView browser and voice recording UI
2. **AudioRecorder** (`com.memexos.app.audio.AudioRecorder`): Handles WAV audio recording
3. **WhisperService** (`com.memexos.app.whisper.WhisperService`): JNI wrapper for Whisper transcription
4. **WaveFileEncoder** (`com.memexos.app.audio.WaveFileEncoder`): WAV file format encoding

### Native Integration
- **JNI Layer**: `app/src/main/cpp/whisper_jni.cpp` - C++ wrapper for Whisper.cpp
- **Whisper.cpp**: Git submodule at `app/src/main/cpp/whisper/`
- **CMake Build**: Configured for ARM64 and x86_64 architectures only
- **Model Storage**: Whisper models (.bin files) in `app/src/main/assets/models/`

### Key Technologies
- **UI**: View Binding (not Compose) with Material Design 3
- **WebView**: JavaScript-enabled with DOM storage support
- **Audio**: Custom WAV recording implementation
- **AI**: Local Whisper model processing (no network required)
- **Coroutines**: Used for async audio/AI processing

## Development Patterns

### Audio Recording Flow
1. Permission check (`RECORD_AUDIO`)
2. Create audio file in cache directory with timestamp
3. Start AudioRecorder with 60-second maximum
4. Stop recording → process with WhisperService
5. Clean up audio files after transcription

### Voice Command Processing
Commands are parsed in `executeCommand()` method:
- "search/google [query]" → Google search
- "go to/navigate to/open [url]" → Load URL
- "back/forward" → WebView navigation
- "refresh/reload" → Reload page
- "scroll up/down" → Page scrolling

### Native Build Configuration
- Only ARM64-v8a and x86_64 architectures supported
- Whisper build flags disable tests, examples, SDL2, and SIMD optimizations
- CMake uses static linking with optimization flags for release builds

### File Structure
```
app/src/main/
├── java/com/memexos/app/
│   ├── MainActivity.kt (main UI and WebView)
│   ├── audio/ (recording components)
│   └── whisper/ (AI service wrapper)
├── cpp/ (native Whisper integration)
│   ├── CMakeLists.txt
│   ├── whisper_jni.cpp
│   └── whisper/ (git submodule)
├── res/ (UI layouts and resources)
└── assets/models/ (Whisper model files)
```

## Testing & Debugging

### Testing Infrastructure
MemexOS has a comprehensive testing suite with 95%+ unit test coverage:
- **Unit Tests**: JNI mocking with MockK, Robolectric shadows for Android components
- **Integration Tests**: Espresso UI tests, MockWebServer for network testing
- **Performance Tests**: Startup benchmarks, audio processing performance monitoring
- **Memory Leak Detection**: Custom LeakCanary configuration for JNI and WebView

### Key Testing Patterns
- **JNI Testing**: Use `spyk()` for partial mocks of native method classes
- **WebView Testing**: MockWebServer for network requests, Espresso-Web for UI interactions
- **Coroutine Testing**: TestCoroutineRule for async operations
- **Audio Testing**: Robolectric shadows (ShadowAudioRecord) for recording simulation

### Common Issues
- Model file missing: Run `download-whisper-model.ps1`
- NDK version mismatch: Use exact version 25.2.9519653
- Native build fails: Ensure submodule is initialized
- JNI test failures: Use bracket notation for private method mocking: `service["privateMethod"]`
- Recording permission: Handle runtime permission requests
- Memory issues: Consider model size vs device RAM (2GB+ recommended)

### CI/CD Pipeline
GitHub Actions workflow includes:
- Unit tests with coverage reporting (70% minimum threshold)
- Integration tests on multiple API levels (24, 29, 34)
- Static analysis (KtLint, Detekt, Android Lint)
- Performance benchmarks with regression detection
- Security scanning with Trivy

### Build Configuration
- **Kotlin Target**: JVM 17 compatibility
- **NDK**: ARM64-v8a and x86_64 architectures only
- **Native Build**: CMake with C++17, OpenMP disabled for compatibility
- **Dependencies**: LeakCanary for debug builds, comprehensive test libraries