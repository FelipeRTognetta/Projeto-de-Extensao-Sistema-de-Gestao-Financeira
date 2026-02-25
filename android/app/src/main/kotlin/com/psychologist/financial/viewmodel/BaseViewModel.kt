package com.psychologist.financial.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * Abstract base ViewModel for all screen ViewModels
 *
 * Responsibilities:
 * - Manage coroutine lifecycle (viewModelScope)
 * - Handle errors consistently across all screens
 * - Provide loading/error state management
 * - Offer safe launch helpers for background operations
 *
 * Architecture:
 * - Inherits from AndroidX ViewModel (lifecycle-aware, survives config changes)
 * - Uses viewModelScope for automatic cleanup on destroy
 * - StateFlow for reactive state management
 * - Shared error handling and logging
 *
 * Error Handling:
 * - All async operations caught and reported via errorFlow
 * - Loading state managed automatically
 * - Allows UI to show consistent error dialogs
 *
 * Usage:
 * ```kotlin
 * class PatientListViewModel(
 *     private val patientRepository: PatientRepository
 * ) : BaseViewModel() {
 *
 *     private val _patients = MutableStateFlow<List<Patient>>(emptyList())
 *     val patients: StateFlow<List<Patient>> = _patients.asStateFlow()
 *
 *     fun loadPatients() {
 *         launchSafe {
 *             _patients.value = patientRepository.getAllPatients()
 *         }
 *     }
 * }
 *
 * // In Composable
 * PatientListScreen(
 *     patients = viewModel.patients.collectAsState().value,
 *     isLoading = viewModel.isLoading.collectAsState().value,
 *     error = viewModel.error.collectAsState().value,
 *     onRetry = { viewModel.loadPatients() }
 * )
 * ```
 */
abstract class BaseViewModel : ViewModel() {

    private companion object {
        private const val TAG = "BaseViewModel"
    }

    // ========================================
    // Error and Loading State Management
    // ========================================

    /**
     * Current error message to display in UI
     *
     * Null = no error
     * Non-null = show error dialog with this message
     *
     * Flow allows UI to react to error changes.
     */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * Loading state for UI spinners/progress bars
     *
     * true = operation in progress (show loading indicator)
     * false = operation complete
     *
     * Flow allows UI to react to loading changes.
     */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * Reusable exception handler for all coroutines
     *
     * Catches all uncaught exceptions and:
     * 1. Logs error with TAG
     * 2. Sets error message for UI
     * 3. Sets loading to false
     *
     * Attached to all launch blocks via coroutineContext.
     */
    private val exceptionHandler = CoroutineExceptionHandler { context, exception ->
        Log.e(TAG, "Coroutine exception in ${this::class.simpleName}", exception)
        setError(exception.message ?: "Unknown error occurred")
        setLoading(false)
    }

    /**
     * Custom CoroutineContext including exception handler
     *
     * Used in launch blocks:
     * ```kotlin
     * viewModelScope.launch(coroutineContext) {
     *     // All exceptions caught by exceptionHandler
     * }
     * ```
     */
    protected val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + exceptionHandler

    // ========================================
    // Safe Launch Helpers
    // ========================================

    /**
     * Launch a coroutine with automatic error handling and loading state
     *
     * Features:
     * - Automatically sets loading = true/false
     * - Catches exceptions and updates error state
     * - Runs on Main dispatcher
     * - Runs in viewModelScope (cleaned up on ViewModel destroy)
     *
     * @param block Suspend lambda for async operation
     * @return Job for cancellation if needed
     *
     * Example:
     * ```kotlin
     * launchSafe {
     *     val patients = patientRepository.getAllPatients()
     *     _patients.value = patients
     * }
     * ```
     */
    protected fun launchSafe(block: suspend () -> Unit): Job {
        return viewModelScope.launch(coroutineContext) {
            try {
                setLoading(true)
                block()
            } finally {
                setLoading(false)
            }
        }
    }

    /**
     * Launch a coroutine with result handling
     *
     * Similar to launchSafe but returns result via lambda.
     * Useful for operations that need to update UI after completion.
     *
     * @param T Result type
     * @param block Suspend lambda returning result
     * @param onSuccess Callback with result (called on Main thread)
     * @return Job for cancellation
     *
     * Example:
     * ```kotlin
     * launchSafeWithResult(
     *     block = { patientRepository.getPatient(patientId) },
     *     onSuccess = { patient ->
     *         _selectedPatient.value = patient
     *         Toast.makeText(context, "Loaded", Toast.LENGTH_SHORT).show()
     *     }
     * )
     * ```
     */
    protected fun <T> launchSafeWithResult(
        block: suspend () -> T,
        onSuccess: (T) -> Unit
    ): Job {
        return viewModelScope.launch(coroutineContext) {
            try {
                setLoading(true)
                val result = block()
                onSuccess(result)
            } finally {
                setLoading(false)
            }
        }
    }

    /**
     * Launch a background operation without loading indicator
     *
     * Use for non-blocking operations (analytics, logging, etc.)
     * that shouldn't affect UI.
     *
     * @param block Suspend lambda
     * @return Job for cancellation
     *
     * Example:
     * ```kotlin
     * launchBackground {
     *     analyticsService.logScreenView("PatientList")
     * }
     * ```
     */
    protected fun launchBackground(block: suspend () -> Unit): Job {
        return viewModelScope.launch(coroutineContext + Dispatchers.IO) {
            try {
                block()
            } catch (e: Exception) {
                Log.e(TAG, "Background operation failed", e)
                // Don't show error for background operations
            }
        }
    }

    // ========================================
    // Error State Management
    // ========================================

    /**
     * Set error message for UI display
     *
     * Triggers error dialog in UI.
     * Message is displayed until clearError() is called.
     *
     * @param message Error message to display
     */
    protected fun setError(message: String) {
        Log.w(TAG, "Error: $message")
        _error.value = message
    }

    /**
     * Clear current error message
     *
     * Dismisses error dialog if visible.
     */
    protected fun clearError() {
        _error.value = null
    }

    /**
     * Handle exception with custom error message
     *
     * Logs exception and sets custom message for UI.
     *
     * @param exception Exception that occurred
     * @param userMessage Message to display to user
     */
    protected fun handleException(exception: Exception, userMessage: String) {
        Log.e(TAG, userMessage, exception)
        setError(userMessage)
    }

    // ========================================
    // Loading State Management
    // ========================================

    /**
     * Set loading state
     *
     * Shows/hides loading indicator in UI.
     *
     * @param loading true to show loading, false to hide
     */
    protected fun setLoading(loading: Boolean) {
        _isLoading.value = loading
    }

    // ========================================
    // Lifecycle
    // ========================================

    /**
     * ViewModel cleanup
     *
     * Called by Android when ViewModel is destroyed.
     * All coroutines in viewModelScope are automatically cancelled.
     * Override in subclasses if additional cleanup needed.
     */
    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "${this::class.simpleName} cleared")
    }
}
