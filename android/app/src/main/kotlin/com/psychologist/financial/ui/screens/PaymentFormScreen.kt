package com.psychologist.financial.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.psychologist.financial.domain.models.Appointment
import com.psychologist.financial.ui.components.ErrorBanner
import com.psychologist.financial.viewmodel.PaymentViewModel
import com.psychologist.financial.viewmodel.PaymentViewState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Format a raw centavos digit string to currency display.
 *
 * Examples:
 *   "0"     → "R$ 0,00"
 *   "1"     → "R$ 0,01"
 *   "150"   → "R$ 1,50"
 *   "15000" → "R$ 150,00"
 *   "1500000" → "R$ 15.000,00"
 */
private fun formatCurrencyMask(digits: String): String {
    val long = digits.filter { it.isDigit() }.toLongOrNull() ?: 0L
    val cents = long % 100
    val integer = long / 100
    val intStr = integer.toString()
    val withSeparators = if (intStr.length > 3) {
        intStr.reversed().chunked(3).joinToString(".").reversed()
    } else {
        intStr
    }
    return "R$ $withSeparators,${"%02d".format(cents)}"
}

/**
 * Payment creation/edit form screen
 *
 * Features:
 * - Amount input (decimal) with validation — accepts both dot and comma (e.g. "150.00" or "150,00")
 * - Date picker dialog for payment date
 * - Payment method selector (dropdown)
 * - Payment status selector (PAID/PENDING)
 * - Optional appointment linker with picker dialog
 * - Real-time validation with error feedback
 * - Submit button
 * - Loading state during submission
 * - Success/error messages
 *
 * @param paymentId When non-null, operates in edit mode — pre-populates the form with existing data
 */
@Composable
fun PaymentFormScreen(
    viewModel: PaymentViewModel,
    patientId: Long,
    paymentId: Long? = null,
    onSuccess: () -> Unit,
    onCancel: () -> Unit
) {
    val isEditMode = paymentId != null

    val formState = viewModel.createFormState.collectAsState().value
    val formAmount = viewModel.formAmount.collectAsState().value
    val formDate = viewModel.formDate.collectAsState().value
    val formMethod = viewModel.formMethod.collectAsState().value
    val formStatus = viewModel.formStatus.collectAsState().value
    val formAppointmentId = viewModel.formAppointmentId.collectAsState().value
    val patientAppointments = viewModel.patientAppointments.collectAsState().value
    val isSubmitting = formState.isSubmitting

    // Load existing payment for editing, and appointments for picker
    LaunchedEffect(patientId, paymentId) {
        viewModel.loadAppointmentsForPatient(patientId)
        if (paymentId != null) {
            viewModel.loadPaymentForEdit(paymentId)
        } else {
            viewModel.resetForm()
        }
    }

    // Handle success navigation
    when (val result = formState.submissionResult) {
        is com.psychologist.financial.domain.usecases.CreatePaymentResult.Success -> {
            LaunchedEffect(result) {
                onSuccess()
            }
            viewModel.clearSubmissionResult()
        }

        else -> {
            // Handle other results
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditMode) "Editar Pagamento" else "Novo Pagamento") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            FormContent(
                formState = formState,
                formAmount = formAmount,
                formDate = formDate,
                formMethod = formMethod,
                formStatus = formStatus,
                formAppointmentId = formAppointmentId,
                patientAppointments = patientAppointments,
                isSubmitting = isSubmitting,
                onAmountChange = { viewModel.setFormAmount(it) },
                onDateChange = { viewModel.setFormDate(it) },
                onMethodChange = { viewModel.setFormMethod(it) },
                onStatusChange = { viewModel.setFormStatus(it) },
                onAppointmentIdChange = { viewModel.setFormAppointmentId(it) },
                onSubmit = {
                    if (isEditMode && paymentId != null) {
                        viewModel.submitUpdatePaymentForm(paymentId, patientId)
                    } else {
                        viewModel.submitCreatePaymentForm(patientId)
                    }
                },
                onCancel = onCancel
            )
        }
    }
}

