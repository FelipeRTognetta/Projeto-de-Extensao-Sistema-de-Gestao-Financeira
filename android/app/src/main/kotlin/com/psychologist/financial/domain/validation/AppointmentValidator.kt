package com.psychologist.financial.domain.validation

import android.util.Log
import java.time.LocalDate
import java.time.LocalTime

/**
 * Validator for Appointment data
 *
 * Enforces business rules for appointment scheduling:
 * - Date: Valid LocalDate, cannot be in future
 * - Time: Valid LocalTime, reasonable working hours (6:00 - 22:00)
 * - Duration: 5-480 minutes (5 min to 8 hours)
 * - Patient Status: Only ACTIVE patients can have appointments created
 *
 * Architecture:
 * - Validation layer (domain)
 * - Independent of UI and database
 * - Testable without framework
 * - Used by use cases before repository operations
 *
 * Validation Flow:
 * 1. AppointmentValidator checks format/constraints
 * 2. Use case applies validator
 * 3. Repository checks scheduling conflicts
 * 4. Database enforces constraints
 * (Defense in depth)
 *
 * Usage:
 * ```kotlin
 * val validator = AppointmentValidator()
 *
 * val errors = validator.validateNewAppointment(
 *     date = LocalDate.of(2024, 3, 15),
 *     timeStart = LocalTime.of(14, 30),
 *     durationMinutes = 60,
 *     patientStatus = PatientStatus.ACTIVE
 * )
 *
 * if (errors.isNotEmpty()) {
 *     showErrors(errors)
 * } else {
 *     createAppointment()
 * }
 * ```
 *
 * Error Messages:
 * - Portuguese localization for user display
 * - Clear, actionable feedback
 * - Field-specific errors
 */
class AppointmentValidator {

    private companion object {
        private const val TAG = "AppointmentValidator"

        // Validation constants
        private const val DURATION_MIN_MINUTES = 5
        private const val DURATION_MAX_MINUTES = 480  // 8 hours
        private const val WORKING_HOURS_START = 6     // 6:00 AM
        private const val WORKING_HOURS_END = 22      // 10:00 PM
    }

    /**
     * Validate new appointment data
     *
     * Checks all required fields for new appointment creation.
     * Returns list of validation errors (empty if valid).
     *
     * @param date Appointment date
     * @param timeStart Session start time
     * @param durationMinutes Duration in minutes
     * @param patientStatus Patient status (to prevent appointments for inactive)
     * @return List of ValidationError (empty if valid)
     *
     * Example:
     * ```kotlin
     * val errors = validator.validateNewAppointment(
     *     date = LocalDate.of(2024, 3, 15),
     *     timeStart = LocalTime.of(14, 30),
     *     durationMinutes = 60,
     *     patientStatus = PatientStatus.ACTIVE
     * )
     * ```
     */
    fun validateNewAppointment(
        date: LocalDate,
        timeStart: LocalTime,
        durationMinutes: Int,
        patientStatus: String = "ACTIVE"
    ): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        // Validate patient status
        validatePatientStatus(patientStatus).let { errors.addAll(it) }

        // Validate date
        validateDate(date).let { errors.addAll(it) }

        // Validate time
        validateTime(timeStart).let { errors.addAll(it) }

        // Validate duration
        validateDuration(durationMinutes).let { errors.addAll(it) }

        if (errors.isNotEmpty()) {
            Log.w(TAG, "Validation failed: ${errors.size} errors")
        }

