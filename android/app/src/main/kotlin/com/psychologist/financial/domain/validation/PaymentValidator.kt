package com.psychologist.financial.domain.validation

import android.util.Log
import com.psychologist.financial.domain.models.Payment
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Validator for Payment data
 *
 * Enforces business rules for payment recording:
 * - Amount: BigDecimal, positive, > 0, max 999999.99
 * - Patient ID: > 0 (required)
 * - Appointment IDs: Optional list, all IDs must be > 0 if provided
 *
 * Migration note (v2→v3):
 * - Removed: Payment method and status validation (no longer fields in Payment model)
 * - All payments are now implicitly PAID (status field removed)
 * - Appointment linking is via junction table (many-to-many)
 *
 * Architecture:
 * - Validation layer (domain)
 * - Independent of UI and database
 * - Testable without framework
 * - Used by use cases before repository operations
 *
 * Validation Flow:
 * 1. PaymentValidator.validate(payment: Payment) checks domain constraints
 * 2. Use case applies validator
 * 3. Repository checks related data (patient existence, appointment validity)
 * 4. Database enforces constraints
 * (Defense in depth)
 *
 * Usage:
 * ```kotlin
 * val validator = PaymentValidator()
 *
 * val payment = Payment(
 *     id = 0L,
 *     patientId = 1L,
 *     amount = BigDecimal("150.00"),
 *     paymentDate = LocalDate.of(2024, 3, 15),
 *     appointmentIds = listOf(10L, 11L)
 * )
 *
 * val result = validator.validate(payment)
 *
 * if (!result.isValid) {
 *     showErrors(result.errors)
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
    }

    /**
     * Validate payment domain model
     *
     * Checks all required fields and business rules for payment creation.
     * Returns ValidationResult with isValid flag and error list.
     *
     * @param payment Payment to validate
     * @return ValidationResult containing isValid flag and errors list
     */
    fun validate(payment: Payment): ValidationResult {
        val errors = mutableListOf<String>()

        // Validate amount
        validateAmount(payment.amount).let { errors.addAll(it) }

        // Validate patient ID
        validatePatientId(payment.patientId).let { errors.addAll(it) }

        // Validate appointment IDs (optional, but if provided, must be valid)
        validateAppointmentIds(payment.appointmentIds).let { errors.addAll(it) }

        if (errors.isNotEmpty()) {
            Log.w(TAG, "Validation failed: ${errors.size} errors")
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }

    /**
     * Validate payment amount
     *
     * Rules:
     * - Must be positive (> 0)
     * - Minimum: 0.01
     * - Maximum: 999999.99
     *
     * @param amount Amount to validate
     * @return List of error messages (empty if valid)
     */
    private fun validateAmount(amount: BigDecimal): List<String> {
        val errors = mutableListOf<String>()

        // Must be positive
        if (amount <= BigDecimal.ZERO) {
            errors.add("Valor deve ser maior que zero")
        }

        // Maximum amount
        if (amount > AMOUNT_MAX) {
            errors.add("Valor não pode exceder R\$ ${AMOUNT_MAX.toPlainString()}")
        }

        return errors
    }

    /**
     * Validate patient ID
     *
     * Rules:
     * - Must be positive (> 0)
     *
     * @param patientId Patient ID to validate
     * @return List of error messages (empty if valid)
     */
    private fun validatePatientId(patientId: Long): List<String> {
        val errors = mutableListOf<String>()

        if (patientId <= 0) {
            errors.add("Paciente inválido")
        }

        return errors
    }

    /**
     * Validate appointment IDs
     *
     * Rules:
     * - Optional (list can be empty)
     * - If provided, all IDs must be positive (> 0)
     *
     * @param appointmentIds List of appointment IDs to validate
     * @return List of error messages (empty if valid)
     */
    private fun validateAppointmentIds(appointmentIds: List<Long>): List<String> {
        val errors = mutableListOf<String>()

        // Check each appointment ID is positive
        val invalidIds = appointmentIds.filter { it <= 0 }
        if (invalidIds.isNotEmpty()) {
            errors.add("IDs de consulta inválidos: $invalidIds")
        }

        return errors
    }
}

/**
 * Validation result data class
 *
 * Encapsulates validation outcome with validity flag and error messages.
 *
 * @param isValid true if validation passed, false otherwise
 * @param errors List of error messages (empty if valid)
 */
data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String>
)


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
            // Format: 1.500,00 (Brazilian - comma is decimal, dot is thousands)
            // Detected by: last "," comes after last "." → comma is the decimal separator
            trimmed.contains(",") && trimmed.contains(".") &&
                    trimmed.lastIndexOf(",") > trimmed.lastIndexOf(".") -> {
                trimmed.replace(".", "").replace(",", ".")
            }
            // Format: 1,500.00 (US - dot is decimal, comma is thousands)
            // Detected by: last "." comes after last "," → dot is the decimal separator
            trimmed.contains(",") && trimmed.contains(".") &&
                    trimmed.lastIndexOf(".") > trimmed.lastIndexOf(",") -> {
                trimmed.replace(",", "")
            }
            // Format: 150,00 (Brazilian no thousands separator - only comma = decimal)
            trimmed.contains(",") && !trimmed.contains(".") -> {
                trimmed.replace(",", ".")
            }
            // Format: 150.00 (already normalized decimal)
            else -> trimmed
        }

        return BigDecimal(normalized)
    } catch (e: Exception) {
        Log.w("PaymentValidator", "Failed to parse amount: $amountString", e)
        return null
    }
}
