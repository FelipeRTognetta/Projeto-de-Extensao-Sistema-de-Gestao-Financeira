package com.psychologist.financial.ui.screens

import android.content.Intent
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.psychologist.financial.ui.components.ErrorDialog
import com.psychologist.financial.ui.components.ExportProgressComponent
import com.psychologist.financial.ui.components.ExportSuccessIndicator
import com.psychologist.financial.viewmodel.ExportViewModel
import com.psychologist.financial.viewmodel.ExportViewState

/**
 * Export data screen
 *
 * Allows users to export patient, appointment, and payment data to CSV files.
 * Shows export statistics before exporting, progress during export,
 * and success with file sharing options.
 *
 * Features:
 * - Display export statistics (record counts, storage)
 * - Export button with validation
 * - Progress indication during export
 * - Success message with file info
 * - Error handling with retry
 * - File sharing options
 * - Cleanup of old exports
 *
 * Layout:
 * ```
 * ┌─────────────────────────────────┐
 * │ Export Data                      │
 * ├─────────────────────────────────┤
 * │ Statistics:                      │
 * │ • Pacientes: 150                 │
 * │ • Atendimentos: 500              │
 * │ • Pagamentos: 1200               │
 * │ • Total: 1850 registros          │
 * │                                  │
 * │ Storage: 2048 MB disponível      │
 * ├─────────────────────────────────┤
 * │         [EXPORTAR AGORA]         │
 * └─────────────────────────────────┘
 * ```
 *
 * States:
 * - Idle: Show statistics and export button
 * - Validating: Show validation progress
 * - InProgress: Show export progress with counts
 * - Success: Show success message with file info
 * - Error: Show error with retry option
 *
 * Usage:
 * ```kotlin
 * ExportScreen(
 *     viewModel = exportViewModel
 * )
 * ```
 *
 * @param viewModel ExportViewModel for state management
 */
@Composable
fun ExportScreen(
    viewModel: ExportViewModel
) {
    val exportState = viewModel.exportState.collectAsState()
    val currentState = exportState.value
    val isExporting = viewModel.isExporting.collectAsState()
    val error = viewModel.error.collectAsState()

    Log.d("ExportScreen", "Rendering with state: ${currentState::class.simpleName}")

    // Load statistics on screen open
    LaunchedEffect(Unit) {
        viewModel.loadExportStatistics()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        when (currentState) {
            // Validating state
            is ExportViewState.Validating -> {
                ValidatingScreen(
                    paddingValues = paddingValues,
                    state = currentState
                )
            }

            // In-progress state
            is ExportViewState.InProgress -> {
                ProgressScreen(
                    paddingValues = paddingValues,
                    state = currentState,
                    onCancel = { viewModel.cancelExport() }
                )
            }

            // Success state
            is ExportViewState.Success -> {
                SuccessScreen(
                    paddingValues = paddingValues,
                    state = currentState,
                    viewModel = viewModel
                )
            }

            // Error state
            is ExportViewState.Error -> {
                ErrorScreen(
                    paddingValues = paddingValues,
                    state = currentState,
                    onRetry = { viewModel.retryExport() },
                    onDismiss = { viewModel.dismissError() }
                )
            }

            // Idle/ready state
            is ExportViewState.Idle -> {
                IdleScreen(
                    paddingValues = paddingValues,
                    state = currentState,
                    isExporting = isExporting.value,
                    viewModel = viewModel
                )
            }
        }
    }

    // Show error dialog if present
    if (error.value != null) {
        ErrorDialog(
            message = error.value ?: "Unknown error",
            onDismiss = { viewModel.clearError() },
            onRetry = { viewModel.performExport() },
            title = "Erro na Exportação"
        )
    }
}

/**
 * Idle/ready state screen
 *
 * Shows export statistics and ready to export.
 * User can tap export button to start.
 *
 * @param paddingValues Scaffold padding
 * @param state Idle state with statistics
 * @param isExporting Whether export is currently running
 * @param viewModel Export ViewModel
 */