@Composable
private fun FormContent(
    formState: PaymentViewState.CreatePaymentState,
    formAmount: String,
    formDate: LocalDate,
    formMethod: String,
    formStatus: String,
    formAppointmentId: Long?,
    patientAppointments: List<Appointment>,
    isSubmitting: Boolean,
    onAmountChange: (String) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onMethodChange: (String) -> Unit,
    onStatusChange: (String) -> Unit,
    onAppointmentIdChange: (Long?) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit
) {
    val methodDropdownExpanded = remember { mutableStateOf(false) }
    val statusDropdownExpanded = remember { mutableStateOf(false) }
    val methods = listOf("Dinheiro", "Débito", "Crédito", "Pix", "Cheque", "Outro")

    var showDatePicker by remember { mutableStateOf(false) }
    var showAppointmentPicker by remember { mutableStateOf(false) }

    val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    // DatePickerDialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = formDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
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

    // Appointment picker dialog
    if (showAppointmentPicker) {
        AlertDialog(
            onDismissRequest = { showAppointmentPicker = false },
            title = { Text("Selecionar Consulta") },
            text = {
                if (patientAppointments.isEmpty()) {
                    Text("Nenhuma consulta encontrada para este paciente.")
                } else {
                    Column {
                        patientAppointments.forEach { appointment ->
                            val label = "${appointment.date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))} às ${appointment.displayTime} (${appointment.durationMinutes}min)"
                            Text(
                                text = label,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onAppointmentIdChange(appointment.id)
                                        showAppointmentPicker = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (formAppointmentId == appointment.id)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAppointmentPicker = false }) { Text("Fechar") }
            },
            dismissButton = {
                if (formAppointmentId != null) {
                    TextButton(onClick = {
                        onAppointmentIdChange(null)
                        showAppointmentPicker = false
                    }) { Text("Remover vínculo") }
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Error message if submission failed
        formState.submissionResult?.let { result ->
            if (result is com.psychologist.financial.domain.usecases.CreatePaymentResult.ValidationError) {
                ErrorBanner(
                    message = result.getFirstErrorMessage(),
                    onDismiss = { /* Clear handled by ViewModel */ }
                )
            }
        }

        // Amount field with currency mask
        OutlinedTextField(
            value = formatCurrencyMask(formAmount),
            onValueChange = { newText ->
                // Pass only the digit portion to the ViewModel
                onAmountChange(newText.filter { it.isDigit() })
            },
            label = { Text("Valor *") },
            modifier = Modifier.fillMaxWidth(),
            isError = formState.hasFieldError("amount"),
            supportingText = {
                val amountError = formState.getFieldError("amount")
                if (amountError != null) {
                    Text(amountError, color = MaterialTheme.colorScheme.error)
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next
            ),
            enabled = !isSubmitting
        )

        // Date field with picker
        OutlinedTextField(
            value = formDate.format(dateFormatter),
            onValueChange = { },
            label = { Text("Data do Pagamento *") },
            modifier = Modifier.fillMaxWidth(),
            isError = formState.hasFieldError("paymentDate"),
            supportingText = {
                val dateError = formState.getFieldError("paymentDate")
                if (dateError != null) {
                    Text(dateError, color = MaterialTheme.colorScheme.error)
                }
            },
            trailingIcon = {
                IconButton(
                    onClick = { if (!isSubmitting) showDatePicker = true }
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = "Selecionar data")
                }
            },
            readOnly = true,
            enabled = !isSubmitting
        )

        // Method dropdown
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                1.dp,
                if (formState.hasFieldError("method"))
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.outline
            )
        ) {
            OutlinedButton(
                onClick = { methodDropdownExpanded.value = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(0.dp),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = formMethod.ifEmpty { "Selecione Método *" },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }

            DropdownMenu(
                expanded = methodDropdownExpanded.value,
                onDismissRequest = { methodDropdownExpanded.value = false },
                modifier = Modifier.fillMaxWidth()
            ) {
                methods.forEach { method ->
                    DropdownMenuItem(
                        text = { Text(method) },
                        onClick = {
                            onMethodChange(method)
                            methodDropdownExpanded.value = false
                        }
                    )
                }
            }
        }

        formState.getFieldError("method")?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }

        // Status dropdown
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                1.dp,
                if (formState.hasFieldError("status"))
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.outline
            )
        ) {
            OutlinedButton(
                onClick = { statusDropdownExpanded.value = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(0.dp),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = when (formStatus) {
                        "PAID" -> "Pago"
                        "PENDING" -> "Pendente"
                        else -> "Selecione Status *"
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }

            DropdownMenu(
                expanded = statusDropdownExpanded.value,
                onDismissRequest = { statusDropdownExpanded.value = false },
                modifier = Modifier.fillMaxWidth()
            ) {
                DropdownMenuItem(
                    text = { Text("Pago") },
                    onClick = {
                        onStatusChange("PAID")
                        statusDropdownExpanded.value = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Pendente") },
                    onClick = {
                        onStatusChange("PENDING")
                        statusDropdownExpanded.value = false
                    }
                )
            }
        }

        formState.getFieldError("status")?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }

        // Appointment linker (optional)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Text(
                text = "Vinculado a Consulta (opcional)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (formAppointmentId != null) {
                    OutlinedButton(
                        onClick = { onAppointmentIdChange(null) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("Remover")
                    }
                }

                OutlinedButton(
                    onClick = { showAppointmentPicker = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(4.dp),
                    enabled = !isSubmitting
                ) {
                    Text(if (formAppointmentId != null) "Trocar Consulta" else "Selecionar")
                }
            }

            formAppointmentId?.let { apptId ->
                val appt = patientAppointments.find { it.id == apptId }
                val label = appt?.let {
                    "${it.date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))} às ${it.displayTime}"
                } ?: "Consulta #$apptId"
                Text(
                    text = "Consulta selecionada: $label",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Form validation status
        Text(
            text = formState.getValidationStatus(),
            style = MaterialTheme.typography.bodySmall,
            color = when {
                formState.isFormValid() -> MaterialTheme.colorScheme.onSurfaceVariant
                formState.getAllErrors().isNotEmpty() -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(
                onClick = onCancel,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                enabled = !isSubmitting
            ) {
                Text("Cancelar")
            }

            Button(
                onClick = onSubmit,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                enabled = formState.isFormValid() && !isSubmitting
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Text("Salvar")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Os campos marcados com * são obrigatórios.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
