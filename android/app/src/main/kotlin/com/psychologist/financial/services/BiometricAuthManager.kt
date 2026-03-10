package com.psychologist.financial.services

import com.psychologist.financial.utils.AppLogger
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.psychologist.financial.domain.models.BiometricAuthResult
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Biometric Authentication Manager
 *
 * Handles app-level biometric/PIN authentication at startup.
 * Implements single authentication with 15-minute session timeout.
 *
 * Responsibilities:
 * - Check biometric availability and enrollment
 * - Display biometric prompt to user
 * - Handle biometric authentication results
 * - Manage 15-minute session timeout
 * - Provide PIN fallback option
 *
 * Architecture:
 * - Uses androidx.biometric.BiometricPrompt (modern, unified API)
 * - Supports fingerprint, face, iris biometrics
 * - Class 2 biometrics acceptable for app-level auth
 * - PIN fallback enabled for all scenarios
 * - Session tracked via timestamp (15 minutes)
 *
 * Usage:
 * ```kotlin
 * val authManager = BiometricAuthManager(fragmentActivity)
 *
 * when (val result = authManager.authenticate()) {
 *     is BiometricAuthResult.Success -> proceedToApp()
 *     is BiometricAuthResult.UserCancelled -> finishApp()
 *     is BiometricAuthResult.NeedsFallback -> showPINScreen()
 *     is BiometricAuthResult.Error -> showError(result.message)
 * }
 *
 * if (!authManager.isSessionValid()) {
 *     showAuthenticationScreen()
 * }
 * ```
 *
 * @property fragmentActivity FragmentActivity for showing biometric prompt
 */
