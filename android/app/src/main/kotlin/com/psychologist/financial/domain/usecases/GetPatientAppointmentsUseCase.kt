package com.psychologist.financial.domain.usecases

import com.psychologist.financial.data.repositories.AppointmentRepository
import com.psychologist.financial.domain.models.Appointment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * Get patient appointments use case
 *
 * Retrieves and presents appointments for a patient.
 * Provides multiple views: all, past, upcoming, by date range.
 * Supports both sync and reactive (Flow) APIs.
 *
 * Responsibilities:
 * - Retrieve appointments from repository
 * - Sort appointments chronologically
 * - Filter by status (past/upcoming)
 * - Provide reactive streams for UI binding
 * - Count and statistics
 *
 * Sorting Strategy:
 * - Default: Newest first (date DESC, time DESC)
 * - Chronological: Oldest first (date ASC, time ASC)
 * - Timeline: Grouped by date
 *
 * Usage Examples:
 * ```kotlin
 * val useCase = GetPatientAppointmentsUseCase(appointmentRepository)

 * // Get all appointments (sync)
 * val appointments = useCase.execute(patientId = 1L)
 *
 * // Get appointments as reactive stream
 * useCase.executeFlow(patientId = 1L).collect { appointments ->
 *     updateAppointmentList(appointments)
 * }
 *
 * // Get only past appointments (for billable hours)
 * val pastAppointments = useCase.getPastAppointments(patientId = 1L)
 *
 * // Get only upcoming appointments (for scheduling)
 * val upcomingAppointments = useCase.getUpcomingAppointments(patientId = 1L)
 *
 * // Get appointments for specific month
 * val monthAppointments = useCase.getByDateRange(
 *     patientId = 1L,
 *     startDate = LocalDate.of(2024, 3, 1),
 *     endDate = LocalDate.of(2024, 3, 31)
 * )
 * ```
 */
