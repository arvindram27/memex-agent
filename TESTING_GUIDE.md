# 🧪 Memex Agent Testing Guide

## 📱 **APK Ready for Testing!**

✅ **APK Built Successfully**: `app-debug.apk` (109.6 MB)  
📍 **Location**: `app\build\outputs\apk\debug\app-debug.apk`  
🕐 **Build Time**: 2025-09-07 22:35:13

## 🚀 **Installation Instructions**

### **Option 1: Android Device (Recommended)**
1. **Connect Android device** to your computer via USB
2. **Enable Developer Options & USB Debugging**:
   - Go to `Settings > About Phone`
   - Tap "Build Number" 7 times to enable Developer Options
   - Go to `Settings > Developer Options`
   - Enable "USB Debugging"
3. **Install APK**:
   ```bash
   adb install app\build\outputs\apk\debug\app-debug.apk
   ```
4. **Grant Permissions** when prompted:
   - Microphone access
   - Storage access

### **Option 2: Android Emulator**
1. **Start Android Studio AVD Manager**
2. **Create/Start emulator** (API 24+ recommended, 4GB+ RAM)
3. **Install APK**:
   ```bash
   adb install app\build\outputs\apk\debug\app-debug.apk
   ```

### **Option 3: Manual Installation**
1. **Transfer APK** to Android device (via USB/cloud)
2. **Enable "Install from Unknown Sources"** in Android settings
3. **Open APK file** on device and install

## 🧪 **Testing Components**

### **1. App Launch Test** ✅
**What happens**: 
- App launches with WebView browser interface
- Voice Agent components are automatically tested
- You'll see a toast notification: "✅ Voice Agent components loaded successfully!"

**What to check**:
- App doesn't crash on startup
- WebView loads Google homepage
- Toast shows successful component loading
- FAB (record button) is visible

### **2. Basic Browser Test** ✅
**Test Steps**:
1. **URL Navigation**: Type `github.com` in address bar, press Enter
2. **WebView Functionality**: Verify page loads correctly
3. **JavaScript**: Check if interactive elements work on web pages

**Expected Results**:
- Pages load smoothly
- JavaScript works (buttons, forms, etc.)
- Address bar updates with current URL

### **3. Voice Recording Test** ⚠️
**Test Steps**:
1. **Tap the microphone FAB** (floating action button)
2. **Grant microphone permission** if requested
3. **Speak a command**: "Click the first link" or "Scroll down"
4. **Recording should show** animated pulse effect

**Expected Results**:
- Recording dialog appears with animation
- Audio is captured (progress indicates duration)
- Recording stops after timeout or manual stop

### **4. Voice Processing Test** ⚠️ 
**Current Status**: 
- Whisper model integration is present but may need model file
- Mock transcription will return "test transcription"

**Test Steps**:
1. **Complete voice recording** (Step 3)
2. **Wait for processing** - progress overlay shows status
3. **Check for transcription result**

**Expected Results**:
- Processing overlay shows "Transcribing audio..."
- Either real transcription or "test transcription" appears
- Dialog shows recognized command

### **5. Voice Command Recognition Test** ✅
**Available Commands to Test**:

**Navigation Commands**:
- "Go to google.com"
- "Refresh page"
- "Go back"

**Interaction Commands**:
- "Click the blue button"
- "Click first result" 
- "Scroll down"

**Form Commands**:
- "Fill email with test@example.com"
- "Submit form"

**Content Commands**:
- "Read this page"
- "Find text contact"
- "Extract prices"

**Test Steps**:
1. **Use voice recording** or **manually call** `processVoiceCommand("click the blue button")`
2. **Check logcat** for processing details:
   ```bash
   adb logcat -s VoiceIntentProcessor:* BrowserActionController:* ContextualAI:*
   ```

**Expected Results**:
- Commands are parsed into intents and entities
- Browser actions are attempted via JavaScript injection
- Feedback messages indicate success/failure

## 📊 **Component Status**

| Component | Status | Testing Level |
|-----------|--------|---------------|
| **VoiceIntentProcessor** | ✅ Ready | Full command parsing |
| **VisualContextProcessor** | ✅ Ready | Basic page analysis |
| **BrowserActionController** | ✅ Ready | JavaScript automation |
| **ContextualAI** | ✅ Ready | Page type detection |
| **ScreenContextManager** | ⚠️ Needs Permission | Screen capture functionality |
| **WhisperService** | ⚠️ Model Dependent | Speech-to-text transcription |

## 🔧 **Advanced Testing (Optional)**

### **Manual Component Testing**
Add to MainActivity's `onCreate()`:
```kotlin
// Test individual components
val voiceTest = VoiceAgentTest()
voiceTest.runAllTests() // Check logcat for results
```

### **Enable Debug Logging**
```bash
# View all Memex Agent logs
adb logcat -s MainActivity:* VoiceAgentCoordinator:* VoiceIntentProcessor:* BrowserActionController:*

# View specific component logs
adb logcat -s ContextualAI:*
```

### **Test Voice Commands Programmatically**
In MainActivity, add test buttons or modify `executeCommand()`:
```kotlin
private fun testVoiceCommands() {
    val testCommands = listOf(
        "click the blue button",
        "scroll down", 
        "fill email with test@example.com",
        "search for android development"
    )
    
    testCommands.forEach { command ->
        executeCommand(command)
        // Check results in logcat
    }
}
```

## 🚨 **Troubleshooting**

### **Common Issues**

1. **App Crashes on Startup**
   - Check logcat: `adb logcat -s AndroidRuntime:E`
   - Likely issue: Missing permissions or component initialization

2. **"Voice Agent component test failed"**
   - Check individual component errors in logcat
   - May indicate missing dependencies or incompatible device

3. **Whisper Model Missing**
   - Dialog will appear: "The Whisper AI model is missing"
   - For now, voice commands use mock transcription
   - Full Whisper integration requires model file download

4. **Screen Capture Permission Denied**
   - ScreenContextManager will show permission request
   - Visual context features may be limited without this

5. **Voice Recording Not Working**
   - Check microphone permissions in Android settings
   - Try different audio input methods

### **Debugging Commands**
```bash
# Check app installation
adb shell pm list packages | grep memexagent

# Check app permissions
adb shell dumpsys package com.memexagent.app | grep permission

# Clear app data (if needed)
adb shell pm clear com.memexagent.app

# Reinstall APK
adb uninstall com.memexagent.app
adb install app\build\outputs\apk\debug\app-debug.apk
```

## 🎯 **Success Criteria**

**Minimum Success** (Basic functionality):
- ✅ App launches without crashing
- ✅ WebView loads web pages
- ✅ Voice Agent components initialize
- ✅ Basic voice command recognition works

**Full Success** (Complete functionality):
- ✅ All above +
- ✅ Voice recording captures audio
- ✅ Speech-to-text transcription works
- ✅ Browser automation executes commands
- ✅ Screen context awareness functions
- ✅ Contextual AI provides smart suggestions

## 📈 **What's Next**

After successful testing:
1. **Voice Model Integration**: Add full Whisper model support
2. **UI Polish**: Improve voice command feedback and visual indicators
3. **Advanced Features**: Implement proactive suggestions and learning
4. **Performance Optimization**: Optimize for different device types

---

🎆 **Your Memex Agent is ready for testing - the first truly voice-controlled browser agent!** 🎆
