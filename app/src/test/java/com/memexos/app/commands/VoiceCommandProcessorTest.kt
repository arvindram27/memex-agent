package com.memexos.app.commands

import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import com.google.common.truth.Truth.assertThat
import com.memexos.app.TestCoroutineRule
import com.memexos.app.commands.VoiceCommandProcessor.VoiceCommand
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for VoiceCommandProcessor.
 * 
 * These tests verify:
 * - Command parsing for various input formats
 * - WebView command execution
 * - Navigation commands (go to, search, scroll)
 * - Error handling for malformed commands
 * - Edge cases and boundary conditions
 */
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class VoiceCommandProcessorTest {

    @get:Rule
    val testCoroutineRule = TestCoroutineRule()

    private lateinit var activity: AppCompatActivity
    private lateinit var webView: WebView
    private lateinit var voiceCommandProcessor: VoiceCommandProcessor

    @Before
    fun setUp() {
        activity = mockk(relaxed = true)
        webView = mockk(relaxed = true)
        voiceCommandProcessor = VoiceCommandProcessor(activity, webView)
    }

    // ==================== Command Parsing Tests ====================

    @Test
    fun `parseCommand - search commands - returns Search command`() {
        val testCases = listOf(
            "search for cats" to "cats",
            "google android development" to "android development", 
            "find restaurants near me" to "restaurants near me",
            "Search For Pizza Places" to "Pizza Places", // Case insensitive
            "google for weather forecast" to "weather forecast"
        )

        testCases.forEach { (input, expectedQuery) ->
            val result = voiceCommandProcessor.parseCommand(input)
            
            assertThat(result).isInstanceOf(VoiceCommand.Search::class.java)
            assertThat((result as VoiceCommand.Search).query).isEqualTo(expectedQuery)
        }
    }

    @Test
    fun `parseCommand - navigation commands - returns Navigate command`() {
        val testCases = listOf(
            "go to google.com" to "google.com",
            "navigate to youtube.com" to "youtube.com",
            "open reddit.com" to "reddit.com",
            "visit stackoverflow.com" to "stackoverflow.com",
            "Go To GitHub.com" to "GitHub.com" // Case insensitive
        )

        testCases.forEach { (input, expectedUrl) ->
            val result = voiceCommandProcessor.parseCommand(input)
            
            assertThat(result).isInstanceOf(VoiceCommand.Navigate::class.java)
            assertThat((result as VoiceCommand.Navigate).url).isEqualTo(expectedUrl)
        }
    }

    @Test
    fun `parseCommand - browser navigation commands - returns correct commands`() {
        val testCases = listOf(
            "go back" to VoiceCommand.Back::class.java,
            "back" to VoiceCommand.Back::class.java,
            "go forward" to VoiceCommand.Forward::class.java,
            "forward" to VoiceCommand.Forward::class.java,
            "refresh page" to VoiceCommand.Refresh::class.java,
            "reload" to VoiceCommand.Refresh::class.java,
            "refresh" to VoiceCommand.Refresh::class.java
        )

        testCases.forEach { (input, expectedClass) ->
            val result = voiceCommandProcessor.parseCommand(input)
            assertThat(result).isInstanceOf(expectedClass)
        }
    }

    @Test
    fun `parseCommand - scroll commands - returns correct scroll commands`() {
        val testCases = listOf(
            "scroll up" to VoiceCommand.ScrollUp::class.java,
            "scroll down" to VoiceCommand.ScrollDown::class.java,
            "Scroll Up Please" to VoiceCommand.ScrollUp::class.java, // Case insensitive
            "Scroll Down Now" to VoiceCommand.ScrollDown::class.java
        )

        testCases.forEach { (input, expectedClass) ->
            val result = voiceCommandProcessor.parseCommand(input)
            assertThat(result).isInstanceOf(expectedClass)
        }
    }

    @Test
    fun `parseCommand - unknown commands - returns Unknown command`() {
        val unknownCommands = listOf(
            "play music",
            "turn on lights", 
            "what's the weather",
            "call mom",
            "",
            "   ", // Whitespace only
            "some random text that doesn't match any pattern"
        )

        unknownCommands.forEach { input ->
            val result = voiceCommandProcessor.parseCommand(input)
            
            assertThat(result).isInstanceOf(VoiceCommand.Unknown::class.java)
            assertThat((result as VoiceCommand.Unknown).originalCommand).isEqualTo(input)
        }
    }

    @Test
    fun `parseCommand - edge cases - handles gracefully`() {
        // Empty search query
        val emptySearchResult = voiceCommandProcessor.parseCommand("search for")
        assertThat(emptySearchResult).isInstanceOf(VoiceCommand.Search::class.java)
        assertThat((emptySearchResult as VoiceCommand.Search).query).isEmpty()

        // Empty navigation URL
        val emptyNavigateResult = voiceCommandProcessor.parseCommand("go to")
        assertThat(emptyNavigateResult).isInstanceOf(VoiceCommand.Navigate::class.java)
        assertThat((emptyNavigateResult as VoiceCommand.Navigate).url).isEmpty()

        // Mixed case
        val mixedCaseResult = voiceCommandProcessor.parseCommand("SeArCh FoR KoTlIn")
        assertThat(mixedCaseResult).isInstanceOf(VoiceCommand.Search::class.java)
        assertThat((mixedCaseResult as VoiceCommand.Search).query).isEqualTo("KoTlIn")
    }

    // ==================== Command Execution Tests ====================

    @Test
    fun `executeCommand - Search with valid query - loads search URL`() {
        val searchCommand = VoiceCommand.Search("android development")
        
        voiceCommandProcessor.executeCommand(searchCommand)
        
        verify { webView.loadUrl("https://www.google.com/search?q=android+development") }
    }

    @Test
    fun `executeCommand - Search with empty query - shows toast`() {
        val searchCommand = VoiceCommand.Search("")
        
        voiceCommandProcessor.executeCommand(searchCommand)
        
        verify(exactly = 0) { webView.loadUrl(any()) }
        // Note: Toast verification would require additional setup for Robolectric
    }

    @Test
    fun `executeCommand - Navigate with valid URL - loads formatted URL`() {
        val navigateCommand = VoiceCommand.Navigate("google.com")
        
        voiceCommandProcessor.executeCommand(navigateCommand)
        
        verify { webView.loadUrl("https://google.com") }
    }

    @Test
    fun `executeCommand - Navigate with HTTPS URL - loads as-is`() {
        val navigateCommand = VoiceCommand.Navigate("https://github.com")
        
        voiceCommandProcessor.executeCommand(navigateCommand)
        
        verify { webView.loadUrl("https://github.com") }
    }

    @Test
    fun `executeCommand - Navigate with HTTP URL - loads as-is`() {
        val navigateCommand = VoiceCommand.Navigate("http://example.com")
        
        voiceCommandProcessor.executeCommand(navigateCommand)
        
        verify { webView.loadUrl("http://example.com") }
    }

    @Test
    fun `executeCommand - Navigate with empty URL - shows toast`() {
        val navigateCommand = VoiceCommand.Navigate("")
        
        voiceCommandProcessor.executeCommand(navigateCommand)
        
        verify(exactly = 0) { webView.loadUrl(any()) }
    }

    @Test
    fun `executeCommand - Back when can go back - calls goBack`() {
        every { webView.canGoBack() } returns true
        
        voiceCommandProcessor.executeCommand(VoiceCommand.Back)
        
        verify { webView.goBack() }
    }

    @Test
    fun `executeCommand - Back when cannot go back - shows toast`() {
        every { webView.canGoBack() } returns false
        
        voiceCommandProcessor.executeCommand(VoiceCommand.Back)
        
        verify(exactly = 0) { webView.goBack() }
        // Toast would be shown but not verified here
    }

    @Test
    fun `executeCommand - Forward when can go forward - calls goForward`() {
        every { webView.canGoForward() } returns true
        
        voiceCommandProcessor.executeCommand(VoiceCommand.Forward)
        
        verify { webView.goForward() }
    }

    @Test
    fun `executeCommand - Forward when cannot go forward - shows toast`() {
        every { webView.canGoForward() } returns false
        
        voiceCommandProcessor.executeCommand(VoiceCommand.Forward)
        
        verify(exactly = 0) { webView.goForward() }
    }

    @Test
    fun `executeCommand - Refresh - calls reload`() {
        voiceCommandProcessor.executeCommand(VoiceCommand.Refresh)
        
        verify { webView.reload() }
    }

    @Test
    fun `executeCommand - ScrollUp - calls scrollBy with negative Y`() {
        voiceCommandProcessor.executeCommand(VoiceCommand.ScrollUp)
        
        verify { webView.scrollBy(0, -500) }
    }

    @Test
    fun `executeCommand - ScrollDown - calls scrollBy with positive Y`() {
        voiceCommandProcessor.executeCommand(VoiceCommand.ScrollDown)
        
        verify { webView.scrollBy(0, 500) }
    }

    @Test
    fun `executeCommand - Unknown command - shows toast with original command`() {
        val unknownCommand = VoiceCommand.Unknown("play music")
        
        voiceCommandProcessor.executeCommand(unknownCommand)
        
        // No WebView interactions should occur
        verify(exactly = 0) { webView.loadUrl(any()) }
        verify(exactly = 0) { webView.goBack() }
        verify(exactly = 0) { webView.goForward() }
        verify(exactly = 0) { webView.reload() }
        verify(exactly = 0) { webView.scrollBy(any(), any()) }
    }

    // ==================== Integration Tests ====================

    @Test
    fun `processCommand - end to end - parses and executes correctly`() {
        voiceCommandProcessor.processCommand("search for kotlin tutorials")
        
        verify { webView.loadUrl("https://www.google.com/search?q=kotlin+tutorials") }
    }

    @Test
    fun `processCommand - various commands - executes correctly`() {
        val testCases = listOf(
            "go to stackoverflow.com",
            "search for android jetpack compose",
            "scroll up",
            "refresh page"
        )

        testCases.forEach { command ->
            clearMocks(webView)
            voiceCommandProcessor.processCommand(command)
            verify(atLeast = 1) { 
                webView.run { 
                    loadUrl(any()) or 
                    scrollBy(any(), any()) or 
                    reload() 
                } 
            }
        }
    }

    // ==================== Query Extraction Tests ====================

    @Test
    fun `parseCommand - complex search queries - extracts correctly`() {
        val testCases = listOf(
            "search for the best android development tutorials" to "the best android development tutorials",
            "google how to implement websockets in kotlin" to "how to implement websockets in kotlin",
            "find restaurants near Central Park New York" to "restaurants near Central Park New York"
        )

        testCases.forEach { (input, expectedQuery) ->
            val result = voiceCommandProcessor.parseCommand(input)
            assertThat((result as VoiceCommand.Search).query).isEqualTo(expectedQuery)
        }
    }

    @Test
    fun `parseCommand - URL extraction - handles various formats`() {
        val testCases = listOf(
            "navigate to www.example.com" to "www.example.com",
            "go to http://secure.example.com" to "http://secure.example.com",
            "open https://api.github.com/users" to "https://api.github.com/users",
            "visit subdomain.example.org" to "subdomain.example.org"
        )

        testCases.forEach { (input, expectedUrl) ->
            val result = voiceCommandProcessor.parseCommand(input)
            assertThat((result as VoiceCommand.Navigate).url).isEqualTo(expectedUrl)
        }
    }

    // ==================== Error Handling Tests ====================

    @Test
    fun `parseCommand - malformed inputs - handles gracefully`() {
        val malformedInputs = listOf(
            "search",         // Missing query
            "go",            // Incomplete command
            "to google.com", // Missing trigger word
            "search for for for", // Repeated trigger words
            "SEARCH FOR CAPS", // All caps
            "search\nfor\nmultiline" // Special characters
        )

        malformedInputs.forEach { input ->
            // Should not throw exceptions
            val result = voiceCommandProcessor.parseCommand(input)
            assertThat(result).isNotNull()
        }
    }

    @Test
    fun `executeCommand - WebView exceptions - handles gracefully`() {
        // Simulate WebView throwing exception
        every { webView.loadUrl(any()) } throws RuntimeException("WebView error")
        
        // Should not propagate exception
        voiceCommandProcessor.executeCommand(VoiceCommand.Search("test"))
        
        verify { webView.loadUrl(any()) }
    }

    // ==================== Special Characters and Encoding Tests ====================

    @Test
    fun `executeCommand - Search with special characters - encodes correctly`() {
        val specialQueries = listOf(
            "cats & dogs" to "cats+&+dogs",
            "100% kotlin" to "100%+kotlin", 
            "what is c++?" to "what+is+c++?"
        )

        specialQueries.forEach { (query, expectedEncoded) ->
            clearMocks(webView)
            voiceCommandProcessor.executeCommand(VoiceCommand.Search(query))
            verify { webView.loadUrl("https://www.google.com/search?q=$expectedEncoded") }
        }
    }

    @Test
    fun `executeCommand - Navigate with special URLs - handles correctly`() {
        val specialUrls = listOf(
            "api.example.com/v1/users?id=123",
            "localhost:8080",
            "192.168.1.1:3000"
        )

        specialUrls.forEach { url ->
            clearMocks(webView)
            voiceCommandProcessor.executeCommand(VoiceCommand.Navigate(url))
            verify { webView.loadUrl("https://$url") }
        }
    }
}

