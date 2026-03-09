package com.psychologist.financial.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.psychologist.financial.data.entities.AppointmentEntity
import com.psychologist.financial.data.entities.PaymentAppointmentCrossRef
import com.psychologist.financial.data.entities.PaymentEntity
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Room DAO (Data Access Object) for Payment operations
 *
 * Provides database operations for PaymentEntity:
 * - CRUD: Create (insert), Read (query), Update, Delete
 * - Queries: By patient, date range, balance calculations
 * - Junction operations: Link/unlink payments to multiple appointments
 * - Reactive: Flow<> return types for observable data
 * - Transactions: Multi-operation atomic updates
 *
 * All queries run on IO thread automatically (suspend functions).
 *
 * Junction Table (payment_appointments):
 * - Many-to-many relationship: one payment can cover multiple appointments
 * - Composed: payment_id FK, appointment_id FK, composite PK
 * - Cascade delete on both sides
 *
 * Indexing Strategy:
 * - patient_id: Fast patient payment lookups
 * - (patient_id, payment_date DESC): Fast payment history queries
 * - payment_id (on payment_appointments): Fast appointment lookup by payment
 * - appointment_id (on payment_appointments): Fast payment lookup by appointment
 *
 * Balance Calculations:
 * - Total Amount: SUM(amount) - all payments are PAID (status field removed)
 * - Total Received: SUM(amount) for patient
 *
 * Usage Example:
 * ```kotlin
 * // Insert payment
 * val paymentId = paymentDao.insert(paymentEntity)
 *
 * // Link payment to appointment (many-to-many)
 * paymentDao.insertAppointmentLink(PaymentAppointmentCrossRef(paymentId, appointmentId))
 *
 * // Get all payments for patient with linked appointments (reactive)
 * paymentDao.getByPatientWithAppointments(patientId).collect { paymentsWithAppointments ->
 *     updateUI(paymentsWithAppointments)
 * }
 *
 * // Get unlinked (unpaid) appointments for patient
 * val unpaidAppointments = paymentDao.getUnpaidAppointmentsByPatient(patientId)
 *
 * // Update payment
 * paymentDao.update(updatedEntity)
 *
 * // Delete payment (automatically deletes junction rows via CASCADE)
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

    @Query("""
        SELECT COUNT(*) FROM payments
        WHERE payment_date >= :startDate AND payment_date <= :endDate
    """)
    fun countByDateRangeFlow(startDate: LocalDate, endDate: LocalDate): Flow<Int>

    // ========================================
    // Aggregate Queries (global, no status filter — all payments are PAID)
    // ========================================

    @Query("SELECT COALESCE(SUM(amount), 0) FROM payments WHERE payment_date >= :startDate AND payment_date <= :endDate")
    suspend fun getSumByDateRange(startDate: LocalDate, endDate: LocalDate): BigDecimal

    @Query("SELECT COALESCE(SUM(amount), 0) FROM payments WHERE payment_date >= :startDate AND payment_date <= :endDate")
    fun getSumByDateRangeFlow(startDate: LocalDate, endDate: LocalDate): Flow<BigDecimal>

    @Query("SELECT COALESCE(AVG(amount), 0) FROM payments WHERE payment_date >= :startDate AND payment_date <= :endDate")
    suspend fun getAverageByDateRange(startDate: LocalDate, endDate: LocalDate): BigDecimal

    @Query("SELECT COALESCE(AVG(amount), 0) FROM payments WHERE payment_date >= :startDate AND payment_date <= :endDate")
    fun getAverageByDateRangeFlow(startDate: LocalDate, endDate: LocalDate): Flow<BigDecimal>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM payments")
    suspend fun getSum(): BigDecimal

    @Query("SELECT COALESCE(SUM(amount), 0) FROM payments")
    fun getSumFlow(): Flow<BigDecimal>

    @Query("SELECT COALESCE(AVG(amount), 0) FROM payments")
    suspend fun getAverage(): BigDecimal

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
     * Get total amount received from patient (sum of all payments)
     *
     * All payments are PAID (status field removed). Pending amounts are derived
     * from appointments without payment links in the junction table.
     *
     * @param patientId Patient ID
     * @return Total amount received from patient
     */
    @Query("""
        SELECT COALESCE(SUM(amount), 0) FROM payments
        WHERE patient_id = :patientId
    """)
    suspend fun getTotalAmountPaid(patientId: Long): BigDecimal

    /**
     * Get total amount received as Flow (reactive)
     *
     * @param patientId Patient ID
     * @return Flow of total amount received
     */
    @Query("""
        SELECT COALESCE(SUM(amount), 0) FROM payments
        WHERE patient_id = :patientId
    """)
    fun getTotalAmountPaidFlow(patientId: Long): Flow<BigDecimal>

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
     * Delete payments older than date
     *
     * @param beforeDate Cutoff date (exclusive)
     */
    @Query("""
        DELETE FROM payments
        WHERE payment_date < :beforeDate
    """)
    suspend fun deleteOlderThan(beforeDate: LocalDate)

    // ========================================
    // Junction Table Operations (Many-to-Many)
    // ========================================

    /**
     * Link payment to appointment (junction table insert)
     *
     * Creates a row in payment_appointments junction table to associate
     * one payment with one appointment. A payment can cover multiple
     * appointments via multiple junction rows.
     *
     * @param link PaymentAppointmentCrossRef with paymentId and appointmentId
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAppointmentLink(link: PaymentAppointmentCrossRef)

    /**
     * Delete all appointment links for a payment (junction table delete)
     *
     * Removes all associations between a payment and its appointments.
     * Useful when unlinking all appointments from a payment.
     *
     * @param paymentId Payment ID
     */
    @Query("DELETE FROM payment_appointments WHERE payment_id = :paymentId")
    suspend fun deleteAppointmentLinksByPayment(paymentId: Long)

    /**
     * Delete all payment-appointment cross-refs (for full backup restore).
     */
    @Query("DELETE FROM payment_appointments")
    suspend fun deleteAllCrossRefs()

    /**
     * Bulk insert payment-appointment cross-refs (for backup restore).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllCrossRefs(links: List<PaymentAppointmentCrossRef>)

    /**
     * Delete all payments (for full backup restore).
     */
    @Query("DELETE FROM payments")
    suspend fun deleteAllPayments()

    // ========================================
    // Read Models (With Related Data)
    // ========================================

    /**
     * Get all payments with their linked appointments (read model)
     *
     * Uses Room @Relation to automatically join payment data with
     * all linked appointments via the junction table. Returns a
     * PaymentWithAppointments object that includes payment entity
     * and list of associated appointment entities.
     *
     * Results ordered by most recent payment date first.
     *
     * @return Flow of all payments with their linked appointments
     */
    @Transaction
    @Query("""
        SELECT payments.*, pat.name as patient_name
        FROM payments
        JOIN patient pat ON payments.patient_id = pat.id
        ORDER BY payments.payment_date DESC, payments.created_date DESC
    """)
    fun getAllWithAppointmentsAndPatient(): Flow<List<PaymentWithAppointmentsAndPatient>>

    @Transaction
    @Query("""
        SELECT * FROM payments
        ORDER BY payment_date DESC, created_date DESC
    """)
    fun getAllWithAppointments(): Flow<List<PaymentWithAppointments>>

    /**
     * Get payments for patient with their linked appointments (read model)
     *
     * Filters payments by patient ID and loads all linked appointments
     * for each payment via the junction table.
     *
     * Results ordered by most recent payment date first.
     *
     * @param patientId Patient ID
     * @return Flow of patient's payments with linked appointments
     */
    @Transaction
    @Query("""
        SELECT * FROM payments
        WHERE patient_id = :patientId
        ORDER BY payment_date DESC, created_date DESC
    """)
    fun getByPatientWithAppointments(patientId: Long): Flow<List<PaymentWithAppointments>>

    /**
     * Get unlinked (unpaid) appointments for patient
     *
     * Returns appointments that have NO payment link in the junction table.
     * These are appointments with pending payments that need to be covered
     * by a payment record.
     *
     * Results ordered by most recent appointment date first.
     *
     * @param patientId Patient ID
     * @return List of appointments without payment links
     */
    @Query("""
        SELECT * FROM appointments
        WHERE patient_id = :patientId
        AND id NOT IN (SELECT appointment_id FROM payment_appointments)
        ORDER BY date DESC, time_start DESC
    """)
    suspend fun getUnpaidAppointmentsByPatient(patientId: Long): List<AppointmentEntity>

    /**
     * Get a single payment with its linked appointments.
     *
     * @param paymentId Payment ID
     * @return Payment with appointments or null
     */
    @Transaction
    @Query("SELECT * FROM payments WHERE id = :paymentId")
    suspend fun getByIdWithAppointments(paymentId: Long): PaymentWithAppointments?
}
