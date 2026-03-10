package com.psychologist.financial.ui.screens

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.psychologist.financial.domain.models.BiometricAuthResult
import com.psychologist.financial.services.PerOperationAuthManager
import com.psychologist.financial.ui.components.ErrorBanner
import com.psychologist.financial.viewmodel.AppointmentViewModel
import com.psychologist.financial.viewmodel.AppointmentViewState
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.launch

/**
 * Appointment creation form screen
 *
 * Features:
 * - DatePickerDialog for appointment date
 * - TimePickerDialog for session start time
 * - Duration input (in minutes)
 * - Notes field for session notes
 * - Real-time validation with error feedback
 * - Submit button with loading state
 *
 * @param viewModel AppointmentViewModel for form state
 * @param patientId Patient ID to create appointment for
 * @param onSuccess Callback when appointment created successfully
 * @param onCancel Callback when user cancels
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppointmentFormScreen(
    viewModel: AppointmentViewModel,
    patientId: Long,
    onSuccess: () -> Unit,
    onCancel: () -> Unit,
    editingAppointmentId: Long? = null
) {
    val isEditing = editingAppointmentId != null
    val formState = viewModel.createFormState.collectAsState().value
    val formDate = viewModel.formDate.collectAsState().value
    val formTime = viewModel.formTime.collectAsState().value
    val formNotes = viewModel.formNotes.collectAsState().value
    val isSubmitting = formState.isSubmitting
    val deleteState = viewModel.deleteAppointmentState.collectAsState().value
    val activity = LocalContext.current as FragmentActivity
    val scope = rememberCoroutineScope()

    // Load existing appointment data when editing, or reset for new
    LaunchedEffect(editingAppointmentId) {
        if (editingAppointmentId != null) {
            viewModel.selectAppointment(editingAppointmentId)
        } else {
            viewModel.resetForm()
        }
    }

    // When edit detail is loaded, pre-fill the form fields
    val detailState = viewModel.appointmentDetailState.collectAsState().value
    LaunchedEffect(detailState) {
        if (isEditing && detailState is AppointmentViewState.DetailState.Success) {
            viewModel.prepareEditForm(detailState.appointment)
        }
    }

    // Handle success navigation
    val submissionResult = formState.submissionResult
    if (submissionResult is com.psychologist.financial.domain.usecases.CreateAppointmentUseCase.CreateAppointmentResult.Success) {
        LaunchedEffect(submissionResult) {
            onSuccess()
            viewModel.clearSubmissionResult()
        }
    }

    // Navigate back when delete succeeds; trigger biometric when AwaitingAuth
    LaunchedEffect(deleteState) {
        when (deleteState) {
            is AppointmentViewState.DeleteAppointmentState.AwaitingAuth -> {
                scope.launch {
                    val result = PerOperationAuthManager(activity).authenticateDelete()
                    if (result is BiometricAuthResult.Success) {
                        viewModel.confirmDeleteAppointment()
                    } else {
                        viewModel.cancelDeleteAppointment()
                    }
                }
            }
            is AppointmentViewState.DeleteAppointmentState.Success -> {
                onSuccess()
                viewModel.cancelDeleteAppointment()
            }
            else -> Unit
        }
    }

    // Delete confirmation dialog
    if (deleteState is AppointmentViewState.DeleteAppointmentState.AwaitingConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelDeleteAppointment() },
            title = { Text("Excluir Consulta") },
            text = { Text("Tem certeza que deseja excluir esta consulta? Esta ação é irreversível.") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.onAppointmentDeleteAuthSuccess() },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Excluir")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDeleteAppointment() }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Delete error dialog
    if (deleteState is AppointmentViewState.DeleteAppointmentState.Error) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelDeleteAppointment() },
            title = { Text("Erro ao excluir") },
            text = { Text(deleteState.message) },
            confirmButton = {
                TextButton(onClick = { viewModel.cancelDeleteAppointment() }) { Text("OK") }
            }
        )
    }

    // Picker visibility state
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    // DatePickerDialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = formDate
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val selected = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                        viewModel.setFormDate(selected)
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

    // TimePickerDialog (Material3 has no built-in TimePickerDialog; wrap in AlertDialog)
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = formTime.hour,
            initialMinute = formTime.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setFormTime(LocalTime.of(timePickerState.hour, timePickerState.minute))
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancelar") }
            },
            text = {
                TimePicker(state = timePickerState)
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Editar Consulta" else "Nova Consulta") },
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
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Submission error banner
            val result = formState.submissionResult
            if (result is com.psychologist.financial.domain.usecases.CreateAppointmentUseCase.CreateAppointmentResult.Error) {
                ErrorBanner(
                    message = result.message,
                    onDismiss = { viewModel.clearSubmissionResult() }
                )
            }

            // Date field — tapping opens DatePickerDialog
            val dateInteractionSource = remember { MutableInteractionSource() }
            LaunchedEffect(dateInteractionSource) {
                dateInteractionSource.interactions.collect { interaction ->
                    if (interaction is PressInteraction.Release) {
                        showDatePicker = true
                    }
                }
            }
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
                trailingIcon = {
                    Icon(Icons.Default.CalendarMonth, contentDescription = "Selecionar data")
                },
                readOnly = true,
                enabled = !isSubmitting,
                interactionSource = dateInteractionSource
            )

            // Time field — tapping opens TimePickerDialog
            val timeInteractionSource = remember { MutableInteractionSource() }
            LaunchedEffect(timeInteractionSource) {
                timeInteractionSource.interactions.collect { interaction ->
                    if (interaction is PressInteraction.Release) {
                        showTimePicker = true
                    }
                }
            }
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
                trailingIcon = {
                    Icon(Icons.Default.Schedule, contentDescription = "Selecionar horário")
                },
                readOnly = true,
                enabled = !isSubmitting,
                interactionSource = timeInteractionSource
            )

            // Notes field
            OutlinedTextField(
                value = formNotes,
                onValueChange = { viewModel.setFormNotes(it) },
                label = { Text("Observações (opcional)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                minLines = 3,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Default
                ),
                enabled = !isSubmitting
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Validation status
            if (formState.getAllErrors().isNotEmpty()) {
                Text(
                    text = "✗ ${formState.getAllErrors().size} erro(s): ${formState.getAllErrors().first()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
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
                    onClick = {
                        if (isEditing) {
                            viewModel.submitEditAppointmentForm(editingAppointmentId!!, patientId)
                        } else {
                            viewModel.submitCreateAppointmentForm(patientId)
                        }
                    },
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

            // Delete button — only visible in edit mode
            if (isEditing) {
                Spacer(modifier = Modifier.height(8.dp))
                val deleteEnabled = !isSubmitting &&
                    deleteState !is AppointmentViewState.DeleteAppointmentState.InProgress
                OutlinedButton(
                    onClick = {
                        if (editingAppointmentId != null) {
                            viewModel.requestDeleteAppointment(editingAppointmentId)
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    enabled = deleteEnabled,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (deleteEnabled) MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    )
                ) {
                    Text("Excluir consulta", style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
