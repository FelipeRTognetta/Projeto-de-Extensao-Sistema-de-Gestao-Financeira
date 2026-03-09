package com.psychologist.financial.domain.usecases

import com.psychologist.financial.data.repositories.AppointmentRepository
import com.psychologist.financial.domain.models.AppointmentWithPaymentStatus
import kotlinx.coroutines.flow.Flow

/**
 * Use case: Retrieve all appointments from all patients with payment status.
 *
 * Returns a reactive Flow from the repository. Each emission contains all
 * appointments across all patients, ordered by date DESC, with a derived
 * [AppointmentWithPaymentStatus.hasPendingPayment] flag that reflects whether
 * the appointment is linked to a payment via the junction table.
 *
 * Used by:
 * - [GlobalAppointmentListScreen] — bottom-nav Consultas tab
 * - [AppointmentViewModel.loadAllAppointments]
 *
 * @param appointmentRepository Repository providing the reactive data source
 */
class GetAllAppointmentsUseCase(
    private val appointmentRepository: AppointmentRepository
) {
    /**
     * Execute the use case.
     *
     * @return Flow emitting the list of all appointments with derived payment status
     */
    fun execute(): Flow<List<AppointmentWithPaymentStatus>> =
        appointmentRepository.getAllWithPaymentStatus()
}
