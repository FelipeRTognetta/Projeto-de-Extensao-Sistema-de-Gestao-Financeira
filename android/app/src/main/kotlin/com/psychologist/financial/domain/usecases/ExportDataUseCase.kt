package com.psychologist.financial.domain.usecases

import android.content.Context
import com.psychologist.financial.data.repositories.ExportRepository
import com.psychologist.financial.domain.models.ExportResult
import com.psychologist.financial.domain.models.FinanceiroCsvRow
import com.psychologist.financial.services.CsvExportService
import com.psychologist.financial.services.FileStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * Export Data Use Case
 *
 * Provides financial CSV export and export statistics.
 *
 * Public API:
 * - [executeFinanceiro]: Generates a monthly financial CSV of payments
 * - [getExportStats]: Returns record counts for the export statistics screen
 * - [getStorageStatus]: Returns a human-readable storage availability string
 *
 * @property context Android application context
 * @property repository Repository for data queries
 * @property csvService CSV export service
 * @property storageManager File storage manager
 */
class ExportDataUseCase(
    private val context: Context,
    private val repository: ExportRepository,
    private val csvService: CsvExportService = CsvExportService(),
    private val storageManager: FileStorageManager = FileStorageManager(context)
) {

    /**
     * Get export statistics without performing export.
     *
     * @return Map with keys: patients, appointments, payments, total
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
     * Get storage status.
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
     * Execute financial CSV export for a given month.
     *
     * Fetches all payments in the selected month, resolves patient and payer
     * information, builds one [FinanceiroCsvRow] per payment, and writes a
     * semicolon-delimited CSV file.
     *
     * Returns an [ExportResult] with:
     * - `paymentFile = null` and `paymentCount = 0` when the month has no payments
     * - `paymentFile` set and `paymentCount = N` when payments exist
     *
     * @param yearMonth The month/year to export
     * @return ExportResult with CSV file or empty result
     */
    suspend fun executeFinanceiro(yearMonth: YearMonth): ExportResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

        try {
            val payments = repository.getPaymentsByMonth(yearMonth)

            if (payments.isEmpty()) {
                return@withContext ExportResult(
                    success = true,
                    paymentCount = 0,
                    paymentFile = null,
                    durationSeconds = (System.currentTimeMillis() - startTime) / 1000
                )
            }

            val patients = repository.getAllPatients()
            val payerInfos = repository.getAllPayerInfos()

            val patientById = patients.associateBy { it.id }
            val payerInfoByPatientId = payerInfos.associateBy { it.patientId }

            val rows = payments
                .sortedWith(compareBy(
                    { patientById[it.patientId]?.name?.lowercase() ?: "" },
                    { it.paymentDate }
                ))
                .mapNotNull { payment ->
                val patient = patientById[payment.patientId] ?: return@mapNotNull null
                val payerInfo = payerInfoByPatientId[payment.patientId]
                FinanceiroCsvRow(
                    nomePaciente = patient.name,
                    cpfPaciente = patient.cpf ?: "",
                    emailPaciente = patient.email ?: "",
                    telefonePaciente = patient.phone ?: "",
                    enderecoPaciente = patient.endereco ?: "",
                    nomeResponsavel = payerInfo?.nome ?: "",
                    cpfResponsavel = payerInfo?.cpf ?: "",
                    emailResponsavel = payerInfo?.email ?: "",
                    telefoneResponsavel = payerInfo?.telefone ?: "",
                    enderecoResponsavel = payerInfo?.endereco ?: "",
                    valorPagamento = payment.amount.setScale(2).toPlainString().replace(".", ","),
                    dataPagamento = payment.paymentDate.format(dateFormatter)
                )
            }

            val exportDir = storageManager.getTimestampedExportDirectory()
            val csvFile = csvService.exportFinanceiroCsv(rows, exportDir)

            ExportResult(
                success = true,
                paymentFile = csvFile,
                paymentCount = rows.size,
                durationSeconds = (System.currentTimeMillis() - startTime) / 1000
            )

        } catch (e: Exception) {
            ExportResult.failure(e)
        }
    }
}
