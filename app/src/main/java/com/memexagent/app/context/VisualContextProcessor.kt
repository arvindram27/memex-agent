package com.memexagent.app.context

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import android.webkit.WebView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Processes visual context from screen captures and web pages using ML Kit OCR
 * and JavaScript injection for comprehensive page analysis.
 */
class VisualContextProcessor {
    
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    
    companion object {
        private const val TAG = "VisualContextProcessor"
    }
    
    data class PageElement(
        val type: ElementType,
        val text: String,
        val boundingBox: Rect? = null,
        val attributes: Map<String, String> = emptyMap(),
        val selector: String? = null
    )
    
    enum class ElementType {
        TEXT, BUTTON, LINK, INPUT, FORM, IMAGE, HEADING, NAVIGATION, UNKNOWN
    }
    
    data class WebPageContext(
        val visibleText: String,
        val clickableElements: List<PageElement>,
        val formFields: List<PageElement>,
        val currentUrl: String,
        val pageTitle: String,
        val ocrResults: List<Text.TextBlock> = emptyList(),
        val pageStructure: Map<String, Any> = emptyMap()
    )
    
    /**
     * Extract text from screen capture using ML Kit OCR.
     */
    suspend fun extractTextFromScreen(bitmap: Bitmap): Text? = suspendCoroutine { continuation ->
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    Log.d(TAG, "OCR completed successfully. Found ${visionText.textBlocks.size} text blocks")
                    continuation.resume(visionText)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "OCR failed", e)
                    continuation.resume(null)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create InputImage", e)
            continuation.resume(null)
        }
    }
    
    /**
     * Analyze web page elements by injecting JavaScript into WebView.
     */
    suspend fun analyzeWebPageElements(webView: WebView): WebPageContext = suspendCoroutine { continuation ->
        try {
            // JavaScript to extract comprehensive page information
            val jsCode = """
                (function() {
                    const context = {
                        visibleText: '',
                        clickableElements: [],
                        formFields: [],
                        currentUrl: window.location.href,
                        pageTitle: document.title,
                        pageStructure: {}
                    };
                    
                    // Extract visible text
                    const textNodes = [];
                    const walker = document.createTreeWalker(
                        document.body,
                        NodeFilter.SHOW_TEXT,
                        {
                            acceptNode: function(node) {
                                const parent = node.parentElement;
                                if (!parent) return NodeFilter.FILTER_REJECT;
                                
                                const style = window.getComputedStyle(parent);
                                if (style.display === 'none' || style.visibility === 'hidden') {
                                    return NodeFilter.FILTER_REJECT;
                                }
                                
                                return node.textContent.trim().length > 0 ? 
                                    NodeFilter.FILTER_ACCEPT : NodeFilter.FILTER_REJECT;
                            }
                        }
                    );
                    
                    let node;
                    while (node = walker.nextNode()) {
                        textNodes.push(node.textContent.trim());
                    }
                    context.visibleText = textNodes.join(' ').substring(0, 2000); // Limit text length
                    
                    // Extract clickable elements
                    const clickableSelectors = ['a', 'button', '[onclick]', '[role="button"]', 'input[type="submit"]', 'input[type="button"]'];
                    clickableSelectors.forEach(selector => {
                        document.querySelectorAll(selector).forEach((element, index) => {
                            if (element.offsetParent !== null) { // Element is visible
                                const rect = element.getBoundingClientRect();
                                const text = element.textContent?.trim() || element.getAttribute('aria-label') || element.getAttribute('title') || '';
                                
                                context.clickableElements.push({
                                    type: 'CLICKABLE',
                                    text: text.substring(0, 100), // Limit text length
                                    selector: selector + ':nth-child(' + (index + 1) + ')',
                                    tagName: element.tagName.toLowerCase(),
                                    attributes: {
                                        href: element.getAttribute('href') || '',
                                        id: element.id || '',
                                        className: element.className || '',
                                        type: element.getAttribute('type') || ''
                                    },
                                    boundingBox: {
                                        left: Math.round(rect.left),
                                        top: Math.round(rect.top),
                                        right: Math.round(rect.right),
                                        bottom: Math.round(rect.bottom)
                                    }
                                });
                            }
                        });
                    });
                    
                    // Extract form fields
                    const formSelectors = ['input', 'textarea', 'select'];
                    formSelectors.forEach(selector => {
                        document.querySelectorAll(selector).forEach((element, index) => {
                            if (element.offsetParent !== null && element.type !== 'hidden') {
                                const rect = element.getBoundingClientRect();
                                const label = element.getAttribute('aria-label') || 
                                            element.getAttribute('placeholder') || 
                                            element.getAttribute('name') || '';
                                
                                context.formFields.push({
                                    type: 'FORM_FIELD',
                                    text: label,
                                    selector: selector + ':nth-child(' + (index + 1) + ')',
                                    tagName: element.tagName.toLowerCase(),
                                    attributes: {
                                        type: element.getAttribute('type') || '',
                                        name: element.getAttribute('name') || '',
                                        id: element.id || '',
                                        placeholder: element.getAttribute('placeholder') || '',
                                        required: element.hasAttribute('required').toString()
                                    },
                                    boundingBox: {
                                        left: Math.round(rect.left),
                                        top: Math.round(rect.top),
                                        right: Math.round(rect.right),
                                        bottom: Math.round(rect.bottom)
                                    }
                                });
                            }
                        });
                    });
                    
                    // Extract page structure
                    context.pageStructure = {
                        headings: Array.from(document.querySelectorAll('h1, h2, h3, h4, h5, h6')).map(h => ({
                            level: h.tagName.toLowerCase(),
                            text: h.textContent?.trim().substring(0, 100) || ''
                        })),
                        navigation: Array.from(document.querySelectorAll('nav, [role="navigation"]')).length > 0,
                        forms: document.querySelectorAll('form').length,
                        images: document.querySelectorAll('img').length
                    };
                    
                    return JSON.stringify(context);
                })();
            """.trimIndent()
            
            webView.evaluateJavascript(jsCode) { result ->
                try {
                    if (result != null && result != "null") {
                        // Remove surrounding quotes from JavaScript result
                        val jsonResult = result.removePrefix("\"").removeSuffix("\"")
                            .replace("\\\"", "\"")
                            .replace("\\n", "\n")
                            .replace("\\t", "\t")
                        
                        val webPageContext = parseWebPageContext(jsonResult)
                        Log.d(TAG, "Web page analysis completed: ${webPageContext.clickableElements.size} clickable elements, ${webPageContext.formFields.size} form fields")
                        continuation.resume(webPageContext)
                    } else {
                        Log.w(TAG, "JavaScript returned null result")
                        continuation.resume(createEmptyContext(webView))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse JavaScript result", e)
                    continuation.resume(createEmptyContext(webView))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inject JavaScript", e)
            continuation.resume(createEmptyContext(webView))
        }
    }
    
    /**
     * Combine OCR results with web page analysis for comprehensive context.
     */
    suspend fun buildComprehensiveContext(webView: WebView, screenCapture: Bitmap? = null): WebPageContext {
        val webPageContext = analyzeWebPageElements(webView)
        
        return if (screenCapture != null) {
            val ocrResults = extractTextFromScreen(screenCapture)
            webPageContext.copy(
                ocrResults = ocrResults?.textBlocks ?: emptyList()
            )
        } else {
            webPageContext
        }
    }
    
    /**
     * Find elements matching a text query within the page context.
     */
    fun findElementsByText(context: WebPageContext, query: String, fuzzy: Boolean = true): List<PageElement> {
        val allElements = context.clickableElements + context.formFields
        val queryLower = query.lowercase()
        
        return allElements.filter { element ->
            if (fuzzy) {
                element.text.lowercase().contains(queryLower) ||
                element.attributes.values.any { it.lowercase().contains(queryLower) }
            } else {
                element.text.equals(query, ignoreCase = true)
            }
        }
    }
    
    /**
     * Extract clickable elements near OCR-detected text.
     */
    fun findClickableElementsNearText(context: WebPageContext, ocrText: String, maxDistance: Int = 100): List<PageElement> {
        // This would require correlating OCR bounding boxes with DOM element positions
        // For now, return elements with matching text
        return findElementsByText(context, ocrText, fuzzy = true)
    }
    
    private fun parseWebPageContext(jsonString: String): WebPageContext {
        try {
            // Simple JSON parsing - in production, consider using a proper JSON library
            val context = parseJsonString(jsonString)
            return WebPageContext(
                visibleText = context["visibleText"] as? String ?: "",
                clickableElements = parseElements(context["clickableElements"] as? List<*> ?: emptyList()),
                formFields = parseElements(context["formFields"] as? List<*> ?: emptyList()),
                currentUrl = context["currentUrl"] as? String ?: "",
                pageTitle = context["pageTitle"] as? String ?: "",
                pageStructure = context["pageStructure"] as? Map<String, Any> ?: emptyMap()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse JSON context", e)
            return WebPageContext("", emptyList(), emptyList(), "", "")
        }
    }
    
    private fun parseElements(elementList: List<*>): List<PageElement> {
        return elementList.mapNotNull { item ->
            try {
                val elementMap = item as? Map<*, *> ?: return@mapNotNull null
                val type = when (elementMap["type"]) {
                    "CLICKABLE" -> ElementType.BUTTON
                    "FORM_FIELD" -> ElementType.INPUT
                    else -> ElementType.UNKNOWN
                }
                
                val boundingBoxMap = elementMap["boundingBox"] as? Map<*, *>
                val boundingBox = if (boundingBoxMap != null) {
                    Rect(
                        (boundingBoxMap["left"] as? Number)?.toInt() ?: 0,
                        (boundingBoxMap["top"] as? Number)?.toInt() ?: 0,
                        (boundingBoxMap["right"] as? Number)?.toInt() ?: 0,
                        (boundingBoxMap["bottom"] as? Number)?.toInt() ?: 0
                    )
                } else null
                
                PageElement(
                    type = type,
                    text = elementMap["text"] as? String ?: "",
                    boundingBox = boundingBox,
                    attributes = (elementMap["attributes"] as? Map<*, *>)?.mapKeys { it.key.toString() }?.mapValues { it.value.toString() } ?: emptyMap(),
                    selector = elementMap["selector"] as? String
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse element", e)
                null
            }
        }
    }
    
    private fun parseJsonString(jsonString: String): Map<String, Any> {
        // Basic JSON parsing - replace with proper JSON library in production
        // This is a simplified implementation for demonstration
        return try {
            // Use Android's built-in JSON parsing
            val jsonObject = org.json.JSONObject(jsonString)
            jsonObject.toMap()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse JSON", e)
            emptyMap()
        }
    }
    
    private fun createEmptyContext(webView: WebView): WebPageContext {
        return WebPageContext(
            visibleText = "",
            clickableElements = emptyList(),
            formFields = emptyList(),
            currentUrl = webView.url ?: "",
            pageTitle = webView.title ?: "",
            pageStructure = emptyMap()
        )
    }
    
    /**
     * Release resources used by the OCR recognizer.
     */
    fun cleanup() {
        try {
            recognizer.close()
            Log.d(TAG, "OCR recognizer resources released")
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up OCR recognizer", e)
        }
    }
}

// Extension function to convert JSONObject to Map
private fun org.json.JSONObject.toMap(): Map<String, Any> {
    val map = mutableMapOf<String, Any>()
    val keysItr = this.keys()
    while (keysItr.hasNext()) {
        val key = keysItr.next()
        var value = this[key]
        
        if (value is org.json.JSONObject) {
            value = value.toMap()
        } else if (value is org.json.JSONArray) {
            value = value.toList()
        }
        
        map[key] = value
    }
    return map
}

// Extension function to convert JSONArray to List
private fun org.json.JSONArray.toList(): List<Any> {
    val list = mutableListOf<Any>()
    for (i in 0 until this.length()) {
        var value = this[i]
        
        if (value is org.json.JSONObject) {
            value = value.toMap()
        } else if (value is org.json.JSONArray) {
            value = value.toList()
        }
        
        list.add(value)
    }
    return list
}
