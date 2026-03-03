package com.psychologist.financial.domain.usecases

import com.psychologist.financial.data.repositories.AppointmentRepository
import com.psychologist.financial.domain.models.Appointment
import com.psychologist.financial.domain.validation.AppointmentValidator
import com.psychologist.financial.domain.validation.ValidationError
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Create appointment use case
 *
 * Handles appointment creation with comprehensive validation.
 * Enforces business rules and constraints before persisting.
 *
 * Responsibilities:
 * - Validate appointment data (date, time, duration)
 * - Check scheduling conflicts
 * - Persist appointment to repository
 * - Return structured result (Success/Error)
 *
 * Validation Rules:
 * - Patient ID: Must be > 0
 * - Date: Cannot be in future (optional validation)
 * - Time: Must be valid 24-hour format (enforced by LocalTime)
 * - Duration: 5-480 minutes (5 min to 8 hours)
 * - Overlap: Check for scheduling conflicts with patient's other appointments
 *
 * Result Types:
 * - Success: Contains appointment ID
 * - ValidationError: Contains field-specific errors
 * - Error: Contains generic error message (database failure, etc.)
 *
 * Example Usage:
 * ```kotlin
 * val useCase = CreateAppointmentUseCase(
 *     repository = appointmentRepository,
 *     validator = appointmentValidator
 * )
 *
 * val result = useCase.execute(
 *     patientId = 1L,
 *     date = LocalDate.of(2024, 3, 15),
 *     timeStart = LocalTime.of(14, 30),
 *     durationMinutes = 60,
 *     notes = "Session notes"
 * )

 * when (result) {
 *     is CreateAppointmentResult.Success -> {
 *         println("Created appointment: ${result.appointmentId}")
 *     }
 *     is CreateAppointmentResult.ValidationError -> {
 *         result.errors.forEach { error ->
 *             println("${error.field}: ${error.message}")
 *         }
 *     }
 *     is CreateAppointmentResult.Error -> {
 *         println("Error: ${result.message}")
 *     }
 * }
 * ```
 */
class CreateAppointmentUseCase(
    private val repository: AppointmentRepository,
    private val validator: AppointmentValidator? = null
) {

    /**
     * Execute appointment creation
     *
     * @param patientId Patient ID
     * @param date Appointment date
     * @param timeStart Session start time
     * @param durationMinutes Duration in minutes
     * @param notes Optional session notes
     * @return CreateAppointmentResult (Success/Error)
     */
    suspend fun execute(
        patientId: Long,
        date: LocalDate,
        timeStart: LocalTime,
        durationMinutes: Int,
        notes: String? = null
    ): CreateAppointmentResult {
        try {
            // Validate appointment data
            val validationErrors = mutableListOf<ValidationError>()

            // Patient validation
            if (patientId <= 0) {
                validationErrors.add(ValidationError(
                    field = "patientId",
                    message = "Paciente inválido"
                ))
            }

            // Date validation
            if (date.isAfter(LocalDate.now())) {
                validationErrors.add(ValidationError(
                    field = "date",
                    message = "Data da consulta não pode ser no futuro"
                ))
            }

            // Duration validation
            if (durationMinutes < 5) {
                validationErrors.add(ValidationError(
                    field = "durationMinutes",
                    message = "Duração mínima é 5 minutos"
                ))
            }
            if (durationMinutes > 480) {
                validationErrors.add(ValidationError(
                    field = "durationMinutes",
                    message = "Duração máxima é 8 horas (480 minutos)"
                ))
            }

            // Check time validity (LocalTime already validates)
            // Additional: Check reasonable working hours (optional)
            if (timeStart.isBefore(LocalTime.of(6, 0)) || timeStart.isAfter(LocalTime.of(22, 0))) {
                // Allow but warn - could be evening/early sessions
            }

            // Check for overlapping appointments with same patient
            val appointmentToCreate = Appointment(
                id = 0L,
                patientId = patientId,
                date = date,
                timeStart = timeStart,
                durationMinutes = durationMinutes,
                notes = notes
            )

            val patientAppointments = repository.getByPatient(patientId)
            val overlappingAppointments = patientAppointments.filter { existing ->
                appointmentToCreate.overlaps(existing)
            }

            if (overlappingAppointments.isNotEmpty()) {
                validationErrors.add(ValidationError(
                    field = "time",
                    message = "Conflito de agenda: já existe agendamento neste horário"
                ))
            }

            // Return validation errors if any
            if (validationErrors.isNotEmpty()) {
                return CreateAppointmentResult.ValidationError(errors = validationErrors)
            }

            // Create appointment
            val appointmentId = repository.insert(
                patientId = patientId,
                date = date,
                timeStart = timeStart,
                durationMinutes = durationMinutes,
                notes = notes
            )

            return CreateAppointmentResult.Success(appointmentId = appointmentId)

        } catch (e: Exception) {
            return CreateAppointmentResult.Error(
                message = "Erro ao criar agendamento: ${e.message ?: "erro desconhecido"}"
            )
        }
    }

    /**
     * Sealed class for appointment creation results
     *
     * Type-safe result handling with specific success/error cases.
     */
    sealed class CreateAppointmentResult {

        /**
         * Successful appointment creation
         *
         * @param appointmentId ID of created appointment
         */
        data class Success(val appointmentId: Long) : CreateAppointmentResult()

        /**
         * Validation failed (user input error)
         *
         * @param errors List of validation errors by field
         */
        data class ValidationError(val errors: List<com.psychologist.financial.domain.validation.ValidationError>) :
            CreateAppointmentResult() {

            /**
             * Get error for specific field
             *
             * @param field Field name
             * @return Error message or null
             */
            fun getFieldError(field: String): String? {
                return errors.firstOrNull { it.field == field }?.message
            }

            /**
             * Check if specific field has error
             *
             * @param field Field name
             * @return true if field has error
             */
            fun hasFieldError(field: String): Boolean {
                return errors.any { it.field == field }
            }
        }

        /**
         * Generic error (database failure, etc.)
         *
         * @param message Error description
         */
        data class Error(val message: String) : CreateAppointmentResult()
    }
}

/**
 * Extension function for quick validation
 *
 * @receiver Appointment
 * @return Error message or null if valid
 */
fun Appointment.validateForCreation(): String? {
    return when {
        patientId <= 0 -> "Paciente inválido"
        date.isAfter(LocalDate.now()) -> "Data não pode ser no futuro"
        durationMinutes < 5 -> "Duração mínima é 5 minutos"
        durationMinutes > 480 -> "Duração máxima é 8 horas"
        else -> null
    }
}
