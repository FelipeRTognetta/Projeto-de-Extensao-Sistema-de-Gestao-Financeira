package com.psychologist.financial.data.repositories

import android.util.Log
import com.psychologist.financial.data.database.AppDatabase
import com.psychologist.financial.data.database.AppointmentDao
import com.psychologist.financial.data.database.PatientDao
import com.psychologist.financial.data.database.PaymentDao
import com.psychologist.financial.domain.models.Appointment
import com.psychologist.financial.domain.models.Payment
import com.psychologist.financial.domain.models.Patient
import com.psychologist.financial.domain.models.PatientStatus

/**
 * Export Repository
 *
 * Provides data access for export operations.
 * Queries all patients, appointments, and payment records for export.
 *
 * Responsibilities:
 * - Retrieve all patients (active and inactive)
 * - Retrieve all appointments
 * - Retrieve all payments
 * - Count records by type
 * - Map database entities to domain models
 * - Handle data transformations for export
 *
 * Architecture:
 * - Extends BaseRepository for transaction management
 * - Uses DAOs for database access
 * - Converts entities to domain models
 * - No filtering (exports all data for backup purposes)
 *
 * Queries:
 * - getAllPatients(): List<Patient> - All patients (active + inactive)
 * - getAllAppointments(): List<Appointment> - All appointments
 * - getAllPayments(): List<Payment> - All payments
 * - countAllPatients(): Int - Patient count
 * - countAllAppointments(): Int - Appointment count
 * - countAllPayments(): Int - Payment count
 *
 * Usage:
 * ```kotlin
 * val repository = ExportRepository(database)
 *
 * // Get all data for export
 * val patients = repository.getAllPatients()
 * val appointments = repository.getAllAppointments()
 * val payments = repository.getAllPayments()
 *
 * // Check counts
 * val patientCount = repository.countAllPatients()
 * ```
 *
 * @property database AppDatabase instance
 */
class ExportRepository(database: AppDatabase) : BaseRepository(database) {

    private companion object {
        private const val TAG = "ExportRepository"
    }

    private val patientDao: PatientDao = database.patientDao()
    private val appointmentDao: AppointmentDao = database.appointmentDao()
    private val paymentDao: PaymentDao = database.paymentDao()

    // ========================================
    // Patient Queries
    // ========================================

    /**
     * Get all patients (active and inactive)
     *
     * Retrieves complete patient list including inactive patients.
     * Used for full data backup and export.
     *
     * @return List of all patients
     */
    suspend fun getAllPatients(): List<Patient> {
        return try {
            Log.d(TAG, "Querying all patients for export...")
            val entities = patientDao.getAllPatients()
            Log.d(TAG, "Retrieved ${entities.size} patients")
            entities.map { it.toPatient() }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving patients", e)
            throw e
        }
    }

    /**
     * Count all patients
     *
     * @return Total number of patients
     */
    suspend fun countAllPatients(): Int {
        return try {
            patientDao.countAllPatients()
        } catch (e: Exception) {
            Log.e(TAG, "Error counting patients", e)
            0
        }
    }

    /**
     * Get only active patients
     *
     * Retrieves patients with ACTIVE status.
     * Useful for filtered exports.
     *
     * @return List of active patients
     */
    suspend fun getActivePatients(): List<Patient> {
        return try {
            Log.d(TAG, "Querying active patients...")
            val entities = patientDao.getPatientsByStatus("ACTIVE")
            entities.map { it.toPatient() }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving active patients", e)
            emptyList()
        }
    }

    /**
     * Get only inactive patients
     *
     * Retrieves patients with INACTIVE status.
     *
     * @return List of inactive patients
     */
    suspend fun getInactivePatients(): List<Patient> {
        return try {
            Log.d(TAG, "Querying inactive patients...")
            val entities = patientDao.getPatientsByStatus("INACTIVE")
            entities.map { it.toPatient() }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving inactive patients", e)
            emptyList()
        }
    }

    // ========================================
    // Appointment Queries
    // ========================================

    /**
     * Get all appointments
     *
     * Retrieves complete appointment list regardless of patient status.
     * Used for full data export and backup.
     *
     * @return List of all appointments
     */
    suspend fun getAllAppointments(): List<Appointment> {
        return try {
            Log.d(TAG, "Querying all appointments for export...")
            val entities = appointmentDao.getAllAppointments()
            Log.d(TAG, "Retrieved ${entities.size} appointments")
            entities.map { it.toAppointment() }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving appointments", e)
            throw e
        }
    }

    /**
     * Count all appointments
     *
     * @return Total number of appointments
     */
    suspend fun countAllAppointments(): Int {
        return try {
            appointmentDao.countAllAppointments()
        } catch (e: Exception) {
            Log.e(TAG, "Error counting appointments", e)
            0
        }
    }