class BiometricAuthManager(
    private val fragmentActivity: FragmentActivity
) {

    private companion object {
        private const val TAG = "BiometricAuthManager"
        private const val SESSION_TIMEOUT_MILLIS = 15 * 60 * 1000L
    }

    private var lastAuthTime: Long = 0
    private val biometricManager: BiometricManager = BiometricManager.from(fragmentActivity)

    fun isBiometricAvailable(): Boolean {
        return try {
            val canAuthenticate = biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            when (canAuthenticate) {
                BiometricManager.BIOMETRIC_SUCCESS,
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> true
                else -> false
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Error checking biometric availability", e)
            false
        }
    }

    fun isBiometricEnrolled(): Boolean {
        return try {
            val canAuthenticate = biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS
        } catch (e: Exception) {
            AppLogger.w(TAG, "Error checking biometric enrollment", e)
            false
        }
    }

    fun getBiometricStatus(): String {
        return try {
            val canAuthenticate = biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            when (canAuthenticate) {
                BiometricManager.BIOMETRIC_SUCCESS ->
                    "Autenticação biométrica disponível"
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                    "Dispositivo não possui hardware biométrico"
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                    "Hardware biométrico indisponível"
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                    "Nenhuma biometria cadastrada"
                BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
                    "Atualização de segurança necessária"
                else -> "Status biométrico desconhecido"
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Error getting biometric status", e)
            "Erro ao verificar biometria"
        }
    }

    suspend fun authenticate(): BiometricAuthResult = suspendCancellableCoroutine { continuation ->
        try {
            AppLogger.d(TAG, "Starting biometric authentication...")

            // Check that at least one credential method is available (biometric OR device credential)
            val canAuth = biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            if (canAuth == BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE) {
                continuation.resume(BiometricAuthResult.Unavailable("Nenhum método de autenticação disponível"))
                return@suspendCancellableCoroutine
            }

            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    super.onAuthenticationSucceeded(result)
                    AppLogger.d(TAG, "Authentication succeeded")
                    lastAuthTime = System.currentTimeMillis()
                    continuation.resume(BiometricAuthResult.Success(result.cryptoObject))
                }

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence
                ) {
                    super.onAuthenticationError(errorCode, errString)
                    AppLogger.w(TAG, "Authentication error: $errorCode - $errString")
                    val message = translateErrorMessage(errorCode)
                    continuation.resume(BiometricAuthResult.Error(message, errorCode))
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // Partial failure (e.g. finger not recognised) — prompt stays open, no action needed
                    AppLogger.w(TAG, "Authentication attempt failed, prompt remains open")
                }
            }
            val biometricPrompt = BiometricPrompt(
                fragmentActivity,
                ContextCompat.getMainExecutor(fragmentActivity),
                callback
            )

            // BIOMETRIC_WEAK or DEVICE_CREDENTIAL: the system shows biometric first;
            // if unavailable or the user skips it, the OS presents the device lock screen
            // (PIN, pattern, password — whatever the user has set). No custom PIN screen needed.
            // NOTE: setNegativeButtonText must NOT be set when DEVICE_CREDENTIAL is allowed.
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Autenticação de Segurança")
                .setSubtitle("Use sua biometria ou credencial do dispositivo")
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or
                            BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                .build()

            biometricPrompt.authenticate(promptInfo)

        } catch (e: Exception) {
            AppLogger.e(TAG, "Error during authentication setup", e)
            continuation.resume(BiometricAuthResult.Error("Erro na autenticação: ${e.message ?: "desconhecido"}", exception = e))
        }
    }

    fun isSessionValid(): Boolean {
        if (lastAuthTime == 0L) return false
        val elapsedTime = System.currentTimeMillis() - lastAuthTime
        val isValid = elapsedTime < SESSION_TIMEOUT_MILLIS
        if (!isValid) AppLogger.d(TAG, "Session expired after ${elapsedTime / 1000}s")
        return isValid
    }

    fun extendSession() {
        lastAuthTime = System.currentTimeMillis()
        AppLogger.d(TAG, "Session extended")
    }

    fun getRemainingSessionTime(): Long? {
        if (lastAuthTime == 0L) return null
        val elapsedTime = System.currentTimeMillis() - lastAuthTime
        val remaining = SESSION_TIMEOUT_MILLIS - elapsedTime
        return if (remaining > 0) remaining / 1000 else 0
    }

    fun getSessionTimeoutSeconds(): Long = SESSION_TIMEOUT_MILLIS / 1000

    fun clearSession() {
        lastAuthTime = 0
        AppLogger.d(TAG, "Session cleared")
    }

    private fun translateErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            BiometricPrompt.ERROR_HW_UNAVAILABLE -> "Hardware biométrico indisponível no momento"
            BiometricPrompt.ERROR_UNABLE_TO_PROCESS -> "Erro ao processar biometria. Tente novamente"
            BiometricPrompt.ERROR_TIMEOUT -> "Tempo limite excedido. Tente novamente"
            BiometricPrompt.ERROR_NO_SPACE -> "Sem espaço para autenticação"
            BiometricPrompt.ERROR_CANCELED -> "Autenticação cancelada"
            BiometricPrompt.ERROR_HW_NOT_PRESENT -> "Dispositivo não possui hardware biométrico"
            BiometricPrompt.ERROR_NO_BIOMETRICS -> "Nenhuma biometria registrada no dispositivo"
            BiometricPrompt.ERROR_SECURITY_UPDATE_REQUIRED -> "Atualização de segurança necessária para biometria"
            else -> "Erro na autenticação biométrica. Tente novamente"
        }
    }

    // With DEVICE_CREDENTIAL fallback, the OS handles all credential types natively.
    // This method is kept for completeness but shouldOfferFallback logic is no longer used
    // in authenticate() — errors are forwarded directly as BiometricAuthResult.Error.
    private fun shouldOfferFallback(errorCode: Int): Boolean {
        return when (errorCode) {
            BiometricPrompt.ERROR_TIMEOUT,
            BiometricPrompt.ERROR_UNABLE_TO_PROCESS,
            BiometricPrompt.ERROR_HW_UNAVAILABLE -> true
            else -> false
        }
    }
}
