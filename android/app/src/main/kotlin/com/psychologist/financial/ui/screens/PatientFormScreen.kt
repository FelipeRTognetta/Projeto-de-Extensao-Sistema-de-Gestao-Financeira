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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.psychologist.financial.domain.models.Patient
import com.psychologist.financial.ui.components.CpfVisualTransformation
import com.psychologist.financial.ui.components.ErrorBanner
import com.psychologist.financial.viewmodel.PatientViewModel
import com.psychologist.financial.viewmodel.PatientViewState.CreatePatientState
import java.time.LocalDate

/**
 * Patient creation/edit form screen
 *
 * Features:
 * - Form fields (name, phone, email, CPF, address, initial consult date)
 * - "Paciente não pagante" switch — reveals Responsável Financeiro section
 * - AlertDialog to confirm payer removal when toggling naoPagante off
 * - Real-time validation with error feedback
 * - Submit button with loading state
 *
 * Navigation:
 * - Back button → PatientListScreen
 * - Submit → PatientDetailScreen (on success)
 *
 * @param viewModel PatientViewModel for form state
 * @param onSuccess Callback when patient created/updated (passes patientId)
 * @param onCancel Callback when user cancels (back button)
 * @param editingPatient Existing patient to edit (null = create mode)
 */
@Composable
fun PatientFormScreen(
    viewModel: PatientViewModel,
    onSuccess: (Long) -> Unit,
    onCancel: () -> Unit,
    editingPatient: Patient? = null
) {
    val isEditing = editingPatient != null
    val formState = viewModel.createFormState.collectAsState().value
    val formName = viewModel.formName.collectAsState().value
    val formPhone = viewModel.formPhone.collectAsState().value
    val formEmail = viewModel.formEmail.collectAsState().value
    val formDate = viewModel.formInitialConsultDate.collectAsState().value
    val formCpf = viewModel.formCpf.collectAsState().value
    val formEndereco = viewModel.formEndereco.collectAsState().value
    val formNaoPagante = viewModel.formNaoPagante.collectAsState().value
    val formPayerNome = viewModel.formPayerNome.collectAsState().value
    val formPayerCpf = viewModel.formPayerCpf.collectAsState().value
    val formPayerEndereco = viewModel.formPayerEndereco.collectAsState().value
    val formPayerEmail = viewModel.formPayerEmail.collectAsState().value
    val formPayerTelefone = viewModel.formPayerTelefone.collectAsState().value
    val payerFieldErrors = viewModel.payerFieldErrors.collectAsState().value
    val showRemovePayerConfirmation = viewModel.showRemovePayerConfirmation.collectAsState().value
    val isSubmitting = formState.isSubmitting

    // Pre-fill form when editing
    LaunchedEffect(editingPatient?.id) {
        if (editingPatient != null) {
            viewModel.prepareEditForm(editingPatient)
        } else {
            viewModel.resetForm()
        }
    }

    // Handle success navigation
    when (val result = formState.submissionResult) {
        is CreatePatientState.SubmissionResult.Success -> {
            LaunchedEffect(result) {
                onSuccess(result.patientId)
            }
            viewModel.clearSubmissionResult()
        }
        else -> { }
    }

    // Confirmation dialog when user tries to turn off naoPagante with an existing payer
    if (showRemovePayerConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissRemovePayerConfirmation() },
            title = { Text("Remover Responsável Financeiro?") },
            text = {
                Text(
                    "Ao desmarcar \"Paciente não pagante\", os dados do Responsável Financeiro " +
                        "serão removidos ao salvar. Deseja continuar?"
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmRemovePayer() }) {
                    Text("Remover", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissRemovePayerConfirmation() }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Editar Paciente" else "Novo Paciente") },
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
                formName = formName,
                formPhone = formPhone,
                formEmail = formEmail,
                formDate = formDate,
                formCpf = formCpf,
                formEndereco = formEndereco,
                formNaoPagante = formNaoPagante,
                formPayerNome = formPayerNome,
                formPayerCpf = formPayerCpf,
                formPayerEndereco = formPayerEndereco,
                formPayerEmail = formPayerEmail,
                formPayerTelefone = formPayerTelefone,
                payerFieldErrors = payerFieldErrors,
                isSubmitting = isSubmitting,
                onNameChange = { viewModel.setFormName(it) },
                onPhoneChange = { viewModel.setFormPhone(it) },
                onEmailChange = { viewModel.setFormEmail(it) },
                onDateChange = { viewModel.setFormInitialConsultDate(it) },
                onCpfChange = { viewModel.setFormCpf(it) },
                onEnderecoChange = { viewModel.setFormEndereco(it) },
                onNaoPaganteChange = { newValue ->
                    val savedPayerExists = editingPatient?.payerInfo != null
                    viewModel.setFormNaoPagante(newValue, savedPayerExists)
                },
                onPayerNomeChange = { viewModel.setFormPayerNome(it) },
                onPayerCpfChange = { viewModel.setFormPayerCpf(it) },
                onPayerEnderecoChange = { viewModel.setFormPayerEndereco(it) },
                onPayerEmailChange = { viewModel.setFormPayerEmail(it) },
                onPayerTelefoneChange = { viewModel.setFormPayerTelefone(it) },
                onSubmit = {
                    if (isEditing) {
                        viewModel.submitEditPatientForm(editingPatient!!.id)
                    } else {
                        viewModel.submitCreatePatientForm()
                    }
                },
                onCancel = onCancel,
                onValidate = { viewModel.validateForm() }
            )
        }
    }
}

