package com.psychologist.financial.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Payment database entity
 *
 * Represents a payment transaction for a patient's therapy services.
 * Tracks amount paid, status (PAID/PENDING), payment method, and optional appointment link.
 *
 * Database Constraints:
 * - id: Primary key (auto-increment)
 * - patient_id: Foreign key to PatientEntity, cascading delete
 * - appointment_id: Optional foreign key to AppointmentEntity (nullable)
 * - amount: Payment amount in Brazilian Real (BRL) as DECIMAL(10,2)
 * - status: Payment status (PAID or PENDING)
 * - payment_method: Payment method (CASH, TRANSFER, CARD, CHECK, etc.)
 * - payment_date: Date the payment was received or due (if PENDING)
 * - created_date: Record creation timestamp
 *
 * Indexes:
 * - patient_id (for fast patient payment queries)
 * - (patient_id, status) (for balance calculations by status)
 * - (patient_id, payment_date DESC) (for payment history queries)
 * - status (for filtering by PAID/PENDING)
 * - created_date (for recent payments)
 *
 * Relationships:
 * - belongs_to: Patient (patient_id → PatientEntity.id)
 * - belongs_to: Appointment (appointment_id → AppointmentEntity.id, optional)
 * - cascade_delete: Deleting patient deletes all payments
 *
 * Business Rules:
 * - Amount: Must be > 0, max 999,999.99
 * - Status: Only PAID or PENDING (enforced in validation)
 * - Prevent payment creation for Inactive patients (enforced in use case)
 * - Payment date: Can be past, present, or future
 *
 * Balance Calculations:
 * - Amount Due Now: SUM(amount) WHERE status = 'PAID'
 * - Total Outstanding: SUM(amount) WHERE status = 'PENDING'
 * - Total Received: SUM(amount) WHERE status = 'PAID' (same as due now for paid)
 *
 * Sorting:
 * - Chronological: payment_date DESC (most recent first)
 * - By Patient: patient_id ASC, payment_date DESC
 * - By Status: status ASC, payment_date DESC
 *
 * Example:
 * ```kotlin
 * val entity = PaymentEntity(
 *     patientId = 1L,
 *     appointmentId = null,  // Optional link
 *     amount = BigDecimal("150.00"),
 *     status = "PAID",
 *     paymentMethod = "TRANSFER",
 *     paymentDate = LocalDate.now()
 * )
 * ```
 */
@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(
            entity = PatientEntity::class,
            parentColumns = ["id"],
            childColumns = ["patient_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AppointmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["appointment_id"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(name = "idx_payment_patient_id", value = ["patient_id"]),
        Index(name = "idx_payment_patient_status", value = ["patient_id", "status"]),
        Index(name = "idx_payment_patient_date", value = ["patient_id", "payment_date"], orders = [androidx.room.Index.Order.ASC, androidx.room.Index.Order.DESC]),
        Index(name = "idx_payment_status", value = ["status"]),
        Index(name = "idx_payment_date", value = ["payment_date"]),
        Index(name = "idx_payment_created_date", value = ["created_date"], orders = [androidx.room.Index.Order.DESC])
    ]
)
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "patient_id")
    val patientId: Long,

    @ColumnInfo(name = "appointment_id")
    val appointmentId: Long? = null,

    @ColumnInfo(name = "amount")
    val amount: BigDecimal,

    @ColumnInfo(name = "status")
    val status: String,  // PAID or PENDING

    @ColumnInfo(name = "payment_method")
    val paymentMethod: String,  // CASH, TRANSFER, CARD, CHECK, etc.

    @ColumnInfo(name = "payment_date")
    val paymentDate: LocalDate,

    @ColumnInfo(name = "created_date")
    val createdDate: LocalDateTime = LocalDateTime.now()
) : BaseEntity(id = id, createdDate = createdDate) {

    /**
     * Check if payment is fully paid
     *
     * @return true if status is PAID
     */
    fun isPaid(): Boolean = status == STATUS_PAID

    /**
     * Check if payment is pending
     *
     * @return true if status is PENDING
     */
    fun isPending(): Boolean = status == STATUS_PENDING

    /**
     * Check if payment date is in the past
     *
     * @return true if payment date has passed
     */
    fun isPastDue(): Boolean {
        return paymentDate < LocalDate.now()
    }

    /**
     * Check if payment date is today
     *
     * @return true if payment is today
     */
    fun isToday(): Boolean {
        return paymentDate == LocalDate.now()
    }

    /**
     * Check if payment date is in the future
     *
     * @return true if payment date is upcoming
     */
    fun isFuture(): Boolean {
        return paymentDate > LocalDate.now()
    }

    /**
     * Get days until payment due
     *
     * Returns negative values for overdue payments.
     *
     * @return Number of days until payment date
     */
    fun getDaysUntilDue(): Long {
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), paymentDate)
    }

    /**
     * Get formatted amount string
     *
     * @return Amount formatted as currency (e.g., "R$ 150,00")
     */
    fun getFormattedAmount(): String {
        return "R$ ${amount.setScale(2, java.math.RoundingMode.HALF_UP)}"
    }

    /**
     * Get display status string
     *
     * @return Localized status (e.g., "Pago" or "Pendente")
     */
    fun getStatusDisplay(): String {
        return when (status) {
            STATUS_PAID -> "Pago"
            STATUS_PENDING -> "Pendente"
            else -> status
        }
    }

    /**
     * Get display method string
     *
     * @return Localized payment method (e.g., "Transferência" or "Dinheiro")
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
     * Validate payment constraints
     *
     * Checks business rules (amount > 0, valid status, etc.)
     *
     * @return true if payment meets all constraints
     */
    fun isValid(): Boolean {
        // Amount must be positive and <= 999,999.99
        if (amount <= BigDecimal.ZERO || amount > BigDecimal("999999.99")) {
            return false
        }

        // Status must be PAID or PENDING
        if (status != STATUS_PAID && status != STATUS_PENDING) {
            return false
        }

        // Patient ID must be positive
        if (patientId <= 0) {
            return false
        }

        // Payment method must not be empty
        if (paymentMethod.isBlank()) {
            return false
        }

        return true
    }

    companion object {
        const val TABLE_NAME = "payments"
        const val COLUMN_ID = "id"
        const val COLUMN_PATIENT_ID = "patient_id"
        const val COLUMN_APPOINTMENT_ID = "appointment_id"
        const val COLUMN_AMOUNT = "amount"
        const val COLUMN_STATUS = "status"
        const val COLUMN_PAYMENT_METHOD = "payment_method"
        const val COLUMN_PAYMENT_DATE = "payment_date"
        const val COLUMN_CREATED_DATE = "created_date"

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
        const val AMOUNT_PRECISION = 2  // Two decimal places
    }
}
