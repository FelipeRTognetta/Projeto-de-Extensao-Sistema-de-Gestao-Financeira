package com.psychologist.financial.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psychologist.financial.viewmodel.ExportViewState

/**
 * Reusable export progress display component
 *
 * Shows progress during data export operation.
 * Displays current step, record counts, and progress bar.
 *
 * Features:
 * - Indeterminate progress (validating)
 * - Determinate progress bar (in progress)
 * - Record count tracking (patients, appointments, payments)
 * - Current step message
 * - Large circular indicator or linear progress
 * - Material 3 styling
 * - Responsive layout
 *
 * Layout (In Progress):
 * ```
 * ┌────────────────────────────────┐
 * │      Circular Progress (50%)   │
 * │         50 registros           │
 * │                                │
 * │  Exportando pacientes...       │
 * │                                │
 * │  Pacientes: 150                │
 * │  Atendimentos: 0               │
 * │  Pagamentos: 0                 │
 * │                                │
 * │  Linear Progress Bar (50%)     │
 * └────────────────────────────────┘
 * ```
 *
 * Layout (Validating):
 * ```
 * ┌────────────────────────────────┐
 * │    Indeterminate Spinner       │
 * │                                │
 * │  Validando pré-requisitos...   │
 * │                                │
 * │  Circular Progress (no %)      │
 * └────────────────────────────────┘
 * ```
 *
 * Usage:
 * ```kotlin
 * when (val state = exportState.collectAsState().value) {
 *     is ExportViewState.Validating -> {
 *         ExportProgressComponent(state)
 *     }
 *     is ExportViewState.InProgress -> {
 *         ExportProgressComponent(state)
 *     }
 *     else -> {}
 * }
 * ```
 *
 * @param state Export state (Validating or InProgress)
 * @param modifier Modifier for customization
 */
@Composable
fun ExportProgressComponent(
    state: ExportViewState,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium
    ) {
        when (state) {
            is ExportViewState.Validating -> {
                ValidatingProgressContent(state)
            }

            is ExportViewState.InProgress -> {
                InProgressContent(state)
            }

            else -> {}
        }
    }
}

/**
 * Validating state content
 *
 * Shows indeterminate spinner while checking prerequisites.
 * Used while validating storage, permissions, data availability.
 *
 * @param state Validating state with message
 */
@Composable
private fun ValidatingProgressContent(
    state: ExportViewState.Validating
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Indeterminate spinner
        CircularProgressIndicator(
            modifier = Modifier.padding(vertical = 16.dp),
            color = MaterialTheme.colorScheme.primary,
            strokeCap = StrokeCap.Round
        )

        // Validation message
        Text(
            text = state.message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * In-progress state content
 *
 * Shows export progress with records being processed.
 * Displays circular progress indicator, record counts, and step message.
 *
 * @param state InProgress state with progress tracking
 */
@Composable
private fun InProgressContent(
    state: ExportViewState.InProgress
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Circular progress indicator
        CircularProgressIndicator(
            modifier = Modifier.padding(vertical = 8.dp),
            progress = { (state.totalProgress / 100f).coerceIn(0f, 1f) },
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round
        )

        // Progress percentage and total records
        Text(
            text = "${state.totalProgress}% - ${state.getTotalProcessed()} registros",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        // Current step message
        Text(
            text = state.currentStep,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        // Record counts breakdown
        RecordCountsDisplay(state)

        // Linear progress bar
        LinearProgressIndicator(
            progress = { (state.totalProgress / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round
        )
    }
}

/**
 * Record counts display
 *
 * Shows breakdown of how many records of each type have been processed.
 * Displays in a grid format.
 *
 * @param state InProgress state with record counts
 */
@Composable
private fun RecordCountsDisplay(
    state: ExportViewState.InProgress
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Patients row
        RecordCountRow(
            label = "Pacientes",
            count = state.patientsExported
        )

        // Appointments row
        RecordCountRow(
            label = "Atendimentos",
            count = state.appointmentsExported
        )

        // Payments row
        RecordCountRow(
            label = "Pagamentos",
            count = state.paymentsExported
        )
    }
}

/**
 * Individual record count row
 *
 * Shows label and count for one record type.
 *
 * @param label Record type label (e.g., "Pacientes")
 * @param count Number of records processed
 */
@Composable
private fun RecordCountRow(
    label: String,
    count: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = "$count",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp
        )
    }
}

/**
 * Export complete success indicator
 *
 * Shows success icon and message when export finishes.
 * Used in success state display.
 *
 * Features:
 * - Large green check icon
 * - Success message
 * - File count and size
 * - Export duration
 *
 * Usage:
 * ```kotlin
 * if (state is ExportViewState.Success) {
 *     ExportSuccessIndicator(state)
 * }
 * ```
 *
 * @param state Success state with export result
 * @param modifier Modifier for customization
 */
@Composable
fun ExportSuccessIndicator(
    state: ExportViewState.Success,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Success icon
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Exportação bem-sucedida",
                modifier = Modifier.padding(vertical = 8.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            // Success message
            Text(
                text = "Exportação Concluída",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            // Summary info
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Total records
                SummaryRow(
                    label = "Registros exportados",
                    value = "${state.result.totalRecords}"
                )

                // File count
                SummaryRow(
                    label = "Arquivos",
                    value = "${state.getExportedFiles().size}"
                )

                // File size
                SummaryRow(
                    label = "Tamanho",
                    value = "%.2f MB".format(state.getFileSizeMB())
                )

                // Duration
                SummaryRow(
                    label = "Tempo de exportação",
                    value = "${state.result.durationSeconds}s"
                )

                // Export date
                SummaryRow(
                    label = "Data e hora",
                    value = state.getExportDate()
                )
            }
        }
    }
}

/**
 * Summary information row
 *
 * Shows label and value pair for export summary.
 *
 * @param label Information label
 * @param value Information value
 */
@Composable
private fun SummaryRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
