package com.psychologist.financial.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.psychologist.financial.ui.components.PaymentListItem
import com.psychologist.financial.viewmodel.PaymentViewModel
import com.psychologist.financial.viewmodel.PaymentViewState

/**
 * Global payment list screen (bottom-nav Pagamentos tab)
 *
 * Lists all payments from all patients, ordered by payment date DESC.
 * Each item shows: amount, patient name, date, and linked appointments.
 * No FAB — payment creation is done from the patient detail screen.
 *
 * @param viewModel PaymentViewModel
 * @param patientNameProvider Optional function to resolve patient name by ID
 */
@Composable
fun GlobalPaymentListScreen(
    viewModel: PaymentViewModel,
    patientNameProvider: (Long) -> String = { "" }
) {
    val state by viewModel.globalListState.collectAsState()

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
            when (val s = state) {
                is PaymentViewState.GlobalListState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                is PaymentViewState.GlobalListState.Success -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = s.payments,
                            key = { it.payment.id }
                        ) { paymentWithDetails ->
                            PaymentListItem(
                                paymentWithDetails = paymentWithDetails,
                                patientName = patientNameProvider(paymentWithDetails.payment.patientId)
                            )
                        }
                    }
                }

                is PaymentViewState.GlobalListState.Empty -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                            .align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Nenhum pagamento registrado",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Os pagamentos aparecerão aqui após serem registrados na tela do paciente.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                is PaymentViewState.GlobalListState.Error -> {
                    Text(
                        text = s.message,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
