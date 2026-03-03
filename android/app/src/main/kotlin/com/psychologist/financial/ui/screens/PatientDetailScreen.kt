package com.psychologist.financial.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.psychologist.financial.domain.models.Patient
import com.psychologist.financial.domain.models.PatientStatus
import com.psychologist.financial.viewmodel.PatientViewModel
import com.psychologist.financial.viewmodel.PatientViewState.DetailState

/**
 * Patient detail screen
 *
 * Shows full patient profile with:
 * - Patient name and avatar
 * - Contact information
 * - Status badge
 * - Registration and consultation dates
 * - Appointment count (placeholder)
 * - Payment summary (placeholder)
 * - Edit button
 * - Mark as inactive button
 *
 * Navigation:
 * - Back button → PatientListScreen
 * - Edit button → PatientFormScreen (TODO: edit mode)
 *
 * Usage:
 * ```kotlin
 * PatientDetailScreen(
 *     viewModel = patientViewModel,
 *     patientId = 123,
 *     onBack = { navigateBack() },
 *     onEdit = { navigateToForm(patientId) }
 * )
 * ```
 *
 * @param viewModel PatientViewModel for data
 * @param patientId Patient ID to display
 * @param onBack Callback when back button tapped
 * @param onEdit Callback when edit button tapped
 */
@Composable
fun PatientDetailScreen(
    viewModel: PatientViewModel,
    patientId: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onNavigateToAppointments: (patientId: Long, patientName: String) -> Unit = { _, _ -> },
    onNavigateToPayments: (patientId: Long, patientName: String) -> Unit = { _, _ -> }
) {
    val detailState = viewModel.patientDetailState.collectAsState().value

    // Load patient detail when screen opens
    LaunchedEffect(patientId) {
        viewModel.selectPatient(patientId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Perfil do Paciente") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Voltar")
                    }
                },
                actions = {
                    if (detailState is DetailState.Success && detailState.isActive()) {
                        IconButton(onClick = { onEdit(patientId) }) {
                            Icon(Icons.Default.Edit, "Editar")
                        }
                    }
                },
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
            when (detailState) {
                is DetailState.Loading -> {
                    // Loading state
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is DetailState.Success -> {
                    // Patient detail content
                    PatientDetailContent(
                        patient = detailState.patient,
                        viewModel = viewModel,
                        onEdit = { onEdit(patientId) },
                        onNavigateToAppointments = {
                            onNavigateToAppointments(detailState.patient.id, detailState.patient.name)
                        },
                        onNavigateToPayments = {
                            onNavigateToPayments(detailState.patient.id, detailState.patient.name)
                        }
                    )
                }

                is DetailState.Error -> {
                    // Error state
                    ErrorContent(
                        message = detailState.message,
                        onBack = onBack
                    )
                }

                is DetailState.Idle -> {
                    // Empty state (shouldn't happen)
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Selecione um paciente")
                    }
                }
            }
        }
    }
}

/**
 * Patient detail content
 */
