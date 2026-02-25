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
import com.psychologist.financial.viewmodel.AppointmentViewModel
import com.psychologist.financial.viewmodel.AppointmentViewState
import java.time.LocalDate
import java.time.LocalTime

/**
 * Appointment creation form screen
 *
 * Features:
 * - Date picker for appointment date
 * - Time picker for session start time
 * - Duration input (in minutes)
 * - Notes field for session notes
 * - Real-time validation with error feedback
 * - Submit button
 * - Loading state during submission
 * - Success/error messages
 *
 * Navigation:
 * - Back button → AppointmentListScreen
 * - Submit → AppointmentListScreen (on success)
 *
 * Usage:
 * ```kotlin
 * AppointmentFormScreen(
 *     viewModel = appointmentViewModel,
 *     patientId = 1L,
 *     onSuccess = { navigateBack() },
 *     onCancel = { navigateBack() }
 * )
 * ```
 *
 * @param viewModel AppointmentViewModel for form state
 * @param patientId Patient ID to create appointment for
 * @param onSuccess Callback when appointment created successfully
 * @param onCancel Callback when user cancels (back button)
 */
@Composable
fun AppointmentFormScreen(
    viewModel: AppointmentViewModel,
    patientId: Long,
    onSuccess: () -> Unit,
    onCancel: () -> Unit
) {
    val formState = viewModel.createFormState.collectAsState().value
    val formDate = viewModel.formDate.collectAsState().value
    val formTime = viewModel.formTime.collectAsState().value
    val formDuration = viewModel.formDuration.collectAsState().value
    val formNotes = viewModel.formNotes.collectAsState().value
    val isSubmitting = formState.isSubmitting

    // Handle success navigation
    when (val result = formState.submissionResult) {
        is com.psychologist.financial.domain.usecases.CreateAppointmentUseCase.CreateAppointmentResult.Success -> {
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
                title = { Text("Nova Consulta") },
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
                formDate = formDate,
                formTime = formTime,
                formDuration = formDuration,
                formNotes = formNotes,
                isSubmitting = isSubmitting,
                onDateChange = { viewModel.setFormDate(it) },
                onTimeChange = { viewModel.setFormTime(it) },
                onDurationChange = { viewModel.setFormDuration(it) },
                onNotesChange = { viewModel.setFormNotes(it) },
                onSubmit = { viewModel.submitCreateAppointmentForm(patientId) },
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
    formState: AppointmentViewState.CreateAppointmentState,
    formDate: LocalDate,
    formTime: LocalTime,
    formDuration: Int,
    formNotes: String,
    isSubmitting: Boolean,
    onDateChange: (LocalDate) -> Unit,
    onTimeChange: (LocalTime) -> Unit,
    onDurationChange: (Int) -> Unit,
    onNotesChange: (String) -> Unit,
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
            if (result is com.psychologist.financial.domain.usecases.CreateAppointmentUseCase.CreateAppointmentResult.Error) {
                ErrorBanner(
                    message = result.message,
                    onDismiss = { /* Clear handled by ViewModel */ }
                )
            }
        }

        // Date field
        OutlinedTextField(
            value = formDate.toString(),
            onValueChange = { },
            label = { Text("Data da Consulta *") },
            modifier = Modifier.fillMaxWidth(),
            isError = formState.hasFieldError("date"),
            supportingText = {
                formState.getFieldError("date")?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            },
            readOnly = true,
            enabled = !isSubmitting,
            tag = "dateField"
        )

        // Time field
        OutlinedTextField(
            value = String.format("%02d:%02d", formTime.hour, formTime.minute),
            onValueChange = { },
            label = { Text("Horário da Consulta *") },
            modifier = Modifier.fillMaxWidth(),
            isError = formState.hasFieldError("time"),
            supportingText = {
                formState.getFieldError("time")?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            },
            readOnly = true,
            enabled = !isSubmitting,
            tag = "timeField"
        )

        // Duration field
        OutlinedTextField(
            value = formDuration.toString(),
            onValueChange = { newValue ->
                newValue.toIntOrNull()?.let { onDurationChange(it) }
            },
            label = { Text("Duração (minutos) *") },
            modifier = Modifier.fillMaxWidth(),
            isError = formState.hasFieldError("duration"),
            supportingText = {
                formState.getFieldError("duration")?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                } ?: Text("Mínimo 5 min, máximo 480 min (8h)")
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next
            ),
            enabled = !isSubmitting
        )

        // Notes field
        OutlinedTextField(
            value = formNotes,
            onValueChange = { onNotesChange(it) },
            label = { Text("Observações (opcional)") },
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            minLines = 4,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Default
            ),
            enabled = !isSubmitting
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
            text = "Os campos marcados com * são obrigatórios.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
