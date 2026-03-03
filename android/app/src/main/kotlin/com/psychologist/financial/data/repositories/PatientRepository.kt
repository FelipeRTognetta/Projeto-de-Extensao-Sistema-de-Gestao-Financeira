package com.psychologist.financial.data.repositories

import com.psychologist.financial.data.database.AppDatabase
import com.psychologist.financial.data.database.PatientDao
import com.psychologist.financial.data.entities.PatientEntity
import com.psychologist.financial.domain.models.Patient
import com.psychologist.financial.domain.models.PatientStatus
import com.psychologist.financial.utils.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * Repository for Patient data access
 *
 * Responsibilities:
 * - Mapping between database (PatientEntity) and domain (Patient) layers
 * - Implementing business logic for patient operations
 * - Coordinating with PatientDao for database operations
 * - Handling soft delete via status field
 * - Validating uniqueness constraints before insert/update
 *
 * Architecture:
 * - Extends BaseRepository for transaction management
 * - Uses Coroutines for non-blocking database operations
 * - Provides both synchronous and reactive (Flow) queries
 * - Clean separation from UI layer
 *
 * Usage:
 * ```kotlin
 * val repository = PatientRepository(database)
 *
 * // CRUD Operations
 * val patientId = repository.createPatient(patient)
 * val patient = repository.getPatient(patientId)
 * val allPatients = repository.getAllPatients()
 * repository.updatePatient(patient)
 * repository.deletePatient(patientId)  // Soft delete
 *
 * // Queries
 * val active = repository.getActivePatients()
 * val search = repository.searchPatients("João")
 *
 * // Reactive
 * repository.getPatientsFlow().collect { patients ->
 *     updateUI(patients)
 * }
 * ```
 *
 * @property database AppDatabase instance
 */
class PatientRepository(database: AppDatabase) : BaseRepository(database) {

    private companion object {
        private const val TAG = "PatientRepository"
    }

    private val patientDao: PatientDao = database.patientDao()

    // ========================================
    // CREATE Operations
    // ========================================

    /**
     * Create a new patient
     *
     * Validates uniqueness of phone/email before insertion.
     * Returns generated ID on success.
     *
     * @param patient Patient domain model to create
     * @return Generated patient ID
     * @throws IllegalArgumentException if validation fails
     *
     * Validation:
     * - Phone not already in use (if provided)
     * - Email not already in use (if provided)
     * - At least one contact method (phone or email)
     *
     * Example:
     * ```kotlin
     * val patient = Patient(
     *     id = 0,
     *     name = "João Silva",
     *     phone = "(11) 99999-9999",
     *     email = "joao@example.com",
     *     initialConsultDate = LocalDate.now(),
     *     registrationDate = LocalDate.now()
     * )
     * val patientId = repository.createPatient(patient)
     * ```
     */
    suspend fun createPatient(patient: Patient): Long {
        return withTransaction {
            // Validate contact info
            if (!patient.hasContactInfo()) {
                throw IllegalArgumentException("Patient must have phone or email")
            }

            // Check phone uniqueness
            if (!patient.phone.isNullOrEmpty()) {
                if (patientDao.isPhoneInUse(patient.phone, excludePatientId = 0)) {
                    throw IllegalArgumentException("Phone already in use: ${patient.phone}")
                }
            }

            // Check email uniqueness
            if (!patient.email.isNullOrEmpty()) {
                if (patientDao.isEmailInUse(patient.email, excludePatientId = 0)) {
                    throw IllegalArgumentException("Email already in use: ${patient.email}")
                }
            }

            // Convert domain model to entity and insert
            val entity = patient.toEntity()
            val generatedId = patientDao.insert(entity)
            AppLogger.d(TAG, "Patient created: id=$generatedId, name=${patient.name}")
            generatedId
        }
    }

    // ========================================
    // READ Operations
    // ========================================

    /**
     * Get patient by ID
     *
     * Returns null if not found.
     * Converts PatientEntity to Patient domain model.
     *
     * @param id Patient ID
     * @return Patient or null if not found
     *
     * Example:
     * ```kotlin
     * val patient = repository.getPatient(123)
     * ```
     */
    suspend fun getPatient(id: Long): Patient? {
        return withRead {
            patientDao.getPatient(id)?.toPatient()
        }
    }

    /**
     * Observe patient changes (reactive)
     *
     * Returns Flow that emits updates when patient changes.
     * Converts PatientEntity to Patient on each emission.
     *
     * @param id Patient ID
     * @return Flow<Patient?> emitting updates
     *
     * Example:
     * ```kotlin
     * repository.getPatientFlow(123).collect { patient ->
     *     updateUI(patient)
     * }
     * ```
     */
    fun getPatientFlow(id: Long): Flow<Patient?> {
        return patientDao.getPatientFlow(id).map { entity ->
            entity?.toPatient()
        }
    }