class GetPatientAppointmentsUseCase(
    private val repository: AppointmentRepository
) {

    // ========================================
    // Main Execution Methods
    // ========================================

    /**
     * Execute appointment retrieval (sync)
     *
     * Gets all appointments for patient, sorted newest first.
     *
     * @param patientId Patient ID
     * @return List of appointments (newest first)
     */
    suspend fun execute(patientId: Long): List<Appointment> {
        return repository.getByPatient(patientId)
    }

    /**
     * Execute appointment retrieval (reactive)
     *
     * Returns Flow that automatically updates when appointments change.
     * Use in UI layer for reactive binding.
     *
     * @param patientId Patient ID
     * @return Flow of appointment list
     */
    fun executeFlow(patientId: Long): Flow<List<Appointment>> {
        return repository.getByPatientFlow(patientId)
    }

    // ========================================
    // Filtered Queries
    // ========================================

    /**
     * Get past appointments (completed sessions)
     *
     * Useful for billable hours calculation and session history.
     *
     * @param patientId Patient ID
     * @param beforeDate Cutoff date (default: today)
     * @return Past appointments (newest first)
     */
    suspend fun getPastAppointments(
        patientId: Long,
        beforeDate: LocalDate = LocalDate.now()
    ): List<Appointment> {
        return repository.getPastAppointmentsByPatient(patientId, beforeDate)
    }

    /**
     * Get upcoming appointments (scheduled sessions)
     *
     * Useful for showing patient's upcoming schedule.
     *
     * @param patientId Patient ID
     * @param fromDate Cutoff date (default: today)
     * @return Upcoming appointments (chronological)
     */
    suspend fun getUpcomingAppointments(
        patientId: Long,
        fromDate: LocalDate = LocalDate.now()
    ): List<Appointment> {
        return repository.getUpcomingAppointmentsByPatient(patientId, fromDate)
    }

    /**
     * Get appointments within date range
     *
     * Useful for monthly or weekly views.
     *
     * @param patientId Patient ID
     * @param startDate Start date (inclusive)
     * @param endDate End date (inclusive)
     * @return Appointments in range (newest first)
     */
    suspend fun getByDateRange(
        patientId: Long,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<Appointment> {
        return repository.getByPatientAndDateRange(patientId, startDate, endDate)
    }

    /**
     * Get appointments for current month
     *
     * @param patientId Patient ID
     * @return Appointments this month
     */
    suspend fun getCurrentMonthAppointments(patientId: Long): List<Appointment> {
        val now = LocalDate.now()
        val startDate = now.withDayOfMonth(1)
        val endDate = now.withDayOfMonth(now.lengthOfMonth())
        return getByDateRange(patientId, startDate, endDate)
    }

    /**
     * Get appointments for specific month
     *
     * @param patientId Patient ID
     * @param year Year
     * @param month Month (1-12)
     * @return Appointments for month
     */
    suspend fun getMonthAppointments(patientId: Long, year: Int, month: Int): List<Appointment> {
        val startDate = LocalDate.of(year, month, 1)
        val endDate = startDate.withDayOfMonth(startDate.lengthOfMonth())
        return getByDateRange(patientId, startDate, endDate)
    }

    /**
     * Get appointments for current week
     *
     * @param patientId Patient ID
     * @return Appointments this week
     */
    suspend fun getCurrentWeekAppointments(patientId: Long): List<Appointment> {
        val today = LocalDate.now()
        // Monday-based week
        val startDate = today.minusDays(today.dayOfWeek.value.toLong() - 1)
        val endDate = startDate.plusDays(6)
        return getByDateRange(patientId, startDate, endDate)
    }

    /**
     * Get last appointment for patient
     *
     * @param patientId Patient ID
     * @return Last appointment or null
     */
    suspend fun getLastAppointment(patientId: Long): Appointment? {
        return repository.getLastAppointmentByPatient(patientId)
    }

    /**
     * Get last appointment date
     *
     * Useful for "last session" display in patient profile.
     *
     * @param patientId Patient ID
     * @return Date of last appointment or null
     */
    suspend fun getLastAppointmentDate(patientId: Long): LocalDate? {
        return repository.getLastAppointmentDateByPatient(patientId)
    }

    // ========================================
    // Statistics & Counting
    // ========================================

    /**
     * Get total appointment count for patient
     *
     * @param patientId Patient ID
     * @return Number of appointments
     */
    suspend fun getCount(patientId: Long): Int {
        return repository.countByPatient(patientId)
    }

    /**
     * Get count of past appointments (completed sessions)
     *
     * @param patientId Patient ID
     * @return Number of past appointments
     */
    suspend fun getPastCount(patientId: Long): Int {
        return repository.getPastAppointmentsByPatient(patientId).size
    }

    /**
     * Get count of upcoming appointments
     *
     * @param patientId Patient ID
     * @return Number of upcoming appointments
     */
    suspend fun getUpcomingCount(patientId: Long): Int {
        return repository.getUpcomingAppointmentsByPatient(patientId).size
    }

    /**
     * Get total billable hours for patient
     *
     * Sum of all past appointment durations.
     *
     * @param patientId Patient ID
     * @return Total billable hours
     */
    suspend fun getTotalBillableHours(patientId: Long): Double {
        return repository.getTotalBillableHours(patientId)
    }

    /**
     * Get average session duration in hours
     *
     * @param patientId Patient ID
     * @return Average hours per session
     */
    suspend fun getAverageSessionHours(patientId: Long): Double {
        val past = getPastCount(patientId)
        return if (past > 0) {
            getTotalBillableHours(patientId) / past
        } else {
            0.0
        }
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
        return repository.hasAppointments(patientId)
    }

    /**
     * Check if patient has upcoming appointments
     *
     * @param patientId Patient ID
     * @return true if patient has scheduled sessions
     */
    suspend fun hasUpcomingAppointments(patientId: Long): Boolean {
        return repository.hasUpcomingAppointments(patientId)
    }

    // ========================================
    // Grouped/Formatted Results
    // ========================================

    /**
     * Get appointments grouped by date
     *
     * Useful for timeline/grouped list views.
     *
     * @param patientId Patient ID
     * @return Map of date to appointments
     */
    suspend fun getGroupedByDate(patientId: Long): Map<LocalDate, List<Appointment>> {
        return execute(patientId).groupBy { it.date }.toSortedMap(compareBy { it })
    }

    /**
     * Get appointments grouped by status (past/upcoming)
     *
     * @param patientId Patient ID
     * @return Object with past and upcoming lists
     */
    suspend fun getGroupedByStatus(patientId: Long): AppointmentsByStatus {
        val appointments = execute(patientId)
        return AppointmentsByStatus(
            past = appointments.filter { it.isPast },
            upcoming = appointments.filter { !it.isPast }
        )
    }

    /**
     * Get appointments with statistics
     *
     * @param patientId Patient ID
     * @return Object with appointments and statistics
     */
    suspend fun getWithStatistics(patientId: Long): AppointmentsWithStatistics {
        val appointments = execute(patientId)
        val stats = repository.getAppointmentStatistics(patientId)
        return AppointmentsWithStatistics(
            appointments = appointments,
            totalCount = stats.totalAppointments,
            pastCount = stats.pastAppointments,
            upcomingCount = stats.upcomingAppointments,
            totalBillableHours = stats.totalBillableHours,
            averageSessionHours = if (stats.pastAppointments > 0) {
                stats.totalBillableHours / stats.pastAppointments
            } else {
                0.0
            }
        )
    }

    // ========================================
    // Reactive Statistics
    // ========================================

    /**
     * Get count as reactive stream
     *
     * @param patientId Patient ID
     * @return Flow of appointment count
     */
    fun getCountFlow(patientId: Long): Flow<Int> {
        return executeFlow(patientId).map { it.size }
    }

    /**
     * Get billable hours as reactive stream
     *
     * @param patientId Patient ID
     * @return Flow of total billable hours
     */
    fun getTotalBillableHoursFlow(patientId: Long): Flow<Double> {
        return executeFlow(patientId).map { appointments ->
            appointments.filter { it.isPast }.getTotalBillableHours()
        }
    }
}

/**
 * Appointments grouped by status
 */
data class AppointmentsByStatus(
    val past: List<Appointment>,
    val upcoming: List<Appointment>
) {
    val total: Int
        get() = past.size + upcoming.size
}

/**
 * Appointments with statistics
 */
data class AppointmentsWithStatistics(
    val appointments: List<Appointment>,
    val totalCount: Int,
    val pastCount: Int,
    val upcomingCount: Int,
    val totalBillableHours: Double,
    val averageSessionHours: Double
) {
    /**
     * Get completion percentage (past vs total)
     */
    val completionPercentage: Int
        get() = if (totalCount > 0) (pastCount * 100) / totalCount else 0
}

/**
 * Extension function to sum billable hours
 *
 * @receiver List of appointments
 * @return Total billable hours
 */
fun List<Appointment>.getTotalBillableHours(): Double {
    return sumOf { it.billableHours }
}

/**
 * Extension function to get average session duration
 *
 * @receiver List of appointments
 * @return Average hours per session
 */
fun List<Appointment>.getAverageSessionHours(): Double {
    return if (isNotEmpty()) getTotalBillableHours() / size else 0.0
}
