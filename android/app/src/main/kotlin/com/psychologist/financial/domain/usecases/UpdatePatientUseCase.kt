package com.psychologist.financial.domain.usecases

import com.psychologist.financial.data.repositories.PatientRepository
import com.psychologist.financial.domain.models.Patient
import com.psychologist.financial.domain.validation.PatientValidator
import java.time.LocalDate

/**
 * Use case: Update an existing patient's information
 *
 * Validates and persists changes to name, phone, email and initial consult date.
 * Business rules:
 * - Patient must exist (id > 0)
 * - Name is mandatory
 * - At least phone or email must be provided
 * - Phone/email must be unique (excluding the patient itself)
 */
class UpdatePatientUseCase(
    private val repository: PatientRepository,
    private val patientValidator: PatientValidator = PatientValidator()
) {

    sealed class UpdatePatientResult {
        data class Success(val patient: Patient) : UpdatePatientResult()
        data class ValidationError(val message: String) : UpdatePatientResult()
        data class Error(val message: String) : UpdatePatientResult()
    }

    suspend fun execute(
        patientId: Long,
        name: String,
        phone: String?,
        email: String?,
        initialConsultDate: LocalDate,
        cpf: String? = null,
        endereco: String? = null
    ): UpdatePatientResult {
        // Basic validation
        if (name.isBlank()) {
            return UpdatePatientResult.ValidationError("Nome é obrigatório")
        }
        if (phone.isNullOrBlank() && email.isNullOrBlank()) {
            return UpdatePatientResult.ValidationError("Informe telefone ou email")
        }

        val rawCpf = cpf?.filter { it.isDigit() }?.ifEmpty { null }
        val cpfErrors = patientValidator.validateCpf(rawCpf)
        if (cpfErrors.isNotEmpty()) {
            return UpdatePatientResult.ValidationError(cpfErrors.first().message)
        }

        val existing = repository.getPatient(patientId)
            ?: return UpdatePatientResult.Error("Paciente não encontrado")

        val updated = existing.copy(
            name = name.trim(),
            phone = phone?.trim()?.ifBlank { null },
            email = email?.trim()?.ifBlank { null },
            initialConsultDate = initialConsultDate,
            cpf = rawCpf,
            endereco = endereco?.trim()?.ifBlank { null }
        )

        return try {
            repository.updatePatient(updated)
            UpdatePatientResult.Success(updated)
        } catch (e: IllegalArgumentException) {
            UpdatePatientResult.ValidationError(e.message ?: "Dados inválidos")
        } catch (e: Exception) {
            UpdatePatientResult.Error("Erro ao atualizar paciente: ${e.message}")
        }
    }
}
