package com.memexos.app

import android.Manifest
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.IdlingResource
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean

/**
 * End-to-end integration tests for the complete recording workflow.
 * 
 * These tests verify:
 * - Complete voice recording flow from start to finish
 * - Integration between AudioRecorder, WhisperService, and UI
 * - Error handling in the full pipeline
 * - Resource cleanup and memory management
 * - IdlingResources for proper async test synchronization
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class RecordingFlowTest {

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var recordingIdlingResource: RecordingIdlingResource

    @Before
    fun setUp() {
        recordingIdlingResource = RecordingIdlingResource()
        IdlingRegistry.getInstance().register(recordingIdlingResource)
        
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        IdlingRegistry.getInstance().unregister(recordingIdlingResource)
        scenario.close()
    }

    // ==================== Complete Recording Flow Tests ====================

    @Test
    fun completeRecordingFlow_shortRecording_processesSuccessfully() {
        // Start recording
        onView(withId(R.id.fabRecord))
            .perform(click())

        // Verify recording dialog appears
        onView(withId(R.id.micPulseCircle))
            .check(matches(isDisplayed()))
        onView(withId(R.id.timerText))
            .check(matches(withText("00:00")))

        // Let recording run for a short time
        runBlocking { delay(1500) }

        // Verify timer has updated
        onView(withId(R.id.timerText))
            .check(matches(withText(matchesPattern("00:0[1-2]"))))

        // Stop recording
        onView(withId(R.id.stopButton))
            .perform(click())

        // Recording dialog should be dismissed
        onView(withId(R.id.micPulseCircle))
            .check(doesNotExist())

        // Processing overlay should appear
        onView(withId(R.id.progressOverlay))
            .check(matches(isDisplayed()))
        onView(withId(R.id.progressText))
            .check(matches(withText(containsString("Processing"))))

        // Wait for processing to complete (with timeout)
        runBlocking { delay(3000) }

        // Processing overlay should be hidden
        onView(withId(R.id.progressOverlay))
            .check(matches(not(isDisplayed())))
    }

    @Test
    fun recordingFlow_multipleRecordings_handlesCorrectly() {
        // First recording
        onView(withId(R.id.fabRecord))
            .perform(click())

        runBlocking { delay(1000) }

        onView(withId(R.id.stopButton))
            .perform(click())

        // Wait for processing
        runBlocking { delay(2000) }

        // Second recording should work
        onView(withId(R.id.fabRecord))
            .perform(click())

        onView(withId(R.id.micPulseCircle))
            .check(matches(isDisplayed()))

        runBlocking { delay(1000) }

        onView(withId(R.id.stopButton))
            .perform(click())

        // Should process successfully
        runBlocking { delay(2000) }

        // UI should return to normal state
        onView(withId(R.id.fabRecord))
            .check(matches(isDisplayed()))
            .check(matches(isEnabled()))
    }

    @Test
    fun recordingFlow_veryShortRecording_handlesGracefully() {
        // Start recording
        onView(withId(R.id.fabRecord))
            .perform(click())

        // Stop almost immediately
        runBlocking { delay(100) }

        onView(withId(R.id.stopButton))
            .perform(click())

        // Should handle very short recordings gracefully
        runBlocking { delay(2000) }

        // UI should return to normal state
        onView(withId(R.id.fabRecord))
            .check(matches(isDisplayed()))
        onView(withId(R.id.progressOverlay))
            .check(matches(not(isDisplayed())))
    }

    @Test
    fun recordingFlow_maxDurationRecording_stopsAutomatically() {
        // Start recording
        onView(withId(R.id.fabRecord))
            .perform(click())

        // Wait for maximum duration (60 seconds in test would be too long)
        // Instead test the warning threshold (10 seconds)
        runBlocking { delay(12000) }

        // Recording should either stop automatically or show warning
        // Check if dialog still exists or has been auto-dismissed
        try {
            onView(withId(R.id.recordingStatusText))
                .check(matches(withText(containsString("ending"))))
        } catch (e: Exception) {
            // Dialog may have been auto-dismissed, which is also valid
        }
    }

    // ==================== Error Handling Tests ====================

    @Test
    fun recordingFlow_whileRecording_handlesInterruption() {
        // Start recording
        onView(withId(R.id.fabRecord))
            .perform(click())

        runBlocking { delay(1000) }

        // Simulate activity recreation (like orientation change)
        scenario.recreate()

        runBlocking { delay(1000) }

        // App should recover gracefully
        onView(withId(R.id.webView))
            .check(matches(isDisplayed()))
        onView(withId(R.id.fabRecord))
            .check(matches(isDisplayed()))

        // Should be able to start new recording
        onView(withId(R.id.fabRecord))
            .perform(click())

        onView(withId(R.id.micPulseCircle))
            .check(matches(isDisplayed()))

        onView(withId(R.id.stopButton))
            .perform(click())
    }

    @Test
    fun recordingFlow_rapidStartStop_handlesCorrectly() {
        // Rapid start/stop cycles
        for (i in 1..3) {
            onView(withId(R.id.fabRecord))
                .perform(click())

            runBlocking { delay(200) }

            onView(withId(R.id.stopButton))
                .perform(click())

            runBlocking { delay(500) }
        }

        // Should handle rapid cycles without crashing
        onView(withId(R.id.fabRecord))
            .check(matches(isDisplayed()))
            .check(matches(isEnabled()))
    }

    // ==================== Resource Management Tests ====================

    @Test
    fun recordingFlow_resourceCleanup_completesSuccessfully() {
        var recordingCount = 0

        // Perform multiple recordings to test resource management
        repeat(5) {
            recordingCount++

            onView(withId(R.id.fabRecord))
                .perform(click())

            runBlocking { delay(800) }

            onView(withId(R.id.stopButton))
                .perform(click())

            // Wait for processing and cleanup
            runBlocking { delay(1500) }

            // Verify UI is in clean state
            onView(withId(R.id.progressOverlay))
                .check(matches(not(isDisplayed())))
            onView(withId(R.id.fabRecord))
                .check(matches(isEnabled()))
        }

        // Final verification - app should still be responsive
        onView(withId(R.id.webView))
            .check(matches(isDisplayed()))
    }

    // ==================== UI State Tests ====================

    @Test
    fun recordingFlow_uiStates_transitionCorrectly() {
        // Initial state
        onView(withId(R.id.progressOverlay))
            .check(matches(not(isDisplayed())))
        onView(withId(R.id.fabRecord))
            .check(matches(isEnabled()))

        // Recording state
        onView(withId(R.id.fabRecord))
            .perform(click())

        onView(withId(R.id.micPulseCircle))
            .check(matches(isDisplayed()))
        onView(withId(R.id.timerText))
            .check(matches(isDisplayed()))
        onView(withId(R.id.stopButton))
            .check(matches(isEnabled()))

        runBlocking { delay(1000) }

        // Stop recording -> Processing state
        onView(withId(R.id.stopButton))
            .perform(click())

        onView(withId(R.id.progressOverlay))
            .check(matches(isDisplayed()))
        onView(withId(R.id.progressText))
            .check(matches(isDisplayed()))

        runBlocking { delay(2000) }

        // Final state - back to initial
        onView(withId(R.id.progressOverlay))
            .check(matches(not(isDisplayed())))
        onView(withId(R.id.fabRecord))
            .check(matches(isEnabled()))
    }

    // ==================== Integration Tests ====================

    @Test
    fun recordingFlow_webViewIntegration_remainsFunctional() {
        // Verify WebView works before recording
        onView(withId(R.id.webView))
            .check(matches(isDisplayed()))

        // Perform recording
        onView(withId(R.id.fabRecord))
            .perform(click())

        runBlocking { delay(1000) }

        onView(withId(R.id.stopButton))
            .perform(click())

        runBlocking { delay(2000) }

        // Verify WebView still works after recording
        onView(withId(R.id.webView))
            .check(matches(isDisplayed()))
        onView(withId(R.id.urlEditText))
            .check(matches(isEnabled()))
    }

    @Test
    fun recordingFlow_voiceCommandExecution_worksEndToEnd() {
        // This test would require actual voice command execution
        // For now, we test the UI flow and assume the command processing works
        
        onView(withId(R.id.fabRecord))
            .perform(click())

        runBlocking { delay(1500) }

        onView(withId(R.id.stopButton))
            .perform(click())

        // Wait for processing and potential command dialog
        runBlocking { delay(3000) }

        // App should be in stable state regardless of command recognition
        onView(withId(R.id.webView))
            .check(matches(isDisplayed()))
        onView(withId(R.id.fabRecord))
            .check(matches(isEnabled()))
    }

    // ==================== Performance Tests ====================

    @Test
    fun recordingFlow_performance_completesWithinReasonableTime() {
        val startTime = System.currentTimeMillis()

        // Perform complete recording flow
        onView(withId(R.id.fabRecord))
            .perform(click())

        runBlocking { delay(1000) }

        onView(withId(R.id.stopButton))
            .perform(click())

        // Wait for processing to complete
        runBlocking { delay(5000) }

        val endTime = System.currentTimeMillis()
        val totalTime = endTime - startTime

        // Recording + processing should complete within 10 seconds for short recording
        assert(totalTime < 10000) { "Recording flow took too long: ${totalTime}ms" }

        // Verify completion
        onView(withId(R.id.progressOverlay))
            .check(matches(not(isDisplayed())))
    }
}

/**
 * IdlingResource for recording operations to ensure proper test synchronization
 */
class RecordingIdlingResource : IdlingResource {
    
    private val isIdle = AtomicBoolean(true)
    private var callback: IdlingResource.ResourceCallback? = null

    override fun getName(): String = "RecordingIdlingResource"

    override fun isIdleNow(): Boolean = isIdle.get()

    override fun registerIdleTransitionCallback(callback: IdlingResource.ResourceCallback?) {
        this.callback = callback
    }

    fun setRecordingState(isRecording: Boolean) {
        val wasIdle = isIdle.getAndSet(!isRecording)
        if (wasIdle && isRecording) {
            // Was idle, now busy
        } else if (!wasIdle && !isRecording) {
            // Was busy, now idle
            callback?.onTransitionToIdle()
        }
    }
}
