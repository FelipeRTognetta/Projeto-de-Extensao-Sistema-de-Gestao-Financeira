package com.psychologist.financial.domain.usecases

import com.psychologist.financial.data.repositories.PatientRepository
import com.psychologist.financial.data.repositories.PaymentRepository
import com.psychologist.financial.domain.models.Payment
import com.psychologist.financial.domain.models.PatientStatus
import com.psychologist.financial.domain.validation.PaymentValidator

/**
 * Use case: Create a new payment with validation
 *
 * Responsibilities:
 * - Validate payment data before insertion
 * - Enforce business rules (patient must be ACTIVE)
 * - Prevent payments for inactive patients
 * - Save to repository (atomically with appointment links)
 *
 * Validation order:
 * 1. Patient existence check
 * 2. Patient status check (must be ACTIVE)
 * 3. PaymentValidator rules (amount range, patientId)
 * 4. Database insertion (with appointment links if provided)
 *
 * Business rules:
 * - INACTIVE patients cannot receive payments
 * - Payments with no appointments: inserts to payments table only
 * - Payments with appointments: atomic insert to payments + junction table
 * - All payments are implicitly PAID (no status field)
 *
 * @property paymentRepository PaymentRepository for data persistence
 * @property patientRepository PatientRepository for patient verification
 * @property paymentValidator PaymentValidator for validation rules
 */
class CreatePaymentUseCase(
    private val paymentRepository: PaymentRepository,
    private val patientRepository: PatientRepository,
    private val paymentValidator: PaymentValidator
) {

    /**
     * Create payment with optional appointment links
     *
     * @param payment Payment to create (must have id=0)
     * @param appointmentIds List of appointment IDs to link (empty = no links)
     * @return ID of inserted payment
     * @throws IllegalStateException if patient not found or inactive
     * @throws IllegalArgumentException if payment data is invalid
     */
    suspend fun createPayment(
        payment: Payment,
        appointmentIds: List<Long>
    ): Long {
        // Check patient exists
        val patient = patientRepository.getPatient(payment.patientId)
            ?: throw IllegalStateException("Paciente não encontrado: ${payment.patientId}")

        // Check patient is active
        if (patient.status != PatientStatus.ACTIVE) {
            throw IllegalStateException(
                "Não é possível adicionar pagamentos para pacientes inativos (${patient.status})"
            )
        }

        // Validate payment data
        val validationResult = paymentValidator.validate(payment)
        if (!validationResult.isValid) {
            throw IllegalArgumentException(validationResult.errors.joinToString("; "))
        }

        // Save atomically
        return if (appointmentIds.isEmpty()) {
            paymentRepository.insert(payment.patientId, payment.amount, payment.paymentDate)
        } else {
            paymentRepository.createPaymentWithAppointments(payment, appointmentIds)
        }
    }
}
