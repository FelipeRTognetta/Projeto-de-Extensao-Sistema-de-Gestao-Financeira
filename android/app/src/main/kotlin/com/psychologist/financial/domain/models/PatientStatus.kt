package com.psychologist.financial.domain.models

/**
 * Patient status enumeration
 *
 * Represents the current status/state of a patient record.
 * Controls whether the patient is eligible to receive new appointments/payments.
 *
 * Architecture:
 * - Part of domain layer (business logic)
 * - Used by Patient entity
 * - Controls access rules via PatientStatusEnforcer service
 * - Persisted in database as string (Room: @ColumnInfo(name = "status"))
 *
 * State Transitions:
 * - ACTIVE → INACTIVE: Mark patient inactive (archive)
 * - INACTIVE → ACTIVE: Reactivate patient
 * - ACTIVE → ACTIVE: No change (idempotent)
 * - INACTIVE → INACTIVE: No change (idempotent)
 *
 * Display Names (Portuguese):
 * - ACTIVE → "Ativo"
 * - INACTIVE → "Inativo"
 *
 * Usage:
 * ```kotlin
 * // Check patient status
 * val isActive = patient.status == PatientStatus.ACTIVE
 * val isInactive = patient.status == PatientStatus.INACTIVE
 *
 * // Status transitions
 * val inactivePatient = patient.copy(status = PatientStatus.INACTIVE)
 * val activePatient = patient.copy(status = PatientStatus.ACTIVE)
 *
 * // Filter by status
 * val activePatients = patients.filter { it.status == PatientStatus.ACTIVE }
 * val inactivePatients = patients.filter { it.status == PatientStatus.INACTIVE }
 *
 * // Display name
 * val displayName = when (patient.status) {
 *     PatientStatus.ACTIVE -> "Ativo"
 *     PatientStatus.INACTIVE -> "Inativo"
 * }
 * ```
 */
enum class PatientStatus {
    /**
     * ACTIVE status
     *
     * Patient is currently receiving treatment or available for new appointments.
     * - Can create new appointments
     * - Can record new payments
     * - Included in active patient count/dashboard
     * - Default status for new patients
     *
     * Characteristics:
     * - Shown in active patient list
     * - Eligible for billing
     * - Eligible for contact
     * - Read-write access to patient record
     */
    ACTIVE,

    /**
     * INACTIVE status
     *
     * Patient is archived/not currently receiving treatment.
     * - Cannot create new appointments (system prevents)
     * - Cannot record new payments (system prevents)
     * - Hidden from active patient list
     * - Soft delete pattern (data preserved, not deleted)
     *
     * Characteristics:
     * - Hidden from active patient list (shown in full list with "Inactive" badge)
     * - Not eligible for billing
     * - Not eligible for contact (unless explicitly reactivated)
     * - Read-only access to patient record
     * - Data preserved for historical/audit purposes
     */
    INACTIVE;

    /**
     * Get display name in Portuguese
     *
     * Converts enum value to user-facing string.
     *
     * @return Portuguese status name
     */
    fun getDisplayName(): String = when (this) {
        ACTIVE -> "Ativo"
        INACTIVE -> "Inativo"
    }

    /**
     * Check if patient is active
     *
     * Convenience method for readability.
     *
     * @return true if status is ACTIVE
     */
    fun isActive(): Boolean = this == ACTIVE

    /**
     * Check if patient is inactive
     *
     * Convenience method for readability.
     *
     * @return true if status is INACTIVE
     */
    fun isInactive(): Boolean = this == INACTIVE

    companion object {
        /**
         * Get status from string value
         *
         * Safe parsing of status strings (e.g., from database).
         *
         * @param value String value ("ACTIVE" or "INACTIVE")
         * @return PatientStatus enum value, or ACTIVE if invalid
         */
        fun fromString(value: String?): PatientStatus {
            return try {
                if (value.isNullOrEmpty()) ACTIVE else valueOf(value.uppercase())
            } catch (e: IllegalArgumentException) {
                ACTIVE // Default to ACTIVE if invalid
            }
        }

        /**
         * Get all possible statuses
         *
         * @return List of all PatientStatus values
         */
        fun getAllStatuses(): List<PatientStatus> = listOf(ACTIVE, INACTIVE)
    }
}
