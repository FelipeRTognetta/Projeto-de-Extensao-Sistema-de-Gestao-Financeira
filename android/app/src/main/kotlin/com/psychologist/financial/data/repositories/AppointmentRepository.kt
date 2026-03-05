package com.psychologist.financial.data.repositories

import com.psychologist.financial.data.database.AppDatabase
import com.psychologist.financial.data.database.AppointmentDao
import com.psychologist.financial.data.database.AppointmentStats
import com.psychologist.financial.data.entities.AppointmentEntity
import com.psychologist.financial.domain.models.Appointment
import com.psychologist.financial.domain.models.AppointmentWithPaymentStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * Appointment repository
 *
 * Data access layer for appointment management.
 * Handles mapping between AppointmentEntity (database) and Appointment (domain).
 * Provides high-level operations for appointment access and manipulation.
 *
 * Architecture:
 * - Extends BaseRepository for transaction management
 * - Uses AppointmentDao for database operations
 * - Maps Entity ↔ Domain model bidirectionally
 * - Provides reactive (Flow) and sync APIs
 * - Enforces business logic constraints
 *
 * Responsibilities:
 * - CRUD operations (Create, Read, Update, Delete)
 * - Patient-specific appointment queries
 * - Date range filtering
 * - Billable hours calculation
 * - Timeline and statistics queries
 * - Entity ↔ Model mapping
 * - Transaction management
 *
 * Reactive Streams:
 * - All Flow<> methods return cold flows
 * - Automatically reused and collected by UI layers
 * - Updates trigger automatically when data changes
 *
 * Usage Example:
 * ```kotlin
 * // Get all patient appointments (reactive)
 * appointmentRepository.getByPatientFlow(patientId).collect { appointments ->
 *     updateAppointmentList(appointments)
 * }
 *
 * // Insert new appointment
 * val appointmentId = appointmentRepository.insert(
 *     patientId = 1L,
 *     date = LocalDate.now(),
 *     timeStart = LocalTime.of(14, 30),
 *     durationMinutes = 60,
 *     notes = "Session notes"
 * )
 *
 * // Get appointments for date range
 * val monthAppointments = appointmentRepository.getByPatientAndDateRange(
 *     patientId = 1L,
 *     startDate = LocalDate.now().withDayOfMonth(1),
 *     endDate = LocalDate.now().withDayOfMonth(1).plusMonths(1).minusDays(1)
 * )
 * ```
 */
