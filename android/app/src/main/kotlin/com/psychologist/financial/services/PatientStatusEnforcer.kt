package com.psychologist.financial.services

import com.psychologist.financial.domain.models.Appointment
import com.psychologist.financial.domain.models.Patient
import com.psychologist.financial.domain.models.PatientStatus
import com.psychologist.financial.domain.models.Payment

/**
 * Service: Patient Status Enforcer
 *
 * Responsibility:
 * - Enforce business rules for patient status (ACTIVE vs INACTIVE)
 * - Prevent operations on INACTIVE patients
 * - Provide validation and error messages
 * - Central place for status-related access control
 *
 * Business Rules Enforced:
 * - ACTIVE patients: Can create appointments and payments
 * - INACTIVE patients: Read-only (no new appointments or payments)
 * - Status changes: ACTIVE ↔ INACTIVE (managed by use cases)
 *
 * Architecture:
 * - Shared service used by appointment/payment use cases
 * - Validates before operations (fail-fast approach)
 * - Returns detailed error messages for UI/logging
 * - Does not modify data (read-only validation)
 *
 * Dependencies:
 * - Patient domain model for status checking
 *
 * Usage:
 * ```kotlin
 * val enforcer = PatientStatusEnforcer()
 *
 * // Check before creating appointment
 * if (!enforcer.canCreateAppointment(patient)) {
 *     throw IllegalStateException(
 *         enforcer.getAppointmentCreationError(patient)
 *     )
 * }
 *
 * // Check before creating payment
 * if (!enforcer.canCreatePayment(patient)) {
 *     throw IllegalStateException(
 *         enforcer.getPaymentCreationError(patient)
 *     )
 * }
 *
 * // Check general access
 * val isReadOnly = enforcer.isPatientReadOnly(patient)
 * ```
 *
 * Integration Points:
 * - CreateAppointmentUseCase: Validates before appointment creation
 * - CreatePaymentUseCase: Validates before payment creation
 * - PatientViewModel: Disables buttons for inactive patients
 * - PatientStatusEnforcer tests: Comprehensive validation tests
 */
class PatientStatusEnforcer {

    // ========================================
    // Appointment Validation
    // ========================================

    /**
     * Check if appointment can be created for patient
     *
     * Validation rule: Only ACTIVE patients can have new appointments.
     *
     * @param patient Patient to check
     * @return true if appointment can be created, false if patient is INACTIVE
     */
    fun canCreateAppointment(patient: Patient): Boolean {
        return patient.status == PatientStatus.ACTIVE
    }

    /**
     * Check if appointment can be created by patient status
     *
     * Alternative method: Takes patient ID and requires separate lookup.
     * More flexible for caller but requires PatientRepository access.
     *
     * @param patientStatus Status to check
     * @return true if appointment can be created for patient with this status
     */
    fun canCreateAppointmentByStatus(patientStatus: PatientStatus): Boolean {
        return patientStatus == PatientStatus.ACTIVE
    }

    /**
     * Get error message for appointment creation failure
     *
     * User-facing error message in Portuguese.
     *
     * @param patient Patient (for name in message)
     * @return Error message explaining why appointment cannot be created
     */
    fun getAppointmentCreationError(patient: Patient): String {
        return if (patient.status == PatientStatus.INACTIVE) {
            "Não é possível criar atendimento para paciente inativo \"${patient.name}\". " +
                    "Por favor, reative o paciente primeiro."
        } else {
            "Não é possível criar atendimento para este paciente."
        }
    }

    /**
     * Get informational message about appointment restrictions
     *
     * Helper for UI to explain read-only status.
     *
     * @param patient Patient to describe
     * @return Message about restrictions (Portuguese)
     */
    fun getAppointmentRestrictionMessage(patient: Patient): String {
        return "O paciente \"${patient.name}\" está inativo. " +
                "Novos atendimentos não podem ser registrados."
    }

    // ========================================
    // Payment Validation
    // ========================================

    /**
     * Check if payment can be created for patient
     *
     * Validation rule: Only ACTIVE patients can have new payments recorded.
     *
     * @param patient Patient to check
     * @return true if payment can be created, false if patient is INACTIVE
     */
    fun canCreatePayment(patient: Patient): Boolean {
        return patient.status == PatientStatus.ACTIVE
    }

    /**
     * Check if payment can be created by patient status
     *
     * Alternative method: Takes patient ID and requires separate lookup.
     * More flexible for caller but requires PatientRepository access.
     *
     * @param patientStatus Status to check
     * @return true if payment can be created for patient with this status
     */
    fun canCreatePaymentByStatus(patientStatus: PatientStatus): Boolean {
        return patientStatus == PatientStatus.ACTIVE
    }

