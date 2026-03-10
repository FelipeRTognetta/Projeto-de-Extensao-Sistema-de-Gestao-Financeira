package com.psychologist.financial.viewmodel

import com.psychologist.financial.domain.models.Appointment
import com.psychologist.financial.domain.models.AppointmentWithPaymentStatus
import com.psychologist.financial.domain.usecases.CreateAppointmentUseCase

/**
 * Appointment ViewModel state classes
 *
 * Defines sealed classes and data classes for managing appointment screen states.
 * Provides type-safe state management for list, detail, and form views.
 *
 * State Hierarchy:
 * - ListState: Appointment list view (Loading, Success, Empty, Error)
 * - DetailState: Single appointment detail (Idle, Loading, Success, Error)
 * - CreateAppointmentState: Form state with validation and submission
 *
 * Usage:
 * ```kotlin
 * // List state
 * when (appointmentListState) {
 *     is AppointmentViewState.ListState.Loading -> showLoadingIndicator()
 *     is AppointmentViewState.ListState.Success -> displayAppointments(appointments)
 *     is AppointmentViewState.ListState.Empty -> showEmptyMessage()
 *     is AppointmentViewState.ListState.Error -> showErrorMessage(message)
 * }
 *
 * // Form state
 * val formState = createFormState.value
 * val isFormValid = formState.isFormValid()
 * val errors = formState.getAllErrors()
 * ```
 */
object AppointmentViewState {

    // ========================================
    // List State
    // ========================================

    /**
     * Appointment list view state
     *
     * Represents different states when displaying appointment list.
     */
    sealed class ListState {
        /**
         * Loading appointments
         */
        object Loading : ListState()

        /**
         * Appointments loaded successfully
         *
         * @param appointments List of appointments with payment status
         */
        data class Success(
            val appointments: List<AppointmentWithPaymentStatus>
        ) : ListState() {
            /**
             * Get appointment count
             */
            fun getCount(): Int = appointments.size

            /**
             * Check if list is empty
             */
            fun isEmpty(): Boolean = appointments.isEmpty()

            /**
             * Filter appointments by status
             *
             * @param isPast true for past, false for upcoming
             * @return Filtered appointments
             */
            fun filterByStatus(isPast: Boolean): List<AppointmentWithPaymentStatus> {
                return appointments.filter { it.appointment.isPast == isPast }
            }
        }

        /**
         * No appointments found
         */
        object Empty : ListState()

        /**
         * Error loading appointments
         *
         * @param message Error message
         */
        data class Error(val message: String) : ListState()
    }

    // ========================================
    // Detail State
    // ========================================

    /**
     * Appointment detail view state
     *
     * Represents different states when displaying single appointment.
     */
    sealed class DetailState {
        /**
         * No appointment selected (initial state)
         */
        object Idle : DetailState()

        /**
         * Loading appointment detail
         */
        object Loading : DetailState()

        /**
         * Appointment loaded successfully
         *
         * @param appointment The appointment data
         */
        data class Success(
            val appointment: Appointment
        ) : DetailState() {
            /**
             * Check if appointment is in past
             */
            fun isPast(): Boolean = appointment.isPast

            /**
             * Get appointment display time
             */
            fun getDisplayTime(): String = appointment.displayTime

            /**
             * Get appointment display duration
             */
            fun getDisplayDuration(): String = appointment.displayDuration
        }

        /**
         * Error loading appointment
         *
         * @param message Error message
         */
        data class Error(val message: String) : DetailState()
    }

    // ========================================
    // Create Appointment Form State
    // ========================================

