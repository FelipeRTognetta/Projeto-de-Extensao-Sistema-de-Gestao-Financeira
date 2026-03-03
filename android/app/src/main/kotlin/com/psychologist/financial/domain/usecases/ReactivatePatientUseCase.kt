package com.psychologist.financial.domain.usecases

import com.psychologist.financial.data.repositories.PatientRepository
import com.psychologist.financial.domain.models.Patient
import com.psychologist.financial.domain.models.PatientStatus

/**
 * Use case: Reactivate an inactive patient
 *
 * Responsibility:
 * - Change patient status from INACTIVE to ACTIVE
 * - Restores read-write access to patient record
 * - Allows creation of new appointments and payments
 * - Implements business logic for patient status transitions
 *
 * Business Rules:
 * - Only INACTIVE patients can be reactivated
 * - Reactivation is voluntary (not automatic)
 * - All historical data is preserved
 * - After reactivation, patient can receive new appointments/payments
 * - Enables patient filtering and dashboard display
 *
 * Dependencies:
 * - PatientRepository: for database operations
 *
 * Usage:
 * ```kotlin
 * val useCase = ReactivatePatientUseCase(repository)
 * val activePatient = useCase.execute(patientId)
 *
 * if (activePatient != null) {
 *     // Successfully reactivated
 *     showSuccess("Patient reactivated")
 * } else {
 *     // Patient not found
 *     showError("Patient not found")
 * }
 * ```
 *
 * @property repository PatientRepository for data access
 */
class ReactivatePatientUseCase(
    private val repository: PatientRepository
) {
    /**
     * Reactivate an inactive patient
     *
     * Performs the status transition from INACTIVE to ACTIVE.
     * Does not fail if patient is already active (idempotent).
     * Preserves all historical data.
     *
     * @param patientId ID of patient to reactivate
     * @return Updated patient with ACTIVE status, or null if not found
     * @throws IllegalArgumentException if patientId <= 0
     *
     * Validation:
     * - patientId must be valid (> 0)
     * - Patient must exist in database
     *
     * Side Effects:
     * - Updates patient.status to ACTIVE
     * - Sets timestamp for status change
     * - Triggers audit log entry (future enhancement)
     * - Makes patient eligible for appointments and payments
     */
    suspend fun execute(patientId: Long): Patient? {
        if (patientId <= 0) {
            throw IllegalArgumentException("Patient ID must be positive")
        }

        // Get current patient to verify existence
        val patient = repository.getPatient(patientId) ?: return null

        // Mark as active (idempotent operation)
        repository.markAsActive(patientId)

        // Return updated patient with ACTIVE status
        return repository.getPatient(patientId)
    }

    /**
     * Check if patient can be reactivated
     *
     * Validation for business logic before attempting status change.
     *
     * @param patient Patient to check
     * @return true if patient can be reactivated
     *
     * Rules:
     * - Patient must exist (id > 0)
     * - Patient must be INACTIVE
     */
    fun canReactivate(patient: Patient): Boolean {
        return patient.id > 0 && patient.isInactive
    }

    /**
     * Check if patient has conflicting data
     *
     * Validates that reactivation won't cause data issues.
     *
     * Current implementation: Always returns true (no restrictions).
     * Future enhancement: Could check if all appointments are in past.
     *
     * @param patient Patient to validate for reactivation
     * @return true if patient can be safely reactivated
     */
    suspend fun validateReactivation(patient: Patient): Boolean {
        if (patient.id <= 0) return false

        // Current: No restrictions on reactivation
        // Future: Could add business rules like:
        // - Check if last appointment was > 30 days ago
        // - Check if outstanding balance is resolved
        // - Require manager approval for inactive patients

        return true
    }

    /**
     * Get status display message (Portuguese)
     *
     * User-facing message for reactivating patient.
     *
     * @param patientName Name of patient being reactivated
     * @return Portuguese message for confirmation/success
     */
    fun getStatusChangeMessage(patientName: String): String {
        return "Paciente \"$patientName\" foi reativado"
    }

    /**
     * Get warning message (Portuguese)
     *
     * Alert user about implications of reactivating a patient.
     *
     * @param patientName Name of patient being reactivated
     * @return Portuguese warning message
     */
    fun getWarningMessage(patientName: String): String {
        return "Após reativar, você poderá adicionar novos atendimentos e pagamentos para \"$patientName\""
    }
}
