package com.psychologist.financial.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.psychologist.financial.domain.models.BillableHoursSummary

/**
 * Billable hours summary display component
 *
 * Displays billable hours metrics in a visually organized card format.
 * Shows total sessions, total hours, and average session duration.
 *
 * Layout:
 * ```
 * ┌─────────────────────────────────────────────┐
 * │ Resumo de Consultas                         │
 * ├─────────────────────────────────────────────┤
 * │ Total de Consultas    │ Horas      │ Média  │
 * │ [sessions]            │ [hours]h   │ [avg]h │
 * └─────────────────────────────────────────────┘
 * ```
 *
 * Features:
 * - Three-column metric display (sessions, hours, average)
 * - Material 3 card with primary container background
 * - Icons for visual clarity
 * - Responsive layout (scales with content)
 * - Portuguese labels
 * - Supports null summary (shows "Sem dados")
 *
 * Example:
 * ```kotlin
 * BillableHoursSummaryCard(
 *     summary = billableHoursSummary,
 *     modifier = Modifier.padding(16.dp)
 * )
 * ```
 *
 * @param summary BillableHoursSummary data to display (can be null)
 * @param modifier Compose modifier for layout customization
 */
@Composable
fun BillableHoursSummaryCard(
    summary: BillableHoursSummary?,
    modifier: Modifier = Modifier
) {
    if (summary == null) {
        // Empty state
        EmptyBillableHoursSummary(modifier = modifier)
        return
    }

    Card(
        modifier = modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Text(
                text = "Resumo de Consultas",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold
            )

            // Metrics Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Total Sessions Metric
                MetricColumn(
                    icon = Icons.Default.Assignment,
                    label = "Total de Consultas",
                    value = summary.totalSessions.toString(),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )

                // Total Hours Metric
                MetricColumn(
                    icon = Icons.Default.AccessTime,
                    label = "Horas",
                    value = summary.getFormattedTotalHours(),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )

                // Average Session Hours Metric
                MetricColumn(
                    icon = Icons.Default.TrendingUp,
                    label = "Média",
                    value = summary.getFormattedAverageSessionHours(),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            // Optional: Progress indicators or additional info
            if (summary.totalSessions > 0) {
                BillableHoursSummaryDetails(summary)
            }
        }
    }
}

/**
 * Detailed billable hours information
 *
 * Shows additional metrics below the main summary.
 *
 * @param summary BillableHoursSummary to extract details from
 */
@Composable
private fun BillableHoursSummaryDetails(
    summary: BillableHoursSummary
) {
    // Divider
    androidx.compose.material3.HorizontalDivider(
        modifier = Modifier.padding(vertical = 8.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f)
    )

    // Additional info row
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Primeira Consulta",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            Text(
                text = summary.firstSessionDate?.toString() ?: "N/A",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Sessões/Semana",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            Text(
                text = summary.getFormattedSessionsPerWeek(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Status",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            Text(
                text = summary.progressStatus,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Single metric column (icon + label + value)
 *
 * Reusable component for displaying a single metric in the summary.
 *
 * @param icon Icon to display above the metric
 * @param label Label text below the value
 * @param value Metric value to display
 * @param contentColor Color for icon and text
 */
@Composable
private fun MetricColumn(
    icon: ImageVector,
    label: String,
    value: String,
    contentColor: androidx.compose.ui.graphics.Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = contentColor,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Empty billable hours summary (no data state)
 *
 * Displayed when no appointments have been logged yet.
 *
 * @param modifier Compose modifier for layout customization
 */
@Composable
private fun EmptyBillableHoursSummary(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Sem Consultas",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "Nenhuma consulta registrada para este paciente.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Inline billable hours metric (compact variant)
 *
 * Minimal variant for display in lists or sidebars.
 *
 * Layout:
 * ```
 * 45 consultas | 180.5h | 4.0h média
 * ```
 *
 * @param summary BillableHoursSummary data
 * @param modifier Compose modifier
 */
@Composable
fun BillableHoursSummaryInline(
    summary: BillableHoursSummary,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sessions count
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = summary.totalSessions.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Consultas",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            // Vertical divider
            Box(
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .background(
                        MaterialTheme.colorScheme.outlineVariant,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(1.dp)
                    ),
                content = {
                    Box(modifier = Modifier
                        .fillMaxWidth(1f)
                        .padding(vertical = 8.dp))
                }
            )

            // Total hours
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = summary.getFormattedTotalHours(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Horas",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            // Vertical divider
            Box(
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .background(
                        MaterialTheme.colorScheme.outlineVariant,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(1.dp)
                    ),
                content = {
                    Box(modifier = Modifier
                        .fillMaxWidth(1f)
                        .padding(vertical = 8.dp))
                }
            )

            // Average
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = summary.getFormattedAverageSessionHours(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Média",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * Minimal billable hours badge
 *
 * Very compact variant for use in buttons, tags, etc.
 *
 * Layout:
 * ```
 * 45 consultas • 180.5h
 * ```
 *
 * @param summary BillableHoursSummary data
 * @param modifier Compose modifier
 */
@Composable
fun BillableHoursBadge(
    summary: BillableHoursSummary,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${summary.totalSessions} consultas",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "•",
                style = MaterialTheme.typography.labelSmall
            )

            Text(
                text = "${summary.getFormattedTotalHours()}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
