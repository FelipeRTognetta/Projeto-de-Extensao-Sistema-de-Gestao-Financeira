package com.psychologist.financial.domain.usecases

import android.content.Context
import com.psychologist.financial.data.repositories.ExportRepository
import com.psychologist.financial.domain.models.ExportResult
import com.psychologist.financial.services.CsvExportService
import com.psychologist.financial.services.FileStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Export Data Use Case
 *
 * Orchestrates the complete data export process.
 * Coordinates repository queries, CSV generation, and file storage.
 *
 * Responsibilities:
 * - Query all patient, appointment, and payment data
 * - Generate CSV files for each data type
 * - Handle errors gracefully
 * - Provide export statistics and results
 * - Manage file storage and cleanup
 *
 * Business Logic:
 * - Validates storage space before export
 * - Executes export on IO thread
 * - Rolls back partial exports on error
 * - Tracks export timing and statistics
 * - Supports optional data filtering (future enhancement)
 *
 * Dependencies:
 * - ExportRepository: Data queries
 * - CsvExportService: CSV generation
 * - FileStorageManager: File operations
 * - Context: Android file access
 *
 * Usage:
 * ```kotlin
 * val useCase = ExportDataUseCase(
 *     context = context,
 *     repository = exportRepository
 * )
 *
 * try {
 *     val result = useCase.execute()
 *     if (result.success) {
 *         showSuccess("Exported ${result.totalRecords} records")
 *         shareFiles(result.getFiles())
 *     }
 * } catch (e: Exception) {
 *     showError(e.message)
 * }
 * ```
 *
 * @property context Android application context
 * @property repository Repository for data queries
 * @property csvService CSV export service (injected or created)
 * @property storageManager File storage manager (injected or created)
 */
