package com.psychologist.financial.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.psychologist.financial.domain.models.Appointment
import com.psychologist.financial.ui.components.AppointmentListItem
import com.psychologist.financial.viewmodel.AppointmentViewModel
import com.psychologist.financial.viewmodel.AppointmentViewState

/**
 * Appointment list screen
 *
 * Shows patient's appointment history with sessions sorted chronologically.
 * Allows creating new appointments and viewing details.
 *
 * Features:
 * - Display list of patient's appointments (newest first)
 * - Filter by status (all, upcoming, past)
 * - FAB button to add new appointment
 * - Loading, empty, and error states
 * - Navigate to detail or form screens
 *
 * Navigation:
 * - Back button → PatientDetailScreen
 * - FAB button → AppointmentFormScreen
 * - Appointment item → AppointmentDetailScreen (TODO)
 *
 * Usage:
 * ```kotlin
 * AppointmentListScreen(
 *     viewModel = appointmentViewModel,
 *     patientId = 1L,
 *     patientName = "João Silva",
 *     onBack = { navigateBack() },
 *     onAddAppointment = { navigateToForm(patientId) },
 *     onSelectAppointment = { appointmentId -> navigateToDetail(appointmentId) }
 * )
 * ```
 *
 * @param viewModel AppointmentViewModel for data
 * @param patientId Patient ID to load appointments for
 * @param patientName Patient name for display
 * @param onBack Callback when back button tapped
 * @param onAddAppointment Callback when FAB tapped
 * @param onSelectAppointment Callback when appointment item tapped
 */
@Composable
fun AppointmentListScreen(
    viewModel: AppointmentViewModel,
    patientId: Long,
    patientName: String = "",
    onBack: () -> Unit,
    onAddAppointment: () -> Unit,
    onSelectAppointment: (Long) -> Unit = { }
) {
    val listState = viewModel.appointmentListState.collectAsState().value
    val billableHours = viewModel.billableHoursSummary.collectAsState().value

    // Load appointments when screen opens
    LaunchedEffect(patientId) {
        viewModel.loadPatientAppointments(patientId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Consultas")
                        if (patientName.isNotEmpty()) {
                            Text(
                                text = patientName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddAppointment,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, "Adicionar Consulta")
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (listState) {
                is AppointmentViewState.ListState.Loading -> {
                    // Loading state
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is AppointmentViewState.ListState.Success -> {
                    // Appointment list
                    AppointmentListContent(
                        appointments = listState.appointments,
                        billableHoursSummary = billableHours,
                        onSelectAppointment = onSelectAppointment
                    )
                }

                is AppointmentViewState.ListState.Empty -> {
                    // Empty state
                    EmptyAppointmentsContent(
                        onAddAppointment = onAddAppointment
                    )
                }

                is AppointmentViewState.ListState.Error -> {
                    // Error state
                    ErrorAppointmentsContent(
                        message = listState.message,
                        onBack = onBack
                    )
                }
            }
        }
    }
}

/**
 * Appointment list content
 */
@Composable
private fun AppointmentListContent(
    appointments: List<Appointment>,
    billableHoursSummary: com.psychologist.financial.domain.models.BillableHoursSummary?,
    onSelectAppointment: (Long) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Billable hours summary card
        if (billableHoursSummary != null) {
            BillableHoursSummaryCard(summary = billableHoursSummary)
        }

        // Appointment list
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = appointments,
                key = { it.id }
            ) { appointment ->
                AppointmentListItem(
                    appointment = appointment,
                    onClick = { onSelectAppointment(appointment.id) }
                )
            }
        }
    }
}

/**
 * Billable hours summary card
 */
@Composable
private fun BillableHoursSummaryCard(
    summary: com.psychologist.financial.domain.models.BillableHoursSummary
) {
    androidx.compose.material3.Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Resumo de Consultas",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${summary.totalSessions}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Total de Consultas",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = summary.getFormattedTotalHours(),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Horas",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = summary.getFormattedAverageSessionHours(),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Média",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

/**
 * Empty appointments content
 */
@Composable
private fun EmptyAppointmentsContent(
    onAddAppointment: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Nenhuma Consulta",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        androidx.compose.foundation.layout.Spacer(
            modifier = Modifier.height(12.dp)
        )

        Text(
            text = "Ainda não há consultas registradas para este paciente.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        androidx.compose.foundation.layout.Spacer(
            modifier = Modifier.height(24.dp)
        )

        Button(onClick = onAddAppointment) {
            Text("Registrar Primeira Consulta")
        }
    }
}

/**
 * Error appointments content
 */
@Composable
private fun ErrorAppointmentsContent(
    message: String,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Erro ao Carregar",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error
        )

        androidx.compose.foundation.layout.Spacer(
            modifier = Modifier.height(12.dp)
        )

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        androidx.compose.foundation.layout.Spacer(
            modifier = Modifier.height(24.dp)
        )

        TextButton(onClick = onBack) {
            Text("Voltar")
        }
    }
}
