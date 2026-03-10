package com.psychologist.financial.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.psychologist.financial.domain.models.BackupResult
import com.psychologist.financial.domain.usecases.ExportBackupUseCase
import com.psychologist.financial.domain.usecases.ExportDataUseCase
import com.psychologist.financial.domain.usecases.ImportBackupUseCase
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
    private val exportDataUseCase: ExportDataUseCase,
    private val exportBackupUseCase: ExportBackupUseCase? = null,
    private val importBackupUseCase: ImportBackupUseCase? = null
) : BaseViewModel() {

    private companion object {
        private const val TAG = "ExportViewModel"
    }

    // ========================================
    // Export Statistics State
    // ========================================

    /** Statistics displayed in the stats/storage cards on the export screen. */
    private val _exportState = MutableStateFlow(ExportViewState())
    val exportState: StateFlow<ExportViewState> = _exportState.asStateFlow()

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

    // ========================================
    // Backup Export State (US3)
    // ========================================

    /**
     * State of the full-database backup export operation.
     */
    private val _backupExportState = MutableStateFlow<BackupExportState>(BackupExportState.Idle)
    val backupExportState: StateFlow<BackupExportState> = _backupExportState.asStateFlow()

    // ========================================
    // Backup Import State (US4)
    // ========================================

    /**
     * State of the backup import operation.
     */
    private val _backupImportState = MutableStateFlow<BackupImportState>(BackupImportState.Idle)
    val backupImportState: StateFlow<BackupImportState> = _backupImportState.asStateFlow()

    /** URI of the file selected by the user — held until [confirmBackupImport] is called. */
    private var pendingImportUri: Uri? = null

    /** Password entered by the user — held until [confirmBackupImport] is called. */
    private var pendingImportPassword: String = ""

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
                Log.d(TAG, "Stats loaded: $stats")

                // Update idle state with statistics
                val patientCount = stats["patients"] ?: 0
                val appointmentCount = stats["appointments"] ?: 0
                val paymentCount = stats["payments"] ?: 0
                val totalRecords = stats["total"] ?: 0

                val storageStatus = exportDataUseCase.getStorageStatus()
                Log.d(TAG, "Storage: $storageStatus")

                _exportState.value = ExportViewState(
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
    // Backup Export Operations (US3)
    // ========================================

    /**
     * Perform a full-database backup export.
     *
     * Validates password, serializes all data, encrypts with PBKDF2+AES-256-GCM,
     * and writes a .pgfbackup file.
     *
     * @param password User's password (min 6 chars)
     * @param passwordConfirmation Must match [password]
     */
    fun performBackupExport(password: String, passwordConfirmation: String) {
        val useCase = exportBackupUseCase ?: run {
            Log.w(TAG, "exportBackupUseCase not configured")
            _backupExportState.value = BackupExportState.Error("Serviço de backup não disponível.")
            return
        }
        Log.d(TAG, "Starting backup export...")
        _backupExportState.value = BackupExportState.InProgress

        launchSafe {
            try {
                val result = useCase.execute(password, passwordConfirmation)
                when (result) {
                    is BackupResult.ExportSuccess -> {
                        Log.d(TAG, "Backup export successful: ${result.totalRecords} records")
                        _backupExportState.value = BackupExportState.Success(result)
                    }
                    is BackupResult.Failure -> {
                        Log.w(TAG, "Backup export failed: ${result.message}")
                        _backupExportState.value = BackupExportState.Error(result.message)
                    }
                    else -> {
                        _backupExportState.value = BackupExportState.Error("Resultado inesperado.")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Backup export exception", e)
                _backupExportState.value = BackupExportState.Error(
                    "Erro ao exportar backup: ${e.message ?: "erro desconhecido"}"
                )
            }
        }
    }

    /**
     * Reset backup export state to Idle.
     *
     * Called when user dismisses result or wants to start fresh.
     */
    fun resetBackupExportState() {
        _backupExportState.value = BackupExportState.Idle
    }

    // ========================================
    // Backup Import Operations (US4)
    // ========================================

    /**
     * Stage a backup import: store URI + password and move to [BackupImportState.AwaitingConfirmation].
     *
     * The actual import runs only after [confirmBackupImport] is called.
     *
     * @param uri      Content URI of the `.pgfbackup` file
     * @param password User-provided decryption password
     */
    fun initiateBackupImport(uri: Uri, password: String) {
        pendingImportUri = uri
        pendingImportPassword = password
        _backupImportState.value = BackupImportState.AwaitingConfirmation
        Log.d(TAG, "Backup import staged, awaiting confirmation")
    }

    /**
     * Execute the pending backup import after user confirms the data-overwrite warning.
     *
     * Requires [initiateBackupImport] to have been called first.
     */
    fun confirmBackupImport() {
        val useCase = importBackupUseCase ?: run {
            Log.w(TAG, "importBackupUseCase not configured")
            _backupImportState.value = BackupImportState.Error("Serviço de importação não disponível.")
            return
        }
        val uri = pendingImportUri ?: run {
            Log.w(TAG, "No pending import URI")
            _backupImportState.value = BackupImportState.Error("Nenhum arquivo selecionado.")
            return
        }
        val password = pendingImportPassword

        Log.d(TAG, "Starting backup import...")
        _backupImportState.value = BackupImportState.InProgress

        launchSafe {
            try {
                val result = useCase.execute(uri, password)
                when (result) {
                    is BackupResult.ImportSuccess -> {
                        Log.d(TAG, "Backup import successful: ${result.totalRecords} records")
                        _backupImportState.value = BackupImportState.Success(result)
                    }
                    is BackupResult.Failure -> {
                        Log.w(TAG, "Backup import failed: ${result.message}")
                        _backupImportState.value = BackupImportState.Error(result.message)
                    }
                    else -> {
                        _backupImportState.value = BackupImportState.Error("Resultado inesperado.")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Backup import exception", e)
                _backupImportState.value = BackupImportState.Error(
                    "Erro ao importar backup: ${e.message ?: "erro desconhecido"}"
                )
            } finally {
                pendingImportUri = null
                pendingImportPassword = ""
            }
        }
    }

    /**
     * Cancel a pending or completed import and return to [BackupImportState.Idle].
     */
    fun cancelBackupImport() {
        pendingImportUri = null
        pendingImportPassword = ""
        _backupImportState.value = BackupImportState.Idle
        Log.d(TAG, "Backup import cancelled")
    }

}
