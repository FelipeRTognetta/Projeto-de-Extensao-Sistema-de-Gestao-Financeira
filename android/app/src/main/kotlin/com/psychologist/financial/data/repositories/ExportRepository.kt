package com.psychologist.financial.data.repositories

import com.psychologist.financial.utils.AppLogger
import com.psychologist.financial.data.database.AppDatabase
import com.psychologist.financial.data.database.AppointmentDao
import com.psychologist.financial.data.database.PatientDao
import com.psychologist.financial.data.database.PayerInfoDao
import com.psychologist.financial.data.database.PaymentDao
import com.psychologist.financial.domain.models.Appointment
import com.psychologist.financial.domain.models.Payment
import com.psychologist.financial.domain.models.Patient
import com.psychologist.financial.domain.models.PatientStatus
import com.psychologist.financial.domain.models.PayerInfo
import java.time.YearMonth

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
    private val payerInfoDao: PayerInfoDao = database.payerInfoDao()

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
            AppLogger.d(TAG, "Querying all patients for export...")
            val entities = patientDao.getAllPatients()
            AppLogger.d(TAG, "Retrieved ${entities.size} patients")
            entities.map { it.toPatient() }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error retrieving patients", e)
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
            AppLogger.e(TAG, "Error counting patients", e)
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
            AppLogger.d(TAG, "Querying active patients...")
            val entities = patientDao.getAllActivePatients()
            entities.map { it.toPatient() }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error retrieving active patients", e)
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
            AppLogger.d(TAG, "Querying inactive patients...")
            val entities = patientDao.getAllInactivePatients()
            entities.map { it.toPatient() }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error retrieving inactive patients", e)
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
            AppLogger.d(TAG, "Querying all appointments for export...")
            val entities = appointmentDao.getAll()
            AppLogger.d(TAG, "Retrieved ${entities.size} appointments")
            entities.map { it.toAppointment() }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error retrieving appointments", e)
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
            appointmentDao.count()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error counting appointments", e)
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
            AppLogger.d(TAG, "Querying appointments from $startDate to $endDate...")
            val entities = appointmentDao.getByDateRange(startDate, endDate)
            entities.map { it.toAppointment() }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error retrieving appointments by date range", e)
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
            AppLogger.d(TAG, "Querying all payments for export...")
            val entities = paymentDao.getAll()
            AppLogger.d(TAG, "Retrieved ${entities.size} payments")
            entities.map { it.toPayment() }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error retrieving payments", e)
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
            paymentDao.count()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error counting payments", e)
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
            AppLogger.d(TAG, "Querying payments (status filter removed — all payments are PAID)...")
            val entities = paymentDao.getAll()
            entities.map { it.toPayment() }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error retrieving payments by status", e)
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
            AppLogger.d(TAG, "Querying payments from $startDate to $endDate...")
            val entities = paymentDao.getByDateRange(startDate, endDate)
            entities.map { it.toPayment() }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error retrieving payments by date range", e)
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
            AppLogger.e(TAG, "Error retrieving statistics", e)
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
    // Financial CSV Queries
    // ========================================

    /**
     * Get payments registered within the specified month/year.
     *
     * Filters by [Payment.paymentDate] in the closed interval
     * [firstDayOfMonth, lastDayOfMonth]. Used by the financial CSV export.
     *
     * @param yearMonth The target month/year
     * @return Payments in that month ordered by paymentDate ASC
     */
    suspend fun getPaymentsByMonth(yearMonth: YearMonth): List<Payment> {
        return try {
            val firstDay = yearMonth.atDay(1)
            val lastDay = yearMonth.atEndOfMonth()
            AppLogger.d(TAG, "Querying payments for $yearMonth ($firstDay – $lastDay)")
            val entities = paymentDao.getByDateRange(firstDay, lastDay)
            AppLogger.d(TAG, "Retrieved ${entities.size} payments for $yearMonth")
            entities.map { it.toPayment() }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error retrieving payments for month $yearMonth", e)
            throw e
        }
    }

    /**
     * Get all payment-appointment cross-refs.
     *
     * Returns every record from the payment_appointments junction table.
     * Used by the backup export to preserve payment-appointment links.
     *
     * @return List of all [PaymentAppointmentCrossRef]
     */
    suspend fun getAllPaymentCrossRefs(): List<com.psychologist.financial.data.entities.PaymentAppointmentCrossRef> {
        return try {
            AppLogger.d(TAG, "Querying all payment-appointment cross-refs for export...")
            val refs = paymentDao.getAllCrossRefs()
            AppLogger.d(TAG, "Retrieved ${refs.size} cross-refs")
            refs
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error retrieving payment cross-refs", e)
            throw e
        }
    }

    /**
     * Get all PayerInfo records.
     *
     * Returns every record from the payer_info table.
     * Used by the financial CSV and backup export to resolve
     * responsible-payer data for non-paying patients.
     *
     * @return List of all [PayerInfo] records
     */
    suspend fun getAllPayerInfos(): List<PayerInfo> {
        return try {
            AppLogger.d(TAG, "Querying all payer infos for export...")
            val entities = payerInfoDao.getAll()
            AppLogger.d(TAG, "Retrieved ${entities.size} payer infos")
            entities.map { it.toPayerInfo() }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error retrieving payer infos", e)
            throw e
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
            lastAppointmentDate = this.lastAppointmentDate,
            cpf = this.cpf,
            endereco = this.endereco,
            naoPagante = this.naoPagante
        )
    }

    private fun com.psychologist.financial.data.entities.AppointmentEntity.toAppointment(): Appointment {
        return Appointment(
            id = this.id,
            patientId = this.patientId,
            date = this.date,
            timeStart = this.timeStart,
            durationMinutes = this.durationMinutes,
            notes = this.notes,
            createdDate = this.createdDate
        )
    }

    private fun com.psychologist.financial.data.entities.PaymentEntity.toPayment(): Payment {
        return Payment(
            id = this.id,
            patientId = this.patientId,
            amount = this.amount,
            paymentDate = this.paymentDate,
            createdDate = this.createdDate
        )
    }

    private fun com.psychologist.financial.data.entities.PayerInfoEntity.toPayerInfo(): PayerInfo {
        return PayerInfo(
            id = this.id,
            patientId = this.patientId,
            nome = this.nome,
            cpf = this.cpf,
            endereco = this.endereco,
            email = this.email,
            telefone = this.telefone
        )
    }
}
