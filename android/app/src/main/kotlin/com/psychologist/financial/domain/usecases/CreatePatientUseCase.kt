package com.psychologist.financial.domain.usecases

import android.util.Log
import com.psychologist.financial.data.repositories.PatientRepository
import com.psychologist.financial.domain.models.Patient
import com.psychologist.financial.domain.models.PatientStatus
import com.psychologist.financial.domain.validation.PatientValidator
import com.psychologist.financial.domain.validation.ValidationError
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Use case: Create a new patient with validation
 *
 * Responsibilities:
 * - Validate patient data before insertion
 * - Enforce business rules (contact required, unique phone/email)
 * - Save to repository
 * - Return validation errors or generated ID
 *
 * Architecture:
 * - Validation happens here (use case layer)
 * - Business rules enforced before repository call
 * - Repository re-validates (defense in depth)
 * - ViewModels call this use case with user input
 *
 * Validation Rules:
 * - Name: 2-200 characters, required
 * - Phone: Optional, must be valid format if provided, must be unique
 * - Email: Optional, must be valid format if provided, must be unique
 * - At least one contact: Phone or email required
 * - Initial Consult Date: Valid date, not in future
 * - Registration Date: Auto-set to today (immutable)
 *
 * Error Handling:
 * - Returns ValidationError for all failures
 * - Caller handles errors (show UI messages, logging, etc.)
 * - No exceptions thrown for validation errors
 * - Technical errors propagate (database errors, etc.)
 *
 * Usage:
 * ```kotlin
 * val useCase = CreatePatientUseCase(patientRepository, patientValidator)
 *
 * // Create with validation
 * val result = useCase.execute(
 *     name = "João Silva",
 *     phone = "(11) 99999-9999",
 *     email = "joao@example.com",
 *     initialConsultDate = LocalDate.now()
 * )
 *
 * when (result) {
 *     is CreatePatientResult.Success -> {
 *         Log.d("Patient created: ${result.patientId}")
 *         navigateToPatientDetail(result.patientId)
 *     }
 *     is CreatePatientResult.ValidationError -> {
 *         showError(result.errors)
 *     }
 * }
 * ```
 *
 * @property patientRepository PatientRepository for data persistence
 * @property patientValidator PatientValidator for validation rules
 */
