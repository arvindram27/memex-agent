package com.memexagent.app.ai

import android.util.Log
import com.memexagent.app.context.VisualContextProcessor
import com.memexagent.app.voice.VoiceIntentProcessor
import kotlinx.coroutines.*
import java.util.*

/**
 * Contextual AI engine that combines OCR results with DOM analysis to create
 * semantic understanding of web pages and resolve ambiguous voice commands.
 */
class ContextualAI {
    
    companion object {
        private const val TAG = "ContextualAI"
        private const val MIN_CONFIDENCE_THRESHOLD = 0.6f
        private const val MAX_SUGGESTIONS = 5
    }
    
    data class PageContext(
        val visibleText: String,
        val clickableElements: List<VisualContextProcessor.PageElement>,
        val formFields: List<VisualContextProcessor.PageElement>,
        val currentUrl: String,
        val pageTitle: String,
        val pageType: PageType = PageType.UNKNOWN,
        val semanticElements: Map<String, List<String>> = emptyMap(),
        val userIntent: UserIntent = UserIntent.BROWSE
    )
    
    enum class PageType {
        SEARCH_ENGINE,
        E_COMMERCE,
        SOCIAL_MEDIA,
        NEWS_ARTICLE,
        FORM_PAGE,
        LOGIN_PAGE,
        HOMEPAGE,
        UNKNOWN
    }
    
    enum class UserIntent {
        BROWSE,
        SHOP,
        SEARCH,
        READ,
        INTERACT,
        NAVIGATE
    }
    
    data class ResolvedCommand(
        val originalCommand: VoiceIntentProcessor.VoiceCommand,
        val resolvedIntent: VoiceIntentProcessor.CommandIntent,
        val targetElements: List<VisualContextProcessor.PageElement>,
        val confidence: Float,
        val reasoning: String,
        val suggestions: List<String> = emptyList()
    )
    
    data class SuggestedAction(
        val action: VoiceIntentProcessor.CommandIntent,
        val description: String,
        val elements: List<VisualContextProcessor.PageElement> = emptyList(),
        val confidence: Float,
        val voiceCommand: String
    )
    
    private val browsingHistory = mutableListOf<BrowsingAction>()
    private val userPreferences = mutableMapOf<String, String>()
    
    data class BrowsingAction(
        val timestamp: Long,
        val url: String,
        val action: VoiceIntentProcessor.CommandIntent,
        val success: Boolean,
        val context: String
    )
    
    /**
     * Build comprehensive page context by analyzing visual and textual elements.
     */
    fun buildContext(
        webPageContext: VisualContextProcessor.WebPageContext,
        ocrResults: List<com.google.mlkit.vision.text.Text.TextBlock> = emptyList()
    ): PageContext {
        
        Log.d(TAG, "Building contextual understanding for page: ${webPageContext.pageTitle}")
        
        val pageType = analyzePageType(webPageContext)
        val userIntent = inferUserIntent(webPageContext, pageType)
        val semanticElements = extractSemanticElements(webPageContext)
        
        return PageContext(
            visibleText = webPageContext.visibleText,
            clickableElements = webPageContext.clickableElements,
            formFields = webPageContext.formFields,
            currentUrl = webPageContext.currentUrl,
            pageTitle = webPageContext.pageTitle,
            pageType = pageType,
            semanticElements = semanticElements,
            userIntent = userIntent
        )
    }
    
    /**
     * Resolve ambiguous voice commands using contextual understanding.
     */
    fun resolveAmbiguousCommand(
        command: VoiceIntentProcessor.VoiceCommand, 
        context: PageContext
    ): ResolvedCommand {
        
        Log.d(TAG, "Resolving command: '${command.originalText}' with confidence: ${command.confidence}")
        
        // If command is already confident enough, return as-is
        if (command.confidence >= MIN_CONFIDENCE_THRESHOLD) {
            val targetElements = findTargetElements(command, context)
            return ResolvedCommand(
                originalCommand = command,
                resolvedIntent = command.intent,
                targetElements = targetElements,
                confidence = command.confidence,
                reasoning = "Original command was confident enough"
            )
        }
        
        // Apply contextual resolution strategies
        val resolvedIntent = resolveIntent(command, context)
        val targetElements = findTargetElements(
            command.copy(intent = resolvedIntent), 
            context
        )
        
        val confidence = calculateResolvedConfidence(command, context, targetElements)
        val reasoning = generateReasoning(command, resolvedIntent, context, targetElements)
        val suggestions = generateSuggestions(command, context)
        
        return ResolvedCommand(
            originalCommand = command,
            resolvedIntent = resolvedIntent,
            targetElements = targetElements,
            confidence = confidence,
            reasoning = reasoning,
            suggestions = suggestions
        )
    }
    
