package com.memexagent.app.actions

import android.util.Log
import android.webkit.WebView
import android.webkit.ValueCallback
import com.memexagent.app.context.VisualContextProcessor
import com.memexagent.app.voice.VoiceIntentProcessor
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Browser Action Controller that executes voice commands by interacting with WebView
 * through JavaScript injection and DOM manipulation.
 */
class BrowserActionController(private val webView: WebView) {
    
    companion object {
        private const val TAG = "BrowserActionController"
        private const val SCROLL_ANIMATION_DURATION = 300 // milliseconds
        private const val CLICK_HIGHLIGHT_DURATION = 500 // milliseconds
    }
    
    data class ActionResult(
        val success: Boolean,
        val message: String,
        val data: Map<String, Any> = emptyMap()
    )
    
    /**
     * Execute a voice command using the current page context.
     */
    suspend fun executeCommand(
        command: VoiceIntentProcessor.VoiceCommand,
        pageContext: VisualContextProcessor.WebPageContext? = null
    ): ActionResult {
        
        Log.d(TAG, "Executing command: ${command.intent} with entities: ${command.entities}")
        
        return try {
            when (command.intent) {
                VoiceIntentProcessor.CommandIntent.CLICK -> handleClick(command, pageContext)
                VoiceIntentProcessor.CommandIntent.SCROLL -> handleScroll(command)
                VoiceIntentProcessor.CommandIntent.NAVIGATE -> handleNavigation(command)
                VoiceIntentProcessor.CommandIntent.FILL_FORM -> handleFormFilling(command, pageContext)
                VoiceIntentProcessor.CommandIntent.SUBMIT_FORM -> handleFormSubmission(command)
                VoiceIntentProcessor.CommandIntent.SEARCH -> handleSearch(command)
                VoiceIntentProcessor.CommandIntent.READ -> handleRead(command, pageContext)
                VoiceIntentProcessor.CommandIntent.EXTRACT -> handleExtract(command, pageContext)
                VoiceIntentProcessor.CommandIntent.FIND_TEXT -> handleFindText(command)
                VoiceIntentProcessor.CommandIntent.GO_BACK -> handleGoBack()
                VoiceIntentProcessor.CommandIntent.GO_FORWARD -> handleGoForward()
                VoiceIntentProcessor.CommandIntent.REFRESH -> handleRefresh()
                VoiceIntentProcessor.CommandIntent.SCREENSHOT -> handleScreenshot()
                else -> ActionResult(false, "Unsupported command: ${command.intent}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing command: ${command.intent}", e)
            ActionResult(false, "Failed to execute command: ${e.message}")
        }
    }
    
    /**
     * Handle click commands by finding and clicking elements.
     */
    private suspend fun handleClick(
        command: VoiceIntentProcessor.VoiceCommand,
        pageContext: VisualContextProcessor.WebPageContext?
    ): ActionResult = suspendCoroutine { continuation ->
        
        val entities = command.entities
        if (entities.isEmpty()) {
            continuation.resume(ActionResult(false, "No target specified for click command"))
            return@suspendCoroutine
        }
        
        // Generate JavaScript to find and click the target element
        val jsCode = generateClickScript(entities, pageContext)
        
        webView.evaluateJavascript(jsCode) { result ->
            val success = result == "true"
            val message = if (success) {
                "Successfully clicked on ${entities.joinToString(", ")}"
            } else {
                "Could not find or click target: ${entities.joinToString(", ")}"
            }
            continuation.resume(ActionResult(success, message))
        }
    }
    
    /**
     * Generate JavaScript code to find and click elements based on entities.
     */
    private fun generateClickScript(
        entities: List<String>,
        pageContext: VisualContextProcessor.WebPageContext?
    ): String {
        return """
            (function() {
                const entities = [${entities.joinToString(", ") { "\"$it\"" }}];
                
                // Function to highlight element before clicking
                function highlightElement(element) {
                    const originalStyle = element.style.cssText;
                    element.style.cssText += 'outline: 3px solid #ff4444; background-color: rgba(255, 68, 68, 0.1);';
                    setTimeout(() => {
                        element.style.cssText = originalStyle;
                    }, $CLICK_HIGHLIGHT_DURATION);
                }
                
                // Try different strategies to find the element
                for (const entity of entities) {
                    let element = null;
                    
                    // Strategy 1: Find by exact text content
                    const allElements = document.querySelectorAll('a, button, [onclick], [role="button"], input[type="submit"], input[type="button"]');
                    for (const el of allElements) {
                        if (el.textContent && el.textContent.toLowerCase().includes(entity.toLowerCase())) {
                            element = el;
                            break;
                        }
                    }
                    
                    // Strategy 2: Find by attributes (aria-label, title, alt)
                    if (!element) {
                        element = document.querySelector('[aria-label*="' + entity + '" i], [title*="' + entity + '" i], [alt*="' + entity + '" i]');
                    }
                    
                    // Strategy 3: Find by ID or class containing the entity
                    if (!element) {
                        element = document.querySelector('#' + entity + ', .' + entity + ', [id*="' + entity + '"], [class*="' + entity + '"]');
                    }
                    
                    // Strategy 4: Positional matching (first, last, etc.)
                    if (!element && (entity === 'first' || entity === 'last' || entity === 'next' || entity === 'previous')) {
                        const clickableElements = Array.from(allElements).filter(el => 
                            el.offsetParent !== null && 
                            window.getComputedStyle(el).visibility !== 'hidden'
                        );
                        
                        if (entity === 'first' && clickableElements.length > 0) {
                            element = clickableElements[0];
                        } else if (entity === 'last' && clickableElements.length > 0) {
                            element = clickableElements[clickableElements.length - 1];
                        }
                    }
                    
                    // Strategy 5: Color-based matching (red button, blue link, etc.)
                    if (!element && entity.includes(' ')) {
                        const parts = entity.split(' ');
                        if (parts.length >= 2) {
                            const color = parts[0];
                            const elementType = parts[1];
                            
                            const selector = elementType === 'button' ? 'button, [role="button"], input[type="submit"], input[type="button"]' :
                                           elementType === 'link' ? 'a' :
                                           '*';
                            
                            const elements = document.querySelectorAll(selector);
                            for (const el of elements) {
                                const style = window.getComputedStyle(el);
                                const bgColor = style.backgroundColor;
                                const textColor = style.color;
                                const borderColor = style.borderColor;
                                
                                if (bgColor.includes(color) || textColor.includes(color) || borderColor.includes(color) ||
                                    el.className.toLowerCase().includes(color) || el.style.cssText.toLowerCase().includes(color)) {
                                    element = el;
                                    break;
                                }
                            }
                        }
                    }
                    
                    if (element && element.offsetParent !== null) {
                        highlightElement(element);
                        
                        // Scroll element into view if necessary
                        element.scrollIntoView({ behavior: 'smooth', block: 'center' });
                        
                        // Click the element after a short delay for scroll animation
                        setTimeout(() => {
                            try {
                                element.click();
                                console.log('Clicked element:', element);
                            } catch (e) {
                                console.error('Click failed:', e);
                            }
                        }, 300);
                        
                        return true;
                    }
                }
                
                return false;
            })();
        """.trimIndent()
    }
    
    /**
     * Handle scroll commands with direction and amount.
     */
    private suspend fun handleScroll(command: VoiceIntentProcessor.VoiceCommand): ActionResult = suspendCoroutine { continuation ->
        
        val entities = command.entities
        val scrollDirection = when {
            entities.any { it.contains("up") || it.contains("top") } -> "up"
            entities.any { it.contains("down") || it.contains("bottom") } -> "down"
            entities.any { it.contains("left") } -> "left"
            entities.any { it.contains("right") } -> "right"
            else -> "down" // default
        }
        
        val scrollAmount = command.parameters["amount"]?.toIntOrNull() ?: 300
        
        val jsCode = """
            (function() {
                const direction = '$scrollDirection';
                const amount = $scrollAmount;
                
                let scrollOptions = { behavior: 'smooth' };
                
                switch (direction) {
                    case 'up':
                        window.scrollBy({ top: -amount, ...scrollOptions });
                        break;
                    case 'down':
                        window.scrollBy({ top: amount, ...scrollOptions });
                        break;
                    case 'left':
                        window.scrollBy({ left: -amount, ...scrollOptions });
                        break;
                    case 'right':
                        window.scrollBy({ left: amount, ...scrollOptions });
                        break;
                }
                
                return true;
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(jsCode) { result ->
            continuation.resume(ActionResult(true, "Scrolled $scrollDirection by $scrollAmount pixels"))
        }
    }
    
    /**
     * Handle navigation commands.
     */
    private suspend fun handleNavigation(command: VoiceIntentProcessor.VoiceCommand): ActionResult {
        val entities = command.entities
        if (entities.isEmpty()) {
            return ActionResult(false, "No URL specified for navigation")
        }
        
        val url = entities.first().let { entity ->
            when {
                entity.startsWith("http://") || entity.startsWith("https://") -> entity
                entity.contains(".") -> "https://$entity"
                else -> "https://www.google.com/search?q=${entity.replace(" ", "+")}"
            }
        }
        
        return withContext(Dispatchers.Main) {
            try {
                webView.loadUrl(url)
                ActionResult(true, "Navigating to $url")
            } catch (e: Exception) {
                ActionResult(false, "Failed to navigate: ${e.message}")
            }
        }
    }
    
    /**
     * Handle form filling commands.
     */
    private suspend fun handleFormFilling(
        command: VoiceIntentProcessor.VoiceCommand,
        pageContext: VisualContextProcessor.WebPageContext?
    ): ActionResult = suspendCoroutine { continuation ->
        
        val fieldName = command.parameters["field"] ?: command.entities.firstOrNull()
        val value = command.parameters["value"] ?: command.entities.getOrNull(1)
        
        if (fieldName == null || value == null) {
            continuation.resume(ActionResult(false, "Missing field name or value for form filling"))
            return@suspendCoroutine
        }
        
        val jsCode = """
            (function() {
                const fieldName = '$fieldName'.toLowerCase();
                const value = '$value';
                
                // Find form fields by various attributes
                const selectors = [
                    `input[name*="\${fieldName}"]`,
                    `input[id*="\${fieldName}"]`,
                    `input[placeholder*="\${fieldName}"]`,
                    `input[aria-label*="\${fieldName}"]`,
                    `textarea[name*="\${fieldName}"]`,
                    `textarea[id*="\${fieldName}"]`,
                    `textarea[placeholder*="\${fieldName}"]`,
                    'input[type="email"]',
                    'input[type="password"]',
                    'input[type="text"]',
                    'input[type="search"]'
                ];
                
                let field = null;
                
                // Try each selector
                for (const selector of selectors) {
                    field = document.querySelector(selector);
                    if (field && field.offsetParent !== null) {
                        break;
                    }
                }
                
                // Special handling for common field types
                if (!field) {
                    if (fieldName.includes('email')) {
                        field = document.querySelector('input[type="email"]') || 
                               document.querySelector('input[name*="email"]') ||
                               document.querySelector('input[id*="email"]');
                    } else if (fieldName.includes('password')) {
                        field = document.querySelector('input[type="password"]');
                    } else if (fieldName.includes('search')) {
                        field = document.querySelector('input[type="search"]') ||
                               document.querySelector('input[name*="search"]') ||
                               document.querySelector('input[placeholder*="search"]');
                    }
                }
                
                if (field) {
                    // Focus the field
                    field.focus();
                    
                    // Clear existing value
                    field.value = '';
                    
                    // Set the new value
                    field.value = value;
                    
                    // Trigger input events to ensure form validation
                    field.dispatchEvent(new Event('input', { bubbles: true }));
                    field.dispatchEvent(new Event('change', { bubbles: true }));
                    
                    // Highlight the field briefly
                    const originalStyle = field.style.cssText;
                    field.style.cssText += 'outline: 2px solid #4CAF50; background-color: rgba(76, 175, 80, 0.1);';
                    setTimeout(() => {
                        field.style.cssText = originalStyle;
                    }, 1000);
                    
                    return true;
                }
                
                return false;
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(jsCode) { result ->
            val success = result == "true"
            val message = if (success) {
                "Successfully filled $fieldName with value"
            } else {
                "Could not find form field: $fieldName"
            }
            continuation.resume(ActionResult(success, message))
        }
    }
    
    /**
     * Handle form submission.
     */
    private suspend fun handleFormSubmission(command: VoiceIntentProcessor.VoiceCommand): ActionResult = suspendCoroutine { continuation ->
        
        val jsCode = """
            (function() {
                // Try to find submit button or form to submit
                let submitButton = document.querySelector('input[type="submit"], button[type="submit"], button:contains("Submit"), button:contains("Send"), button:contains("Login"), button:contains("Sign")');
                
                if (!submitButton) {
                    // Look for forms and submit the first one found
                    const form = document.querySelector('form');
                    if (form) {
                        form.submit();
                        return true;
                    }
                }
                
                if (submitButton && submitButton.offsetParent !== null) {
                    submitButton.click();
                    return true;
                }
                
                return false;
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(jsCode) { result ->
            val success = result == "true"
            val message = if (success) {
                "Form submitted successfully"
            } else {
                "Could not find submit button or form"
            }
            continuation.resume(ActionResult(success, message))
        }
    }
    
    /**
     * Handle search commands.
     */
    private suspend fun handleSearch(command: VoiceIntentProcessor.VoiceCommand): ActionResult {
        val searchTerm = command.entities.joinToString(" ")
        if (searchTerm.isEmpty()) {
            return ActionResult(false, "No search term specified")
        }
        
        val searchUrl = "https://www.google.com/search?q=${searchTerm.replace(" ", "+")}"
        return handleNavigation(
            VoiceIntentProcessor.VoiceCommand(
                VoiceIntentProcessor.CommandIntent.NAVIGATE,
                listOf(searchUrl),
                command.originalText
            )
        )
    }
    
    /**
     * Handle read commands - extract and return text content.
     */
    private suspend fun handleRead(
        command: VoiceIntentProcessor.VoiceCommand,
        pageContext: VisualContextProcessor.WebPageContext?
    ): ActionResult = suspendCoroutine { continuation ->
        
        val jsCode = """
            (function() {
                // Extract main content text
                const contentSelectors = ['main', 'article', '.content', '#content', 'body'];
                let content = '';
                
                for (const selector of contentSelectors) {
                    const element = document.querySelector(selector);
                    if (element) {
                        content = element.innerText || element.textContent;
                        break;
                    }
                }
                
                // Fallback to page title and first paragraph
                if (!content) {
                    const title = document.title;
                    const firstParagraph = document.querySelector('p');
                    content = title + (firstParagraph ? '\n\n' + firstParagraph.textContent : '');
                }
                
                return content.substring(0, 1000); // Limit content length
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(jsCode) { result ->
            val content = result?.removeSurrounding("\"")?.replace("\\n", "\n") ?: ""
            val message = if (content.isNotEmpty()) {
                "Page content: $content"
            } else {
                "Could not extract readable content from page"
            }
            continuation.resume(ActionResult(content.isNotEmpty(), message, mapOf("content" to content)))
        }
    }
    
    /**
     * Handle extract commands - extract specific information.
     */
    private suspend fun handleExtract(
        command: VoiceIntentProcessor.VoiceCommand,
        pageContext: VisualContextProcessor.WebPageContext?
    ): ActionResult = suspendCoroutine { continuation ->
        
        val target = command.entities.joinToString(" ")
        
        val jsCode = """
            (function() {
                const target = '$target'.toLowerCase();
                const extracted = [];
                
                // Extract based on target type
                if (target.includes('price') || target.includes('cost') || target.includes('$')) {
                    const priceElements = document.querySelectorAll('*');
                    for (const el of priceElements) {
                        const text = el.textContent || '';
                        if (text.match(/\$[\d,]+\.?\d*/g)) {
                            extracted.push(...text.match(/\$[\d,]+\.?\d*/g));
                        }
                    }
                } else if (target.includes('email')) {
                    const emailRegex = /[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}/g;
                    const pageText = document.body.textContent || '';
                    extracted.push(...(pageText.match(emailRegex) || []));
                } else if (target.includes('phone')) {
                    const phoneRegex = /(?:\+?1[-.\s]?)?\(?[0-9]{3}\)?[-.\s]?[0-9]{3}[-.\s]?[0-9]{4}/g;
                    const pageText = document.body.textContent || '';
                    extracted.push(...(pageText.match(phoneRegex) || []));
                } else {
                    // General text extraction
                    const elements = document.querySelectorAll('*');
                    for (const el of elements) {
                        if (el.textContent && el.textContent.toLowerCase().includes(target)) {
                            extracted.push(el.textContent.trim().substring(0, 200));
                        }
                    }
                }
                
                return extracted.slice(0, 10); // Limit results
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(jsCode) { result ->
            try {
                val extractedData = result?.removeSurrounding("\"") ?: "[]"
                val message = if (extractedData != "[]") {
                    "Extracted: $extractedData"
                } else {
                    "Could not find $target on the page"
                }
                continuation.resume(ActionResult(extractedData != "[]", message, mapOf("extracted" to extractedData)))
            } catch (e: Exception) {
                continuation.resume(ActionResult(false, "Failed to extract data: ${e.message}"))
            }
        }
    }
    
    /**
     * Handle find text commands.
     */
    private suspend fun handleFindText(command: VoiceIntentProcessor.VoiceCommand): ActionResult = suspendCoroutine { continuation ->
        
        val searchText = command.entities.joinToString(" ")
        if (searchText.isEmpty()) {
            continuation.resume(ActionResult(false, "No text specified to find"))
            return@suspendCoroutine
        }
        
        val jsCode = """
            (function() {
                const searchText = '$searchText';
                
                // Remove any existing highlights
                const existingHighlights = document.querySelectorAll('.voice-agent-highlight');
                existingHighlights.forEach(el => {
                    el.outerHTML = el.innerHTML;
                });
                
                // Create a TreeWalker to find text nodes
                const walker = document.createTreeWalker(
                    document.body,
                    NodeFilter.SHOW_TEXT,
                    null,
                    false
                );
                
                let found = false;
                let node;
                while (node = walker.nextNode()) {
                    const text = node.textContent;
                    if (text.toLowerCase().includes(searchText.toLowerCase())) {
                        const parent = node.parentElement;
                        if (parent && parent.offsetParent !== null) {
                            // Highlight the text
                            const highlightedText = text.replace(
                                new RegExp(searchText, 'gi'),
                                '<span class="voice-agent-highlight" style="background-color: yellow; padding: 2px; border-radius: 2px;">$&</span>'
                            );
                            parent.innerHTML = parent.innerHTML.replace(text, highlightedText);
                            
                            // Scroll to the highlighted text
                            const highlight = parent.querySelector('.voice-agent-highlight');
                            if (highlight) {
                                highlight.scrollIntoView({ behavior: 'smooth', block: 'center' });
                            }
                            
                            found = true;
                            break;
                        }
                    }
                }
                
                return found;
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(jsCode) { result ->
            val found = result == "true"
            val message = if (found) {
                "Found and highlighted: $searchText"
            } else {
                "Could not find text: $searchText"
            }
            continuation.resume(ActionResult(found, message))
        }
    }
    
    /**
     * Handle go back command.
     */
    private suspend fun handleGoBack(): ActionResult {
        return withContext(Dispatchers.Main) {
            try {
                if (webView.canGoBack()) {
                    webView.goBack()
                    ActionResult(true, "Navigated back")
                } else {
                    ActionResult(false, "Cannot go back - no previous page")
                }
            } catch (e: Exception) {
                ActionResult(false, "Failed to go back: ${e.message}")
            }
        }
    }
    
    /**
     * Handle go forward command.
     */
    private suspend fun handleGoForward(): ActionResult {
        return withContext(Dispatchers.Main) {
            try {
                if (webView.canGoForward()) {
                    webView.goForward()
                    ActionResult(true, "Navigated forward")
                } else {
                    ActionResult(false, "Cannot go forward - no next page")
                }
            } catch (e: Exception) {
                ActionResult(false, "Failed to go forward: ${e.message}")
            }
        }
    }
    
    /**
     * Handle refresh command.
     */
    private suspend fun handleRefresh(): ActionResult {
        return withContext(Dispatchers.Main) {
            try {
                webView.reload()
                ActionResult(true, "Page refreshed")
            } catch (e: Exception) {
                ActionResult(false, "Failed to refresh: ${e.message}")
            }
        }
    }
    
    /**
     * Handle screenshot command.
     */
    private suspend fun handleScreenshot(): ActionResult {
        return ActionResult(false, "Screenshot functionality requires integration with ScreenContextManager")
    }
}
