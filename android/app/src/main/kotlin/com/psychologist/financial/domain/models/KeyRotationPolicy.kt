package com.psychologist.financial.domain.models

import java.time.DayOfWeek
import java.time.LocalDateTime

/**
 * Key Rotation Policy
 *
 * Defines when and how encryption keys should be rotated.
 * Enforces rotation schedules, warnings, and expiration thresholds.
 *
 * Rotation Strategy:
 * - Primary: Time-based (90-day intervals)
 * - Secondary: Usage-based (after X transactions)
 * - Emergency: Force rotation on security events
 *
 * Policy Rules:
 * 1. Keys rotate every 90 days (configurable)
 * 2. Rotation initiated 7 days before expiration (warning period)
 * 3. Expired keys become inactive, cannot encrypt new data
 * 4. Key rotation happens automatically on first use after expiration
 * 5. Old key retained for decryption of old data (seamless migration)
 *
 * Implementation:
 * - KeyRotationService checks policy and rotates keys
 * - Rotation is background operation (transparent to app)
 * - No data loss or interruption during rotation
 * - Audit logging tracks all key events
 *
 * @property rotationIntervalDays How often keys should rotate (default: 90)
 * @property warningThresholdDays Show warnings when key expires within N days (default: 7)
 * @property gracePeriodDays Keep old key available after rotation (default: 30)
 * @property enableAutomaticRotation Whether rotation happens automatically (default: true)
 * @property rotationWindowStartDay Day of week rotation window starts (default: MONDAY)
 * @property rotationWindowStartHour Hour rotation should start (default: 02:00 AM)
 * @property maxConsecutiveFailures Max failed rotations before disabling auto-rotation (default: 3)
 * @property requiresUserAuthForRotation User must authenticate to perform rotation (default: false)
 */
