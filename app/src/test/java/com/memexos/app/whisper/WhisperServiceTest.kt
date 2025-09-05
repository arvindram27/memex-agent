package com.memexos.app.whisper

import android.content.Context
import android.content.res.AssetManager
import com.google.common.truth.Truth.assertThat
import com.memexos.app.AssetTestHelper
import com.memexos.app.TestCoroutineRule
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.FileNotFoundException

/**
 * Unit tests for WhisperService.
 * 
 * These tests verify:
 * - Initialization from assets and files
 * - Audio transcription functionality
 * - Resource management and cleanup
 * - Error handling and edge cases
 * - Concurrent access scenarios
 */
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class WhisperServiceTest {

    @get:Rule
    val testCoroutineRule = TestCoroutineRule()

    private lateinit var context: Context
    private lateinit var assetManager: AssetManager
    private lateinit var whisperService: WhisperService
    
    // Mock the JNI native methods
    private val mockContextPtr = 12345L
    
    @Before
    fun setUp() {
        // Mock Android context and AssetManager
        context = mockk(relaxed = true)
        assetManager = AssetTestHelper.createMockAssetManager(
            mapOf(
                "models/ggml-tiny.bin" to AssetTestHelper.createMockWhisperModel()
            )
        )
        every { context.assets } returns assetManager

        // Create WhisperService instance
        whisperService = spyk(WhisperService(context))

        // Mock all native methods
        mockNativeMethods()
    }

    private fun mockNativeMethods() {
        // Mock native method calls using spyk and every
        every { whisperService["initContext"](any<String>()) } returns mockContextPtr
        every { whisperService["initContextFromAsset"](any<AssetManager>(), any<String>()) } returns mockContextPtr
        every { whisperService["freeContext"](any<Long>()) } just Runs
        every { whisperService["fullTranscribe"](any<Long>(), any<Int>(), any<FloatArray>()) } just Runs
        every { whisperService["getTextSegmentCount"](any<Long>()) } returns 2
        every { whisperService["getTextSegment"](mockContextPtr, 0) } returns "Hello"
        every { whisperService["getTextSegment"](mockContextPtr, 1) } returns "world"
    }

    @After
    fun tearDown() {
        whisperService.release()
    }

    @Test
    fun `initializeFromAsset - success - returns true and sets initialized state`() = testCoroutineRule.runTest {
        // When
        val result = whisperService.initializeFromAsset("models/ggml-tiny.bin")

        // Then
        assertThat(result).isTrue()
        verify { whisperService["initContextFromAsset"](assetManager, "models/ggml-tiny.bin") }
    }

    @Test
    fun `initializeFromAsset - invalid asset - returns false`() = testCoroutineRule.runTest {
        // Given
        every { whisperService["initContextFromAsset"](any<AssetManager>(), any<String>()) } returns 0L

        // When
        val result = whisperService.initializeFromAsset("models/nonexistent.bin")

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `initializeFromAsset - exception during initialization - returns false`() = testCoroutineRule.runTest {
        // Given
        every { whisperService["initContextFromAsset"](any<AssetManager>(), any<String>()) } throws RuntimeException("JNI error")

        // When
        val result = whisperService.initializeFromAsset("models/ggml-tiny.bin")

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `initializeFromFile - success - returns true`() = testCoroutineRule.runTest {
        // Given
        val modelPath = "/path/to/model.bin"

        // When
        val result = whisperService.initializeFromFile(modelPath)

        // Then
        assertThat(result).isTrue()
        verify { whisperService["initContext"](modelPath) }
    }

    @Test
    fun `initializeFromFile - invalid file - returns false`() = testCoroutineRule.runTest {
        // Given
        every { whisperService["initContext"](any<String>()) } returns 0L

        // When
        val result = whisperService.initializeFromFile("/path/to/nonexistent.bin")

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `transcribe - with valid audio data - returns transcribed text`() = testCoroutineRule.runTest {
        // Given
        whisperService.initializeFromAsset("models/ggml-tiny.bin")
        val audioData = AssetTestHelper.createTestAudioData(1.0f, 440.0f)

        // When
        val result = whisperService.transcribe(audioData)

        // Then
        assertThat(result).isEqualTo("Hello world")
        verify { whisperService["fullTranscribe"](mockContextPtr, 4, audioData) }
        verify { whisperService["getTextSegmentCount"](mockContextPtr) }
        verify { whisperService["getTextSegment"](mockContextPtr, 0) }
        verify { whisperService["getTextSegment"](mockContextPtr, 1) }
    }

    @Test
    fun `transcribe - not initialized - returns null`() = testCoroutineRule.runTest {
        // Given
        val audioData = AssetTestHelper.createTestAudioData()

        // When
        val result = whisperService.transcribe(audioData)

        // Then
        assertThat(result).isNull()
        verify(exactly = 0) { whisperService["fullTranscribe"](any<Long>(), any<Int>(), any<FloatArray>()) }
    }

    @Test
    fun `transcribe - exception during transcription - returns null`() = testCoroutineRule.runTest {
        // Given
        whisperService.initializeFromAsset("models/ggml-tiny.bin")
        val audioData = AssetTestHelper.createTestAudioData()
        every { whisperService["fullTranscribe"](any<Long>(), any<Int>(), any<FloatArray>()) } throws RuntimeException("Transcription error")

        // When
        val result = whisperService.transcribe(audioData)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `transcribe - no text segments - returns empty string`() = testCoroutineRule.runTest {
        // Given
        whisperService.initializeFromAsset("models/ggml-tiny.bin")
        val audioData = AssetTestHelper.createTestAudioData()
        every { whisperService["getTextSegmentCount"](mockContextPtr) } returns 0

        // When
        val result = whisperService.transcribe(audioData)

        // Then
        assertThat(result).isEmpty()
    }

    @Test
    fun `transcribeFile - valid WAV file - returns transcribed text`() = testCoroutineRule.runTest {
        // Given
        whisperService.initializeFromAsset("models/ggml-tiny.bin")
        val audioData = AssetTestHelper.createTestAudioData()
        val wavContent = AssetTestHelper.createTestWavFile(audioData)
        val tempFile = File.createTempFile("test_audio", ".wav").apply {
            writeBytes(wavContent)
        }

        // Mock WaveFileEncoder.decodeWaveFile
        mockkObject(com.memexos.app.audio.WaveFileEncoder)
        every { com.memexos.app.audio.WaveFileEncoder.decodeWaveFile(tempFile) } returns audioData

        // When
        val result = whisperService.transcribeFile(tempFile)

        // Then
        assertThat(result).isEqualTo("Hello world")
        
        // Cleanup
        tempFile.delete()
        unmockkObject(com.memexos.app.audio.WaveFileEncoder)
    }

    @Test
    fun `transcribeFile - file does not exist - returns null`() = testCoroutineRule.runTest {
        // Given
        whisperService.initializeFromAsset("models/ggml-tiny.bin")
        val nonExistentFile = File("/path/to/nonexistent.wav")

        // When
        val result = whisperService.transcribeFile(nonExistentFile)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `transcribeFile - error reading file - returns null`() = testCoroutineRule.runTest {
        // Given
        whisperService.initializeFromAsset("models/ggml-tiny.bin")
        val tempFile = File.createTempFile("test_audio", ".wav")
        
        mockkObject(com.memexos.app.audio.WaveFileEncoder)
        every { com.memexos.app.audio.WaveFileEncoder.decodeWaveFile(tempFile) } throws FileNotFoundException("File read error")

        // When
        val result = whisperService.transcribeFile(tempFile)

        // Then
        assertThat(result).isNull()
        
        // Cleanup
        tempFile.delete()
        unmockkObject(com.memexos.app.audio.WaveFileEncoder)
    }

    @Test
    fun `release - initialized context - frees native context`() {
        // Given
        runTest { whisperService.initializeFromAsset("models/ggml-tiny.bin") }

        // When
        whisperService.release()

        // Then
        verify { whisperService["freeContext"](mockContextPtr) }
    }

    @Test
    fun `release - not initialized - does not crash`() {
        // When/Then (should not throw)
        whisperService.release()
        
        // Verify freeContext was not called
        verify(exactly = 0) { whisperService["freeContext"](any<Long>()) }
    }

    @Test
    fun `release - called multiple times - only frees once`() {
        // Given
        runTest { whisperService.initializeFromAsset("models/ggml-tiny.bin") }

        // When
        whisperService.release()
        whisperService.release()

        // Then
        verify(exactly = 1) { whisperService["freeContext"](mockContextPtr) }
    }

    @Test
    fun `initialize - already initialized - releases previous context first`() = testCoroutineRule.runTest {
        // Given
        whisperService.initializeFromAsset("models/ggml-tiny.bin")

        // When
        whisperService.initializeFromAsset("models/ggml-tiny.bin")

        // Then
        verify(exactly = 1) { whisperService["freeContext"](mockContextPtr) }
        verify(exactly = 2) { whisperService["initContextFromAsset"](assetManager, "models/ggml-tiny.bin") }
    }

    @Test
    fun `concurrent transcription - multiple calls - handles correctly`() = testCoroutineRule.runTest {
        // Given
        whisperService.initializeFromAsset("models/ggml-tiny.bin")
        val audioData1 = AssetTestHelper.createTestAudioData(1.0f, 440.0f)
        val audioData2 = AssetTestHelper.createTestAudioData(1.0f, 880.0f)

        // When
        val result1 = whisperService.transcribe(audioData1)
        val result2 = whisperService.transcribe(audioData2)

        // Then
        assertThat(result1).isEqualTo("Hello world")
        assertThat(result2).isEqualTo("Hello world")
        verify(exactly = 2) { whisperService["fullTranscribe"](mockContextPtr, 4, any<FloatArray>()) }
    }
}
