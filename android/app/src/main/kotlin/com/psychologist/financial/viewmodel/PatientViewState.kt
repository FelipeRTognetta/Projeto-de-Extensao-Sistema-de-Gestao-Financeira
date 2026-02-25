package com.psychologist.financial.viewmodel

import com.psychologist.financial.domain.models.Patient

/**
 * State classes for Patient screens
 *
 * Sealed classes representing different UI states.
 * Composables pattern match on state to render appropriate UI.
 *
 * State Management Strategy:
 * - ListState: For patient list screen
 * - DetailState: For patient detail screen
 * - CreatePatientState: For patient creation form
 *
 * Benefits:
 * - Type-safe state representation
 * - Forces handling all cases (sealed classes)
 * - Clear state transitions
 * - Easy to test
 *
 * Usage:
 * ```kotlin
 * val state = patientListState.collectAsState().value
 *
 * when (state) {
 *     is PatientViewState.ListState.Loading -> LoadingSpinner()
 *     is PatientViewState.ListState.Success -> PatientList(state.patients)
 *     is PatientViewState.ListState.Empty -> EmptyStateMessage()
 *     is PatientViewState.ListState.Error -> ErrorMessage(state.message)
 * }
 * ```
 */
object PatientViewState {

    /**
     * List screen state
     *
     * Represents different states of patient list view.
     * Sealed class ensures all states handled.
     */
    sealed class ListState {
        /**
         * Initial load or refresh in progress
         *
         * UI should show loading spinner.
         */
        object Loading : ListState()

        /**
         * List loaded successfully
         *
         * @property patients List of patients to display
         */
        data class Success(val patients: List<Patient>) : ListState() {
            /**
             * Check if list has any patients
             *
             * @return true if patients list is not empty
             */
            fun hasPatients(): Boolean = patients.isNotEmpty()

            /**
             * Get patient count
             *
             * @return Number of patients in list
             */
            fun getPatientCount(): Int = patients.size

            /**
             * Get active patients from list
             *
             * @return Filtered list of active patients
             */
            fun getActivePatients(): List<Patient> = patients.filter { it.isActive }

            /**
             * Search patients by name
             *
             * @param term Search term
             * @return Matching patients
             */
            fun searchByName(term: String): List<Patient> =
                patients.filter { it.name.contains(term, ignoreCase = true) }
        }

        /**
         * No patients found
         *
         * UI should show empty state message.
         * Can occur from:
         * - No patients created yet
         * - All patients are inactive (if not showing them)
         * - Search returned no results
         */
        object Empty : ListState() {
            /**
             * Get empty state message
             *
             * @return User-friendly message
             */
            fun getMessage(): String = "Nenhum paciente cadastrado"
        }

        /**
         * List failed to load
         *
         * @property message Error message to display
         */
        data class Error(val message: String) : ListState() {
            /**
             * Check if error is retriable
             *
             * Some errors (network) are retriable, others (validation) are not.
             *
             * @return true if user should be offered retry button
             */
            fun isRetriable(): Boolean = !message.contains("validation", ignoreCase = true)
        }
    }

    /**
     * Detail screen state
     *
     * Shows full patient information.
     */
    sealed class DetailState {
        /**
         * No patient selected
         *
         * UI should show empty state or previous list.
         */
        object Idle : DetailState()

        /**
         * Loading patient detail
         *
         * UI should show loading indicator.
         */
        object Loading : DetailState()

        /**
         * Patient detail loaded
         *
         * @property patient Full patient information
         */
        data class Success(val patient: Patient) : DetailState() {
            /**
             * Check if patient is active
             *
             * @return true if patient status is ACTIVE
             */
            fun isActive(): Boolean = patient.isActive

            /**
             * Get formatted status
             *
             * @return Portuguese status text
             */
            fun getStatusText(): String = patient.getStatusDisplayName()

            /**
             * Get primary contact
             *
             * @return Phone or email, or empty string
             */
            fun getPrimaryContact(): String = patient.primaryContact ?: "(Sem contato)"

            /**
             * Get contact method descriptor
             *
             * @return "Telefone", "Email", or "Telefone e Email"
             */
            fun getContactMethod(): String = when {
                !patient.phone.isNullOrEmpty() && !patient.email.isNullOrEmpty() ->
                    "Telefone e Email"
                !patient.phone.isNullOrEmpty() -> "Telefone"
                !patient.email.isNullOrEmpty() -> "Email"
                else -> "Sem contato"
            }

            /**
             * Format display name
             *
             * @return Trimmed patient name
             */
            fun getDisplayName(): String = patient.getDisplayName()

            /**
             * Get initials for avatar
             *
             * @return 2-letter initials (uppercase)
             */
            fun getInitials(): String = patient.getInitials()