    /**
     * Analyze the type of web page based on content and structure.
     */
    private fun analyzePageType(context: VisualContextProcessor.WebPageContext): PageType {
        val url = context.currentUrl.lowercase()
        val title = context.pageTitle.lowercase()
        val visibleText = context.visibleText.lowercase()
        
        return when {
            // Search engines
            url.contains("google.com/search") || 
            url.contains("bing.com/search") || 
            url.contains("duckduckgo.com") -> PageType.SEARCH_ENGINE
            
            // E-commerce indicators
            visibleText.contains("add to cart") ||
            visibleText.contains("buy now") ||
            visibleText.contains("$") ||
            visibleText.contains("price") ||
            url.contains("amazon") ||
            url.contains("shop") -> PageType.E_COMMERCE
            
            // Social media
            url.contains("facebook") ||
            url.contains("twitter") ||
            url.contains("instagram") ||
            url.contains("linkedin") -> PageType.SOCIAL_MEDIA
            
            // Login pages
            context.formFields.any { 
                it.attributes["type"] == "password" ||
                it.text.contains("password", true) ||
                it.text.contains("login", true)
            } -> PageType.LOGIN_PAGE
            
            // Forms
            context.formFields.size > 2 -> PageType.FORM_PAGE
            
            // News articles
            visibleText.contains("published") ||
            visibleText.contains("author") ||
            context.pageStructure["headings"]?.let { headings ->
                (headings as? List<*>)?.size ?: 0 > 3
            } == true -> PageType.NEWS_ARTICLE
            
            // Homepage indicators
            url.split("/").size <= 3 && 
            (title.contains("home") || url.endsWith(".com") || url.endsWith(".com/")) -> PageType.HOMEPAGE
            
            else -> PageType.UNKNOWN
        }
    }
    
    /**
     * Infer user intent based on page context and command history.
     */
    private fun inferUserIntent(
        context: VisualContextProcessor.WebPageContext, 
        pageType: PageType
    ): UserIntent {
        
        return when (pageType) {
            PageType.SEARCH_ENGINE -> UserIntent.SEARCH
            PageType.E_COMMERCE -> UserIntent.SHOP
            PageType.NEWS_ARTICLE -> UserIntent.READ
            PageType.FORM_PAGE, PageType.LOGIN_PAGE -> UserIntent.INTERACT
            else -> {
                // Analyze recent actions for pattern
                val recentActions = browsingHistory.takeLast(5)
                when {
                    recentActions.any { it.action == VoiceIntentProcessor.CommandIntent.SEARCH } -> UserIntent.SEARCH
                    recentActions.any { it.action == VoiceIntentProcessor.CommandIntent.READ } -> UserIntent.READ
                    recentActions.any { it.action == VoiceIntentProcessor.CommandIntent.FILL_FORM } -> UserIntent.INTERACT
                    else -> UserIntent.BROWSE
                }
            }
        }
    }
    
    /**
     * Extract semantic elements from page context.
     */
    private fun extractSemanticElements(context: VisualContextProcessor.WebPageContext): Map<String, List<String>> {
        val elements = mutableMapOf<String, MutableList<String>>()
        
        // Extract navigation elements
        val navElements = context.clickableElements.filter { element ->
            element.text.lowercase().let { text ->
                text.contains("home") || text.contains("menu") || text.contains("nav") ||
                text.contains("back") || text.contains("next") || text.contains("previous")
            }
        }.map { it.text }
        if (navElements.isNotEmpty()) {
            elements["navigation"] = navElements.toMutableList()
        }
        
        // Extract action elements
        val actionElements = context.clickableElements.filter { element ->
            element.text.lowercase().let { text ->
                text.contains("buy") || text.contains("add") || text.contains("submit") ||
                text.contains("save") || text.contains("send") || text.contains("search")
            }
        }.map { it.text }
        if (actionElements.isNotEmpty()) {
            elements["actions"] = actionElements.toMutableList()
        }
        
        // Extract content areas
        val contentKeywords = mutableListOf<String>()
        context.visibleText.split(" ").forEach { word ->
            if (word.length > 4 && word.matches(Regex("[a-zA-Z]+"))) {
                contentKeywords.add(word.lowercase())
            }
        }
        if (contentKeywords.isNotEmpty()) {
            elements["keywords"] = contentKeywords.take(20).toMutableList()
        }
        
        // Extract form-related elements
        val formElements = context.formFields.map { element ->
            element.attributes["placeholder"] ?: element.attributes["name"] ?: element.text
        }.filter { it.isNotEmpty() }
        if (formElements.isNotEmpty()) {
            elements["forms"] = formElements.toMutableList()
        }
        
        return elements
    }
    