        return errors
    }

    /**
     * Validate appointment date
     *
     * Rules:
     * - Required (not null)
     * - Cannot be in future (appointments are logged for past/current sessions)
     * - Must be a valid LocalDate
     *
     * Note: Past dates are allowed for entering historical appointment data
     *
     * @param date Date to validate
     * @return List of errors (empty if valid)
     */
    fun validateDate(date: LocalDate): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        val today = LocalDate.now()

        // Cannot be in future
        if (date.isAfter(today)) {
            errors.add(ValidationError(
                field = "date",
                message = "Data da consulta não pode ser no futuro"
            ))
        }

        return errors
    }

    /**
     * Validate appointment start time
     *
     * Rules:
     * - Required (not null)
     * - Valid 24-hour format (enforced by LocalTime)
     * - Reasonable working hours (6:00 - 22:00)
     *   Note: Can be outside working hours for evening/early sessions,
     *         but a warning is logged
     *
     * @param timeStart Start time to validate
     * @return List of errors (empty if valid)
     */
    fun validateTime(timeStart: LocalTime): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        val startHour = timeStart.hour

        // Check if outside typical working hours (informational only)
        if (startHour < WORKING_HOURS_START || startHour >= WORKING_HOURS_END) {
            Log.w(TAG, "Appointment scheduled outside working hours: ${timeStart.hour}:${timeStart.minute}")
        }

        return errors
    }

    /**
     * Validate appointment duration
     *
     * Rules:
     * - Required (> 0)
     * - Minimum: 5 minutes
     * - Maximum: 480 minutes (8 hours)
     *
     * @param durationMinutes Duration in minutes to validate
     * @return List of errors (empty if valid)
     */
    fun validateDuration(durationMinutes: Int): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        // Minimum duration
        if (durationMinutes < DURATION_MIN_MINUTES) {
            errors.add(ValidationError(
                field = "duration",
                message = "Duração mínima é $DURATION_MIN_MINUTES minutos"
            ))
        }

        // Maximum duration
        if (durationMinutes > DURATION_MAX_MINUTES) {
            errors.add(ValidationError(
                field = "duration",
                message = "Duração máxima é $DURATION_MAX_MINUTES minutos (8 horas)"
            ))
        }

        return errors
    }

    /**
     * Validate patient status
     *
     * Business rule: Only ACTIVE patients can have new appointments created.
     * Prevents accidental scheduling for inactive/archived patients.
     *
     * @param patientStatus Patient status (e.g., "ACTIVE", "INACTIVE")
     * @return List of errors (empty if valid)
     */
    fun validatePatientStatus(patientStatus: String): List<ValidationError> {
        val status = patientStatus.trim().uppercase()

        // Only ACTIVE patients can have appointments
        if (status != "ACTIVE") {
            return listOf(ValidationError(
                field = "patientStatus",
                message = "Não é possível criar agendamento para paciente inativo"
            ))
        }

        return emptyList()
    }

    /**
     * Validate appointment update (same as new, but with id)
     *
     * For future use when implementing appointment edit.
     *
     * @param appointmentId Current appointment ID
     * @param date Updated date
     * @param timeStart Updated time
     * @param durationMinutes Updated duration
     * @param patientStatus Patient status
     * @return List of errors (empty if valid)
     */
    fun validateUpdate(
        appointmentId: Long,
        date: LocalDate,
        timeStart: LocalTime,
        durationMinutes: Int,
        patientStatus: String = "ACTIVE"
    ): List<ValidationError> {
        require(appointmentId > 0) { "Appointment must be saved (id > 0) to update" }

        // Use same validation as new appointment
        return validateNewAppointment(date, timeStart, durationMinutes, patientStatus)
    }
}

/**
 * Validate appointment date quickly
 *
 * @param date Date to check
 * @return true if date is valid (not in future)
 */
fun isValidAppointmentDate(date: LocalDate): Boolean {
    return !date.isAfter(LocalDate.now())
}

/**
 * Validate appointment time quickly
 *
 * @param time Time to check
 * @return true if time is valid (always true for LocalTime)
 */
fun isValidAppointmentTime(time: LocalTime): Boolean {
    // LocalTime is always valid if it's a valid LocalTime
    return true
}

/**
 * Validate appointment duration quickly
 *
 * @param durationMinutes Duration to check
 * @return true if duration is in valid range (5-480 minutes)
 */
fun isValidAppointmentDuration(durationMinutes: Int): Boolean {
    return durationMinutes in 5..480
}

/**
 * Validate patient status for appointment creation
 *
 * @param patientStatus Patient status string
 * @return true if patient is active and can have appointments
 */
fun canCreateAppointmentForPatient(patientStatus: String): Boolean {
    return patientStatus.trim().uppercase() == "ACTIVE"
}

/**
 * Convert duration in minutes to hours string
 *
 * Examples:
 * - 30 → "0.5h"
 * - 60 → "1h"
 * - 90 → "1.5h"
 *
 * @param durationMinutes Duration in minutes
 * @return Formatted duration string
 */
fun formatAppointmentDuration(durationMinutes: Int): String {
    val hours = durationMinutes / 60
    val minutes = durationMinutes % 60

    return when {
        hours == 0 -> "${durationMinutes}min"
        minutes == 0 -> "${hours}h"
        else -> "${hours}h${minutes}m"
    }
}

/**
 * Convert duration in minutes to decimal hours
 *
 * Examples:
 * - 30 → 0.5
 * - 60 → 1.0
 * - 90 → 1.5
 *
 * @param durationMinutes Duration in minutes
 * @return Duration in decimal hours
 */
fun toDecimalHours(durationMinutes: Int): Double {
    return durationMinutes / 60.0
}
