package com.psychologist.financial.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Junction entity for many-to-many relationship between Payment and Appointment.
 *
 * Implements the bridge table pattern: payment_appointments.
 * Allows a single payment to be linked to multiple appointments (and vice versa).
 *
 * Database Constraints:
 * - Composite primary key (payment_id, appointment_id) ensures uniqueness
 * - payment_id: Foreign key to PaymentEntity, cascading delete
 * - appointment_id: Foreign key to AppointmentEntity, cascading delete
 * - Indices on both FK columns for efficient lookups in both directions
 *
 * Deletion behavior:
 * - If payment is deleted → cascade deletes all junction rows for that payment
 * - If appointment is deleted → cascade deletes all junction rows for that appointment
 *
 * Usage:
 * - Inserted when creating a payment with linked appointments
 * - Queried to find all appointments for a payment, or all payments for an appointment
 * - Used by DAO @Relation to populate PaymentWithAppointments read model
 *
 * Example:
 * ```kotlin
 * val link = PaymentAppointmentCrossRef(
 *     paymentId = 5L,
 *     appointmentId = 10L
 * )
 * ```
 */
@Entity(
    tableName = "payment_appointments",
    primaryKeys = ["payment_id", "appointment_id"],
    foreignKeys = [
        ForeignKey(
            entity = PaymentEntity::class,
            parentColumns = ["id"],
            childColumns = ["payment_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AppointmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["appointment_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(name = "idx_pa_payment_id", value = ["payment_id"]),
        Index(name = "idx_pa_appointment_id", value = ["appointment_id"])
    ]
)
data class PaymentAppointmentCrossRef(
    @ColumnInfo(name = "payment_id")
    val paymentId: Long,

    @ColumnInfo(name = "appointment_id")
    val appointmentId: Long
)