    /**
     * Get error message for payment creation failure
     *
     * User-facing error message in Portuguese.
     *
     * @param patient Patient (for name in message)
     * @return Error message explaining why payment cannot be created
     */
    fun getPaymentCreationError(patient: Patient): String {
        return if (patient.status == PatientStatus.INACTIVE) {
            "Não é possível registrar pagamento para paciente inativo \"${patient.name}\". " +
                    "Por favor, reative o paciente primeiro."
        } else {
            "Não é possível registrar pagamento para este paciente."
        }
    }

    /**
     * Get informational message about payment restrictions
     *
     * Helper for UI to explain read-only status.
     *
     * @param patient Patient to describe
     * @return Message about restrictions (Portuguese)
     */
    fun getPaymentRestrictionMessage(patient: Patient): String {
        return "O paciente \"${patient.name}\" está inativo. " +
                "Novos pagamentos não podem ser registrados."
    }

    // ========================================
    // General Access Control
    // ========================================

    /**
     * Check if patient is read-only
     *
     * Determines if patient data can be modified.
     * ACTIVE: read-write, INACTIVE: read-only
     *
     * @param patient Patient to check
     * @return true if patient is read-only (INACTIVE)
     */
    fun isPatientReadOnly(patient: Patient): Boolean {
        return patient.status == PatientStatus.INACTIVE
    }

    /**
     * Check if patient is write-accessible
     *
     * Determines if new operations can be performed.
     * Inverse of isPatientReadOnly.
     *
     * @param patient Patient to check
     * @return true if patient allows write operations (ACTIVE)
     */
    fun isPatientWriteAccessible(patient: Patient): Boolean {
        return patient.status == PatientStatus.ACTIVE
    }

    /**
     * Get access level description
     *
     * Returns human-readable access status.
     *
     * @param patient Patient to describe
     * @return Access level string (Portuguese)
     */
    fun getAccessLevelDescription(patient: Patient): String {
        return when (patient.status) {
            PatientStatus.ACTIVE -> "Paciente ativo - leitura e escrita permitidas"
            PatientStatus.INACTIVE -> "Paciente inativo - apenas leitura permitida"
        }
    }

    /**
     * Validate patient for general operations
     *
     * Comprehensive check for all restrictions.
     *
     * @param patient Patient to validate
     * @param operationType Type of operation (for error message)
     * @return ValidationResult with success/failure and error message
     */
    fun validatePatientAccess(
        patient: Patient,
        operationType: String
    ): ValidationResult {
        return if (patient.status == PatientStatus.INACTIVE) {
            ValidationResult(
                isValid = false,
                errorMessage = "Paciente inativo não pode realizar $operationType. " +
                        "Reative o paciente primeiro."
            )
        } else {
            ValidationResult(isValid = true)
        }
    }

    // ========================================
    // Batch Operations
    // ========================================

    /**
     * Filter patients to only active ones
     *
     * Utility for filtering patient lists.
     *
     * @param patients List of patients to filter
     * @return List containing only ACTIVE patients
     */
    fun filterActivePatients(patients: List<Patient>): List<Patient> {
        return patients.filter { it.status == PatientStatus.ACTIVE }
    }

    /**
     * Filter patients to only inactive ones
     *
     * Utility for filtering patient lists.
     *
     * @param patients List of patients to filter
     * @return List containing only INACTIVE patients
     */
    fun filterInactivePatients(patients: List<Patient>): List<Patient> {
        return patients.filter { it.status == PatientStatus.INACTIVE }
    }

    /**
     * Count active patients in list
     *
     * @param patients List of patients
     * @return Count of ACTIVE patients
     */
    fun countActivePatients(patients: List<Patient>): Int {
        return patients.count { it.status == PatientStatus.ACTIVE }
    }

    /**
     * Count inactive patients in list
     *
     * @param patients List of patients
     * @return Count of INACTIVE patients
     */
    fun countInactivePatients(patients: List<Patient>): Int {
        return patients.count { it.status == PatientStatus.INACTIVE }
    }

    // ========================================
    // Data Classes
    // ========================================

    /**
     * Result of validation check
     *
     * @property isValid true if validation passed
     * @property errorMessage Optional error message (null if valid)
     */
    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String? = null
    ) {
        /**
         * Check if validation failed
         *
         * @return true if validation did not pass
         */
        val isFailed: Boolean
            get() = !isValid

        /**
         * Get error message or default
         *
         * @return Error message if failed, else empty string
         */
        fun getErrorMessageOrEmpty(): String = errorMessage ?: ""
    }
}
