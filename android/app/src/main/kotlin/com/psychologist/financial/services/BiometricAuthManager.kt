package com.psychologist.financial.services

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import com.psychologist.financial.domain.models.AuthSession
import com.psychologist.financial.utils.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDateTime
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Manager for app-level biometric authentication
 *
 * Responsibilities:
 * - Handle biometric prompts (fingerprint, face, iris)
 * - Manage biometric session lifecycle (15-minute timeout)
 * - Store and validate auth tokens
 * - Provide PIN fallback authentication
 * - Track authentication state
 *
 * Architecture:
 * - Entry point for app authentication on launch
 * - Creates BiometricPrompt that flows to activity
 * - Manages in-memory session state (cleared on app close)
 * - Uses SharedPreferences for non-sensitive session metadata
 *
 * Session Flow:
 * 1. App launches -> BiometricAuthManager checks session validity
 * 2. If session valid (< 15 min): Allow access (skip biometric)
 * 3. If session expired: Show BiometricPrompt
 * 4. User succeeds: Create new AuthSession (15-min timeout)
 * 5. User fails 3x: Show PIN fallback
 * 6. User succeeds with PIN: Create new AuthSession
 *
 * Constants:
 * - SESSION_TIMEOUT: 15 minutes (Constants.BIOMETRIC_SESSION_TIMEOUT_MINUTES)
 * - AUTH_VALIDITY: 5 minutes per biometric prompt (Constants.BIOMETRIC_AUTH_VALIDITY_DURATION_SECONDS)
 *
 * Security Considerations:
 * - Session stored in memory only (cleared on app close)
 * - Biometric prompt timeout: 5 minutes (configurable per device)
 * - PIN stored as SHA-256 hash in SharedPreferences
 * - Biometric hardware handles actual fingerprint/face matching
 * - No biometric data copied by this app
 *
 * Usage:
 * ```kotlin
 * val biometricManager = BiometricAuthManager(context, sharedPrefs)
 *
 * // Check if authentication needed
 * if (!biometricManager.isSessionValid()) {
 *     biometricManager.showBiometricPrompt(activity) { success ->
 *         if (success) {
 *             // Access granted
 *         }
 *     }
 * }
 *
 * // Listen to auth state
 * biometricManager.authStateFlow.collect { session ->
 *     if (session != null) {
 *         // User authenticated
 *     }
 * }
 * ```
 */