    /**
     * Get all patients (active and inactive)
     *
     * Returns all patients in name order.
     * For UI, use getActivePatients() instead.
     *
     * @return List of all Patient
     *
     * Example:
     * ```kotlin
     * val allPatients = repository.getAllPatients()
     * ```
     */
    suspend fun getAllPatients(): List<Patient> {
        return withRead {
            patientDao.getAllPatients().map { it.toPatient() }
        }
    }

    /**
     * Get all active patients
     *
     * Most common query - returns only ACTIVE patients.
     * Used by patient list screen.
     *
     * @return List of active Patient
     *
     * Example:
     * ```kotlin
     * val activePatients = repository.getActivePatients()
     * ```
     */
    suspend fun getActivePatients(): List<Patient> {
        return withRead {
            patientDao.getAllActivePatients().map { it.toPatient() }
        }
    }

    /**
     * Observe all active patients (reactive)
     *
     * Returns Flow that emits entire list when any patient changes.
     * Useful for reactive UI.
     *
     * @return Flow<List<Patient>> of active patients
     *
     * Example:
     * ```kotlin
     * repository.getActivePatientsFlow().collect { patients ->
     *     updatePatientList(patients)
     * }
     * ```
     */
    fun getActivePatientsFlow(): Flow<List<Patient>> {
        return patientDao.getAllActivePatientsFlow().map { entities ->
            entities.map { it.toPatient() }
        }
    }

    /**
     * Get all inactive patients
     *
     * Useful for archive screen.
     *
     * @return List of inactive Patient
     *
     * Example:
     * ```kotlin
     * val inactive = repository.getInactivePatients()
     * ```
     */
    suspend fun getInactivePatients(): List<Patient> {
        return withRead {
            patientDao.getAllInactivePatients().map { it.toPatient() }
        }
    }

    /**
     * Search patients by name
     *
     * Case-insensitive partial matching.
     * Only searches ACTIVE patients.
     *
     * @param searchTerm Name to search (e.g., "joão" or "silva")
     * @return List of matching Patient
     *
     * Example:
     * ```kotlin
     * val results = repository.searchPatients("sil")
     * // Returns: João Silva, Maria Silva, etc.
     * ```
     */
    suspend fun searchPatients(searchTerm: String): List<Patient> {
        return withRead {
            val pattern = "%$searchTerm%"
            patientDao.searchPatientsByName(pattern).map { it.toPatient() }
        }
    }

    /**
     * Get patient by phone
     *
     * Phone is unique - returns 0 or 1 patient.
     *
     * @param phone Phone number
     * @return Patient or null if not found
     *
     * Example:
     * ```kotlin
     * val patient = repository.getPatientByPhone("11999999999")
     * ```
     */
    suspend fun getPatientByPhone(phone: String): Patient? {
        return withRead {
            patientDao.getPatientByPhone(phone)?.toPatient()
        }
    }

    /**
     * Get patient by email
     *
     * Email is unique - returns 0 or 1 patient.
     *
     * @param email Email address
     * @return Patient or null if not found
     */
    suspend fun getPatientByEmail(email: String): Patient? {
        return withRead {
            patientDao.getPatientByEmail(email)?.toPatient()
        }
    }

    /**
     * Get recently active patients
     *
     * Sorted by most recent appointment.
     * Useful for dashboard.
     *
     * @param limit Max patients to return (default 10)
     * @return List of recently active Patient
     *
     * Example:
     * ```kotlin
     * val recent = repository.getRecentlyActive(limit = 5)
     * ```
     */
    suspend fun getRecentlyActive(limit: Int = 10): List<Patient> {
        return withRead {
            patientDao.getMostRecentlyActive(limit).map { it.toPatient() }
        }
    }

    /**
     * Count active patients
     *
     * @return Number of ACTIVE patients
     *
     * Example:
     * ```kotlin
     * val count = repository.countActivePatients()
     * ```
     */
    suspend fun countActivePatients(): Int {
        return withRead {
            patientDao.countActivePatients()
        }
    }

    /**
     * Count all patients
     *
     * @return Total number of all patients
     */
    suspend fun countAllPatients(): Int {
        return withRead {
            patientDao.countAllPatients()
        }
    }

    // ========================================
    // UPDATE Operations
    // ========================================

