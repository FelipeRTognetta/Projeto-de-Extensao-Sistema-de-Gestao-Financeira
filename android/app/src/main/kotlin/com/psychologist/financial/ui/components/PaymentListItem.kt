package com.psychologist.financial.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.psychologist.financial.data.repositories.PaymentWithDetails
import com.psychologist.financial.domain.models.Appointment
import com.psychologist.financial.domain.models.Payment
import java.time.format.DateTimeFormatter

/**
 * Payment list item card
 *
 * Displays payment summary: amount, patient name, date, and linked appointments.
 * No status or method fields (removed in v3).
 *
 * @param paymentWithDetails Payment with linked appointments
 * @param patientName Patient name for display
 * @param onClick Callback when item is tapped
 */
@Composable
fun PaymentListItem(
    paymentWithDetails: PaymentWithDetails,
    patientName: String = "",
    onClick: () -> Unit = {}
) {
    val payment = paymentWithDetails.payment
    val appointments = paymentWithDetails.appointments
    val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Amount + patient name
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = payment.displayAmount,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                if (patientName.isNotEmpty()) {
                    Text(
                        text = patientName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            // Payment date
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = payment.paymentDate.format(dateFormatter),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Linked appointments
            if (appointments.isNotEmpty()) {
                val appointmentDates = appointments.joinToString(", ") {
                    it.date.format(DateTimeFormatter.ofPattern("dd/MM"))
                }
                val label = if (appointments.size == 1) {
                    "1 consulta: $appointmentDates"
                } else {
                    "${appointments.size} consultas: $appointmentDates"
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Event,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * Legacy overload accepting a plain Payment (no appointments).
 * Used by patient-scoped payment lists.
 */
@Composable
fun PaymentListItem(
    payment: Payment,
    onClick: () -> Unit = {}
) {
    PaymentListItem(
        paymentWithDetails = PaymentWithDetails(payment = payment, appointments = emptyList()),
        patientName = "",
        onClick = onClick
    )
}
