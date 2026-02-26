package com.psychologist.financial.services

import android.util.Log
import com.psychologist.financial.domain.models.OperationType
import com.psychologist.financial.domain.models.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.max

/**
 * Session Manager
 *
 * Manages user session lifecycle, including authentication, timeout tracking,
 * and per-operation authentication requirements.
 *
 * Responsibilities:
 * - Track session state (Authenticated, Expired, BiometricRequired, Unauthenticated)
 * - Monitor 15-minute inactivity timeout
 * - Detect and trigger re-authentication when session expires
 * - Request biometric authentication for sensitive operations (payments, exports)
 * - Calculate remaining session time
 * - Provide session status to UI
 *
 * Session Lifecycle:
 * 1. Unauthenticated → User launches app
 * 2. Authenticated → User completes biometric authentication
 * 3. BiometricRequired → User attempts sensitive operation (payment/export)
 * 4. Expired → 15-minute inactivity timeout elapses
 * 5. Unauthenticated → User logs out (session cleared)
 *
 * Usage:
 * ```kotlin
 * val sessionManager = SessionManager()
 *
 * // Start session after successful authentication
 * sessionManager.startSession()
 *
 * // Monitor session state
 * sessionManager.sessionState.collect { state ->
 *     when (state) {
 *         is SessionState.Authenticated -> showApp()
 *         is SessionState.Expired -> showReauthenticationScreen()
 *         is SessionState.BiometricRequired -> showBiometricPrompt()
 *         else -> showLoginScreen()
 *     }
 * }
 *
 * // Request per-operation authentication
 * if (needsPaymentVerification()) {
 *     sessionManager.requireBiometricForOperation(OperationType.PAYMENT)
 * }
 *
 * // Check session validity
 * if (!sessionManager.isSessionValid()) {
 *     sessionManager.expireSession("User initiated logout")
 * }
 * ```
 *
 * @see SessionState
 * @see OperationType
 */
class SessionManager {
    private companion object {
        private const val TAG = "SessionManager"
        private const val SESSION_TIMEOUT_MILLIS = 15 * 60 * 1000L // 15 minutes
        private const val SESSION_TIMEOUT_SECONDS = 15 * 60L // 900 seconds
    }

