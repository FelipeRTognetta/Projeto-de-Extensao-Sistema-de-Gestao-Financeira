package com.psychologist.financial.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.psychologist.financial.data.entities.PaymentEntity
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Room DAO (Data Access Object) for Payment operations
 *
 * Provides database operations for PaymentEntity:
 * - CRUD: Create (insert), Read (query), Update, Delete
 * - Queries: By patient, status, date range, balance calculations
 * - Reactive: Flow<> return types for observable data
 * - Transactions: Multi-operation atomic updates
 *
 * All queries run on IO thread automatically (suspend functions).
 *
 * Indexing Strategy:
 * - patient_id: Fast patient payment lookups
 * - (patient_id, status): Fast balance calculation queries
 * - (patient_id, payment_date DESC): Fast payment history queries
 * - status: Fast payment status filtering
 * - created_date DESC: Fast recent payment queries
 *
 * Balance Calculations:
 * - Amount Due Now: SUM(amount) WHERE status = 'PAID'
 * - Total Outstanding: SUM(amount) WHERE status = 'PENDING'
 * - Total Received: SUM(amount) WHERE status = 'PAID'
 *
 * Usage Example:
 * ```kotlin
 * // Insert payment
 * val paymentId = paymentDao.insert(paymentEntity)
 *
 * // Get all payments for patient (reactive)
 * paymentDao.getByPatientFlow(patientId).collect { payments ->
 *     updateUI(payments)
 * }
 *
 * // Get balance for patient
 * val amountDueNow = paymentDao.getAmountDueNow(patientId)
 * val totalOutstanding = paymentDao.getTotalOutstanding(patientId)
 *
 * // Get payments in date range
 * val rangePayments = paymentDao.getByPatientAndDateRange(
 *     patientId = 1L,
 *     startDate = LocalDate.now().minusMonths(1),
 *     endDate = LocalDate.now()
 * )
 *
 * // Update payment
 * paymentDao.update(updatedEntity)
 *
 * // Delete payment
 * paymentDao.delete(paymentEntity)
 * ```
 */
@Dao
interface PaymentDao {

    // ========================================
    // Create Operations
    // ========================================

    /**
     * Insert new payment
     *
     * @param payment Payment entity to insert
     * @return ID of inserted payment
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(payment: PaymentEntity): Long

    /**
     * Insert multiple payments (batch)
     *
     * @param payments List of payments to insert
     * @return List of inserted IDs
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(payments: List<PaymentEntity>): List<Long>

    // ========================================
    // Read Operations - Single / Count
    // ========================================

    /**
     * Get payment by ID
     *
     * @param id Payment ID
     * @return Payment or null if not found
     */
    @Query("SELECT * FROM payments WHERE id = :id")
    suspend fun getById(id: Long): PaymentEntity?

    /**
     * Check if payment exists
     *
     * @param id Payment ID
     * @return true if payment exists
     */
    @Query("SELECT EXISTS(SELECT 1 FROM payments WHERE id = :id)")
    suspend fun existsById(id: Long): Boolean

    /**
     * Get total count of payments
     *
     * @return Number of payments
     */
    @Query("SELECT COUNT(*) FROM payments")
    suspend fun count(): Int

    /**
     * Get count of payments for patient
     *
     * @param patientId Patient ID
     * @return Number of payments for patient
     */
    @Query("SELECT COUNT(*) FROM payments WHERE patient_id = :patientId")
    suspend fun countByPatient(patientId: Long): Int

    /**
     * Get count of payments by status
     *
     * @param status Payment status (PAID or PENDING)
     * @return Number of payments with status
     */
    @Query("SELECT COUNT(*) FROM payments WHERE status = :status")
    suspend fun countByStatus(status: String): Int

    /**
     * Get count of payments for patient by status
     *
     * @param patientId Patient ID
     * @param status Payment status
     * @return Number of payments matching both criteria
     */
    @Query("""
        SELECT COUNT(*) FROM payments
        WHERE patient_id = :patientId AND status = :status
    """)
    suspend fun countByPatientAndStatus(patientId: Long, status: String): Int

