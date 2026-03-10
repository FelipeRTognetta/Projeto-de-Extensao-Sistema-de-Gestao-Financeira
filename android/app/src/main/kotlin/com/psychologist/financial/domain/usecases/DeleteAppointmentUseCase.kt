package com.psychologist.financial.domain.usecases

import com.psychologist.financial.data.repositories.AppointmentRepository
import com.psychologist.financial.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Use case: Delete an appointment permanently
 *
 * Responsibility:
 * - Delete a single appointment by ID
 * - Payment-appointment cross-refs (if any) are removed by the DB cascade
 *   or handled by the caller; appointments can be deleted independently
 * - Throws exception on failure; callers handle errors via try/catch
 *
 * Usage:
 * ```kotlin
 * val useCase = DeleteAppointmentUseCase(appointmentRepository)
 * useCase.execute(appointmentId)
 * ```
 *
 * @property repository AppointmentRepository for data operations
 */
class DeleteAppointmentUseCase(
    private val repository: AppointmentRepository
) {

    private companion object {
        private const val TAG = "DeleteAppointmentUseCase"
    }

    /**
     * Delete appointment by ID.
     *
     * @param appointmentId ID of the appointment to delete
     * @throws Exception if deletion fails
     */
    suspend fun execute(appointmentId: Long) = withContext(Dispatchers.IO) {
        AppLogger.d(TAG, "Deleting appointment id=$appointmentId")
        repository.deleteById(appointmentId)
        AppLogger.d(TAG, "Appointment id=$appointmentId deleted successfully")
    }
}
