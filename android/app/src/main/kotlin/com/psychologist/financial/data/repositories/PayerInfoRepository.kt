package com.psychologist.financial.data.repositories

import com.psychologist.financial.data.database.AppDatabase
import com.psychologist.financial.data.entities.PayerInfoEntity
import com.psychologist.financial.domain.models.PayerInfo
import com.psychologist.financial.utils.AppLogger

/**
 * Repository for Responsável Financeiro (Payer Information) data access.
 *
 * Responsibilities:
 * - Mapping between PayerInfoEntity (Room) and PayerInfo (domain model)
 * - Providing save (insert-or-replace), remove, and get operations
 * - Upsert pattern: delete old record then insert new (avoids UNIQUE conflict)
 *
 * Architecture:
 * - Extends BaseRepository for transaction management
 * - Clean separation from UI layer
 * - Each operation runs inside a transaction for atomicity
 *
 * Usage:
 * ```kotlin
 * val repo = PayerInfoRepository(database)
 *
 * // Save or update payer for a patient
 * repo.savePayerInfo(patientId, payerInfo)
 *
 * // Retrieve payer for a patient
 * val payer = repo.getPayerInfoByPatientId(patientId)
 *
 * // Remove payer when patient is re-marked as paying
 * repo.removePayerInfo(patientId)
 * ```
 *
 * @property database AppDatabase instance
 */
class PayerInfoRepository(database: AppDatabase) : BaseRepository(database) {

    private companion object {
        private const val TAG = "PayerInfoRepository"
    }

    private val payerInfoDao = database.payerInfoDao()

    /**
     * Save (insert or replace) the payer info for a patient.
     *
     * Implements upsert by deleting any existing record for the patientId
     * before inserting the new one. Both operations run in a single transaction.
     *
     * @param patientId The ID of the non-paying patient
     * @param payerInfo The payer information to persist
     */
    suspend fun savePayerInfo(patientId: Long, payerInfo: PayerInfo) {
        withTransaction {
            // Delete existing record to avoid UNIQUE constraint conflict
            payerInfoDao.deleteByPatientId(patientId)
            // Insert new record
            payerInfoDao.insert(payerInfo.toEntity(patientId))
            AppLogger.d(TAG, "PayerInfo saved for patientId=$patientId, nome=${payerInfo.nome}")
        }
    }

    /**
     * Remove the payer info associated with the given patient.
     *
     * No-op if no record exists for the patientId.
     *
     * @param patientId The ID of the patient whose payer record should be removed
     */
    suspend fun removePayerInfo(patientId: Long) {
        withTransaction {
            payerInfoDao.deleteByPatientId(patientId)
            AppLogger.d(TAG, "PayerInfo removed for patientId=$patientId")
        }
    }

    /**
     * Get the payer info for a patient (one-shot).
     *
     * @param patientId The patient ID
     * @return PayerInfo domain model, or null if no payer record exists
     */
    suspend fun getPayerInfoByPatientId(patientId: Long): PayerInfo? {
        return withRead {
            payerInfoDao.getByPatientId(patientId)?.toDomain()
        }
    }

    // ========================================
    // Conversion Methods
    // ========================================

    private fun PayerInfo.toEntity(patientId: Long): PayerInfoEntity = PayerInfoEntity(
        id = if (this.id > 0) this.id else 0,
        patientId = patientId,
        nome = this.nome.trim(),
        cpf = this.cpf?.filter { it.isDigit() }?.ifEmpty { null },
        endereco = this.endereco?.trim()?.ifBlank { null },
        email = this.email?.trim()?.ifBlank { null },
        telefone = this.telefone?.trim()?.ifBlank { null }
    )

    private fun PayerInfoEntity.toDomain(): PayerInfo = PayerInfo(
        id = this.id,
        patientId = this.patientId,
        nome = this.nome,
        cpf = this.cpf,
        endereco = this.endereco,
        email = this.email,
        telefone = this.telefone
    )
}