    /**
     * Get count of payments in date range
     *
     * @param startDate Start date (inclusive)
     * @param endDate End date (inclusive)
     * @return Number of payments in range
     */
    @Query("""
        SELECT COUNT(*) FROM payments
        WHERE payment_date >= :startDate AND payment_date <= :endDate
    """)
    suspend fun countByDateRange(startDate: LocalDate, endDate: LocalDate): Int

    // ========================================
    // Read Operations - Lists
    // ========================================

    /**
     * Get all payments (most recent first)
     *
     * @return All payments sorted by payment_date DESC
     */
    @Query("""
        SELECT * FROM payments
        ORDER BY payment_date DESC, created_date DESC
    """)
    suspend fun getAll(): List<PaymentEntity>

    /**
     * Get all payments as Flow (reactive)
     *
     * Updates UI automatically when data changes.
     *
     * @return Flow of payment list
     */
    @Query("""
        SELECT * FROM payments
        ORDER BY payment_date DESC, created_date DESC
    """)
    fun getAllFlow(): Flow<List<PaymentEntity>>

    /**
     * Get payments for specific patient
     *
     * @param patientId Patient ID
     * @return Patient's payments sorted by payment_date DESC (most recent first)
     */
    @Query("""
        SELECT * FROM payments
        WHERE patient_id = :patientId
        ORDER BY payment_date DESC, created_date DESC
    """)
    suspend fun getByPatient(patientId: Long): List<PaymentEntity>

    /**
     * Get payments for patient as Flow (reactive)
     *
     * Updates automatically when patient's payments change.
     *
     * @param patientId Patient ID
     * @return Flow of patient's payments
     */
    @Query("""
        SELECT * FROM payments
        WHERE patient_id = :patientId
        ORDER BY payment_date DESC, created_date DESC
    """)
    fun getByPatientFlow(patientId: Long): Flow<List<PaymentEntity>>

    /**
     * Get payments by status
     *
     * @param status Payment status (PAID or PENDING)
     * @return Payments with status, sorted by payment_date DESC
     */
    @Query("""
        SELECT * FROM payments
        WHERE status = :status
        ORDER BY payment_date DESC, created_date DESC
    """)
    suspend fun getByStatus(status: String): List<PaymentEntity>

    /**
     * Get payments by status as Flow (reactive)
     *
     * @param status Payment status
     * @return Flow of payments with status
     */
    @Query("""
        SELECT * FROM payments
        WHERE status = :status
        ORDER BY payment_date DESC, created_date DESC
    """)
    fun getByStatusFlow(status: String): Flow<List<PaymentEntity>>

    /**
     * Get payments for patient by status
     *
     * @param patientId Patient ID
     * @param status Payment status
     * @return Patient's payments with status, sorted by payment_date DESC
     */
    @Query("""
        SELECT * FROM payments
        WHERE patient_id = :patientId AND status = :status
        ORDER BY payment_date DESC, created_date DESC
    """)
    suspend fun getByPatientAndStatus(patientId: Long, status: String): List<PaymentEntity>

    /**
     * Get payments for patient by status as Flow (reactive)
     *
     * @param patientId Patient ID
     * @param status Payment status
     * @return Flow of patient's payments with status
     */
    @Query("""
        SELECT * FROM payments
        WHERE patient_id = :patientId AND status = :status
        ORDER BY payment_date DESC, created_date DESC
    """)
    fun getByPatientAndStatusFlow(patientId: Long, status: String): Flow<List<PaymentEntity>>

    /**
     * Get payments in date range
     *
     * @param startDate Start date (inclusive)
     * @param endDate End date (inclusive)
     * @return Payments in range sorted by payment_date DESC
     */
    @Query("""
        SELECT * FROM payments
        WHERE payment_date >= :startDate AND payment_date <= :endDate
        ORDER BY payment_date DESC, created_date DESC
    """)
    suspend fun getByDateRange(startDate: LocalDate, endDate: LocalDate): List<PaymentEntity>

