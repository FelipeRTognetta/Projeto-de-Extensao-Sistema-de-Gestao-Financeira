package com.psychologist.financial.services

import android.util.Log
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import com.psychologist.financial.utils.Constants
import java.time.LocalDateTime
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Manager for per-operation biometric authentication
 *
 * Responsibilities:
 * - Require biometric/PIN confirmation for sensitive operations
 * - Operations: Payment recording, patient deletion (future)
 * - Track per-operation auth validity (5-minute window)
 * - Prevent unauthorized modifications during session
 *
 * Architecture:
 * - Called before recording payment or sensitive changes
 * - User must provide biometric within 5-minute window (Constants.BIOMETRIC_AUTH_VALIDITY_DURATION_SECONDS)
 * - Cannot reuse app-level auth session (must provide new biometric)
 * - Useful for applications like:
 *   - Payment authorization
 *   - Patient deletion confirmation
 *   - Large balance adjustments
 *
 * Security Model:
 * - Per-operation auth is independent of app-level auth
 * - Even with valid app session, user must re-authenticate for sensitive operations
 * - This prevents unauthorized payments if device is stolen with active session
 * - 5-minute validity window for each operation
 *
 * Usage:
 * ```kotlin
 * val perOpAuthManager = PerOperationAuthManager()
 *
 * // Before recording payment
 * perOpAuthManager.requireBiometricAuth(
 *     activity = this,
 *     operationName = "Registrar Pagamento",
 *     onAuthSuccess = {
 *         recordPayment(...)  // Safe to proceed
 *     }
 * )
 * ```
 *
 * Constants:
 * - AUTH_VALIDITY_DURATION_SECONDS: 5 minutes (from Constants)
 * - Timeout is hard-coded in BiometricPrompt (device-level)
 */
class PerOperationAuthManager {

    private companion object {
        private const val TAG = "PerOperationAuthManager"
    }

    // Track last successful per-operation auth and operation name
    private var lastAuthTime: LocalDateTime? = null
    private var lastAuthOperation: String? = null

    // Executor for biometric operations
    private val executor: Executor = Executors.newSingleThreadExecutor()

