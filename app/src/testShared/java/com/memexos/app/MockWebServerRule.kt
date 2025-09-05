package com.memexos.app

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.rules.ExternalResource
import java.util.concurrent.TimeUnit

/**
 * JUnit rule for managing MockWebServer lifecycle in tests.
 * 
 * Usage:
 * ```kotlin
 * @get:Rule
 * val mockWebServerRule = MockWebServerRule()
 * 
 * @Test
 * fun testWebViewLoading() {
 *     mockWebServerRule.enqueue(
 *         MockResponse()
 *             .setResponseCode(200)
 *             .setBody("<html><body>Test Page</body></html>")
 *             .addHeader("Content-Type", "text/html")
 *     )
 *     
 *     val url = mockWebServerRule.url("/test")
 *     // Test your WebView loading with this URL
 * }
 * ```
 */
class MockWebServerRule : ExternalResource() {
    
    private val mockWebServer = MockWebServer()
    
    override fun before() {
        mockWebServer.start()
    }
    
    override fun after() {
        mockWebServer.shutdown()
    }
    
    /**
     * Enqueue a mock response to be served by the server.
     */
    fun enqueue(response: MockResponse) {
        mockWebServer.enqueue(response)
    }
    
    /**
     * Get the base URL of the mock server.
     */
    fun url(path: String = "/"): String {
        return mockWebServer.url(path).toString()
    }
    
    /**
     * Get a recorded request that was made to the server.
     * This will block until a request is available or timeout is reached.
     */
    fun takeRequest(timeout: Long = 5, unit: TimeUnit = TimeUnit.SECONDS): RecordedRequest? {
        return try {
            mockWebServer.takeRequest(timeout, unit)
        } catch (e: InterruptedException) {
            null
        }
    }
    
    /**
     * Get the number of requests that have been made to the server.
     */
    val requestCount: Int
        get() = mockWebServer.requestCount
    
    companion object {
        /**
         * Creates a simple HTML response for testing WebView loading.
         */
        fun createHtmlResponse(
            title: String = "Test Page",
            body: String = "Test Content",
            responseCode: Int = 200
        ): MockResponse {
            val html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>$title</title>
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                </head>
                <body>
                    <h1>$title</h1>
                    <p>$body</p>
                </body>
                </html>
            """.trimIndent()
            
            return MockResponse()
                .setResponseCode(responseCode)
                .setBody(html)
                .addHeader("Content-Type", "text/html; charset=utf-8")
        }
        
        /**
         * Creates a JSON response for API testing.
         */
        fun createJsonResponse(
            json: String,
            responseCode: Int = 200
        ): MockResponse {
            return MockResponse()
                .setResponseCode(responseCode)
                .setBody(json)
                .addHeader("Content-Type", "application/json; charset=utf-8")
        }
        
        /**
         * Creates an error response with the specified code and message.
         */
        fun createErrorResponse(
            responseCode: Int,
            message: String = "Error"
        ): MockResponse {
            return MockResponse()
                .setResponseCode(responseCode)
                .setBody(message)
        }
        
        /**
         * Creates a search results page response (useful for testing search functionality).
         */
        fun createSearchResultsResponse(
            query: String,
            results: List<String>
        ): MockResponse {
            val resultsHtml = results.joinToString("\n") { result ->
                "<div class='search-result'>$result</div>"
            }
            
            val html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>Search results for: $query</title>
                </head>
                <body>
                    <h1>Search results for: $query</h1>
                    <div class='results'>
                        $resultsHtml
                    </div>
                </body>
                </html>
            """.trimIndent()
            
            return MockResponse()
                .setResponseCode(200)
                .setBody(html)
                .addHeader("Content-Type", "text/html; charset=utf-8")
        }
    }
}
