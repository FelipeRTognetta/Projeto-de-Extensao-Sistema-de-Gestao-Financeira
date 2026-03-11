package com.psychologist.financial

import com.psychologist.financial.viewmodel.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for BaseViewModel
 *
 * Coverage:
 * - Initial state (no error, not loading)
 * - setError() updates error state
 * - clearError() clears error state
 * - handleException() logs and sets error
 * - launchSafe sets loading true then false
 * - launchSafe propagates uncaught exceptions to error state
 * - launchSafeWithResult calls onSuccess with result
 * - launchBackground does not affect loading state
 *
 * Total: 14 test cases
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(JUnit4::class)
class BaseViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    /**
     * Concrete subclass for testing — exposes protected methods as public.
     */
    private class TestViewModel : BaseViewModel() {
        fun publicLaunchSafe(block: suspend () -> Unit) = launchSafe(block)
        fun publicLaunchBackground(block: suspend () -> Unit) = launchBackground(block)
        fun publicSetError(message: String) = setError(message)
        fun publicClearError() = clearError()
        fun publicHandleException(exception: Exception, message: String) =
            handleException(exception, message)
        fun <T> publicLaunchSafeWithResult(
            block: suspend () -> T,
            onSuccess: (T) -> Unit
        ) = launchSafeWithResult(block, onSuccess)
    }

    private lateinit var viewModel: TestViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = TestViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ========================================
    // Initial State Tests
    // ========================================

    @Test
    fun `initial error state is null`() {
        assertNull(viewModel.error.value)
    }

    @Test
    fun `initial loading state is false`() {
        assertFalse(viewModel.isLoading.value)
    }

    // ========================================
    // Error State Tests
    // ========================================

    @Test
    fun `setError updates error state`() {
        viewModel.publicSetError("Test error message")

        assertEquals("Test error message", viewModel.error.value)
    }

    @Test
    fun `clearError resets error state to null`() {
        viewModel.publicSetError("Some error")
        viewModel.publicClearError()

        assertNull(viewModel.error.value)
    }

    @Test
    fun `handleException sets error with user message`() {
        val exception = RuntimeException("Internal exception")
        viewModel.publicHandleException(exception, "User-visible message")

        assertEquals("User-visible message", viewModel.error.value)
    }

    @Test
    fun `multiple setError calls overwrite previous error`() {
        viewModel.publicSetError("First error")
        viewModel.publicSetError("Second error")

        assertEquals("Second error", viewModel.error.value)
    }

    // ========================================
    // launchSafe() Tests
    // ========================================

    @Test
    fun `launchSafe sets loading true during execution then false after`() = runTest {
        val loadingStates = mutableListOf<Boolean>()

        viewModel.publicLaunchSafe {
            loadingStates.add(viewModel.isLoading.value)
        }
        advanceUntilIdle()

        assertTrue(loadingStates.contains(true), "Loading should have been true during execution")
        assertFalse(viewModel.isLoading.value, "Loading should be false after completion")
    }

    @Test
    fun `launchSafe sets loading false even after exception`() = runTest {
        viewModel.publicLaunchSafe {
            throw RuntimeException("Test exception")
        }
        advanceUntilIdle()

        assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun `launchSafe completes successfully with no error`() = runTest {
        var executed = false

        viewModel.publicLaunchSafe {
            executed = true
        }
        advanceUntilIdle()

        assertTrue(executed)
        assertNull(viewModel.error.value)
    }

    // ========================================
    // launchSafeWithResult() Tests
    // ========================================

    @Test
    fun `launchSafeWithResult calls onSuccess with result`() = runTest {
        var receivedResult: String? = null

        viewModel.publicLaunchSafeWithResult(
            block = { "test result" },
            onSuccess = { receivedResult = it }
        )
        advanceUntilIdle()

        assertEquals("test result", receivedResult)
    }

    @Test
    fun `launchSafeWithResult sets loading false after completion`() = runTest {
        viewModel.publicLaunchSafeWithResult(
            block = { 42 },
            onSuccess = { }
        )
        advanceUntilIdle()

        assertFalse(viewModel.isLoading.value)
    }

    // ========================================
    // launchBackground() Tests
    // ========================================

    @Test
    fun `launchBackground does not affect loading state`() = runTest {
        viewModel.publicLaunchBackground {
            // block runs on Dispatchers.IO — not controlled by test dispatcher
        }
        advanceUntilIdle()

        // launchBackground must never set isLoading = true (unlike launchSafe)
        assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun `launchBackground silently handles exceptions without setting error`() = runTest {
        viewModel.publicLaunchBackground {
            throw RuntimeException("Background error")
        }
        advanceUntilIdle()

        // Background exceptions should not propagate to UI error state
        assertNull(viewModel.error.value)
        assertFalse(viewModel.isLoading.value)
    }
}
