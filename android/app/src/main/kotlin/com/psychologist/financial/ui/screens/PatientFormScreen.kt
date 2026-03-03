package com.psychologist.financial.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.psychologist.financial.ui.components.ErrorBanner
import com.psychologist.financial.viewmodel.PatientViewModel
import com.psychologist.financial.viewmodel.PatientViewState.CreatePatientState
import java.time.LocalDate

/**
 * Patient creation/edit form screen
 *
 * Features:
 * - Form fields (name, phone, email, initial consult date)
 * - Real-time validation with error feedback
 * - Submit button
 * - Loading state during submission
 * - Success/error messages
 *
 * Navigation:
 * - Back button → PatientListScreen
 * - Submit → PatientDetailScreen (on success)
 *
 * Usage:
 * ```kotlin
 * PatientFormScreen(
 *     viewModel = patientViewModel,
 *     onSuccess = { patientId ->
 *         navigateToDetail(patientId)
 *     },
 *     onCancel = { navigateBack() }
 * )
 * ```
 *
 * @param viewModel PatientViewModel for form state
 * @param onSuccess Callback when patient created (passes patientId)
 * @param onCancel Callback when user cancels (back button)
 */
@Composable
fun PatientFormScreen(
    viewModel: PatientViewModel,
    onSuccess: (Long) -> Unit,
    onCancel: () -> Unit
) {
    val formState = viewModel.createFormState.collectAsState().value
    val formName = viewModel.formName.collectAsState().value
    val formPhone = viewModel.formPhone.collectAsState().value
    val formEmail = viewModel.formEmail.collectAsState().value
    val formDate = viewModel.formInitialConsultDate.collectAsState().value
    val isSubmitting = formState.isSubmitting

    // Handle success navigation
    when (val result = formState.submissionResult) {
        is CreatePatientState.SubmissionResult.Success -> {
            // Navigate to detail screen after success
            LaunchedEffect(result) {
                onSuccess(result.patientId)
            }
            // Clear result so we don't navigate twice
            viewModel.clearSubmissionResult()
        }
        else -> {
            // Handle other results
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Novo Paciente") },
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
                isSubmitting = isSubmitting,
                onNameChange = { viewModel.setFormName(it) },
                onPhoneChange = { viewModel.setFormPhone(it) },
                onEmailChange = { viewModel.setFormEmail(it) },
                onDateChange = { viewModel.setFormInitialConsultDate(it) },
                onSubmit = { viewModel.submitCreatePatientForm() },
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
    isSubmitting: Boolean,
    onNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onDateChange: (LocalDate) -> Unit,
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
