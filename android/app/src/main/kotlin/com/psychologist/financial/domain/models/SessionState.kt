package com.psychologist.financial.domain.models

import java.time.LocalDateTime

/**
 * Session State
 *
 * Represents the authentication session state for the application.
 * Tracks user authentication status, session expiration, and biometric requirements.
 *
 * States:
 * - Authenticated: User is authenticated and session is active
 * - Expired: User session has expired and re-authentication is required
 * - BiometricRequired: Per-operation biometric authentication required
 * - Unauthenticated: User is not authenticated (app startup)
 *
 * @sealed Ensures exhaustive when() statements for state handling
 */
sealed class SessionState {
    /**
     * User is authenticated and session is active
     *
     * @property authenticatedAt Timestamp when user was authenticated
     * @property expiresAt Expected expiration time (15 minutes after authentication)
     * @property remainingSeconds Seconds remaining before session expires
     */
    data class Authenticated(
        val authenticatedAt: LocalDateTime,
        val expiresAt: LocalDateTime,
        val remainingSeconds: Long
    ) : SessionState() {
        /**
         * Check if session is still valid (not expired)
         *
         * @return true if remaining time > 0
         */
        fun isValid(): Boolean = remainingSeconds > 0

        /**
         * Get human-readable session status
         *
         * @return Status message with time remaining
         */
        fun getStatusMessage(): String = when {
            remainingSeconds > 300 -> "Sessão ativa"
            remainingSeconds > 60 -> "Sessão expirando em ${remainingSeconds / 60} minuto(s)"
            else -> "Sessão expirando em ${remainingSeconds} segundo(s)"
        }

        /**
         * Check if session is about to expire (less than 2 minutes)
         *
         * @return true if remaining time < 120 seconds
         */
        fun isAboutToExpire(): Boolean = remainingSeconds < 120
    }

    /**
     * User session has expired and re-authentication is required
     *
     * @property expiredAt Timestamp when session expired
     * @property reason Reason for expiration (inactivity, manual logout, etc.)
     */
    data class Expired(
        val expiredAt: LocalDateTime,
        val reason: String = "Sessão expirada por inatividade"
    ) : SessionState() {
        /**
         * Get human-readable expiration message
         *
         * @return Expiration reason message
         */
        fun getExpirationMessage(): String = reason
    }

    /**
     * Per-operation biometric authentication required
     *
     * Used when user attempts a sensitive operation (payment, data export).
     * User must authenticate with biometric before proceeding.
     *
     * @property operation Type of operation requiring authentication (PAYMENT, EXPORT)
     * @property reason Description of why biometric is required
     * @property requestedAt Timestamp when biometric was requested
     */
    data class BiometricRequired(
        val operation: OperationType,
        val reason: String,
        val requestedAt: LocalDateTime
    ) : SessionState() {
        /**
         * Get human-readable biometric request message
         *
         * @return Message describing the operation requiring biometric
         */
        fun getAuthenticationMessage(): String = when (operation) {
            OperationType.PAYMENT -> "Biometria necessária para confirmar pagamento"
            OperationType.EXPORT -> "Biometria necessária para exportar dados"
        }
    }

    /**
     * User is not authenticated
     *
     * Initial state at app startup before any authentication attempt.
     */
    object Unauthenticated : SessionState()

    /**
     * Get human-readable status for UI display
     *
     * @return Status message
     */
    fun getDisplayMessage(): String = when (this) {
        is Authenticated -> this.getStatusMessage()
        is Expired -> this.getExpirationMessage()
        is BiometricRequired -> this.getAuthenticationMessage()
        is Unauthenticated -> "Autenticação necessária"
    }
}

/**
 * Operation Type for Per-Operation Authentication
 *
 * Specifies the type of sensitive operation requiring biometric authentication.
 */
enum class OperationType {
    /**
     * Payment transaction - requires Class 3 biometric
     */
    PAYMENT,

    /**
     * Data export operation - requires Class 3 biometric
     */
    EXPORT
}
