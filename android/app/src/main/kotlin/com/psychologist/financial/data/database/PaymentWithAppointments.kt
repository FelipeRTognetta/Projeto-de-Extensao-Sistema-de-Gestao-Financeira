package com.psychologist.financial.data.database

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.psychologist.financial.data.entities.AppointmentEntity
import com.psychologist.financial.data.entities.PaymentAppointmentCrossRef
import com.psychologist.financial.data.entities.PaymentEntity

/**
 * Room read model: Payment with all linked Appointments.
 *
 * Uses the @Relation annotation to load a payment and all its associated
 * appointments in a single query via the junction table (payment_appointments).
 *
 * This is the primary data structure returned by queries that need to display
 * payment details with their linked appointments (e.g., PaymentListScreen).
 *
 * Room guarantees atomicity when used with @Transaction:
 * - Loads payment
 * - Joins payment_appointments to find all appointment IDs
 * - Loads all appointments
 * - Returns in a single atomic transaction
 *
 * Example:
 * ```kotlin
 * @Query("SELECT * FROM payments WHERE patient_id = :patientId")
 * @Transaction
 * suspend fun getByPatientWithAppointments(patientId: Long): List<PaymentWithAppointments>
 *
 * // Result:
 * // PaymentWithAppointments(
 * //   payment = PaymentEntity(id=1, patientId=5, amount=150.00, paymentDate=2024-02-15),
 * //   appointments = [
 * //     AppointmentEntity(id=10, patientId=5, date=2024-02-10, ...),
 * //     AppointmentEntity(id=15, patientId=5, date=2024-02-15, ...)
 * //   ]
 * // )
 * ```
 *
 * Database mapping:
 * - @Embedded loads columns from payments table
 * - @Relation follows the FK via junction table:
 *   - parentColumn "id" = payments.id
 *   - entityColumn "id" = appointments.id
 *   - associateBy: the junction table PaymentAppointmentCrossRef
 */
data class PaymentWithAppointments(
    @Embedded
    val payment: PaymentEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = PaymentAppointmentCrossRef::class,
            parentColumn = "payment_id",
            entityColumn = "appointment_id"
        )
    )
    val appointments: List<AppointmentEntity> = emptyList()
)

/**
 * Room read model: Payment + patient name + linked Appointments.
 *
 * Used by [PaymentDao.getAllWithAppointmentsAndPatient] to include the patient
 * name from a JOIN on the parent query, alongside the @Relation appointments.
 */
data class PaymentWithAppointmentsAndPatient(
    @Embedded
    val payment: PaymentEntity,

    @ColumnInfo(name = "patient_name")
    val patientName: String = "",

    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = PaymentAppointmentCrossRef::class,
            parentColumn = "payment_id",
            entityColumn = "appointment_id"
        )
    )
    val appointments: List<AppointmentEntity> = emptyList()
)
