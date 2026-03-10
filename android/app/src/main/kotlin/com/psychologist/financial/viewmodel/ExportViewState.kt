package com.psychologist.financial.viewmodel

import com.psychologist.financial.domain.models.BackupResult
import java.io.File

/**
 * Idle/ready state for the export screen.
 * Holds statistics displayed in the stats card and storage card.
 *
 * @property patientCount Number of patients
 * @property appointmentCount Number of appointments
 * @property paymentCount Number of payments
 * @property totalRecords Total number of records
 * @property availableStorageMB Available storage in MB
 */
data class ExportViewState(
    val patientCount: Int = 0,
    val appointmentCount: Int = 0,
    val paymentCount: Int = 0,
    val totalRecords: Int = 0,
    val availableStorageMB: Long = 0
)

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

/**
 * State for the backup export operation (US3).
 *
 * Transitions:
 *   Idle → InProgress → Success (file ready to share)
 *                     → Error   (unexpected failure)
 */
sealed class BackupExportState {

    /** No backup export in progress. */
    object Idle : BackupExportState()

    /** Backup export is executing. */
    object InProgress : BackupExportState()

    /**
     * Backup export completed — file is ready to share.
     *
     * @property result [BackupResult.ExportSuccess] with file and record counts
     */
    data class Success(val result: BackupResult.ExportSuccess) : BackupExportState()

    /**
     * Backup export failed.
     *
     * @property message Human-readable error message in Portuguese
     */
    data class Error(val message: String) : BackupExportState()
}

/**
 * State for the backup import operation (US4).
 *
 * Transitions:
 *   Idle → AwaitingConfirmation (file + password provided, waiting for user to confirm)
 *        → InProgress           (user confirmed, import running)
 *        → Success              (import done, records restored)
 *        → Error                (failure at any step)
 *   AwaitingConfirmation → InProgress | Idle (user confirms or cancels)
 */
sealed class BackupImportState {

    /** No import in progress. */
    object Idle : BackupImportState()

    /**
     * File and password are ready — waiting for user confirmation.
     * UI shows AlertDialog warning that existing data will be overwritten.
     */
    object AwaitingConfirmation : BackupImportState()

    /** Import is executing. */
    object InProgress : BackupImportState()

    /**
     * Import completed — all records restored.
     *
     * @property result [BackupResult.ImportSuccess] with entity counts
     */
    data class Success(val result: BackupResult.ImportSuccess) : BackupImportState()

    /**
     * Import failed.
     *
     * @property message Human-readable error message in Portuguese
     */
    data class Error(val message: String) : BackupImportState()
}
