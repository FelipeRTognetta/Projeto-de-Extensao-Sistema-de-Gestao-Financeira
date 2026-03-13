package com.psychologist.financial.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.psychologist.financial.data.entities.PatientEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Room Data Access Object (DAO) for Patient entity
 *
 * Responsibilities:
 * - CRUD operations (Create, Read, Update, Delete)
 * - Complex queries (filtering, sorting, relationships)
 * - Type-safe SQL abstraction
 * - Suspend function support for coroutines
 *
 * Architecture:
 * - Used by PatientRepository (data layer)
 * - Parameters are auto-escaped (SQL injection safe)
 * - Room handles type conversion via TypeConverters
 *
 * Transaction Behavior:
 * - Single operations (insert, update, delete) auto-committed
 * - Multiple operations wrapped in transaction via withTransaction()
 *
 * Flow Support:
 * - getPatientFlow() and similar methods return Flow<T>
 * - Automatically emit new values when data changes
 * - Useful for reactive UI (LiveData, StateFlow)
 *
 * Usage:
 * ```kotlin
 * val dao = database.patientDao()
 *
 * // Insert
 * val id = dao.insert(patient)
 *
 * // Read
 * val patient = dao.getPatient(1)
 * val allPatients = dao.getAllPatients()
 *
 * // Update
 * dao.update(patient.copy(name = "New Name"))
 *
 * // Delete (soft delete via status)
 * dao.markAsInactive(patientId)
 *
 * // Observe changes
 * dao.getPatientFlow(1).collect { patient ->
 *     // React to patient changes
 * }
 * ```
 */
@Dao
interface PatientDao {

    // ========================================
    // CREATE Operations
    // ========================================

    /**
     * Insert a new patient
     *
     * If ID already exists, fails (no replace).
     * Auto-generates ID if provided as 0.
     *
     * @param patient PatientEntity to insert
     * @return Generated ID of inserted patient
     * @throws SQLiteConstraintException if unique constraint violated (duplicate phone/email)
     *
     * Example:
     * ```kotlin
     * val newPatient = PatientEntity(name = "John", phone = "123456789")
     * val generatedId = patientDao.insert(newPatient)
     * ```
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(patient: PatientEntity): Long

    /**
     * Insert multiple patients
     *
     * Atomically inserts all patients (all-or-nothing).
     * If any fails, entire operation rolled back.
     *
     * @param patients List of PatientEntity to insert
     * @return List of generated IDs in same order as input
     * @throws SQLiteConstraintException if any unique constraint violated
     *
     * Example:
     * ```kotlin
     * val patients = listOf(
     *     PatientEntity(name = "John"),
     *     PatientEntity(name = "Jane")
     * )
     * val ids = patientDao.insertAll(patients)
     * ```
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(patients: List<PatientEntity>): List<Long>

    // ========================================
    // READ Operations
    // ========================================

    /**
     * Get patient by ID
     *
     * Returns null if not found.
     * Non-blocking (coroutine suspend).
     *
     * @param id Patient ID
     * @return PatientEntity or null if not found
     *
     * Example:
     * ```kotlin
     * val patient = patientDao.getPatient(123)
     * ```
     */
    @Query("SELECT * FROM patient WHERE id = :id")
    suspend fun getPatient(id: Long): PatientEntity?

    /**
     * Observe patient changes (reactive)
     *
     * Returns Flow that emits updates when patient changes.
     * Useful for reactive UI that updates automatically.
     *
     * @param id Patient ID
     * @return Flow<PatientEntity> or null if not found
     *
     * Example:
     * ```kotlin
     * patientDao.getPatientFlow(123).collect { patient ->
     *     if (patient != null) {
     *         updateUI(patient)
     *     }
     * }
     * ```
     */
    @Query("SELECT * FROM patient WHERE id = :id")
    fun getPatientFlow(id: Long): Flow<PatientEntity?>

    /**
     * Get all patients (active and inactive)
     *
     * Returns all patients regardless of status.
     * For UI, use getAllActivePatients() instead.
     *
     * @return List of all PatientEntity
     *
     * Example:
     * ```kotlin
     * val allPatients = patientDao.getAllPatients()
     * ```
     */
    @Query("SELECT * FROM patient ORDER BY name ASC")
    suspend fun getAllPatients(): List<PatientEntity>

