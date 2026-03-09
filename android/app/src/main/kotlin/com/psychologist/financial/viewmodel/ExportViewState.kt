package com.psychologist.financial.viewmodel

import com.psychologist.financial.domain.models.ExportResult
import java.io.File
import java.time.YearMonth

/**
 * State classes for Export screens
 *
 * Sealed classes representing different UI states during data export.
 * Composables pattern match on state to render appropriate UI.
 *
 * State Management Strategy:
 * - Idle: No export in progress, ready for user action
 * - Validating: Checking prerequisites (storage, data)
 * - InProgress: Export executing with progress indication
 * - Success: Export completed successfully with file info
 * - Error: Export failed with error message
 *
 * Benefits:
 * - Type-safe state representation
 * - Forces handling all cases (sealed classes)
 * - Clear state transitions
 * - Easy to test
 * - Progress tracking during long operations
 *
 * Usage:
 * ```kotlin
 * val state = exportState.collectAsState().value
 *
 * when (state) {
 *     is ExportViewState.Idle -> ExportButton()
 *     is ExportViewState.Validating -> ValidationProgress()
 *     is ExportViewState.InProgress -> ExportProgress(state.progress)
 *     is ExportViewState.Success -> ExportSuccess(state.result)
 *     is ExportViewState.Error -> ErrorMessage(state.message)
 * }
 * ```
 */
sealed class ExportViewState {

    /**
     * Initial state: Ready for export
     *
     * UI shows export button and summary statistics.
     * No operation in progress.
     *
     * @property patientCount Number of patients to export
     * @property appointmentCount Number of appointments to export
     * @property paymentCount Number of payments to export
     * @property totalRecords Total number of records
     * @property availableStorageMB Available storage in MB
     */
    data class Idle(
        val patientCount: Int = 0,
        val appointmentCount: Int = 0,
        val paymentCount: Int = 0,
        val totalRecords: Int = 0,
        val availableStorageMB: Long = 0
    ) : ExportViewState() {
        /**
         * Check if there is data to export
         */
        fun hasData(): Boolean = totalRecords > 0

        /**
         * Get total records as formatted string
         */
        fun getTotalRecordsText(): String = "Total de registros: $totalRecords"
    }

    /**
     * Validating prerequisites before export
     *
     * Checking storage space, data availability, and prerequisites.
     * UI should show indeterminate progress indicator.
     *
     * @property message Validation message (e.g., "Verificando espaço em disco...")
     */
    data class Validating(
        val message: String = "Validando pré-requisitos..."
    ) : ExportViewState()

    /**
     * Export in progress with progress indication
     *
     * Exporting data to CSV files.
     * UI should show progress indicator with current status.
     *
     * @property currentStep Current step (e.g., "Exportando pacientes...")
     * @property patientsExported Number of patients exported so far
     * @property appointmentsExported Number of appointments exported
     * @property paymentsExported Number of payments exported
     * @property totalProgress Estimated progress (0-100)
     */
    data class InProgress(
        val currentStep: String = "Iniciando exportação...",
        val patientsExported: Int = 0,
        val appointmentsExported: Int = 0,
        val paymentsExported: Int = 0,
        val totalProgress: Int = 0
    ) : ExportViewState() {
        /**
         * Get status message with current counts
         */
        fun getStatusMessage(): String = buildString {
            append(currentStep)
            append("\n\n")
            append("Pacientes: $patientsExported\n")
            append("Atendimentos: $appointmentsExported\n")
            append("Pagamentos: $paymentsExported")
        }

        /**
         * Total records processed so far
         */
        fun getTotalProcessed(): Int =
            patientsExported + appointmentsExported + paymentsExported
    }

    /**
     * Export completed successfully
     *
     * All data exported to CSV files.
     * UI shows success message with file info and share options.
     *
     * @property result ExportResult with file paths and statistics
     * @property successMessage Summary message
     */
    data class Success(
        val result: ExportResult,
        val successMessage: String = "Exportação concluída com sucesso!"
    ) : ExportViewState() {
        /**
         * Check if export has files
         */
        fun hasFiles(): Boolean = result.getFiles().isNotEmpty()

        /**
         * Get export summary with all details
         */
        fun getSummary(): String = result.getSummary()

        /**
         * Get human-readable file size
         */
        fun getFileSizeMB(): Float = result.getTotalSizeMB()

        /**
         * Get export directory for file operations
         */
        fun getExportDirectory(): File? = result.exportDirectory

        /**
         * Get list of exported files
         */
        fun getExportedFiles(): List<File> = result.getFiles()

        /**
         * Get timestamp of export
         */
        fun getExportDate(): String =
            result.exportedAt.format(
                java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
            )
    }

    /**
     * Export failed with error
     *
     * Export operation encountered an error and was aborted.
     * UI shows error message with retry option.
     *
     * @property message Error message to display
     * @property exception Optional exception that caused the error
     */
    data class Error(
        val message: String,
        val exception: Exception? = null
    ) : ExportViewState() {
        /**
         * Get detailed error message for logging
         */
        fun getDetailedMessage(): String = buildString {
            append("Erro na exportação:\n")
            append(message)
            if (exception != null) {
                append("\n\nDetalhes: ")
                append(exception.message ?: "Erro desconhecido")
            }
        }
    }
}

/**
 * State for the financial CSV monthly export operation (US1).
 *
 * Transitions:
 *   Idle → InProgress → Success (file ready to share)
 *                     → Empty   (no payments in selected month)
 *                     → Error   (unexpected failure)
 */
sealed class FinanceiroCsvState {

    /** No export in progress. */
    object Idle : FinanceiroCsvState()

    /** Export is executing. */
    object InProgress : FinanceiroCsvState()

    /**
     * Export completed — CSV file is ready to share.
     *
     * @property file Generated CSV file
     * @property rowCount Number of payment rows written
     */
    data class Success(val file: File, val rowCount: Int) : FinanceiroCsvState()

    /**
     * Month has no payments — no file generated.
     *
     * UI should display an informational message instead of a share button.
     */
    object Empty : FinanceiroCsvState()

    /**
     * Export failed.
     *
     * @property message Human-readable error message in Portuguese
     */
    data class Error(val message: String) : FinanceiroCsvState()
}
