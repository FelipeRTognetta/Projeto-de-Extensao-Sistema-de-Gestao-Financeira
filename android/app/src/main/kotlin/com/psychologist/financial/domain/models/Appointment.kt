package com.psychologist.financial.domain.models

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Appointment domain model
 *
 * Represents a psychotherapy session in the domain layer.
 * Mapped from AppointmentEntity (database) for business logic.
 *
 * Responsibilities:
 * - Represent appointment data in domain layer
 * - Provide business logic helper methods
 * - Independent of database/UI frameworks
 * - Rich model with computed properties
 *
 * Properties:
 * - id: Unique appointment identifier
 * - patientId: Link to patient
 * - date: Appointment date
 * - timeStart: Session start time
 * - durationMinutes: Session duration in minutes (5-480)
 * - notes: Optional session notes
 * - createdDate: When appointment was created
 *
 * Computed Properties:
 * - billableHours: Duration converted to decimal hours
 * - endTime: Calculated end time
 * - isPast: True if appointment has passed
 * - isUpcoming: True if appointment is in future
 * - isToday: True if appointment is today
 * - daysUntil: Days until appointment (negative for past)
 * - displayTime: Formatted time string
 * - displayDuration: Formatted duration string
 *
 * Business Rules:
 * - Duration: 5-480 minutes (5 min to 8 hours)
 * - Date: Can be past (historical data entry)
 * - Notes: Optional, max 1000 characters
 * - Patient Link: Required (every appointment has a patient)
 *
 * Example:
 * ```kotlin
 * val appointment = Appointment(
 *     id = 1L,
 *     patientId = 1L,
 *     date = LocalDate.of(2024, 3, 15),
 *     timeStart = LocalTime.of(14, 30),
 *     durationMinutes = 60,
 *     notes = "Discussed anxiety management"
 * )
 *
 * // Business logic
 * println(appointment.billableHours)  // 1.0
 * println(appointment.displayDuration) // "1h"
 * println(appointment.isPast)  // true/false based on current time
 * println(appointment.getFormattedDateTime()) // "15/03/2024 14:30"
 * ```
 *
 * Comparison with Entity:
 * - AppointmentEntity: Database representation
 * - Appointment: Business logic representation
 * - Conversion: Repository handles Entity ↔ Model mapping
 */
