package com.psychologist.financial.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
import com.psychologist.financial.data.repositories.PaymentWithDetails
import com.psychologist.financial.domain.models.Payment
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
    if (patientId == 0L) {
        GlobalPaymentListScreen(viewModel = viewModel)
    } else {
        PatientPaymentListScreen(
            viewModel = viewModel,
            patientId = patientId,
            patientName = patientName,
            onBack = onBack,
            onAddPayment = onAddPayment,
            onSelectPayment = onSelectPayment
        )
    }
}

@Composable
private fun GlobalPaymentListScreen(viewModel: PaymentViewModel) {
    val globalState = viewModel.globalListState.collectAsState().value

    LaunchedEffect(Unit) {
        viewModel.loadAllPayments()
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (globalState) {
                is PaymentViewState.GlobalListState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is PaymentViewState.GlobalListState.Success -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = globalState.payments,
                            key = { it.payment.id }
                        ) { paymentWithDetails ->
                            PaymentListItem(
                                paymentWithDetails = paymentWithDetails,
                                patientName = paymentWithDetails.patientName,
                                onClick = {}
                            )
                        }
                    }
                }

                is PaymentViewState.GlobalListState.Empty -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Nenhum Pagamento",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Nenhum pagamento registrado ainda.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                is PaymentViewState.GlobalListState.Error -> {
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

@Composable
private fun PatientPaymentListScreen(
    viewModel: PaymentViewModel,
    patientId: Long,
    patientName: String,
    onBack: () -> Unit,
    onAddPayment: () -> Unit,
    onSelectPayment: (Long) -> Unit
) {
    val listState = viewModel.paymentListState.collectAsState().value

    LaunchedEffect(patientId) {
        viewModel.loadPatientPayments(patientId)
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
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is PaymentViewState.ListState.Success -> {
                    PaymentListContent(
                        payments = listState.payments,
                        onSelectPayment = onSelectPayment
                    )
                }

                is PaymentViewState.ListState.Empty -> {
                    EmptyPaymentsContent(onAddPayment = onAddPayment)
                }

                is PaymentViewState.ListState.Error -> {
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
    onSelectPayment: (Long) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
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
