package com.psychologist.financial.viewmodel

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.psychologist.financial.domain.models.BiometricAuthResult
import com.psychologist.financial.services.BiometricAuthManager
import com.psychologist.financial.services.PerOperationAuthManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Authentication ViewModel
 *
 * Manages authentication UI state and session timeout.
 * Coordinates between BiometricAuthManager and UI screens.
 *
 * Responsibilities:
 * - Manage app-level biometric authentication
 * - Track authentication state (idle, authenticating, authenticated, error)
 * - Handle session timeout (15 minutes)
 * - Coordinate with BiometricAuthManager for biometric prompts
 * - Provide fallback options (PIN, enrollment)
 * - Expose session remaining time
 *
 * State Management:
 * - authState: Current authentication state
 * - sessionState: Session validity status
 * - isBiometricEnrolled: Check if user has biometric
 * - remainingSessionTime: Seconds remaining in 15-min window
 *
 * Usage:
 * ```kotlin
 * val viewModel = AuthenticationViewModel(biometricAuthManager)
 *
 * LaunchedEffect(Unit) {
 *     viewModel.startAuthentication()
 * }
 *
 * val state = viewModel.authState.collectAsState().value
 * when (state) {
 *     is AuthState.Authenticated -> proceedToApp()
 *     is AuthState.NeedsBiometric -> showBiometricPrompt()
 *     is AuthState.NeedsEnrollment -> showEnrollmentScreen()
 *     is AuthState.Error -> showError(state.message)
 * }
 * ```
 */
