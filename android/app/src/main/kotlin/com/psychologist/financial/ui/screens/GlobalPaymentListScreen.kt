package com.psychologist.financial.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import com.psychologist.financial.ui.components.PaginatedLazyColumn
import com.psychologist.financial.ui.components.PaymentListItem
import com.psychologist.financial.viewmodel.PaymentViewModel

/**
 * Global payment list screen (bottom-nav Pagamentos tab)
 *
 * Lists all payments from all patients, ordered by payment date DESC.
 * Each item shows: amount, patient name, date, and linked appointments.
 * Uses paginated loading via [PaginatedLazyColumn] — loads [Constants.PAGE_SIZE]
 * items at a time as the user scrolls.
 *
 * @param viewModel PaymentViewModel
 * @param patientNameProvider Optional function to resolve patient name by ID
 */
@Composable
fun GlobalPaymentListScreen(
    viewModel: PaymentViewModel,
    patientNameProvider: (Long) -> String = { "" },
    onPatientClick: (Long) -> Unit = { }
) {
    val paginationState by viewModel.globalPaginationState.collectAsState()
    var nameQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.resetGlobalPaymentList()
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.resetNameFilter() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pagamentos") },
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

            Box(modifier = Modifier.weight(1f)) {
                if (paginationState.items.isEmpty() && !paginationState.isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (nameQuery.isNotEmpty())
                                "Nenhum pagamento encontrado para \"$nameQuery\""
                            else
                                "Nenhum pagamento registrado",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                PaginatedLazyColumn(
                    items = paginationState.items,
                    isLoading = paginationState.isLoading,
                    isError = paginationState.isError,
                    allLoaded = !paginationState.hasMore,
                    onLoadMore = { viewModel.loadNextGlobalPaymentPage() },
                    modifier = Modifier.fillMaxSize(),
                    key = { it.payment.id }
                ) { paymentWithDetails ->
                    PaymentListItem(
                        paymentWithDetails = paymentWithDetails,
                        patientName = paymentWithDetails.patientName.ifEmpty {
                            patientNameProvider(paymentWithDetails.payment.patientId)
                        },
                        onClick = { onPatientClick(paymentWithDetails.payment.patientId) }
                    )
                }
            }
        }
    }
}