    /**
     * Resolve command intent using contextual clues.
     */
    private fun resolveIntent(
        command: VoiceIntentProcessor.VoiceCommand, 
        context: PageContext
    ): VoiceIntentProcessor.CommandIntent {
        
        val originalText = command.originalText.lowercase()
        
        // Context-based intent resolution
        when (context.pageType) {
            PageType.SEARCH_ENGINE -> {
                if (originalText.contains("click") && command.entities.isNotEmpty()) {
                    return VoiceIntentProcessor.CommandIntent.CLICK
                }
                if (originalText.contains("search") || originalText.contains("find")) {
                    return VoiceIntentProcessor.CommandIntent.SEARCH
                }
            }
            
            PageType.E_COMMERCE -> {
                if (originalText.contains("buy") || originalText.contains("purchase") || 
                    originalText.contains("add to cart")) {
                    return VoiceIntentProcessor.CommandIntent.CLICK
                }
                if (originalText.contains("price") || originalText.contains("cost")) {
                    return VoiceIntentProcessor.CommandIntent.EXTRACT
                }
            }
            
            PageType.LOGIN_PAGE, PageType.FORM_PAGE -> {
                if (originalText.contains("fill") || originalText.contains("enter") || 
                    originalText.contains("type")) {
                    return VoiceIntentProcessor.CommandIntent.FILL_FORM
                }
                if (originalText.contains("submit") || originalText.contains("send") || 
                    originalText.contains("login")) {
                    return VoiceIntentProcessor.CommandIntent.SUBMIT_FORM
                }
            }
            
            PageType.NEWS_ARTICLE -> {
                if (originalText.contains("read") || originalText.contains("tell me")) {
                    return VoiceIntentProcessor.CommandIntent.READ
                }
            }
            
            else -> {
                // Use original intent if no context-specific override
            }
        }
        
        // If no context-specific resolution, use original intent
        return command.intent
    }
    
    /**
     * Find target elements for a command based on entities and context.
     */
    private fun findTargetElements(
        command: VoiceIntentProcessor.VoiceCommand, 
        context: PageContext
    ): List<VisualContextProcessor.PageElement> {
        
        val allElements = context.clickableElements + context.formFields
        val entities = command.entities
        
        if (entities.isEmpty()) return emptyList()
        
        val matches = mutableListOf<VisualContextProcessor.PageElement>()
        
        for (entity in entities) {
            val entityLower = entity.lowercase()
            
            // Exact text matches
            allElements.filter { element ->
                element.text.lowercase().contains(entityLower)
            }.let { matches.addAll(it) }
            
            // Attribute matches
            allElements.filter { element ->
                element.attributes.values.any { value ->
                    value.lowercase().contains(entityLower)
                }
            }.let { matches.addAll(it) }
            
            // Semantic matches based on page context
            when (command.intent) {
                VoiceIntentProcessor.CommandIntent.FILL_FORM -> {
                    context.formFields.filter { field ->
                        val fieldType = field.attributes["type"] ?: ""
                        val fieldName = field.attributes["name"] ?: ""
                        val placeholder = field.attributes["placeholder"] ?: ""
                        
                        when (entityLower) {
                            "email" -> fieldType == "email" || fieldName.contains("email") || placeholder.contains("email")
                            "password" -> fieldType == "password"
                            "search" -> fieldType == "search" || fieldName.contains("search") || placeholder.contains("search")
                            else -> fieldName.contains(entityLower) || placeholder.contains(entityLower)
                        }
                    }.let { matches.addAll(it) }
                }
                
                VoiceIntentProcessor.CommandIntent.CLICK -> {
                    // Prioritize buttons and links for click commands
                    context.clickableElements.filter { element ->
                        element.type == VisualContextProcessor.ElementType.BUTTON ||
                        element.type == VisualContextProcessor.ElementType.LINK
                    }.filter { element ->
                        element.text.lowercase().contains(entityLower)
                    }.let { matches.addAll(it) }
                }
                
                else -> {
                    // General matching already done above
                }
            }
        }
        
        return matches.distinctBy { it.text + it.selector }.take(3)
    }
    
