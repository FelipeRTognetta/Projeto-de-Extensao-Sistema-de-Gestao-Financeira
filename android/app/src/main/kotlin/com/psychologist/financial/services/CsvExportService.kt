package com.psychologist.financial.services

import com.psychologist.financial.domain.models.Appointment
import com.psychologist.financial.domain.models.Payment
import com.psychologist.financial.domain.models.Patient
import java.io.File
import java.io.FileWriter

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
            writer.append("ID,ID Paciente,ID Atendimento,Valor,Status,Método,Data Pagamento,Data Registro\n")

            // Write data
            payments.forEach { payment ->
                writer.append("${payment.id},")
                writer.append("${payment.patientId},")
                writer.append("${payment.appointmentId ?: ""},")
                writer.append("${payment.amount},")
                writer.append("${payment.status},")
                writer.append("${payment.paymentMethod},")
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
