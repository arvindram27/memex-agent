package com.memexagent.app.commands

import android.util.Log
import android.webkit.WebView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.*

/**
 * Enhanced voice command processor with structured command handling
 */
class VoiceCommandProcessor(
    private val activity: AppCompatActivity,
    private val webView: WebView
) {
    
    companion object {
        private const val TAG = "VoiceCommandProcessor"
        private const val GOOGLE_SEARCH_BASE_URL = "https://www.google.com/search?q="
        private const val SCROLL_DISTANCE_PX = 500
    }
    
    /**
     * Sealed class representing different voice commands
     */
    sealed class VoiceCommand {
        data class Search(val query: String) : VoiceCommand()
        data class Navigate(val url: String) : VoiceCommand()
        object Back : VoiceCommand()
        object Forward : VoiceCommand()
        object Refresh : VoiceCommand()
        object ScrollUp : VoiceCommand()
        object ScrollDown : VoiceCommand()
        data class Unknown(val originalCommand: String) : VoiceCommand()
    }
    
    /**
     * Parse voice command text into structured command
     */
    fun parseCommand(commandText: String): VoiceCommand {
        val lowerCommand = commandText.lowercase(Locale.US).trim()
        
        return when {
            // Search commands
            containsAny(lowerCommand, listOf("search", "google", "find")) -> {
                val query = extractQuery(commandText, listOf("search", "google", "find", "for"))
                VoiceCommand.Search(query)
            }
            
            // Navigation commands
            containsAny(lowerCommand, listOf("go to", "navigate to", "open", "visit")) -> {
                val url = extractQuery(commandText, listOf("go to", "navigate to", "open", "visit"))
                VoiceCommand.Navigate(url)
            }
            
            // Browser navigation
            lowerCommand.contains("back") -> VoiceCommand.Back
            lowerCommand.contains("forward") -> VoiceCommand.Forward
            containsAny(lowerCommand, listOf("refresh", "reload")) -> VoiceCommand.Refresh
            
            // Scrolling
            lowerCommand.contains("scroll down") -> VoiceCommand.ScrollDown
            lowerCommand.contains("scroll up") -> VoiceCommand.ScrollUp
            
            else -> VoiceCommand.Unknown(commandText)
        }
    }
    
    /**
     * Execute the parsed voice command
     */
    fun executeCommand(command: VoiceCommand) {
        Log.d(TAG, "Executing command: $command")
        
        when (command) {
            is VoiceCommand.Search -> executeSearch(command.query)
            is VoiceCommand.Navigate -> executeNavigation(command.url)
            is VoiceCommand.Back -> executeBack()
            is VoiceCommand.Forward -> executeForward()
            is VoiceCommand.Refresh -> executeRefresh()
            is VoiceCommand.ScrollUp -> executeScrollUp()
            is VoiceCommand.ScrollDown -> executeScrollDown()
            is VoiceCommand.Unknown -> executeUnknown(command.originalCommand)
        }
    }
    
    /**
     * Process and execute voice command in one call
     */
    fun processCommand(commandText: String) {
        val command = parseCommand(commandText)
        executeCommand(command)
    }
    
    private fun executeSearch(query: String) {
        if (query.isNotBlank()) {
            val searchUrl = GOOGLE_SEARCH_BASE_URL + query.replace(" ", "+")
            Log.d(TAG, "Performing search: $query")
            webView.loadUrl(searchUrl)
        } else {
            showToast("Please provide a search query")
        }
    }
    
    private fun executeNavigation(url: String) {
        if (url.isNotBlank()) {
            val formattedUrl = formatUrl(url)
            Log.d(TAG, "Navigating to: $formattedUrl")
            webView.loadUrl(formattedUrl)
        } else {
            showToast("Please provide a valid URL")
        }
    }
    
    private fun executeBack() {
        if (webView.canGoBack()) {
            Log.d(TAG, "Going back")
            webView.goBack()
        } else {
            showToast("Cannot go back")
        }
    }
    
    private fun executeForward() {
        if (webView.canGoForward()) {
            Log.d(TAG, "Going forward")
            webView.goForward()
        } else {
            showToast("Cannot go forward")
        }
    }
    
    private fun executeRefresh() {
        Log.d(TAG, "Refreshing page")
        webView.reload()
    }
    
    private fun executeScrollUp() {
        Log.d(TAG, "Scrolling up")
        webView.scrollBy(0, -SCROLL_DISTANCE_PX)
    }
    
    private fun executeScrollDown() {
        Log.d(TAG, "Scrolling down")
        webView.scrollBy(0, SCROLL_DISTANCE_PX)
    }
    
    private fun executeUnknown(originalCommand: String) {
        Log.w(TAG, "Unknown command: $originalCommand")
        showToast("Command not recognized: $originalCommand")
    }
    
    private fun containsAny(text: String, keywords: List<String>): Boolean {
        return keywords.any { text.contains(it) }
    }
    
    private fun extractQuery(commandText: String, triggerWords: List<String>): String {
        val lowerCommand = commandText.lowercase(Locale.US)
        var query = commandText
        
        // Remove trigger words (case insensitive)
        for (word in triggerWords) {
            val regex = Regex("\\b$word\\b", RegexOption.IGNORE_CASE)
            query = query.replace(regex, "")
        }
        
        return query.trim()
    }
    
    private fun formatUrl(url: String): String {
        val trimmedUrl = url.trim()
        return if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
            "https://$trimmedUrl"
        } else {
            trimmedUrl
        }
    }
    
    private fun showToast(message: String) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
    }
}