    /**
     * Calculate confidence for resolved command.
     */
    private fun calculateResolvedConfidence(
        originalCommand: VoiceIntentProcessor.VoiceCommand,
        context: PageContext,
        targetElements: List<VisualContextProcessor.PageElement>
    ): Float {
        
        var confidence = originalCommand.confidence
        
        // Boost confidence based on context relevance
        when (context.pageType) {
            PageType.SEARCH_ENGINE -> {
                if (originalCommand.intent == VoiceIntentProcessor.CommandIntent.SEARCH ||
                    originalCommand.intent == VoiceIntentProcessor.CommandIntent.CLICK) {
                    confidence += 0.2f
                }
            }
            PageType.E_COMMERCE -> {
                if (originalCommand.intent == VoiceIntentProcessor.CommandIntent.CLICK ||
                    originalCommand.intent == VoiceIntentProcessor.CommandIntent.EXTRACT) {
                    confidence += 0.2f
                }
            }
            PageType.FORM_PAGE, PageType.LOGIN_PAGE -> {
                if (originalCommand.intent == VoiceIntentProcessor.CommandIntent.FILL_FORM ||
                    originalCommand.intent == VoiceIntentProcessor.CommandIntent.SUBMIT_FORM) {
                    confidence += 0.3f
                }
            }
            else -> {
                // No specific boost
            }
        }
        
        // Boost confidence based on target element matches
        if (targetElements.isNotEmpty()) {
            confidence += 0.1f * targetElements.size
        }
        
        // Reduce confidence if no clear targets found
        if (targetElements.isEmpty() && 
            (originalCommand.intent == VoiceIntentProcessor.CommandIntent.CLICK ||
             originalCommand.intent == VoiceIntentProcessor.CommandIntent.FILL_FORM)) {
            confidence -= 0.2f
        }
        
        return minOf(confidence, 1.0f)
    }
    
    /**
     * Generate reasoning explanation for the command resolution.
     */
    private fun generateReasoning(
        command: VoiceIntentProcessor.VoiceCommand,
        resolvedIntent: VoiceIntentProcessor.CommandIntent,
        context: PageContext,
        targetElements: List<VisualContextProcessor.PageElement>
    ): String {
        
        val reasons = mutableListOf<String>()
        
        if (command.intent != resolvedIntent) {
            reasons.add("Intent changed from ${command.intent} to $resolvedIntent based on page context (${context.pageType})")
        }
        
        if (targetElements.isNotEmpty()) {
            reasons.add("Found ${targetElements.size} matching elements: ${targetElements.take(2).map { it.text }.joinToString(", ")}")
        } else if (command.intent == VoiceIntentProcessor.CommandIntent.CLICK || 
                   command.intent == VoiceIntentProcessor.CommandIntent.FILL_FORM) {
            reasons.add("No clear target elements found for entities: ${command.entities.joinToString(", ")}")
        }
        
        if (context.userIntent != UserIntent.BROWSE) {
            reasons.add("User intent inferred as: ${context.userIntent}")
        }
        
        return reasons.joinToString("; ").takeIf { it.isNotEmpty() } ?: "Standard command processing"
    }
    
    /**
     * Generate alternative suggestions for ambiguous commands.
     */
    private fun generateSuggestions(
        command: VoiceIntentProcessor.VoiceCommand,
        context: PageContext
    ): List<String> {
        
        val suggestions = mutableListOf<String>()
        
        when (context.pageType) {
            PageType.SEARCH_ENGINE -> {
                suggestions.add("Try: 'click the first result'")
                suggestions.add("Try: 'search for [your query]'")
                suggestions.add("Try: 'click next page'")
            }
            
            PageType.E_COMMERCE -> {
                suggestions.add("Try: 'add to cart'")
                suggestions.add("Try: 'show me the price'")
                suggestions.add("Try: 'read product details'")
            }
            
            PageType.LOGIN_PAGE -> {
                suggestions.add("Try: 'fill email with [your email]'")
                suggestions.add("Try: 'fill password'")
                suggestions.add("Try: 'submit form'")
            }
            
            PageType.NEWS_ARTICLE -> {
                suggestions.add("Try: 'read this article'")
                suggestions.add("Try: 'scroll down'")
                suggestions.add("Try: 'go back'")
            }
            
            else -> {
                // Generic suggestions based on available elements
                if (context.clickableElements.isNotEmpty()) {
                    val topButtons = context.clickableElements.take(3)
                    topButtons.forEach { element ->
                        if (element.text.isNotEmpty()) {
                            suggestions.add("Try: 'click ${element.text.take(20)}'")
                        }
                    }
                }
            }
        }
        
        return suggestions.take(MAX_SUGGESTIONS)
    }
    
