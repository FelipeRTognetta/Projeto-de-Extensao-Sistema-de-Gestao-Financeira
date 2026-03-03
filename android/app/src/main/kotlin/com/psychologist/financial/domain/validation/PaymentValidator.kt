package com.psychologist.financial.domain.validation

import android.util.Log
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Validator for Payment data
 *
 * Enforces business rules for payment recording:
 * - Amount: BigDecimal, positive, max 999999.99
 * - Payment Date: Valid LocalDate, cannot be in future
 * - Payment Method: Required, valid method (Dinheiro, Débito, Crédito, Pix, Cheque, Outro)
 * - Payment Status: PAID or PENDING
 * - Patient Status: Only ACTIVE patients can have payments created
 *
 * Architecture:
 * - Validation layer (domain)
 * - Independent of UI and database
 * - Testable without framework
 * - Used by use cases before repository operations
 *
 * Validation Flow:
 * 1. PaymentValidator checks format/constraints
 * 2. Use case applies validator
 * 3. Repository checks related data (patient existence, appointment if linked)
 * 4. Database enforces constraints
 * (Defense in depth)
 *
 * Usage:
 * ```kotlin
 * val validator = PaymentValidator()
 *
 * val errors = validator.validateNewPayment(
 *     amount = BigDecimal("150.00"),
 *     paymentDate = LocalDate.of(2024, 3, 15),
 *     method = "Débito",
 *     status = "PAID",
 *     patientStatus = "ACTIVE"
 * )
 *
 * if (errors.isNotEmpty()) {
 *     showErrors(errors)
 * } else {
 *     createPayment()
 * }
 * ```
 *
 * Error Messages:
 * - Portuguese localization for user display
 * - Clear, actionable feedback
 * - Field-specific errors
 */
class PaymentValidator {

    private companion object {
        private const val TAG = "PaymentValidator"

        // Validation constants
        private val AMOUNT_MIN = BigDecimal("0.01")
        private val AMOUNT_MAX = BigDecimal("999999.99")
        private val VALID_METHODS = setOf(
            "Dinheiro", "Débito", "Crédito", "Pix", "Cheque", "Outro",
            "dinheiro", "débito", "crédito", "pix", "cheque", "outro"
        )
        private val VALID_STATUSES = setOf("PAID", "PENDING", "Pago", "Pendente")
    }

    /**
     * Validate new payment data
     *
     * Checks all required fields for new payment creation.
     * Returns list of validation errors (empty if valid).
     *
     * @param amount Payment amount in BigDecimal
     * @param paymentDate Date of payment
     * @param method Payment method (e.g., "Débito", "Crédito", "Pix")
     * @param status Payment status (PAID or PENDING)
     * @param patientStatus Patient status (to prevent payments for inactive)
     * @return List of ValidationError (empty if valid)
     *
     * Example:
     * ```kotlin
     * val errors = validator.validateNewPayment(
     *     amount = BigDecimal("150.00"),
     *     paymentDate = LocalDate.of(2024, 3, 15),
     *     method = "Débito",
     *     status = "PAID",
     *     patientStatus = "ACTIVE"
     * )
     * ```
     */
    fun validateNewPayment(
        amount: BigDecimal?,
        paymentDate: LocalDate,
        method: String,
        status: String,
        patientStatus: String = "ACTIVE"
    ): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        // Validate patient status first (blocking check)
        validatePatientStatus(patientStatus).let { errors.addAll(it) }

        // Validate amount
        validateAmount(amount).let { errors.addAll(it) }

        // Validate payment date
        validatePaymentDate(paymentDate).let { errors.addAll(it) }

        // Validate method
        validateMethod(method).let { errors.addAll(it) }

        // Validate status
        validateStatus(status).let { errors.addAll(it) }

        if (errors.isNotEmpty()) {
            Log.w(TAG, "Validation failed: ${errors.size} errors")
        }