    /**
     * Get all active patients
     *
     * Returns only ACTIVE patients, sorted by name.
     * Most common query for UI (patient list screen).
     *
     * @return List of active PatientEntity
     *
     * Example:
     * ```kotlin
     * val activePatients = patientDao.getAllActivePatients()
     * ```
     */
    @Query(
        """
        SELECT * FROM patient
        WHERE status = 'ACTIVE'
        ORDER BY name ASC
        """
    )
    suspend fun getAllActivePatients(): List<PatientEntity>

    /**
     * Observe all active patients (reactive)
     *
     * Returns Flow that emits entire list whenever any patient changes.
     * Useful for reactive UI that updates on any change.
     *
     * @return Flow<List<PatientEntity>> of active patients
     *
     * Example:
     * ```kotlin
     * patientDao.getAllActivePatientsFlow().collect { patients ->
     *     updatePatientList(patients)
     * }
     * ```
     */
    @Query(
        """
        SELECT * FROM patient
        WHERE status = 'ACTIVE'
        ORDER BY name ASC
        """
    )
    fun getAllActivePatientsFlow(): Flow<List<PatientEntity>>

    /**
     * Get all inactive patients
     *
     * Returns only INACTIVE patients.
     * Useful for admin/archive screens.
     *
     * @return List of inactive PatientEntity
     */
    @Query(
        """
        SELECT * FROM patient
        WHERE status = 'INACTIVE'
        ORDER BY name ASC
        """
    )
    suspend fun getAllInactivePatients(): List<PatientEntity>

    /**
     * Search patients by name (case-insensitive)
     *
     * Returns ACTIVE patients matching name pattern.
     * Uses LIKE with wildcards for partial matching.
     *
     * @param searchTerm Name search term (e.g., "jo%" for names starting with "jo")
     * @return List of matching PatientEntity
     *
     * Example:
     * ```kotlin
     * val results = patientDao.searchPatientsByName("%silva%")
     * ```
     */
    @Query(
        """
        SELECT * FROM patient
        WHERE status = 'ACTIVE' AND REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(LOWER(name),'á','a'),'Á','a'),'à','a'),'À','a'),'â','a'),'Â','a'),'ã','a'),'Ã','a'),'é','e'),'É','e'),'ê','e'),'Ê','e'),'í','i'),'Í','i'),'ó','o'),'Ó','o'),'ô','o'),'Ô','o'),'õ','o'),'Õ','o'),'ú','u'),'Ú','u'),'ü','u'),'Ü','u'),'ç','c'),'Ç','c') LIKE :searchTerm
        ORDER BY name ASC
        """
    )
    suspend fun searchPatientsByName(searchTerm: String): List<PatientEntity>