    /**
     * Analyze page for proactive assistance opportunities.
     */
    fun analyzePageForOpportunities(context: PageContext): List<SuggestedAction> {
        val suggestions = mutableListOf<SuggestedAction>()
        
        when (context.pageType) {
            PageType.SEARCH_ENGINE -> {
                if (context.clickableElements.isNotEmpty()) {
                    suggestions.add(
                        SuggestedAction(
                            action = VoiceIntentProcessor.CommandIntent.CLICK,
                            description = "Click on the first search result",
                            elements = context.clickableElements.take(1),
                            confidence = 0.8f,
                            voiceCommand = "click first result"
                        )
                    )
                }
            }
            
            PageType.FORM_PAGE -> {
                val emptyFields = context.formFields.filter { field ->
                    field.attributes["value"]?.isEmpty() != false
                }
                if (emptyFields.isNotEmpty()) {
                    suggestions.add(
                        SuggestedAction(
                            action = VoiceIntentProcessor.CommandIntent.FILL_FORM,
                            description = "Fill out the form fields",
                            elements = emptyFields,
                            confidence = 0.9f,
                            voiceCommand = "fill out form"
                        )
                    )
                }
            }
            
            PageType.E_COMMERCE -> {
                val addToCartButtons = context.clickableElements.filter { element ->
                    element.text.lowercase().contains("add to cart") ||
                    element.text.lowercase().contains("buy")
                }
                if (addToCartButtons.isNotEmpty()) {
                    suggestions.add(
                        SuggestedAction(
                            action = VoiceIntentProcessor.CommandIntent.CLICK,
                            description = "Add item to cart",
                            elements = addToCartButtons.take(1),
                            confidence = 0.85f,
                            voiceCommand = "add to cart"
                        )
                    )
                }
            }
            
            else -> {
                // Generic suggestions
                if (context.visibleText.length > 500) {
                    suggestions.add(
                        SuggestedAction(
                            action = VoiceIntentProcessor.CommandIntent.READ,
                            description = "Read page content aloud",
                            confidence = 0.7f,
                            voiceCommand = "read this page"
                        )
                    )
                }
            }
        }
        
        return suggestions.take(3)
    }
    
    /**
     * Remember user action for learning.
     */
    fun rememberAction(
        url: String,
        action: VoiceIntentProcessor.CommandIntent,
        success: Boolean,
        context: String = ""
    ) {
        val browsingAction = BrowsingAction(
            timestamp = System.currentTimeMillis(),
            url = url,
            action = action,
            success = success,
            context = context
        )
        
        browsingHistory.add(browsingAction)
        
        // Keep only recent history
        if (browsingHistory.size > 100) {
            browsingHistory.removeAt(0)
        }
        
        Log.d(TAG, "Remembered action: $action on $url (success: $success)")
    }
    
    /**
     * Get usage patterns for adaptive behavior.
     */
    fun getUsagePatterns(): Map<String, Any> {
        val patterns = mutableMapOf<String, Any>()
        
        // Most common actions
        val actionCounts = browsingHistory.groupBy { it.action }.mapValues { it.value.size }
        patterns["most_used_actions"] = actionCounts.entries.sortedByDescending { it.value }.take(5)
        
        // Success rates
        val successRates = browsingHistory.groupBy { it.action }.mapValues { entries ->
            val total = entries.value.size
            val successful = entries.value.count { it.success }
            if (total > 0) successful.toFloat() / total else 0f
        }
        patterns["success_rates"] = successRates
        
        // Most visited domains
        val domainCounts = browsingHistory.map { action ->
            try {
                java.net.URI(action.url).host ?: "unknown"
            } catch (e: Exception) {
                "unknown"
            }
        }.groupBy { it }.mapValues { it.value.size }
        patterns["frequent_domains"] = domainCounts.entries.sortedByDescending { it.value }.take(5)
        
        return patterns
    }
}
