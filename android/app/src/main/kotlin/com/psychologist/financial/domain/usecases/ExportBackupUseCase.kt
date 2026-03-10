package com.psychologist.financial.domain.usecases

import com.psychologist.financial.data.repositories.ExportRepository
import com.psychologist.financial.domain.models.BackupResult
import com.psychologist.financial.services.BackupExportService
import com.psychologist.financial.services.FileStorageManager
import com.psychologist.financial.utils.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Export Backup Use Case
 *
 * Orchestrates the full database backup export:
 * 1. Validates password (≥ 6 chars, confirmation matches)
 * 2. Loads all entities from ExportRepository
 * 3. Serializes to JSON via BackupExportService
 * 4. Encrypts with PBKDF2+AES-256-GCM via BackupExportService
 * 5. Writes .pgfbackup file via FileStorageManager
 * 6. Returns BackupResult.ExportSuccess or Failure
 *
 * Password policy:
 * - Minimum 6 characters
 * - Must match confirmation
 *
 * @property repository Data access for all entities
 * @property backupExportService Serialization + encryption
 * @property storageManager File I/O
 */
class ExportBackupUseCase(
    private val repository: ExportRepository,
    private val backupExportService: BackupExportService,
    private val storageManager: FileStorageManager
) {

    private companion object {
        private const val TAG = "ExportBackupUseCase"
        private const val MIN_PASSWORD_LENGTH = 6
        private val fileTimestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss")
    }

    /**
     * Execute the backup export.
     *
     * @param password User's chosen password (min 6 chars)
     * @param passwordConfirmation Must match [password] exactly
     * @return [BackupResult.ExportSuccess] on success, [BackupResult.Failure] on any error
     */
    suspend fun execute(password: String, passwordConfirmation: String): BackupResult {
        // Validate password length
        if (password.length < MIN_PASSWORD_LENGTH) {
            return BackupResult.Failure(
                reason = BackupResult.FailureReason.PASSWORD_TOO_SHORT,
                message = "A senha deve ter pelo menos $MIN_PASSWORD_LENGTH caracteres."
            )
        }

        // Validate password confirmation
        if (password != passwordConfirmation) {
            return BackupResult.Failure(
                reason = BackupResult.FailureReason.PASSWORDS_DO_NOT_MATCH,
                message = "As senhas não coincidem. Por favor, confirme a senha corretamente."
            )
        }

        return withContext(Dispatchers.IO) {
            try {
                AppLogger.d(TAG, "Starting backup export...")

                // Load all entities
                val patients = repository.getAllPatients()
                val appointments = repository.getAllAppointments()
                val payments = repository.getAllPayments()
                val payerInfos = repository.getAllPayerInfos()
                val crossRefs = repository.getAllPaymentCrossRefs()

                AppLogger.d(TAG, "Loaded: ${patients.size} patients, ${appointments.size} appointments, " +
                    "${payments.size} payments, ${payerInfos.size} payerInfos, ${crossRefs.size} crossRefs")

                // Serialize to JSON
                val jsonBytes = backupExportService.serialize(
                    patients = patients,
                    appointments = appointments,
                    payments = payments,
                    paymentAppointments = crossRefs,
                    payerInfos = payerInfos
                )

                // Encrypt
                val encryptedBytes = backupExportService.encrypt(jsonBytes, password)

                // Write to file
                val exportDir = storageManager.getTimestampedExportDirectory()
                val timestamp = LocalDateTime.now().format(fileTimestampFormatter)
                val backupFile = File(exportDir, "backup_$timestamp.pgfbackup")
                backupFile.writeBytes(encryptedBytes)

                AppLogger.d(TAG, "Backup written to ${backupFile.absolutePath} (${encryptedBytes.size} bytes)")

                BackupResult.ExportSuccess(
                    file = backupFile,
                    patientCount = patients.size,
                    appointmentCount = appointments.size,
                    paymentCount = payments.size,
                    payerInfoCount = payerInfos.size
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "Backup export failed", e)
                BackupResult.Failure(
                    reason = BackupResult.FailureReason.STORAGE_ERROR,
                    message = "Erro ao exportar backup: ${e.message ?: "erro desconhecido"}"
                )
            }
        }
    }
}