    /**
     * Create appointment form state
     *
     * Manages form fields, validation errors, and submission state.
     *
     * @param fieldErrors Map of field name to error message
     * @param isSubmitting true if form is being submitted
     * @param submissionResult Result of form submission
     */
    data class CreateAppointmentState(
        val fieldErrors: Map<String, String> = emptyMap(),
        val isSubmitting: Boolean = false,
        val submissionResult: CreateAppointmentUseCase.CreateAppointmentResult? = null
    ) {
        // ========================================
        // Error Helpers
        // ========================================

        /**
         * Check if specific field has error
         *
         * @param field Field name
         * @return true if field has error
         */
        fun hasFieldError(field: String): Boolean {
            return fieldErrors.containsKey(field)
        }

        /**
         * Get error message for field
         *
         * @param field Field name
         * @return Error message or null
         */
        fun getFieldError(field: String): String? {
            return fieldErrors[field]
        }

        /**
         * Get all errors as list
         *
         * @return List of all error messages
         */
        fun getAllErrors(): List<String> {
            return fieldErrors.values.toList()
        }

        /**
         * Get error count
         *
         * @return Number of validation errors
         */
        fun getErrorCount(): Int {
            return fieldErrors.size
        }

        // ========================================
        // Validation Helpers
        // ========================================

        /**
         * Check if form is valid
         *
         * Form is valid if:
         * - No validation errors
         * - Not currently submitting
         *
         * @return true if form can be submitted
         */
        fun isFormValid(): Boolean {
            return fieldErrors.isEmpty() && !isSubmitting
        }

        /**
         * Check if form has any errors
         *
         * @return true if form has validation errors
         */
        fun hasErrors(): Boolean {
            return fieldErrors.isNotEmpty()
        }

        /**
         * Check if specific field is valid
         *
         * @param field Field name
         * @return true if field has no error
         */
        fun isFieldValid(field: String): Boolean {
            return !fieldErrors.containsKey(field)
        }

        /**
         * Get validation status as text (Portuguese)
         *
         * @return "Válido", "N erros", or "Salvando..."
         */
        fun getValidationStatus(): String {
            return when {
                isSubmitting -> "Salvando..."
                fieldErrors.isEmpty() -> "✓ Válido"
                fieldErrors.size == 1 -> "✗ 1 erro"
                else -> "✗ ${fieldErrors.size} erros"
            }
        }

        // ========================================
        // Submission Helpers
        // ========================================

        /**
         * Check if submission was successful
         *
         * @return true if submission succeeded
         */
        fun isSuccess(): Boolean {
            return submissionResult is CreateAppointmentUseCase.CreateAppointmentResult.Success
        }

        /**
         * Check if submission has validation errors
         *
         * @return true if validation failed during submission
         */
        fun hasSubmissionValidationErrors(): Boolean {
            return submissionResult is CreateAppointmentUseCase.CreateAppointmentResult.ValidationError
        }

        /**
         * Check if submission has system error
         *
         * @return true if system error occurred
         */
        fun hasSubmissionError(): Boolean {
            return submissionResult is CreateAppointmentUseCase.CreateAppointmentResult.Error
        }

        /**
         * Get submission error message
         *
         * @return Error message or null
         */
        fun getSubmissionErrorMessage(): String? {
            return when (val result = submissionResult) {
                is CreateAppointmentUseCase.CreateAppointmentResult.Error -> result.message
                else -> null
            }
        }

        /**
         * Get success message if submission succeeded
         *
         * @return "Agendamento criado com sucesso" or null
         */
        fun getSuccessMessage(): String? {
            return if (isSuccess()) "Agendamento criado com sucesso" else null
        }

        // ========================================
        // UI Helper Methods
        // ========================================

        /**
         * Should show error message?
         *
         * @return true if there are errors and not submitting
         */
        fun shouldShowErrors(): Boolean {
            return fieldErrors.isNotEmpty() && !isSubmitting
        }

        /**
         * Should show loading indicator?
         *
         * @return true if currently submitting
         */
        fun shouldShowLoading(): Boolean {
            return isSubmitting
        }

        /**
         * Should enable submit button?
         *
         * @return true if form valid and not submitting
         */
        fun shouldEnableSubmit(): Boolean {
            return isFormValid() && !isSubmitting
        }

        /**
         * Get submit button text (Portuguese)
         *
         * @return "Salvar" or "Salvando..."
         */
        fun getSubmitButtonText(): String {
            return if (isSubmitting) "Salvando..." else "Salvar"
        }

        /**
         * Get help text for form (Portuguese)
         *
         * @return Help or error message
         */
        fun getHelpText(): String {
            return when {
                hasErrors() -> "Corrija os erros abaixo"
                isSubmitting -> "Criando agendamento..."
                else -> "Preencha os campos para continuar"
            }
        }
    }

    // ========================================
    // Global List State (bottom-nav Consultas tab)
    // ========================================

    /**
     * Global appointment list state — all patients, with payment-status filter.
     */
    sealed class GlobalListState {
        object Loading : GlobalListState()

        data class Success(
            val allAppointments: List<AppointmentWithPaymentStatus>,
            val filteredAppointments: List<AppointmentWithPaymentStatus>,
            val activeFilter: AppointmentFilter
        ) : GlobalListState() {
            fun getCount(): Int = filteredAppointments.size
            fun isEmpty(): Boolean = filteredAppointments.isEmpty()
        }

        object Empty : GlobalListState()

        data class Error(val message: String) : GlobalListState()
    }

    // ========================================
    // Enums and Supporting Classes
    // ========================================

    /**
     * Appointment payment-status filter for the global list screen.
     */
    enum class AppointmentFilter {
        ALL,     // All appointments
        PENDING, // Only appointments with pending payment (hasPendingPayment = true)
        PAID     // Only appointments with payment linked (hasPendingPayment = false)
    }

    /**
     * Appointment list filter (legacy — patient-specific screen)
     */
    enum class ListFilter {
        ALL,      // All appointments
        UPCOMING, // Future appointments only
        PAST      // Past appointments only
    }

    // ========================================
    // Delete Appointment State
    // ========================================

    /**
     * State for the delete-appointment flow in AppointmentFormScreen.
     */
    sealed class DeleteAppointmentState {
        /** No delete in progress. */
        object Idle : DeleteAppointmentState()
        /** Waiting for user to confirm the irreversible delete dialog. */
        object AwaitingConfirmation : DeleteAppointmentState()
        /** Waiting for biometric authentication after dialog confirmation. */
        object AwaitingAuth : DeleteAppointmentState()
        /** Delete is executing. */
        object InProgress : DeleteAppointmentState()
        /** Delete completed successfully. */
        object Success : DeleteAppointmentState()
        /** Delete failed with an error message. */
        data class Error(val message: String) : DeleteAppointmentState()
    }

    /**
     * Appointment list sort order
     */
    enum class ListSort {
        NEWEST_FIRST,    // date DESC (most recent first)
        OLDEST_FIRST,    // date ASC (earliest first)
        CHRONOLOGICAL    // date ASC, time ASC (timeline order)
    }

    /**
     * Form validation status
     */
    enum class ValidationStatus {
        VALID,      // No errors
        INVALID,    // Has errors
        SUBMITTING  // Form is being submitted
    }
}
