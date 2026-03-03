package com.psychologist.financial.domain.usecases

import com.psychologist.financial.data.repositories.PatientRepository
import com.psychologist.financial.domain.models.Patient
import com.psychologist.financial.domain.models.PatientStatus

/**
 * Use case: Mark patient as inactive (soft delete)
 *
 * Responsibility:
 * - Mark a patient's status as INACTIVE
 * - Prevents creation of new appointments/payments for inactive patients
 * - Preserves all historical data (appointments, payments)
 * - Implements business logic for patient status transitions
 *
 * Business Rules:
 * - Only ACTIVE patients can be marked inactive
 * - Once inactive, patient is read-only (no new appointments/payments)
 * - All historical data is preserved
 * - Patient can be reactivated later
 * - Used for archiving inactive patients
 *
 * Dependencies:
 * - PatientRepository: for database operations
 *
 * Usage:
 * ```kotlin
 * val useCase = MarkPatientInactiveUseCase(repository)
 * val inactivePatient = useCase.execute(patientId)
 *
 * if (inactivePatient != null) {
 *     // Successfully marked as inactive
 *     showSuccess("Patient archived")
 * } else {
 *     // Patient not found
 *     showError("Patient not found")
 * }
 * ```
 *
 * @property repository PatientRepository for data access
 */
class MarkPatientInactiveUseCase(
    private val repository: PatientRepository
) {
    /**
     * Mark a patient as inactive
     *
     * Performs the status transition from ACTIVE to INACTIVE.
     * Does not fail if patient is already inactive (idempotent).
     * Preserves all historical data.
     *
     * @param patientId ID of patient to mark inactive
     * @return Updated patient with INACTIVE status, or null if not found
     * @throws IllegalArgumentException if patientId <= 0
     *
     * Validation:
     * - patientId must be valid (> 0)
     * - Patient must exist in database
     *
     * Side Effects:
     * - Updates patient.status to INACTIVE
     * - Sets timestamp for status change
     * - Triggers audit log entry (future enhancement)
     */
    suspend fun execute(patientId: Long): Patient? {
        if (patientId <= 0) {
            throw IllegalArgumentException("Patient ID must be positive")
        }

        // Get current patient to verify existence
        val patient = repository.getPatient(patientId) ?: return null

        // Mark as inactive (idempotent operation)
        repository.markAsInactive(patientId)

        // Return updated patient with INACTIVE status
        return repository.getPatient(patientId)
    }

    /**
     * Check if patient can be marked inactive
     *
     * Validation for business logic before attempting status change.
     *
     * @param patient Patient to check
     * @return true if patient can be marked inactive
     *
     * Rules:
     * - Patient must exist (id > 0)
     * - Patient must be ACTIVE (can idempotently mark already-inactive as inactive)
     */
    fun canMarkInactive(patient: Patient): Boolean {
        return patient.id > 0 && patient.isActive
    }

    /**
     * Get status display message (Portuguese)
     *
     * User-facing message for marking patient inactive.
     *
     * @param patientName Name of patient being marked inactive
     * @return Portuguese message for confirmation/success
     */
    fun getStatusChangeMessage(patientName: String): String {
        return "Paciente \"$patientName\" foi marcado como inativo"
    }
}