            /**
             * Check if has recent appointments
             *
             * @return true if last appointment within 30 days
             */
            fun hasRecentActivity(): Boolean = patient.hasRecentActivity()
        }

        /**
         * Failed to load patient
         *
         * @property message Error message
         */
        data class Error(val message: String) : DetailState()
    }

    /**
     * Patient creation form state
     *
     * Tracks form field values, validation, and submission.
     *
     * @property fieldErrors Map of field name to error message
     * @property isSubmitting Whether form is currently submitting
     * @property submissionResult Success or error from last submission
     */
    data class CreatePatientState(
        val fieldErrors: Map<String, String> = emptyMap(),
        val isSubmitting: Boolean = false,
        val submissionResult: SubmissionResult? = null
    ) {
        /**
         * Get error for specific field
         *
         * @param field Field name
         * @return Error message or null if no error
         */
        fun getFieldError(field: String): String? = fieldErrors[field]

        /**
         * Check if field has error
         *
         * @param field Field name
         * @return true if field has validation error
         */
        fun hasFieldError(field: String): Boolean = fieldErrors.containsKey(field)

        /**
         * Check if form is valid
         *
         * @return true if no field errors
         */
        fun isFormValid(): Boolean = fieldErrors.isEmpty() && !isSubmitting

        /**
         * Check if form can be submitted
         *
         * @return true if valid and not already submitting
         */
        fun canSubmit(): Boolean = isFormValid() && !isSubmitting

        /**
         * Get all field errors as list
         *
         * @return List of error messages
         */
        fun getAllErrors(): List<String> = fieldErrors.values.toList()

        /**
         * Check if submission succeeded
         *
         * @return true if last submission was successful
         */
        fun isSubmissionSuccessful(): Boolean =
            submissionResult is SubmissionResult.Success

        /**
         * Get successful submission result if available
         *
         * @return PatientId if successful, null otherwise
         */
        fun getSuccessfulPatientId(): Long? =
            (submissionResult as? SubmissionResult.Success)?.patientId

        /**
         * Submission result
         *
         * Represents outcome of form submission.
         */
        sealed class SubmissionResult {
            /**
             * Patient created successfully
             *
             * @property patientId Generated patient ID
             */
            data class Success(val patientId: Long) : SubmissionResult()

            /**
             * Submission failed
             *
             * @property message Error message to display
             */
            data class Error(val message: String) : SubmissionResult()
        }
    }

    /**
     * Validation state for form fields
     *
     * Tracks validation state per field for UI feedback.
     */
    data class FieldValidationState(
        val name: ValidationStatus = ValidationStatus.Pristine,
        val phone: ValidationStatus = ValidationStatus.Pristine,
        val email: ValidationStatus = ValidationStatus.Pristine,
        val initialConsultDate: ValidationStatus = ValidationStatus.Pristine
    ) {
        /**
         * Check if any field has error
         *
         * @return true if any field is Invalid
         */
        fun hasErrors(): Boolean = listOf(name, phone, email, initialConsultDate)
            .any { it == ValidationStatus.Invalid }

        /**
         * Check if all fields valid
         *
         * @return true if all fields Valid or Pristine
         */
        fun isValid(): Boolean = !hasErrors()

        /**
         * Mark all fields as touched (no longer pristine)
         *
         * @return Updated state with all touched
         */
        fun markAllTouched(): FieldValidationState = copy(
            name = if (name == ValidationStatus.Pristine) ValidationStatus.Valid else name,
            phone = if (phone == ValidationStatus.Pristine) ValidationStatus.Valid else phone,
            email = if (email == ValidationStatus.Pristine) ValidationStatus.Valid else email,
            initialConsultDate = if (initialConsultDate == ValidationStatus.Pristine)
                ValidationStatus.Valid else initialConsultDate
        )
    }

    /**
     * Field validation status enum
     *
     * Pristine: Not yet touched by user
     * Valid: User provided valid input
     * Invalid: User provided invalid input
     */
    enum class ValidationStatus {
        Pristine,  // Not yet interacted with
        Valid,     // User entered valid data
        Invalid    // User entered invalid data
    }

    /**
     * Form submission state
     *
     * Tracks the lifecycle of form submission.
     */
    enum class FormSubmissionState {
        Idle,          // Not submitting
        Submitting,    // Request in progress
        Success,       // Successfully submitted
        Error,         // Submission failed
        ValidationError // Form validation failed
    }

    /**
     * List filter options
     *
     * Available filters for patient list.
     */
    enum class ListFilter {
        Active,        // Only ACTIVE patients
        Inactive,      // Only INACTIVE patients
        All            // All patients
    }

    /**
     * List sort options
     *
     * Ways to sort patient list.
     */
    enum class ListSort {
        NameAsc,       // Name A-Z
        NameDesc,      // Name Z-A
        RecentFirst,   // Most recent appointment first
        RegisteredFirst // Oldest registration first
    }
}
