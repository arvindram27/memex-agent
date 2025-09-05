package com.memexos.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Audio recording performance benchmark tests.
 * 
 * These benchmarks measure:
 * - Recording start latency
 * - Recording stop and processing time
 * - UI responsiveness during recording
 * - Memory usage during recording sessions
 * - Multiple recording sessions performance
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class AudioRecordingBenchmark {
    
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun recordingStartLatencyBenchmark() {
        benchmarkRule.measureRepeated(
            packageName = "com.memexos.app",
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(),
            iterations = 10,
            startupMode = StartupMode.WARM,
            setupBlock = {
                pressHome()
            }
        ) {
            startActivityAndWait()
            
            // Wait for app to be fully loaded
            device.wait(Until.hasObject(By.res("com.memexos.app", "fabRecord")), 5000)
            
            // Measure time to start recording
            val startTime = System.nanoTime()
            
            val recordFab = device.findObject(By.res("com.memexos.app", "fabRecord"))
            recordFab.click()
            
            // Wait for recording dialog to appear
            device.wait(Until.hasObject(By.res("com.memexos.app", "micPulseCircle")), 3000)
            
            val endTime = System.nanoTime()
            val latency = (endTime - startTime) / 1_000_000 // Convert to milliseconds
            
            // Log latency for analysis
            println("Recording start latency: ${latency}ms")
            
            // Stop recording for cleanup
            val stopButton = device.findObject(By.res("com.memexos.app", "stopButton"))
            stopButton?.click()
            
            // Wait for processing to complete
            Thread.sleep(2000)
        }
    }

    @Test
    fun recordingStopAndProcessingBenchmark() {
        benchmarkRule.measureRepeated(
            packageName = "com.memexos.app",
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(),
            iterations = 5,
            startupMode = StartupMode.WARM
        ) {
            startActivityAndWait()
            
            // Start recording
            val recordFab = device.findObject(By.res("com.memexos.app", "fabRecord"))
            recordFab.click()
            
            // Wait for recording to be established
            device.wait(Until.hasObject(By.res("com.memexos.app", "micPulseCircle")), 3000)
            
            // Record for a fixed duration
            Thread.sleep(2000) // 2 seconds of recording
            
            // Measure stop and processing time
            val startTime = System.nanoTime()
            
            val stopButton = device.findObject(By.res("com.memexos.app", "stopButton"))
            stopButton.click()
            
            // Wait for processing overlay to appear
            device.wait(Until.hasObject(By.res("com.memexos.app", "progressOverlay")), 2000)
            
            // Wait for processing to complete (overlay to disappear)
            device.wait(Until.gone(By.res("com.memexos.app", "progressOverlay")), 10000)
            
            val endTime = System.nanoTime()
            val processingTime = (endTime - startTime) / 1_000_000
            
            println("Recording stop + processing time: ${processingTime}ms")
        }
    }

    @Test
    fun uiResponsivenessDuringRecordingBenchmark() {
        benchmarkRule.measureRepeated(
            packageName = "com.memexos.app",
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(),
            iterations = 5,
            startupMode = StartupMode.WARM
        ) {
            startActivityAndWait()
            
            // Start recording
            val recordFab = device.findObject(By.res("com.memexos.app", "fabRecord"))
            recordFab.click()
            
            device.wait(Until.hasObject(By.res("com.memexos.app", "micPulseCircle")), 3000)
            
            // Test UI responsiveness during recording
            // Try interacting with various UI elements
            val urlEditText = device.findObject(By.res("com.memexos.app", "urlEditText"))
            urlEditText?.click()
            
            // Test WebView scrolling
            val webView = device.findObject(By.res("com.memexos.app", "webView"))
            webView?.let {
                it.scroll(it.visibleCenter, it.visibleCenter.x, it.visibleCenter.y - 200, 10)
            }
            
            // Stop recording
            val stopButton = device.findObject(By.res("com.memexos.app", "stopButton"))
            stopButton.click()
            
            Thread.sleep(3000) // Wait for processing
        }
    }

    @Test
    fun multipleRecordingSessionsBenchmark() {
        benchmarkRule.measureRepeated(
            packageName = "com.memexos.app",
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(),
            iterations = 3,
            startupMode = StartupMode.WARM
        ) {
            startActivityAndWait()
            
            // Perform multiple recording sessions
            repeat(5) { sessionNumber ->
                println("Starting recording session ${sessionNumber + 1}")
                
                val recordFab = device.findObject(By.res("com.memexos.app", "fabRecord"))
                recordFab.click()
                
                device.wait(Until.hasObject(By.res("com.memexos.app", "micPulseCircle")), 3000)
                
                // Variable recording duration to test different scenarios
                val recordingDuration = when (sessionNumber) {
                    0 -> 1000L  // 1 second
                    1 -> 2000L  // 2 seconds
                    2 -> 3000L  // 3 seconds
                    3 -> 1500L  // 1.5 seconds
                    else -> 2000L
                }
                
                Thread.sleep(recordingDuration)
                
                val stopButton = device.findObject(By.res("com.memexos.app", "stopButton"))
                stopButton.click()
                
                // Wait for processing to complete before next session
                device.wait(Until.gone(By.res("com.memexos.app", "progressOverlay")), 15000)
                
                // Small delay between sessions
                Thread.sleep(500)
            }
        }
    }

    @Test
    fun recordingMemoryUsageBenchmark() {
        benchmarkRule.measureRepeated(
            packageName = "com.memexos.app",
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(),
            iterations = 3,
            startupMode = StartupMode.COLD,
            setupBlock = {
                killProcess()
                System.gc() // Force garbage collection
            }
        ) {
            startActivityAndWait()
            
            // Get baseline memory usage
            val initialMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
            println("Initial memory usage: ${initialMemory / (1024 * 1024)}MB")
            
            // Start recording
            val recordFab = device.findObject(By.res("com.memexos.app", "fabRecord"))
            recordFab.click()
            
            device.wait(Until.hasObject(By.res("com.memexos.app", "micPulseCircle")), 3000)
            
            // Record for a longer duration to accumulate audio data
            Thread.sleep(5000) // 5 seconds
            
            val duringRecordingMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
            println("Memory during recording: ${duringRecordingMemory / (1024 * 1024)}MB")
            
            val stopButton = device.findObject(By.res("com.memexos.app", "stopButton"))
            stopButton.click()
            
            // Wait for processing
            device.wait(Until.gone(By.res("com.memexos.app", "progressOverlay")), 15000)
            
            val afterProcessingMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
            println("Memory after processing: ${afterProcessingMemory / (1024 * 1024)}MB")
            
            // Calculate memory differences
            val recordingOverhead = duringRecordingMemory - initialMemory
            val processingOverhead = afterProcessingMemory - initialMemory
            
            println("Recording memory overhead: ${recordingOverhead / (1024 * 1024)}MB")
            println("Processing memory overhead: ${processingOverhead / (1024 * 1024)}MB")
        }
    }

    @Test
    fun longRecordingSessionBenchmark() {
        benchmarkRule.measureRepeated(
            packageName = "com.memexos.app",
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(),
            iterations = 2,
            startupMode = StartupMode.WARM
        ) {
            startActivityAndWait()
            
            // Test performance with longer recording sessions
            val recordFab = device.findObject(By.res("com.memexos.app", "fabRecord"))
            recordFab.click()
            
            device.wait(Until.hasObject(By.res("com.memexos.app", "micPulseCircle")), 3000)
            
            // Record for 30 seconds (close to maximum duration)
            val recordingDuration = 30000L
            val startTime = System.currentTimeMillis()
            
            while (System.currentTimeMillis() - startTime < recordingDuration) {
                // Check if recording is still active
                val micCircle = device.findObject(By.res("com.memexos.app", "micPulseCircle"))
                if (micCircle == null) {
                    println("Recording stopped automatically")
                    break
                }
                
                Thread.sleep(1000)
                
                // Check timer updates
                val timerText = device.findObject(By.res("com.memexos.app", "timerText"))
                timerText?.let {
                    println("Timer: ${it.text}")
                }
            }
            
            // Stop recording if still active
            val stopButton = device.findObject(By.res("com.memexos.app", "stopButton"))
            stopButton?.click()
            
            // Wait for processing (may take longer for longer recordings)
            device.wait(Until.gone(By.res("com.memexos.app", "progressOverlay")), 30000)
        }
    }

    private fun MacrobenchmarkScope.waitForAppReady() {
        device.wait(Until.hasObject(By.res("com.memexos.app", "webView")), 10000)
        device.wait(Until.hasObject(By.res("com.memexos.app", "fabRecord")), 5000)
        Thread.sleep(1000) // Allow UI to settle
    }
}