@Composable
private fun PatientDetailContent(
    patient: Patient,
    viewModel: PatientViewModel,
    onEdit: () -> Unit,
    onNavigateToAppointments: () -> Unit = {},
    onNavigateToPayments: () -> Unit = {}
) {
    val showStatusDialog = remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with avatar and name
        PatientHeader(patient)

        // Contact information
        ContactCard(patient)

        // Status and dates
        StatusCard(patient)

        // Navigation buttons — Appointments and Payments
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onNavigateToAppointments,
                modifier = Modifier.weight(1f)
            ) {
                Text("Consultas")
            }
            Button(
                onClick = onNavigateToPayments,
                modifier = Modifier.weight(1f)
            ) {
                Text("Pagamentos")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Action buttons
        if (patient.isActive) {
            Button(
                onClick = onEdit,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Editar Informações")
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = { showStatusDialog.value = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Marcar como Inativo")
            }
        } else {
            Button(
                onClick = { showStatusDialog.value = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Reativar Paciente")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }

    // Status change confirmation dialog
    if (showStatusDialog.value) {
        StatusChangeDialog(
            patient = patient,
            viewModel = viewModel,
            onDismiss = { showStatusDialog.value = false }
        )
    }
}

/**
 * Patient header with avatar and name
 */
@Composable
private fun PatientHeader(patient: Patient) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(80.dp),
            shape = CircleShape,
            color = when (patient.status) {
                PatientStatus.ACTIVE -> MaterialTheme.colorScheme.primaryContainer
                PatientStatus.INACTIVE -> MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = when (patient.status) {
                PatientStatus.ACTIVE -> MaterialTheme.colorScheme.onPrimaryContainer
                PatientStatus.INACTIVE -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        ) {
            Text(
                text = patient.getInitials(),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = patient.getDisplayName(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Surface(
            shape = RoundedCornerShape(6.dp),
            color = when (patient.status) {
                PatientStatus.ACTIVE -> MaterialTheme.colorScheme.tertiaryContainer
                PatientStatus.INACTIVE -> MaterialTheme.colorScheme.errorContainer
            },
            contentColor = when (patient.status) {
                PatientStatus.ACTIVE -> MaterialTheme.colorScheme.onTertiaryContainer
                PatientStatus.INACTIVE -> MaterialTheme.colorScheme.onErrorContainer
            }
        ) {
            Text(
                text = patient.getStatusDisplayName(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

/**
 * Contact information card
 */
@Composable
private fun ContactCard(patient: Patient) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Contato",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            if (!patient.phone.isNullOrEmpty()) {
                ContactRow(label = "Telefone", value = patient.phone)
            }

            if (!patient.email.isNullOrEmpty()) {
                ContactRow(label = "Email", value = patient.email)
            }

            if (patient.phone.isNullOrEmpty() && patient.email.isNullOrEmpty()) {
                Text(
                    text = "Sem contato registrado",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Contact information row
 */
@Composable
private fun ContactRow(label: String, value: String) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/**
 * Status and dates card
 */
@Composable
private fun StatusCard(patient: Patient) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Informações",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Primeira Consulta",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = patient.initialConsultDate.toString(),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Data de Registro",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = patient.registrationDate.toString(),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (patient.lastAppointmentDate != null) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Última Consulta",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = patient.lastAppointmentDate.toString(),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

/**
 * Summary card (appointments, payments)
 */
@Composable
private fun SummaryCard(
    title: String,
    value: String,
    subtitle: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * Error state content
 */
@Composable
private fun ErrorContent(
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
            text = "Erro ao carregar",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        TextButton(onClick = onBack) {
            Text("Voltar")
        }
    }
}

/**
 * Status change confirmation dialog
 *
 * Shows confirmation before marking patient active/inactive.
 * Includes warning about consequences of status change.
 */
@Composable
private fun StatusChangeDialog(
    patient: Patient,
    viewModel: PatientViewModel,
    onDismiss: () -> Unit
) {
    val isActive = patient.isActive

    AlertDialog(
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                text = if (isActive) "Marcar como Inativo?" else "Reativar Paciente?",
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (isActive) {
                        "Ao marcar \"${patient.name}\" como inativo:\n\n" +
                                "• Não será possível adicionar novos atendimentos\n" +
                                "• Não será possível registrar novos pagamentos\n" +
                                "• O paciente será ocultado da lista ativa\n" +
                                "• Os dados históricos serão preservados"
                    } else {
                        "Ao reativar \"${patient.name}\":\n\n" +
                                "• Será possível adicionar novos atendimentos\n" +
                                "• Será possível registrar novos pagamentos\n" +
                                "• O paciente aparecerá na lista ativa\n" +
                                "• Todos os dados históricos serão mantidos"
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    if (isActive) {
                        viewModel.markPatientInactive(patient.id)
                    } else {
                        viewModel.reactivatePatient(patient.id)
                    }
                    onDismiss()
                }
            ) {
                Text(if (isActive) "Marcar Inativo" else "Reativar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
