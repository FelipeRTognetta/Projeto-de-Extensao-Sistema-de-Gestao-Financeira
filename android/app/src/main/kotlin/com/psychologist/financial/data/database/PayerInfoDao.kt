package com.psychologist.financial.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.psychologist.financial.data.entities.PayerInfoEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room Data Access Object (DAO) for PayerInfoEntity.
 *
 * Manages persistence of Responsável Financeiro records linked to non-paying patients.
 *
 * Relationship constraint: each patient_id is unique — enforced by the database index
 * idx_payer_info_patient_id. Inserting a second record for the same patient_id will fail.
 * Use deleteByPatientId() before inserting to replace an existing record.
 *
 * Usage:
 * ```kotlin
 * val dao = database.payerInfoDao()
 *
 * // Save payer for patient
 * dao.insert(PayerInfoEntity(patientId = 1, nome = "Maria Silva"))
 *
 * // Load payer for patient
 * val payer = dao.getByPatientId(1)
 *
 * // Remove payer when patient is re-marked as paying
 * dao.deleteByPatientId(1)
 * ```
 */
@Dao
interface PayerInfoDao {

    /**
     * Insert a new PayerInfo record.
     *
     * Will fail with SQLiteConstraintException if a record with the same
     * patient_id already exists (UNIQUE constraint on patient_id).
     * Call deleteByPatientId() first when replacing an existing record.
     *
     * @param entity PayerInfoEntity to insert
     * @return Generated row ID
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: PayerInfoEntity): Long

    /**
     * Update an existing PayerInfo record.
     *
     * The entity's id must match an existing record.
     *
     * @param entity PayerInfoEntity with updated values
     */
    @Update
    suspend fun update(entity: PayerInfoEntity)

    /**
     * Delete the PayerInfo record associated with the given patient.
     *
     * Called when a patient is re-marked as paying (naoPagante = false).
     * No-op if no record exists for patientId.
     *
     * @param patientId The patient whose payer record should be removed
     */
    @Query("DELETE FROM payer_info WHERE patient_id = :patientId")
    suspend fun deleteByPatientId(patientId: Long)

    /**
     * Get the PayerInfo for a patient (one-shot).
     *
     * Returns null if no payer record exists for the given patient.
     *
     * @param patientId The patient whose payer record to retrieve
     * @return PayerInfoEntity or null
     */
    @Query("SELECT * FROM payer_info WHERE patient_id = :patientId LIMIT 1")
    suspend fun getByPatientId(patientId: Long): PayerInfoEntity?

    /**
     * Observe the PayerInfo for a patient (reactive).
     *
     * Emits the current record and any subsequent changes.
     * Emits null when no record exists.
     *
     * @param patientId The patient whose payer record to observe
     * @return Flow emitting PayerInfoEntity or null
     */
    @Query("SELECT * FROM payer_info WHERE patient_id = :patientId LIMIT 1")
    fun getByPatientIdFlow(patientId: Long): Flow<PayerInfoEntity?>

    /**
     * Get all PayerInfo records (one-shot).
     *
     * Used for backup export and financial CSV generation.
     *
     * @return List of all PayerInfoEntity records ordered by id ASC
     */
    @Query("SELECT * FROM payer_info ORDER BY id ASC")
    suspend fun getAll(): List<PayerInfoEntity>
}