class AppointmentRepository(
    database: AppDatabase,
    private val appointmentDao: AppointmentDao
) : BaseRepository(database) {

    // ========================================
    // Create Operations
    // ========================================

    /**
     * Insert new appointment
     *
     * @param patientId Patient ID
     * @param date Appointment date
     * @param timeStart Session start time
     * @param durationMinutes Duration in minutes
     * @param notes Optional notes
     * @return ID of inserted appointment
     */
    suspend fun insert(
        patientId: Long,
        date: java.time.LocalDate,
        timeStart: java.time.LocalTime,
        durationMinutes: Int,
        notes: String? = null
    ): Long {
        val entity = AppointmentEntity(
            patientId = patientId,
            date = date,
            timeStart = timeStart,
            durationMinutes = durationMinutes,
            notes = notes
        )
        return appointmentDao.insert(entity)
    }

    /**
     * Insert appointment entity directly
     *
     * @param entity Appointment entity
     * @return ID of inserted appointment
     */
    suspend fun insertEntity(entity: AppointmentEntity): Long {
        return appointmentDao.insert(entity)
    }

    // ========================================
    // Read Operations - Single/Count
    // ========================================

    /**
     * Get appointment by ID
     *
     * @param id Appointment ID
     * @return Appointment or null if not found
     */
    suspend fun getById(id: Long): Appointment? {
        return appointmentDao.getById(id)?.toDomain()
    }

    /**
     * Check if appointment exists
     *
     * @param id Appointment ID
     * @return true if appointment exists
     */
    suspend fun existsById(id: Long): Boolean {
        return appointmentDao.existsById(id)
    }

    /**
     * Get total count of appointments
     *
     * @return Number of appointments
     */
    suspend fun count(): Int {
        return appointmentDao.count()
    }

    /**
     * Get count of appointments for patient
     *
     * @param patientId Patient ID
     * @return Number of appointments
     */
    suspend fun countByPatient(patientId: Long): Int {
        return appointmentDao.countByPatient(patientId)
    }

    /**
     * Get count of appointments in date range
     *
     * @param startDate Start date
     * @param endDate End date
     * @return Count
     */
    suspend fun countByDateRange(startDate: LocalDate, endDate: LocalDate): Int {
        return appointmentDao.countByDateRange(startDate, endDate)
    }

    // ========================================
    // Read Operations - Lists
    // ========================================

    /**
     * Get all appointments
     *
     * @return All appointments sorted chronologically
     */
    suspend fun getAll(): List<Appointment> {
        return appointmentDao.getAll().map { it.toDomain() }
    }

    /**
     * Get all appointments as Flow (reactive)
     *
     * @return Flow of appointment list
     */
    fun getAllFlow(): Flow<List<Appointment>> {
        return appointmentDao.getAllFlow().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Get appointments for patient
     *
     * @param patientId Patient ID
     * @return Patient's appointments sorted by date DESC
     */
    suspend fun getByPatient(patientId: Long): List<Appointment> {
        return appointmentDao.getByPatient(patientId).map { it.toDomain() }
    }

    /**
     * Get appointments for patient as Flow (reactive)
     *
     * @param patientId Patient ID
     * @return Flow of patient's appointments
     */
    fun getByPatientFlow(patientId: Long): Flow<List<Appointment>> {
        return appointmentDao.getByPatientFlow(patientId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Get appointments in date range
     *
     * @param startDate Start date
     * @param endDate End date
     * @return Appointments in range
     */
    suspend fun getByDateRange(
        startDate: LocalDate,
        endDate: LocalDate
    ): List<Appointment> {
        return appointmentDao.getByDateRange(startDate, endDate).map { it.toDomain() }
    }

    /**
     * Get appointments for patient in date range
     *
     * Combines patient filter + date range for efficient queries.
     *
     * @param patientId Patient ID
     * @param startDate Start date
     * @param endDate End date
     * @return Appointments matching both filters
     */
    suspend fun getByPatientAndDateRange(
        patientId: Long,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<Appointment> {
        return appointmentDao.getByPatientAndDateRange(patientId, startDate, endDate)
            .map { it.toDomain() }
    }

    /**
     * Get past appointments (completed sessions)
     *
     * @param beforeDate Cutoff date
     * @return Past appointments
     */
    suspend fun getPastAppointments(beforeDate: LocalDate = LocalDate.now()): List<Appointment> {
        return appointmentDao.getPastAppointments(beforeDate).map { it.toDomain() }
    }

    /**
     * Get past appointments for patient
     *
     * @param patientId Patient ID
     * @param beforeDate Cutoff date
     * @return Patient's past appointments
     */
    suspend fun getPastAppointmentsByPatient(
        patientId: Long,
        beforeDate: LocalDate = LocalDate.now()
    ): List<Appointment> {
        return appointmentDao.getPastAppointmentsByPatient(patientId, beforeDate)
            .map { it.toDomain() }
    }

    /**
     * Get upcoming appointments (not yet occurred)
     *
     * @param fromDate Cutoff date
     * @return Upcoming appointments
     */
    suspend fun getUpcomingAppointments(fromDate: LocalDate = LocalDate.now()): List<Appointment> {
        return appointmentDao.getUpcomingAppointments(fromDate).map { it.toDomain() }
    }

    /**
     * Get upcoming appointments for patient
     *
     * @param patientId Patient ID
     * @param fromDate Cutoff date
     * @return Patient's upcoming appointments
     */
    suspend fun getUpcomingAppointmentsByPatient(
        patientId: Long,
        fromDate: LocalDate = LocalDate.now()
    ): List<Appointment> {
        return appointmentDao.getUpcomingAppointmentsByPatient(patientId, fromDate)
            .map { it.toDomain() }
    }

    /**
     * Get recent appointments
     *
     * @param limit Number of recent appointments
     * @return Recent appointments
     */
    suspend fun getRecentAppointments(limit: Int = 10): List<Appointment> {
        return appointmentDao.getRecentAppointments(limit).map { it.toDomain() }
    }

    /**
     * Get last appointment for patient
     *
     * @param patientId Patient ID
     * @return Last appointment or null
     */
    suspend fun getLastAppointmentByPatient(patientId: Long): Appointment? {
        return appointmentDao.getLastAppointmentByPatient(patientId)?.toDomain()
    }

    /**
     * Get last appointment date for patient
     *
     * @param patientId Patient ID
     * @return Last appointment date or null
     */
    suspend fun getLastAppointmentDateByPatient(patientId: Long): LocalDate? {
        return appointmentDao.getLastAppointmentDateByPatient(patientId)
    }

    // ========================================
    // Update Operations
    // ========================================

    /**
     * Update appointment
     *
     * @param appointment Updated appointment
     */
    suspend fun update(appointment: Appointment) {
        val entity = appointment.toEntity()
        appointmentDao.update(entity)
    }

    /**
     * Update appointment entity
     *
     * @param entity Updated entity
     */
    suspend fun updateEntity(entity: AppointmentEntity) {
        appointmentDao.update(entity)
    }

    // ========================================
    // Delete Operations
    // ========================================

    /**
     * Delete appointment
     *
     * @param appointment Appointment to delete
     */
    suspend fun delete(appointment: Appointment) {
        val entity = appointment.toEntity()
        appointmentDao.delete(entity)
    }

    /**
     * Delete appointment by ID
     *
     * @param id Appointment ID
     */
    suspend fun deleteById(id: Long) {
        appointmentDao.deleteById(id)
    }

    /**
     * Delete all appointments for patient
     *
     * @param patientId Patient ID
     * @return Number of appointments deleted
     */
    suspend fun deleteByPatient(patientId: Long): Int {
        return appointmentDao.deleteByPatient(patientId)
    }

    /**
     * Delete all appointments
     */
    suspend fun deleteAll() {
        appointmentDao.deleteAll()
    }

    // ========================================
    // Statistics Operations
    // ========================================

    /**
     * Calculate total billable hours for patient
     *
     * @param patientId Patient ID
     * @param beforeDate Cutoff date for "past" appointments
     * @return Total hours
     */
    suspend fun getTotalBillableHours(
        patientId: Long,
        beforeDate: LocalDate = LocalDate.now()
    ): Double {
        return appointmentDao.getTotalBillableHours(patientId, beforeDate)
    }

    /**
     * Get appointment statistics for patient
     *
     * @param patientId Patient ID
     * @return Statistics object with counts and hours
     */
    suspend fun getAppointmentStatistics(patientId: Long): AppointmentStats {
        return appointmentDao.getAppointmentStatistics(patientId)
    }

    // ========================================
    // Existence Checks
    // ========================================

    /**
     * Check if patient has any appointments
     *
     * @param patientId Patient ID
     * @return true if patient has appointments
     */
    suspend fun hasAppointments(patientId: Long): Boolean {
        return appointmentDao.hasAppointments(patientId)
    }

    /**
     * Check if patient has upcoming appointments
     *
     * @param patientId Patient ID
     * @param fromDate Cutoff date
     * @return true if has upcoming appointments
     */
    suspend fun hasUpcomingAppointments(
        patientId: Long,
        fromDate: LocalDate = LocalDate.now()
    ): Boolean {
        return appointmentDao.hasUpcomingAppointments(patientId, fromDate)
    }

    // ========================================
    // Payment Status Queries (Junction Table)
    // ========================================

    /**
     * Get all appointments with payment status (read model)
     *
     * Derives payment status from junction table via LEFT JOIN:
     * - hasPendingPayment = true if appointment is NOT linked to a payment
     * - hasPendingPayment = false if appointment IS linked to a payment
     *
     * Ordered by most recent appointment date first.
     *
     * @return Flow of all appointments with payment status
     */
    fun getAllWithPaymentStatus(): Flow<List<AppointmentWithPaymentStatus>> {
        return appointmentDao.getAllWithPaymentStatus().map { appointmentWithStatusList ->
            appointmentWithStatusList.map { aws ->
                AppointmentWithPaymentStatus(
                    appointment = aws.appointment.toDomain(),
                    hasPendingPayment = aws.hasPendingPayment
                )
            }
        }
    }

    /**
     * Get unpaid (unlinked) appointments for patient
     *
     * Returns appointments that have NO payment link in the junction table.
     * These are appointments with pending payment that need to be covered.
     *
     * Useful for payment form to show which appointments are available to link.
     *
     * @param patientId Patient ID
     * @return List of unpaid appointments
     */
    suspend fun getUnpaidByPatient(patientId: Long): List<Appointment> {
        return appointmentDao.getUnpaidByPatient(patientId).map { it.toDomain() }
    }

    // ========================================
    // Mapping Functions
    // ========================================

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

    /**
     * Convert domain Appointment to AppointmentEntity
     *
     * @receiver Domain model
     * @return Entity
     */
    private fun Appointment.toEntity(): AppointmentEntity {
        return AppointmentEntity(
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
