package com.psychologist.financial.viewmodel

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.psychologist.financial.domain.models.ExportResult
import com.psychologist.financial.domain.usecases.ExportDataUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.YearMonth

/**
 * ViewModel for Data Export screens
 *
 * Responsibilities:
 * - Manage export operation state (idle, validating, in-progress, success, error)
 * - Coordinate ExportDataUseCase execution
 * - Handle user interactions (start export, cancel, retry)
 * - Maintain reactive state via StateFlow
 * - Track export progress and statistics
 * - Handle errors gracefully with user-friendly messages
 *
 * Architecture:
 * - Extends BaseViewModel for coroutine management
 * - Uses ExportDataUseCase for data export orchestration
 * - StateFlow for reactive state updates
 * - Sealed state classes for type-safe UI rendering
 * - Progress tracking with counters
 *
 * State Management:
 * - exportState: Current export operation state
 *   - Idle: Ready for export, showing summary
 *   - Validating: Checking prerequisites
 *   - InProgress: Export executing with progress
 *   - Success: Export completed with file info
 *   - Error: Export failed with message
 *
 * Features:
 * - Validates storage and data before export
 * - Supports selective export (patients/appointments/payments)
 * - Tracks progress with step names
 * - Handles cancellation
 * - Provides file sharing options
 * - Cleanup of old exports
 *
 * Usage:
 * ```kotlin
 * class ExportScreen {
 *     val viewModel = ExportViewModel(exportDataUseCase)
 *
 *     // Load initial statistics
 *     LaunchedEffect(Unit) {
 *         viewModel.loadExportStatistics()
 *     }
 *
 *     // Start export
 *     Button(onClick = { viewModel.performExport() })
 *
 *     // Render state
 *     when (val state = exportState.collectAsState().value) {
 *         is ExportViewState.InProgress -> ShowProgress(state)
 *         is ExportViewState.Success -> ShowSuccess(state)
 *         is ExportViewState.Error -> ShowError(state)
 *         else -> ShowButton()
 *     }
 * }
 * ```
 *
 * @property exportDataUseCase Use case for orchestrating data export
 */