    /**
     * Get a single page of patients with server-side name filter and status filter.
     *
     * Used for paginated patient list. Pass searchTerm = "%" to return all.
     * Pass includeInactive = true to include INACTIVE patients.
     * Results are ordered alphabetically by name for stable LIMIT/OFFSET pagination.
     *
     * @param searchTerm LIKE pattern for name filter (e.g. "%" or "%silva%")
     * @param includeInactive Whether to include INACTIVE patients
     * @param offset Row offset (page * PAGE_SIZE)
     * @param limit Max rows to return (PAGE_SIZE)
     * @return List of matching PatientEntity for this page
     */
    @Query(
        """
        SELECT * FROM patient
        WHERE REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(LOWER(name),'á','a'),'Á','a'),'à','a'),'À','a'),'â','a'),'Â','a'),'ã','a'),'Ã','a'),'é','e'),'É','e'),'ê','e'),'Ê','e'),'í','i'),'Í','i'),'ó','o'),'Ó','o'),'ô','o'),'Ô','o'),'õ','o'),'Õ','o'),'ú','u'),'Ú','u'),'ü','u'),'Ü','u'),'ç','c'),'Ç','c') LIKE :searchTerm
          AND (:includeInactive = 1 OR status = 'ACTIVE')
        ORDER BY name ASC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getPagedPatients(
        searchTerm: String,
        includeInactive: Boolean,
        offset: Int,
        limit: Int
    ): List<PatientEntity>

    /**
     * Get patient by phone number
     *
     * Phone is unique, so returns 0 or 1 patient.
     * Returns null if not found.
     *
     * @param phone Phone number to search
     * @return PatientEntity or null
     *
     * Example:
     * ```kotlin
     * val patient = patientDao.getPatientByPhone("11999999999")
     * ```
     */
    @Query("SELECT * FROM patient WHERE phone = :phone")
    suspend fun getPatientByPhone(phone: String): PatientEntity?

    /**
     * Get patient by email
     *
     * Email is unique, so returns 0 or 1 patient.
     * Returns null if not found.
     *
     * @param email Email to search
     * @return PatientEntity or null
     *
     * Example:
     * ```kotlin
     * val patient = patientDao.getPatientByEmail("joao@example.com")
     * ```
     */
    @Query("SELECT * FROM patient WHERE email = :email")
    suspend fun getPatientByEmail(email: String): PatientEntity?

    /**
     * Get patients registered between dates
     *
     * Useful for filtering by registration period.
     * Includes start and end dates (inclusive range).
     *
     * @param startDate Start date (inclusive)
     * @param endDate End date (inclusive)
     * @return List of PatientEntity registered in range
     *
     * Example:
     * ```kotlin
     * val thisMonth = patientDao.getPatientsByRegistrationDate(
     *     LocalDate.of(2026, 2, 1),
     *     LocalDate.of(2026, 2, 28)
     * )
     * ```
     */
    @Query(
        """
        SELECT * FROM patient
        WHERE registration_date >= :startDate AND registration_date <= :endDate
        ORDER BY registration_date DESC
        """
    )
    suspend fun getPatientsByRegistrationDate(startDate: LocalDate, endDate: LocalDate): List<PatientEntity>

    /**
     * Get most recently active patients
     *
     * Returns patients sorted by most recent appointment.
     * Null last_appointment_date sorts to end.
     * Useful for dashboard/activity view.
     *
     * @param limit Max number of patients to return
     * @return List of recently active PatientEntity
     *
     * Example:
     * ```kotlin
     * val recent = patientDao.getMostRecentlyActive(limit = 10)
     * ```
     */
    @Query(
        """
        SELECT * FROM patient
        WHERE status = 'ACTIVE'
        ORDER BY last_appointment_date DESC
        LIMIT :limit
        """
    )
    suspend fun getMostRecentlyActive(limit: Int = 10): List<PatientEntity>

    /**
     * Count active patients
     *
     * Returns number of ACTIVE patients.
     * Useful for dashboard metrics.
     *
     * @return Count of active patients
     *
     * Example:
     * ```kotlin
     * val count = patientDao.countActivePatients()
     * ```
     */
    @Query("SELECT COUNT(*) FROM patient WHERE status = 'ACTIVE'")
    suspend fun countActivePatients(): Int

    /**
     * Count all patients (including inactive)
     *
     * @return Total count of all patients
     */
    @Query("SELECT COUNT(*) FROM patient")
    suspend fun countAllPatients(): Int

    /**
     * Count patients by status
     *
     * @param status Patient status (ACTIVE or INACTIVE)
     * @return Count of patients with status
     */
    @Query("SELECT COUNT(*) FROM patient WHERE status = :status")
    suspend fun countByStatus(status: String): Int

    /**
     * Count patients by status as Flow (reactive)
     *
     * Updates automatically when patient status changes.
     *
     * @param status Patient status
     * @return Flow of patient count
     */
    @Query("SELECT COUNT(*) FROM patient WHERE status = :status")
    fun countByStatusFlow(status: String): Flow<Int>

    /**
     * Check if phone already exists
     *
     * Used for validation before insert/update.
     * Returns true if phone is in use (by different patient).
     *
     * @param phone Phone number to check
     * @param excludePatientId Exclude this patient ID from check (for updates)
     * @return true if phone already in use
     *
     * Example:
     * ```kotlin
     * val phoneExists = patientDao.isPhoneInUse("11999999999", excludePatientId = 1)
     * ```
     */
    @Query(
        """
        SELECT COUNT(*) > 0 FROM patient
        WHERE phone = :phone AND id != :excludePatientId
        """
    )
    suspend fun isPhoneInUse(phone: String, excludePatientId: Long = 0): Boolean

    /**
     * Check if email already exists
     *
     * Used for validation before insert/update.
     * Returns true if email is in use (by different patient).
     *
     * @param email Email to check
     * @param excludePatientId Exclude this patient ID from check (for updates)
     * @return true if email already in use
     *
     * Example:
     * ```kotlin
     * val emailExists = patientDao.isEmailInUse("joao@example.com", excludePatientId = 1)
     * ```
     */
    @Query(
        """
        SELECT COUNT(*) > 0 FROM patient
        WHERE email = :email AND id != :excludePatientId
        """
    )
    suspend fun isEmailInUse(email: String, excludePatientId: Long = 0): Boolean

    /**
     * Check if a CPF is already in use by another patient.
     *
     * Used for application-level uniqueness validation before INSERT/UPDATE,
     * complementing the partial unique index idx_patient_cpf in the database.
     *
     * @param cpf CPF to check (11 raw digits, no mask)
     * @param excludePatientId Exclude this patient's ID from the check (for updates)
     * @return true if the CPF is already registered to a different patient
     *
     * Example:
     * ```kotlin
     * val cpfExists = patientDao.isCpfInUse("12345678909", excludePatientId = currentId)
     * ```
     */
    @Query(
        """
        SELECT COUNT(*) > 0 FROM patient
        WHERE cpf = :cpf AND id != :excludePatientId
        """
    )
    suspend fun isCpfInUse(cpf: String, excludePatientId: Long = 0): Boolean

    // ========================================
    // UPDATE Operations
    // ========================================

    /**
     * Update patient
     *
     * Updates entire patient row.
     * ID must exist or fails silently (no error).
     *
     * @param patient PatientEntity with updates
     *
     * Example:
     * ```kotlin
     * val updated = patient.copy(name = "New Name")
     * patientDao.update(updated)
     * ```
     */
    @Update
    suspend fun update(patient: PatientEntity)

    /**
     * Update multiple patients
     *
     * Atomically updates all patients.
     *
     * @param patients List of PatientEntity to update
     */
    @Update
    suspend fun updateAll(patients: List<PatientEntity>)

    /**
     * Mark patient as inactive (soft delete)
     *
     * Updates status to INACTIVE without deleting data.
     * Used instead of actual deletion to preserve history.
     *
     * @param patientId Patient ID to inactivate
     *
     * Example:
     * ```kotlin
     * patientDao.markAsInactive(123)
     * ```
     */
    @Query("UPDATE patient SET status = 'INACTIVE' WHERE id = :patientId")
    suspend fun markAsInactive(patientId: Long)

    /**
     * Mark patient as active (reactivation)
     *
     * Updates status back to ACTIVE.
     *
     * @param patientId Patient ID to reactivate
     *
     * Example:
     * ```kotlin
     * patientDao.markAsActive(123)
     * ```
     */
    @Query("UPDATE patient SET status = 'ACTIVE' WHERE id = :patientId")
    suspend fun markAsActive(patientId: Long)

    /**
     * Update last appointment date
     *
     * Called when new appointment created.
     * Updates patient's last_appointment_date to appointment date.
     *
     * @param patientId Patient ID
     * @param appointmentDate New last appointment date
     *
     * Example:
     * ```kotlin
     * patientDao.updateLastAppointmentDate(1, LocalDate.now())
     * ```
     */
    @Query("UPDATE patient SET last_appointment_date = :appointmentDate WHERE id = :patientId")
    suspend fun updateLastAppointmentDate(patientId: Long, appointmentDate: LocalDate)

    // ========================================
    // DELETE Operations
    // ========================================

    /**
     * Delete patient (permanent deletion)
     *
     * DESTRUCTIVE: Permanently removes patient and all related data.
     * Prefer markAsInactive() for soft delete.
     * Called only via patientDao.delete() interface.
     *
     * @param patient PatientEntity to delete
     *
     * WARNING: This deletes all related appointments and payments (cascade).
     *
     * Example:
     * ```kotlin
     * patientDao.delete(patient)
     * ```
     */
    @Delete
    suspend fun delete(patient: PatientEntity)

    /**
     * Delete multiple patients (permanent deletion)
     *
     * DESTRUCTIVE: Permanently removes patients.
     * Prefer markAsInactive() for soft delete.
     *
     * @param patients List of PatientEntity to delete
     *
     * WARNING: This deletes all related appointments and payments (cascade).
     */
    @Delete
    suspend fun deleteAll(patients: List<PatientEntity>)

    /**
     * Clear all patients (for testing only)
     *
     * DESTRUCTIVE: Removes all patients.
     * Used only in unit tests for cleanup.
     *
     * Never call in production.
     */
    @Query("DELETE FROM patient")
    suspend fun deleteAll()
}
