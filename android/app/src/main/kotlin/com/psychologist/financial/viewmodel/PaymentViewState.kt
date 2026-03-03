package com.psychologist.financial.viewmodel

import com.psychologist.financial.domain.models.Payment
import com.psychologist.financial.domain.models.PatientBalance
import com.psychologist.financial.domain.usecases.CreatePaymentResult
import com.psychologist.financial.domain.usecases.CreatePaymentUseCase
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Payment ViewModel state classes
 *
 * Defines sealed classes and data classes for managing payment screen states.
 * Provides type-safe state management for list, detail, form, and balance views.
 *
 * State Hierarchy:
 * - ListState: Payment list view (Loading, Success, Empty, Error)
 * - DetailState: Single payment detail (Idle, Loading, Success, Error)
 * - BalanceState: Balance summary with metrics
 * - CreatePaymentState: Form state with validation and submission
 *
 * Usage:
 * ```kotlin
 * // List state
 * when (paymentListState) {
 *     is PaymentViewState.ListState.Loading -> showLoadingIndicator()
 *     is PaymentViewState.ListState.Success -> displayPayments(payments)
 *     is PaymentViewState.ListState.Empty -> showEmptyMessage()
 *     is PaymentViewState.ListState.Error -> showErrorMessage(message)
 * }
 *
 * // Balance state
 * val balance = balanceState.value
 * println("Due: ${balance.getFormattedAmountDue()}")
 * println("Outstanding: ${balance.getFormattedOutstanding()}")
 *
 * // Form state
 * val formState = createFormState.value
 * val isFormValid = formState.isFormValid()
 * val errors = formState.getAllErrors()
 * ```
 */
object PaymentViewState {

    // ========================================
    // List State
    // ========================================

    /**
     * Payment list view state
     *
     * Represents different states when displaying payment list.
     */
    sealed class ListState {
        /**
         * Loading payments
         */
        object Loading : ListState()

        /**
         * Payments loaded successfully
         *
         * @param payments List of payments
         */
        data class Success(
            val payments: List<Payment>
        ) : ListState() {
            /**
             * Get payment count
             */
            fun getCount(): Int = payments.size

            /**
             * Check if list is empty
             */
            fun isEmpty(): Boolean = payments.isEmpty()

            /**
             * Filter payments by status
             *
             * @param status Payment status (PAID/PENDING)
             * @return Filtered payments
             */
            fun filterByStatus(status: String): List<Payment> {
                return payments.filter { it.status == status }
            }

            /**
             * Get paid payments count
             */
            fun getPaidCount(): Int = payments.count { it.isPaid }

            /**
             * Get pending payments count
             */
            fun getPendingCount(): Int = payments.count { it.isPending }

            /**
             * Get overdue count
             */
            fun getOverdueCount(): Int = payments.count { it.isPastDue }
        }

        /**
         * No payments found
         */
        object Empty : ListState()

        /**
         * Error loading payments
         *
         * @param message Error message
         */
        data class Error(val message: String) : ListState()
    }

    // ========================================
    // Detail State
    // ========================================

    /**
     * Payment detail view state
     *
     * Represents different states when displaying single payment.
     */
    sealed class DetailState {
        /**
         * No payment selected (initial state)
         */
        object Idle : DetailState()

        /**
         * Loading payment detail
         */
        object Loading : DetailState()

        /**
         * Payment loaded successfully
         *
         * @param payment The payment data
         */
        data class Success(
            val payment: Payment
        ) : DetailState() {
            /**
             * Check if payment is paid
             */
            fun isPaid(): Boolean = payment.isPaid

            /**
             * Get payment display amount
             */
            fun getDisplayAmount(): String = payment.displayAmount

            /**
             * Get payment status label
             */
            fun getStatusLabel(): String = payment.getStatusLabel()
        }

        /**
         * Error loading payment
         *
         * @param message Error message
         */
        data class Error(val message: String) : DetailState()
    }

    // ========================================
    // Balance State
    // ========================================

    /**
     * Balance summary state
     *
     * Represents patient's financial status snapshot.
     */
    data class BalanceState(
        val balance: PatientBalance,
        val isLoading: Boolean = false,
        val error: String? = null
    ) {
        /**
         * Check if balance has outstanding
         */
        fun hasOutstanding(): Boolean = balance.hasOutstandingBalance

        /**
         * Get amount due display
         */
        fun getFormattedAmountDue(): String = balance.getFormattedAmountDue()

        /**
         * Get outstanding display
         */
        fun getFormattedOutstanding(): String = balance.getFormattedOutstanding()

        /**
         * Get total balance display
         */
        fun getFormattedTotal(): String = balance.getFormattedTotal()

        /**
         * Get status label
         */
        fun getStatusLabel(): String = balance.getStatusLabel()

        /**
         * Get collection rate
         */
        fun getCollectionRate(): Int = balance.collectionPercentage
    }

    // ========================================
    // Create Payment Form State
    // ========================================

    /**
     * Create payment form state
     *
     * Manages form fields, validation errors, and submission state.
     *
     * @param fieldErrors Map of field name to error message
     * @param isSubmitting true if form is being submitted
     * @param submissionResult Result of form submission
     */
    data class CreatePaymentState(
        val fieldErrors: Map<String, String> = emptyMap(),
        val isSubmitting: Boolean = false,
        val submissionResult: CreatePaymentResult? = null
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
            return submissionResult is CreatePaymentResult.Success
        }

        /**
         * Check if submission has validation errors
         *
         * @return true if validation failed during submission
         */
        fun hasSubmissionValidationErrors(): Boolean {
            return submissionResult is CreatePaymentResult.ValidationError
        }

        /**
         * Get success message with payment ID
         *
         * @return Success message or null
         */
        fun getSuccessMessage(): String? {
            return if (isSuccess()) {
                val paymentId = (submissionResult as CreatePaymentResult.Success).paymentId
                "Pagamento #$paymentId criado com sucesso"
            } else {
                null
            }
        }

        /**
         * Get submission validation errors
         *
         * @return List of validation errors or empty
         */
        fun getSubmissionValidationErrors(): List<com.psychologist.financial.domain.validation.ValidationError> {
            return when (val result = submissionResult) {
                is CreatePaymentResult.ValidationError -> result.errors
                else -> emptyList()
            }
        }
    }

    // ========================================
    // Status Filter
    // ========================================

    /**
     * Payment status filter options
     */
    enum class PaymentStatusFilter {
        ALL, PAID, PENDING, OVERDUE
    }
}
