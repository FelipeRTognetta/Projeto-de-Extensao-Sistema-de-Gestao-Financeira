package com.psychologist.financial.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.psychologist.financial.ui.theme.WarningColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.psychologist.financial.domain.models.Payment

/**
 * Payment list item card
 *
 * Displays payment summary in a card format.
 * Shows amount, date, status, method, and optional appointment link.
 *
 * Layout:
 * ```
 * ┌─────────────────────────────────┐
 * │ R$ 150,00  │  Pago    │  Débito │
 * │ 15/03/2024 | Pix                │
 * └─────────────────────────────────┘
 * ```
 *
 * Features:
 * - Amount display with currency
 * - Status badge (paid/pending/overdue)
 * - Payment method display
 * - Date display
 * - Color-coded status indicators
 * - Click handler for selection
 * - Material 3 styling
 * - Responsive layout
 *
 * Example:
 * ```kotlin
 * PaymentListItem(
 *     payment = payment,
 *     onClick = { navigateToDetail(payment.id) }
 * )
 * ```
 *
 * @param payment Payment data to display
 * @param onClick Callback when item is tapped
 */
@Composable
fun PaymentListItem(
    payment: Payment,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Amount, Status, Method
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = payment.displayAmount,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = payment.getMethodDisplay(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                // Status badge
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                    color = when {
                        payment.isPaid -> MaterialTheme.colorScheme.tertiary
                        payment.isPastDue -> MaterialTheme.colorScheme.error
                        else -> WarningColor
                    },
                    contentColor = when {
                        payment.isPaid -> MaterialTheme.colorScheme.onTertiary
                        payment.isPastDue -> MaterialTheme.colorScheme.onError
                        else -> MaterialTheme.colorScheme.onError
                    }
                ) {
                    Text(
                        text = payment.getStatusLabel(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Divider
            androidx.compose.material3.HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // Footer: Date and appointment info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        modifier = Modifier.padding(0.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = payment.getFormattedDate(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Appointment indicator if linked
                if (payment.isLinkedToAppointment) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachMoney,
                            contentDescription = null,
                            modifier = Modifier.padding(0.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Consulta",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact payment list item (alternative)
 *
 * Condensed version for summary/timeline views.
 *
 * Layout:
 * ```
 * R$ 150,00 | Pago | 15/03
 * ```
 *
 * @param payment Payment data to display
 * @param onClick Callback when item is tapped
 */
@Composable
fun CompactPaymentListItem(
    payment: Payment,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = payment.displayAmount,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = payment.getFormattedDate(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
            color = when {
                payment.isPaid -> MaterialTheme.colorScheme.tertiary
                payment.isPastDue -> MaterialTheme.colorScheme.error
                else -> WarningColor
            }
        ) {
            Text(
                text = payment.getStatusLabel(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}
