package com.psychologist.financial.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.psychologist.financial.ui.components.AppointmentListItem
import com.psychologist.financial.ui.components.PaginatedLazyColumn
import com.psychologist.financial.viewmodel.AppointmentViewModel
import com.psychologist.financial.viewmodel.AppointmentViewState

/**
 * Global appointment list screen (bottom-nav Consultas tab).
 *
 * Lists all appointments from all patients, ordered by date DESC.
 * Provides FilterChips to filter by payment-pending status:
 * - "Todas" — all appointments
 * - "Com pendência" — appointments without a linked payment
 * - "Sem pendência" — appointments linked to a payment
 *
 * Uses paginated loading via [PaginatedLazyColumn] — loads [Constants.PAGE_SIZE]
 * items at a time as the user scrolls.
 *
 * @param viewModel AppointmentViewModel
 */
@Composable
fun GlobalAppointmentListScreen(
    viewModel: AppointmentViewModel,
    onPatientClick: (Long) -> Unit = { }
) {
    val paginationState by viewModel.globalPaginationState.collectAsState()
    var nameQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf(AppointmentViewState.AppointmentFilter.ALL) }

    LaunchedEffect(Unit) {
        viewModel.resetGlobalList()
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

            FilterRow(
                activeFilter = activeFilter,
                onFilterSelected = {
                    activeFilter = it
                    viewModel.setFilter(it)
                }
            )

            Box(modifier = Modifier.weight(1f)) {
                when {
                    paginationState.items.isEmpty() && paginationState.isLoading -> {
                        // initial load — PaginatedLazyColumn handles the spinner
                    }

                    paginationState.items.isEmpty() && !paginationState.isLoading -> {
                        val emptyMsg = when {
                            nameQuery.isNotEmpty() -> "Nenhuma consulta encontrada para \"$nameQuery\""
                            activeFilter == AppointmentViewState.AppointmentFilter.PENDING ->
                                "Nenhuma consulta com pagamento pendente"
                            activeFilter == AppointmentViewState.AppointmentFilter.PAID ->
                                "Nenhuma consulta com pagamento registrado"
                            else -> "Nenhuma consulta registrada"
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = emptyMsg,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                PaginatedLazyColumn(
                    items = paginationState.items,
                    isLoading = paginationState.isLoading,
                    isError = paginationState.isError,
                    allLoaded = !paginationState.hasMore,
                    onLoadMore = { viewModel.loadNextGlobalPage() },
                    modifier = Modifier.fillMaxSize(),
                    key = { it.appointment.id }
                ) { appointmentWithStatus ->
                    AppointmentListItem(
                        appointmentWithStatus = appointmentWithStatus,
                        onClick = { onPatientClick(appointmentWithStatus.appointment.patientId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterRow(
    activeFilter: AppointmentViewState.AppointmentFilter,
    onFilterSelected: (AppointmentViewState.AppointmentFilter) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = activeFilter == AppointmentViewState.AppointmentFilter.ALL,
                onClick = { onFilterSelected(AppointmentViewState.AppointmentFilter.ALL) },
                label = { Text("Todas") }
            )
        }
        item {
            FilterChip(
                selected = activeFilter == AppointmentViewState.AppointmentFilter.PENDING,
                onClick = { onFilterSelected(AppointmentViewState.AppointmentFilter.PENDING) },
                label = { Text("Com pendência") }
            )
        }
        item {
            FilterChip(
                selected = activeFilter == AppointmentViewState.AppointmentFilter.PAID,
                onClick = { onFilterSelected(AppointmentViewState.AppointmentFilter.PAID) },
                label = { Text("Sem pendência") }
            )
        }
    }
}
