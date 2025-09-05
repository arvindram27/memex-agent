package com.memexagent.app.voice

import android.util.Log
import com.memexagent.app.context.VisualContextProcessor
import java.util.*

/**
 * Enhanced voice command processing system that extends Whisper integration
 * to understand user intents and extract relevant entities from voice commands.
 */
class VoiceIntentProcessor {
    
    companion object {
        private const val TAG = "VoiceIntentProcessor"
    }
    
    data class VoiceCommand(
        val intent: CommandIntent,
        val entities: List<String>,
        val originalText: String,
        val confidence: Float = 0.0f,
        val context: VisualContextProcessor.WebPageContext? = null,
        val parameters: Map<String, String> = emptyMap()
    )
    
    enum class CommandIntent {
        // Navigation commands
        NAVIGATE,
        GO_BACK,
        GO_FORWARD,
        REFRESH,
        GO_HOME,
        
        // Interaction commands
        CLICK,
        SCROLL,
        SWIPE,
        LONG_PRESS,
        
        // Form interaction
        FILL_FORM,
        SUBMIT_FORM,
        CLEAR_FORM,
        
        // Search and find
        SEARCH,
        FIND_TEXT,
        FIND_ELEMENT,
        
        // Information extraction
        READ,
        EXTRACT,
        SUMMARIZE,
        
        // Utility commands
        TRANSLATE,
        COPY,
        SHARE,
        SCREENSHOT,
        
        // Agent control
        HELP,
        STOP,
        
        UNKNOWN
    }
    
    // Command patterns for intent recognition
    private val commandPatterns = mapOf(
        // Navigation patterns
        CommandIntent.NAVIGATE to listOf(
            "go to", "navigate to", "open", "visit", "load", "browse to"
        ),
        CommandIntent.GO_BACK to listOf(
            "go back", "back", "previous page", "return"
        ),
        CommandIntent.GO_FORWARD to listOf(
            "go forward", "forward", "next page", "continue"
        ),
        CommandIntent.REFRESH to listOf(
            "refresh", "reload", "update page", "refresh page"
        ),
        
        // Click patterns
        CommandIntent.CLICK to listOf(
            "click", "tap", "press", "select", "choose", "click on", "tap on"
        ),
        
        // Scroll patterns
        CommandIntent.SCROLL to listOf(
            "scroll", "scroll down", "scroll up", "page down", "page up", "scroll to"
        ),
        
        // Form patterns
        CommandIntent.FILL_FORM to listOf(
            "fill", "fill in", "fill out", "enter", "type", "input", "write"
        ),
        CommandIntent.SUBMIT_FORM to listOf(
            "submit", "send", "submit form", "send form", "save form"
        ),
        
        // Search patterns
        CommandIntent.SEARCH to listOf(
            "search", "search for", "find", "look for", "google", "search on"
        ),
        CommandIntent.FIND_TEXT to listOf(
            "find text", "find word", "locate", "find on page", "search page"
        ),
        
        // Reading patterns
        CommandIntent.READ to listOf(
            "read", "read aloud", "read this", "tell me", "what does it say"
        ),
        CommandIntent.EXTRACT to listOf(
            "extract", "get", "copy text", "save", "grab", "collect"
        ),
        CommandIntent.SUMMARIZE to listOf(
            "summarize", "summary", "brief", "overview", "main points"
        ),
        
        // Utility patterns
        CommandIntent.TRANSLATE to listOf(
            "translate", "translate to", "convert to", "in spanish", "in french"
        ),
        CommandIntent.SCREENSHOT to listOf(
            "screenshot", "capture", "take picture", "save image"
        ),
        
        // Agent control
        CommandIntent.HELP to listOf(
            "help", "what can you do", "commands", "assistance", "how do i"
        ),
        CommandIntent.STOP to listOf(
            "stop", "cancel", "never mind", "quit", "exit"
        )
    )
    
    // Directional and positional keywords
    private val directionalKeywords = mapOf(
        "up" to "up",
        "down" to "down",
        "left" to "left", 
        "right" to "right",
        "top" to "top",
        "bottom" to "bottom",
        "first" to "first",
        "last" to "last",
        "second" to "second",
        "third" to "third",
        "next" to "next",
        "previous" to "previous"
    )
    
    // Form field keywords
    private val formFieldKeywords = mapOf(
        "email" to "email",
        "password" to "password",
        "username" to "username",
        "name" to "name",
        "phone" to "phone",
        "address" to "address",
        "search" to "search",
        "comment" to "comment",
        "message" to "message"
    )
    
    /**
     * Process transcribed voice text and extract command intent and entities.
     */
    fun processCommand(
        transcribedText: String, 
        pageContext: VisualContextProcessor.WebPageContext? = null
    ): VoiceCommand {
        
        val normalizedText = normalizeText(transcribedText)
        Log.d(TAG, "Processing voice command: $normalizedText")
        
        // Extract intent
        val intent = extractIntent(normalizedText)
        
        // Extract entities based on intent
        val entities = extractEntities(normalizedText, intent, pageContext)
        
        // Extract parameters
        val parameters = extractParameters(normalizedText, intent)
        
        // Calculate confidence (simplified)
        val confidence = calculateConfidence(normalizedText, intent, entities)
        
        val command = VoiceCommand(
            intent = intent,
            entities = entities,
            originalText = transcribedText,
            confidence = confidence,
            context = pageContext,
            parameters = parameters
        )
        
        Log.d(TAG, "Processed command: Intent=$intent, Entities=$entities, Confidence=$confidence")
        return command
    }
    
