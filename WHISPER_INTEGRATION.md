# Whisper.cpp Android Integration

This document describes the integration of Whisper.cpp native library into the MemexOS Android application.

## Architecture Overview

The integration consists of:
1. **Whisper.cpp** - Added as a git submodule under `app/src/main/cpp/whisper/`
2. **JNI Wrapper** - Native C++ code exposing Whisper functionality to Java
3. **Java Interface** - WhisperWrapper class providing easy-to-use API
4. **CMake Build** - Native library compilation configuration

## Components

### 1. Native Library Structure
```
app/src/main/cpp/
├── CMakeLists.txt         # Build configuration
├── whisper_jni.cpp        # JNI wrapper implementation
└── whisper/              # Whisper.cpp submodule
    ├── whisper.h
    ├── whisper.cpp
    └── ...
```

### 2. Java Integration
```
app/src/main/java/com/example/memexos/
├── WhisperWrapper.java    # Main API wrapper
├── ModelUtils.java        # Model file management
└── WhisperTestActivity.java # Sample usage
```

### 3. Model Storage
```
app/src/main/assets/models/
├── README.md              # Model download instructions
├── ggml-tiny.bin         # Tiny model (39 MB) - for testing
└── ggml-base.bin         # Base model (142 MB) - better accuracy
```

## Build Configuration

### CMakeLists.txt Features
- Builds Whisper.cpp as a static library
- Disables unnecessary features (tests, examples, SDL2)
- Optimizations for ARM64 and x86_64
- JNI wrapper compilation

### build.gradle.kts Configuration
```kotlin
android {
    ndkVersion = "25.2.9519653"
    
    defaultConfig {
        ndk {
            // PoC: Supporting only 64-bit architectures
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
        
        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17", "-O3", "-fexceptions")
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DWHISPER_BUILD_TESTS=OFF",
                    "-DWHISPER_BUILD_EXAMPLES=OFF"
                )
            }
        }
    }
    
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}
```

## JNI Functions

The native library exposes the following JNI functions:

### `nativeTranscribe(audioPath: String, modelPath: String): String`
Main transcription function that:
- Loads the Whisper model from the specified path
- Reads audio file (WAV format)
- Performs speech-to-text transcription
- Returns transcribed text

### `nativeInit()`
Initializes the Whisper native library (optional setup)

### `nativeCleanup()`
Cleans up resources (optional cleanup)

## Usage Example

```java
// 1. Copy model from assets to internal storage
String modelPath = ModelUtils.copyModelFromAssets(context, "models/ggml-tiny.bin");

// 2. Get WhisperWrapper instance
WhisperWrapper whisper = WhisperWrapper.getInstance();

// 3. Transcribe audio file
String transcription = whisper.transcribe(audioFilePath, modelPath);
```

## Setup Instructions

### 1. Clone Repository with Submodules
```bash
git clone --recursive [repository-url]
# OR if already cloned:
git submodule update --init --recursive
```

### 2. Download Whisper Model
Run the PowerShell script:
```powershell
.\download-whisper-model.ps1
```

Or manually download from:
- Tiny: https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin
- Base: https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin

Place in `app/src/main/assets/models/`

### 3. Build the Project
```bash
# Windows
gradlew.bat assembleDebug

# Linux/Mac
./gradlew assembleDebug
```

## Performance Considerations

### Model Selection
| Model | Size | Speed | Use Case |
|-------|------|-------|----------|
| Tiny | 39 MB | ~5x realtime | Quick testing, commands |
| Base | 142 MB | ~3x realtime | General transcription |
| Small | 466 MB | ~2x realtime | Better accuracy |

### Optimization Tips
1. Use appropriate thread count (default: 4)
2. Process audio in chunks for long recordings
3. Consider quantized models for smaller size
4. Cache loaded models between uses

## Limitations

1. **Audio Format**: Currently supports WAV format (16kHz, mono recommended)
2. **Memory**: Models are loaded entirely in memory
3. **Processing**: CPU-only processing (no GPU acceleration)
4. **Languages**: English by default, other languages require configuration

## Troubleshooting

### Build Issues
- Ensure NDK version 25.2.9519653 is installed
- Check CMake version 3.22.1 or higher
- Verify submodule is properly initialized

### Runtime Issues
- Check model file exists and is accessible
- Verify audio file format (WAV)
- Monitor memory usage for large models
- Check logcat for native library errors

### Model Loading
- Ensure sufficient storage space
- Verify model file integrity
- Check file permissions

## Future Enhancements

1. **Audio Processing**
   - Add real-time recording to WAV
   - Support more audio formats
   - Implement audio preprocessing

2. **Model Management**
   - Download models on-demand
   - Model selection UI
   - Automatic model updates

3. **Performance**
   - Add GPU acceleration support
   - Implement streaming transcription
   - Optimize for specific devices

4. **Features**
   - Multi-language support
   - Speaker diarization
   - Timestamp generation
   - Confidence scores

## References

- [Whisper.cpp GitHub](https://github.com/ggerganov/whisper.cpp)
- [Whisper Models](https://huggingface.co/ggerganov/whisper.cpp)
- [Android NDK Documentation](https://developer.android.com/ndk)
- [JNI Specification](https://docs.oracle.com/javase/8/docs/technotes/guides/jni/)
