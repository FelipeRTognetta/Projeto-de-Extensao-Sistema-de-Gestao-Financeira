package com.psychologist.financial.domain.models

import java.io.File

/**
 * Sealed class representing the outcome of a backup export or import operation.
 *
 * Usage:
 * ```kotlin
 * when (val result = exportBackupUseCase.execute(password, passwordConfirm)) {
 *     is BackupResult.ExportSuccess -> share(result.file)
 *     is BackupResult.ImportSuccess -> showSummary(result.getSummary())
 *     is BackupResult.Failure       -> showError(result.message)
 * }
 * ```
 */
sealed class BackupResult {

    /**
     * Backup file exported successfully.
     *
     * @property file The generated [File] with extension `.pgfbackup`
     * @property patientCount Number of patient records written
     * @property appointmentCount Number of appointment records written
     * @property paymentCount Number of payment records written
     * @property payerInfoCount Number of payer-info records written
     */
    data class ExportSuccess(
        val file: File,
        val patientCount: Int,
        val appointmentCount: Int,
        val paymentCount: Int,
        val payerInfoCount: Int
    ) : BackupResult() {
        val totalRecords: Int
            get() = patientCount + appointmentCount + paymentCount + payerInfoCount
    }

    /**
     * Backup file imported and all records restored successfully.
     *
     * @property patientCount Number of patient records restored
     * @property appointmentCount Number of appointment records restored
     * @property paymentCount Number of payment records restored
     * @property payerInfoCount Number of payer-info records restored
     */
    data class ImportSuccess(
        val patientCount: Int,
        val appointmentCount: Int,
        val paymentCount: Int,
        val payerInfoCount: Int
    ) : BackupResult() {
        val totalRecords: Int
            get() = patientCount + appointmentCount + paymentCount + payerInfoCount

        fun getSummary(): String =
            "$patientCount pacientes, $appointmentCount consultas, " +
            "$paymentCount pagamentos, $payerInfoCount responsáveis importados"
    }

    /**
     * The operation failed.
     *
     * @property reason Machine-readable failure category
     * @property message Human-readable error message (Portuguese)
     */
    data class Failure(
        val reason: FailureReason,
        val message: String
    ) : BackupResult()

    /** Categorises the cause of a backup/import failure. */
    enum class FailureReason {
        WRONG_PASSWORD,
        INVALID_FILE,
        INCOMPATIBLE_VERSION,
        STORAGE_ERROR,
        IMPORT_FAILED,
        PASSWORD_TOO_SHORT,
        PASSWORDS_DO_NOT_MATCH
    }
}
