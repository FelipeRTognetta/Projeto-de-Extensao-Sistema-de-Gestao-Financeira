package com.psychologist.financial.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
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
import com.psychologist.financial.ui.components.ErrorDialog
import com.psychologist.financial.ui.components.PatientListItem
import com.psychologist.financial.viewmodel.PatientViewModel
import com.psychologist.financial.viewmodel.PatientViewState.ListState

/**
 * Patient list screen
 *
 * Shows list of patients with:
 * - Search bar (TODO)
 * - Filter chips (Active/Inactive)
 * - Patient list in LazyColumn
 * - Floating Action Button to add new patient
 * - Error handling and loading states
 *
 * Navigation:
 * - Click patient → PatientDetailScreen
 * - FAB (Add button) → PatientFormScreen
 *
 * Usage:
 * ```kotlin
 * PatientListScreen(
 *     viewModel = patientViewModel,
 *     onPatientClick = { navigateToDetail(it) },
 *     onAddClick = { navigateToForm() }
 * )
 * ```
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
    val listState = viewModel.patientListState.collectAsState().value
    val includeInactive = viewModel.includeInactivePatients.collectAsState().value
    val isLoading = viewModel.isLoading.collectAsState().value
    val errorMessage = viewModel.error.collectAsState().value
    var nameQuery by remember { mutableStateOf("") }

    // Load patients on first composition
    LaunchedEffect(Unit) {
        viewModel.loadPatients()
    }

    // Reset name filter when leaving screen
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
            if (listState !is ListState.Error && errorMessage == null) {
                OutlinedTextField(
                    value = nameQuery,
                    onValueChange = { nameQuery = it; viewModel.setNameFilter(it) },
                    placeholder = { Text("Buscar por nome") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    singleLine = true,
                    trailingIcon = {
                        if (nameQuery.isNotEmpty()) {
                            IconButton(onClick = { nameQuery = ""; viewModel.resetNameFilter() }) {
                                Icon(Icons.Default.Close, contentDescription = "Limpar busca")
                            }
                        }
                    }
                )
                FilterChips(
                    includeInactive = includeInactive,
                    onFilterChange = { viewModel.toggleInactiveFilter() }
                )
            }

            // Content fills remaining space below filter chips
            Box(modifier = Modifier.weight(1f)) {
                when {
                    isLoading -> LoadingContent()

                    errorMessage != null -> ErrorContent(
                        message = errorMessage,
                        onRetry = { viewModel.loadPatients() },
                        onDismiss = { viewModel.clearError() }
                    )

                    listState is ListState.Loading -> LoadingContent()

                    listState is ListState.Success -> PatientListContent(
                        patients = listState.patients,
                        pendingPatientIds = viewModel.pendingPatientIds.collectAsState().value,
                        onPatientClick = onPatientClick,
                        onRefresh = { viewModel.refreshPatients() }
                    )

                    listState is ListState.Empty -> EmptyListContent(
                        onAddClick = onAddClick
                    )

                    listState is ListState.Error -> ErrorContent(
                        message = listState.message,
                        onRetry = { viewModel.loadPatients() },
                        onDismiss = { viewModel.clearError() }
                    )
                }
            }
        }
    }

    // Error dialog
    if (errorMessage != null) {
        ErrorDialog(
            message = errorMessage,
            onDismiss = { viewModel.clearError() },
            onRetry = { viewModel.loadPatients() }
        )
    }
}

/**
 * Loading state UI
 */
@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * Patient list content
 */
@Composable
private fun PatientListContent(
    patients: List<com.psychologist.financial.domain.models.Patient>,
    pendingPatientIds: Set<Long>,
    onPatientClick: (Long) -> Unit,
    onRefresh: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 0.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        items(
            items = patients,
            key = { it.id }
        ) { patient ->
            PatientListItem(
                patient = patient,
                onClick = { onPatientClick(patient.id) },
                hasPendingPayments = patient.id in pendingPatientIds
            )
        }
    }
}

/**
 * Empty state UI
 */
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
            text = "Nenhum paciente cadastrado",
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

/**
 * Error state UI
 */
@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Erro ao carregar",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp)
        )
    }
}

/**
 * Filter chips for showing active/inactive toggle
 */
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
