package com.psychologist.financial.domain.models

/**
 * Domain model: Appointment with derived payment status.
 *
 * Wraps an Appointment with a boolean flag indicating whether the appointment
 * has a pending payment (no payment linked to it).
 *
 * The `hasPendingPayment` state is DERIVED, not stored:
 * - Calculated by the absence of a row in the payment_appointments junction table
 * - Always consistent with database state
 * - Computed at query time (DAO joins on junction table)
 *
 * This model is used in views that need to filter appointments by payment status,
 * such as the appointment list screen with pending/paid filters.
 *
 * Usage:
 * ```kotlin
 * // From DAO query:
 * val appointmentsWithStatus: List<AppointmentWithPaymentStatus> = appointmentDao.getAllWithPaymentStatus()
 *
 * // Filter by payment status:
 * val pendingAppointments = appointmentsWithStatus.filter { it.hasPendingPayment }
 * val paidAppointments = appointmentsWithStatus.filter { !it.hasPendingPayment }
 *
 * // Render in UI with visual indicator:
 * appointmentsWithStatus.forEach { item ->
 *     if (item.hasPendingPayment) {
 *         showPendingBadge(item.appointment)
 *     } else {
 *         hidePendingBadge(item.appointment)
 *     }
 * }
 * ```
 *
 * Database mapping (Room DAO):
 * - SELECT a.*, (payment_appointments.payment_id IS NULL) as has_pending_payment
 * - FROM appointments a
 * - LEFT JOIN payment_appointments ON a.id = payment_appointments.appointment_id
 *
 * Visual representation:
 * - hasPendingPayment = true  → Show "Pagamento em aberto" badge
 * - hasPendingPayment = false → No badge (appointment is paid)
 *
 * @param appointment The appointment data
 * @param hasPendingPayment True if appointment has no payment linked, false if payment is linked
 */
data class AppointmentWithPaymentStatus(
    val appointment: Appointment,
    val hasPendingPayment: Boolean,
    val patientName: String = ""
)