@Composable
private fun IdleScreen(
    paddingValues: PaddingValues,
    state: ExportViewState.Idle,
    isExporting: Boolean,
    viewModel: ExportViewModel
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Exportar Dados",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Faça backup de todos os seus dados para arquivo CSV",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Statistics card
        item {
            StatisticsCard(state)
        }

        // Storage info card
        item {
            StorageInfoCard(state)
        }

        // Export button
        item {
            Button(
                onClick = { viewModel.performExport() },
                enabled = state.hasData() && !isExporting,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = if (state.hasData()) "EXPORTAR AGORA" else "Nenhum Dado para Exportar",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Info text
        item {
            Text(
                text = "Os arquivos serão salvos em formato CSV " +
                    "e podem ser abertos em aplicativos de planilha como Excel ou Google Sheets.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Statistics card
 *
 * Shows breakdown of records to be exported.
 *
 * @param state Idle state with record counts
 */
@Composable
private fun StatisticsCard(
    state: ExportViewState.Idle
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Dados a Exportar",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            StatisticRow(
                label = "Pacientes",
                value = "${state.patientCount}",
                color = MaterialTheme.colorScheme.primary
            )

            StatisticRow(
                label = "Atendimentos",
                value = "${state.appointmentCount}",
                color = MaterialTheme.colorScheme.secondary
            )

            StatisticRow(
                label = "Pagamentos",
                value = "${state.paymentCount}",
                color = MaterialTheme.colorScheme.tertiary
            )

            // Divider
            androidx.compose.material3.Divider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )

            // Total
            StatisticRow(
                label = "Total de Registros",
                value = "${state.totalRecords}",
                color = MaterialTheme.colorScheme.primary,
                bold = true,
                fontSize = 16.sp
            )
        }
    }
}

/**
 * Storage info card
 *
 * Shows available storage space for export.
 *
 * @param state Idle state with storage info
 */
@Composable
private fun StorageInfoCard(
    state: ExportViewState.Idle
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Armazenamento",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Espaço disponível",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "${state.availableStorageMB} MB",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Statistic row display
 *
 * Shows label and value with color coding.
 *
 * @param label Statistic label
 * @param value Statistic value
 * @param color Color for the value
 * @param bold Whether to make text bold
 * @param fontSize Font size for value
 */
@Composable
private fun StatisticRow(
    label: String,
    value: String,
    color: Color,
    bold: Boolean = false,
    fontSize: androidx.compose.ui.unit.TextUnit = 14.sp
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
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
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = color,
            fontSize = fontSize
        )
    }
}

/**
 * Validating state screen
 *
 * Shows validation progress while checking prerequisites.
 *
 * @param paddingValues Scaffold padding
 * @param state Validating state
 */
@Composable
private fun ValidatingScreen(
    paddingValues: PaddingValues,
    state: ExportViewState.Validating
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ExportProgressComponent(state)
    }
}

/**
 * Progress state screen
 *
 * Shows export progress with detailed record counts.
 *
 * @param paddingValues Scaffold padding
 * @param state InProgress state
 * @param onCancel Callback to cancel export
 */
@Composable
private fun ProgressScreen(
    paddingValues: PaddingValues,
    state: ExportViewState.InProgress,
    onCancel: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            ExportProgressComponent(state)
        }

        item {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text("Cancelar Exportação")
            }
        }
    }
}

/**
 * Success state screen
 *
 * Shows export completion with file info and sharing options.
 *
 * @param paddingValues Scaffold padding
 * @param state Success state
 * @param viewModel Export ViewModel
 */
@Composable
private fun SuccessScreen(
    paddingValues: PaddingValues,
    state: ExportViewState.Success,
    viewModel: ExportViewModel
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ExportSuccessIndicator(state)
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Share button
                Button(
                    onClick = {
                        val files = state.result.getFiles()
                        if (files.isNotEmpty()) {
                            val uris = ArrayList(files.map { file ->
                                FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                            })
                            val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                type = "text/csv"
                                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(
                                Intent.createChooser(shareIntent, "Compartilhar arquivos CSV")
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("COMPARTILHAR ARQUIVOS")
                }

                // New export button
                OutlinedButton(
                    onClick = { viewModel.loadExportStatistics() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("NOVA EXPORTAÇÃO")
                }
            }
        }

        item {
            Text(
                text = "Os arquivos foram salvos com sucesso. " +
                    "Você pode encontrá-los na pasta de aplicativos no armazenamento do dispositivo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Error state screen
 *
 * Shows error message with retry and dismiss options.
 *
 * @param paddingValues Scaffold padding
 * @param state Error state
 * @param onRetry Callback to retry export
 * @param onDismiss Callback to dismiss error
 */
@Composable
private fun ErrorScreen(
    paddingValues: PaddingValues,
    state: ExportViewState.Error,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Erro na Exportação",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center
                )

                // Retry button
                Button(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("TENTAR NOVAMENTE")
                }

                // Dismiss button
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("FECHAR")
                }
            }
        }
    }
}