    // ========================================
    // Session State Management
    // ========================================

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Unauthenticated)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private var lastActivityTime: LocalDateTime? = null
    private var sessionStartTime: LocalDateTime? = null

    init {
        Log.d(TAG, "SessionManager initialized")
    }

    // ========================================
    // Session Lifecycle
    // ========================================

    /**
     * Start a new authenticated session
     *
     * Called after successful biometric/PIN authentication.
     * Initializes 15-minute timeout window.
     */
    fun startSession() {
        Log.d(TAG, "Starting new session...")
        val now = LocalDateTime.now()
        sessionStartTime = now
        lastActivityTime = now

        val expiresAt = now.plusMinutes(15)
        _sessionState.value = SessionState.Authenticated(
            authenticatedAt = now,
            expiresAt = expiresAt,
            remainingSeconds = SESSION_TIMEOUT_SECONDS
        )

        Log.d(TAG, "Session started. Expires at: $expiresAt")
    }

    /**
     * Extend current session for 15 minutes
     *
     * Called on user activity (app brought to foreground, action performed).
     * Resets the inactivity timeout if session is still valid.
     *
     * @return true if session was successfully extended, false if already expired
     */
    fun extendSession(): Boolean {
        val currentState = _sessionState.value
        if (currentState !is SessionState.Authenticated) {
            Log.w(TAG, "Cannot extend inactive session")
            return false
        }

        if (!isSessionValid()) {
            Log.w(TAG, "Session already expired, cannot extend")
            return false
        }

        val now = LocalDateTime.now()
        lastActivityTime = now

        val expiresAt = now.plusMinutes(15)
        _sessionState.value = currentState.copy(
            expiresAt = expiresAt,
            remainingSeconds = SESSION_TIMEOUT_SECONDS
        )

        Log.d(TAG, "Session extended. New expiration: $expiresAt")
        return true
    }

    /**
     * Expire the current session
     *
     * Transitions session to Expired state, forcing re-authentication.
     * Called on timeout or manual logout.
     *
     * @param reason Reason for expiration (for logging and user messaging)
     */
    fun expireSession(reason: String = "Sessão expirada por inatividade") {
        Log.d(TAG, "Expiring session: $reason")
        _sessionState.value = SessionState.Expired(
            expiredAt = LocalDateTime.now(),
            reason = reason
        )
        lastActivityTime = null
        sessionStartTime = null
    }

    /**
     * Clear session (logout)
     *
     * Transitions to Unauthenticated state.
     * Called when user explicitly logs out.
     */
    fun clearSession() {
        Log.d(TAG, "Clearing session (user logout)")
        _sessionState.value = SessionState.Unauthenticated
        lastActivityTime = null
        sessionStartTime = null
    }

    // ========================================
    // Session Validity Checks
    // ========================================

    /**
     * Check if current session is valid and not expired
     *
     * @return true if session is Authenticated and remaining time > 0
     */
    fun isSessionValid(): Boolean {
        val currentState = _sessionState.value
        if (currentState !is SessionState.Authenticated) {
            return false
        }

        val remainingSeconds = getRemainingSessionTime()
        val isValid = remainingSeconds > 0

        Log.d(TAG, "Session validity check: isValid=$isValid, remaining=${remainingSeconds}s")
        return isValid
    }

    /**
     * Check if session exists (user is authenticated or biometric required)
     *
     * @return true if not in Unauthenticated or Expired state
     */
    fun hasActiveSession(): Boolean {
        val currentState = _sessionState.value
        return currentState is SessionState.Authenticated ||
               currentState is SessionState.BiometricRequired
    }

    /**
     * Get remaining session time in seconds
     *
     * @return Seconds remaining until session expires, or 0 if already expired
     */
    fun getRemainingSessionTime(): Long {
        val currentState = _sessionState.value
        if (currentState !is SessionState.Authenticated) {
            return 0
        }

        val now = LocalDateTime.now()
        val remaining = ChronoUnit.SECONDS.between(now, currentState.expiresAt)
        return max(0, remaining)
    }

    /**
     * Check if session is about to expire (less than 2 minutes remaining)
     *
     * Used to show warning UI before session expiration.
     *
     * @return true if remaining time < 120 seconds and session is Authenticated
     */
    fun isSessionAboutToExpire(): Boolean {
        val currentState = _sessionState.value
        if (currentState !is SessionState.Authenticated) {
            return false
        }
        return currentState.isAboutToExpire()
    }

    // ========================================
    // Per-Operation Authentication
    // ========================================

    /**
     * Request biometric authentication for a sensitive operation
     *
     * Transitions session to BiometricRequired state.
     * User must complete biometric authentication to proceed.
     * Used for payments and data exports.
     *
     * @param operationType Type of operation (PAYMENT or EXPORT)
     * @param reason Human-readable description of why biometric is required
     *
     * @return true if biometric requirement was set, false if session not valid
     */
    fun requireBiometricForOperation(
        operationType: OperationType,
        reason: String = "Operação requer autenticação adicional"
    ): Boolean {
        if (!isSessionValid()) {
            Log.w(TAG, "Cannot require biometric: session not valid")
            return false
        }

        Log.d(TAG, "Requiring biometric for operation: ${operationType.name}")
        _sessionState.value = SessionState.BiometricRequired(
            operation = operationType,
            reason = reason,
            requestedAt = LocalDateTime.now()
        )

        return true
    }

    /**
     * Clear per-operation biometric requirement after successful authentication
     *
     * Restores session to Authenticated state after per-operation biometric success.
     * User may now proceed with the operation.
     */
    fun completeBiometricAuthentication() {
        val currentState = _sessionState.value
        if (currentState !is SessionState.BiometricRequired) {
            Log.w(TAG, "No pending biometric authentication to complete")
            return
        }

        Log.d(TAG, "Completing biometric authentication for ${currentState.operation.name}")

        // Restore to Authenticated state with updated expiration
        extendSession()
    }

    /**
     * Cancel per-operation biometric requirement
     *
     * User cancelled the biometric prompt without authenticating.
     * Session remains Authenticated if not expired.
     */
    fun cancelBiometricAuthentication() {
        val currentState = _sessionState.value
        if (currentState !is SessionState.BiometricRequired) {
            Log.w(TAG, "No pending biometric authentication to cancel")
            return
        }

        Log.d(TAG, "Cancelling biometric authentication")

        // Restore to Authenticated state if session still valid
        if (isSessionValid()) {
            extendSession()
        } else {
            expireSession("Sessão expirou durante autenticação biométrica")
        }
    }

    // ========================================
    // Session Monitoring & Status
    // ========================================

    /**
     * Get current session state
     *
     * @return Current SessionState (Authenticated, Expired, BiometricRequired, or Unauthenticated)
     */
    fun getCurrentState(): SessionState = _sessionState.value

    /**
     * Get session status message for UI display
     *
     * @return Human-readable session status
     */
    fun getSessionStatus(): String = _sessionState.value.getDisplayMessage()

    /**
     * Get detailed session information for logging/debugging
     *
     * @return Map with session details (state, remaining time, authenticated at, etc.)
     */
    fun getSessionInfo(): Map<String, Any> {
        val currentState = _sessionState.value
        val info = mutableMapOf<String, Any>(
            "state" to currentState::class.simpleName.orEmpty(),
            "timestamp" to LocalDateTime.now().toString()
        )

        when (currentState) {
            is SessionState.Authenticated -> {
                info["remainingSeconds"] = getRemainingSessionTime()
                info["expiresAt"] = currentState.expiresAt.toString()
                info["authenticatedAt"] = currentState.authenticatedAt.toString()
                info["aboutToExpire"] = currentState.isAboutToExpire()
            }
            is SessionState.Expired -> {
                info["expiredAt"] = currentState.expiredAt.toString()
                info["reason"] = currentState.reason
            }
            is SessionState.BiometricRequired -> {
                info["operation"] = currentState.operation.name
                info["reason"] = currentState.reason
                info["requestedAt"] = currentState.requestedAt.toString()
            }
            else -> { /* No additional info */ }
        }

        return info
    }

    /**
     * Calculate session duration (from start to now)
     *
     * Useful for analytics and logging.
     *
     * @return Duration in seconds, or 0 if no active session
     */
    fun getSessionDuration(): Long {
        val startTime = sessionStartTime ?: return 0
        return ChronoUnit.SECONDS.between(startTime, LocalDateTime.now())
    }
}
