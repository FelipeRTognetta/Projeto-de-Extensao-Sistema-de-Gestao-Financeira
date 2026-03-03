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
 * - appointmentId: Optional link to appointment
 * - amount: Payment amount (BigDecimal for currency)
 * - status: PAID or PENDING
 * - paymentMethod: CASH, TRANSFER, CARD, CHECK, PIX
 * - paymentDate: When paid or due
 * - createdDate: When payment record created
 *
 * Computed Properties:
 * - isPaid: True if status == PAID
 * - isPending: True if status == PENDING
 * - isPastDue: True if payment date in past and pending
 * - isToday: True if payment date is today
 * - isFuture: True if payment date in future
 * - daysUntilDue: Days until payment date
 * - displayAmount: Formatted currency string
 * - displayStatus: Localized status (e.g., "Pago", "Pendente")
 * - displayMethod: Localized payment method
 *
 * Business Rules:
 * - Amount: > 0, max 999,999.99
 * - Status: PAID or PENDING only
 * - Patient Link: Required (every payment has patient)
 * - Appointment Link: Optional (payment can be linked or unlinked)
 *
 * Example:
 * ```kotlin
 * val payment = Payment(
 *     id = 1L,
 *     patientId = 1L,
 *     appointmentId = null,
 *     amount = BigDecimal("150.00"),
 *     status = Payment.STATUS_PAID,
 *     paymentMethod = Payment.METHOD_TRANSFER,
 *     paymentDate = LocalDate.of(2024, 3, 15)
 * )
 *
 * // Business logic
 * println(payment.isPaid)  // true
 * println(payment.displayAmount)  // "R$ 150,00"
 * println(payment.displayStatus)  // "Pago"
 * println(payment.getDescription()) // "R$ 150,00 - Pago em 15/03/2024"
 * ```
 *
 * Comparison with Entity:
 * - PaymentEntity: Database representation
 * - Payment: Business logic representation
 * - Conversion: Repository handles Entity ↔ Model mapping
 */
