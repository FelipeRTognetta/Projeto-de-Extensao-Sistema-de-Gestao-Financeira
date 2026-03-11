package com.psychologist.financial.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.psychologist.financial.domain.models.AppointmentWithPaymentStatus
import com.psychologist.financial.ui.components.AppointmentListItem
import com.psychologist.financial.ui.components.PaginatedLazyColumn
import com.psychologist.financial.viewmodel.AppointmentViewModel

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
    isPatientActive: Boolean = true,
    onBack: () -> Unit,
    onAddAppointment: () -> Unit,
    onSelectAppointment: (Long) -> Unit = { },
    onPatientClick: (Long) -> Unit = { }
) {
    val isGlobalView = patientId == 0L

    if (isGlobalView) {
        GlobalAppointmentListScreen(
            viewModel = viewModel,
            onPatientClick = onPatientClick
        )
    } else {
        PatientAppointmentListScreen(
            viewModel = viewModel,
            patientId = patientId,
            patientName = patientName,
            isPatientActive = isPatientActive,
            onBack = onBack,
            onAddAppointment = onAddAppointment,
            onSelectAppointment = onSelectAppointment
        )
    }
}

@Composable
private fun PatientAppointmentListScreen(
    viewModel: AppointmentViewModel,
    patientId: Long,
    patientName: String,
    isPatientActive: Boolean,
    onBack: () -> Unit,
    onAddAppointment: () -> Unit,
    onSelectAppointment: (Long) -> Unit
) {
    val paginationState by viewModel.perPatientPaginationState.collectAsState()
    var showOnlyPending by remember { mutableStateOf(false) }

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
                onClick = { if (isPatientActive) onAddAppointment() },
                containerColor = if (isPatientActive)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                modifier = Modifier
                    .testTag("fab_add_appointment")
                    .then(
                        if (!isPatientActive) Modifier
                            .alpha(0.38f)
                            .semantics { disabled() }
                        else Modifier
                    )
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
            val filtered = if (showOnlyPending)
                paginationState.items.filter { it.hasPendingPayment }
            else
                paginationState.items

            when {
                paginationState.items.isEmpty() && paginationState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                paginationState.items.isEmpty() && !paginationState.isLoading -> {
                    EmptyAppointmentsContent(
                        onAddAppointment = onAddAppointment,
                        isGlobalView = false
                    )
                }

                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                FilterChip(
                                    selected = !showOnlyPending,
                                    onClick = { showOnlyPending = false },
                                    label = { Text("Todos") }
                                )
                            }
                            item {
                                FilterChip(
                                    selected = showOnlyPending,
                                    onClick = { showOnlyPending = true },
                                    label = { Text("Pagamento em aberto") }
                                )
                            }
                        }

                        if (filtered.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Nenhuma consulta com pagamento pendente.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            PaginatedLazyColumn(
                                items = filtered,
                                isLoading = paginationState.isLoading,
                                isError = paginationState.isError,
                                allLoaded = !paginationState.hasMore,
                                onLoadMore = { viewModel.loadNextPatientAppointmentsPage() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                key = { it.appointment.id }
                            ) { appointmentWithStatus ->
                                AppointmentListItem(
                                    appointmentWithStatus = appointmentWithStatus,
                                    onClick = {
                                        if (isPatientActive) onSelectAppointment(appointmentWithStatus.appointment.id)
                                    }
                                )
                            }
                        }
                    }
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
    onAddAppointment: () -> Unit,
    isGlobalView: Boolean = false
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
            text = if (isGlobalView)
                "Para registrar uma consulta, acesse o perfil do paciente."
            else
                "Ainda não há consultas registradas para este paciente.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (!isGlobalView) {
            androidx.compose.foundation.layout.Spacer(
                modifier = Modifier.height(24.dp)
            )

            Button(onClick = onAddAppointment) {
                Text("Registrar Primeira Consulta")
            }
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
