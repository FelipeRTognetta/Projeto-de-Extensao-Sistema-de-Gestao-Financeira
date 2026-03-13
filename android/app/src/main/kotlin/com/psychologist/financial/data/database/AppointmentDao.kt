package com.psychologist.financial.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.psychologist.financial.data.entities.AppointmentEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Room DAO (Data Access Object) for Appointment operations
 *
 * Provides database operations for AppointmentEntity:
 * - CRUD: Create (insert), Read (query), Update, Delete
 * - Queries: By patient, date range, search, statistics
 * - Reactive: Flow<> return types for observable data
 * - Transactions: Multi-operation atomic updates
 *
 * All queries run on IO thread automatically (suspend functions).
 *
 * Indexing Strategy:
 * - patient_id: Fast patient appointment lookups
 * - (patient_id, date): Fast filtered queries by patient and date
 * - date: Fast timeline queries
 * - created_date: Fast recent appointment queries
 *
 * Usage Example:
 * ```kotlin
 * // Insert appointment
 * val appointmentId = appointmentDao.insert(appointmentEntity)
 *
 * // Get all appointments for patient (reactive)
 * appointmentDao.getByPatientFlow(patientId).collect { appointments ->
 *     updateUI(appointments)
 * }
 *
 * // Get appointments in date range
 * val rangeAppointments = appointmentDao.getByPatientAndDateRange(
 *     patientId = 1L,
 *     startDate = LocalDate.now().minusMonths(1),
 *     endDate = LocalDate.now()
 * )
 *
 * // Update appointment
 * appointmentDao.update(updatedEntity)
 *
 * // Delete appointment
 * appointmentDao.delete(appointmentEntity)
 * ```
 */
@Dao
interface AppointmentDao {

    // ========================================
    // Create Operations
    // ========================================

    /**
     * Insert new appointment
     *
     * @param appointment Appointment entity to insert
     * @return ID of inserted appointment
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(appointment: AppointmentEntity): Long

    /**
     * Insert multiple appointments (batch)
     *
     * @param appointments List of appointments to insert
     * @return List of inserted IDs
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(appointments: List<AppointmentEntity>): List<Long>

    // ========================================
    // Read Operations - Single / Count
    // ========================================

    /**
     * Get appointment by ID
     *
     * @param id Appointment ID
     * @return Appointment or null if not found
     */
    @Query("SELECT * FROM appointments WHERE id = :id")
    suspend fun getById(id: Long): AppointmentEntity?

    /**
     * Check if appointment exists
     *
     * @param id Appointment ID
     * @return true if appointment exists
     */
    @Query("SELECT EXISTS(SELECT 1 FROM appointments WHERE id = :id)")
    suspend fun existsById(id: Long): Boolean

    /**
     * Get total count of appointments
     *
     * @return Number of appointments
     */
    @Query("SELECT COUNT(*) FROM appointments")
    suspend fun count(): Int

    /**
     * Get count of appointments for patient
     *
     * @param patientId Patient ID
     * @return Number of appointments for patient
     */
    @Query("SELECT COUNT(*) FROM appointments WHERE patient_id = :patientId")
    suspend fun countByPatient(patientId: Long): Int

    /**
     * Get count of appointments in date range
     *
     * @param startDate Start date (inclusive)
     * @param endDate End date (inclusive)
     * @return Number of appointments in range
     */
    @Query("""
        SELECT COUNT(*) FROM appointments
        WHERE date >= :startDate AND date <= :endDate
    """)
    suspend fun countByDateRange(startDate: LocalDate, endDate: LocalDate): Int

    // ========================================
    // Read Operations - Lists
    // ========================================

    /**
     * Get all appointments (chronological order)
     *
     * @return All appointments sorted by date ASC, time ASC
     */
    @Query("""
        SELECT * FROM appointments
        ORDER BY date ASC, time_start ASC
    """)
    suspend fun getAll(): List<AppointmentEntity>

    /**
     * Get all appointments as Flow (reactive)
     *
     * Updates UI automatically when data changes.
     *
     * @return Flow of appointment list
     */
    @Query("""
        SELECT * FROM appointments
        ORDER BY date ASC, time_start ASC
    """)
    fun getAllFlow(): Flow<List<AppointmentEntity>>

    /**
     * Get appointments for specific patient (chronological)
     *
     * @param patientId Patient ID
     * @return Patient's appointments sorted by date DESC (newest first)
     */
    @Query("""
        SELECT * FROM appointments
        WHERE patient_id = :patientId
        ORDER BY date DESC, time_start DESC
    """)
    suspend fun getByPatient(patientId: Long): List<AppointmentEntity>