data class Payment(
    val id: Long,
    val patientId: Long,
    val appointmentId: Long? = null,
    val amount: BigDecimal,
    val status: String,  // PAID or PENDING
    val paymentMethod: String,  // CASH, TRANSFER, CARD, CHECK, PIX
    val paymentDate: LocalDate,
    val createdDate: LocalDateTime = LocalDateTime.now()
) {

    // ========================================
    // Computed Properties
    // ========================================

    /**
     * Check if payment is paid
     *
     * @return true if status == PAID
     */
    val isPaid: Boolean
        get() = status == STATUS_PAID

    /**
     * Check if payment is pending
     *
     * @return true if status == PENDING
     */
    val isPending: Boolean
        get() = status == STATUS_PENDING

    /**
     * Check if payment is overdue
     *
     * True only if pending and payment date is in past.
     *
     * @return true if overdue pending payment
     */
    val isPastDue: Boolean
        get() = status == STATUS_PENDING && paymentDate < LocalDate.now()

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
     * Check if payment has appointment link
     *
     * @return true if appointmentId is not null
     */
    val isLinkedToAppointment: Boolean
        get() = appointmentId != null

    /**
     * Get days until payment due
     *
     * Returns negative for overdue payments, 0 for today.
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
     * Get status display text (Portuguese)
     *
     * @return "Pago" or "Pendente"
     */
    fun getStatusDisplay(): String {
        return when (status) {
            STATUS_PAID -> "Pago"
            STATUS_PENDING -> "Pendente"
            else -> status
        }
    }

    /**
     * Get payment method display text (Portuguese)
     *
     * @return Localized method (e.g., "Transferência", "Dinheiro")
     */
    fun getMethodDisplay(): String {
        return when (paymentMethod) {
            METHOD_CASH -> "Dinheiro"
            METHOD_TRANSFER -> "Transferência"
            METHOD_CARD -> "Cartão"
            METHOD_CHECK -> "Cheque"
            METHOD_PIX -> "PIX"
            else -> paymentMethod
        }
    }

    /**
     * Get payment description
     *
     * Format: "R$ 150,00 - Pago em 15/03/2024"
     *
     * @return Description string
     */
    fun getDescription(): String {
        return "$displayAmount - ${getStatusDisplay()} em ${getFormattedDate()}"
    }

    /**
     * Get payment summary for list display
     *
     * Format: "R$ 150,00 (Transferência) - Pago"
     *
     * @return Summary string
     */
    fun getSummary(): String {
        return "$displayAmount (${getMethodDisplay()}) - ${getStatusDisplay()}"
    }

    /**
     * Get status label relative to payment date
     *
     * Returns status and due information.
     *
     * @return "Pago", "Pendente", or "Vencido"
     */
    fun getStatusLabel(): String {
        return when {
            status == STATUS_PAID -> "Pago"
            isPastDue -> "Vencido"
            else -> "Pendente"
        }
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
     * - Status: PAID or PENDING
     * - Patient ID: > 0
     * - Method: Not empty
     *
     * @return true if all validations pass
     */
    fun isValid(): Boolean {
        return patientId > 0 &&
                amount > BigDecimal.ZERO &&
                amount <= BigDecimal("999999.99") &&
                (status == STATUS_PAID || status == STATUS_PENDING) &&
                paymentMethod.isNotBlank()
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
        if (status != STATUS_PAID && status != STATUS_PENDING) return "Status inválido"
        if (paymentMethod.isBlank()) return "Método de pagamento inválido"
        return null
    }

    // ========================================
    // Companion Object
    // ========================================

    companion object {
        // Status constants
        const val STATUS_PAID = "PAID"
        const val STATUS_PENDING = "PENDING"

        // Payment method constants
        const val METHOD_CASH = "CASH"
        const val METHOD_TRANSFER = "TRANSFER"
        const val METHOD_CARD = "CARD"
        const val METHOD_CHECK = "CHECK"
        const val METHOD_PIX = "PIX"

        // Constraints
        val MIN_AMOUNT = BigDecimal.ONE
        val MAX_AMOUNT = BigDecimal("999999.99")

        /**
         * Create sample payment for testing
         *
         * @param patientId Patient ID (default 1L)
         * @param offset Days offset from today (default 0 = today)
         * @param status Payment status (default PAID)
         * @return Sample payment
         */
        fun sample(
            patientId: Long = 1L,
            offset: Long = 0,
            status: String = STATUS_PAID
        ): Payment {
            return Payment(
                id = 0L,
                patientId = patientId,
                appointmentId = null,
                amount = BigDecimal("150.00"),
                status = status,
                paymentMethod = METHOD_TRANSFER,
                paymentDate = LocalDate.now().plusDays(offset)
            )
        }
    }
}

/**
 * Extension function to filter payments by status
 *
 * Useful for separating paid from pending.
 *
 * @receiver List of payments
 * @param status Payment status filter
 * @return Filtered list
 */
fun List<Payment>.filterByStatus(status: String): List<Payment> {
    return filter { it.status == status }
}

/**
 * Extension function to get all paid payments
 *
 * @receiver List of payments
 * @return Paid payments only
 */
fun List<Payment>.getPaid(): List<Payment> {
    return filter { it.isPaid }
}

/**
 * Extension function to get all pending payments
 *
 * @receiver List of payments
 * @return Pending payments only
 */
fun List<Payment>.getPending(): List<Payment> {
    return filter { it.isPending }
}

/**
 * Extension function to get overdue payments
 *
 * @receiver List of payments
 * @return Overdue payments only
 */
fun List<Payment>.getOverdue(): List<Payment> {
    return filter { it.isPastDue }
}

/**
 * Extension function to calculate total amount
 *
 * Sums amount across all payments.
 *
 * @receiver List of payments
 * @return Total amount
 */
fun List<Payment>.getTotalAmount(): BigDecimal {
    return fold(BigDecimal.ZERO) { acc, payment ->
        acc + payment.amount
    }
}

/**
 * Extension function to get total paid amount
 *
 * @receiver List of payments
 * @return Total paid amount
 */
fun List<Payment>.getTotalPaid(): BigDecimal {
    return getPaid().getTotalAmount()
}

/**
 * Extension function to get total pending amount
 *
 * @receiver List of payments
 * @return Total pending amount
 */
fun List<Payment>.getTotalPending(): BigDecimal {
    return getPending().getTotalAmount()
}

/**
 * Extension function to filter payments by date range
 *
 * @receiver List of payments
 * @param startDate Start date (inclusive)
 * @param endDate End date (inclusive)
 * @return Filtered list
 */
fun List<Payment>.filterByDateRange(startDate: LocalDate, endDate: LocalDate): List<Payment> {
    return filter { it.paymentDate >= startDate && it.paymentDate <= endDate }
}
