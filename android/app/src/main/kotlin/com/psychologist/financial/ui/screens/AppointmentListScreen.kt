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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
    isPatientActive: Boolean = true,
    onBack: () -> Unit,
    onAddAppointment: () -> Unit,
    onSelectAppointment: (Long) -> Unit = { }
) {
    val isGlobalView = patientId == 0L

    if (isGlobalView) {
        GlobalAppointmentListScreen(
            viewModel = viewModel,
            onSelectAppointment = onSelectAppointment
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
private fun GlobalAppointmentListScreen(
    viewModel: AppointmentViewModel,
    onSelectAppointment: (Long) -> Unit
) {
    val globalState = viewModel.globalListState.collectAsState().value
    var nameQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.loadAllAppointments()
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.resetNameFilter() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Consultas") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (globalState !is AppointmentViewState.GlobalListState.Error) {
                OutlinedTextField(
                    value = nameQuery,
                    onValueChange = { nameQuery = it; viewModel.setNameFilter(it) },
                    placeholder = { Text("Buscar por nome do paciente") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    singleLine = true,
                    trailingIcon = {
                        if (nameQuery.isNotEmpty()) {
                            IconButton(onClick = { nameQuery = ""; viewModel.resetNameFilter() }) {
                                Icon(Icons.Default.Close, contentDescription = "Limpar busca")
                            }
                        }
                    }
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                when (globalState) {
                    is AppointmentViewState.GlobalListState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }

                    is AppointmentViewState.GlobalListState.Success -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item {
                                    FilterChip(
                                        selected = globalState.activeFilter == AppointmentViewState.AppointmentFilter.ALL,
                                        onClick = { viewModel.setFilter(AppointmentViewState.AppointmentFilter.ALL) },
                                        label = { Text("Todas") }
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = globalState.activeFilter == AppointmentViewState.AppointmentFilter.PENDING,
                                        onClick = { viewModel.setFilter(AppointmentViewState.AppointmentFilter.PENDING) },
                                        label = { Text("Com pendência") }
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = globalState.activeFilter == AppointmentViewState.AppointmentFilter.PAID,
                                        onClick = { viewModel.setFilter(AppointmentViewState.AppointmentFilter.PAID) },
                                        label = { Text("Sem pendência") }
                                    )
                                }
                            }

                            if (globalState.filteredAppointments.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (nameQuery.isNotEmpty())
                                            "Nenhuma consulta encontrada para \"$nameQuery\""
                                        else
                                            "Nenhuma consulta encontrada para este filtro.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentPadding = PaddingValues(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(
                                        items = globalState.filteredAppointments,
                                        key = { it.appointment.id }
                                    ) { appointmentWithStatus ->
                                        AppointmentListItem(
                                            appointmentWithStatus = appointmentWithStatus,
                                            onClick = { onSelectAppointment(appointmentWithStatus.appointment.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    is AppointmentViewState.GlobalListState.Empty -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Nenhuma Consulta",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Para registrar uma consulta, acesse o perfil do paciente.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                is AppointmentViewState.GlobalListState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = globalState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
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
    val listState = viewModel.appointmentListState.collectAsState().value
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
            when (listState) {
                is AppointmentViewState.ListState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is AppointmentViewState.ListState.Success -> {
                    AppointmentListContent(
                        appointments = listState.appointments,
                        showOnlyPending = showOnlyPending,
                        onFilterChange = { showOnlyPending = it },
                        onSelectAppointment = if (isPatientActive) onSelectAppointment else { _ -> }
                    )
                }

                is AppointmentViewState.ListState.Empty -> {
                    EmptyAppointmentsContent(
                        onAddAppointment = onAddAppointment,
                        isGlobalView = false
                    )
                }

                is AppointmentViewState.ListState.Error -> {
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
 * Appointment list content with filter chips
 */
@Composable
private fun AppointmentListContent(
    appointments: List<AppointmentWithPaymentStatus>,
    showOnlyPending: Boolean,
    onFilterChange: (Boolean) -> Unit,
    onSelectAppointment: (Long) -> Unit
) {
    val filtered = if (showOnlyPending) appointments.filter { it.hasPendingPayment } else appointments

    Column(modifier = Modifier.fillMaxSize()) {
        // Filter chips
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = !showOnlyPending,
                    onClick = { onFilterChange(false) },
                    label = { Text("Todos") }
                )
            }
            item {
                FilterChip(
                    selected = showOnlyPending,
                    onClick = { onFilterChange(true) },
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = filtered,
                    key = { it.appointment.id }
                ) { appointmentWithStatus ->
                    AppointmentListItem(
                        appointmentWithStatus = appointmentWithStatus,
                        onClick = { onSelectAppointment(appointmentWithStatus.appointment.id) }
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
