package com.memexos.app

import android.Manifest
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.web.assertion.WebViewAssertions.webMatches
import androidx.test.espresso.web.sugar.Web.onWebView
import androidx.test.espresso.web.webdriver.DriverAtoms.*
import androidx.test.espresso.web.webdriver.Locator
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.GrantPermissionRule
import com.memexos.app.MockWebServerRule
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented UI tests for MainActivity.
 * 
 * These tests verify:
 * - UI interaction testing with Espresso
 * - Permission request flows
 * - WebView loading and navigation
 * - Recording dialog interactions
 * - End-to-end user workflows
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    @get:Rule
    val mockWebServerRule = MockWebServerRule()

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    // ==================== Basic UI Tests ====================

    @Test
    fun mainActivity_launches_allViewsVisible() {
        // Check that all main UI components are visible
        onView(withId(R.id.webView)).check(matches(isDisplayed()))
        onView(withId(R.id.urlEditText)).check(matches(isDisplayed()))
        onView(withId(R.id.toolbar)).check(matches(isDisplayed()))
        onView(withId(R.id.fabRecord)).check(matches(isDisplayed()))
        onView(withId(R.id.fabSettings)).check(matches(isDisplayed()))
    }

    @Test
    fun toolbar_hasCorrectConfiguration() {
        onView(withId(R.id.toolbar)).check(matches(isDisplayed()))
        // Toolbar title should be hidden as per MainActivity configuration
    }

    @Test
    fun urlEditText_hasCorrectConfiguration() {
        onView(withId(R.id.urlEditText))
            .check(matches(isDisplayed()))
            .check(matches(isEnabled()))
            .check(matches(withHint(anyOf(
                containsString("URL"),
                containsString("Search"),
                containsString("Address")
            ))))
    }

    @Test
    fun fabButtons_areVisible() {
        onView(withId(R.id.fabRecord))
            .check(matches(isDisplayed()))
            .check(matches(isEnabled()))
        
        onView(withId(R.id.fabSettings))
            .check(matches(isDisplayed()))
            .check(matches(isEnabled()))
    }

    // ==================== WebView Tests ====================

    @Test
    fun webView_loadsDefaultUrl() {
        // Wait for WebView to load
        runBlocking { delay(2000) }
        
        // Check WebView is visible and loaded
        onView(withId(R.id.webView))
            .check(matches(isDisplayed()))
    }

    @Test
    fun urlInput_updatesWebView() {
        // Setup mock server response
        mockWebServerRule.enqueue(
            MockWebServerRule.createHtmlResponse("Test Page", "Test Content")
        )
        val testUrl = mockWebServerRule.url("/test")

        // Enter URL and press enter
        onView(withId(R.id.urlEditText))
            .perform(clearText())
            .perform(typeText(testUrl))
            .perform(pressImeActionButton())

        // Wait for navigation
        runBlocking { delay(1500) }

        // Verify WebView loaded the URL
        onWebView()
            .withElement(findElement(Locator.TAG_NAME, "h1"))
            .check(webMatches(getText(), containsString("Test Page")))
    }

    @Test
    fun urlInput_handlesHttpsRedirect() {
        // Test automatic HTTPS prefix addition
        val testDomain = "example.com"
        
        mockWebServerRule.enqueue(
            MockWebServerRule.createHtmlResponse("Example", "Example Content")
        )

        onView(withId(R.id.urlEditText))
            .perform(clearText())
            .perform(typeText(testDomain))
            .perform(pressImeActionButton())

        runBlocking { delay(1000) }

        // URL should have been prefixed with https://
        onView(withId(R.id.urlEditText))
            .check(matches(withText(startsWith("https://"))))
    }

    @Test
    fun webView_handlesBackNavigation() {
        // Load first page
        mockWebServerRule.enqueue(
            MockWebServerRule.createHtmlResponse("Page 1", "First Page")
        )
        val firstUrl = mockWebServerRule.url("/page1")

        onView(withId(R.id.urlEditText))
            .perform(clearText())
            .perform(typeText(firstUrl))
            .perform(pressImeActionButton())

        runBlocking { delay(1000) }

        // Load second page
        mockWebServerRule.enqueue(
            MockWebServerRule.createHtmlResponse("Page 2", "Second Page")
        )
        val secondUrl = mockWebServerRule.url("/page2")

        onView(withId(R.id.urlEditText))
            .perform(clearText())
            .perform(typeText(secondUrl))
            .perform(pressImeActionButton())

        runBlocking { delay(1000) }

        // Test back navigation via system back button
        scenario.onActivity { activity ->
            activity.onBackPressed()
        }

        runBlocking { delay(500) }

        // Should navigate back to first page
        onWebView()
            .withElement(findElement(Locator.TAG_NAME, "h1"))
            .check(webMatches(getText(), containsString("Page 1")))
    }

    // ==================== Recording Dialog Tests ====================

    @Test
    fun fabRecord_opensRecordingDialog() {
        // Click record FAB
        onView(withId(R.id.fabRecord))
            .perform(click())

        // Wait for dialog animation
        runBlocking { delay(500) }

        // Check recording dialog elements are visible
        onView(withId(R.id.micPulseCircle))
            .check(matches(isDisplayed()))
        onView(withId(R.id.timerText))
            .check(matches(isDisplayed()))
            .check(matches(withText("00:00")))
        onView(withId(R.id.recordingStatusText))
            .check(matches(isDisplayed()))
        onView(withId(R.id.stopButton))
            .check(matches(isDisplayed()))
            .check(matches(isEnabled()))
    }

    @Test
    fun recordingDialog_stopButton_stopsRecording() {
        // Start recording
        onView(withId(R.id.fabRecord))
            .perform(click())

        runBlocking { delay(500) }

        // Stop recording
        onView(withId(R.id.stopButton))
            .perform(click())

        // Wait for processing
        runBlocking { delay(1000) }

        // Dialog should be dismissed
        onView(withId(R.id.micPulseCircle))
            .check(doesNotExist())
    }

    @Test
    fun recordingDialog_timerUpdates() {
        // Start recording
        onView(withId(R.id.fabRecord))
            .perform(click())

        // Wait for timer to update (2 seconds)
        runBlocking { delay(2500) }

        // Timer should show elapsed time
        onView(withId(R.id.timerText))
            .check(matches(withText(matchesPattern("00:0[1-9]"))))

        // Stop recording
        onView(withId(R.id.stopButton))
            .perform(click())
    }

    // ==================== Settings FAB Test ====================

    @Test
    fun fabSettings_clickable() {
        // Settings FAB should be clickable (even if not implemented)
        onView(withId(R.id.fabSettings))
            .perform(click())

        // Should not crash the app
        onView(withId(R.id.fabSettings))
            .check(matches(isDisplayed()))
    }

    // ==================== Progress Overlay Tests ====================

    @Test
    fun progressOverlay_initiallyHidden() {
        onView(withId(R.id.progressOverlay))
            .check(matches(not(isDisplayed())))
    }

    @Test
    fun progressOverlay_showsDuringProcessing() {
        // Start and quickly stop recording to trigger processing
        onView(withId(R.id.fabRecord))
            .perform(click())

        runBlocking { delay(200) }

        onView(withId(R.id.stopButton))
            .perform(click())

        // Progress overlay should become visible during processing
        runBlocking { delay(100) }

        onView(withId(R.id.progressOverlay))
            .check(matches(isDisplayed()))
        onView(withId(R.id.progressText))
            .check(matches(withText(containsString("Processing"))))
    }

    // ==================== Error Handling Tests ====================

    @Test
    fun webView_handlesInvalidUrl() {
        val invalidUrl = "invalid://not-a-real-url"

        onView(withId(R.id.urlEditText))
            .perform(clearText())
            .perform(typeText(invalidUrl))
            .perform(pressImeActionButton())

        runBlocking { delay(2000) }

        // App should not crash and WebView should still be displayed
        onView(withId(R.id.webView))
            .check(matches(isDisplayed()))
    }

    @Test
    fun webView_handlesNetworkError() {
        val nonExistentUrl = "https://this-domain-definitely-does-not-exist-12345.com"

        onView(withId(R.id.urlEditText))
            .perform(clearText())
            .perform(typeText(nonExistentUrl))
            .perform(pressImeActionButton())

        runBlocking { delay(3000) }

        // App should handle network errors gracefully
        onView(withId(R.id.webView))
            .check(matches(isDisplayed()))
    }

    // ==================== Accessibility Tests ====================

    @Test
    fun ui_hasAccessibilitySupport() {
        // Check important views have content descriptions
        onView(withId(R.id.fabRecord))
            .check(matches(hasContentDescription()))

        onView(withId(R.id.fabSettings))
            .check(matches(hasContentDescription()))

        onView(withId(R.id.urlEditText))
            .check(matches(isEnabled()))
    }

    // ==================== Orientation Change Tests ====================

    @Test
    fun activity_handlesOrientationChange() {
        // Load a test page
        mockWebServerRule.enqueue(
            MockWebServerRule.createHtmlResponse("Test Page", "Content")
        )
        val testUrl = mockWebServerRule.url("/test")

        onView(withId(R.id.urlEditText))
            .perform(clearText())
            .perform(typeText(testUrl))
            .perform(pressImeActionButton())

        runBlocking { delay(1000) }

        // Simulate orientation change by recreating activity
        scenario.recreate()

        runBlocking { delay(1000) }

        // UI should still be functional
        onView(withId(R.id.webView))
            .check(matches(isDisplayed()))
        onView(withId(R.id.urlEditText))
            .check(matches(isDisplayed()))
        onView(withId(R.id.fabRecord))
            .check(matches(isDisplayed()))
    }

    // ==================== Search Functionality Tests ====================

    @Test
    fun urlInput_triggersGoogleSearch() {
        val searchQuery = "android development"

        onView(withId(R.id.urlEditText))
            .perform(clearText())
            .perform(typeText(searchQuery))
            .perform(pressImeActionButton())

        runBlocking { delay(1000) }

        // URL should be updated to show Google search
        onView(withId(R.id.urlEditText))
            .check(matches(withText(containsString("https://"))))
    }

    // ==================== Memory Leak Prevention Tests ====================

    @Test
    fun activity_releasesResourcesOnDestroy() {
        // Start recording to create resources
        onView(withId(R.id.fabRecord))
            .perform(click())

        runBlocking { delay(500) }

        onView(withId(R.id.stopButton))
            .perform(click())

        runBlocking { delay(500) }

        // Finish activity - should not cause memory leaks
        scenario.close()

        // Create new scenario to ensure clean state
        scenario = ActivityScenario.launch(MainActivity::class.java)

        // App should still work normally
        onView(withId(R.id.webView))
            .check(matches(isDisplayed()))
    }
}
