package com.psychologist.financial.domain.usecases

import android.content.ContentResolver
import android.net.Uri
import com.psychologist.financial.data.database.AppDatabase
import com.psychologist.financial.domain.models.BackupResult
import com.psychologist.financial.services.BackupImportService
import com.psychologist.financial.utils.AppLogger
import com.psychologist.financial.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Import Backup Use Case
 *
 * Orchestrates the full database restore from a `.pgfbackup` file:
 * 1. Reads file bytes from a content URI
 * 2. Decrypts with PBKDF2+AES-256-GCM via BackupImportService (wrong password → WRONG_PASSWORD)
 * 3. Parses JSON via BackupImportService (bad data → INVALID_FILE)
 * 4. Validates backup version (future version → INCOMPATIBLE_VERSION)
 * 5. Atomically restores all entities via BackupImportService.importAtomic
 * 6. Returns BackupResult.ImportSuccess with entity counts
 *
 * @property contentResolver  Android ContentResolver to read the file URI
 * @property database          AppDatabase (used to check version constant)
 * @property backupImportService Service that handles decrypt, parse, and atomic import
 */
class ImportBackupUseCase(
    private val contentResolver: ContentResolver,
    private val database: AppDatabase,
    private val backupImportService: BackupImportService
) {

    private companion object {
        private const val TAG = "ImportBackupUseCase"
    }

    /**
     * Execute the backup import.
     *
     * @param uri      Content URI pointing to the `.pgfbackup` file
     * @param password User-provided decryption password
     * @return [BackupResult.ImportSuccess] on success, [BackupResult.Failure] on any error
     */
    suspend fun execute(uri: Uri, password: String): BackupResult {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Read file bytes
                val fileBytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return@withContext BackupResult.Failure(
                        reason = BackupResult.FailureReason.INVALID_FILE,
                        message = "Não foi possível abrir o arquivo de backup. Verifique se o arquivo existe e tente novamente."
                    )

                AppLogger.d(TAG, "Read ${fileBytes.size} bytes from backup file")

                // 2. Decrypt
                val jsonBytes = try {
                    backupImportService.decrypt(fileBytes, password)
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Decryption failed — wrong password or corrupt file")
                    return@withContext BackupResult.Failure(
                        reason = BackupResult.FailureReason.WRONG_PASSWORD,
                        message = "Senha incorreta ou arquivo corrompido. Verifique a senha e tente novamente."
                    )
                }

                // 3. Parse
                val backupData = try {
                    backupImportService.parse(jsonBytes)
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Parsing failed — invalid backup format", e)
                    return@withContext BackupResult.Failure(
                        reason = BackupResult.FailureReason.INVALID_FILE,
                        message = "Arquivo de backup inválido ou corrompido. O formato não é reconhecido."
                    )
                }

                // 4. Validate version
                if (backupData.version > Constants.DATABASE_VERSION) {
                    AppLogger.w(TAG, "Incompatible backup version: ${backupData.version} > ${Constants.DATABASE_VERSION}")
                    return@withContext BackupResult.Failure(
                        reason = BackupResult.FailureReason.INCOMPATIBLE_VERSION,
                        message = "Este backup foi criado com uma versão mais recente do app (v${backupData.version}). " +
                            "Atualize o aplicativo para importar este backup."
                    )
                }

                // 5. Atomic import
                backupImportService.importAtomic(backupData)

                AppLogger.d(TAG, "Import complete: ${backupData.patients.size} patients, " +
                    "${backupData.appointments.size} appointments, ${backupData.payments.size} payments")

                // 6. Return success
                BackupResult.ImportSuccess(
                    patientCount = backupData.patients.size,
                    appointmentCount = backupData.appointments.size,
                    paymentCount = backupData.payments.size,
                    payerInfoCount = backupData.payerInfos.size
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "Unexpected error during import", e)
                BackupResult.Failure(
                    reason = BackupResult.FailureReason.IMPORT_FAILED,
                    message = "Erro ao importar backup: ${e.message ?: "erro desconhecido"}"
                )
            }
        }
    }
}
