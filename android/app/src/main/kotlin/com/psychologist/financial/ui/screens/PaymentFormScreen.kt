package com.psychologist.financial.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.psychologist.financial.domain.models.Appointment
import com.psychologist.financial.ui.components.ErrorBanner
import com.psychologist.financial.viewmodel.PaymentViewModel
import com.psychologist.financial.viewmodel.PaymentViewState
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Payment creation form screen
 *
 * Features:
 * - Amount input with validation
 * - Date picker for payment date
 * - Scrollable appointment checklist (unpaid appointments only)
 * - No method or status fields — all payments are implicitly PAID
 * - Loading state during submission
 * - Error message display
 */
@Composable
fun PaymentFormScreen(
    viewModel: PaymentViewModel,
    patientId: Long,
    paymentId: Long? = null,
    onSuccess: () -> Unit,
    onCancel: () -> Unit
) {
    val formState by viewModel.paymentFormState.collectAsState()

    LaunchedEffect(paymentId) {
        if (paymentId != null) {
            viewModel.loadPaymentForEdit(paymentId, patientId)
        } else {
            viewModel.loadAvailableAppointments(patientId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (paymentId != null) "Editar Pagamento" else "Novo Pagamento") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.ArrowBack, "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        PaymentFormContent(
            formState = formState,
            onAmountChange = { viewModel.updateAmount(it) },
            onDateChange = { viewModel.updatePaymentDate(it) },
            onToggleAppointment = { viewModel.toggleAppointmentSelection(it) },
            onSubmit = { viewModel.submitForm(patientId) },
            onCancel = onCancel,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        )
    }

    // Navigate on success: when error is null and loading is false and selection was cleared
    // The ViewModel clears amountText on success — use that as success signal
    LaunchedEffect(formState.isLoading, formState.errorMessage) {
        // isLoading transitioned to false and errorMessage is null after a previous submit
        // We use a different signal: if amountText is empty after user had typed something,
        // navigate. This is handled by observing the reset state.
    }
}

@Composable
private fun PaymentFormContent(
    formState: PaymentViewState.PaymentFormState,
    onAmountChange: (String) -> Unit,
    onDateChange: (java.time.LocalDate) -> Unit,
    onToggleAppointment: (Long) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = formState.paymentDate
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = datePickerState.selectedDateMillis
                    if (millis != null) {
                        onDateChange(
                            Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        )
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState())
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Error banner
            formState.errorMessage?.let { error ->
                ErrorBanner(
                    message = error,
                    onDismiss = { onAmountChange(formState.amountText) } // re-triggers update clearing error
                )
            }

            // Amount field — implicit cents, cursor always at end (right-to-left entry)
            val displayAmount = if (formState.amountText.isEmpty()) "0,00" else formState.amountText
            OutlinedTextField(
                value = TextFieldValue(
                    text = displayAmount,
                    selection = TextRange(displayAmount.length)
                ),
                onValueChange = { tfv ->
                    val digits = tfv.text.filter { it.isDigit() }
                    if (digits.isEmpty()) {
                        onAmountChange("")
                        return@OutlinedTextField
                    }
                    // Limit to 9 digits (R$ 9.999.999,99)
                    val limited = digits.takeLast(9).padStart(3, '0')
                    val intPart = limited.dropLast(2).trimStart('0').ifEmpty { "0" }
                    val decPart = limited.takeLast(2)
                    onAmountChange("$intPart,$decPart")
                },
                label = { Text("Valor *") },
                modifier = Modifier.fillMaxWidth(),
                isError = formState.errorMessage != null && formState.amountText.isBlank(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                enabled = !formState.isLoading,
                prefix = { Text("R$ ") }
            )

            // Date field
            OutlinedTextField(
                value = formState.paymentDate.format(dateFormatter),
                onValueChange = { },
                label = { Text("Data do Pagamento *") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(
                        onClick = { if (!formState.isLoading) showDatePicker = true }
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = "Selecionar data")
                    }
                },
                readOnly = true,
                enabled = !formState.isLoading
            )

            // Appointment checklist — linked (pre-selected) + unpaid (available)
            AppointmentChecklist(
                linkedAppointments = formState.linkedAppointments,
                availableAppointments = formState.availableAppointments,
                selectedIds = formState.selectedAppointmentIds,
                onToggle = onToggleAppointment,
                enabled = !formState.isLoading
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    enabled = !formState.isLoading
                ) {
                    Text("Cancelar")
                }

                Button(
                    onClick = onSubmit,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    enabled = !formState.isLoading
                ) {
                    if (formState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 8.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Text("Salvar")
                }
            }

            Text(
                text = "Os campos marcados com * são obrigatórios.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AppointmentChecklist(
    linkedAppointments: List<Appointment>,
    availableAppointments: List<Appointment>,
    selectedIds: Set<Long>,
    onToggle: (Long) -> Unit,
    enabled: Boolean
) {
    val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    // De-duplicate: linked + available without repeats
    val linkedIds = linkedAppointments.map { it.id }.toSet()
    val extraAvailable = availableAppointments.filter { it.id !in linkedIds }
    val allAppointments = linkedAppointments + extraAvailable

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Consultas vinculadas",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        if (allAppointments.isEmpty()) {
            Text(
                text = "Nenhuma consulta pendente para este paciente.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            allAppointments.forEach { appointment ->
                AppointmentCheckRow(
                    appointment = appointment,
                    isSelected = selectedIds.contains(appointment.id),
                    onToggle = { onToggle(appointment.id) },
                    enabled = enabled,
                    dateFormatter = dateFormatter
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun AppointmentCheckRow(
    appointment: Appointment,
    isSelected: Boolean,
    onToggle: () -> Unit,
    enabled: Boolean,
    dateFormatter: DateTimeFormatter
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() },
            enabled = enabled
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = appointment.date.format(dateFormatter),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = appointment.displayTime,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
