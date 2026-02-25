package com.psychologist.financial.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.psychologist.financial.domain.models.PatientBalance

/**
 * Balance summary component
 *
 * Displays patient's financial balance with metrics:
 * - Amount Due Now (excluding pending)
 * - Total Outstanding (including pending)
 * - Collection rate percentage
 * - Status indicator with color coding
 *
 * Layout:
 * ```
 * ┌─────────────────────────────────┐
 * │ Resumo de Saldo                  │
 * │ Recebido: R$ 1.500,00 (67%)      │
 * │ ████████░░ Pendente: R$ 750,00   │
 * │ Status: Parcialmente Quitado     │
 * └─────────────────────────────────┘
 * ```
 *
 * Features:
 * - Displays amount received vs outstanding
 * - Color-coded status (green/yellow/red)
 * - Progress bar showing collection rate
 * - Status label with description
 * - Compact, information-dense design
 * - Material 3 styling
 * - Responsive layout
 *
 * Example:
 * ```kotlin
 * BalanceSummary(
 *     balance = patientBalance
 * )
 * ```
 *
 * @param balance PatientBalance data to display
 */
@Composable
fun BalanceSummary(
    balance: PatientBalance
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
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
            // Title
            Text(
                text = "Resumo de Saldo",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold
            )

            // Amount Due Now and Outstanding
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Recebido",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = balance.getFormattedAmountDue(),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Pendente",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = balance.getFormattedOutstanding(),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Collection rate progress bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Taxa de Cobrança",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = balance.getCollectionRateDisplay(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                LinearProgressIndicator(
                    progress = balance.collectionPercentage / 100f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    color = when {
                        balance.isFullyPaid -> MaterialTheme.colorScheme.tertiary
                        balance.isPartialllyPaid -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.error
                    },
                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f)
                )
            }

            // Status indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Status",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = balance.getStatusLabel(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                // Status badge
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                    color = when {
                        balance.isFullyPaid -> MaterialTheme.colorScheme.tertiary
                        balance.isPartialllyPaid -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.error
                    }
                ) {
                    Text(
                        text = when {
                            balance.isFullyPaid -> "✓"
                            balance.isPartialllyPaid -> "◐"
                            else -> "✗"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        color = when {
                            balance.isFullyPaid -> MaterialTheme.colorScheme.onTertiary
                            balance.isPartialllyPaid -> MaterialTheme.colorScheme.onSecondary
                            else -> MaterialTheme.colorScheme.onError
                        },
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            // Description
            Text(
                text = balance.getStatusDescription(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/**
 * Compact balance summary (alternative)
 *
 * Minimal version for dashboard/overview screens.
 *
 * @param balance PatientBalance data to display
 */
@Composable
fun CompactBalanceSummary(
    balance: PatientBalance
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Recebido",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = balance.getFormattedAmountDue(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Pendente",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = balance.getFormattedOutstanding(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "${balance.collectionPercentage}%",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Taxa",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
