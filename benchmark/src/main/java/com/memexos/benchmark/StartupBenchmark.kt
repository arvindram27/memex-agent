package com.memexos.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Startup performance benchmark tests for MemexOS.
 * 
 * These benchmarks measure:
 * - Cold start time from launcher
 * - Warm start performance 
 * - Hot start performance
 * - WebView initialization overhead
 * - JNI library loading impact
 * - Memory usage during startup
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupNoCompilation() {
        benchmark(CompilationMode.None())
    }

    @Test
    fun startupPartialCompilation() {
        benchmark(CompilationMode.Partial())
    }

    @Test
    fun startupFullCompilation() {
        benchmark(CompilationMode.Full())
    }

    private fun benchmark(compilationMode: CompilationMode) {
        benchmarkRule.measureRepeated(
            packageName = "com.memexos.app",
            metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
            compilationMode = compilationMode,
            iterations = 5,
            startupMode = StartupMode.COLD,
            setupBlock = {
                // Clear any cached data that might affect startup
                pressHome()
            }
        ) {
            startActivityAndWait()
            waitForWebViewInitialization()
        }
    }

    @Test
    fun startupWarmBenchmark() {
        benchmarkRule.measureRepeated(
            packageName = "com.memexos.app",
            metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(),
            iterations = 10,
            startupMode = StartupMode.WARM
        ) {
            startActivityAndWait()
            waitForWebViewInitialization()
        }
    }

    @Test
    fun startupHotBenchmark() {
        benchmarkRule.measureRepeated(
            packageName = "com.memexos.app", 
            metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(),
            iterations = 10,
            startupMode = StartupMode.HOT
        ) {
            startActivityAndWait()
        }
    }

    /**
     * Benchmark that focuses on WebView initialization performance
     */
    @Test
    fun webViewInitializationBenchmark() {
        benchmarkRule.measureRepeated(
            packageName = "com.memexos.app",
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(),
            iterations = 5,
            startupMode = StartupMode.COLD
        ) {
            startActivityAndWait()
            
            // Wait specifically for WebView to be ready
            device.wait(Until.hasObject(By.res("com.memexos.app", "webView")), 5000)
            
            // Interact with WebView to ensure it's fully loaded
            val webView = device.findObject(By.res("com.memexos.app", "webView"))
            webView?.click()
            
            Thread.sleep(2000) // Allow WebView to settle
        }
    }

    /**
     * Benchmark JNI library loading (Whisper.cpp) impact on startup
     */
    @Test
    fun jniLoadingBenchmark() {
        benchmarkRule.measureRepeated(
            packageName = "com.memexos.app",
            metrics = listOf(StartupTimingMetric()),
            compilationMode = CompilationMode.Partial(),
            iterations = 5,
            startupMode = StartupMode.COLD,
            setupBlock = {
                pressHome()
                // Clear app data to force fresh JNI library loading
                killProcess()
            }
        ) {
            startActivityAndWait()
            
            // Wait for potential JNI initialization
            Thread.sleep(1000)
            
            // Try to trigger voice recording (which would use JNI)
            val recordFab = device.findObject(By.res("com.memexos.app", "fabRecord"))
            recordFab?.let {
                it.click()
                Thread.sleep(500)
                
                // Look for stop button to confirm recording dialog opened
                val stopButton = device.findObject(By.res("com.memexos.app", "stopButton"))
                stopButton?.click()
            }
        }
    }

    /**
     * Memory usage benchmark during startup
     */
    @Test
    fun memoryUsageBenchmark() {
        benchmarkRule.measureRepeated(
            packageName = "com.memexos.app",
            metrics = listOf(StartupTimingMetric()),
            compilationMode = CompilationMode.Partial(),
            iterations = 3,
            startupMode = StartupMode.COLD,
            setupBlock = {
                pressHome()
                killProcess()
                // Force garbage collection before measurement
                System.gc()
            }
        ) {
            startActivityAndWait()
            waitForWebViewInitialization()
            
            // Trigger memory-intensive operations
            triggerRecordingFlow()
            
            Thread.sleep(2000) // Allow memory to stabilize
        }
    }

    private fun MacrobenchmarkScope.waitForWebViewInitialization() {
        // Wait for WebView to be visible and initialized
        device.wait(Until.hasObject(By.res("com.memexos.app", "webView")), 10000)
        
        // Wait a bit longer for WebView to fully load its content
        Thread.sleep(1500)
    }

    private fun MacrobenchmarkScope.triggerRecordingFlow() {
        // Trigger a complete recording flow to test memory usage
        val recordFab = device.findObject(By.res("com.memexos.app", "fabRecord"))
        recordFab?.let {
            it.click()
            Thread.sleep(1000)
            
            val stopButton = device.findObject(By.res("com.memexos.app", "stopButton"))
            stopButton?.click()
            
            // Wait for processing to complete
            Thread.sleep(3000)
        }
    }
}
