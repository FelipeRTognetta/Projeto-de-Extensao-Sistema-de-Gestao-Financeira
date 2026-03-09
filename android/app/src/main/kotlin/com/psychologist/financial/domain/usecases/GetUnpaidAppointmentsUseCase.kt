package com.psychologist.financial.domain.usecases

import com.psychologist.financial.data.repositories.AppointmentRepository
import com.psychologist.financial.domain.models.Appointment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Use case: Get unpaid appointments for a patient
 *
 * Returns appointments that have no payment linked in the junction table.
 * Used by the payment form to show which appointments are available to link.
 *
 * @property appointmentRepository Repository for appointment queries
 */
class GetUnpaidAppointmentsUseCase(
    private val appointmentRepository: AppointmentRepository
) {

    /**
     * Get unpaid appointments for patient as a Flow
     *
     * @param patientId Patient ID
     * @return Flow emitting list of unpaid appointments
     */
    fun execute(patientId: Long): Flow<List<Appointment>> = flow {
        emit(appointmentRepository.getUnpaidByPatient(patientId))
    }
}
