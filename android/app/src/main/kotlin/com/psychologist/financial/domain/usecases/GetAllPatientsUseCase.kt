package com.psychologist.financial.domain.usecases

import com.psychologist.financial.data.repositories.PatientRepository
import com.psychologist.financial.domain.models.Patient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Use case: Get all patients with optional filtering
 *
 * Responsibilities:
 * - Retrieve patients from repository
 * - Apply business logic filtering (ACTIVE by default)
 * - Sort results appropriately
 * - Provide both synchronous and reactive interfaces
 *
 * Architecture:
 * - Use case layer: Business logic independent of UI
 * - Orchestrates repository calls
 * - Applies domain rules (e.g., default filter ACTIVE)
 * - ViewModels call use cases, not repositories directly
 *
 * Usage:
 * ```kotlin
 * val useCase = GetAllPatientsUseCase(patientRepository)
 *
 * // Synchronous (call from coroutine)
 * val patients = useCase.execute(includeInactive = false)
 *
 * // Reactive (for UI binding)
 * useCase.executeFlow(includeInactive = false).collect { patients ->
 *     updateUI(patients)
 * }
 *
 * // Include archived patients
 * val all = useCase.execute(includeInactive = true)
 * ```
 *
 * @property patientRepository PatientRepository for data access
 */
class GetAllPatientsUseCase(
    private val patientRepository: PatientRepository
) {
    /**
     * Get patients (optionally include inactive)
     *
     * Default behavior: returns only ACTIVE patients.
     * Useful for patient list screen.
     *
     * If includeInactive = true: returns all patients (ACTIVE + INACTIVE).
     * Useful for admin/archive views.
     *
     * Results are sorted by patient name.
     *
     * @param includeInactive Whether to include INACTIVE patients
     * @return List of Patient ordered by name
     *
     * Example:
     * ```kotlin
     * // Get only active patients (default)
     * val active = useCase.execute()
     *
     * // Get all patients including archived
     * val all = useCase.execute(includeInactive = true)
     * ```
     */
    suspend fun execute(includeInactive: Boolean = false): List<Patient> {
        return if (includeInactive) {
            patientRepository.getAllPatients()
        } else {
            patientRepository.getActivePatients()
        }
    }

    /**
     * Get patients reactively (Flow)
     *
     * Returns Flow that emits new list when any patient changes.
     * Useful for reactive UI that updates automatically.
     *
     * @param includeInactive Whether to include INACTIVE patients
     * @return Flow<List<Patient>> emitting updates on changes
     *
     * Example:
     * ```kotlin
     * useCase.executeFlow().collect { patients ->
     *     updatePatientListUI(patients)
     * }
     * ```
     */
    fun executeFlow(includeInactive: Boolean = false): Flow<List<Patient>> {
        return if (includeInactive) {
            patientRepository.getAllPatients().toFlow()
        } else {
            patientRepository.getActivePatientsFlow()
        }
    }

    /**
     * Get only active patients
     *
     * Shorthand for execute(includeInactive = false).
     *
     * @return List of ACTIVE Patient
     *
     * Example:
     * ```kotlin
     * val active = useCase.getActiveOnly()
     * ```
     */
    suspend fun getActiveOnly(): List<Patient> {
        return patientRepository.getActivePatients()
    }

    /**
     * Observe only active patients (Flow)
     *
     * Shorthand for executeFlow(includeInactive = false).
     *
     * @return Flow<List<Patient>> of active patients
     *
     * Example:
     * ```kotlin
     * useCase.observeActive().collect { patients ->
     *     updateUI(patients)
     * }
     * ```
     */
    fun observeActive(): Flow<List<Patient>> {
        return patientRepository.getActivePatientsFlow()
    }

    /**
     * Get only inactive patients
     *
     * Useful for archive/restore screens.
     *
     * @return List of INACTIVE Patient
     *
     * Example:
     * ```kotlin
     * val archived = useCase.getInactiveOnly()
     * ```
     */
    suspend fun getInactiveOnly(): List<Patient> {
        return patientRepository.getInactivePatients()
    }

    /**
     * Get patient count
     *
     * Returns count of active patients.
     *
     * @param includeInactive If true, count all patients; else count active only
     * @return Patient count
     *
     * Example:
     * ```kotlin
     * val count = useCase.getCount()  // Active count
     * val total = useCase.getCount(includeInactive = true)  // All
     * ```
     */
    suspend fun getCount(includeInactive: Boolean = false): Int {
        return if (includeInactive) {
            patientRepository.countAllPatients()
        } else {
            patientRepository.countActivePatients()
        }
    }

    /**
     * Check if any patients exist
     *
     * @param includeInactive If true, check all; else check active only
     * @return true if at least one patient exists
     *
     * Example:
     * ```kotlin
     * if (useCase.hasPatients()) {
     *     showPatientList()
     * } else {
     *     showEmptyState()
     * }
     * ```
     */
    suspend fun hasPatients(includeInactive: Boolean = false): Boolean {
        return getCount(includeInactive) > 0
    }

    /**
     * Get patients with filtering and sorting
     *
     * Advanced version with custom filters.
     *
     * @param includeInactive Whether to include INACTIVE patients
     * @param sortBy How to sort results: "name" (default) or "recent"
     * @return List of filtered and sorted Patient
     *
     * Example:
     * ```kotlin
     * val sorted = useCase.getWithOptions(sortBy = "recent")
     * ```
     */
    suspend fun getWithOptions(
        includeInactive: Boolean = false,
        sortBy: String = "name"
    ): List<Patient> {
        val patients = execute(includeInactive)

        return when (sortBy) {
            "recent" -> patients.sortedByDescending { it.lastAppointmentDate }
            "name" -> patients.sortedBy { it.name }
            else -> patients
        }
    }

    companion object {
        /**
         * Convenience extension to convert List to Flow
         *
         * Creates a Flow that emits the list once.
         * Used for compatibility between sync and async APIs.
         */
        private fun <T> List<T>.toFlow(): Flow<List<T>> {
            return kotlinx.coroutines.flow.flowOf(this)
        }
    }
}
