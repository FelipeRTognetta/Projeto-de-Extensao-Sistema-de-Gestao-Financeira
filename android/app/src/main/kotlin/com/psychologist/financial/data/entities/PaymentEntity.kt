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
 * Tracks amount paid and payment date. Appointment links are managed
 * via a separate junction table (payment_appointments).
 *
 * Database Constraints:
 * - id: Primary key (auto-increment)
 * - patient_id: Foreign key to PatientEntity, cascading delete
 * - amount: Payment amount in Brazilian Real (BRL) as TEXT (BigDecimal serialized)
 * - payment_date: Date the payment was received
 * - created_date: Record creation timestamp
 *
 * Indexes:
 * - patient_id (for fast patient payment queries)
 * - (patient_id, payment_date DESC) (for payment history queries)
 * - payment_date (for timeline queries)
 * - created_date (for recent payments)
 *
 * Relationships:
 * - belongs_to: Patient (patient_id → PatientEntity.id)
 * - many_to_many: Appointment (via payment_appointments junction table)
 * - cascade_delete: Deleting patient deletes all payments
 *
 * Business Rules:
 * - Amount: Must be > 0, max 999,999.99
 * - Prevent payment creation for Inactive patients (enforced in use case)
 * - Payment date: Can be past, present, or future
 * - All payments are PAID (no status field; status was removed per v2→v3 migration)
 *
 * Sorting:
 * - Chronological: payment_date DESC (most recent first)
 * - By Patient: patient_id ASC, payment_date DESC
 *
 * Example:
 * ```kotlin
 * val entity = PaymentEntity(
 *     patientId = 1L,
 *     amount = BigDecimal("150.00"),
 *     paymentDate = LocalDate.now()
 * )
 * ```
 *
 * Migration note (v2→v3):
 * - Removed field: appointmentId (replaced by payment_appointments junction table)
 * - Removed field: status (all payments are PAID; no need for status)
 * - Removed field: paymentMethod (business rule simplification)
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
        )
    ],
    indices = [
        Index(name = "idx_payment_patient_id", value = ["patient_id"]),
        Index(name = "idx_payment_patient_date", value = ["patient_id", "payment_date"], orders = [androidx.room.Index.Order.ASC, androidx.room.Index.Order.DESC]),
        Index(name = "idx_payment_date", value = ["payment_date"]),
        Index(name = "idx_payment_created_date", value = ["created_date"], orders = [androidx.room.Index.Order.DESC])
    ]
)
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    override val id: Long = 0L,

    @ColumnInfo(name = "patient_id")
    val patientId: Long,

    @ColumnInfo(name = "amount")
    val amount: BigDecimal,

    @ColumnInfo(name = "payment_date")
    val paymentDate: LocalDate,

    @ColumnInfo(name = "created_date")
    override val createdDate: LocalDateTime = LocalDateTime.now()
) : BaseEntity() {

    /**
     * Get formatted amount string
     *
     * @return Amount formatted as currency (e.g., "R$ 150,00")
     */
    fun getFormattedAmount(): String {
        return "R$ ${amount.setScale(2, java.math.RoundingMode.HALF_UP)}"
    }

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
     * Validate payment constraints
     *
     * Checks business rules (amount > 0, etc.)
     *
     * @return true if payment meets all constraints
     */
    fun isValid(): Boolean {
        // Amount must be positive and <= 999,999.99
        if (amount <= BigDecimal.ZERO || amount > BigDecimal("999999.99")) {
            return false
        }

        // Patient ID must be positive
        if (patientId <= 0) {
            return false
        }

        return true
    }

    companion object {
        const val TABLE_NAME = "payments"
        const val COLUMN_ID = "id"
        const val COLUMN_PATIENT_ID = "patient_id"
        const val COLUMN_AMOUNT = "amount"
        const val COLUMN_PAYMENT_DATE = "payment_date"
        const val COLUMN_CREATED_DATE = "created_date"

        // Constraints
        val MIN_AMOUNT = BigDecimal.ONE
        val MAX_AMOUNT = BigDecimal("999999.99")
        const val AMOUNT_PRECISION = 2  // Two decimal places
    }
}
