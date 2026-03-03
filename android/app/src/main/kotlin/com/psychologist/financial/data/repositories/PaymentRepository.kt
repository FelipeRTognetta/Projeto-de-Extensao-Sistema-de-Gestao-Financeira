package com.psychologist.financial.data.repositories

import com.psychologist.financial.data.database.AppDatabase
import com.psychologist.financial.data.database.PaymentDao
import com.psychologist.financial.data.entities.PaymentEntity
import com.psychologist.financial.domain.models.Payment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Payment repository
 *
 * Data access layer for payment management.
 * Handles mapping between PaymentEntity (database) and Payment (domain).
 * Provides high-level operations for payment access and manipulation.
 *
 * Architecture:
 * - Extends BaseRepository for transaction management
 * - Uses PaymentDao for database operations
 * - Maps Entity ↔ Domain model bidirectionally
 * - Provides reactive (Flow) and sync APIs
 * - Enforces business logic constraints
 *
 * Responsibilities:
 * - CRUD operations (Create, Read, Update, Delete)
 * - Patient-specific payment queries
 * - Status filtering (PAID/PENDING)
 * - Date range filtering
 * - Balance calculations (Amount Due Now, Total Outstanding)
 * - Entity ↔ Model mapping
 * - Transaction management
 *
 * Reactive Streams:
 * - All Flow<> methods return cold flows
 * - Automatically reused and collected by UI layers
 * - Updates trigger automatically when data changes
 *
 * Balance Calculations:
 * - Amount Due Now: SUM(amount) WHERE status = 'PAID'
 * - Total Outstanding: SUM(amount) WHERE status = 'PENDING'
 * - Total Received: SUM(amount) WHERE status = 'PAID' (same as due now)
 *
 * Usage Example:
 * ```kotlin
 * // Get all patient payments (reactive)
 * paymentRepository.getByPatientFlow(patientId).collect { payments ->
 *     updatePaymentList(payments)
 * }
 *
 * // Insert new payment
 * val paymentId = paymentRepository.insert(
 *     patientId = 1L,
 *     appointmentId = null,
 *     amount = BigDecimal("150.00"),
 *     status = "PAID",
 *     paymentMethod = "TRANSFER",
 *     paymentDate = LocalDate.now()
 * )
 *
 * // Get balance calculations
 * val amountDueNow = paymentRepository.getAmountDueNow(patientId)
 * val totalOutstanding = paymentRepository.getTotalOutstanding(patientId)
 *
 * // Get payments for date range
 * val monthPayments = paymentRepository.getByPatientAndDateRange(
 *     patientId = 1L,
 *     startDate = LocalDate.now().withDayOfMonth(1),
 *     endDate = LocalDate.now().withDayOfMonth(1).plusMonths(1).minusDays(1)
 * )
 * ```
 */