data class KeyRotationPolicy(
    val rotationIntervalDays: Int = 90,
    val warningThresholdDays: Int = 7,
    val gracePeriodDays: Int = 30,
    val enableAutomaticRotation: Boolean = true,
    val rotationWindowStartDay: DayOfWeek = DayOfWeek.MONDAY,
    val rotationWindowStartHour: Int = 2, // 02:00 AM
    val rotationWindowDurationHours: Int = 2, // 2-hour window
    val maxConsecutiveFailures: Int = 3,
    val requiresUserAuthForRotation: Boolean = false,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val lastModifiedAt: LocalDateTime = LocalDateTime.now()
) {
    /**
     * Check if rotation is due for a key
     *
     * @param key EncryptionKey to check
     * @return true if key should be rotated
     */
    fun isRotationDue(key: EncryptionKey): Boolean {
        return key.isExpired() || key.isAboutToExpire()
    }

    /**
     * Check if rotation warning should be shown to user
     *
     * @param key EncryptionKey to check
     * @return true if key is expiring within warning threshold
     */
    fun shouldShowWarning(key: EncryptionKey): Boolean {
        return key.isAboutToExpire() && !key.isExpired()
    }

    /**
     * Check if rotation is in the configured time window
     *
     * Rotation should happen during quiet hours to minimize impact.
     * Default: Monday 02:00-04:00 AM
     *
     * @return true if current time is in rotation window
     */
    fun isInRotationWindow(): Boolean {
        val now = LocalDateTime.now()
        val dayOfWeek = now.dayOfWeek
        val hour = now.hour

        // Check if it's the right day of week
        if (dayOfWeek != rotationWindowStartDay) {
            return false
        }

        // Check if it's within the time window
        val windowEnd = rotationWindowStartHour + rotationWindowDurationHours
        return hour >= rotationWindowStartHour && hour < windowEnd
    }

    /**
     * Check if old key should still be kept for decryption
     *
     * Old key is retained during grace period for data migration.
     * After grace period, old key can be safely deleted.
     *
     * @param oldKey Previously active key
     * @param newKey Currently active key
     * @return true if old key should be retained
     */
    fun shouldRetainOldKey(oldKey: EncryptionKey, newKey: EncryptionKey): Boolean {
        val gracePeriodEnd = oldKey.expiresAt.plusDays(gracePeriodDays.toLong())
        return LocalDateTime.now() < gracePeriodEnd
    }

    /**
     * Get days remaining in grace period for old key
     *
     * @param oldKey Previously active key
     * @return Days remaining in grace period
     */
    fun getGracePeriodDaysRemaining(oldKey: EncryptionKey): Long {
        val gracePeriodEnd = oldKey.expiresAt.plusDays(gracePeriodDays.toLong())
        val daysRemaining = java.time.temporal.ChronoUnit.DAYS.between(
            LocalDateTime.now(),
            gracePeriodEnd
        )
        return maxOf(0, daysRemaining)
    }

    /**
     * Get next scheduled rotation time
     *
     * @param lastRotation Time of last rotation
     * @return Recommended next rotation time
     */
    fun getNextRotationTime(lastRotation: LocalDateTime): LocalDateTime {
        return lastRotation.plusDays(rotationIntervalDays.toLong())
    }

    /**
     * Get minutes until next rotation window
     *
     * @return Minutes until rotation window opens
     */
    fun getMinutesUntilNextRotationWindow(): Long {
        val now = LocalDateTime.now()
        val nextWindow = calculateNextRotationWindow()
        return java.time.temporal.ChronoUnit.MINUTES.between(now, nextWindow)
    }

    /**
     * Calculate next rotation window start time
     *
     * @return LocalDateTime of next rotation window
     */
    private fun calculateNextRotationWindow(): LocalDateTime {
        var current = LocalDateTime.now()
        val target = current.withHour(rotationWindowStartHour).withMinute(0).withSecond(0)

        // If we're already in the window or past it today, go to next week
        val dayOfWeek = current.dayOfWeek
        val daysUntilTarget = (rotationWindowStartDay.value - dayOfWeek.value + 7) % 7
        val nextWindow = if (daysUntilTarget == 0 && current < target) {
            target
        } else {
            target.plusDays(daysUntilTarget.toLong())
        }

        return nextWindow
    }

    /**
     * Get policy as human-readable summary
     *
     * @return Summary string
     */
    fun getSummary(): String = """
        Key Rotation Policy:
        - Rotation Interval: $rotationIntervalDays days
        - Warning Threshold: $warningThresholdDays days before expiration
        - Grace Period: $gracePeriodDays days after rotation
        - Automatic Rotation: ${if (enableAutomaticRotation) "Enabled" else "Disabled"}
        - Rotation Window: $rotationWindowStartDay at ${String.format("%02d:00", rotationWindowStartHour)}-${String.format("%02d:00", rotationWindowStartHour + rotationWindowDurationHours)}
        - Max Failures: $maxConsecutiveFailures before disabling auto-rotation
        - User Authentication: ${if (requiresUserAuthForRotation) "Required" else "Not required"}
    """.trimIndent()

    /**
     * Get policy metadata for logging
     *
     * @return Map with policy details
     */
    fun getMetadata(): Map<String, Any> = mapOf(
        "rotationIntervalDays" to rotationIntervalDays,
        "warningThresholdDays" to warningThresholdDays,
        "gracePeriodDays" to gracePeriodDays,
        "enableAutomaticRotation" to enableAutomaticRotation,
        "rotationWindowStartDay" to rotationWindowStartDay.name,
        "rotationWindowStartHour" to rotationWindowStartHour,
        "rotationWindowDurationHours" to rotationWindowDurationHours,
        "maxConsecutiveFailures" to maxConsecutiveFailures,
        "requiresUserAuthForRotation" to requiresUserAuthForRotation,
        "createdAt" to createdAt.toString(),
        "lastModifiedAt" to lastModifiedAt.toString()
    )

    companion object {
        /**
         * Get default production policy
         *
         * - 90-day rotation interval
         * - 7-day warning period
         * - 30-day grace period for old key
         * - Auto-rotation enabled
         * - Rotation window: Monday 2-4 AM
         *
         * @return KeyRotationPolicy with recommended defaults
         */
        fun production(): KeyRotationPolicy = KeyRotationPolicy(
            rotationIntervalDays = 90,
            warningThresholdDays = 7,
            gracePeriodDays = 30,
            enableAutomaticRotation = true,
            rotationWindowStartDay = DayOfWeek.MONDAY,
            rotationWindowStartHour = 2
        )

        /**
         * Get policy for testing
         *
         * - 1-day rotation interval (for quick testing)
         * - 6-hour warning period
         * - 1-hour grace period
         * - Auto-rotation enabled
         *
         * @return KeyRotationPolicy for testing
         */
        fun testing(): KeyRotationPolicy = KeyRotationPolicy(
            rotationIntervalDays = 1,
            warningThresholdDays = 0,
            gracePeriodDays = 1,
            enableAutomaticRotation = true,
            rotationWindowStartHour = 0
        )
    }
}
