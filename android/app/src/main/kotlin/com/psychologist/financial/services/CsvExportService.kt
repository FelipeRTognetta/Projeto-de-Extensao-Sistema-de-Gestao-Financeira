package com.psychologist.financial.services

import com.psychologist.financial.domain.models.Appointment
import com.psychologist.financial.domain.models.FinanceiroCsvRow
import com.psychologist.financial.domain.models.Payment
import com.psychologist.financial.domain.models.Patient
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import java.io.File
import java.io.FileWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * CSV Export Service
 *
 * Handles exporting patient, appointment, and payment data to CSV format.
 * Generates properly formatted CSV files with headers and escaped values.
 *
 * Features:
 * - Patient data export (name, phone, email, status, dates)
 * - Appointment data export (patient, date, time, duration, notes)
 * - Payment data export (patient, amount, status, method, date)
 * - Special character handling (quotes, commas, newlines)
 * - Date/time formatting for CSV compatibility
 * - Currency formatting with proper decimal places
 *
 * Usage:
 * ```kotlin
 * val service = CsvExportService()
 * val patientFile = service.exportPatients(patients, File(exportDir))
 * val appointmentFile = service.exportAppointments(appointments, File(exportDir))
 * val paymentFile = service.exportPayments(payments, File(exportDir))
 * ```
 */
class CsvExportService {

    /**
     * Export patients to CSV file
     *
     * @param patients List of patients to export
     * @param exportDir Directory to save file
     * @return File containing exported patients
     */
    fun exportPatients(patients: List<Patient>, exportDir: File): File {
        val file = File(exportDir, "patients.csv")

        FileWriter(file).use { writer ->
            // Write header
            writer.append("ID,Nome,Telefone,Email,Status,Data Primeira Consulta,Data Registro,Última Consulta\n")

            // Write data
            patients.forEach { patient ->
                writer.append("${patient.id},")
                writer.append("\"${escapeForCsv(patient.name)}\",")
                writer.append("\"${escapeForCsv(patient.phone ?: "")}\",")
                writer.append("\"${escapeForCsv(patient.email ?: "")}\",")
                writer.append("${patient.status.name},")
                writer.append("${patient.initialConsultDate},")
                writer.append("${patient.registrationDate},")
                writer.append("${patient.lastAppointmentDate ?: ""}\n")
            }
        }

        return file
    }

    /**
     * Export appointments to CSV file
     *
     * @param appointments List of appointments to export
     * @param exportDir Directory to save file
     * @return File containing exported appointments
     */
    fun exportAppointments(appointments: List<Appointment>, exportDir: File): File {
        val file = File(exportDir, "appointments.csv")

        FileWriter(file).use { writer ->
            // Write header
            writer.append("ID,ID Paciente,Data,Hora,Duração (min),Notas,Data Criação\n")

            // Write data
            appointments.forEach { appointment ->
                writer.append("${appointment.id},")
                writer.append("${appointment.patientId},")
                writer.append("${appointment.date},")
                writer.append("${appointment.timeStart},")
                writer.append("${appointment.durationMinutes},")
                writer.append("\"${escapeForCsv(appointment.notes ?: "")}\",")
                writer.append("${appointment.createdDate}\n")
            }
        }

        return file
    }

    /**
     * Export payments to CSV file
     *
     * @param payments List of payments to export
     * @param exportDir Directory to save file
     * @return File containing exported payments
     */
    fun exportPayments(payments: List<Payment>, exportDir: File): File {
        val file = File(exportDir, "payments.csv")

        FileWriter(file).use { writer ->
            // Write header
            writer.append("ID,ID Paciente,IDs Atendimentos,Valor,Data Pagamento,Data Registro\n")

            // Write data
            payments.forEach { payment ->
                writer.append("${payment.id},")
                writer.append("${payment.patientId},")
                writer.append("\"${payment.appointmentIds.joinToString("|")}\",")
                writer.append("${payment.amount},")
                writer.append("${payment.paymentDate},")
                writer.append("${payment.createdDate}\n")
            }
        }

        return file
    }

    /**
     * Escape CSV special characters
     *
     * Handles quotes, commas, and newlines according to CSV specification.
     *
     * @param value String to escape
     * @return Escaped string safe for CSV
     */
    private fun escapeForCsv(value: String?): String {
        if (value == null) return ""
        return value
            .replace("\"", "\"\"")  // Double quotes for escaping
            .replace("\n", " ")      // Replace newlines with space
            .replace("\r", " ")      // Replace carriage returns
    }

    /**
     * Export financial data to CSV with semicolon separator
     *
     * Generates a monthly financial report with one row per payment.
     * Each row contains patient data (5 cols), responsible payer data (5 cols),
     * payment amount, and payment date. Payer columns are blank when the patient
     * is the payer (naoPagante = false).
     *
     * Uses Apache Commons CSV with `;` as delimiter (Excel-compatible in pt-BR locale).
     *
     * @param rows List of pre-built FinanceiroCsvRow (one per payment)
     * @param exportDir Directory to save the file
     * @return Generated CSV file
     */
    fun exportFinanceiroCsv(rows: List<FinanceiroCsvRow>, exportDir: File): File {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val file = File(exportDir, "financeiro_$timestamp.csv")

        val format = CSVFormat.DEFAULT.builder()
            .setDelimiter(';')
            .setRecordSeparator('\n')
            .setHeader(
                "Nome Paciente", "CPF Paciente", "Email Paciente",
                "Telefone Paciente", "Endereço Paciente",
                "Nome Responsável", "CPF Responsável", "Email Responsável",
                "Telefone Responsável", "Endereço Responsável",
                "Valor Pagamento", "Data Pagamento"
            )
            .build()

        file.bufferedWriter().use { writer ->
            CSVPrinter(writer, format).use { printer ->
                rows.forEach { row ->
                    printer.printRecord(
                        row.nomePaciente, row.cpfPaciente, row.emailPaciente,
                        row.telefonePaciente, row.enderecoPaciente,
                        row.nomeResponsavel, row.cpfResponsavel, row.emailResponsavel,
                        row.telefoneResponsavel, row.enderecoResponsavel,
                        row.valorPagamento, row.dataPagamento
                    )
                }
            }
        }

        return file
    }

    /**
     * Get file extension for exported files
     *
     * @return ".csv"
     */
    fun getFileExtension(): String = ".csv"

    /**
     * Get MIME type for CSV files
     *
     * @return "text/csv"
     */
    fun getMimeType(): String = "text/csv"
}