data class Appointment(
    val id: Long,
    val patientId: Long,
    val date: LocalDate,
    val timeStart: LocalTime,
    val durationMinutes: Int,
    val notes: String? = null,
    val createdDate: LocalDateTime = LocalDateTime.now()
) {

    // ========================================
    // Computed Properties
    // ========================================

    /**
     * Calculate billable hours for this appointment
     *
     * Billable hours = duration_minutes / 60
     * Example: 90 minutes = 1.5 hours
     *
     * @return Hours as decimal
     */
    val billableHours: Double
        get() = durationMinutes / 60.0

    /**
     * Calculate end time based on start + duration
     *
     * Example: 14:30 + 60 min = 15:30
     *
     * @return End time (LocalTime)
     */
    val endTime: LocalTime
        get() = timeStart.plusMinutes(durationMinutes.toLong())

    /**
     * Check if appointment has passed
     *
     * Compares appointment date/time with current moment.
     *
     * @return true if appointment time is before now
     */
    val isPast: Boolean
        get() {
            val now = LocalDateTime.now()
            val appointmentDateTime = java.time.LocalDateTime.of(date, timeStart)
            return appointmentDateTime.isBefore(now)
        }

    /**
     * Check if appointment is in the future
     *
     * @return true if appointment time is after now
     */
    val isUpcoming: Boolean
        get() = !isPast && !isToday

    /**
     * Check if appointment is today
     *
     * @return true if appointment date is today
     */
    val isToday: Boolean
        get() = date == LocalDate.now()

    /**
     * Get number of days until appointment
     *
     * Negative for past appointments, 0 for today.
     *
     * @return Days count (-infinity to +infinity)
     */
    val daysUntilAppointment: Long
        get() = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), date)

    /**
     * Format appointment time as "HH:mm"
     *
     * @return Formatted time string (e.g., "14:30")
     */
    val displayTime: String
        get() {
            val hour = timeStart.hour.toString().padStart(2, '0')
            val minute = timeStart.minute.toString().padStart(2, '0')
            return "$hour:$minute"
        }

    /**
     * Format duration as human-readable string
     *
     * Examples:
     * - 30 min → "30min"
     * - 60 min → "1h"
     * - 90 min → "1h 30min"
     *
     * @return Formatted duration string
     */
    val displayDuration: String
        get() {
            val hours = durationMinutes / 60
            val minutes = durationMinutes % 60

            return when {
                hours > 0 && minutes > 0 -> "${hours}h ${minutes}min"
                hours > 0 -> "${hours}h"
                else -> "${minutes}min"
            }
        }

    /**
     * Check if has notes
     *
     * @return true if notes field is not null/empty
     */
    val hasNotes: Boolean
        get() = !notes.isNullOrBlank()

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
        val day = date.dayOfMonth.toString().padStart(2, '0')
        val month = date.monthValue.toString().padStart(2, '0')
        val year = date.year
        return "$day/$month/$year"
    }

    /**
     * Get formatted date and time (Portuguese)
     *
     * Format: "dd/MM/yyyy HH:mm" (e.g., "15/03/2024 14:30")
     *
     * @return Formatted datetime string
     */
    fun getFormattedDateTime(): String {
        return "${getFormattedDate()} $displayTime"
    }

    /**
     * Get human-readable appointment description
     *
     * Format: "15/03/2024 às 14:30 (1h)"
     *
     * @return Description string
     */
    fun getDescription(): String {
        return "${getFormattedDate()} às $displayTime ($displayDuration)"
    }

    /**
     * Get status label (Portuguese)
     *
     * Returns appointment status relative to current time.
     *
     * @return "Passada", "Hoje", "Próxima", or "Agendada"
     */
    fun getStatusLabel(): String {
        return when {
            isPast -> "Passada"
            isToday -> "Hoje"
            daysUntilAppointment <= 7 -> "Próxima"
            else -> "Agendada"
        }
    }

    /**
     * Get relative date display (Portuguese)
     *
     * Examples:
     * - Today: "Hoje"
     * - Tomorrow: "Amanhã"
     * - Past: "15/03/2024"
     * - Future: "15/03/2024"
     *
     * @return Relative or absolute date string
     */
    fun getRelativeDate(): String {
        return when {
            isToday -> "Hoje"
            daysUntilAppointment == 1L -> "Amanhã"
            else -> getFormattedDate()
        }
    }

    /**
     * Get appointment summary for list display
     *
     * Format: "15/03 às 14:30 (1h) - João Silva"
     * This would need patient name injected.
     *
     * @param patientName Name of the patient
     * @return Summary string
     */
    fun getSummary(patientName: String = ""): String {
        val dateStr = "${date.dayOfMonth}/${date.monthValue}"
        val timeStr = displayTime
        val suffix = if (patientName.isNotEmpty()) " - $patientName" else ""
        return "$dateStr às $timeStr ($displayDuration)$suffix"
    }

    // ========================================
    // Comparison Methods
    // ========================================

    /**
     * Check if this appointment overlaps with another
     *
     * Same patient, same date, overlapping time ranges.
     *
     * @param other Other appointment to check
     * @return true if appointments overlap
     */
    fun overlaps(other: Appointment): Boolean {
        if (patientId != other.patientId || date != other.date) {
            return false
        }

        return timeStart < other.endTime && endTime > other.timeStart
    }

    /**
     * Check if appointment is within date range
     *
     * @param startDate Start date (inclusive)
     * @param endDate End date (inclusive)
     * @return true if appointment date is within range
     */
    fun isWithinDateRange(startDate: LocalDate, endDate: LocalDate): Boolean {
        return !date.isBefore(startDate) && !date.isAfter(endDate)
    }

    /**
     * Check if appointment is within time range on a specific date
     *
     * Useful for checking day schedule conflicts.
     *
     * @param checkDate Date to check against
     * @param startTime Start of range
     * @param endTime End of range
     * @return true if appointment overlaps with time range on date
     */
    fun isWithinTimeRange(checkDate: LocalDate, startTime: LocalTime, endTime: LocalTime): Boolean {
        if (date != checkDate) return false
        return timeStart < endTime && this.endTime > startTime
    }

    // ========================================
    // Validation Methods
    // ========================================

    /**
     * Check if appointment data is valid
     *
     * Business rule validations:
     * - Duration: 5-480 minutes
     * - Patient ID: > 0
     * - Date: Valid (not enforced here, LocalDate handles)
     * - Time: Valid (not enforced here, LocalTime handles)
     *
     * @return true if all validations pass
     */
    fun isValid(): Boolean {
        return patientId > 0 &&
                durationMinutes in 5..480
    }

    /**
     * Get validation error if invalid
     *
     * @return Error message or null if valid
     */
    fun getValidationError(): String? {
        if (patientId <= 0) return "Paciente inválido"
        if (durationMinutes < 5) return "Duração mínima é 5 minutos"
        if (durationMinutes > 480) return "Duração máxima é 8 horas"
        return null
    }

    // ========================================
    // Companion Object
    // ========================================

    companion object {
        // Duration constraints (in minutes)
        const val MIN_DURATION_MINUTES = 5
        const val MAX_DURATION_MINUTES = 480  // 8 hours

        /**
         * Create sample appointment for testing
         *
         * @param patientId Patient ID (default 1L)
         * @param offset Days offset from today (default 0 = today)
         * @return Sample appointment
         */
        fun sample(patientId: Long = 1L, offset: Long = 0): Appointment {
            return Appointment(
                id = 0L,
                patientId = patientId,
                date = LocalDate.now().plusDays(offset),
                timeStart = LocalTime.of(14, 30),
                durationMinutes = 60,
                notes = "Session notes"
            )
        }
    }
}

/**
 * Extension function to check if list contains overlapping appointments
 *
 * Useful for detecting scheduling conflicts.
 *
 * @receiver List of appointments
 * @return true if any appointments overlap
 */
fun List<Appointment>.hasOverlappingAppointments(): Boolean {
    for (i in indices) {
        for (j in (i + 1) until size) {
            if (this[i].overlaps(this[j])) {
                return true
            }
        }
    }
    return false
}

/**
 * Extension function to get total billable hours
 *
 * Sums billable hours across all appointments.
 *
 * @receiver List of appointments
 * @return Total billable hours
 */
fun List<Appointment>.getTotalBillableHours(): Double {
    return sumOf { it.billableHours }
}

/**
 * Extension function to filter appointments by date range
 *
 * @receiver List of appointments
 * @param startDate Start date (inclusive)
 * @param endDate End date (inclusive)
 * @return Filtered list
 */
fun List<Appointment>.filterByDateRange(startDate: LocalDate, endDate: LocalDate): List<Appointment> {
    return filter { it.isWithinDateRange(startDate, endDate) }
}

/**
 * Extension function to get upcoming appointments
 *
 * @receiver List of appointments
 * @return Appointments with date >= today
 */
fun List<Appointment>.getUpcoming(): List<Appointment> {
    return filter { !it.isPast }
}

/**
 * Extension function to get past appointments
 *
 * @receiver List of appointments
 * @return Appointments with date < today
 */
fun List<Appointment>.getPast(): List<Appointment> {
    return filter { it.isPast }
}
