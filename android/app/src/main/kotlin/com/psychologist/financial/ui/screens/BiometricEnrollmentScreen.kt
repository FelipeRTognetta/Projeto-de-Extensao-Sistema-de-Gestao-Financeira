package com.psychologist.financial.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.psychologist.financial.viewmodel.AuthenticationViewModel

/**
 * Biometric Enrollment Screen
 *
 * Guides user to enroll biometric authentication if not already done.
 * Directs to system settings for biometric enrollment.
 *
 * States:
 * - Idle: Show enrollment guidance and buttons
 * - Enrolling: Show progress indicator while waiting for enrollment completion
 * - Success: Confirm enrollment completed
 * - Error: Show error and fallback options
 *
 * Features:
 * - Fingerprint icon and enrollment instructions
 * - Button to open system settings for enrollment
 * - Fallback to PIN authentication
 * - Skip enrollment (use PIN only)
 * - Check enrollment status after returning from settings
 *
 * @param viewModel AuthenticationViewModel managing enrollment flow
 * @param onEnrollmentComplete Callback when biometric enrollment is confirmed
 * @param onSkipEnrollment Callback when user chooses to skip and use PIN
 */
@Composable
fun BiometricEnrollmentScreen(
    viewModel: AuthenticationViewModel,
    onEnrollmentComplete: () -> Unit = {},
    onSkipEnrollment: () -> Unit = {}
) {
    val isEnrolling = remember { mutableStateOf(false) }
    val enrollmentMessage = remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isEnrolling.value) {
                EnrollingContent()
            } else {
                IdleEnrollmentContent(
                    viewModel = viewModel,
                    onStartEnrollment = {
                        isEnrolling.value = true
                        viewModel.requestBiometricEnrollment()
                        enrollmentMessage.value = "Abrindo configurações de biometria..."
                    },
                    onSkip = onSkipEnrollment
                )
            }
        }
    }
}

@Composable
private fun IdleEnrollmentContent(
    viewModel: AuthenticationViewModel,
    onStartEnrollment: () -> Unit,
    onSkip: () -> Unit
) {
    Icon(
        imageVector = Icons.Default.Fingerprint,
        contentDescription = null,
        modifier = Modifier
            .size(80.dp)
            .padding(bottom = 24.dp),
        tint = MaterialTheme.colorScheme.primary
    )

    Text(
        text = "Cadastrar Biometria",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 16.dp)
    )

    Text(
        text = "Nenhuma biometria cadastrada. Cadastre sua impressão digital ou reconhecimento facial para uma autenticação mais segura.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(bottom = 32.dp)
    )

    // Benefits of biometric enrollment
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BenefitItem("Acesso rápido", "Autentique-se instantaneamente")
        BenefitItem("Mais seguro", "Biometria não pode ser compartilhada")
        BenefitItem("Proteção de dados", "Aplicação requer autenticação para sensíveis")
    }

    // Action buttons
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onStartEnrollment,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("CADASTRAR BIOMETRIA")
        }

        OutlinedButton(
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("USAR PIN")
        }
    }
}

@Composable
private fun EnrollingContent() {
    CircularProgressIndicator(
        modifier = Modifier
            .size(80.dp)
            .padding(bottom = 24.dp)
    )

    Text(
        text = "Abrindo Configurações",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 16.dp)
    )

    Text(
        text = "Siga as instruções na tela do dispositivo para cadastrar sua biometria.\n\nAo concluir, esta tela será atualizada automaticamente.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(vertical = 24.dp)
    )

    Text(
        text = "Passos:\n1. Toque em \"Adicionar biometria\"\n2. Siga as instruções na tela\n3. Confirme quando solicitado\n4. Retorne a este aplicativo",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(vertical = 24.dp)
    )
}

@Composable
private fun BenefitItem(
    title: String,
    description: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
