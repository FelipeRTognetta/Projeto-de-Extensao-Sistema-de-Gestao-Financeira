package com.psychologist.financial.domain.models

import java.io.File
import java.time.LocalDateTime

/**
 * Export Result Data Model
 *
 * Encapsulates the result of a data export operation.
 * Includes file paths, export statistics, and status information.
 *
 * Features:
 * - Export success/failure status
 * - File paths for patients, appointments, payments
 * - Export timestamps and statistics
 * - Error messages for failures
 * - File size information
 * - Record count per data type
 *
 * Usage:
 * ```kotlin
 * val result = ExportResult(
 *     success = true,
 *     patientFile = File("patients.csv"),
 *     appointmentFile = File("appointments.csv"),
 *     paymentFile = File("payments.csv"),
 *     patientCount = 150,
 *     appointmentCount = 500,
 *     paymentCount = 1200,
 *     exportedAt = LocalDateTime.now()
 * )
 *
 * if (result.success) {
 *     showSuccess("Exported ${result.totalRecords} records")
 *     shareFiles(result.getFiles())
 * } else {
 *     showError(result.errorMessage)
 * }
 * ```
 */
data class ExportResult(
    /**
     * Export success status
     */
    val success: Boolean,

    /**
     * Path to exported patients CSV file
     */
    val patientFile: File? = null,

    /**
     * Path to exported appointments CSV file
     */
    val appointmentFile: File? = null,

    /**
     * Path to exported payments CSV file
     */
    val paymentFile: File? = null,

    /**
     * Number of patients exported
     */
    val patientCount: Int = 0,

    /**
     * Number of appointments exported
     */
    val appointmentCount: Int = 0,

    /**
     * Number of payments exported
     */
    val paymentCount: Int = 0,

    /**
     * Export timestamp
     */
    val exportedAt: LocalDateTime = LocalDateTime.now(),

    /**
     * Error message (if export failed)
     */
    val errorMessage: String? = null,

    /**
     * Export duration in seconds
     */
    val durationSeconds: Long = 0
) {
    /**
     * Total number of records exported
     *
     * @return Sum of patients, appointments, and payments
     */
    val totalRecords: Int
        get() = patientCount + appointmentCount + paymentCount

    /**
     * Export directory path
     *
     * @return Parent directory of exported files, or null if no files
     */
    val exportDirectory: File?
        get() = patientFile?.parentFile

    /**
     * Get all exported files as list
     *
     * @return List of non-null exported files
     */
    fun getFiles(): List<File> {
        return listOfNotNull(patientFile, appointmentFile, paymentFile)
    }

    /**
     * Get total exported size in bytes
     *
     * @return Sum of all file sizes
     */
    fun getTotalSizeBytes(): Long {
        return getFiles().sumOf { it.length() }
    }

    /**
     * Get total exported size in MB
     *
     * @return Size in megabytes (rounded)
     */
    fun getTotalSizeMB(): Float {
        return getTotalSizeBytes() / (1024f * 1024f)
    }

    /**
     * Get human-readable status message
     *
     * @return Status description in Portuguese
     */
    fun getStatusMessage(): String {
        return if (success) {
            "Exportação bem-sucedida: $totalRecords registros em ${getFiles().size} arquivos"
        } else {
            "Erro na exportação: $errorMessage"
        }
    }

    /**
     * Get detailed export summary
     *
     * @return Detailed summary with all statistics
     */
    fun getSummary(): String {
        return buildString {
            append("=== Resumo da Exportação ===\n")
            append("Status: ${if (success) "Sucesso" else "Falha"}\n")
            append("Data: $exportedAt\n")
            append("Duração: ${durationSeconds}s\n")
            append("\nRegistros:\n")
            append("  Pacientes: $patientCount\n")
            append("  Atendimentos: $appointmentCount\n")
            append("  Pagamentos: $paymentCount\n")
            append("  Total: $totalRecords\n")
            append("\nArquivos: ${getFiles().size}\n")
            getFiles().forEach { file ->
                append("  - ${file.name} (${file.length() / 1024}KB)\n")
            }
            if (errorMessage != null) {
                append("\nErro: $errorMessage\n")
            }
        }
    }

    /**
     * Sealed class for export status variants
     */
    sealed class Status {
        /**
         * Export in progress
         */
        object Exporting : Status()

        /**
         * Export completed successfully
         */
        data class Success(val result: ExportResult) : Status()

        /**
         * Export failed
         */
        data class Error(val message: String, val exception: Throwable? = null) : Status()
    }

    companion object {
        /**
         * Create successful export result
         *
         * @param patientFile Exported patients file
         * @param appointmentFile Exported appointments file
         * @param paymentFile Exported payments file
         * @param patientCount Number of patients
         * @param appointmentCount Number of appointments
         * @param paymentCount Number of payments
         * @param durationSeconds Export duration
         * @return Success result
         */
        fun success(
            patientFile: File,
            appointmentFile: File,
            paymentFile: File,
            patientCount: Int,
            appointmentCount: Int,
            paymentCount: Int,
            durationSeconds: Long = 0
        ): ExportResult {
            return ExportResult(
                success = true,
                patientFile = patientFile,
                appointmentFile = appointmentFile,
                paymentFile = paymentFile,
                patientCount = patientCount,
                appointmentCount = appointmentCount,
                paymentCount = paymentCount,
                durationSeconds = durationSeconds
            )
        }

        /**
         * Create failed export result
         *
         * @param errorMessage Error description
         * @return Failure result
         */
        fun failure(errorMessage: String): ExportResult {
            return ExportResult(
                success = false,
                errorMessage = errorMessage
            )
        }

        /**
         * Create failed export result from exception
         *
         * @param exception Exception that occurred
         * @return Failure result with exception message
         */
        fun failure(exception: Exception): ExportResult {
            return ExportResult(
                success = false,
                errorMessage = exception.message ?: "Erro desconhecido na exportação"
            )
        }
    }
}