    /**
     * Normalize text for better pattern matching.
     */
    private fun normalizeText(text: String): String {
        return text.lowercase(Locale.getDefault())
            .replace(Regex("[^a-zA-Z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
    
    /**
     * Extract command intent from normalized text.
     */
    private fun extractIntent(normalizedText: String): CommandIntent {
        var bestMatch = CommandIntent.UNKNOWN
        var maxMatches = 0
        
        for ((intent, patterns) in commandPatterns) {
            val matches = patterns.count { pattern ->
                normalizedText.contains(pattern)
            }
            
            if (matches > maxMatches) {
                maxMatches = matches
                bestMatch = intent
            }
        }
        
        // Special case handling for complex patterns
        if (bestMatch == CommandIntent.UNKNOWN) {
            bestMatch = handleSpecialCases(normalizedText)
        }
        
        return bestMatch
    }
    
    /**
     * Handle special cases that don't fit standard patterns.
     */
    private fun handleSpecialCases(normalizedText: String): CommandIntent {
        return when {
            normalizedText.contains("red button") || 
            normalizedText.contains("blue link") ||
            normalizedText.contains("green text") -> CommandIntent.CLICK
            
            normalizedText.contains("what is") ||
            normalizedText.contains("tell me about") -> CommandIntent.READ
            
            normalizedText.contains("sign in") ||
            normalizedText.contains("log in") -> CommandIntent.FILL_FORM
            
            normalizedText.contains("how much") ||
            normalizedText.contains("price") -> CommandIntent.EXTRACT
            
            else -> CommandIntent.UNKNOWN
        }
    }
    
    /**
     * Extract entities (targets, values, etc.) from the command.
     */
    private fun extractEntities(
        normalizedText: String, 
        intent: CommandIntent,
        pageContext: VisualContextProcessor.WebPageContext?
    ): List<String> {
        val entities = mutableListOf<String>()
        
        when (intent) {
            CommandIntent.CLICK, CommandIntent.FIND_ELEMENT -> {
                // Extract clickable element references
                entities.addAll(extractClickableReferences(normalizedText, pageContext))
            }
            
            CommandIntent.FILL_FORM -> {
                // Extract form field references and values
                entities.addAll(extractFormReferences(normalizedText, pageContext))
            }
            
            CommandIntent.NAVIGATE, CommandIntent.SEARCH -> {
                // Extract URLs or search terms
                entities.addAll(extractUrlsOrSearchTerms(normalizedText))
            }
            
            CommandIntent.SCROLL -> {
                // Extract directional information
                entities.addAll(extractDirections(normalizedText))
            }
            
            CommandIntent.FIND_TEXT, CommandIntent.READ -> {
                // Extract text to find or read
                entities.addAll(extractTextReferences(normalizedText))
            }
            
            CommandIntent.TRANSLATE -> {
                // Extract target language
                entities.addAll(extractLanguage(normalizedText))
            }
            
            else -> {
                // General entity extraction
                entities.addAll(extractGeneralEntities(normalizedText))
            }
        }
        
        return entities.distinct()
    }
    
    /**
     * Extract references to clickable elements.
     */
    private fun extractClickableReferences(
        text: String, 
        pageContext: VisualContextProcessor.WebPageContext?
    ): List<String> {
        val references = mutableListOf<String>()
        
        // Extract color + element type references
        val colorElementRegex = Regex("(red|blue|green|yellow|white|black)\\s+(button|link|text|icon)")
        colorElementRegex.findAll(text).forEach { match ->
            references.add(match.value)
        }
        
        // Extract positional references
        directionalKeywords.keys.forEach { direction ->
            if (text.contains(direction)) {
                references.add(direction)
            }
        }
        
        // Extract specific text from page context
        pageContext?.clickableElements?.forEach { element ->
            if (element.text.isNotEmpty() && text.contains(element.text.lowercase())) {
                references.add(element.text)
            }
        }
        
        return references
    }
    
    /**
     * Extract form field references and potential values.
     */
    private fun extractFormReferences(
        text: String,
        pageContext: VisualContextProcessor.WebPageContext?
    ): List<String> {
        val references = mutableListOf<String>()
        
        // Extract field type references
        formFieldKeywords.keys.forEach { fieldType ->
            if (text.contains(fieldType)) {
                references.add(fieldType)
            }
        }
        
        // Extract potential values after "with" or "as"
        val valueRegex = Regex("(?:with|as)\\s+([\\w\\s@\\.]+)")
        valueRegex.findAll(text).forEach { match ->
            references.add(match.groupValues[1].trim())
        }
        
        return references
    }
    
    /**
     * Extract URLs or search terms.
     */
    private fun extractUrlsOrSearchTerms(text: String): List<String> {
        val terms = mutableListOf<String>()
        
        // Extract URLs
        val urlRegex = Regex("(https?://[\\w\\-\\.]+\\.[a-z]{2,}[/\\w\\-\\._~:/?#\\[\\]@!$&'()*+,;=]*)")
        urlRegex.findAll(text).forEach { match ->
            terms.add(match.value)
        }
        
        // Extract search terms after common search keywords
        val searchRegex = Regex("(?:search for|find|look for|google)\\s+(.+)")
        searchRegex.find(text)?.let { match ->
            terms.add(match.groupValues[1].trim())
        }
        
        return terms
    }
    
    /**
     * Extract directional information.
     */
    private fun extractDirections(text: String): List<String> {
        return directionalKeywords.keys.filter { direction ->
            text.contains(direction)
        }
    }
    
    /**
     * Extract text references for reading or finding.
     */
    private fun extractTextReferences(text: String): List<String> {
        val references = mutableListOf<String>()
        
        // Extract quoted text
        val quotedRegex = Regex("\"([^\"]+)\"")
        quotedRegex.findAll(text).forEach { match ->
            references.add(match.groupValues[1])
        }
        
        // Extract text after "find" or "read"
        val textRegex = Regex("(?:find|read|locate)\\s+(.+)")
        textRegex.find(text)?.let { match ->
            references.add(match.groupValues[1].trim())
        }
        
        return references
    }
    
    /**
     * Extract target language for translation.
     */
    private fun extractLanguage(text: String): List<String> {
        val languages = mapOf(
            "spanish" to "es",
            "french" to "fr", 
            "german" to "de",
            "chinese" to "zh",
            "japanese" to "ja",
            "korean" to "ko",
            "italian" to "it",
            "portuguese" to "pt",
            "russian" to "ru",
            "arabic" to "ar"
        )
        
        return languages.keys.filter { language ->
            text.contains(language)
        }
    }
    
    /**
     * Extract general entities from text.
     */
    private fun extractGeneralEntities(text: String): List<String> {
        val entities = mutableListOf<String>()
        
        // Extract numbers
        val numberRegex = Regex("\\b\\d+\\b")
        numberRegex.findAll(text).forEach { match ->
            entities.add(match.value)
        }
        
        // Extract capitalized words (potential proper nouns)
        val capitalizedRegex = Regex("\\b[A-Z][a-z]+\\b")
        capitalizedRegex.findAll(text).forEach { match ->
            entities.add(match.value.lowercase())
        }
        
        return entities
    }
    
    /**
     * Extract additional parameters from the command.
     */
    private fun extractParameters(text: String, intent: CommandIntent): Map<String, String> {
        val parameters = mutableMapOf<String, String>()
        
        when (intent) {
            CommandIntent.SCROLL -> {
                // Extract scroll amount
                val amountRegex = Regex("(\\d+)\\s*(?:times|pixels|steps)")
                amountRegex.find(text)?.let { match ->
                    parameters["amount"] = match.groupValues[1]
                }
            }
            
            CommandIntent.FILL_FORM -> {
                // Extract field name and value pairs
                val fillRegex = Regex("fill\\s+(\\w+)\\s+(?:with|as)\\s+(.+)")
                fillRegex.find(text)?.let { match ->
                    parameters["field"] = match.groupValues[1]
                    parameters["value"] = match.groupValues[2]
                }
            }
            
            else -> {
                // No additional parameters needed
            }
        }
        
        return parameters
    }
    
    /**
     * Calculate confidence score for the processed command.
     */
    private fun calculateConfidence(
        text: String, 
        intent: CommandIntent, 
        entities: List<String>
    ): Float {
        var confidence = 0.0f
        
        // Base confidence from intent matching
        confidence += if (intent != CommandIntent.UNKNOWN) 0.5f else 0.0f
        
        // Boost confidence based on entity extraction
        confidence += entities.size * 0.1f
        
        // Boost confidence for clear, structured commands
        if (text.split(" ").size <= 5) {
            confidence += 0.2f // Short, clear commands
        }
        
        // Cap confidence at 1.0
        return minOf(confidence, 1.0f)
    }
    
    /**
     * Get human-readable description of the command.
     */
    fun getCommandDescription(command: VoiceCommand): String {
        return when (command.intent) {
            CommandIntent.CLICK -> "Click on ${command.entities.joinToString(", ")}"
            CommandIntent.NAVIGATE -> "Navigate to ${command.entities.joinToString(", ")}"
            CommandIntent.SEARCH -> "Search for ${command.entities.joinToString(", ")}"
            CommandIntent.FILL_FORM -> "Fill form field with ${command.entities.joinToString(", ")}"
            CommandIntent.SCROLL -> "Scroll ${command.entities.joinToString(", ")}"
            CommandIntent.READ -> "Read ${command.entities.joinToString(", ")}"
            CommandIntent.EXTRACT -> "Extract ${command.entities.joinToString(", ")}"
            else -> "Execute ${command.intent.name.lowercase()} command"
        }
    }
}