/**
 * Form content (fields and buttons)
 */
@Composable
private fun FormContent(
    formState: CreatePatientState,
    formName: String,
    formPhone: String,
    formEmail: String,
    formDate: LocalDate,
    formCpf: String,
    formEndereco: String,
    formNaoPagante: Boolean,
    formPayerNome: String,
    formPayerCpf: String,
    formPayerEndereco: String,
    formPayerEmail: String,
    formPayerTelefone: String,
    payerFieldErrors: Map<String, String>,
    isSubmitting: Boolean,
    onNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onCpfChange: (String) -> Unit,
    onEnderecoChange: (String) -> Unit,
    onNaoPaganteChange: (Boolean) -> Unit,
    onPayerNomeChange: (String) -> Unit,
    onPayerCpfChange: (String) -> Unit,
    onPayerEnderecoChange: (String) -> Unit,
    onPayerEmailChange: (String) -> Unit,
    onPayerTelefoneChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    onValidate: () -> Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Error message if submission failed
        formState.submissionResult?.let { result ->
            if (result is CreatePatientState.SubmissionResult.Error) {
                ErrorBanner(
                    message = result.message,
                    onDismiss = { /* Clear handled by ViewModel */ }
                )
            }
        }

        // Name field
        OutlinedTextField(
            value = formName,
            onValueChange = {
                onNameChange(it)
                onValidate()
            },
            label = { Text("Nome *") },
            modifier = Modifier.fillMaxWidth(),
            isError = formState.hasFieldError("name"),
            supportingText = {
                formState.getFieldError("name")?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            ),
            enabled = !isSubmitting
        )

        // CPF field
        OutlinedTextField(
            value = formCpf,
            onValueChange = {
                val digits = it.filter { c -> c.isDigit() }.take(11)
                onCpfChange(digits)
                onValidate()
            },
            label = { Text("CPF (opcional)") },
            modifier = Modifier.fillMaxWidth(),
            isError = formState.hasFieldError("cpf"),
            supportingText = {
                formState.getFieldError("cpf")?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            },
            visualTransformation = CpfVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next
            ),
            enabled = !isSubmitting,
            placeholder = { Text("000.000.000-00") }
        )

        // Email field
        OutlinedTextField(
            value = formEmail,
            onValueChange = {
                onEmailChange(it)
                onValidate()
            },
            label = { Text("Email (opcional)") },
            modifier = Modifier.fillMaxWidth(),
            isError = formState.hasFieldError("email"),
            supportingText = {
                formState.getFieldError("email")?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            enabled = !isSubmitting
        )

        // Phone field
        OutlinedTextField(
            value = formPhone,
            onValueChange = {
                onPhoneChange(it)
                onValidate()
            },
            label = { Text("Telefone (opcional)") },
            modifier = Modifier.fillMaxWidth(),
            isError = formState.hasFieldError("phone"),
            supportingText = {
                formState.getFieldError("phone")?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Next
            ),
            enabled = !isSubmitting
        )

        // Address field
        OutlinedTextField(
            value = formEndereco,
            onValueChange = { onEnderecoChange(it) },
            label = { Text("Endereço (opcional)") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            ),
            minLines = 2,
            maxLines = 3,
            enabled = !isSubmitting
        )

        // Date field (TODO: replace with DatePicker)
        OutlinedTextField(
            value = formDate.toString(),
            onValueChange = { },
            label = { Text("Data da Primeira Consulta *") },
            modifier = Modifier.fillMaxWidth(),
            isError = formState.hasFieldError("initialConsultDate"),
            supportingText = {
                formState.getFieldError("initialConsultDate")?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            },
            readOnly = true,
            enabled = false
        )

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))

        // Não pagante toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Paciente não pagante",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Preencha os dados do responsável financeiro abaixo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = formNaoPagante,
                onCheckedChange = { onNaoPaganteChange(it) },
                enabled = !isSubmitting
            )
        }

        // Responsável Financeiro section — shown only when naoPagante is true
        if (formNaoPagante) {
            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Responsável Financeiro",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )

            // Payer name (required)
            OutlinedTextField(
                value = formPayerNome,
                onValueChange = {
                    onPayerNomeChange(it)
                    onValidate()
                },
                label = { Text("Nome do Responsável *") },
                modifier = Modifier.fillMaxWidth(),
                isError = payerFieldErrors.containsKey("payerNome"),
                supportingText = {
                    payerFieldErrors["payerNome"]?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                enabled = !isSubmitting
            )

            // Payer CPF (optional)
            OutlinedTextField(
                value = formPayerCpf,
                onValueChange = {
                    val digits = it.filter { c -> c.isDigit() }.take(11)
                    onPayerCpfChange(digits)
                    onValidate()
                },
                label = { Text("CPF do Responsável (opcional)") },
                modifier = Modifier.fillMaxWidth(),
                isError = payerFieldErrors.containsKey("payerCpf"),
                supportingText = {
                    payerFieldErrors["payerCpf"]?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                },
                visualTransformation = CpfVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                enabled = !isSubmitting,
                placeholder = { Text("000.000.000-00") }
            )

            // Payer email (optional)
            OutlinedTextField(
                value = formPayerEmail,
                onValueChange = {
                    onPayerEmailChange(it)
                    onValidate()
                },
                label = { Text("Email do Responsável (opcional)") },
                modifier = Modifier.fillMaxWidth(),
                isError = payerFieldErrors.containsKey("payerEmail"),
                supportingText = {
                    payerFieldErrors["payerEmail"]?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                enabled = !isSubmitting
            )

            // Payer phone (optional)
            OutlinedTextField(
                value = formPayerTelefone,
                onValueChange = { onPayerTelefoneChange(it) },
                label = { Text("Telefone do Responsável (opcional)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Next
                ),
                enabled = !isSubmitting
            )

            // Payer address (optional)
            OutlinedTextField(
                value = formPayerEndereco,
                onValueChange = { onPayerEnderecoChange(it) },
                label = { Text("Endereço do Responsável (opcional)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done
                ),
                minLines = 2,
                maxLines = 3,
                enabled = !isSubmitting
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Form validation status
        Text(
            text = when {
                formState.isFormValid() -> "✓ Formulário válido"
                formState.getAllErrors().isNotEmpty() -> "✗ ${formState.getAllErrors().size} erro(s)"
                else -> "Preencha os campos obrigatórios"
            },
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
            text = "Os campos marcados com * são obrigatórios.\nDeve ser fornecido telefone ou email.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
