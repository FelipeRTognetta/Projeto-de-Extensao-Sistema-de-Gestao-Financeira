package com.psychologist.financial.domain.usecases

import com.psychologist.financial.data.repositories.PaymentRepository
import com.psychologist.financial.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Use case: Delete a payment permanently
 *
 * Responsibility:
 * - Remove all payment-appointment cross-refs for the payment first
 * - Delete the payment record by ID
 * - Appointments linked to this payment are NOT deleted; they remain
 *   but will no longer show as "paid"
 * - Biometric authentication is handled by the caller (ViewModel)
 *   before invoking this use case
 * - Throws exception on failure; callers handle errors via try/catch
 *
 * Usage:
 * ```kotlin
 * val useCase = DeletePaymentUseCase(paymentRepository)
 * useCase.execute(paymentId)
 * ```
 *
 * @property repository PaymentRepository for data operations
 */
class DeletePaymentUseCase(
    private val repository: PaymentRepository
) {

    private companion object {
        private const val TAG = "DeletePaymentUseCase"
    }

    /**
     * Delete payment by ID.
     *
     * Removes cross-refs before deleting the payment to maintain referential
     * integrity. Appointments are preserved.
     *
     * @param paymentId ID of the payment to delete
     * @throws Exception if deletion fails
     */
    suspend fun execute(paymentId: Long) = withContext(Dispatchers.IO) {
        AppLogger.d(TAG, "Deleting payment id=$paymentId")
        // Remove cross-refs first so appointments return to unpaid state
        repository.unlinkAllAppointments(paymentId)
        // Delete the payment record
        repository.deleteById(paymentId)
        AppLogger.d(TAG, "Payment id=$paymentId deleted successfully")
    }
}
