package com.psychologist.financial.domain.usecases

import com.psychologist.financial.data.repositories.AppointmentRepository
import com.psychologist.financial.domain.models.Appointment
import java.time.LocalDate
import java.time.LocalTime

/**
 * Use case: Update an existing appointment
 *
 * Validates and persists changes to date, time, duration and notes.
 * Business rules:
 * - Appointment must exist (id > 0)
 * - Date cannot be in the future
 * - Duration: 5–480 minutes
 * - No schedule conflict with other appointments of the same patient
 */
class UpdateAppointmentUseCase(
    private val repository: AppointmentRepository
) {

    sealed class UpdateAppointmentResult {
        data class Success(val appointment: Appointment) : UpdateAppointmentResult()
        data class ValidationError(val field: String, val message: String) : UpdateAppointmentResult()
        data class Error(val message: String) : UpdateAppointmentResult()
    }

    suspend fun execute(
        appointmentId: Long,
        date: LocalDate,
        timeStart: LocalTime,
        durationMinutes: Int,
        notes: String?
    ): UpdateAppointmentResult {
        if (date.isAfter(LocalDate.now())) {
            return UpdateAppointmentResult.ValidationError("date", "Data da consulta não pode ser no futuro")
        }
        if (durationMinutes < 5) {
            return UpdateAppointmentResult.ValidationError("duration", "Duração mínima é 5 minutos")
        }
        if (durationMinutes > 480) {
            return UpdateAppointmentResult.ValidationError("duration", "Duração máxima é 8 horas (480 minutos)")
        }

        val existing = repository.getById(appointmentId)
            ?: return UpdateAppointmentResult.Error("Consulta não encontrada")

        // Check for overlap with other appointments of same patient (excluding self)
        val patientAppointments = repository.getByPatient(existing.patientId)
            .filter { it.id != appointmentId }

        val updated = existing.copy(
            date = date,
            timeStart = timeStart,
            durationMinutes = durationMinutes,
            notes = notes?.ifBlank { null }
        )

        val hasConflict = patientAppointments.any { updated.overlaps(it) }
        if (hasConflict) {
            return UpdateAppointmentResult.ValidationError("time", "Conflito de agenda: já existe agendamento neste horário")
        }

        return try {
            repository.update(updated)
            UpdateAppointmentResult.Success(updated)
        } catch (e: Exception) {
            UpdateAppointmentResult.Error("Erro ao atualizar consulta: ${e.message}")
        }
    }
}