    /**
     * Update patient
     *
     * Updates all patient fields.
     * Validates phone/email uniqueness (excluding current patient).
     *
     * @param patient Patient to update (must have id > 0)
     * @throws IllegalArgumentException if validation fails
     *
     * Example:
     * ```kotlin
     * val updated = patient.copy(name = "New Name")
     * repository.updatePatient(updated)
     * ```
     */
    suspend fun updatePatient(patient: Patient) {
        return withTransaction {
            require(patient.id > 0) { "Patient must be saved (id > 0) to update" }

            // Check phone uniqueness (exclude self)
            if (!patient.phone.isNullOrEmpty()) {
                if (patientDao.isPhoneInUse(patient.phone, excludePatientId = patient.id)) {
                    throw IllegalArgumentException("Phone already in use: ${patient.phone}")
                }
            }

            // Check email uniqueness (exclude self)
            if (!patient.email.isNullOrEmpty()) {
                if (patientDao.isEmailInUse(patient.email, excludePatientId = patient.id)) {
                    throw IllegalArgumentException("Email already in use: ${patient.email}")
                }
            }

            val entity = patient.toEntity()
            patientDao.update(entity)
            AppLogger.d(TAG, "Patient updated: id=${patient.id}, name=${patient.name}")
        }
    }

    /**
     * Mark patient as inactive (soft delete)
     *
     * Sets status to INACTIVE without deleting data.
     * Preserves audit trail and relationships.
     *
     * @param patientId Patient ID to inactivate
     *
     * Example:
     * ```kotlin
     * repository.markAsInactive(123)
     * ```
     */
    suspend fun markAsInactive(patientId: Long) {
        return withWrite {
            patientDao.markAsInactive(patientId)
            AppLogger.d(TAG, "Patient marked inactive: id=$patientId")
        }
    }

    /**
     * Mark patient as active (reactivation)
     *
     * Sets status back to ACTIVE.
     *
     * @param patientId Patient ID to reactivate
     *
     * Example:
     * ```kotlin
     * repository.markAsActive(123)
     * ```
     */
    suspend fun markAsActive(patientId: Long) {
        return withWrite {
            patientDao.markAsActive(patientId)
            AppLogger.d(TAG, "Patient marked active: id=$patientId")
        }
    }

    /**
     * Update last appointment date
     *
     * Called when new appointment created.
     * Denormalizes appointment date to patient for efficient queries.
     *
     * @param patientId Patient ID
     * @param appointmentDate Appointment date
     *
     * Example:
     * ```kotlin
     * repository.updateLastAppointmentDate(123, LocalDate.now())
     * ```
     */
    suspend fun updateLastAppointmentDate(patientId: Long, appointmentDate: LocalDate) {
        return withWrite {
            patientDao.updateLastAppointmentDate(patientId, appointmentDate)
        }
    }

    // ========================================
    // DELETE Operations
    // ========================================

    /**
     * Delete patient (soft delete via status)
     *
     * Alias for markAsInactive().
     * Preferred over permanent deletion.
     *
     * @param patientId Patient ID to delete
     *
     * Example:
     * ```kotlin
     * repository.deletePatient(123)
     * ```
     */
    suspend fun deletePatient(patientId: Long) {
        markAsInactive(patientId)
    }

    /**
     * Permanently delete patient
     *
     * DESTRUCTIVE: Removes all patient data.
     * Use only for test cleanup or admin tools.
     * Prefer deletePatient() (soft delete) for production.
     *
     * @param patientId Patient ID to permanently delete
     *
     * WARNING: This deletes all related appointments and payments.
     *
     * Example:
     * ```kotlin
     * repository.permanentlyDeletePatient(123)
     * ```
     */
    suspend fun permanentlyDeletePatient(patientId: Long) {
        return withWrite {
            val patient = patientDao.getPatient(patientId)
            if (patient != null) {
                patientDao.delete(patient)
                AppLogger.w(TAG, "Patient permanently deleted: id=$patientId")
            }
        }
    }

    // ========================================
    // Conversion Methods
    // ========================================

    /**
     * Convert Patient domain model to PatientEntity
     *
     * @return PatientEntity for database operations
     */
    private fun Patient.toEntity(): PatientEntity {
        return PatientEntity(
            id = this.id,
            name = this.name,
            phone = this.phone,
            email = this.email,
            status = this.status.name,
            initialConsultDate = this.initialConsultDate,
            registrationDate = this.registrationDate,
            lastAppointmentDate = this.lastAppointmentDate,
            createdDate = this.createdDate
        )
    }

    /**
     * Convert PatientEntity to Patient domain model
     *
     * @return Patient for business logic
     */
    private fun PatientEntity.toPatient(): Patient {
        return Patient(
            id = this.id,
            name = this.name,
            phone = this.phone,
            email = this.email,
            status = PatientStatus.valueOf(this.status),
            initialConsultDate = this.initialConsultDate,
            registrationDate = this.registrationDate,
            lastAppointmentDate = this.lastAppointmentDate,
            createdDate = this.createdDate
        )
    }
}
