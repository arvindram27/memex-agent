# MemexOS Testing Strategy & Guidelines

This document outlines the comprehensive testing approach for MemexOS, a voice-powered web browser with complex JNI integration.

## Overview

MemexOS uses a multi-layered testing approach designed to handle the unique challenges of:
- **JNI Integration**: Native Whisper.cpp integration for speech recognition
- **WebView Interactions**: Complex web browser functionality 
- **Audio Processing**: Real-time audio recording and processing
- **Memory Management**: Critical for JNI + WebView combinations

## Testing Architecture

### 1. Build Logic & Convention Plugins

All testing configuration is managed through reusable convention plugins:

- **`memexos.android.test`**: Unit and instrumentation test dependencies
- **`memexos.static.analysis`**: Code quality tools (Detekt, KtLint, Lint)
- **`memexos.performance.test`**: Benchmark and performance testing

### 2. Test Structure

```
app/src/
├── test/                    # Unit tests (JVM)
├── androidTest/            # Instrumentation tests (device/emulator)
└── testShared/             # Shared test utilities
    ├── java/
    │   └── com/memexos/app/
    │       ├── TestCoroutineRule.kt
    │       ├── AssetTestHelper.kt
    │       └── MockWebServerRule.kt
    └── resources/          # Test fixtures
```

## Testing Patterns

### JNI Testing Patterns

Testing JNI components requires special handling since native methods cannot be directly mocked:

#### WhisperService Testing
```kotlin
@RunWith(RobolectricTestRunner::class)
class WhisperServiceTest {
    @Test
    fun `transcribe - with valid audio data - returns transcribed text`() = testCoroutineRule.runTest {
        // Mock the private native methods using MockK's spyk
        every { whisperService["fullTranscribe"](mockContextPtr, 4, audioData) } just Runs
        every { whisperService["getTextSegmentCount"](mockContextPtr) } returns 2
        
        val result = whisperService.transcribe(audioData)
        assertThat(result).isEqualTo("Hello world")
    }
}
```

**Key Patterns:**
- Use `spyk()` to create partial mocks of classes with native methods
- Mock private methods using bracket notation: `whisperService["methodName"]`
- Test error handling for JNI failures (return values of 0, exceptions)
- Verify resource cleanup in `release()` methods

#### Memory Leak Prevention
```kotlin
@Test
fun `release - called multiple times - only frees once`() {
    whisperService.initializeFromAsset("models/ggml-tiny.bin")
    
    whisperService.release()
    whisperService.release()
    
    verify(exactly = 1) { whisperService["freeContext"](mockContextPtr) }
}
```

### Audio Recording Testing

Audio components require shadow classes from Robolectric:

```kotlin
@RunWith(RobolectricTestRunner::class)
class AudioRecorderTest {
    @Before
    fun setUp() {
        ShadowAudioRecord.setMinBufferSize(1024)
    }
    
    @Test
    fun `startRecording - success - creates recording thread`() = testCoroutineRule.runTest {
        audioRecorder.startRecording(outputFile, errorCallback)
        assertThat(audioRecorder.isRecording()).isTrue()
    }
}
```

### WebView Testing Patterns

WebView testing uses both unit tests (with mocks) and integration tests:

#### Unit Testing with Mocks
```kotlin
@Test
fun `processCommand - go to url - loads url in webview`() {
    val mockWebView = mockk<WebView>(relaxed = true)
    val processor = VoiceCommandProcessor(context, mockWebView)
    
    processor.processCommand("go to google.com")
    
    verify { mockWebView.loadUrl("https://google.com") }
}
```

#### Integration Testing with MockWebServer
```kotlin
@get:Rule
val mockWebServerRule = MockWebServerRule()

@Test
fun `webview - loads mock page successfully`() {
    mockWebServerRule.enqueue(
        MockWebServerRule.createHtmlResponse("Test Page", "Test Content")
    )
    
    val url = mockWebServerRule.url("/test")
    // Test WebView loading with this URL
}
```

### Coroutine Testing

All asynchronous code uses the TestCoroutineRule:

```kotlin
@get:Rule
val testCoroutineRule = TestCoroutineRule()

@Test
fun `async operation - completes successfully`() = testCoroutineRule.runTest {
    val result = someAsyncFunction()
    assertThat(result).isNotNull()
}
```

## Test Categories

### 1. Unit Tests (`app/src/test/`)

**WhisperServiceTest.kt**
- JNI context initialization and cleanup
- Audio transcription with various inputs
- Error handling for invalid models/audio
- Resource management verification

**AudioRecorderTest.kt** 
- Recording lifecycle management
- File creation and WAV encoding
- Permission and hardware error handling
- Concurrent recording scenarios

**VoiceCommandProcessorTest.kt**
- Command parsing and execution
- WebView interaction mocking
- Error handling for malformed commands

### 2. Integration Tests (`app/src/androidTest/`)

