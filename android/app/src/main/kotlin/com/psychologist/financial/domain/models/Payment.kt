package com.psychologist.financial.domain.models

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Payment domain model
 *
 * Represents a payment transaction in the domain layer.
 * Mapped from PaymentEntity (database) for business logic.
 *
 * Responsibilities:
 * - Represent payment data in domain layer
 * - Provide business logic helper methods
 * - Independent of database/UI frameworks
 * - Rich model with computed properties
 *
 * Properties:
 * - id: Unique payment identifier
 * - patientId: Link to patient
 * - amount: Payment amount (BigDecimal for currency)
 * - paymentDate: When paid
 * - createdDate: When payment record created
 * - appointmentIds: List of appointment IDs this payment covers (empty if no appointments linked)
 *
 * Computed Properties:
 * - isToday: True if payment date is today
 * - isFuture: True if payment date in future
 * - daysUntilDue: Days until payment date
 * - displayAmount: Formatted currency string
 *
 * Business Rules:
 * - Amount: > 0, max 999,999.99
 * - Patient Link: Required (every payment has patient)
 * - Appointment Links: Optional (0 or more appointments)
 * - All payments are PAID (no status field; field was removed per v2→v3 migration)
 *
 * Migration note (v2→v3):
 * - Removed field: status (all payments are PAID)
 * - Removed field: paymentMethod (business simplification)
 * - Changed field: appointmentId (single FK) → appointmentIds (List, junction table)
 *
 * Example:
 * ```kotlin
 * val payment = Payment(
 *     id = 1L,
 *     patientId = 1L,
 *     amount = BigDecimal("150.00"),
 *     paymentDate = LocalDate.of(2024, 3, 15),
 *     appointmentIds = listOf(10L, 15L)
 * )
 *
 * // Business logic
 * println(payment.displayAmount)  // "R$ 150,00"
 * println(payment.daysUntilDue)   // -20 (20 days ago)
 * ```
 *
 * Comparison with Entity:
 * - PaymentEntity: Database representation (5 fields)
 * - Payment: Business logic representation (with appointmentIds)
 * - Conversion: Repository handles Entity ↔ Model mapping
 */
data class Payment(
    val id: Long,
    val patientId: Long,
    val amount: BigDecimal,
    val paymentDate: LocalDate,
    val createdDate: LocalDateTime = LocalDateTime.now(),
    val appointmentIds: List<Long> = emptyList()
) {

    // ========================================
    // Computed Properties
    // ========================================

    /**
     * Check if payment date is today
     *
     * @return true if payment date is today
     */
    val isToday: Boolean
        get() = paymentDate == LocalDate.now()

    /**
     * Check if payment date is in future
     *
     * @return true if payment date is upcoming
     */
    val isFuture: Boolean
        get() = paymentDate > LocalDate.now()

    /**
     * Check if payment has appointment links
     *
     * @return true if appointmentIds is not empty
     */
    val hasLinkedAppointments: Boolean
        get() = appointmentIds.isNotEmpty()

    /**
     * Get days until payment due
     *
     * Returns negative for past payments, 0 for today.
     *
     * @return Number of days until payment date
     */
    val daysUntilDue: Long
        get() = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), paymentDate)

    /**
     * Format amount as currency string
     *
     * Format: "R$ 150,00"
     *
     * @return Formatted currency string
     */
    val displayAmount: String
        get() = "R$ ${amount.setScale(2, java.math.RoundingMode.HALF_UP)}"

    /**
     * Format amount for math operations (decimal with 2 places)
     *
     * @return Amount with proper decimal places
     */
    val formattedAmount: BigDecimal
        get() = amount.setScale(2, java.math.RoundingMode.HALF_UP)

    // ========================================
    // Display Methods
    // ========================================

    /**
     * Get formatted date string (Portuguese)
     *
     * Format: "dd/MM/yyyy" (e.g., "15/03/2024")
     *
     * @return Formatted date string
     */
    fun getFormattedDate(): String {
        val day = paymentDate.dayOfMonth.toString().padStart(2, '0')
        val month = paymentDate.monthValue.toString().padStart(2, '0')
        val year = paymentDate.year
        return "$day/$month/$year"
    }

    /**
     * Get payment description
     *
     * Format: "R$ 150,00 - Pago em 15/03/2024"
     *
     * @return Description string
     */
    fun getDescription(): String {
        return "$displayAmount - Pago em ${getFormattedDate()}"
    }

    /**
     * Get relative date display (Portuguese)
     *
     * Examples:
     * - Today: "Hoje"
     * - Tomorrow: "Amanhã"
     * - Other: "15/03/2024"
     *
     * @return Relative or absolute date string
     */
    fun getRelativeDate(): String {
        return when {
            isToday -> "Hoje"
            daysUntilDue == 1L -> "Amanhã"
            else -> getFormattedDate()
        }
    }

    /**
     * Check if payment data is valid
     *
     * Business rule validations:
     * - Amount: > 0, <= 999,999.99
     * - Patient ID: > 0
     *
     * @return true if all validations pass
     */
    fun isValid(): Boolean {
        return patientId > 0 &&
                amount > BigDecimal.ZERO &&
                amount <= BigDecimal("999999.99")
    }

    /**
     * Get validation error if invalid
     *
     * @return Error message or null if valid
     */
    fun getValidationError(): String? {
        if (patientId <= 0) return "Paciente inválido"
        if (amount <= BigDecimal.ZERO) return "Valor deve ser maior que 0"
        if (amount > BigDecimal("999999.99")) return "Valor máximo é R$ 999.999,99"
        return null
    }

    // ========================================
    // Companion Object
    // ========================================

    companion object {
        // Constraints
        val MIN_AMOUNT = BigDecimal.ONE
        val MAX_AMOUNT = BigDecimal("999999.99")

        /**
         * Create sample payment for testing
         *
         * @param patientId Patient ID (default 1L)
         * @param offset Days offset from today (default 0 = today)
         * @param appointmentIds Linked appointment IDs (default empty)
         * @return Sample payment
         */
        fun sample(
            patientId: Long = 1L,
            offset: Long = 0,
            appointmentIds: List<Long> = emptyList()
        ): Payment {
            return Payment(
                id = 0L,
                patientId = patientId,
                amount = BigDecimal("150.00"),
                paymentDate = LocalDate.now().plusDays(offset),
                appointmentIds = appointmentIds
            )
        }
    }
}