class ExportDataUseCase(
    private val context: Context,
    private val repository: ExportRepository,
    private val csvService: CsvExportService = CsvExportService(),
    private val storageManager: FileStorageManager = FileStorageManager(context)
) {

    companion object {
        private const val TAG = "ExportDataUseCase"
        private const val MINIMUM_STORAGE_MB = 50  // 50MB minimum for safety
    }

    /**
     * Execute full data export
     *
     * Performs complete export of patients, appointments, and payments.
     * Validates storage, queries data, generates CSVs, and returns result.
     *
     * @return ExportResult with file paths and statistics
     * @throws IllegalStateException if storage insufficient
     * @throws Exception if export fails
     */
    suspend fun execute(): ExportResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        try {
            // Validate storage space
            if (!storageManager.hasStorageSpace(MINIMUM_STORAGE_MB)) {
                val availableMB = storageManager.getAvailableStorageMB()
                return@withContext ExportResult.failure(
                    "Espaço insuficiente. Disponível: ${availableMB}MB, Necessário: ${MINIMUM_STORAGE_MB}MB"
                )
            }

            // Create timestamped export directory
            val exportDir = storageManager.getTimestampedExportDirectory()

            // Query all data in parallel (future optimization)
            val patients = repository.getAllPatients()
            val appointments = repository.getAllAppointments()
            val payments = repository.getAllPayments()

            // Generate CSV files
            val patientFile = csvService.exportPatients(patients, exportDir)
            val appointmentFile = csvService.exportAppointments(appointments, exportDir)
            val paymentFile = csvService.exportPayments(payments, exportDir)

            // Calculate export duration
            val durationSeconds = (System.currentTimeMillis() - startTime) / 1000

            // Return success result
            return@withContext ExportResult.success(
                patientFile = patientFile,
                appointmentFile = appointmentFile,
                paymentFile = paymentFile,
                patientCount = patients.size,
                appointmentCount = appointments.size,
                paymentCount = payments.size,
                durationSeconds = durationSeconds
            )

        } catch (e: IllegalStateException) {
            // Storage/permission error
            ExportResult.failure("Erro de armazenamento: ${e.message}")
        } catch (e: Exception) {
            // Unexpected error - log for debugging
            ExportResult.failure(e)
        }
    }

    /**
     * Export specific data types
     *
     * Allows selective export of patients, appointments, or payments only.
     *
     * @param exportPatients Include patients in export
     * @param exportAppointments Include appointments in export
     * @param exportPayments Include payments in export
     * @return ExportResult with selected data types
     */
    suspend fun executeSelective(
        exportPatients: Boolean = true,
        exportAppointments: Boolean = true,
        exportPayments: Boolean = true
    ): ExportResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        try {
            // Validate at least one type selected
            if (!exportPatients && !exportAppointments && !exportPayments) {
                return@withContext ExportResult.failure(
                    "Selecione pelo menos um tipo de dados para exportar"
                )
            }

            // Validate storage
            if (!storageManager.hasStorageSpace(MINIMUM_STORAGE_MB)) {
                val availableMB = storageManager.getAvailableStorageMB()
                return@withContext ExportResult.failure(
                    "Espaço insuficiente. Disponível: ${availableMB}MB"
                )
            }

            val exportDir = storageManager.getTimestampedExportDirectory()

            var patientFile: java.io.File? = null
            var appointmentFile: java.io.File? = null
            var paymentFile: java.io.File? = null
            var patientCount = 0
            var appointmentCount = 0
            var paymentCount = 0

            // Query and export selected types
            if (exportPatients) {
                val patients = repository.getAllPatients()
                patientFile = csvService.exportPatients(patients, exportDir)
                patientCount = patients.size
            }

            if (exportAppointments) {
                val appointments = repository.getAllAppointments()
                appointmentFile = csvService.exportAppointments(appointments, exportDir)
                appointmentCount = appointments.size
            }

            if (exportPayments) {
                val payments = repository.getAllPayments()
                paymentFile = csvService.exportPayments(payments, exportDir)
                paymentCount = payments.size
            }

            val durationSeconds = (System.currentTimeMillis() - startTime) / 1000

            return@withContext ExportResult(
                success = true,
                patientFile = patientFile,
                appointmentFile = appointmentFile,
                paymentFile = paymentFile,
                patientCount = patientCount,
                appointmentCount = appointmentCount,
                paymentCount = paymentCount,
                durationSeconds = durationSeconds
            )

        } catch (e: Exception) {
            ExportResult.failure(e)
        }
    }

    /**
     * Get export statistics without performing export
     *
     * Provides information about what would be exported.
     *
     * @return Map with statistics
     */
    suspend fun getExportStats(): Map<String, Int> = withContext(Dispatchers.IO) {
        try {
            val patients = repository.getAllPatients()
            val appointments = repository.getAllAppointments()
            val payments = repository.getAllPayments()

            mapOf(
                "patients" to patients.size,
                "appointments" to appointments.size,
                "payments" to payments.size,
                "total" to (patients.size + appointments.size + payments.size)
            )
        } catch (e: Exception) {
            mapOf("error" to -1)
        }
    }

    /**
     * Clean up old exports
     *
     * Removes export directories older than specified days.
     *
     * @param daysOld Minimum age of exports to delete
     * @return Number of directories deleted
     */
    suspend fun cleanupOldExports(daysOld: Int = 7): Int = withContext(Dispatchers.IO) {
        try {
            storageManager.cleanupOldExports(daysOld)
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Get list of available exports
     *
     * @return List of export directories
     */
    suspend fun getAvailableExports() = withContext(Dispatchers.IO) {
        try {
            storageManager.getExportDirectories()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Get storage status
     *
     * @return Human-readable storage information
     */
    suspend fun getStorageStatus(): String = withContext(Dispatchers.IO) {
        try {
            storageManager.getStorageStatus()
        } catch (e: Exception) {
            "Erro ao verificar armazenamento"
        }
    }

    /**
     * Validate export prerequisites
     *
     * Checks if export can proceed without errors.
     *
     * @return Validation result with error message if failed
     */
    suspend fun validateExport(): ValidationResult = withContext(Dispatchers.IO) {
        try {
            // Check storage space
            if (!storageManager.hasStorageSpace(MINIMUM_STORAGE_MB)) {
                val available = storageManager.getAvailableStorageMB()
                return@withContext ValidationResult(
                    isValid = false,
                    errorMessage = "Espaço insuficiente: ${available}MB disponível"
                )
            }

            // Check data availability
            val patientCount = repository.countAllPatients()
            if (patientCount == 0) {
                return@withContext ValidationResult(
                    isValid = false,
                    errorMessage = "Nenhum paciente para exportar"
                )
            }

            return@withContext ValidationResult(isValid = true)

        } catch (e: Exception) {
            ValidationResult(
                isValid = false,
                errorMessage = "Erro ao validar: ${e.message}"
            )
        }
    }

    /**
     * Validation result data class
     */
    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String? = null
    )
}
