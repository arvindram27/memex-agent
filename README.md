# Memex Agent - Voice-Controlled Browser AI Assistant

A revolutionary Android application that transforms web browsing through natural voice commands. Memex Agent combines advanced AI technologies including OpenAI's Whisper, ML Kit OCR, screen context awareness, and intelligent command processing to create the first truly voice-controlled browser agent.

## 🚀 Key Features

### **Voice-Controlled Web Browsing**
- **Natural Language Commands**: "Click the blue button", "Fill email with my@email.com", "Search for weather"
- **Context-Aware Actions**: Understands page types (search, e-commerce, forms) and adapts responses
- **Smart Element Detection**: Finds elements by text, color, position, and semantic meaning
- **Proactive Assistance**: Suggests relevant actions based on current page context

### **Advanced AI Integration**
- **Whisper AI Transcription**: On-device speech-to-text with high accuracy
- **ML Kit OCR**: Visual text recognition from screen content
- **Contextual AI Engine**: Resolves ambiguous commands and learns user patterns
- **Screen Context Awareness**: Real-time screen capture and analysis

### **Intelligent Browser Automation**
- **Form Intelligence**: Auto-detects and fills form fields
- **Content Extraction**: Extracts prices, emails, phone numbers, and specific text
- **Navigation Control**: Voice-controlled browsing with history management
- **Visual Feedback**: Highlights elements and provides smooth animations

## 🎯 Voice Commands

### **Navigation & Control**
- `"Go to google.com"` - Smart URL navigation
- `"Refresh page"` / `"Go back"` - Browser navigation
- `"Scroll down"` / `"Scroll to top"` - Smooth page scrolling

### **Smart Interaction**
- `"Click the blue button"` - Color-based element targeting
- `"Click first result"` - Positional element selection
- `"Tap the login link"` - Text-based element matching

### **Form Intelligence**
- `"Fill email with test@email.com"` - Smart field detection
- `"Fill password"` - Secure input handling
- `"Submit form"` - Automatic form submission

### **Content Operations**
- `"Read this article"` - Content extraction and narration
- `"Extract all prices"` - Smart data extraction
- `"Find text 'contact us'"` - Page search with highlighting
- `"Summarize page"` - Content analysis

### **Context-Aware Actions**
- `"Add to cart"` - E-commerce page detection
- `"Search for weather"` - Search engine integration
- `"Login"` - Form page recognition

## 🏗 Architecture

### Core Components

1. **VoiceAgentCoordinator**: Central orchestration hub for all voice processing
2. **ScreenContextManager**: Real-time screen capture and context awareness
3. **VisualContextProcessor**: ML Kit OCR and web page element analysis
4. **VoiceIntentProcessor**: Advanced voice command understanding with 15+ intents
5. **BrowserActionController**: JavaScript-powered WebView automation engine
6. **ContextualAI**: Intelligent command resolution and page understanding
7. **WhisperService**: On-device speech-to-text transcription
8. **MainActivity**: Modern UI with voice controls integration

### Tech Stack

- **Language**: Kotlin with Coroutines
- **AI/ML**: OpenAI Whisper (ggml), Google ML Kit OCR
- **UI Framework**: Jetpack Compose & View Binding
- **Web Integration**: WebView with JavaScript injection
- **Screen Capture**: MediaProjection API
- **Native Integration**: C++ with JNI for Whisper
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
   git clone https://github.com/arvindram27/memex-agent.git
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
│   │   │   │   ├── VoiceAgentCoordinator.kt          # 🧠 Central AI coordinator
│   │   │   │   ├── actions/
│   │   │   │   │   └── BrowserActionController.kt   # 🎯 WebView automation
│   │   │   │   ├── ai/
│   │   │   │   │   └── ContextualAI.kt              # 🤖 Smart command resolution
│   │   │   │   ├── context/
│   │   │   │   │   ├── ScreenContextManager.kt     # 📱 Screen capture
│   │   │   │   │   └── VisualContextProcessor.kt   # 👁 OCR & page analysis
│   │   │   │   ├── voice/
│   │   │   │   │   └── VoiceIntentProcessor.kt      # 🗣 Voice command parsing
│   │   │   │   ├── whisper/
│   │   │   │   │   └── WhisperService.kt           # 🎙 Speech-to-text
│   │   │   │   └── audio/
│   │   │   │       └── AudioRecorder.kt
│   │   │   ├── cpp/
│   │   │   │   ├── CMakeLists.txt
│   │   │   │   ├── whisper_jni.cpp
│   │   │   │   └── whisper/ (submodule)
│   │   │   ├── res/
│   │   │   └── assets/
│   │   │       └── models/
│   │   │           └── ggml-base.en.bin
│   │   └── build.gradle.kts
├── gradle/
├── .gitmodules
├── settings.gradle.kts
├── build.gradle.kts
└── README.md
```

## 🧠 AI Intelligence Features

### **Contextual Understanding**
- **Page Type Detection**: Automatically identifies search engines, e-commerce sites, forms, news articles
- **User Intent Inference**: Understands whether you want to browse, shop, read, or interact
- **Command Disambiguation**: Resolves unclear voice commands using page context

### **Learning & Adaptation**
- **Behavior Pattern Recognition**: Learns from successful interactions
- **Proactive Suggestions**: "Try: click the first result", "Try: add to cart"
- **Usage Analytics**: Tracks success rates and optimizes performance

### **Visual Intelligence**
- **Advanced Element Finding**: 5 strategies - text, color, position, attributes, semantic matching
- **Visual Feedback**: Highlights clicked elements and found text with animations
- **Smart Form Detection**: Automatically identifies email, password, and search fields

## 📋 Permissions

The app requires the following permissions:
- `RECORD_AUDIO`: For voice command input
- `INTERNET`: For web browsing functionality  
- `SYSTEM_ALERT_WINDOW`: For overlay UI elements
- `FOREGROUND_SERVICE`: For background voice processing
- `WAKE_LOCK`: For continuous operation
- `MEDIA_PROJECTION`: For screen capture and context awareness

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

## 🚀 Competitive Advantages

Memex Agent surpasses existing voice assistants and browser automation tools:

### **vs. Perplexity Android Voice Assistant**
- ✅ Deep browser integration (not just app-level)
- ✅ Context-aware command processing  
- ✅ Visual element recognition and interaction

### **vs. Voice Access**
- ✅ Natural language understanding (not just basic commands)
- ✅ Page-specific intelligence and adaptation
- ✅ Proactive assistance and suggestions

### **vs. Browser Extensions**
- ✅ On-device privacy (no cloud dependency)
- ✅ Screen context awareness with OCR
- ✅ Cross-page learning and behavior adaptation
- ✅ Mobile-first design with touch integration

### **Unique Features**
- 🤖 **Multi-modal AI**: Combines speech, vision, and contextual understanding
- 🎯 **Smart Targeting**: 5-strategy element finding system
- 📊 **Learning Engine**: Adapts to user patterns and preferences
- 🔒 **Privacy-First**: All processing happens on-device

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

**Note**: Memex Agent represents the next evolution of web browsing - a truly intelligent, voice-controlled browser agent that understands context, learns from interactions, and proactively assists users. This is the first implementation of its kind, combining cutting-edge AI technologies for a revolutionary browsing experience.

---

🎆 **Ready to transform how you browse the web with voice commands!** 🎆