    /**
     * Get appointments for patient as Flow (reactive)
     *
     * Updates automatically when patient's appointments change.
     *
     * @param patientId Patient ID
     * @return Flow of patient's appointments
     */
    @Query("""
        SELECT * FROM appointments
        WHERE patient_id = :patientId
        ORDER BY date DESC, time_start DESC
    """)
    fun getByPatientFlow(patientId: Long): Flow<List<AppointmentEntity>>

    /**
     * Get appointments in date range (chronological)
     *
     * @param startDate Start date (inclusive)
     * @param endDate End date (inclusive)
     * @return Appointments in range sorted by date ASC
     */
    @Query("""
        SELECT * FROM appointments
        WHERE date >= :startDate AND date <= :endDate
        ORDER BY date ASC, time_start ASC
    """)
    suspend fun getByDateRange(
        startDate: LocalDate,
        endDate: LocalDate
    ): List<AppointmentEntity>

    /**
     * Get appointments for patient in date range
     *
     * Combines both filters for efficient queried results.
     *
     * @param patientId Patient ID
     * @param startDate Start date (inclusive)
     * @param endDate End date (inclusive)
     * @return Appointments matching both filters, sorted by date DESC
     */
    @Query("""
        SELECT * FROM appointments
        WHERE patient_id = :patientId
        AND date >= :startDate
        AND date <= :endDate
        ORDER BY date DESC, time_start DESC
    """)
    suspend fun getByPatientAndDateRange(
        patientId: Long,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<AppointmentEntity>

    /**
     * Get past appointments (completed sessions)
     *
     * Useful for calculating billable hours and session history.
     *
     * @param beforeDate Cutoff date (inclusive)
     * @return Appointments before cutoff, newest first
     */
    @Query("""
        SELECT * FROM appointments
        WHERE date < :beforeDate
        ORDER BY date DESC, time_start DESC
    """)
    suspend fun getPastAppointments(beforeDate: LocalDate): List<AppointmentEntity>

    /**
     * Get past appointments for patient
     *
     * @param patientId Patient ID
     * @param beforeDate Cutoff date (inclusive)
     * @return Patient's past appointments, newest first
     */
    @Query("""
        SELECT * FROM appointments
        WHERE patient_id = :patientId AND date < :beforeDate
        ORDER BY date DESC, time_start DESC
    """)
    suspend fun getPastAppointmentsByPatient(
        patientId: Long,
        beforeDate: LocalDate
    ): List<AppointmentEntity>

    /**
     * Get upcoming appointments (not yet occurred)
     *
     * @param fromDate Cutoff date (inclusive)
     * @return Appointments from cutoff onwards, chronological
     */
    @Query("""
        SELECT * FROM appointments
        WHERE date >= :fromDate
        ORDER BY date ASC, time_start ASC
    """)
    suspend fun getUpcomingAppointments(fromDate: LocalDate): List<AppointmentEntity>

    /**
     * Get upcoming appointments for patient
     *
     * @param patientId Patient ID
     * @param fromDate Cutoff date (inclusive)
     * @return Patient's upcoming appointments, chronological
     */
    @Query("""
        SELECT * FROM appointments
        WHERE patient_id = :patientId AND date >= :fromDate
        ORDER BY date ASC, time_start ASC
    """)
    suspend fun getUpcomingAppointmentsByPatient(
        patientId: Long,
        fromDate: LocalDate
    ): List<AppointmentEntity>

    /**
     * Get recent appointments (most recently created)
     *
     * Useful for "recent sessions" view.
     *
     * @param limit Number of recent appointments to return
     * @return Most recently created appointments
     */
    @Query("""
        SELECT * FROM appointments
        ORDER BY created_date DESC
        LIMIT :limit
    """)
    suspend fun getRecentAppointments(limit: Int = 10): List<AppointmentEntity>

    /**
     * Get last appointment for patient
     *
     * Useful for displaying "last session" date.
     *
     * @param patientId Patient ID
     * @return Most recent appointment for patient or null
     */
    @Query("""
        SELECT * FROM appointments
        WHERE patient_id = :patientId
        ORDER BY date DESC, time_start DESC
        LIMIT 1
    """)
    suspend fun getLastAppointmentByPatient(patientId: Long): AppointmentEntity?

    /**
     * Get last appointment date for patient
     *
     * Useful for patient profile display.
     *
     * @param patientId Patient ID
     * @return Last appointment date or null if no appointments
     */
    @Query("""
        SELECT MAX(date) FROM appointments
        WHERE patient_id = :patientId
    """)
    suspend fun getLastAppointmentDateByPatient(patientId: Long): LocalDate?

    // ========================================
    // Update Operations
    // ========================================

    /**
     * Update appointment
     *
     * @param appointment Updated appointment entity
     */
    @Update
    suspend fun update(appointment: AppointmentEntity)

    /**
     * Update multiple appointments (batch)
     *
     * @param appointments List of updated appointments
     */
    @Update
    suspend fun updateAll(appointments: List<AppointmentEntity>)

    // ========================================
    // Delete Operations
    // ========================================

    /**
     * Delete appointment
     *
     * @param appointment Appointment to delete
     */
    @Delete
    suspend fun delete(appointment: AppointmentEntity)

    /**
     * Delete appointment by ID
     *
     * @param id Appointment ID
     */
    @Query("DELETE FROM appointments WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * Delete all appointments for patient
     *
     * Useful for patient deletion (cascade delete may handle this).
     *
     * @param patientId Patient ID
     * @return Number of appointments deleted
     */
    @Query("DELETE FROM appointments WHERE patient_id = :patientId")
    suspend fun deleteByPatient(patientId: Long): Int

    /**
     * Delete all appointments
     *
     * WARNING: This deletes all appointment records!
     * Use with caution (typically for testing only).
     */
    @Query("DELETE FROM appointments")
    suspend fun deleteAll()

    // ========================================
    // Statistics Queries
    // ========================================

    /**
     * Calculate total billable hours for patient
     *
     * Sums all appointment durations and converts to hours.
     * Only counts past appointments (completed sessions).
     *
     * @param patientId Patient ID
     * @param beforeDate Cutoff date for "past" definition
     * @return Total hours as decimal (0.0 if no appointments)
     */
    @Query("""
        SELECT COALESCE(SUM(duration_minutes), 0) / 60.0
        FROM appointments
        WHERE patient_id = :patientId AND date < :beforeDate
    """)
    suspend fun getTotalBillableHours(
        patientId: Long,
        beforeDate: LocalDate = LocalDate.now()
    ): Double

    /**
     * Get appointment statistics for patient
     *
     * Returns count of appointments in different categories.
     *
     * @param patientId Patient ID
     * @return Statistics (total, past, upcoming)
     */
    suspend fun getAppointmentStatistics(patientId: Long): AppointmentStats {
        val total = countByPatient(patientId)
        val past = getPastAppointmentsByPatient(patientId, LocalDate.now()).size
        val upcoming = getUpcomingAppointmentsByPatient(patientId, LocalDate.now()).size
        val billableHours = getTotalBillableHours(patientId)

        return AppointmentStats(
            totalAppointments = total,
            pastAppointments = past,
            upcomingAppointments = upcoming,
            totalBillableHours = billableHours
        )
    }

    // ========================================
    // Existence Checks
    // ========================================

    /**
     * Check if patient has any appointments
     *
     * @param patientId Patient ID
     * @return true if patient has at least one appointment
     */
    @Query("SELECT EXISTS(SELECT 1 FROM appointments WHERE patient_id = :patientId)")
    suspend fun hasAppointments(patientId: Long): Boolean

    /**
     * Check if patient has upcoming appointments
     *
     * @param patientId Patient ID
     * @param fromDate Cutoff date
     * @return true if patient has appointments on/after date
     */
    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM appointments
            WHERE patient_id = :patientId AND date >= :fromDate
        )
    """)
    suspend fun hasUpcomingAppointments(
        patientId: Long,
        fromDate: LocalDate = LocalDate.now()
    ): Boolean

    // ========================================
    // Payment Status Queries (Junction Table)
    // ========================================

    /**
     * Get all appointments with payment status (read model)
     *
     * Derives payment status from junction table:
     * - hasPendingPayment = true if appointment is NOT in payment_appointments
     * - hasPendingPayment = false if appointment IS linked to a payment
     *
     * Uses LEFT JOIN on payment_appointments to efficiently calculate status
     * in a single query. The hasPendingPayment flag is computed via:
     * `(payment_appointments.payment_id IS NULL) as has_pending_payment`
     *
     * Results ordered by most recent appointment date first (DESC).
     *
     * @return Flow of all appointments with derived payment status
     */
    @Query("""
        SELECT
            a.*,
            (a.id NOT IN (SELECT appointment_id FROM payment_appointments)) as has_pending_payment,
            p.name as patient_name
        FROM appointments a
        JOIN patient p ON a.patient_id = p.id
        ORDER BY a.date DESC, a.time_start DESC
    """)
    fun getAllWithPaymentStatus(): Flow<List<AppointmentWithStatusResult>>

    /**
     * Get unpaid (unlinked) appointments for patient
     *
     * Returns appointments that have NO payment link in the junction table.
     * Useful for the payment form to show which appointments are available
     * for linking to a new or existing payment.
     *
     * Results ordered by most recent appointment date first (DESC).
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
    suspend fun getUnpaidByPatient(patientId: Long): List<AppointmentEntity>

    /**
     * Get all appointments for a patient with payment status.
     *
     * Derives payment status from junction table via LEFT JOIN:
     * - hasPendingPayment = true if appointment is NOT linked to a payment
     * - hasPendingPayment = false if appointment IS linked to a payment
     *
     * @param patientId Patient ID
     * @return List of appointments with derived payment status
     */
    @Query("""
        SELECT
            a.*,
            (a.id NOT IN (SELECT appointment_id FROM payment_appointments)) as has_pending_payment,
            p.name as patient_name
        FROM appointments a
        JOIN patient p ON a.patient_id = p.id
        WHERE a.patient_id = :patientId
        ORDER BY a.date DESC, a.time_start DESC
    """)
    suspend fun getByPatientWithPaymentStatus(patientId: Long): List<AppointmentWithStatusResult>

    /**
     * Get distinct patient IDs that have appointments with no payment link.
     *
     * Used to show a "Pagamento em aberto" flag on patient cards and detail screen.
     *
     * @return Flow of patient IDs with pending payments
     */
    @Query("""
        SELECT DISTINCT patient_id FROM appointments
        WHERE id NOT IN (SELECT appointment_id FROM payment_appointments)
    """)
    fun getPatientIdsWithPendingPaymentsFlow(): Flow<List<Long>>

    @Query("SELECT COUNT(*) FROM appointments WHERE id NOT IN (SELECT appointment_id FROM payment_appointments)")
    fun countUnpaidAppointmentsFlow(): Flow<Int>

    /**
     * Paginated global appointment list with optional name search and payment-status filter.
     *
     * statusFilter values: "ALL", "PENDING" (no linked payment), "PAID" (has linked payment).
     * Pass searchTerm = "%" to return all patients.
     * Ordered by date DESC for stable LIMIT/OFFSET pagination.
     */
    @Query("""
        SELECT
            a.*,
            (a.id NOT IN (SELECT appointment_id FROM payment_appointments)) AS has_pending_payment,
            p.name AS patient_name
        FROM appointments a
        JOIN patient p ON a.patient_id = p.id
        WHERE (:searchTerm = '%' OR REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(LOWER(p.name),'á','a'),'Á','a'),'à','a'),'À','a'),'â','a'),'Â','a'),'ã','a'),'Ã','a'),'é','e'),'É','e'),'ê','e'),'Ê','e'),'í','i'),'Í','i'),'ó','o'),'Ó','o'),'ô','o'),'Ô','o'),'õ','o'),'Õ','o'),'ú','u'),'Ú','u'),'ü','u'),'Ü','u'),'ç','c'),'Ç','c') LIKE :searchTerm)
        AND (
            :statusFilter = 'ALL'
            OR (:statusFilter = 'PENDING' AND a.id NOT IN (SELECT appointment_id FROM payment_appointments))
            OR (:statusFilter = 'PAID'    AND a.id IN     (SELECT appointment_id FROM payment_appointments))
        )
        ORDER BY a.date DESC, a.time_start DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getPagedWithPaymentStatus(
        searchTerm: String,
        statusFilter: String,
        offset: Int,
        limit: Int
    ): List<AppointmentWithStatusResult>

    /**
     * Paginated per-patient appointment list with payment status. Date DESC.
     */
    @Query("""
        SELECT
            a.*,
            (a.id NOT IN (SELECT appointment_id FROM payment_appointments)) AS has_pending_payment,
            p.name AS patient_name
        FROM appointments a
        JOIN patient p ON a.patient_id = p.id
        WHERE a.patient_id = :patientId
        ORDER BY a.date DESC, a.time_start DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getPagedByPatientWithPaymentStatus(
        patientId: Long,
        offset: Int,
        limit: Int
    ): List<AppointmentWithStatusResult>
}

/**
 * Appointment statistics data class
 *
 * Used for quick access to appointment metrics.
 */
data class AppointmentStats(
    val totalAppointments: Int,
    val pastAppointments: Int,
    val upcomingAppointments: Int,
    val totalBillableHours: Double
) {
    /**
     * Get average session duration in hours
     *
     * @return Average hours per session or 0.0 if no past appointments
     */
    fun getAverageSessionHours(): Double {
        return if (pastAppointments > 0) totalBillableHours / pastAppointments else 0.0
    }
}
