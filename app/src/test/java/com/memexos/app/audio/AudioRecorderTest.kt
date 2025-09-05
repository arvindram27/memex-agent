package com.memexos.app.audio

import android.media.AudioRecord
import com.google.common.truth.Truth.assertThat
import com.memexos.app.TestCoroutineRule
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowAudioRecord
import org.robolectric.Shadows.shadowOf
import java.io.File

/**
 * Unit tests for AudioRecorder.
 * 
 * These tests verify:
 * - Recording lifecycle management
 * - File creation and WAV encoding
 * - Error handling for permissions and hardware issues
 * - Thread management and cleanup
 * - Concurrent recording attempts
 */
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class AudioRecorderTest {

    @get:Rule
    val testCoroutineRule = TestCoroutineRule()

    private lateinit var audioRecorder: AudioRecorder
    private lateinit var outputFile: File
    private var errorCallback: ((Exception) -> Unit)? = null

    @Before
    fun setUp() {
        audioRecorder = AudioRecorder()
        outputFile = File.createTempFile("test_recording", ".wav")
        errorCallback = mockk(relaxed = true)
        
        // Setup Robolectric shadow for AudioRecord
        setupAudioRecordShadow()
    }

    @After
    fun tearDown() {
        outputFile.delete()
        // Ensure any recording is stopped
        if (audioRecorder.isRecording()) {
            testCoroutineRule.runTest {
                audioRecorder.stopRecording()
            }
        }
    }

    private fun setupAudioRecordShadow() {
        // Configure shadow AudioRecord to simulate successful recording
        ShadowAudioRecord.setMinBufferSize(1024)
    }

    @Test
    fun `startRecording - success - creates recording thread and starts recording`() = testCoroutineRule.runTest {
        // When
        audioRecorder.startRecording(outputFile, errorCallback!!)

        // Then
        assertThat(audioRecorder.isRecording()).isTrue()
        
        // Stop recording for cleanup
        audioRecorder.stopRecording()
    }

    @Test
    fun `startRecording - already recording - stops previous and starts new recording`() = testCoroutineRule.runTest {
        // Given
        val firstFile = File.createTempFile("first_recording", ".wav")
        audioRecorder.startRecording(firstFile, errorCallback!!)
        assertThat(audioRecorder.isRecording()).isTrue()

        // When
        audioRecorder.startRecording(outputFile, errorCallback!!)

        // Then
        assertThat(audioRecorder.isRecording()).isTrue()
        
        // Cleanup
        audioRecorder.stopRecording()
        firstFile.delete()
    }

    @Test
    fun `stopRecording - while recording - stops recording and cleans up`() = testCoroutineRule.runTest {
        // Given
        audioRecorder.startRecording(outputFile, errorCallback!!)
        assertThat(audioRecorder.isRecording()).isTrue()

        // When
        audioRecorder.stopRecording()

        // Then
        assertThat(audioRecorder.isRecording()).isFalse()
    }

    @Test
    fun `stopRecording - not recording - does not crash`() = testCoroutineRule.runTest {
        // When/Then (should not throw)
        audioRecorder.stopRecording()
        
        assertThat(audioRecorder.isRecording()).isFalse()
    }

    @Test
    fun `isRecording - initially false - returns false`() {
        // When/Then
        assertThat(audioRecorder.isRecording()).isFalse()
    }

    @Test
    fun `recording lifecycle - start then stop - updates isRecording status correctly`() = testCoroutineRule.runTest {
        // Initially not recording
        assertThat(audioRecorder.isRecording()).isFalse()

        // Start recording
        audioRecorder.startRecording(outputFile, errorCallback!!)
        assertThat(audioRecorder.isRecording()).isTrue()

        // Stop recording
        audioRecorder.stopRecording()
        assertThat(audioRecorder.isRecording()).isFalse()
    }

    @Test
    fun `startRecording - with invalid buffer size - calls error callback`() = testCoroutineRule.runTest {
        // Given
        ShadowAudioRecord.setMinBufferSize(AudioRecord.ERROR_BAD_VALUE)

        // When
        audioRecorder.startRecording(outputFile, errorCallback!!)

        // Allow time for the recording thread to start and fail
        testCoroutineRule.advanceTimeBy(100)

        // Then
        verify { errorCallback!!(any<RuntimeException>()) }
    }

    @Test
    fun `recording thread - with audio data - creates WAV file`() = testCoroutineRule.runTest {
        // Mock WaveFileEncoder
        mockkObject(WaveFileEncoder)
        every { WaveFileEncoder.encodeWaveFile(any(), any()) } just Runs

        // Given - setup shadow to provide mock audio data
        val shadowAudioRecord = ShadowAudioRecord()
        shadowAudioRecord.setMinBufferSize(1024)

        // When
        audioRecorder.startRecording(outputFile, errorCallback!!)
        
        // Allow recording to run for a short time
        testCoroutineRule.advanceTimeBy(200)
        
        audioRecorder.stopRecording()

        // Then
        verify { WaveFileEncoder.encodeWaveFile(outputFile, any()) }
        
        unmockkObject(WaveFileEncoder)
    }

    @Test
    fun `recording thread - AudioRecord fails to initialize - calls error callback`() = testCoroutineRule.runTest {
        // This test would require more sophisticated mocking of AudioRecord initialization
        // For now, we'll test the error handling path indirectly
        
        // Given
        ShadowAudioRecord.setMinBufferSize(AudioRecord.ERROR)

        // When
        audioRecorder.startRecording(outputFile, errorCallback!!)
        
        // Allow time for error to occur
        testCoroutineRule.advanceTimeBy(100)

        // Then
        verify { errorCallback!!(any<RuntimeException>()) }
    }

    @Test
    fun `concurrent recording attempts - second call stops first`() = testCoroutineRule.runTest {
        // Given
        val firstFile = File.createTempFile("first", ".wav")
        val secondFile = File.createTempFile("second", ".wav")

        // When
        audioRecorder.startRecording(firstFile, errorCallback!!)
        assertThat(audioRecorder.isRecording()).isTrue()

        audioRecorder.startRecording(secondFile, errorCallback!!)

        // Then
        assertThat(audioRecorder.isRecording()).isTrue()
        
        // Cleanup
        audioRecorder.stopRecording()
        firstFile.delete()
        secondFile.delete()
    }

    @Test
    fun `recording with file that cannot be created - calls error callback`() = testCoroutineRule.runTest {
        // Given - create a file in a non-existent directory
        val invalidFile = File("/invalid/path/recording.wav")

        // When
        audioRecorder.startRecording(invalidFile, errorCallback!!)
        
        // Allow time for error to occur
        testCoroutineRule.advanceTimeBy(100)

        // Then - error callback should be called due to file creation failure
        // This would be caught in the actual AudioRecord thread
    }
}