    /**
     * Require biometric authentication for a sensitive operation
     *
     * Shows BiometricPrompt and calls onSuccess only if user authenticates.
     * Auth is valid for 5 minutes (Constants.BIOMETRIC_AUTH_VALIDITY_DURATION_SECONDS).
     *
     * **Important**: Must be called from UI thread (FragmentActivity context).
     *
     * @param activity FragmentActivity (for BiometricPrompt)
     * @param operationName Friendly name of operation (e.g., "Registrar Pagamento")
     * @param onAuthSuccess Callback called only if authentication succeeds
     * @param onAuthFailed Callback called if authentication fails or times out
     *
     * Example:
     * ```kotlin
     * perOpAuthManager.requireBiometricAuth(
     *     activity = this,
     *     operationName = "Registrar Pagamento de R$ 150,00",
     *     onAuthSuccess = {
     *         // User authenticated, proceed with payment
     *         recordPayment(payment)
     *     },
     *     onAuthFailed = {
     *         // Show error message
     *         Toast.makeText(this, "Autenticação necessária", Toast.LENGTH_SHORT).show()
     *     }
     * )
     * ```
     */
    fun requireBiometricAuth(
        activity: FragmentActivity,
        operationName: String,
        onAuthSuccess: () -> Unit,
        onAuthFailed: (() -> Unit)? = null
    ) {
        // Check if recent auth for same operation exists
        if (isAuthValid(operationName)) {
            Log.d(TAG, "Using cached auth for operation: $operationName")
            onAuthSuccess()
            return
        }

        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Log.d(TAG, "Per-operation biometric auth succeeded for: $operationName")
                    recordAuthSuccess(operationName)
                    onAuthSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Log.w(TAG, "Per-operation auth error ($errorCode): $errString")
                    onAuthFailed?.invoke()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Log.w(TAG, "Per-operation biometric auth failed")
                    onAuthFailed?.invoke()
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Confirmar Operação")
            .setSubtitle(operationName)
            .setDescription("Use sua biometria para confirmar")
            .setNegativeButtonText("Cancelar")
            .setAllowedAuthenticators(
                BiometricPrompt.AUTHENTICATORS_ALLOWED_AUTH_NEEDED or
                BiometricPrompt.AUTHENTICATORS_ALLOWED
            )
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    /**
     * Verify PIN for sensitive operation
     *
     * Alternative to biometric (e.g., when biometric fails or unavailable).
     * Caller should provide BiometricAuthManager for PIN verification.
     *
     * @param pin PIN entered by user
     * @param biometricAuthManager BiometricAuthManager instance for PIN verification
     * @param operationName Friendly name of operation
     * @return true if PIN matches, false otherwise
     *
     * Example:
     * ```kotlin
     * if (perOpAuthManager.verifyPinForOperation(pin, biometricMgr, "Pagamento")) {
     *     recordPayment(payment)
     * }
     * ```
     */
    fun verifyPinForOperation(
        pin: String,
        biometricAuthManager: BiometricAuthManager,
        operationName: String
    ): Boolean {
        val authenticated = biometricAuthManager.authenticateWithPin(pin)
        if (authenticated) {
            recordAuthSuccess(operationName)
            Log.d(TAG, "PIN authentication succeeded for: $operationName")
        } else {
            Log.w(TAG, "PIN authentication failed for: $operationName")
        }
        return authenticated
    }

    /**
     * Clear all cached per-operation authentications
     *
     * Called on logout, security events, or manual lock.
     */
    fun clearCachedAuth() {
        lastAuthTime = null
        lastAuthOperation = null
        Log.d(TAG, "Cached per-operation auth cleared")
    }

    /**
     * Get remaining validity time for current operation
     *
     * @return Seconds remaining, or 0 if no valid auth
     */
    fun getRemainingAuthSeconds(): Long {
        if (lastAuthTime == null) return 0

        val now = LocalDateTime.now()
        val validityDuration = Constants.BIOMETRIC_AUTH_VALIDITY_DURATION_SECONDS.toLong()
        val expiryTime = lastAuthTime!!.plusSeconds(validityDuration)
        val remaining = java.time.temporal.ChronoUnit.SECONDS.between(now, expiryTime)

        return maxOf(0L, remaining)
    }

    /**
     * Check if authentication is still valid for operation
     *
     * Auth is valid for 5 minutes (Constants.BIOMETRIC_AUTH_VALIDITY_DURATION_SECONDS).
     * Can be reused for same operation within this window.
     *
     * @param operationName Name of operation to check
     * @return true if valid auth exists for this operation
     */
    private fun isAuthValid(operationName: String): Boolean {
        val authTime = lastAuthTime ?: return false
        val lastOp = lastAuthOperation ?: return false

        // Check operation name matches
        if (lastOp != operationName) {
            return false
        }

        // Check validity window (5 minutes)
        val now = LocalDateTime.now()
        val validityDuration = Constants.BIOMETRIC_AUTH_VALIDITY_DURATION_SECONDS.toLong()
        val expiryTime = authTime.plusSeconds(validityDuration)

        return now.isBefore(expiryTime)
    }

    /**
     * Record successful authentication for operation
     *
     * Stores timestamp and operation name for 5-minute reuse window.
     *
     * @param operationName Name of operation
     */
    private fun recordAuthSuccess(operationName: String) {
        lastAuthTime = LocalDateTime.now()
        lastAuthOperation = operationName
        Log.d(TAG, "Auth recorded for operation: $operationName, valid for ${Constants.BIOMETRIC_AUTH_VALIDITY_DURATION_SECONDS}s")
    }
}
