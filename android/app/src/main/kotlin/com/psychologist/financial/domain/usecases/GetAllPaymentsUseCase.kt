package com.psychologist.financial.domain.usecases

import com.psychologist.financial.data.repositories.PaymentRepository
import com.psychologist.financial.data.repositories.PaymentWithDetails
import kotlinx.coroutines.flow.Flow

/**
 * Use case: Get all payments from all patients
 *
 * Returns a reactive flow of all payments with their linked appointments,
 * ordered by payment date DESC (most recent first — enforced at DAO level).
 *
 * Used by the global payment list tab (bottom navigation).
 *
 * @property paymentRepository Repository for payment queries
 */
class GetAllPaymentsUseCase(
    private val paymentRepository: PaymentRepository
) {

    /**
     * Get all payments with linked appointments as a Flow.
     *
     * @return Flow emitting list of PaymentWithDetails ordered by payment_date DESC
     */
    fun execute(): Flow<List<PaymentWithDetails>> {
        return paymentRepository.getAllWithAppointments()
    }
}
