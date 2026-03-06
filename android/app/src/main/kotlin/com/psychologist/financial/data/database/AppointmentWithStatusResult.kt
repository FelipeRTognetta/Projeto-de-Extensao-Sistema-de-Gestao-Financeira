package com.psychologist.financial.data.database

import androidx.room.ColumnInfo
import androidx.room.Embedded
import com.psychologist.financial.data.entities.AppointmentEntity

/**
 * Room read model: AppointmentEntity + derived payment-pending flag.
 *
 * Used by [AppointmentDao.getAllWithPaymentStatus] to map the LEFT JOIN
 * query result. Room maps:
 * - All appointment columns → [appointment] via @Embedded
 * - `has_pending_payment` column → [hasPendingPayment] via @ColumnInfo
 *
 * This class stays in the data layer; the repository converts it to the
 * domain model [com.psychologist.financial.domain.models.AppointmentWithPaymentStatus].
 */
data class AppointmentWithStatusResult(
    @Embedded val appointment: AppointmentEntity,
    @ColumnInfo(name = "has_pending_payment") val hasPendingPayment: Boolean
)
