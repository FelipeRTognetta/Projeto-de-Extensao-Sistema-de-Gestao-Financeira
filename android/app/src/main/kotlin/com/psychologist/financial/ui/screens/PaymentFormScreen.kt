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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.psychologist.financial.ui.components.ErrorBanner
import com.psychologist.financial.viewmodel.PaymentViewModel
import com.psychologist.financial.viewmodel.PaymentViewState
import java.time.LocalDate

/**
 * Payment creation form screen
 *
 * Features:
 * - Amount input (decimal) with validation
 * - Date picker for payment date
 * - Payment method selector (dropdown)
 * - Payment status selector (PAID/PENDING)
 * - Optional appointment linker
 * - Real-time validation with error feedback
 * - Submit button
 * - Loading state during submission
 * - Success/error messages
 *
 * Navigation:
 * - Back button → PaymentListScreen
 * - Submit → PaymentListScreen (on success)
 *
 * Usage:
 * ```kotlin
 * PaymentFormScreen(
 *     viewModel = paymentViewModel,
 *     patientId = 1L,
 *     onSuccess = { navigateBack() },
 *     onCancel = { navigateBack() }
 * )
 * ```
 *
 * @param viewModel PaymentViewModel for form state
 * @param patientId Patient ID to create payment for
 * @param onSuccess Callback when payment created successfully
 * @param onCancel Callback when user cancels (back button)
 */
@Composable
fun PaymentFormScreen(
    viewModel: PaymentViewModel,
    patientId: Long,
    onSuccess: () -> Unit,
    onCancel: () -> Unit
) {
    val formState = viewModel.createFormState.collectAsState().value
    val formAmount = viewModel.formAmount.collectAsState().value
    val formDate = viewModel.formDate.collectAsState().value
    val formMethod = viewModel.formMethod.collectAsState().value
    val formStatus = viewModel.formStatus.collectAsState().value
    val formAppointmentId = viewModel.formAppointmentId.collectAsState().value
    val isSubmitting = formState.isSubmitting

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
                title = { Text("Novo Pagamento") },
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
                isSubmitting = isSubmitting,
                onAmountChange = { viewModel.setFormAmount(it) },
                onDateChange = { viewModel.setFormDate(it) },
                onMethodChange = { viewModel.setFormMethod(it) },
                onStatusChange = { viewModel.setFormStatus(it) },
                onAppointmentIdChange = { viewModel.setFormAppointmentId(it) },
                onSubmit = { viewModel.submitCreatePaymentForm(patientId) },
                onCancel = onCancel,
                onValidate = { viewModel.validateForm(patientId) }
            )
        }
    }
}

/**
 * Form content (fields and buttons)
 */
@Composable
private fun FormContent(
    formState: PaymentViewState.CreatePaymentState,
    formAmount: String,
    formDate: LocalDate,
    formMethod: String,
    formStatus: String,
    formAppointmentId: Long?,
    isSubmitting: Boolean,
    onAmountChange: (String) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onMethodChange: (String) -> Unit,
    onStatusChange: (String) -> Unit,
    onAppointmentIdChange: (Long?) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    onValidate: () -> Unit
) {
    val methodDropdownExpanded = remember { mutableStateOf(false) }
    val statusDropdownExpanded = remember { mutableStateOf(false) }
    val methods = listOf("Dinheiro", "Débito", "Crédito", "Pix", "Cheque", "Outro")
    val statuses = listOf("PAID", "PENDING")

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

        // Amount field
        OutlinedTextField(
            value = formAmount,
            onValueChange = { onAmountChange(it) },
            label = { Text("Valor (R$) *") },
            modifier = Modifier.fillMaxWidth(),
            isError = formState.hasFieldError("amount"),
            supportingText = {
                val amountError = formState.getFieldError("amount")
                if (amountError != null) {
                    Text(amountError, color = MaterialTheme.colorScheme.error)
                } else {
                    Text("Exemplo: 150.00")
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Next
            ),
            enabled = !isSubmitting
        )

        // Date field
        OutlinedTextField(
            value = formDate.toString(),
            onValueChange = { },
            label = { Text("Data do Pagamento *") },
            modifier = Modifier.fillMaxWidth(),
            isError = formState.hasFieldError("date"),
            supportingText = {
                val dateError = formState.getFieldError("date")
                if (dateError != null) {
                    Text(dateError, color = MaterialTheme.colorScheme.error)
                }
            },
            readOnly = true,
            enabled = !isSubmitting
        )

        // Method dropdown
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(
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
                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
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
                modifier = Modifier.padding(start = 16.dp, top = -12.dp)
            )
        }

        // Status dropdown
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(
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
                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
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
                modifier = Modifier.padding(start = 16.dp, top = -12.dp)
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
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onAppointmentIdChange(null) },
                    modifier = Modifier.weight(1f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                ) {
                    Text("Nenhuma")
                }

                OutlinedButton(
                    onClick = { /* TODO: Show appointment picker */ },
                    modifier = Modifier.weight(1f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                ) {
                    Text(
                        if (formAppointmentId != null) "Selecionar Outra" else "Selecionar"
                    )
                }
            }

            formAppointmentId?.let {
                Text(
                    text = "Consulta #$it selecionada",
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

        // Help text
        Text(
            text = "Os campos marcados com * são obrigatórios.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