        return errors
    }

    /**
     * Validate payment amount
     *
     * Rules:
     * - Required (not null, not empty)
     * - Must be positive (> 0)
     * - Minimum: 0.01
     * - Maximum: 999999.99
     * - Must have valid decimal precision (max 2 decimal places)
     *
     * @param amount Amount to validate
     * @return List of errors (empty if valid)
     */
    fun validateAmount(amount: BigDecimal?): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        // Required
        if (amount == null) {
            errors.add(ValidationError(
                field = "amount",
                message = "Valor é obrigatório"
            ))
            return errors
        }

        // Positive
        if (amount <= BigDecimal.ZERO) {
            errors.add(ValidationError(
                field = "amount",
                message = "Valor deve ser maior que zero"
            ))
        }

        // Minimum amount
        if (amount < AMOUNT_MIN) {
            errors.add(ValidationError(
                field = "amount",
                message = "Valor mínimo é ${AMOUNT_MIN.toPlainString()}"
            ))
        }

        // Maximum amount
        if (amount > AMOUNT_MAX) {
            errors.add(ValidationError(
                field = "amount",
                message = "Valor não pode exceder ${AMOUNT_MAX.toPlainString()}"
            ))
        }

        // Decimal precision check (max 2 decimal places)
        val scale = amount.scale()
        if (scale > 2) {
            errors.add(ValidationError(
                field = "amount",
                message = "Valor pode ter no máximo 2 casas decimais"
            ))
        }

        return errors
    }

    /**
     * Validate payment date
     *
     * Rules:
     * - Required (not null)
     * - Cannot be in future (payments are recorded for past/current transactions)
     * - Must be a valid LocalDate
     *
     * Note: Past dates are allowed for entering historical payment data
     *
     * @param paymentDate Date to validate
     * @return List of errors (empty if valid)
     */
    fun validatePaymentDate(paymentDate: LocalDate): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        val today = LocalDate.now()

        // Cannot be in future
        if (paymentDate.isAfter(today)) {
            errors.add(ValidationError(
                field = "paymentDate",
                message = "Data do pagamento não pode ser no futuro"
            ))
        }

        return errors
    }

    /**
     * Validate payment method
     *
     * Rules:
     * - Required (not null, not empty)
     * - Must be one of valid methods (Dinheiro, Débito, Crédito, Pix, Cheque, Outro)
     * - Case-insensitive
     *
     * @param method Method to validate
     * @return List of errors (empty if valid)
     */
    fun validateMethod(method: String): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        val trimmed = method.trim()

        // Required
        if (trimmed.isEmpty()) {
            errors.add(ValidationError(
                field = "method",
                message = "Método de pagamento é obrigatório"
            ))
            return errors
        }

        // Valid method
        if (!VALID_METHODS.contains(trimmed)) {
            errors.add(ValidationError(
                field = "method",
                message = "Método de pagamento inválido. Opções: Dinheiro, Débito, Crédito, Pix, Cheque, Outro"
            ))
        }

        return errors
    }

    /**
     * Validate payment status
     *
     * Rules:
     * - Required (not null, not empty)
     * - Must be PAID or PENDING
     * - Case-insensitive
     *
     * @param status Status to validate
     * @return List of errors (empty if valid)
     */
    fun validateStatus(status: String): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        val trimmed = status.trim().uppercase()

        // Required
        if (trimmed.isEmpty()) {
            errors.add(ValidationError(
                field = "status",
                message = "Status de pagamento é obrigatório"
            ))
            return errors
        }

        // Valid status
        if (trimmed !in setOf("PAID", "PENDING")) {
            errors.add(ValidationError(
                field = "status",
                message = "Status deve ser 'Pago' ou 'Pendente'"
            ))
        }

        return errors
    }

    /**
     * Validate patient status
     *
     * Business rule: Only ACTIVE patients can have new payments created.
     * Prevents accidental payment recording for inactive/archived patients.
     *
     * @param patientStatus Patient status (e.g., "ACTIVE", "INACTIVE")
     * @return List of errors (empty if valid)
     */
    fun validatePatientStatus(patientStatus: String): List<ValidationError> {
        val status = patientStatus.trim().uppercase()

        // Only ACTIVE patients can have payments
        if (status != "ACTIVE") {
            return listOf(ValidationError(
                field = "patientStatus",
                message = "Não é possível criar pagamento para paciente inativo"
            ))
        }

        return emptyList()
    }

    /**
     * Validate payment update (same as new, but with id)
     *
     * For future use when implementing payment edit.
     *
     * @param paymentId Current payment ID
     * @param amount Updated amount
     * @param paymentDate Updated date
     * @param method Updated method
     * @param status Updated status
     * @param patientStatus Patient status
     * @return List of errors (empty if valid)
     */
    fun validateUpdate(
        paymentId: Long,
        amount: BigDecimal?,
        paymentDate: LocalDate,
        method: String,
        status: String,
        patientStatus: String = "ACTIVE"
    ): List<ValidationError> {
        require(paymentId > 0) { "Payment must be saved (id > 0) to update" }

        // Use same validation as new payment
        return validateNewPayment(amount, paymentDate, method, status, patientStatus)
    }
}

