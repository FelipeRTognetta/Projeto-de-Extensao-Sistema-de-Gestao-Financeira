package com.psychologist.financial.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.unit.dp
import com.psychologist.financial.domain.models.Patient
import com.psychologist.financial.domain.models.PaginationState
import com.psychologist.financial.ui.components.ErrorDialog
import com.psychologist.financial.ui.components.PaginatedLazyColumn
import com.psychologist.financial.ui.components.PatientListItem
import com.psychologist.financial.viewmodel.PatientViewModel

/**
 * Patient list screen
 *
 * Shows list of patients with:
 * - Search bar for name filtering (server-side, resets to page 1 on change)
 * - Filter chips (Active/Todos)
 * - Paginated patient list via PaginatedLazyColumn
 * - Floating Action Button to add new patient
 * - Error handling and empty state
 *
 * Navigation:
 * - Click patient → PatientDetailScreen
 * - FAB (Add button) → PatientFormScreen
 *
 * @param viewModel PatientViewModel for state management
 * @param onPatientClick Callback when patient tapped (pass patientId)
 * @param onAddClick Callback when Add button tapped
 */
@Composable
fun PatientListScreen(
    viewModel: PatientViewModel,
    onPatientClick: (Long) -> Unit,
    onAddClick: () -> Unit
) {
    val paginationState by viewModel.paginationState.collectAsState()
    val includeInactive by viewModel.includeInactivePatients.collectAsState()
    val errorMessage by viewModel.error.collectAsState()
    val pendingPatientIds by viewModel.pendingPatientIds.collectAsState()
    var nameQuery by remember { mutableStateOf("") }

    // Reset pagination and load first page on each screen entry (FR-011)
    LaunchedEffect(Unit) {
        viewModel.resetAndLoad()
    }

    // Clear name filter state when leaving screen
    DisposableEffect(Unit) {
        onDispose { viewModel.resetNameFilter() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pacientes") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddClick,
                icon = { Icon(Icons.Default.Add, "Adicionar") },
                text = { Text("Novo") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search + filter controls
            OutlinedTextField(
                value = nameQuery,
                onValueChange = { query ->
                    nameQuery = query
                    viewModel.setNameFilter(query)
                },
                placeholder = { Text("Buscar por nome") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                trailingIcon = {
                    if (nameQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            nameQuery = ""
                            viewModel.resetNameFilter()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Limpar busca")
                        }
                    }
                }
            )
            FilterChips(
                includeInactive = includeInactive,
                onFilterChange = { viewModel.toggleInactiveFilter() }
            )

            // Content fills remaining space below filter controls
            Box(modifier = Modifier.weight(1f)) {
                when {
                    paginationState.items.isEmpty() && !paginationState.isLoading && !paginationState.isError ->
                        EmptyListContent(onAddClick = onAddClick)

                    else -> PaginatedPatientList(
                        paginationState = paginationState,
                        pendingPatientIds = pendingPatientIds,
                        onPatientClick = onPatientClick,
                        onLoadMore = { viewModel.loadNextPage() }
                    )
                }
            }
        }
    }

    // Error dialog for non-list errors (e.g. marking inactive)
    if (errorMessage != null) {
        ErrorDialog(
            message = errorMessage!!,
            onDismiss = { viewModel.clearError() },
            onRetry = { viewModel.resetAndLoad() }
        )
    }
}

@Composable
private fun PaginatedPatientList(
    paginationState: PaginationState<Patient>,
    pendingPatientIds: Set<Long>,
    onPatientClick: (Long) -> Unit,
    onLoadMore: () -> Unit
) {
    PaginatedLazyColumn(
        items = paginationState.items,
        isLoading = paginationState.isLoading,
        isError = paginationState.isError,
        allLoaded = paginationState.allLoaded,
        onLoadMore = onLoadMore,
        modifier = Modifier.fillMaxSize(),
        key = { patient -> patient.id }
    ) { patient ->
        PatientListItem(
            patient = patient,
            onClick = { onPatientClick(patient.id) },
            hasPendingPayments = patient.id in pendingPatientIds
        )
    }
}

@Composable
private fun EmptyListContent(
    onAddClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Nenhum resultado encontrado",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Text(
            text = "Toque no botão + para adicionar seu primeiro paciente",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp)
        )
    }
}

@Composable
private fun FilterChips(
    includeInactive: Boolean,
    onFilterChange: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = !includeInactive,
            onClick = { if (includeInactive) onFilterChange() },
            label = { Text("Ativos") }
        )

        FilterChip(
            selected = includeInactive,
            onClick = { if (!includeInactive) onFilterChange() },
            label = { Text("Todos") }
        )
    }
}
