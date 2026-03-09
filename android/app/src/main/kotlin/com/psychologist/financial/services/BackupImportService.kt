package com.psychologist.financial.services

import com.psychologist.financial.data.database.AppDatabase
import com.psychologist.financial.data.entities.AppointmentEntity
import com.psychologist.financial.data.entities.PatientEntity
import com.psychologist.financial.data.entities.PayerInfoEntity
import com.psychologist.financial.data.entities.PaymentAppointmentCrossRef
import com.psychologist.financial.data.entities.PaymentEntity
import com.psychologist.financial.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Backup Import Service
 *
 * Handles decryption, deserialization, and atomic database restore from a `.pgfbackup` file.
 *
 * Responsibilities:
 * - Decrypt backup bytes using the same PBKDF2+AES-256-GCM envelope as BackupExportService
 * - Parse decrypted JSON into BackupData (kotlinx.serialization)
 * - Atomically restore all entities via database.runInTransaction:
 *   DELETE in FK-safe order (cross-refs → payments → appointments → payerInfos → patients)
 *   INSERT in reverse order (patients → payerInfos → appointments → payments → cross-refs)
 *
 * Usage:
 * ```kotlin
 * val service = BackupImportService(database)
 * val json    = service.decrypt(fileBytes, password)
 * val data    = service.parse(json)
 * service.importAtomic(data)
 * ```
 */
class BackupImportService(
    private val database: AppDatabase,
    private val exportService: BackupExportService = BackupExportService()
) {

    private companion object {
        private const val TAG = "BackupImportService"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // ========================================
    // Decryption
    // ========================================

    /**
     * Decrypt backup file bytes using PBKDF2+AES-256-GCM.
     *
     * Delegates to [BackupExportService.decrypt] which shares the same envelope format.
     * Throws an exception (AEADBadTagException wrapped) if the password is wrong.
     *
     * @param fileBytes Encrypted envelope bytes from `.pgfbackup` file
     * @param password  User-provided password
     * @return Decrypted UTF-8 JSON bytes
     * @throws Exception if password is wrong or data is corrupt
     */
    fun decrypt(fileBytes: ByteArray, password: String): ByteArray =
        exportService.decrypt(fileBytes, password)

    // ========================================
    // Parsing
    // ========================================

    /**
     * Deserialize decrypted JSON bytes into [BackupData].
     *
     * @param jsonBytes UTF-8 JSON bytes produced by [BackupExportService.serialize]
     * @return Parsed [BackupData]
     * @throws Exception if JSON is malformed or missing required fields
     */
    fun parse(jsonBytes: ByteArray): BackupData =
        json.decodeFromString(BackupData.serializer(), jsonBytes.toString(Charsets.UTF_8))

    // ========================================
    // Atomic Import
    // ========================================

    /**
     * Restore all entities from [BackupData] atomically.
     *
     * Runs inside [AppDatabase.runInTransaction] to guarantee full rollback on any failure.
     *
     * Delete order (FK-safe):
     *   payment_appointments → payments → appointments → payer_info → patient
     *
     * Insert order (reverse):
     *   patient → payer_info → appointments → payments → payment_appointments
     *
     * @param data Parsed backup data to restore
     * @throws Exception propagated from DAO if any insert/delete fails (triggers rollback)
     */
    suspend fun importAtomic(data: BackupData) = withContext(Dispatchers.IO) {
        AppLogger.d(TAG, "Starting atomic import: ${data.patients.size} patients, " +
            "${data.appointments.size} appointments, ${data.payments.size} payments")

        database.runInTransaction {
            runBlocking {
                val patientDao = database.patientDao()
                val appointmentDao = database.appointmentDao()
                val paymentDao = database.paymentDao()
                val payerInfoDao = database.payerInfoDao()

                // --- DELETE in FK-safe order ---
                paymentDao.deleteAllCrossRefs()
                paymentDao.deleteAllPayments()
                appointmentDao.deleteAll()
                payerInfoDao.deleteAll()
                patientDao.deleteAll()

                // --- INSERT in reverse order ---
                patientDao.insertAll(data.patients.map { it.toEntity() })
                payerInfoDao.insertAll(data.payerInfos.map { it.toEntity() })
                appointmentDao.insertAll(data.appointments.map { it.toEntity() })
                paymentDao.insertAll(data.payments.map { it.toEntity() })
                paymentDao.insertAllCrossRefs(
                    data.paymentAppointments.map { crossRef ->
                        PaymentAppointmentCrossRef(crossRef.paymentId, crossRef.appointmentId)
                    }
                )
            }
        }

        AppLogger.d(TAG, "Atomic import complete")
    }

    // ========================================
    // Entity Conversion Helpers
    // ========================================

    private fun PatientBackup.toEntity() = PatientEntity(
        id = id,
        name = name,
        phone = phone,
        email = email,
        status = status,
        initialConsultDate = LocalDate.parse(initialConsultDate),
        registrationDate = LocalDate.parse(registrationDate),
        lastAppointmentDate = lastAppointmentDate?.let { LocalDate.parse(it) },
        cpf = cpf,
        endereco = endereco,
        naoPagante = naoPagante,
        createdDate = LocalDateTime.parse(createdDate)
    )

    private fun AppointmentBackup.toEntity() = AppointmentEntity(
        id = id,
        patientId = patientId,
        date = LocalDate.parse(date),
        timeStart = LocalTime.parse(timeStart),
        durationMinutes = durationMinutes,
        notes = notes,
        createdDate = LocalDateTime.parse(createdDate)
    )

    private fun PaymentBackup.toEntity() = PaymentEntity(
        id = id,
        patientId = patientId,
        amount = BigDecimal(amount),
        paymentDate = LocalDate.parse(paymentDate),
        createdDate = LocalDateTime.parse(createdDate)
    )

    private fun PayerInfoBackup.toEntity() = PayerInfoEntity(
        id = id,
        patientId = patientId,
        nome = nome,
        cpf = cpf,
        email = email,
        telefone = telefone,
        endereco = endereco
    )
}
