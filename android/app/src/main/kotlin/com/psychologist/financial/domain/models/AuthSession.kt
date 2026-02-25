package com.psychologist.financial.domain.models

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Data model for authentication session state
 *
 * Represents an authenticated user session with expiration tracking.
 * Used by BiometricAuthManager to track active sessions.
 *
 * Architecture:
 * - Domain model (no Room annotations, clean business logic)
 * - Immutable data class for thread safety
 * - Typically stored in-memory only (cleared on app close)
 * - Can be logged/exported for audit purposes
 *
 * Lifecycle:
 * 1. Created: On successful biometric or PIN authentication
 * 2. Valid: For duration until expiryTime
 * 3. Expired: After expiryTime passes
 * 4. Cleared: On logout or app termination
 *
 * Usage:
 * ```kotlin
 * val session = AuthSession(
 *     token = "abcd1234...",
 *     expiryTime = LocalDateTime.now().plusMinutes(15),
 *     userContext = "psychologist_session"
 * )
 *
 * // Check validity
 * if (LocalDateTime.now().isBefore(session.expiryTime)) {
 *     // Session valid, allow access
 * }
 * ```
 *
 * @property token Random authentication token (32 chars)
 * @property expiryTime Session expiration timestamp
 * @property userContext Context information (single-user app: always "psychologist_session")
 */
data class AuthSession(
    /**
     * Random authentication token
     *
     * Generated on session creation, never extracted for external use.
     * Can be used as:
     * - Session identifier for audit logs
     * - Protection against CSRF (if this app had web API)
     * - Correlation ID for multi-device scenarios (future)
     *
     * Properties:
     * - 32 characters (alphanumeric)
     * - Generated using secure random
     * - Unique per session
     * - Immutable
     *
     * Example: "aBcD1234EfGh5678IjKl9012MnOp3456"
     */
    val token: String,

    /**
     * Session expiration timestamp
     *
     * Absolute time when this session becomes invalid.
     * Typically 15 minutes after creation (Constants.BIOMETRIC_SESSION_TIMEOUT_MINUTES).
     *
     * Format: LocalDateTime (ISO 8601 compatible)
     * Example: 2026-02-25T15:45:30
     *
     * Validation:
     * - Session valid if: LocalDateTime.now() < expiryTime
     * - Session expired if: LocalDateTime.now() >= expiryTime
     *
     * No automatic cleanup - BiometricAuthManager checks validity on use.
     */
    val expiryTime: LocalDateTime,

    /**
     * User context information
     *
     * For single-user mobile app: always "psychologist_session"
     * Allows future multi-user scenarios without code changes.
     *
     * Could be extended to:
     * - "psychologist_session" (current user role)
     * - "admin_session" (if admin features added)
     * - "staff_session" (if multi-staff added)
     *
     * Example: "psychologist_session"
     */
    val userContext: String = "psychologist_session"
) {

    /**
     * Check if session is currently valid
     *
     * @return true if current time is before expiryTime
     *
     * Usage:
     * ```kotlin
     * if (session.isValid()) {
     *     // Allow access
     * } else {
     *     // Re-authenticate
     * }
     * ```
     */
    fun isValid(): Boolean {
        return LocalDateTime.now().isBefore(expiryTime)
    }

    /**
     * Get remaining validity time in seconds
     *
     * Useful for UI indicators or session countdown.
     *
     * @return Seconds remaining, or 0 if expired
     *
     * Example:
     * ```kotlin
     * val remaining = session.getRemainingSeconds()
     * if (remaining < 300) {  // Less than 5 minutes
     *     showWarning("Session expiring in ${remaining / 60} minutes")
     * }
     * ```
     */
    fun getRemainingSeconds(): Long {
        val now = LocalDateTime.now()
        if (now.isAfter(expiryTime)) {
            return 0L
        }
        return java.time.temporal.ChronoUnit.SECONDS.between(now, expiryTime)
    }

    /**
     * Get remaining validity time in human-readable format
     *
     * @return Formatted string like "14m 30s" or "Expired"
     */
    fun getFormattedRemainingTime(): String {
        val seconds = getRemainingSeconds()
        if (seconds <= 0) return "Expirado"

        val minutes = seconds / 60
        val secs = seconds % 60
        return "${minutes}m ${secs}s"
    }

    /**
     * Get expiry time as ISO 8601 string
     *
     * Useful for logging, storage, or serialization.
     *
     * @return ISO 8601 formatted timestamp
     * Example: "2026-02-25T15:45:30"
     */
    fun getExpiryTimeFormatted(): String {
        return expiryTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    }

    /**
     * Create debug string representation
     *
     * @return String for logging
     * Example: "[AuthSession token=aBcd1234..., expires=2026-02-25T15:45:30, context=psychologist_session]"
     */
    fun toDebugString(): String {
        val tokenPreview = token.take(8) + "..."
        return "[AuthSession token=$tokenPreview, expires=${getExpiryTimeFormatted()}, context=$userContext, valid=${isValid()}]"
    }

    companion object {
        /**
         * Create a test session with specified expiry offset
         *
         * Useful for testing and development.
         *
         * @param minutesFromNow Minutes to add to current time for expiry
         * @return AuthSession with generated token
         *
         * Example:
         * ```kotlin
         * val session = AuthSession.createForTesting(minutesFromNow = 15)
         * ```
         */
        fun createForTesting(minutesFromNow: Int = 15): AuthSession {
            val token = (0..31).map { ('a'..'z').random() }.joinToString("")
            val expiryTime = LocalDateTime.now().plusMinutes(minutesFromNow.toLong())
            return AuthSession(
                token = token,
                expiryTime = expiryTime,
                userContext = "test_session"
            )
        }

        /**
         * Create an already-expired session for testing
         *
         * Useful for testing session timeout behavior.
         *
         * @return AuthSession with expiryTime in the past
         */
        fun createExpiredForTesting(): AuthSession {
            val token = (0..31).map { ('a'..'z').random() }.joinToString("")
            val expiryTime = LocalDateTime.now().minusMinutes(1)
            return AuthSession(
                token = token,
                expiryTime = expiryTime,
                userContext = "test_session"
            )
        }
    }
}
