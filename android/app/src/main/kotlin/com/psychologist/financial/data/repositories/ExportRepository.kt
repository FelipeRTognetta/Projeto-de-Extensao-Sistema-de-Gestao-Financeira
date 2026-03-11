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
 *
 * Public API:
 * - [getAllPatients]: All patients for export statistics and financial CSV
 * - [getAllAppointments]: All appointments for export statistics
 * - [getAllPayments]: All payments for export statistics
 * - [getPaymentsByMonth]: Payments in a given month for financial CSV export
 * - [getAllPayerInfos]: All payer info records for financial CSV export
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
     * Get all patients (active and inactive).
     *
     * Used for export statistics and financial CSV patient resolution.
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

    // ========================================
    // Appointment Queries
    // ========================================

    /**
     * Get all appointments.
     *
     * Used for export statistics.
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

    // ========================================
    // Payment Queries
    // ========================================

    /**
     * Get all payments.
     *
     * Used for export statistics.
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
     * Used by the financial CSV export to resolve responsible-payer data
     * for non-paying patients.
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