class PaymentRepository(
    database: AppDatabase,
    private val paymentDao: PaymentDao
) : BaseRepository(database) {

    // ========================================
    // Create Operations
    // ========================================

    /**
     * Insert new payment
     *
     * @param patientId Patient ID
     * @param appointmentId Optional appointment ID
     * @param amount Payment amount
     * @param status Payment status (PAID or PENDING)
     * @param paymentMethod Payment method (CASH, TRANSFER, etc.)
     * @param paymentDate Payment date
     * @return ID of inserted payment
     */
    suspend fun insert(
        patientId: Long,
        appointmentId: Long? = null,
        amount: BigDecimal,
        status: String,
        paymentMethod: String,
        paymentDate: LocalDate
    ): Long {
        val entity = PaymentEntity(
            patientId = patientId,
            appointmentId = appointmentId,
            amount = amount,
            status = status,
            paymentMethod = paymentMethod,
            paymentDate = paymentDate
        )
        return paymentDao.insert(entity)
    }

    /**
     * Insert payment entity directly
     *
     * @param entity Payment entity
     * @return ID of inserted payment
     */
    suspend fun insertEntity(entity: PaymentEntity): Long {
        return paymentDao.insert(entity)
    }

    // ========================================
    // Read Operations - Single/Count
    // ========================================

    /**
     * Get payment by ID
     *
     * @param id Payment ID
     * @return Payment or null if not found
     */
    suspend fun getById(id: Long): Payment? {
        return paymentDao.getById(id)?.toDomain()
    }

    /**
     * Check if payment exists
     *
     * @param id Payment ID
     * @return true if payment exists
     */
    suspend fun existsById(id: Long): Boolean {
        return paymentDao.existsById(id)
    }

    /**
     * Get total count of payments
     *
     * @return Number of payments
     */
    suspend fun count(): Int {
        return paymentDao.count()
    }

    /**
     * Get count of payments for patient
     *
     * @param patientId Patient ID
     * @return Number of payments
     */
    suspend fun countByPatient(patientId: Long): Int {
        return paymentDao.countByPatient(patientId)
    }

    /**
     * Get count of payments by status
     *
     * @param status Payment status
     * @return Number of payments
     */
    suspend fun countByStatus(status: String): Int {
        return paymentDao.countByStatus(status)
    }

    /**
     * Get count of payments for patient by status
     *
     * @param patientId Patient ID
     * @param status Payment status
     * @return Number of payments
     */
    suspend fun countByPatientAndStatus(patientId: Long, status: String): Int {
        return paymentDao.countByPatientAndStatus(patientId, status)
    }

    /**
     * Get count of payments in date range
     *
     * @param startDate Start date
     * @param endDate End date
     * @return Count
     */
    suspend fun countByDateRange(startDate: LocalDate, endDate: LocalDate): Int {
        return paymentDao.countByDateRange(startDate, endDate)
    }

    // ========================================
    // Read Operations - Lists
    // ========================================

    /**
     * Get all payments
     *
     * @return All payments sorted by payment_date DESC
     */
    suspend fun getAll(): List<Payment> {
        return paymentDao.getAll().map { it.toDomain() }
    }

    /**
     * Get all payments as Flow (reactive)
     *
     * @return Flow of payment list
     */
    fun getAllFlow(): Flow<List<Payment>> {
        return paymentDao.getAllFlow().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Get payments for patient
     *
     * @param patientId Patient ID
     * @return Patient's payments sorted by payment_date DESC
     */
    suspend fun getByPatient(patientId: Long): List<Payment> {
        return paymentDao.getByPatient(patientId).map { it.toDomain() }
    }

    /**
     * Get payments for patient as Flow (reactive)
     *
     * @param patientId Patient ID
     * @return Flow of patient's payments
     */
    fun getByPatientFlow(patientId: Long): Flow<List<Payment>> {
        return paymentDao.getByPatientFlow(patientId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Get payments by status
     *
     * @param status Payment status
     * @return Payments with status
     */
    suspend fun getByStatus(status: String): List<Payment> {
        return paymentDao.getByStatus(status).map { it.toDomain() }
    }

    /**
     * Get payments by status as Flow (reactive)
     *
     * @param status Payment status
     * @return Flow of payments with status
     */
    fun getByStatusFlow(status: String): Flow<List<Payment>> {
        return paymentDao.getByStatusFlow(status).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Get payments for patient by status
     *
     * @param patientId Patient ID
     * @param status Payment status
     * @return Patient's payments with status
     */
    suspend fun getByPatientAndStatus(patientId: Long, status: String): List<Payment> {
        return paymentDao.getByPatientAndStatus(patientId, status).map { it.toDomain() }
    }

    /**
     * Get payments for patient by status as Flow (reactive)
     *
     * @param patientId Patient ID
     * @param status Payment status
     * @return Flow of patient's payments with status
     */
    fun getByPatientAndStatusFlow(patientId: Long, status: String): Flow<List<Payment>> {
        return paymentDao.getByPatientAndStatusFlow(patientId, status).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Get payments in date range
     *
     * @param startDate Start date
     * @param endDate End date
     * @return Payments in range
     */
    suspend fun getByDateRange(
        startDate: LocalDate,
        endDate: LocalDate
    ): List<Payment> {
        return paymentDao.getByDateRange(startDate, endDate).map { it.toDomain() }
    }

    /**
     * Get payments for patient in date range
     *
     * @param patientId Patient ID
     * @param startDate Start date
     * @param endDate End date
     * @return Payments matching both filters
     */
    suspend fun getByPatientAndDateRange(
        patientId: Long,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<Payment> {
        return paymentDao.getByPatientAndDateRange(patientId, startDate, endDate)
            .map { it.toDomain() }
    }

    /**
     * Get payments for patient by status and date range
     *
     * @param patientId Patient ID
     * @param status Payment status
     * @param startDate Start date
     * @param endDate End date
     * @return Payments matching all criteria
     */
    suspend fun getByPatientStatusAndDateRange(
        patientId: Long,
        status: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<Payment> {
        return paymentDao.getByPatientStatusAndDateRange(patientId, status, startDate, endDate)
            .map { it.toDomain() }
    }

    /**
     * Get payments linked to appointment
     *
     * @param appointmentId Appointment ID
     * @return Payments for appointment
     */
    suspend fun getByAppointment(appointmentId: Long): List<Payment> {
        return paymentDao.getByAppointment(appointmentId).map { it.toDomain() }
    }

    /**
     * Get unlinked payments for patient
     *
     * @param patientId Patient ID
     * @return Payments without appointment link
     */
    suspend fun getUnlinkedByPatient(patientId: Long): List<Payment> {
        return paymentDao.getUnlinkedByPatient(patientId).map { it.toDomain() }
    }

    /**
     * Get overdue pending payments for patient
     *
     * @param patientId Patient ID
     * @param currentDate Current date for comparison
     * @return Overdue payments
     */
    suspend fun getPastDueByPatient(
        patientId: Long,
        currentDate: LocalDate = LocalDate.now()
    ): List<Payment> {
        return paymentDao.getPastDueByPatient(patientId, currentDate).map { it.toDomain() }
    }

    /**
     * Get recent payments for patient
     *
     * @param patientId Patient ID
     * @param limit Number of recent payments
     * @return Recent payments
     */
    suspend fun getRecentByPatient(patientId: Long, limit: Int = 10): List<Payment> {
        return paymentDao.getRecentByPatient(patientId, limit).map { it.toDomain() }
    }

    // ========================================
    // Update Operations
    // ========================================

    /**
     * Update payment
     *
     * @param payment Updated payment
     */
    suspend fun update(payment: Payment) {
        val entity = payment.toEntity()
        paymentDao.update(entity)
    }

    /**
     * Update payment entity
     *
     * @param entity Updated entity
     */
    suspend fun updateEntity(entity: PaymentEntity) {
        paymentDao.update(entity)
    }

    /**
     * Mark payment as paid
     *
     * @param paymentId Payment ID
     */
    suspend fun markAsPaid(paymentId: Long) {
        paymentDao.markAsPaid(paymentId)
    }

    /**
     * Mark payment as pending
     *
     * @param paymentId Payment ID
     */
    suspend fun markAsPending(paymentId: Long) {
        paymentDao.markAsPending(paymentId)
    }

    /**
     * Link payment to appointment
     *
     * @param paymentId Payment ID
     * @param appointmentId Appointment ID
     */
    suspend fun linkToAppointment(paymentId: Long, appointmentId: Long) {
        paymentDao.linkToAppointment(paymentId, appointmentId)
    }

    /**
     * Unlink payment from appointment
     *
     * @param paymentId Payment ID
     */
    suspend fun unlinkFromAppointment(paymentId: Long) {
        paymentDao.unlinkFromAppointment(paymentId)
    }

    // ========================================
    // Delete Operations
    // ========================================

    /**
     * Delete payment
     *
     * @param payment Payment to delete
     */
    suspend fun delete(payment: Payment) {
        val entity = payment.toEntity()
        paymentDao.delete(entity)
    }

    /**
     * Delete payment by ID
     *
     * @param id Payment ID
     */
    suspend fun deleteById(id: Long) {
        paymentDao.deleteById(id)
    }

    /**
     * Delete all payments for patient
     *
     * @param patientId Patient ID
     */
    suspend fun deleteByPatient(patientId: Long) {
        paymentDao.deleteByPatient(patientId)
    }

    /**
     * Delete all payments
     */
    suspend fun deleteAll() {
        val all = paymentDao.getAll()
        if (all.isNotEmpty()) {
            paymentDao.deleteAll(all)
        }
    }

    // ========================================
    // Balance Calculation Queries
    // ========================================

    /**
     * Get total amount paid
     *
     * Sum of all PAID payments for patient.
     *
     * @param patientId Patient ID
     * @return Total paid amount
     */
    suspend fun getTotalAmountPaid(patientId: Long): BigDecimal {
        return paymentDao.getTotalAmountPaid(patientId)
    }

    /**
     * Get total amount paid as Flow (reactive)
     *
     * @param patientId Patient ID
     * @return Flow of total paid amount
     */
    fun getTotalAmountPaidFlow(patientId: Long): Flow<BigDecimal> {
        return paymentDao.getTotalAmountPaidFlow(patientId)
    }

    /**
     * Get amount due now
     *
     * Sum of all PAID payments (accounting perspective).
     *
     * @param patientId Patient ID
     * @return Amount due now
     */
    suspend fun getAmountDueNow(patientId: Long): BigDecimal {
        return paymentDao.getAmountDueNow(patientId)
    }

    /**
     * Get total outstanding
     *
     * Sum of all PENDING payments.
     *
     * @param patientId Patient ID
     * @return Total outstanding amount
     */
    suspend fun getTotalOutstanding(patientId: Long): BigDecimal {
        return paymentDao.getTotalOutstanding(patientId)
    }

    /**
     * Get total outstanding as Flow (reactive)
     *
     * @param patientId Patient ID
     * @return Flow of total outstanding amount
     */
    fun getTotalOutstandingFlow(patientId: Long): Flow<BigDecimal> {
        return paymentDao.getTotalOutstandingFlow(patientId)
    }

    /**
     * Get total amount by payment method
     *
     * @param patientId Patient ID
     * @param method Payment method
     * @return Total amount for method
     */
    suspend fun getTotalByMethod(patientId: Long, method: String): BigDecimal {
        return paymentDao.getTotalByMethod(patientId, method)
    }

    /**
     * Get total for date range
     *
     * @param patientId Patient ID
     * @param startDate Start date
     * @param endDate End date
     * @return Total amount in range
     */
    suspend fun getTotalByDateRange(
        patientId: Long,
        startDate: LocalDate,
        endDate: LocalDate
    ): BigDecimal {
        return paymentDao.getTotalByDateRange(patientId, startDate, endDate)
    }

    /**
     * Get average payment amount
     *
     * @param patientId Patient ID
     * @return Average payment amount
     */
    suspend fun getAveragePaymentAmount(patientId: Long): BigDecimal {
        return paymentDao.getAveragePaymentAmount(patientId)
    }

    /**
     * Get highest payment amount
     *
     * @param patientId Patient ID
     * @return Highest payment amount
     */
    suspend fun getMaxPaymentAmount(patientId: Long): BigDecimal {
        return paymentDao.getMaxPaymentAmount(patientId)
    }

    /**
     * Get lowest payment amount
     *
     * @param patientId Patient ID
     * @return Lowest payment amount
     */
    suspend fun getMinPaymentAmount(patientId: Long): BigDecimal {
        return paymentDao.getMinPaymentAmount(patientId)
    }

    // ========================================
    // Mapping Functions
    // ========================================

    /**
     * Convert PaymentEntity to domain Payment
     *
     * @receiver Entity
     * @return Domain model
     */
    private fun PaymentEntity.toDomain(): Payment {
        return Payment(
            id = id,
            patientId = patientId,
            appointmentId = appointmentId,
            amount = amount,
            status = status,
            paymentMethod = paymentMethod,
            paymentDate = paymentDate,
            createdDate = createdDate
        )
    }

    /**
     * Convert domain Payment to PaymentEntity
     *
     * @receiver Domain model
     * @return Entity
     */
    private fun Payment.toEntity(): PaymentEntity {
        return PaymentEntity(
            id = id,
            patientId = patientId,
            appointmentId = appointmentId,
            amount = amount,
            status = status,
            paymentMethod = paymentMethod,
            paymentDate = paymentDate,
            createdDate = createdDate
        )
    }
}