**MainActivityTest.kt**
- UI interaction testing with Espresso
- Permission request flows
- WebView loading and navigation
- Recording dialog interactions

**RecordingFlowTest.kt**
- End-to-end voice recording workflow
- Integration of AudioRecorder + WhisperService
- UI state management during recording

### 3. Performance Tests

**WhisperBenchmark.kt**
- Transcription performance measurement
- Memory usage during JNI operations
- Startup time optimization

## Memory Leak Detection

### LeakCanary Configuration

MemexOS uses custom LeakCanary configuration for JNI and WebView monitoring:

```kotlin
// In debug Application class
LeakCanaryConfig.initialize(this)

// Monitor WhisperService instances
LeakCanaryConfig.watchWhisperService(whisperService, "WhisperService instance")

// Monitor WebView when destroyed
LeakCanaryConfig.watchWebView(webView, "MainActivity WebView")
```

### Critical Leak Patterns to Watch

1. **JNI Context Leaks**: Ensure `freeContext()` is called for every `initContext()`
2. **WebView Leaks**: Properly clean up WebView references in Activity lifecycle
3. **Thread Leaks**: Verify recording threads are properly stopped and cleaned up
4. **File Handle Leaks**: Close all audio file streams and temporary files

## Static Analysis

### Detekt Rules

Custom rules focus on:
- JNI safety patterns
- Memory management verification
- Coroutine usage validation
- Android-specific best practices

### KtLint Configuration

- Android Kotlin style guide compliance
- 120-character line limit
- Import organization and wildcard prevention

### Lint Rules

Custom lint checks for:
- WebView security configurations
- JNI exception handling
- Resource leak detection

## Test Data & Fixtures

### Audio Test Data
```kotlin
// Generate test audio (sine wave)
val audioData = AssetTestHelper.createTestAudioData(
    durationSeconds = 1.0f, 
    frequency = 440.0f
)

// Create WAV file for testing
val wavContent = AssetTestHelper.createTestWavFile(audioData)
```

### Mock Assets
```kotlin
val assetManager = AssetTestHelper.createMockAssetManager(
    mapOf("models/ggml-tiny.bin" to AssetTestHelper.createMockWhisperModel())
)
```

## Running Tests

### All Tests
```bash
./gradlew test connectedAndroidTest
```

### Specific Test Categories
```bash
# Unit tests only
./gradlew testDebugUnitTest

# Instrumentation tests only  
./gradlew connectedDebugAndroidTest

# Static analysis
./gradlew qualityCheck

# Benchmarks
./gradlew connectedBenchmarkAndroidTest
```

### Coverage Reports
```bash
./gradlew testDebugUnitTestCoverage
```

## Debugging Test Issues

### Common Problems

1. **JNI Method Mocking Failures**
   - Ensure using `spyk()` instead of `mockk()`
   - Use bracket notation for private method mocking
   - Verify method signatures match exactly

2. **Robolectric Shadow Issues**
   - Check shadow class configuration
   - Verify Android version compatibility
   - Use `@Config(sdk = [Build.VERSION_CODES.P])` for specific versions

3. **Coroutine Test Failures**
   - Always use `testCoroutineRule.runTest { }`
   - Call `advanceUntilIdle()` for background operations
   - Use `UnconfinedTestDispatcher` for immediate execution

4. **WebView Test Issues**
   - Mock WebView interactions for unit tests
   - Use Espresso-Web for instrumentation tests
   - Configure WebView test mode in test setup

### Test Performance

- Keep unit tests fast (< 100ms each)
- Use `@LargeTest` annotation for long-running tests
- Mock expensive operations (file I/O, network, JNI)
- Prefer unit tests over integration tests when possible

## CI/CD Integration

Tests are integrated into the build pipeline with:
- Parallel test execution
- Fail-fast on critical issues
- Coverage reporting and thresholds
- Performance regression detection

## Best Practices

1. **Test Naming**: Use descriptive test names following the pattern:
   `methodName - given - expected behavior`

2. **Test Structure**: Follow Given-When-Then pattern
   ```kotlin
   @Test
   fun `transcribe - with invalid audio - returns null`() {
       // Given
       val invalidAudioData = FloatArray(0)
       
       // When  
       val result = whisperService.transcribe(invalidAudioData)
       
       // Then
       assertThat(result).isNull()
   }
   ```

3. **Resource Management**: Always clean up test resources
   ```kotlin
   @After
   fun tearDown() {
       tempFile.delete()
       whisperService.release()
   }
   ```

4. **Test Isolation**: Each test should be independent and repeatable

5. **Error Testing**: Test both success and failure scenarios

6. **Mock Verification**: Verify important interactions
   ```kotlin
   verify(exactly = 1) { mockWebView.loadUrl(any()) }
   ```

This testing strategy ensures MemexOS maintains high quality while handling the complexities of JNI integration and WebView management.
