package com.memexos.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Test rule that manages coroutines test dispatcher for tests.
 * 
 * Usage:
 * ```kotlin
 * @get:Rule
 * val testCoroutineRule = TestCoroutineRule()
 * 
 * @Test
 * fun myTest() = testCoroutineRule.runTest {
 *     // Your test code here
 * }
 * ```
 */
@ExperimentalCoroutinesApi
class TestCoroutineRule : TestWatcher() {

    private val testDispatcher = UnconfinedTestDispatcher()
    
    override fun starting(description: Description?) {
        super.starting(description)
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description?) {
        super.finished(description)
        Dispatchers.resetMain()
    }

    fun runTest(block: suspend TestScope.() -> Unit) = kotlinx.coroutines.test.runTest(
        context = testDispatcher,
        testBody = block
    )

    /**
     * Advances the test dispatcher by the given amount of virtual time.
     */
    fun advanceTimeBy(delayTimeMillis: Long) {
        testDispatcher.scheduler.advanceTimeBy(delayTimeMillis)
    }

    /**
     * Advances the test dispatcher until all pending tasks are completed.
     */
    fun advanceUntilIdle() {
        testDispatcher.scheduler.advanceUntilIdle()
    }
}