/**
 * Parameterized tests for systematic command parsing validation
 */
@RunWith(Parameterized::class)
class VoiceCommandProcessorParameterizedTest(
    private val input: String,
    private val expectedCommandType: Class<out VoiceCommand>
) {

    private lateinit var processor: VoiceCommandProcessor

    @Before
    fun setUp() {
        val activity = mockk<AppCompatActivity>(relaxed = true)
        val webView = mockk<WebView>(relaxed = true)
        processor = VoiceCommandProcessor(activity, webView)
    }

    @Test
    fun `parseCommand parameterized test`() {
        val result = processor.parseCommand(input)
        assertThat(result).isInstanceOf(expectedCommandType)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "Command: {0} -> {1}")
        fun data(): Collection<Array<Any>> {
            return listOf(
                // Search commands
                arrayOf("search for cats", VoiceCommand.Search::class.java),
                arrayOf("google android", VoiceCommand.Search::class.java),
                arrayOf("find restaurants", VoiceCommand.Search::class.java),
                
                // Navigation commands  
                arrayOf("go to google.com", VoiceCommand.Navigate::class.java),
                arrayOf("navigate to youtube.com", VoiceCommand.Navigate::class.java),
                arrayOf("open reddit.com", VoiceCommand.Navigate::class.java),
                
                // Browser controls
                arrayOf("go back", VoiceCommand.Back::class.java),
                arrayOf("forward", VoiceCommand.Forward::class.java), 
                arrayOf("refresh", VoiceCommand.Refresh::class.java),
                arrayOf("reload page", VoiceCommand.Refresh::class.java),
                
                // Scrolling
                arrayOf("scroll up", VoiceCommand.ScrollUp::class.java),
                arrayOf("scroll down", VoiceCommand.ScrollDown::class.java),
                
                // Unknown commands
                arrayOf("play music", VoiceCommand.Unknown::class.java),
                arrayOf("turn on lights", VoiceCommand.Unknown::class.java),
                arrayOf("", VoiceCommand.Unknown::class.java)
            )
        }
    }
}
