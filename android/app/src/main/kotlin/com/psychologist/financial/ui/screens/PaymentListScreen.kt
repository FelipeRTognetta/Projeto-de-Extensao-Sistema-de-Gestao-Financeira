package com.psychologist.financial.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Surface
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
import com.psychologist.financial.domain.models.Payment
import com.psychologist.financial.ui.components.BalanceSummary
import com.psychologist.financial.ui.components.PaymentListItem
import com.psychologist.financial.viewmodel.PaymentViewModel
import com.psychologist.financial.viewmodel.PaymentViewState

/**
 * Payment list screen
 *
 * Shows patient's payment history with balance summary.
 * Allows creating new payments and filtering by status.
 *
 * Features:
 * - Display balance summary (amount due vs outstanding)
 * - Display list of patient's payments
 * - Filter by status (all, paid, pending, overdue)
 * - FAB button to add new payment
 * - Loading, empty, and error states
 * - Navigate to detail or form screens
 *
 * Navigation:
 * - Back button → PatientDetailScreen
 * - FAB button → PaymentFormScreen
 * - Payment item → PaymentDetailScreen (TODO)
 *
 * Usage:
 * ```kotlin
 * PaymentListScreen(
 *     viewModel = paymentViewModel,
 *     patientId = 1L,
 *     patientName = "João Silva",
 *     onBack = { navigateBack() },
 *     onAddPayment = { navigateToForm(patientId) },
 *     onSelectPayment = { paymentId -> navigateToDetail(paymentId) }
 * )
 * ```
 *
 * @param viewModel PaymentViewModel for data
 * @param patientId Patient ID to load payments for
 * @param patientName Patient name for display
 * @param onBack Callback when back button tapped
 * @param onAddPayment Callback when FAB tapped
 * @param onSelectPayment Callback when payment item tapped
 */
@Composable
fun PaymentListScreen(
    viewModel: PaymentViewModel,
    patientId: Long,
    patientName: String = "",
    onBack: () -> Unit,
    onAddPayment: () -> Unit,
    onSelectPayment: (Long) -> Unit = { }
) {
    val listState = viewModel.paymentListState.collectAsState().value
    val balanceState = viewModel.balanceState.collectAsState().value
    val statusFilter = viewModel.statusFilter.collectAsState().value

    // Load payments and balance when screen opens
    LaunchedEffect(patientId) {
        viewModel.loadPatientPayments(patientId)
        viewModel.loadBalance(patientId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Pagamentos")
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
                onClick = onAddPayment,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, "Adicionar Pagamento")
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (listState) {
                is PaymentViewState.ListState.Loading -> {
                    // Loading state
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is PaymentViewState.ListState.Success -> {
                    // Payment list
                    PaymentListContent(
                        payments = listState.payments,
                        balance = balanceState.balance,
                        statusFilter = statusFilter,
                        onStatusFilterChange = { viewModel.setStatusFilter(it) },
                        onSelectPayment = onSelectPayment
                    )
                }

                is PaymentViewState.ListState.Empty -> {
                    // Empty state
                    EmptyPaymentsContent(
                        onAddPayment = onAddPayment
                    )
                }

                is PaymentViewState.ListState.Error -> {
                    // Error state
                    ErrorPaymentsContent(
                        message = listState.message,
                        onBack = onBack
                    )
                }
            }
        }
    }
}

/**
 * Payment list content
 */
@Composable
private fun PaymentListContent(
    payments: List<Payment>,
    balance: com.psychologist.financial.domain.models.PatientBalance,
    statusFilter: PaymentViewState.PaymentStatusFilter,
    onStatusFilterChange: (PaymentViewState.PaymentStatusFilter) -> Unit,
    onSelectPayment: (Long) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Balance summary card
        BalanceSummary(balance = balance)

        // Status filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PaymentViewState.PaymentStatusFilter.entries.forEach { filter ->
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                    color = if (statusFilter == filter) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    TextButton(
                        onClick = { onStatusFilterChange(filter) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = when (filter) {
                                PaymentViewState.PaymentStatusFilter.ALL -> "Todos"
                                PaymentViewState.PaymentStatusFilter.PAID -> "Pagos"
                                PaymentViewState.PaymentStatusFilter.PENDING -> "Pendentes"
                                PaymentViewState.PaymentStatusFilter.OVERDUE -> "Vencidos"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (statusFilter == filter) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }

        // Payment list
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = payments,
                key = { it.id }
            ) { payment ->
                PaymentListItem(
                    payment = payment,
                    onClick = { onSelectPayment(payment.id) }
                )
            }
        }
    }
}

/**
 * Empty payments content
 */
@Composable
private fun EmptyPaymentsContent(
    onAddPayment: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Nenhum Pagamento",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        androidx.compose.foundation.layout.Spacer(
            modifier = Modifier.height(12.dp)
        )

        Text(
            text = "Ainda não há pagamentos registrados para este paciente.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        androidx.compose.foundation.layout.Spacer(
            modifier = Modifier.height(24.dp)
        )

        Button(onClick = onAddPayment) {
            Text("Registrar Primeiro Pagamento")
        }
    }
}

/**
 * Error payments content
 */
@Composable
private fun ErrorPaymentsContent(
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
