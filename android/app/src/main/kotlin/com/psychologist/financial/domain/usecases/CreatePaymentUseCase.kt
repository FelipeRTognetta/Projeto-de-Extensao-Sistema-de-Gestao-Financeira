package com.psychologist.financial.domain.usecases

import android.util.Log
import com.psychologist.financial.data.repositories.PatientRepository
import com.psychologist.financial.data.repositories.PaymentRepository
import com.psychologist.financial.domain.models.Payment
import com.psychologist.financial.domain.models.PatientStatus
import com.psychologist.financial.domain.validation.PaymentValidator
import com.psychologist.financial.domain.validation.ValidationError
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Use case: Create a new payment with validation
 *
 * Responsibilities:
 * - Validate payment data before insertion
 * - Enforce business rules (patient must be ACTIVE)
 * - Prevent payments for inactive patients
 * - Save to repository
 * - Return validation errors or generated ID
 *
 * Architecture:
 * - Validation happens here (use case layer)
 * - Patient status check enforced here
 * - Business rules enforced before repository call
 * - Repository re-validates (defense in depth)
 * - ViewModels call this use case with user input
 *
 * Validation Rules:
 * - Amount: > 0, max 999,999.99
 * - Status: PAID or PENDING only
 * - Patient ID: Must be valid and ACTIVE (not INACTIVE)
 * - Payment Method: Not empty
 * - Payment Date: Valid date
 *
 * Business Rules:
 * - INACTIVE patients cannot receive payments (prevents archival mutations)
 * - Amount precision: 2 decimal places
 * - All payments must be linked to existing patients
 *
 * Error Handling:
 * - Returns CreatePaymentResult for all outcomes
 * - Caller handles errors (show UI messages, logging, etc.)
 * - No exceptions thrown for validation errors
 * - Technical errors propagate (database errors, etc.)
 *
 * Usage:
 * ```kotlin
 * val useCase = CreatePaymentUseCase(
 *     paymentRepository,
 *     patientRepository,
 *     paymentValidator
 * )
 *
 * // Create with validation
 * val result = useCase.execute(
 *     patientId = 1L,
 *     appointmentId = null,
 *     amount = BigDecimal("150.00"),
 *     status = Payment.STATUS_PAID,
 *     paymentMethod = Payment.METHOD_TRANSFER,
 *     paymentDate = LocalDate.now()
 * )
 *
 * when (result) {
 *     is CreatePaymentResult.Success -> {
 *         Log.d("Payment created: ${result.paymentId}")
 *         navigateToPaymentList()
 *     }
 *     is CreatePaymentResult.ValidationError -> {
 *         showError(result.errors)
 *     }
 * }
 * ```
 *
 * @property paymentRepository PaymentRepository for data persistence
 * @property patientRepository PatientRepository for patient verification
 * @property paymentValidator PaymentValidator for validation rules
 */