class AuthenticationViewModel(
    private val biometricAuthManager: BiometricAuthManager,
    private val perOpAuthManager: PerOperationAuthManager? = null
) : BaseViewModel() {

    private companion object {
        private const val TAG = "AuthenticationViewModel"
    }

    // ========================================
    // Authentication State
    // ========================================

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _isBiometricEnrolled = MutableStateFlow(false)
    val isBiometricEnrolled: StateFlow<Boolean> = _isBiometricEnrolled.asStateFlow()

    private val _remainingSessionTime = MutableStateFlow<Long?>(null)
    val remainingSessionTime: StateFlow<Long?> = _remainingSessionTime.asStateFlow()

    private val _authAttempts = MutableStateFlow(0)
    val authAttempts: StateFlow<Int> = _authAttempts.asStateFlow()

    init {
        Log.d(TAG, "AuthenticationViewModel initialized")
        checkBiometricEnrollment()
    }

    // ========================================
    // Biometric Status Checks
    // ========================================

    private fun checkBiometricEnrollment() {
        _isBiometricEnrolled.value = biometricAuthManager.isBiometricEnrolled()
        Log.d(TAG, "Biometric enrolled: ${_isBiometricEnrolled.value}")
    }

    fun getBiometricStatus(): String = biometricAuthManager.getBiometricStatus()

    // ========================================
    // Authentication Flow
    // ========================================

    fun startAuthentication() {
        Log.d(TAG, "Starting authentication flow...")

        if (!biometricAuthManager.isBiometricAvailable()) {
            _authState.value = AuthState.NeedsEnrollment(
                message = getBiometricStatus()
            )
            return
        }

        if (!biometricAuthManager.isBiometricEnrolled()) {
            _authState.value = AuthState.NeedsEnrollment(
                message = "Nenhuma biometria cadastrada. Cadastre uma para continuar."
            )
            return
        }

        performBiometricAuthentication()
    }

    private fun performBiometricAuthentication() {
        _authState.value = AuthState.Authenticating
        _authAttempts.value = 0

        launchSafe {
            try {
                val result = biometricAuthManager.authenticate()

                when (result) {
                    is BiometricAuthResult.Success -> {
                        Log.d(TAG, "Authentication successful")
                        biometricAuthManager.extendSession()
                        _authState.value = AuthState.Authenticated
                    }

                    is BiometricAuthResult.UserCancelled -> {
                        Log.d(TAG, "User cancelled authentication")
                        _authState.value = AuthState.UserCancelled

                    }

                    is BiometricAuthResult.NeedsFallback -> {
                        Log.d(TAG, "Offering PIN fallback")
                        _authAttempts.value = result.retryCount
                        _authState.value = AuthState.NeedsPIN(
                            message = result.message
                        )
                    }

                    is BiometricAuthResult.Error -> {
                        Log.w(TAG, "Authentication error: ${result.message}")
                        _authAttempts.value++
                        _authState.value = AuthState.Error(
                            message = result.message,
                            isRecoverable = result.isRecoverable()
                        )
                    }

                    is BiometricAuthResult.Unavailable -> {
                        Log.w(TAG, "Biometric unavailable: ${result.reason}")
                        if (result.canUseFallback) {
                            _authState.value = AuthState.NeedsPIN(
                                message = result.reason
                            )
                        } else {
                            _authState.value = AuthState.NeedsEnrollment(
                                message = result.reason
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Authentication exception", e)
                _authState.value = AuthState.Error(
                    message = "Erro na autenticação: ${e.message ?: "desconhecido"}",
                    isRecoverable = true
                )
            }
        }
    }

    fun retryAuthentication() {
        Log.d(TAG, "Retrying authentication...")
        performBiometricAuthentication()
    }

    fun proceedWithPIN() {
        Log.d(TAG, "User chose PIN fallback")
        _authState.value = AuthState.EnteringPIN
    }

    fun validateAndCompletePIN(pin: String) {
        Log.d(TAG, "Validating PIN...")

        // Simplified PIN validation (4-6 digits)
        if (pin.length !in 4..6 || !pin.all { it.isDigit() }) {
            _authState.value = AuthState.PINError(
                message = "PIN deve ter 4-6 dígitos"
            )
            return
        }

        // In production, validate against encrypted stored PIN
        Log.d(TAG, "PIN validated successfully")
        biometricAuthManager.extendSession()
        _authState.value = AuthState.Authenticated
    }

    fun requestBiometricEnrollment() {
        Log.d(TAG, "User requested biometric enrollment")
        _authState.value = AuthState.BiometricEnrollmentNeeded
    }

    fun completeEnrollmentFlow() {
        Log.d(TAG, "Completing enrollment flow")
        checkBiometricEnrollment()
        if (_isBiometricEnrolled.value) {
            performBiometricAuthentication()
        } else {
            _authState.value = AuthState.Error(
                message = "Cadastro de biometria não concluído",
                isRecoverable = true
            )
        }
    }

    // ========================================
    // Session Management
    // ========================================

    fun checkSessionValidity() {
        val isValid = biometricAuthManager.isSessionValid()
        Log.d(TAG, "Session valid: $isValid")

        if (isValid) {
            _authState.value = AuthState.Authenticated
            updateRemainingSessionTime()
        } else {
            _authState.value = AuthState.SessionExpired
        }
    }

    private fun updateRemainingSessionTime() {
        val remaining = biometricAuthManager.getRemainingSessionTime()
        _remainingSessionTime.value = remaining
    }

    fun extendSession() {
        Log.d(TAG, "Extending session...")
        biometricAuthManager.extendSession()
        updateRemainingSessionTime()
    }

    fun logout() {
        Log.d(TAG, "User logout")
        biometricAuthManager.clearSession()
        _authState.value = AuthState.LoggedOut
    }

    fun dismissError() {
        _authState.value = AuthState.Idle
        clearError()
    }
}

// ========================================
// Authentication State Sealed Class
// ========================================

sealed class AuthState {
    object Idle : AuthState()
    object Authenticating : AuthState()
    object Authenticated : AuthState()
    object UserCancelled : AuthState()
    object SessionExpired : AuthState()
    object LoggedOut : AuthState()
    object EnteringPIN : AuthState()
    object BiometricEnrollmentNeeded : AuthState()

    data class NeedsPIN(val message: String) : AuthState()
    data class NeedsEnrollment(val message: String) : AuthState()
    data class Error(val message: String, val isRecoverable: Boolean = true) : AuthState()
    data class PINError(val message: String) : AuthState()
}
