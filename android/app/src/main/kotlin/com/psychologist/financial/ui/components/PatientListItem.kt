package com.psychologist.financial.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.psychologist.financial.domain.models.Patient
import com.psychologist.financial.domain.models.PatientStatus
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Reusable patient card component for list display
 *
 * Shows patient summary with:
 * - Avatar with initials
 * - Patient name
 * - Contact info (phone preferred)
 * - Status badge
 * - Last appointment date (if available)
 *
 * Clickable for navigation to detail screen.
 *
 * Usage:
 * ```kotlin
 * LazyColumn {
 *     items(patients) { patient ->
 *         PatientListItem(
 *             patient = patient,
 *             onClick = { navigateToDetail(patient.id) }
 *         )
 *     }
 * }
 * ```
 *
 * @param patient Patient to display
 * @param onClick Callback when card clicked
 * @param modifier Optional modifier
 */
@Composable
fun PatientListItem(
    patient: Patient,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hasPendingPayments: Boolean = false
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 4.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            // Avatar with initials
            Surface(
                modifier = Modifier.size(48.dp),
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
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Patient info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                // Name
                Text(
                    text = patient.getDisplayName(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Contact info
                if (!patient.primaryContact.isNullOrEmpty()) {
                    Row(
                        modifier = Modifier
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = "Contato",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = patient.primaryContact ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Last appointment (if available)
                if (patient.lastAppointmentDate != null) {
                    Text(
                        text = "Última consulta: ${patient.lastAppointmentDate}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Pending payment chip + status badge
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (hasPendingPayments) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ) {
                        Text(
                            text = "Pgto. em aberto",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                StatusBadge(
                    status = patient.status,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

/**
 * Status badge showing patient state
 *
 * @param status Patient status (ACTIVE/INACTIVE)
 * @param modifier Optional modifier
 */
@Composable
private fun StatusBadge(
    status: PatientStatus,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, textColor, text) = when (status) {
        PatientStatus.ACTIVE -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            "Ativo"
        )
        PatientStatus.INACTIVE -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            "Inativo"
        )
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = backgroundColor,
        contentColor = textColor
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ========================================
// Preview
// ========================================

@Preview(showBackground = true)
@Composable
fun PreviewPatientListItemActive() {
    MaterialTheme {
        Surface {
            PatientListItem(
                patient = Patient(
                    id = 1,
                    name = "João Silva Santos",
                    phone = "(11) 99999-9999",
                    email = "joao@example.com",
                    status = PatientStatus.ACTIVE,
                    initialConsultDate = LocalDate.of(2025, 1, 15),
                    registrationDate = LocalDate.of(2025, 1, 15),
                    lastAppointmentDate = LocalDate.of(2026, 2, 20),
                    createdDate = LocalDateTime.now()
                ),
                onClick = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewPatientListItemInactive() {
    MaterialTheme {
        Surface {
            PatientListItem(
                patient = Patient(
                    id = 2,
                    name = "Maria Santos",
                    phone = "(21) 98765-4321",
                    email = null,
                    status = PatientStatus.INACTIVE,
                    initialConsultDate = LocalDate.of(2024, 6, 10),
                    registrationDate = LocalDate.of(2024, 6, 10),
                    lastAppointmentDate = null,
                    createdDate = LocalDateTime.now()
                ),
                onClick = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewPatientListItemNoContact() {
    MaterialTheme {
        Surface {
            PatientListItem(
                patient = Patient(
                    id = 3,
                    name = "Carlos Oliveira",
                    phone = null,
                    email = "carlos@example.com",
                    status = PatientStatus.ACTIVE,
                    initialConsultDate = LocalDate.now(),
                    registrationDate = LocalDate.now(),
                    lastAppointmentDate = LocalDate.now().minusDays(5),
                    createdDate = LocalDateTime.now()
                ),
                onClick = {}
            )
        }
    }
}
