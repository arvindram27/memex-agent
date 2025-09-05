# Memex Agent - AI-Powered Voice Notes Browser App

A powerful Android application that combines voice note transcription with web browsing capabilities, powered by OpenAI's Whisper model for accurate speech-to-text conversion. Memex Agent is an intelligent assistant that helps you capture and organize your thoughts through voice while seamlessly browsing the web.

## Features

- **Voice Recording & Transcription**: Record audio notes and transcribe them using Whisper AI
- **Integrated Web Browser**: Built-in WebView for seamless web browsing
- **Local Processing**: All transcription happens on-device for privacy
- **Modern UI**: Material Design 3 with intuitive navigation
- **Offline Support**: Works without internet connection for transcription

## Architecture

### Components

1. **MainActivity**: Entry point with bottom navigation
2. **VoiceNotesFragment**: Voice recording and transcription interface
3. **BrowserFragment**: WebView-based browser with JavaScript support
4. **WhisperService**: JNI wrapper for Whisper model integration
5. **AudioRecordService**: Handles audio recording and processing

### Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose & View Binding
- **AI Model**: OpenAI Whisper (ggml format)
- **Native Integration**: C++ with JNI
- **Build System**: Gradle with Kotlin DSL
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)

## Setup Instructions

### Prerequisites

- Android Studio Hedgehog or later
- NDK version 26.1.10909125
- CMake 3.22.1
- Git with submodule support
- PowerShell (for model download scripts)

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/yourusername/memex-agent.git
   cd memex-agent
   ```

2. **Initialize submodules**
   ```bash
   git submodule update --init --recursive
   ```

3. **Download Whisper model**
   ```powershell
   .\download-whisper-model.ps1
   ```
   This will download the `ggml-base.en.bin` model to `app/src/main/assets/`

4. **Open in Android Studio**
   - Open Android Studio
   - Select "Open an Existing Project"
   - Navigate to the Memex Agent directory
   - Let Gradle sync complete

5. **Build and Run**
   - Connect an Android device or start an emulator
   - Click "Run" in Android Studio

## Project Structure

```
MemexAgent/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/memexagent/
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── fragments/
│   │   │   │   │   ├── VoiceNotesFragment.kt
│   │   │   │   │   └── BrowserFragment.kt
│   │   │   │   ├── services/
│   │   │   │   │   ├── WhisperService.kt
│   │   │   │   │   └── AudioRecordService.kt
│   │   │   │   └── utils/
│   │   │   ├── cpp/
│   │   │   │   ├── CMakeLists.txt
│   │   │   │   ├── whisper_jni.cpp
│   │   │   │   └── whisper/ (submodule)
│   │   │   ├── res/
│   │   │   └── assets/
│   │   │       └── ggml-base.en.bin
│   │   └── build.gradle.kts
├── gradle/
├── .gitmodules
├── settings.gradle.kts
├── build.gradle.kts
└── README.md
```

## WebView Configuration

The browser component includes:
- JavaScript enabled for modern web apps
- DOM storage for local data persistence
- Mixed content compatibility mode
- Custom URL handling for in-app navigation
- Back button navigation support

## Permissions

The app requires the following permissions:
- `RECORD_AUDIO`: For voice recording
- `INTERNET`: For web browsing
- `WRITE_EXTERNAL_STORAGE`: For saving recordings (API < 29)

## Development

### Building from Source

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease
```

### Running Tests

```bash
# Unit tests
./gradlew test

# Instrumented tests
./gradlew connectedAndroidTest
```

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## Troubleshooting

### Common Issues

1. **Model not found**: Ensure you've run `download-whisper-model.ps1`
2. **NDK not found**: Install NDK through Android Studio SDK Manager
3. **Build fails**: Clean and rebuild (`./gradlew clean build`)
4. **Whisper crashes**: Check if the device has enough RAM (minimum 2GB recommended)

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgments

- OpenAI for the Whisper model
- whisper.cpp for the C++ implementation
- Android Jetpack libraries
- Material Design components

## Contact

For questions or support, please open an issue on GitHub.

---

**Note**: Memex Agent is an experimental AI assistant project that combines voice AI with web browsing capabilities. Performance may vary based on device capabilities.