class CreatePaymentUseCase(
    private val paymentRepository: PaymentRepository,
    private val patientRepository: PatientRepository,
    private val paymentValidator: PaymentValidator
) {
    private companion object {
        private const val TAG = "CreatePaymentUseCase"
    }

    /**
     * Create payment with all validation
     *
     * Validates input, checks patient status, persists to repository.
     *
     * Validation occurs in this order:
     * 1. PaymentValidator rules (amount range, status, method)
     * 2. Patient existence check
     * 3. Patient status check (must be ACTIVE)
     * 4. Database insertion
     *
     * @param patientId Patient ID
     * @param appointmentId Optional appointment ID to link
     * @param amount Payment amount (BigDecimal with 2 decimal places)
     * @param status Payment status (PAID or PENDING)
     * @param paymentMethod Payment method (CASH, TRANSFER, etc.)
     * @param paymentDate Payment date
     * @return CreatePaymentResult (Success with ID, or ValidationError)
     *
     * Example:
     * ```kotlin
     * val result = useCase.execute(
     *     patientId = 1L,
     *     appointmentId = null,
     *     amount = BigDecimal("150.00"),
     *     status = Payment.STATUS_PAID,
     *     paymentMethod = Payment.METHOD_TRANSFER,
     *     paymentDate = LocalDate.now()
     * )
     * ```
     */
    suspend fun execute(
        patientId: Long,
        appointmentId: Long? = null,
        amount: BigDecimal,
        status: String,
        paymentMethod: String,
        paymentDate: LocalDate
    ): CreatePaymentResult {
        return try {
            // Validate payment data
            val validationErrors = paymentValidator.validateNewPayment(
                amount = amount,
                paymentDate = paymentDate,
                method = paymentMethod,
                status = status
            )

            if (validationErrors.isNotEmpty()) {
                Log.w(TAG, "Validation failed: ${validationErrors.size} errors")
                return CreatePaymentResult.ValidationError(validationErrors)
            }

            // Validate patient exists
            val patient = patientRepository.getPatient(patientId)
            if (patient == null) {
                Log.w(TAG, "Patient not found: $patientId")
                val error = ValidationError(
                    field = "patientId",
                    message = "Paciente não encontrado"
                )
                return CreatePaymentResult.ValidationError(listOf(error))
            }

            // Validate patient is active
            if (patient.status != PatientStatus.ACTIVE) {
                Log.w(TAG, "Patient is inactive: $patientId, status=${patient.status}")
                val error = ValidationError(
                    field = "patientId",
                    message = "Não é possível adicionar pagamentos para pacientes inativos"
                )
                return CreatePaymentResult.ValidationError(listOf(error))
            }

            // Create payment object
            val payment = Payment(
                id = 0,  // Unsaved
                patientId = patientId,
                appointmentId = appointmentId,
                amount = amount.setScale(2, java.math.RoundingMode.HALF_UP),
                status = status.uppercase(),
                paymentMethod = paymentMethod.uppercase(),
                paymentDate = paymentDate
            )

            // Validate payment is valid
            if (!payment.isValid()) {
                Log.w(TAG, "Payment object is invalid: $payment")
                val error = ValidationError(
                    field = "payment",
                    message = payment.getValidationError() ?: "Dados de pagamento inválidos"
                )
                return CreatePaymentResult.ValidationError(listOf(error))
            }

            // Save to repository
            val paymentId = paymentRepository.insert(
                patientId = patientId,
                appointmentId = appointmentId,
                amount = payment.amount,
                status = payment.status,
                paymentMethod = payment.paymentMethod,
                paymentDate = paymentDate
            )
            Log.d(TAG, "Payment created successfully: id=$paymentId, patient=$patientId, amount=$amount")

            CreatePaymentResult.Success(paymentId)
        } catch (e: IllegalArgumentException) {
            // Repository validation error
            Log.w(TAG, "Repository validation error: ${e.message}")
            val error = ValidationError(
                field = "general",
                message = e.message ?: "Não foi possível criar o pagamento"
            )
            CreatePaymentResult.ValidationError(listOf(error))
        } catch (e: Exception) {
            // Technical error
            Log.e(TAG, "Failed to create payment", e)
            throw e
        }
    }

    /**
     * Create payment with Payment object
     *
     * Useful when Payment object already constructed.
     * Still validates before insertion.
     *
     * @param payment Payment to create (must have id=0)
     * @return CreatePaymentResult
     *
     * Example:
     * ```kotlin
     * val payment = Payment(
     *     id = 0,
     *     patientId = 1L,
     *     appointmentId = null,
     *     amount = BigDecimal("150.00"),
     *     status = Payment.STATUS_PAID,
     *     paymentMethod = Payment.METHOD_TRANSFER,
     *     paymentDate = LocalDate.now()
     * )
     * val result = useCase.executeWithObject(payment)
     * ```
     */
    suspend fun executeWithObject(payment: Payment): CreatePaymentResult {
        require(payment.id == 0L) { "Payment must be unsaved (id = 0)" }

        return execute(
            patientId = payment.patientId,
            appointmentId = payment.appointmentId,
            amount = payment.amount,
            status = payment.status,
            paymentMethod = payment.paymentMethod,
            paymentDate = payment.paymentDate
        )
    }

    /**
     * Validate without saving
     *
     * Check if payment would be valid without creating it.
     * Useful for form validation feedback.
     *
     * Includes patient status check.
     *
     * @param patientId Patient ID
     * @param amount Payment amount
     * @param status Payment status
     * @param paymentMethod Payment method
     * @param paymentDate Payment date
     * @return List of ValidationError, empty if valid
     *
     * Example:
     * ```kotlin
     * val errors = useCase.validate(
     *     patientId,
     *     BigDecimal("150.00"),
     *     Payment.STATUS_PAID,
     *     Payment.METHOD_TRANSFER,
     *     LocalDate.now()
     * )
     * if (errors.isEmpty()) {
     *     // Show "Ready to save" state
     * } else {
     *     // Show validation errors
     * }
     * ```
     */
    suspend fun validate(
        patientId: Long,
        amount: BigDecimal,
        status: String,
        paymentMethod: String,
        paymentDate: LocalDate
    ): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        // Validate payment data
        errors.addAll(
            paymentValidator.validateNewPayment(
                amount = amount,
                paymentDate = paymentDate,
                method = paymentMethod,
                status = status
            )
        )

        // Validate patient exists
        val patient = patientRepository.getPatient(patientId)
        if (patient == null) {
            errors.add(
                ValidationError(
                    field = "patientId",
                    message = "Paciente não encontrado"
                )
            )
            return errors
        }

        // Validate patient is active
        if (patient.status != PatientStatus.ACTIVE) {
            errors.add(
                ValidationError(
                    field = "patientId",
                    message = "Não é possível adicionar pagamentos para pacientes inativos"
                )
            )
        }

        return errors
    }
}

/**
 * Result sealed class for CreatePaymentUseCase
 *
 * Represents success or failure of payment creation.
 *
 * Usage:
 * ```kotlin
 * when (result) {
 *     is CreatePaymentResult.Success -> handleSuccess(result.paymentId)
 *     is CreatePaymentResult.ValidationError -> handleErrors(result.errors)
 * }
 * ```
 */
sealed class CreatePaymentResult {
    /**
     * Payment created successfully
     *
     * @property paymentId Generated payment ID (> 0)
     */
    data class Success(val paymentId: Long) : CreatePaymentResult() {
        init {
            require(paymentId > 0) { "Payment ID must be > 0" }
        }
    }

    /**
     * Validation failed
     *
     * @property errors List of validation errors
     */
    data class ValidationError(val errors: List<com.psychologist.financial.domain.validation.ValidationError>) :
        CreatePaymentResult() {
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