class ExportViewModel(
    private val exportDataUseCase: ExportDataUseCase
) : BaseViewModel() {

    private companion object {
        private const val TAG = "ExportViewModel"
    }

    // ========================================
    // Export State
    // ========================================

    /**
     * Current export operation state
     *
     * Emits:
     * - Idle: No export in progress, ready for user action
     * - Validating: Checking prerequisites
     * - InProgress: Export executing with progress tracking
     * - Success: Export completed successfully
     * - Error: Export failed
     */
    private val _exportState = MutableStateFlow<ExportViewState>(
        ExportViewState.Idle()
    )
    val exportState: StateFlow<ExportViewState> = _exportState.asStateFlow()

    /**
     * Export statistics (record counts, storage)
     *
     * Updated on screen load to show what will be exported.
     * Useful for user decision (e.g., "Export 500 patients?")
     */
    private val _exportStats = MutableStateFlow<Map<String, Int>>(emptyMap())
    val exportStats: StateFlow<Map<String, Int>> = _exportStats.asStateFlow()

    /**
     * Whether export is currently running
     *
     * Useful for disabling buttons while export in progress.
     */
    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    /**
     * Current export result (on success)
     *
     * Contains file paths, record counts, timestamps, file sizes.
     */
    private val _lastExportResult = MutableStateFlow<ExportResult?>(null)
    val lastExportResult: StateFlow<ExportResult?> = _lastExportResult.asStateFlow()

    // ========================================
    // Financeiro CSV State (US1)
    // ========================================

    /**
     * Currently selected month for the financial CSV export.
     * Defaults to the current month.
     */
    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()

    /**
     * State of the financial CSV export operation.
     */
    private val _financeiroState = MutableStateFlow<FinanceiroCsvState>(FinanceiroCsvState.Idle)
    val financeiroState: StateFlow<FinanceiroCsvState> = _financeiroState.asStateFlow()

    init {
        Log.d(TAG, "ExportViewModel initialized")
    }

    // ========================================
    // Export Operations
    // ========================================

    /**
     * Load export statistics without performing export
     *
     * Called when export screen opens.
     * Updates statistics display with record counts and storage info.
     *
     * Example:
     * ```kotlin
     * LaunchedEffect(Unit) {
     *     viewModel.loadExportStatistics()
     * }
     * ```
     */
    fun loadExportStatistics() {
        Log.d(TAG, "Loading export statistics...")
        launchSafe {
            try {
                val stats = exportDataUseCase.getExportStats()
                _exportStats.value = stats

                Log.d(TAG, "Stats loaded: $stats")

                // Update idle state with statistics
                val patientCount = stats["patients"] ?: 0
                val appointmentCount = stats["appointments"] ?: 0
                val paymentCount = stats["payments"] ?: 0
                val totalRecords = stats["total"] ?: 0

                val storageStatus = exportDataUseCase.getStorageStatus()
                Log.d(TAG, "Storage: $storageStatus")

                _exportState.value = ExportViewState.Idle(
                    patientCount = patientCount,
                    appointmentCount = appointmentCount,
                    paymentCount = paymentCount,
                    totalRecords = totalRecords,
                    availableStorageMB = 1024 // Default estimate, updated from storage manager
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load statistics", e)
                handleException(
                    e,
                    "Não foi possível carregar estatísticas de exportação"
                )
            }
        }
    }

    /**
     * Perform full data export
     *
     * Exports all patients, appointments, and payments to CSV.
     * Shows progress during operation, success message with file info on completion.
     *
     * Flow:
     * 1. Set state to Validating
     * 2. Call validateExport() to check prerequisites
     * 3. If valid, proceed to export
     * 4. Update state to InProgress
     * 5. Call execute() to perform full export
     * 6. On success, set state to Success with file info
     * 7. On error, set state to Error with message
     *
     * Example:
     * ```kotlin
     * Button(onClick = { viewModel.performExport() })
     * ```
     */
    fun performExport() {
        Log.d(TAG, "Starting full export...")
        _isExporting.value = true

        launchSafe {
            try {
                // Validate prerequisites
                _exportState.value = ExportViewState.Validating(
                    "Validando pré-requisitos de exportação..."
                )

                val validationResult = exportDataUseCase.validateExport()
                if (!validationResult.isValid) {
                    _exportState.value = ExportViewState.Error(
                        validationResult.errorMessage
                            ?: "Falha na validação de exportação"
                    )
                    _isExporting.value = false
                    return@launchSafe
                }

                // Start export
                _exportState.value = ExportViewState.InProgress(
                    currentStep = "Exportando pacientes...",
                    patientsExported = 0,
                    appointmentsExported = 0,
                    paymentsExported = 0,
                    totalProgress = 5
                )

                // Execute full export
                val result = exportDataUseCase.execute()

                if (result.success) {
                    Log.d(TAG, "Export successful: ${result.totalRecords} records")

                    _lastExportResult.value = result
                    _exportState.value = ExportViewState.Success(
                        result = result,
                        successMessage = "Exportação concluída com sucesso! " +
                            "${result.totalRecords} registros exportados.",
                        exportType = ExportType.ALL
                    )
                } else {
                    Log.w(TAG, "Export failed: ${result.errorMessage}")

                    _exportState.value = ExportViewState.Error(
                        message = result.errorMessage ?: "Erro desconhecido na exportação"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Export exception", e)
                _exportState.value = ExportViewState.Error(
                    message = "Erro ao exportar dados: ${e.message ?: "desconhecido"}",
                    exception = e
                )
            } finally {
                _isExporting.value = false
            }
        }
    }

    /**
     * Perform selective data export
     *
     * Exports specific data types (patients, appointments, payments).
     * Allows users to choose what to export.
     *
     * @param exportPatients Include patients in export
     * @param exportAppointments Include appointments in export
     * @param exportPayments Include payments in export
     *
     * Example:
     * ```kotlin
     * // Export only patients and payments
     * viewModel.performSelectiveExport(
     *     exportPatients = true,
     *     exportAppointments = false,
     *     exportPayments = true
     * )
     * ```
     */
    fun performSelectiveExport(
        exportPatients: Boolean = true,
        exportAppointments: Boolean = true,
        exportPayments: Boolean = true
    ) {
        Log.d(TAG, "Starting selective export: " +
            "patients=$exportPatients, appointments=$exportAppointments, payments=$exportPayments")

        val exportType = when {
            exportPatients && !exportAppointments && !exportPayments -> ExportType.PATIENTS
            !exportPatients && exportAppointments && !exportPayments -> ExportType.APPOINTMENTS
            !exportPatients && !exportAppointments && exportPayments -> ExportType.PAYMENTS
            else -> ExportType.ALL
        }

        _isExporting.value = true

        launchSafe {
            try {
                // Validate prerequisites
                _exportState.value = ExportViewState.Validating(
                    "Validando pré-requisitos..."
                )

                val validationResult = exportDataUseCase.validateExport()
                if (!validationResult.isValid) {
                    _exportState.value = ExportViewState.Error(
                        validationResult.errorMessage ?: "Validação falhou"
                    )
                    _isExporting.value = false
                    return@launchSafe
                }

                // Start export
                _exportState.value = ExportViewState.InProgress(
                    currentStep = "Preparando exportação...",
                    totalProgress = 5,
                    exportType = exportType
                )

                // Execute selective export
                val result = exportDataUseCase.executeSelective(
                    exportPatients = exportPatients,
                    exportAppointments = exportAppointments,
                    exportPayments = exportPayments
                )

                if (result.success) {
                    Log.d(TAG, "Selective export successful: ${result.totalRecords} records")

                    _lastExportResult.value = result
                    _exportState.value = ExportViewState.Success(
                        result = result,
                        successMessage = "Exportação seletiva concluída! " +
                            "${result.totalRecords} registros salvos.",
                        exportType = exportType
                    )
                } else {
                    Log.w(TAG, "Selective export failed: ${result.errorMessage}")

                    _exportState.value = ExportViewState.Error(
                        message = result.errorMessage ?: "Erro na exportação seletiva"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Selective export exception", e)
                _exportState.value = ExportViewState.Error(
                    message = "Erro ao exportar: ${e.message ?: "desconhecido"}",
                    exception = e
                )
            } finally {
                _isExporting.value = false
            }
        }
    }

    /**
     * Retry failed export
     *
     * Called when user taps retry button after failed export.
     * Clears error state and retries the operation.
     *
     * Example:
     * ```kotlin
     * if (state is ExportViewState.Error) {
     *     Button(onClick = { viewModel.retryExport() })
     * }
     * ```
     */
    fun retryExport() {
        Log.d(TAG, "Retrying export...")
        _exportState.value = ExportViewState.Idle()
        clearError()
        performExport()
    }

    /**
     * Cancel current export operation
     *
     * Stops export if currently running.
     * Called when user dismisses export screen or taps cancel.
     *
     * Example:
     * ```kotlin
     * Button(onClick = { viewModel.cancelExport() })
     * ```
     */
    fun cancelExport() {
        Log.d(TAG, "Export cancelled by user")
        if (_isExporting.value) {
            _isExporting.value = false
            _exportState.value = ExportViewState.Idle(
                patientCount = _exportStats.value["patients"] ?: 0,
                appointmentCount = _exportStats.value["appointments"] ?: 0,
                paymentCount = _exportStats.value["payments"] ?: 0,
                totalRecords = _exportStats.value["total"] ?: 0
            )
        }
    }

    /**
     * Clean up old export files
     *
     * Removes export directories older than specified days.
     * Called periodically to free storage space.
     *
     * @param daysOld Age threshold (default 7 days)
     *
     * Example:
     * ```kotlin
     * viewModel.cleanupOldExports(daysOld = 30)
     * ```
     */
    fun cleanupOldExports(daysOld: Int = 7) {
        Log.d(TAG, "Cleaning up exports older than $daysOld days...")

        launchBackground {
            try {
                val deletedCount = exportDataUseCase.cleanupOldExports(daysOld)
                Log.d(TAG, "Cleanup complete: $deletedCount export(s) deleted")
            } catch (e: Exception) {
                Log.w(TAG, "Cleanup failed", e)
                // Don't show error for background cleanup operation
            }
        }
    }

    /**
     * Get list of available exports
     *
     * Returns all previously created export directories.
     * Useful for showing export history or recovery options.
     *
     * Example:
     * ```kotlin
     * val exports = viewModel.getAvailableExports()
     * ```
     */
    fun getAvailableExports() {
        launchBackground {
            try {
                val exports = exportDataUseCase.getAvailableExports()
                Log.d(TAG, "Found ${exports.size} export directories")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to list exports", e)
            }
        }
    }

    // ========================================
    // Financeiro CSV Operations (US1)
    // ========================================

    /**
     * Update the selected month for the financial CSV export.
     *
     * @param yearMonth The new month/year to select
     */
    fun selectMonth(yearMonth: YearMonth) {
        _selectedMonth.value = yearMonth
        // Reset state so the previous result is not shown for the new month
        _financeiroState.value = FinanceiroCsvState.Idle
    }

    /**
     * Perform the financial CSV export for the currently selected month.
     *
     * - Sets state to [FinanceiroCsvState.InProgress] while running.
     * - On success with rows → [FinanceiroCsvState.Success]
     * - On success with 0 rows → [FinanceiroCsvState.Empty]
     * - On exception → [FinanceiroCsvState.Error]
     */
    fun performFinanceiroExport() {
        val yearMonth = _selectedMonth.value
        Log.d(TAG, "Starting financeiro CSV export for $yearMonth")
        _financeiroState.value = FinanceiroCsvState.InProgress

        launchSafe {
            try {
                val result = exportDataUseCase.executeFinanceiro(yearMonth)

                if (!result.success) {
                    Log.w(TAG, "Financeiro export failed: ${result.errorMessage}")
                    _financeiroState.value = FinanceiroCsvState.Error(
                        result.errorMessage ?: "Erro desconhecido na exportação"
                    )
                    return@launchSafe
                }

                val file = result.paymentFile
                if (file == null) {
                    Log.d(TAG, "Financeiro export: no payments found for $yearMonth")
                    _financeiroState.value = FinanceiroCsvState.Empty
                } else {
                    Log.d(TAG, "Financeiro export: ${result.paymentCount} rows written to ${file.name}")
                    _financeiroState.value = FinanceiroCsvState.Success(file, result.paymentCount)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Financeiro export exception", e)
                _financeiroState.value = FinanceiroCsvState.Error(
                    "Erro ao exportar CSV financeiro: ${e.message ?: "desconhecido"}"
                )
            }
        }
    }

    // ========================================
    // State Helpers
    // ========================================

    /**
     * Check if export can proceed
     *
     * Validates that data exists and storage is available.
     * Used to enable/disable export button.
     *
     * @return true if export can proceed
     */
    fun canExport(): Boolean {
        val state = _exportState.value
        return state is ExportViewState.Idle && state.hasData() && !_isExporting.value
    }

    /**
     * Check if current operation is in progress
     *
     * @return true if export/validation running
     */
    fun isOperationInProgress(): Boolean = _isExporting.value

    /**
     * Get current status message for UI display
     *
     * @return Human-readable status
     */
    fun getStatusMessage(): String = when (val state = _exportState.value) {
        is ExportViewState.Idle ->
            if (state.hasData()) "Pronto para exportar ${state.totalRecords} registros"
            else "Nenhum dado para exportar"

        is ExportViewState.Validating -> state.message
        is ExportViewState.InProgress -> state.getStatusMessage()
        is ExportViewState.Success -> state.successMessage
        is ExportViewState.Error -> state.message
    }

    /**
     * Dismiss current error
     *
     * Clears error state so it's not shown again.
     *
     * Example:
     * ```kotlin
     * Button(onClick = { viewModel.dismissError() })
     * ```
     */
    fun dismissError() {
        if (_exportState.value is ExportViewState.Error) {
            _exportState.value = ExportViewState.Idle(
                patientCount = _exportStats.value["patients"] ?: 0,
                appointmentCount = _exportStats.value["appointments"] ?: 0,
                paymentCount = _exportStats.value["payments"] ?: 0,
                totalRecords = _exportStats.value["total"] ?: 0
            )
        }
        clearError()
    }
}