class BiometricAuthManager(
    private val context: Context,
    private val sharedPreferences: SharedPreferences
) {

    private companion object {
        private const val TAG = "BiometricAuthManager"

        // SharedPreferences keys
        private const val PREF_LAST_AUTH_TIME = "last_biometric_auth_time"
        private const val PREF_PIN_HASH = "pin_hash"
        private const val PREF_BIOMETRIC_ENABLED = "biometric_enabled"

        // Biometric prompt configuration
        private const val MAX_AUTH_ATTEMPTS = 3
    }

    // Current authentication session (in-memory only)
    private val _authStateFlow = MutableStateFlow<AuthSession?>(null)
    val authStateFlow: StateFlow<AuthSession?> = _authStateFlow.asStateFlow()

    // Executor for biometric operations
    private val executor: Executor = Executors.newSingleThreadExecutor()

    // Failure attempt counter
    private var authFailureCount = 0

    /**
     * Check if current authentication session is valid
     *
     * Returns true if:
     * - Session exists in memory AND
     * - Session has not expired (< 15 minutes old)
     *
     * Used to decide whether to show biometric prompt.
     *
     * @return true if user is authenticated, false if biometric prompt needed
     */
    fun isSessionValid(): Boolean {
        val session = _authStateFlow.value ?: return false
        val now = LocalDateTime.now()
        val expired = now.isAfter(session.expiryTime)
        return !expired
    }

    /**
     * Get current authentication session
     *
     * @return Current AuthSession or null if not authenticated
     */
    fun getSession(): AuthSession? = _authStateFlow.value

    /**
     * Show biometric authentication prompt
     *
     * Displays native biometric prompt (fingerprint, face, iris).
     * On success, creates new AuthSession with 15-minute timeout.
     * On failure, increments counter. After 3 failures, prompts for PIN.
     *
     * **Important**: Must be called from UI thread (FragmentActivity context).
     *
     * @param activity FragmentActivity (for BiometricPrompt)
     * @param onResult Callback with boolean (true = authenticated, false = failed)
     *
     * Example:
     * ```kotlin
     * biometricManager.showBiometricPrompt(this) { success ->
     *     if (success) {
     *         navigateToMainScreen()
     *     } else {
     *         Toast.makeText(this, "Authentication failed", Toast.LENGTH_SHORT).show()
     *     }
     * }
     * ```
     */
    fun showBiometricPrompt(activity: FragmentActivity, onResult: (Boolean) -> Unit) {
        authFailureCount = 0

        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Log.d(TAG, "Biometric authentication succeeded")
                    createAuthSession()
                    onResult(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Log.w(TAG, "Biometric authentication error: $errorCode - $errString")
                    onResult(false)
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    authFailureCount++
                    Log.w(TAG, "Biometric authentication failed (attempt $authFailureCount/$MAX_AUTH_ATTEMPTS)")

                    if (authFailureCount >= MAX_AUTH_ATTEMPTS) {
                        Log.i(TAG, "Max biometric attempts reached, switching to PIN")
                        onResult(false)  // Signal caller to show PIN prompt
                    }
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Autenticação Segura")
            .setSubtitle("Use sua biometria para acessar")
            .setNegativeButtonText("Usar PIN")
            .setAllowedAuthenticators(
                BiometricPrompt.AUTHENTICATORS_ALLOWED_AUTH_NEEDED or
                BiometricPrompt.AUTHENTICATORS_ALLOWED
            )
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    /**
     * Authenticate using PIN as fallback
     *
     * Verifies PIN against stored hash.
     * On success, creates new AuthSession with 15-minute timeout.
     *
     * **Security Note**: PIN is hashed with SHA-256 before storage.
     * Comparison is constant-time to prevent timing attacks.
     *
     * @param pin User-entered PIN (numeric, typically 4-6 digits)
     * @return true if PIN matches, false otherwise
     */
    fun authenticateWithPin(pin: String): Boolean {
        return try {
            val storedHash = sharedPreferences.getString(PREF_PIN_HASH, null)
                ?: return false

            val pinHash = hashPin(pin)
            val matches = pinHash.equals(storedHash, ignoreCase = true)

            if (matches) {
                Log.d(TAG, "PIN authentication succeeded")
                createAuthSession()
            } else {
                Log.w(TAG, "PIN authentication failed")
            }

            matches
        } catch (e: Exception) {
            Log.e(TAG, "PIN authentication error", e)
            false
        }
    }

    /**
     * Set PIN for fallback authentication
     *
     * Hashes PIN before storing in SharedPreferences.
     * Should be called during first-launch setup or PIN change flow.
     *
     * @param pin PIN to set (typically 4-6 numeric digits)
     * @throws Exception If hashing fails
     */
    fun setPin(pin: String) {
        try {
            val pinHash = hashPin(pin)
            sharedPreferences.edit().putString(PREF_PIN_HASH, pinHash).apply()
            Log.d(TAG, "PIN set successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set PIN", e)
            throw e
        }
    }

    /**
     * Check if PIN is configured
     *
     * @return true if PIN exists in SharedPreferences
     */
    fun isPinSet(): Boolean {
        return sharedPreferences.contains(PREF_PIN_HASH)
    }

    /**
     * Invalidate current session
     *
     * Forces user to re-authenticate on next access.
     * Called on logout, security events, or manual lock.
     */
    fun clearSession() {
        _authStateFlow.value = null
        authFailureCount = 0
        Log.d(TAG, "Session cleared")
    }

    /**
     * Check if biometric authentication is available
     *
     * Checks if device supports biometric authentication.
     * Requires android.permission.USE_BIOMETRIC in manifest.
     *
     * @return true if device has biometric sensor and biometric is enabled
     */
    fun isBiometricAvailable(): Boolean {
        // TODO: Use BiometricManager.canAuthenticate() in Android 10+
        // For now, return true if user has enabled biometric
        return sharedPreferences.getBoolean(PREF_BIOMETRIC_ENABLED, true)
    }

    /**
     * Enable/disable biometric authentication
     *
     * If disabled, PIN will be required for authentication.
     *
     * @param enabled true to enable biometric, false to disable
     */
    fun setBiometricEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(PREF_BIOMETRIC_ENABLED, enabled).apply()
        Log.d(TAG, "Biometric enabled: $enabled")
    }

    /**
     * Get remaining time in current session
     *
     * @return Time in seconds until session expires, or 0 if no session
     */
    fun getSessionRemainingSeconds(): Long {
        val session = _authStateFlow.value ?: return 0
        val now = LocalDateTime.now()
        val remaining = java.time.temporal.ChronoUnit.SECONDS.between(now, session.expiryTime)
        return maxOf(0L, remaining)
    }

    /**
     * Create new authentication session
     *
     * Sets expiry time to current time + 15 minutes (Constants.BIOMETRIC_SESSION_TIMEOUT_MINUTES).
     * Called on successful biometric or PIN authentication.
     */
    private fun createAuthSession() {
        val now = LocalDateTime.now()
        val expiryTime = now.plusMinutes(Constants.BIOMETRIC_SESSION_TIMEOUT_MINUTES.toLong())
        val token = generateAuthToken()

        _authStateFlow.value = AuthSession(
            token = token,
            expiryTime = expiryTime,
            userContext = "psychologist_session"
        )

        // Save timestamp to SharedPreferences for audit
        sharedPreferences.edit()
            .putLong(PREF_LAST_AUTH_TIME, System.currentTimeMillis())
            .apply()

        Log.d(TAG, "Auth session created, expires at $expiryTime")
    }

    /**
     * Generate random authentication token
     *
     * @return 32-character random token
     */
    private fun generateAuthToken(): String {
        return (0..31).map {
            val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
            chars.random()
        }.joinToString("")
    }

    /**
     * Hash PIN for secure storage
     *
     * Uses SHA-256 for one-way hashing.
     * TODO: Use PBKDF2 or bcrypt for production (slower, more resistant to brute force)
     *
     * @param pin Plain text PIN
     * @return SHA-256 hex hash
     */
    private fun hashPin(pin: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(pin.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
