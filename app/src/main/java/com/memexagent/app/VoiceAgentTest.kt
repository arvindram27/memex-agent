package com.memexagent.app

import android.util.Log
import com.memexagent.app.context.VisualContextProcessor
import com.memexagent.app.voice.VoiceIntentProcessor

/**
 * Simple test class to verify voice agent components work independently
 */
class VoiceAgentTest {
    
    companion object {
        private const val TAG = "VoiceAgentTest"
    }
    
    fun testVoiceIntentProcessor(): Boolean {
        return try {
            val processor = VoiceIntentProcessor()
            
            // Test basic command processing
            val command1 = processor.processCommand("click the blue button")
            Log.d(TAG, "Command 1: Intent=${command1.intent}, Entities=${command1.entities}")
            
            val command2 = processor.processCommand("scroll down")
            Log.d(TAG, "Command 2: Intent=${command2.intent}, Entities=${command2.entities}")
            
            val command3 = processor.processCommand("fill email with test@example.com")
            Log.d(TAG, "Command 3: Intent=${command3.intent}, Entities=${command3.entities}")
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error testing VoiceIntentProcessor", e)
            false
        }
    }
    
    fun testVisualContextProcessor(): Boolean {
        return try {
            val processor = VisualContextProcessor()
            Log.d(TAG, "VisualContextProcessor created successfully")
            
            // Test would require WebView to be fully functional
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error testing VisualContextProcessor", e)
            false
        }
    }
    
    fun runAllTests(): Boolean {
        Log.d(TAG, "Starting Voice Agent component tests...")
        
        val test1 = testVoiceIntentProcessor()
        val test2 = testVisualContextProcessor()
        
        val allPassed = test1 && test2
        
        Log.d(TAG, "Voice Agent tests completed. All passed: $allPassed")
        return allPassed
    }
}