    /**
     * Get payments for patient in date range
     *
     * @param patientId Patient ID
     * @param startDate Start date (inclusive)
     * @param endDate End date (inclusive)
     * @return Patient's payments in range, sorted by payment_date DESC
     */
    @Query("""
        SELECT * FROM payments
        WHERE patient_id = :patientId
        AND payment_date >= :startDate
        AND payment_date <= :endDate
        ORDER BY payment_date DESC, created_date DESC
    """)
    suspend fun getByPatientAndDateRange(
        patientId: Long,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<PaymentEntity>

    /**
     * Get payments for patient by status and date range
     *
     * @param patientId Patient ID
     * @param status Payment status
     * @param startDate Start date (inclusive)
     * @param endDate End date (inclusive)
     * @return Patient's payments matching all criteria, sorted by payment_date DESC
     */
    @Query("""
        SELECT * FROM payments
        WHERE patient_id = :patientId
        AND status = :status
        AND payment_date >= :startDate
        AND payment_date <= :endDate
        ORDER BY payment_date DESC, created_date DESC
    """)
    suspend fun getByPatientStatusAndDateRange(
        patientId: Long,
        status: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<PaymentEntity>

    /**
     * Get payments linked to specific appointment
     *
     * @param appointmentId Appointment ID
     * @return Payments for appointment
     */
    @Query("""
        SELECT * FROM payments
        WHERE appointment_id = :appointmentId
        ORDER BY payment_date DESC, created_date DESC
    """)
    suspend fun getByAppointment(appointmentId: Long): List<PaymentEntity>

    /**
     * Get unlinked payments for patient
     *
     * @param patientId Patient ID
     * @return Payments without appointment link
     */
    @Query("""
        SELECT * FROM payments
        WHERE patient_id = :patientId AND appointment_id IS NULL
        ORDER BY payment_date DESC, created_date DESC
    """)
    suspend fun getUnlinkedByPatient(patientId: Long): List<PaymentEntity>

    /**
     * Get past due payments for patient (overdue pending payments)
     *
     * @param patientId Patient ID
     * @param currentDate Current date for comparison
     * @return Patient's overdue payments
     */
    @Query("""
        SELECT * FROM payments
        WHERE patient_id = :patientId
        AND status = 'PENDING'
        AND payment_date < :currentDate
        ORDER BY payment_date DESC
    """)
    suspend fun getPastDueByPatient(patientId: Long, currentDate: LocalDate): List<PaymentEntity>

    /**
     * Get recent payments for patient (most recently created)
     *
     * @param patientId Patient ID
     * @param limit Number of recent payments to return
     * @return Most recently created payments for patient
     */
    @Query("""
        SELECT * FROM payments
        WHERE patient_id = :patientId
        ORDER BY created_date DESC
        LIMIT :limit
    """)
    suspend fun getRecentByPatient(patientId: Long, limit: Int = 10): List<PaymentEntity>

    // ========================================
    // Balance Calculation Queries
    // ========================================

    /**
     * Get total amount paid (sum of all PAID payments)
     *
     * @param patientId Patient ID
     * @return Total amount received from patient
     */
    @Query("""
        SELECT COALESCE(SUM(amount), 0) FROM payments
        WHERE patient_id = :patientId AND status = 'PAID'
    """)
    suspend fun getTotalAmountPaid(patientId: Long): BigDecimal

    /**
     * Get total amount paid as Flow (reactive)
     *
     * @param patientId Patient ID
     * @return Flow of total amount paid
     */
    @Query("""
        SELECT COALESCE(SUM(amount), 0) FROM payments
        WHERE patient_id = :patientId AND status = 'PAID'
    """)
    fun getTotalAmountPaidFlow(patientId: Long): Flow<BigDecimal>

    /**
     * Get amount due now (sum of all PAID payments - accounting view)
     *
     * @param patientId Patient ID
     * @return Amount due now
     */
    @Query("""
        SELECT COALESCE(SUM(amount), 0) FROM payments
        WHERE patient_id = :patientId AND status = 'PAID'
    """)
    suspend fun getAmountDueNow(patientId: Long): BigDecimal

    /**
     * Get total outstanding (sum of all PENDING payments)
     *
     * @param patientId Patient ID
     * @return Total outstanding amount
     */
    @Query("""
        SELECT COALESCE(SUM(amount), 0) FROM payments
        WHERE patient_id = :patientId AND status = 'PENDING'
    """)
    suspend fun getTotalOutstanding(patientId: Long): BigDecimal

    /**
     * Get total outstanding as Flow (reactive)
     *
     * @param patientId Patient ID
     * @return Flow of total outstanding amount
     */
    @Query("""
        SELECT COALESCE(SUM(amount), 0) FROM payments
        WHERE patient_id = :patientId AND status = 'PENDING'
    """)
    fun getTotalOutstandingFlow(patientId: Long): Flow<BigDecimal>

    /**
     * Get amount by payment method
     *
     * @param patientId Patient ID
     * @param method Payment method (CASH, TRANSFER, etc.)
     * @return Total amount for payment method
     */
    @Query("""
        SELECT COALESCE(SUM(amount), 0) FROM payments
        WHERE patient_id = :patientId AND payment_method = :method
    """)
    suspend fun getTotalByMethod(patientId: Long, method: String): BigDecimal

    /**
     * Get total for date range
     *
     * @param patientId Patient ID
     * @param startDate Start date (inclusive)
     * @param endDate End date (inclusive)
     * @return Total amount in date range
     */
    @Query("""
        SELECT COALESCE(SUM(amount), 0) FROM payments
        WHERE patient_id = :patientId
        AND payment_date >= :startDate
        AND payment_date <= :endDate
    """)
    suspend fun getTotalByDateRange(
        patientId: Long,
        startDate: LocalDate,
        endDate: LocalDate
    ): BigDecimal

    /**
     * Get average payment amount for patient
     *
     * @param patientId Patient ID
     * @return Average payment amount
     */
    @Query("""
        SELECT COALESCE(AVG(amount), 0) FROM payments
        WHERE patient_id = :patientId
    """)
    suspend fun getAveragePaymentAmount(patientId: Long): BigDecimal

    /**
     * Get highest payment amount for patient
     *
     * @param patientId Patient ID
     * @return Highest payment amount
     */
    @Query("""
        SELECT COALESCE(MAX(amount), 0) FROM payments
        WHERE patient_id = :patientId
    """)
    suspend fun getMaxPaymentAmount(patientId: Long): BigDecimal

    /**
     * Get lowest payment amount for patient
     *
     * @param patientId Patient ID
     * @return Lowest payment amount
     */
    @Query("""
        SELECT COALESCE(MIN(amount), 0) FROM payments
        WHERE patient_id = :patientId AND amount > 0
    """)
    suspend fun getMinPaymentAmount(patientId: Long): BigDecimal

    // ========================================
    // Global Aggregation Queries (all patients)
    // ========================================

    /**
     * Get total amount by status (all patients)
     *
     * @param status Payment status (PAID or PENDING)
     * @return Total amount with status
     */
    @Query("""
        SELECT COALESCE(SUM(amount), 0) FROM payments
        WHERE status = :status
    """)
    suspend fun getSumByStatus(status: String): BigDecimal

    /**
     * Get total amount by status as Flow (reactive, all patients)
     *
     * @param status Payment status
     * @return Flow of total amount
     */
    @Query("""
        SELECT COALESCE(SUM(amount), 0) FROM payments
        WHERE status = :status
    """)
    fun getSumByStatusFlow(status: String): Flow<BigDecimal>

    /**
     * Get total amount by status and date range (all patients)
     *
     * @param status Payment status
     * @param startDate Start date (inclusive)
     * @param endDate End date (inclusive)
     * @return Total amount matching criteria
     */
    @Query("""
        SELECT COALESCE(SUM(amount), 0) FROM payments
        WHERE status = :status
        AND payment_date >= :startDate
        AND payment_date <= :endDate
    """)
    suspend fun getSumByStatusAndDateRange(
        status: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): BigDecimal

    /**
     * Get total amount by status and date range as Flow (reactive, all patients)
     *
     * @param status Payment status
     * @param startDate Start date (inclusive)
     * @param endDate End date (inclusive)
     * @return Flow of total amount
     */
    @Query("""
        SELECT COALESCE(SUM(amount), 0) FROM payments
        WHERE status = :status
        AND payment_date >= :startDate
        AND payment_date <= :endDate
    """)
    fun getSumByStatusAndDateRangeFlow(
        status: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<BigDecimal>

    /**
     * Get average amount by status (all patients)
     *
     * @param status Payment status
     * @return Average amount with status
     */
    @Query("""
        SELECT COALESCE(AVG(amount), 0) FROM payments
        WHERE status = :status
    """)
    suspend fun getAverageByStatus(status: String): BigDecimal

    /**
     * Get average amount by status and date range (all patients)
     *
     * @param status Payment status
     * @param startDate Start date (inclusive)
     * @param endDate End date (inclusive)
     * @return Average amount matching criteria
     */
    @Query("""
        SELECT COALESCE(AVG(amount), 0) FROM payments
        WHERE status = :status
        AND payment_date >= :startDate
        AND payment_date <= :endDate
    """)
    suspend fun getAverageByStatusAndDateRange(
        status: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): BigDecimal

    /**
     * Get average amount by status and date range as Flow (reactive, all patients)
     *
     * @param status Payment status
     * @param startDate Start date (inclusive)
     * @param endDate End date (inclusive)
     * @return Flow of average amount
     */
    @Query("""
        SELECT COALESCE(AVG(amount), 0) FROM payments
        WHERE status = :status
        AND payment_date >= :startDate
        AND payment_date <= :endDate
    """)
    fun getAverageByStatusAndDateRangeFlow(
        status: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<BigDecimal>

    /**
     * Get count of payments by status and date range (all patients)
     *
     * @param status Payment status
     * @param startDate Start date (inclusive)
     * @param endDate End date (inclusive)
     * @return Count of payments matching criteria
     */
    @Query("""
        SELECT COUNT(*) FROM payments
        WHERE status = :status
        AND payment_date >= :startDate
        AND payment_date <= :endDate
    """)
    suspend fun countByStatusAndDateRange(
        status: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Int

    // ========================================
    // Update Operations
    // ========================================

    /**
     * Update payment
     *
     * @param payment Updated payment entity
     */
    @Update
    suspend fun update(payment: PaymentEntity)

    /**
     * Update multiple payments (batch)
     *
     * @param payments List of updated payments
     */
    @Update
    suspend fun updateAll(payments: List<PaymentEntity>)

    /**
     * Update payment status
     *
     * @param paymentId Payment ID
     * @param status New status
     */
    @Query("""
        UPDATE payments
        SET status = :status
        WHERE id = :paymentId
    """)
    suspend fun updateStatus(paymentId: Long, status: String)

    /**
     * Mark payment as paid
     *
     * @param paymentId Payment ID
     */
    @Query("""
        UPDATE payments
        SET status = 'PAID'
        WHERE id = :paymentId
    """)
    suspend fun markAsPaid(paymentId: Long)

    /**
     * Mark payment as pending
     *
     * @param paymentId Payment ID
     */
    @Query("""
        UPDATE payments
        SET status = 'PENDING'
        WHERE id = :paymentId
    """)
    suspend fun markAsPending(paymentId: Long)

    /**
     * Link payment to appointment
     *
     * @param paymentId Payment ID
     * @param appointmentId Appointment ID
     */
    @Query("""
        UPDATE payments
        SET appointment_id = :appointmentId
        WHERE id = :paymentId
    """)
    suspend fun linkToAppointment(paymentId: Long, appointmentId: Long)

    /**
     * Unlink payment from appointment
     *
     * @param paymentId Payment ID
     */
    @Query("""
        UPDATE payments
        SET appointment_id = NULL
        WHERE id = :paymentId
    """)
    suspend fun unlinkFromAppointment(paymentId: Long)

    // ========================================
    // Delete Operations
    // ========================================

    /**
     * Delete payment
     *
     * @param payment Payment entity to delete
     */
    @Delete
    suspend fun delete(payment: PaymentEntity)

    /**
     * Delete multiple payments (batch)
     *
     * @param payments List of payments to delete
     */
    @Delete
    suspend fun deleteAll(payments: List<PaymentEntity>)

    /**
     * Delete payment by ID
     *
     * @param id Payment ID
     */
    @Query("DELETE FROM payments WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * Delete all payments for patient
     *
     * @param patientId Patient ID
     */
    @Query("DELETE FROM payments WHERE patient_id = :patientId")
    suspend fun deleteByPatient(patientId: Long)

    /**
     * Delete payments by status
     *
     * @param status Payment status
     */
    @Query("DELETE FROM payments WHERE status = :status")
    suspend fun deleteByStatus(status: String)

    /**
     * Delete payments older than date
     *
     * @param beforeDate Cutoff date (exclusive)
     */
    @Query("""
        DELETE FROM payments
        WHERE payment_date < :beforeDate
    """)
    suspend fun deleteOlderThan(beforeDate: LocalDate)
}
