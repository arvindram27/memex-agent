package com.memexagent.app

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import android.webkit.WebView
import com.memexagent.app.actions.BrowserActionController
import com.memexagent.app.ai.ContextualAI
import com.memexagent.app.context.ScreenContextManager
import com.memexagent.app.context.VisualContextProcessor
import com.memexagent.app.voice.VoiceIntentProcessor
import com.memexagent.app.whisper.WhisperService
import kotlinx.coroutines.*

/**
 * Central coordinator for the Voice-Controlled Browser Agent.
 * Integrates all components: Screen Context, OCR, Voice Processing, Browser Actions, and Contextual AI.
 */
class VoiceAgentCoordinator(
    private val activity: Activity,
    private val webView: WebView,
    private val whisperService: WhisperService
) {
    
    companion object {
        private const val TAG = "VoiceAgentCoordinator"
        private const val PROCESSING_TIMEOUT = 30_000L // 30 seconds
    }
    
    // Core components
    private val screenContextManager = ScreenContextManager(activity)
    private val visualContextProcessor = VisualContextProcessor()
    private val voiceIntentProcessor = VoiceIntentProcessor()
    private val browserActionController = BrowserActionController(webView)
    private val contextualAI = ContextualAI()
    
    // State management
    private var isProcessing = false
    private var currentPageContext: ContextualAI.PageContext? = null
    
    // Callbacks
    private var onCommandProcessed: ((String, Boolean) -> Unit)? = null
    private var onSuggestionGenerated: ((List<ContextualAI.SuggestedAction>) -> Unit)? = null
    private var onStatusUpdate: ((String) -> Unit)? = null
    
    /**
     * Initialize the voice agent coordinator.
     */
    suspend fun initialize(): Boolean {
        return try {
            Log.d(TAG, "Initializing Voice Agent Coordinator...")
            
            // Initialize screen capture if needed
            if (!screenContextManager.isScreenCaptureAvailable()) {
                onStatusUpdate?.invoke("Requesting screen capture permission...")
                screenContextManager.requestScreenCapturePermission(activity)
                // Note: Screen capture will be initialized in onActivityResult
            }
            
            // Initialize page context
            refreshPageContext()
            
            Log.d(TAG, "Voice Agent Coordinator initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Voice Agent Coordinator", e)
            false
        }
    }
    
    /**
     * Handle screen capture permission result.
     */
    fun handleScreenCaptureResult(resultCode: Int, data: Intent?): Boolean {
        return screenContextManager.initializeScreenCapture(resultCode, data)
    }
    
    /**
     * Process a voice command from start to finish.
     */
    suspend fun processVoiceCommand(audioData: ByteArray): ProcessingResult {
        if (isProcessing) {
            return ProcessingResult(
                success = false,
                message = "Already processing a command. Please wait.",
                command = null,
                executionResult = null
            )
        }
        
        isProcessing = true
        onStatusUpdate?.invoke("Processing voice command...")
        
        return try {
            withTimeout(PROCESSING_TIMEOUT) {
                processVoiceCommandInternal(audioData)
            }
        } catch (e: TimeoutCancellationException) {
            ProcessingResult(
                success = false,
                message = "Voice command processing timed out",
                command = null,
                executionResult = null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error processing voice command", e)
            ProcessingResult(
                success = false,
                message = "Error processing voice command: ${e.message}",
                command = null,
                executionResult = null
            )
        } finally {
            isProcessing = false
        }
    }
    
    /**
     * Internal voice command processing logic.
     */
    private suspend fun processVoiceCommandInternal(audioData: ByteArray): ProcessingResult {
        
        // Step 1: Transcribe audio using Whisper
        onStatusUpdate?.invoke("Transcribing audio...")
        val transcription = transcribeAudio(audioData)
        if (transcription.isNullOrEmpty()) {
            return ProcessingResult(
                success = false,
                message = "Could not transcribe audio. Please try again.",
                command = null,
                executionResult = null
            )
        }
        
        Log.d(TAG, "Transcribed: $transcription")
        
        // Step 2: Refresh page context
        onStatusUpdate?.invoke("Analyzing page context...")
        refreshPageContext()
        
        // Step 3: Process voice command and extract intent
        onStatusUpdate?.invoke("Processing voice intent...")
        val pageContext = currentPageContext
        val webPageContext = if (pageContext != null) {
            VisualContextProcessor.WebPageContext(
                visibleText = pageContext.visibleText,
                clickableElements = pageContext.clickableElements,
                formFields = pageContext.formFields,
                currentUrl = pageContext.currentUrl,
                pageTitle = pageContext.pageTitle,
                pageStructure = emptyMap()
            )
        } else null
        
        val voiceCommand = voiceIntentProcessor.processCommand(transcription, webPageContext)
        Log.d(TAG, "Processed voice command: Intent=${voiceCommand.intent}, Confidence=${voiceCommand.confidence}")
        
        // Step 4: Resolve ambiguous commands using contextual AI
        onStatusUpdate?.invoke("Resolving command...")
        val resolvedCommand = if (pageContext != null) {
            contextualAI.resolveAmbiguousCommand(voiceCommand, pageContext)
        } else {
            ContextualAI.ResolvedCommand(
                originalCommand = voiceCommand,
                resolvedIntent = voiceCommand.intent,
                targetElements = emptyList(),
                confidence = voiceCommand.confidence,
                reasoning = "No page context available"
            )
        }
        
        Log.d(TAG, "Resolved command: ${resolvedCommand.reasoning}")
        
        // Step 5: Execute the command
        onStatusUpdate?.invoke("Executing command...")
        val executionResult = browserActionController.executeCommand(
            resolvedCommand.originalCommand.copy(intent = resolvedCommand.resolvedIntent),
            webPageContext
        )
        
        // Step 6: Remember the action for learning
        pageContext?.let { context ->
            contextualAI.rememberAction(
                url = context.currentUrl,
                action = resolvedCommand.resolvedIntent,
                success = executionResult.success,
                context = resolvedCommand.reasoning
            )
        }
        
        // Step 7: Generate proactive suggestions
        pageContext?.let { context ->
            val suggestions = contextualAI.analyzePageForOpportunities(context)
            if (suggestions.isNotEmpty()) {
                onSuggestionGenerated?.invoke(suggestions)
            }
        }
        
        val result = ProcessingResult(
            success = executionResult.success,
            message = executionResult.message,
            command = resolvedCommand,
            executionResult = executionResult,
            suggestions = resolvedCommand.suggestions
        )
        
        // Notify completion
        onCommandProcessed?.invoke(transcription, executionResult.success)
        onStatusUpdate?.invoke(if (executionResult.success) "Command completed successfully" else "Command failed")
        
        return result
    }
    
    /**
     * Refresh the current page context by analyzing the web page.
     */
    suspend fun refreshPageContext() {
        try {
            Log.d(TAG, "Refreshing page context...")
            
            // Get screen capture if available
            val screenCapture = if (screenContextManager.isScreenCaptureAvailable()) {
                screenContextManager.captureScreen()
            } else null
            
            // Analyze web page elements
            val webPageContext = visualContextProcessor.buildComprehensiveContext(webView, screenCapture)
            
            // Extract OCR results if screen capture is available
            val ocrResults = screenCapture?.let { bitmap ->
                visualContextProcessor.extractTextFromScreen(bitmap)?.textBlocks ?: emptyList()
            } ?: emptyList()
            
            // Build comprehensive context using Contextual AI
            currentPageContext = contextualAI.buildContext(webPageContext, ocrResults)
            
            Log.d(TAG, "Page context refreshed: Type=${currentPageContext?.pageType}, Intent=${currentPageContext?.userIntent}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing page context", e)
            currentPageContext = null
        }
    }
    
    /**
     * Get proactive suggestions based on current page context.
     */
    fun getProactiveSuggestions(): List<ContextualAI.SuggestedAction> {
        return currentPageContext?.let { context ->
            contextualAI.analyzePageForOpportunities(context)
        } ?: emptyList()
    }
    
    /**
     * Execute a suggested action.
     */
    suspend fun executeSuggestion(suggestion: ContextualAI.SuggestedAction): BrowserActionController.ActionResult {
        val voiceCommand = VoiceIntentProcessor.VoiceCommand(
            intent = suggestion.action,
            entities = suggestion.elements.map { it.text },
            originalText = suggestion.voiceCommand,
            confidence = suggestion.confidence
        )
        
        val webPageContext = currentPageContext?.let { context ->
            VisualContextProcessor.WebPageContext(
                visibleText = context.visibleText,
                clickableElements = context.clickableElements,
                formFields = context.formFields,
                currentUrl = context.currentUrl,
                pageTitle = context.pageTitle,
                pageStructure = emptyMap()
            )
        }
        
        return browserActionController.executeCommand(voiceCommand, webPageContext)
    }
    
    /**
     * Get current page information.
     */
    fun getCurrentPageInfo(): PageInfo? {
        return currentPageContext?.let { context ->
            PageInfo(
                title = context.pageTitle,
                url = context.currentUrl,
                pageType = context.pageType.toString(),
                userIntent = context.userIntent.toString(),
                clickableElementsCount = context.clickableElements.size,
                formFieldsCount = context.formFields.size,
                hasScreenCapture = screenContextManager.isScreenCaptureAvailable()
            )
        }
    }
    
    /**
     * Get usage analytics.
     */
    fun getUsageAnalytics(): Map<String, Any> {
        return contextualAI.getUsagePatterns()
    }
    
    /**
     * Transcribe audio using Whisper service.
     */
    private suspend fun transcribeAudio(audioData: ByteArray): String? {
        return try {
            suspendCancellableCoroutine<String?> { continuation ->
                // Note: This will need to be implemented based on your WhisperService API
                // For now, return a mock transcription
                continuation.resumeWith(Result.success("test transcription"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error transcribing audio", e)
            null
        }
    }
    
    /**
     * Set callback for command processing completion.
     */
    fun setOnCommandProcessedCallback(callback: (String, Boolean) -> Unit) {
        onCommandProcessed = callback
    }
    
    /**
     * Set callback for suggestion generation.
     */
    fun setOnSuggestionGeneratedCallback(callback: (List<ContextualAI.SuggestedAction>) -> Unit) {
        onSuggestionGenerated = callback
    }
    
    /**
     * Set callback for status updates.
     */
    fun setOnStatusUpdateCallback(callback: (String) -> Unit) {
        onStatusUpdate = callback
    }
    
    /**
     * Cleanup resources.
     */
    fun cleanup() {
        try {
            screenContextManager.stopScreenCapture()
            visualContextProcessor.cleanup()
            Log.d(TAG, "Voice Agent Coordinator cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
    
    // Data classes for results
    data class ProcessingResult(
        val success: Boolean,
        val message: String,
        val command: ContextualAI.ResolvedCommand?,
        val executionResult: BrowserActionController.ActionResult?,
        val suggestions: List<String> = emptyList()
    )
    
    data class PageInfo(
        val title: String,
        val url: String,
        val pageType: String,
        val userIntent: String,
        val clickableElementsCount: Int,
        val formFieldsCount: Int,
        val hasScreenCapture: Boolean
    )
}