class CreatePatientUseCase(
    private val patientRepository: PatientRepository,
    private val patientValidator: PatientValidator
) {
    private companion object {
        private const val TAG = "CreatePatientUseCase"
    }

    /**
     * Create patient with all validation
     *
     * Validates input, persists to repository, returns result.
     *
     * @param name Patient full name (2-200 chars)
     * @param phone Optional phone number
     * @param email Optional email address
     * @param initialConsultDate First consultation date (not future)
     * @return CreatePatientResult (Success with ID, or ValidationError)
     *
     * Validation occurs in this order:
     * 1. PatientValidator rules (name format, phone format, etc.)
     * 2. Repository uniqueness checks (phone, email)
     * 3. Database insertion
     *
     * Example:
     * ```kotlin
     * val result = useCase.execute(
     *     name = "Maria Santos",
     *     phone = "(21) 98765-4321",
     *     email = "maria@example.com",
     *     initialConsultDate = LocalDate.of(2026, 1, 15)
     * )
     * ```
     */
    suspend fun execute(
        name: String,
        phone: String? = null,
        email: String? = null,
        initialConsultDate: LocalDate = LocalDate.now()
    ): CreatePatientResult {
        return try {
            // Validate input
            val validationErrors = patientValidator.validateNewPatient(
                name = name,
                phone = phone,
                email = email,
                initialConsultDate = initialConsultDate
            )

            if (validationErrors.isNotEmpty()) {
                Log.w(TAG, "Validation failed: ${validationErrors.size} errors")
                return CreatePatientResult.ValidationError(validationErrors)
            }

            // Create patient object
            val patient = Patient(
                id = 0,  // Unsaved
                name = name.trim(),
                phone = phone?.trim(),
                email = email?.trim(),
                status = PatientStatus.ACTIVE,
                initialConsultDate = initialConsultDate,
                registrationDate = LocalDate.now(),
                lastAppointmentDate = null,
                createdDate = LocalDateTime.now()
            )

            // Save to repository
            val patientId = patientRepository.createPatient(patient)
            Log.d(TAG, "Patient created successfully: id=$patientId, name=$name")

            CreatePatientResult.Success(patientId)
        } catch (e: IllegalArgumentException) {
            // Repository validation error (uniqueness, etc.)
            Log.w(TAG, "Repository validation error: ${e.message}")
            val error = ValidationError(
                field = "general",
                message = e.message ?: "Unable to create patient"
            )
            CreatePatientResult.ValidationError(listOf(error))
        } catch (e: Exception) {
            // Technical error
            Log.e(TAG, "Failed to create patient", e)
            throw e
        }
    }

    /**
     * Create patient with Patient object
     *
     * Useful when Patient object already constructed.
     * Still validates before insertion.
     *
     * @param patient Patient to create (must have id=0)
     * @return CreatePatientResult
     *
     * Example:
     * ```kotlin
     * val patient = Patient(
     *     id = 0,
     *     name = "João",
     *     phone = "11999999999",
     *     initialConsultDate = LocalDate.now(),
     *     registrationDate = LocalDate.now()
     * )
     * val result = useCase.executeWithObject(patient)
     * ```
     */
    suspend fun executeWithObject(patient: Patient): CreatePatientResult {
        require(patient.id == 0L) { "Patient must be unsaved (id = 0)" }

        return execute(
            name = patient.name,
            phone = patient.phone,
            email = patient.email,
            initialConsultDate = patient.initialConsultDate
        )
    }

    /**
     * Validate without saving
     *
     * Check if patient would be valid without creating it.
     * Useful for form validation feedback.
     *
     * @param name Patient name
     * @param phone Optional phone
     * @param email Optional email
     * @param initialConsultDate First consultation date
     * @return List of ValidationError, empty if valid
     *
     * Example:
     * ```kotlin
     * val errors = useCase.validate(name, phone, email, date)
     * if (errors.isEmpty()) {
     *     // Show "Ready to save" state
     * } else {
     *     // Show validation errors
     * }
     * ```
     */
    fun validate(
        name: String,
        phone: String? = null,
        email: String? = null,
        initialConsultDate: LocalDate = LocalDate.now()
    ): List<ValidationError> {
        return patientValidator.validateNewPatient(
            name = name,
            phone = phone,
            email = email,
            initialConsultDate = initialConsultDate
        )
    }
}

/**
 * Result sealed class for CreatePatientUseCase
 *
 * Represents success or failure of patient creation.
 *
 * Usage:
 * ```kotlin
 * when (result) {
 *     is CreatePatientResult.Success -> handleSuccess(result.patientId)
 *     is CreatePatientResult.ValidationError -> handleErrors(result.errors)
 * }
 * ```
 */
sealed class CreatePatientResult {
    /**
     * Patient created successfully
     *
     * @property patientId Generated patient ID (> 0)
     */
    data class Success(val patientId: Long) : CreatePatientResult() {
        init {
            require(patientId > 0) { "Patient ID must be > 0" }
        }
    }

    /**
     * Validation failed
     *
     * @property errors List of validation errors
     */
    data class ValidationError(val errors: List<com.psychologist.financial.domain.validation.ValidationError>) :
        CreatePatientResult() {
        init {
            require(errors.isNotEmpty()) { "Must have at least one validation error" }
        }

        /**
         * Get first error message
         *
         * Useful for showing single error in simple UI.
         *
         * @return First error message
         */
        fun getFirstErrorMessage(): String = errors.first().message

        /**
         * Get errors grouped by field
         *
         * Useful for showing field-specific error messages.
         *
         * @return Map of field name to error messages
         */
        fun getErrorsByField(): Map<String, List<String>> {
            return errors.groupBy { it.field }.mapValues { (_, fieldErrors) ->
                fieldErrors.map { it.message }
            }
        }
    }
}