    /**
     * Get appointments by date range
     *
     * Useful for time-based exports and filtering.
     *
     * @param startDate Start date for range
     * @param endDate End date for range
     * @return Appointments in date range
     */
    suspend fun getAppointmentsByDateRange(
        startDate: java.time.LocalDate,
        endDate: java.time.LocalDate
    ): List<Appointment> {
        return try {
            Log.d(TAG, "Querying appointments from $startDate to $endDate...")
            val entities = appointmentDao.getAppointmentsByDateRange(startDate, endDate)
            entities.map { it.toAppointment() }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving appointments by date range", e)
            emptyList()
        }
    }

    // ========================================
    // Payment Queries
    // ========================================

    /**
     * Get all payments
     *
     * Retrieves complete payment list including pending and paid.
     * Used for full financial data export and backup.
     *
     * @return List of all payments
     */
    suspend fun getAllPayments(): List<Payment> {
        return try {
            Log.d(TAG, "Querying all payments for export...")
            val entities = paymentDao.getAllPayments()
            Log.d(TAG, "Retrieved ${entities.size} payments")
            entities.map { it.toPayment() }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving payments", e)
            throw e
        }
    }

    /**
     * Count all payments
     *
     * @return Total number of payments
     */
    suspend fun countAllPayments(): Int {
        return try {
            paymentDao.countAllPayments()
        } catch (e: Exception) {
            Log.e(TAG, "Error counting payments", e)
            0
        }
    }

    /**
     * Get payments by status
     *
     * Retrieves PAID or PENDING payments.
     * Useful for financial reconciliation exports.
     *
     * @param status Payment status (PAID or PENDING)
     * @return Payments with specified status
     */
    suspend fun getPaymentsByStatus(status: String): List<Payment> {
        return try {
            Log.d(TAG, "Querying payments with status $status...")
            val entities = paymentDao.getPaymentsByStatus(status)
            entities.map { it.toPayment() }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving payments by status", e)
            emptyList()
        }
    }

    /**
     * Get payments by date range
     *
     * @param startDate Start date for range
     * @param endDate End date for range
     * @return Payments in date range
     */
    suspend fun getPaymentsByDateRange(
        startDate: java.time.LocalDate,
        endDate: java.time.LocalDate
    ): List<Payment> {
        return try {
            Log.d(TAG, "Querying payments from $startDate to $endDate...")
            val entities = paymentDao.getPaymentsByDateRange(startDate, endDate)
            entities.map { it.toPayment() }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving payments by date range", e)
            emptyList()
        }
    }

    // ========================================
    // Aggregate Queries
    // ========================================

    /**
     * Get export statistics
     *
     * Returns counts of all data types for summary display.
     *
     * @return Map with statistics
     */
    suspend fun getExportStatistics(): Map<String, Int> {
        return try {
            mapOf(
                "patients" to countAllPatients(),
                "appointments" to countAllAppointments(),
                "payments" to countAllPayments(),
                "active_patients" to (getActivePatients().size),
                "inactive_patients" to (getInactivePatients().size)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving statistics", e)
            emptyMap()
        }
    }

    /**
     * Validate data for export
     *
     * Checks if sufficient data exists to export.
     *
     * @return true if there is data to export
     */
    suspend fun hasDataToExport(): Boolean {
        return try {
            countAllPatients() > 0 ||
            countAllAppointments() > 0 ||
            countAllPayments() > 0
        } catch (e: Exception) {
            false
        }
    }

    // ========================================
    // Entity to Model Conversions
    // ========================================

    private fun com.psychologist.financial.data.entities.PatientEntity.toPatient(): Patient {
        return Patient(
            id = this.id,
            name = this.name,
            phone = this.phone,
            email = this.email,
            status = when (this.status) {
                "ACTIVE" -> PatientStatus.ACTIVE
                "INACTIVE" -> PatientStatus.INACTIVE
                else -> PatientStatus.ACTIVE
            },
            initialConsultDate = this.initialConsultDate,
            registrationDate = this.registrationDate,
            lastAppointmentDate = this.lastAppointmentDate
        )
    }

    private fun com.psychologist.financial.data.entities.AppointmentEntity.toAppointment(): Appointment {
        return Appointment(
            id = this.id,
            patientId = this.patientId,
            date = this.date,
            timeStart = this.time,
            durationMinutes = this.durationMinutes,
            notes = this.notes,
            createdDate = this.createdDate
        )
    }

    private fun com.psychologist.financial.data.entities.PaymentEntity.toPayment(): Payment {
        return Payment(
            id = this.id,
            patientId = this.patientId,
            appointmentId = this.appointmentId,
            amount = this.amount,
            status = this.status,
            paymentMethod = this.method,
            paymentDate = this.paymentDate,
            createdDate = this.recordedDate
        )
    }
}
