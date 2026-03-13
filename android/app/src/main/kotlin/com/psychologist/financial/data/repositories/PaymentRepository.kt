package com.psychologist.financial.data.repositories

import com.psychologist.financial.data.database.AppDatabase
import com.psychologist.financial.data.database.PaymentDao
import com.psychologist.financial.data.entities.AppointmentEntity
import com.psychologist.financial.data.entities.PaymentAppointmentCrossRef
import com.psychologist.financial.data.entities.PaymentEntity
import com.psychologist.financial.domain.models.Appointment
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
 * - Manages junction table for many-to-many payment-appointment relationships
 *
 * Responsibilities:
 * - CRUD operations (Create, Read, Update, Delete)
 * - Patient-specific payment queries
 * - Date range filtering
 * - Junction table operations (link/unlink appointments)
 * - Balance calculations (Total amount paid)
 * - Entity ↔ Model mapping
 * - Transaction management
 *
 * Reactive Streams:
 * - All Flow<> methods return cold flows
 * - Automatically reused and collected by UI layers
 * - Updates trigger automatically when data changes
 *
 * Many-to-Many Relationships:
 * - Junction Table: payment_appointments
 * - One payment can cover multiple appointments
 * - One appointment can only be covered by one payment (business rule)
 *
 * Migration note (v2→v3):
 * - Removed: status and paymentMethod fields
 * - Changed: appointmentId FK → many-to-many junction table
 * - All payments are implicitly PAID (status field removed)
 *
 * Usage Example:
 * ```kotlin
 * // Get all patient payments with linked appointments (reactive)
 * paymentRepository.getByPatientWithAppointments(patientId).collect { paymentsWithAppointments ->
 *     updatePaymentList(paymentsWithAppointments)
 * }
 *
 * // Insert new payment with multiple appointments
 * val paymentId = paymentRepository.insert(
 *     patientId = 1L,
 *     amount = BigDecimal("150.00"),
 *     paymentDate = LocalDate.now()
 * )
 * paymentRepository.linkAppointment(paymentId, 10L)
 * paymentRepository.linkAppointment(paymentId, 11L)
 *
 * // Or use atomic createPaymentWithAppointments
 * paymentRepository.createPaymentWithAppointments(
 *     payment = Payment(patientId = 1L, amount = BigDecimal("150.00"), ...),
 *     appointmentIds = listOf(10L, 11L)
 * )
 *
 * // Get unpaid appointments for payment form
 * val unpaidAppointments = paymentRepository.getUnpaidAppointmentsForPatient(patientId)
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
     * Insert new payment (without appointments)
     *
     * Use linkAppointment() or createPaymentWithAppointments() to add appointment links.
     *
     * @param patientId Patient ID
     * @param amount Payment amount
     * @param paymentDate Payment date
     * @return ID of inserted payment
     */
    suspend fun insert(
        patientId: Long,
        amount: BigDecimal,
        paymentDate: LocalDate
    ): Long {
        val entity = PaymentEntity(
            patientId = patientId,
            amount = amount,
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

    /**
     * Create payment with multiple appointment links (atomic operation)
     *
     * Inserts payment and all junction table rows in a single transaction.
     * Ensures data consistency between payment and its appointments.
     *
     * @param payment Payment to create
     * @param appointmentIds List of appointment IDs to link
     * @return ID of inserted payment
     */
    suspend fun createPaymentWithAppointments(
        payment: Payment,
        appointmentIds: List<Long>
    ): Long {
        val entity = payment.toEntity()
        val paymentId = paymentDao.insert(entity)

        // Link all appointments
        appointmentIds.forEach { appointmentId ->
            paymentDao.insertAppointmentLink(
                PaymentAppointmentCrossRef(paymentId, appointmentId)
            )
        }

        return paymentId
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

    suspend fun getByIdWithAppointments(id: Long): PaymentWithDetails? {
        val result = paymentDao.getByIdWithAppointments(id) ?: return null
        return PaymentWithDetails(
            payment = result.payment.toDomain(),
            appointments = result.appointments.map { it.toDomain() }
        )
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
     * Get all payments with linked appointments (read model)
     *
     * Includes the list of appointment IDs for each payment via junction table.
     * Ordered by most recent payment date first.
     *
     * @return Flow of payments with linked appointments
     */
    fun getAllWithAppointments(): Flow<List<PaymentWithDetails>> {
        return paymentDao.getAllWithAppointmentsAndPatient().map { paymentWithAppointments ->
            paymentWithAppointments.map { pa ->
                PaymentWithDetails(
                    payment = pa.payment.toDomain(),
                    appointments = pa.appointments.map { it.toDomain() },
                    patientName = pa.patientName
                )
            }
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
     * Get payments for patient with linked appointments (read model)
     *
     * Includes the list of appointments for each payment.
     * Ordered by most recent payment date first.
     *
     * @param patientId Patient ID
     * @return Flow of patient's payments with linked appointments
     */
    fun getByPatientWithAppointments(patientId: Long): Flow<List<PaymentWithDetails>> {
        return paymentDao.getByPatientWithAppointments(patientId).map { paymentWithAppointments ->
            paymentWithAppointments.map { pa ->
                PaymentWithDetails(
                    payment = pa.payment.toDomain(),
                    appointments = pa.appointments.map { it.toDomain() }
                )
            }
        }
    }

    /**
     * Paginated global payment list with optional patient name search.
     *
     * @param searchTerm "%" for no filter; "%query%" for patient name match
     * @param page Zero-based page number
     */
    suspend fun getPagedWithPatient(
        searchTerm: String,
        page: Int
    ): List<PaymentWithDetails> {
        return withRead {
            paymentDao.getPagedWithPatient(
                searchTerm = searchTerm,
                offset = page * com.psychologist.financial.utils.Constants.PAGE_SIZE,
                limit = com.psychologist.financial.utils.Constants.PAGE_SIZE
            ).map { pa ->
                PaymentWithDetails(
                    payment = pa.payment.toDomain(),
                    appointments = pa.appointments.map { it.toDomain() },
                    patientName = pa.patientName
                )
            }
        }
    }

    /**
     * Paginated per-patient payment list with linked appointments.
     *
     * @param patientId Patient ID
     * @param page Zero-based page number
     */
    suspend fun getPagedByPatient(
        patientId: Long,
        page: Int
    ): List<PaymentWithDetails> {
        return withRead {
            paymentDao.getPagedByPatient(
                patientId = patientId,
                offset = page * com.psychologist.financial.utils.Constants.PAGE_SIZE,
                limit = com.psychologist.financial.utils.Constants.PAGE_SIZE
            ).map { pa ->
                PaymentWithDetails(
                    payment = pa.payment.toDomain(),
                    appointments = pa.appointments.map { it.toDomain() }
                )
            }
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
    // Appointment Linking Operations
    // ========================================

    /**
     * Link payment to appointment (junction table insert)
     *
     * Creates an association between payment and appointment.
     * A payment can cover multiple appointments; use repeatedly to add more.
     *
     * @param paymentId Payment ID
     * @param appointmentId Appointment ID to link
     */
    suspend fun linkAppointment(paymentId: Long, appointmentId: Long) {
        paymentDao.insertAppointmentLink(
            PaymentAppointmentCrossRef(paymentId, appointmentId)
        )
    }

    /**
     * Delete all appointment links for a payment
     *
     * Removes all associations between payment and its appointments.
     *
     * @param paymentId Payment ID
     */
    suspend fun unlinkAllAppointments(paymentId: Long) {
        paymentDao.deleteAppointmentLinksByPayment(paymentId)
    }

    /**
     * Get unpaid (unlinked) appointments for patient
     *
     * Returns appointments that have no payment link in the junction table.
     * Useful for payment form to show available appointments to link.
     *
     * @param patientId Patient ID
     * @return List of unpaid appointments
     */
    suspend fun getUnpaidAppointmentsForPatient(patientId: Long): List<Appointment> {
        return paymentDao.getUnpaidAppointmentsByPatient(patientId)
            .map { it.toDomain() }
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

    // ========================================
    // Delete Operations
    // ========================================

    /**
     * Delete payment
     *
     * Cascade delete removes all junction rows (appointment links) automatically.
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
     * Get total amount paid (sum of all payments for patient)
     *
     * All payments are PAID (status field removed).
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
     * Removes status and paymentMethod fields (removed in v3 migration).
     * Does not include appointment IDs (use DAO read models for that).
     *
     * @receiver Entity
     * @return Domain model
     */
    private fun PaymentEntity.toDomain(): Payment {
        return Payment(
            id = id,
            patientId = patientId,
            amount = amount,
            paymentDate = paymentDate,
            createdDate = createdDate,
            appointmentIds = emptyList() // Use getAllWithAppointments() for appointments
        )
    }

    /**
     * Convert domain Payment to PaymentEntity
     *
     * Appointments are managed separately via junction table.
     *
     * @receiver Domain model
     * @return Entity
     */
    private fun Payment.toEntity(): PaymentEntity {
        return PaymentEntity(
            id = id,
            patientId = patientId,
            amount = amount,
            paymentDate = paymentDate,
            createdDate = createdDate
        )
    }

    /**
     * Convert AppointmentEntity to domain Appointment
     *
     * @receiver Entity
     * @return Domain model
     */
    private fun AppointmentEntity.toDomain(): Appointment {
        return Appointment(
            id = id,
            patientId = patientId,
            date = date,
            timeStart = timeStart,
            durationMinutes = durationMinutes,
            notes = notes,
            createdDate = createdDate
        )
    }
}

/**
 * Payment with linked appointments (read model)
 *
 * Combines payment data with its associated appointments loaded from junction table.
 * Used for payment list screens that need to display appointment details.
 *
 * @param payment Payment domain model
 * @param appointments List of linked appointments
 */
data class PaymentWithDetails(
    val payment: Payment,
    val appointments: List<Appointment>,
    val patientName: String = ""
)
