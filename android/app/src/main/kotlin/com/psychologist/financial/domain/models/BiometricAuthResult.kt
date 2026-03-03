package com.psychologist.financial.domain.models

/**
 * Biometric Authentication Result
 *
 * Represents the outcome of a biometric authentication attempt.
 * Uses sealed class for type-safe result handling.
 *
 * Features:
 * - Success: Authentication successful with crypto object
 * - Error: Authentication failed with error message
 * - UserCancelled: User dismissed authentication prompt
 * - Unavailable: Biometric auth not available on device
 * - NeedsFallback: Biometric failed, should use PIN fallback
 *
 * Architecture:
 * - Sealed class ensures all cases handled
 * - Type-safe error information
 * - Extensible for future auth methods
 * - Clear distinction between user action and system error
 *
 * Usage:
 * ```kotlin
 * val result = biometricManager.authenticate()
 *
 * when (result) {
 *     is BiometricAuthResult.Success -> {
 *         showSuccess("Authentication successful")
 *         proceedWithEncryption(result.cryptoObject)
 *     }
 *     is BiometricAuthResult.Error -> {
 *         showError(result.message)
 *     }
 *     is BiometricAuthResult.UserCancelled -> {
 *         // User dismissed prompt
 *     }
 *     is BiometricAuthResult.NeedsFallback -> {
 *         showPINScreen()
 *     }
 * }
 * ```
 *
 * @see androidx.biometric.BiometricPrompt.CryptoObject
 */
sealed class BiometricAuthResult {

    /**
     * Authentication successful
     *
     * Biometric authentication completed successfully.
     * CryptoObject can be used for encrypted operations (if available).
     *
     * @property cryptoObject Optional CryptoObject for encryption operations (null for app-level auth)
     * @property timestamp When authentication occurred
     */
    data class Success(
        val cryptoObject: androidx.biometric.BiometricPrompt.CryptoObject? = null,
        val timestamp: Long = System.currentTimeMillis()
    ) : BiometricAuthResult() {
        /**
         * Check if crypto operations are available
         *
         * @return true if CryptoObject is present for encryption
         */
        fun hasCryptoObject(): Boolean = cryptoObject != null
    }

    /**
     * Authentication failed with error
     *
     * Biometric authentication failed for a system/hardware reason.
     * User should be shown error message and offered retry or fallback option.
     *
     * Error codes:
     * - BiometricPrompt.ERROR_HW_UNAVAILABLE: Biometric hardware unavailable
     * - BiometricPrompt.ERROR_UNABLE_TO_PROCESS: Biometric processor busy
     * - BiometricPrompt.ERROR_TIMEOUT: Authentication timeout
     * - BiometricPrompt.ERROR_NO_SPACE: Biometric enrollment not available
     * - BiometricPrompt.ERROR_CANCELED: System cancelled (not user)
     * - BiometricPrompt.ERROR_HW_NOT_PRESENT: No biometric hardware
     *
     * @property message User-friendly error message (Portuguese)
     * @property errorCode BiometricPrompt error code
     * @property exception Optional underlying exception
     */
    data class Error(
        val message: String,
        val errorCode: Int? = null,
        val exception: Exception? = null
    ) : BiometricAuthResult() {
        /**
         * Get localized error message for UI display
         */
        fun getDisplayMessage(): String = message

        /**
         * Check if error is recoverable
         *
         * @return true if user should retry
         */
        fun isRecoverable(): Boolean = errorCode != androidx.biometric.BiometricPrompt.ERROR_NO_SPACE
    }

    /**
     * User cancelled authentication
     *
     * User dismissed the biometric prompt without attempting authentication.
     * This is a normal user action, not an error.
     *
     * @property reason Optional reason for cancellation
     */
    data class UserCancelled(
        val reason: String? = null
    ) : BiometricAuthResult()

    /**
     * Biometric authentication unavailable
     *
     * Device does not have biometric capability or biometric is not enrolled.
     * User should be guided to enroll biometrics or use PIN fallback.
     *
     * Reasons:
     * - BIOMETRIC_UNAVAILABLE_NO_HARDWARE: Device lacks biometric hardware
     * - BIOMETRIC_UNAVAILABLE_NO_ENROLLMENT: Biometric enrolled but not available currently
     * - BIOMETRIC_UNAVAILABLE_SECURITY_UPDATE_REQUIRED: Requires security update
     *
     * @property reason Description of why biometric is unavailable
     * @property canUseFallback Whether PIN fallback is available
     */
    data class Unavailable(
        val reason: String,
        val canUseFallback: Boolean = true
    ) : BiometricAuthResult()

    /**
     * Biometric auth failed, needs PIN fallback
     *
     * Biometric authentication failed and user should be offered PIN as fallback.
     * Returned after biometric errors that support fallback recovery.
     *
     * @property message Error message explaining why fallback needed
     * @property retryCount Number of failed attempts
     */
    data class NeedsFallback(
        val message: String,
        val retryCount: Int = 1
    ) : BiometricAuthResult()

    /**
     * Companion object with factory methods
     */
    companion object {
        /**
         * Create success result
         */
        fun success(cryptoObject: androidx.biometric.BiometricPrompt.CryptoObject? = null): Success =
            Success(cryptoObject)

        /**
         * Create error result
         */
        fun error(message: String, errorCode: Int? = null, exception: Exception? = null): Error =
            Error(message, errorCode, exception)

        /**
         * Create cancelled result
         */
        fun cancelled(reason: String? = null): UserCancelled =
            UserCancelled(reason)

        /**
         * Create unavailable result
         */
        fun unavailable(reason: String, canUseFallback: Boolean = true): Unavailable =
            Unavailable(reason, canUseFallback)

        /**
         * Create needs fallback result
         */
        fun needsFallback(message: String, retryCount: Int = 1): NeedsFallback =
            NeedsFallback(message, retryCount)
    }
}