/**
 * Validate payment amount quickly
 *
 * @param amount Amount to check
 * @return true if amount is valid
 */
fun isValidPaymentAmount(amount: BigDecimal?): Boolean {
    if (amount == null) return false
    return amount > BigDecimal.ZERO &&
            amount >= BigDecimal("0.01") &&
            amount <= BigDecimal("999999.99") &&
            amount.scale() <= 2
}

/**
 * Validate payment date quickly
 *
 * @param date Date to check
 * @return true if date is valid (not in future)
 */
fun isValidPaymentDate(date: LocalDate): Boolean {
    return !date.isAfter(LocalDate.now())
}

/**
 * Validate payment method quickly
 *
 * @param method Method to check
 * @return true if method is valid
 */
fun isValidPaymentMethod(method: String?): Boolean {
    if (method.isNullOrEmpty()) return false
    val validMethods = setOf(
        "Dinheiro", "Débito", "Crédito", "Pix", "Cheque", "Outro",
        "dinheiro", "débito", "crédito", "pix", "cheque", "outro"
    )
    return validMethods.contains(method.trim())
}

/**
 * Validate payment status quickly
 *
 * @param status Status to check
 * @return true if status is valid (PAID or PENDING)
 */
fun isValidPaymentStatus(status: String?): Boolean {
    if (status.isNullOrEmpty()) return false
    val trimmed = status.trim().uppercase()
    return trimmed in setOf("PAID", "PENDING")
}

/**
 * Validate patient status for payment creation
 *
 * @param patientStatus Patient status string
 * @return true if patient is active and can have payments
 */
fun canCreatePaymentForPatient(patientStatus: String): Boolean {
    return patientStatus.trim().uppercase() == "ACTIVE"
}

/**
 * Format payment amount as currency string
 *
 * Examples:
 * - BigDecimal("150.00") → "R$ 150,00"
 * - BigDecimal("1500.50") → "R$ 1.500,50"
 * - BigDecimal("0.01") → "R$ 0,01"
 *
 * @param amount Amount to format
 * @return Formatted currency string
 */
fun formatPaymentAmount(amount: BigDecimal): String {
    val absAmount = amount.abs()
    val formatted = String.format("%,.2f", absAmount)
        .replace(".", "#")  // Temporary placeholder
        .replace(",", ".")   // Replace thousands separator
        .replace("#", ",")   // Replace decimal separator
    return "R$ $formatted"
}

/**
 * Parse payment amount string to BigDecimal
 *
 * Handles various formats:
 * - "150.00" → BigDecimal("150.00")
 * - "150,00" → BigDecimal("150.00")
 * - "1.500,00" → BigDecimal("1500.00")
 * - "1,500.00" → BigDecimal("1500.00")
 *
 * @param amountString Amount string to parse
 * @return BigDecimal or null if invalid
 */
fun parsePaymentAmount(amountString: String?): BigDecimal? {
    if (amountString.isNullOrEmpty()) return null

    try {
        val trimmed = amountString.trim()

        // Normalize format: remove thousands separator and normalize decimal
        val normalized = when {
            // Format: 1.500,00 (Brazilian - thousands with dot, decimal with comma)
            trimmed.contains(",") && trimmed.lastIndexOf(".") > trimmed.lastIndexOf(",") -> {
                trimmed.replace(".", "").replace(",", ".")
            }
            // Format: 1,500.00 (US - thousands with comma, decimal with dot)
            trimmed.contains(",") && (trimmed.lastIndexOf(",") > trimmed.lastIndexOf(".") || !trimmed.contains(".")) -> {
                trimmed.replace(",", "")
            }
            // Format: 1.500 (ambiguous - assume thousands)
            trimmed.contains(".") && trimmed.count { it == '.' } == 1 && trimmed.lastIndexOf(".") > trimmed.length - 4 -> {
                trimmed
            }
            // Format: 150.00 or 150,00 (already normalized or single occurrence)
            else -> trimmed.replace(",", ".")
        }

        return BigDecimal(normalized)
    } catch (e: Exception) {
        Log.w("PaymentValidator", "Failed to parse amount: $amountString", e)
        return null
    }
}
