package com.psychologist.financial.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.psychologist.financial.ui.components.ErrorDialog
import com.psychologist.financial.viewmodel.AuthState
import com.psychologist.financial.viewmodel.AuthenticationViewModel

/**
 * Authentication Screen
 *
 * Main authentication screen for app startup.
 * Handles biometric prompt, fallback options, enrollment guidance.
 *
 * States:
 * - Idle: Ready for authentication
 * - Authenticating: Biometric prompt shown
 * - Authenticated: User authenticated successfully
 * - NeedsPIN: Biometric failed, offer PIN fallback
 * - NeedsEnrollment: Guide user to enroll biometric
 * - Error: Show error message with retry
 *
 * @param viewModel AuthenticationViewModel
 * @param onAuthenticationSuccess Callback when user authenticated
 * @param onNavigateToApp Callback to proceed to main app
 */
@Composable
fun AuthenticationScreen(
    viewModel: AuthenticationViewModel,
    onAuthenticationSuccess: () -> Unit = {},
    onNavigateToApp: () -> Unit = {}
) {
    val authState = viewModel.authState.collectAsState()
    val error = viewModel.error.collectAsState()
    val currentState = authState.value

    LaunchedEffect(Unit) {
        viewModel.startAuthentication()
    }

    LaunchedEffect(currentState) {
        if (currentState is AuthState.Authenticated) {
            onAuthenticationSuccess()
            onNavigateToApp()
        }
    }

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
            when (currentState) {
                is AuthState.Idle -> IdleContent(viewModel)
                is AuthState.Authenticating -> AuthenticatingContent()
                is AuthState.NeedsPIN -> NeedsPINContent(viewModel)
                is AuthState.EnteringPIN -> PINFallbackScreen(viewModel)
                is AuthState.PINError -> PINFallbackScreen(viewModel)
                is AuthState.NeedsEnrollment -> NeedsEnrollmentContent(
                    currentState,
                    viewModel
                )
                is AuthState.UserCancelled -> UserCancelledContent(viewModel)
                is AuthState.Error -> ErrorContent(currentState, viewModel)
                else -> { }
            }
        }
    }

    if (error.value != null) {
        ErrorDialog(
            message = error.value ?: "",
            onDismiss = { viewModel.clearError() }
        )
    }
}

@Composable
private fun IdleContent(viewModel: AuthenticationViewModel) {
    Icon(
        imageVector = Icons.Default.Lock,
        contentDescription = null,
        modifier = Modifier.padding(bottom = 24.dp),
        tint = MaterialTheme.colorScheme.primary
    )

    Text(
        text = "Segurança",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold
    )

    Text(
        text = "Autentique-se para acessar a aplicação",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(vertical = 16.dp)
    )

    Button(
        onClick = { viewModel.startAuthentication() },
        modifier = Modifier.padding(top = 24.dp)
    ) {
        Text("INICIAR AUTENTICAÇÃO")
    }
}

@Composable
private fun AuthenticatingContent() {
    CircularProgressIndicator(
        modifier = Modifier.padding(bottom = 24.dp)
    )

    Text(
        text = "Autenticando...",
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun NeedsPINContent(viewModel: AuthenticationViewModel) {
    Text(
        text = "PIN Necessário",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
    )

    Text(
        text = "A biometria não está disponível. Use seu PIN.",
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(vertical = 16.dp)
    )

    Button(
        onClick = { viewModel.proceedWithPIN() },
        modifier = Modifier.padding(top = 24.dp)
    ) {
        Text("USAR PIN")
    }

    OutlinedButton(
        onClick = { viewModel.retryAuthentication() },
        modifier = Modifier.padding(top = 8.dp)
    ) {
        Text("TENTAR NOVAMENTE")
    }
}

@Composable
private fun NeedsEnrollmentContent(
    state: AuthState.NeedsEnrollment,
    viewModel: AuthenticationViewModel
) {
    Text(
        text = "Cadastro Necessário",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
    )

    Text(
        text = state.message,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(vertical = 16.dp)
    )

    Button(
        onClick = { viewModel.requestBiometricEnrollment() },
        modifier = Modifier.padding(top = 24.dp)
    ) {
        Text("CADASTRAR BIOMETRIA")
    }

    OutlinedButton(
        onClick = { viewModel.proceedWithPIN() },
        modifier = Modifier.padding(top = 8.dp)
    ) {
        Text("USAR PIN")
    }
}

@Composable
private fun UserCancelledContent(viewModel: AuthenticationViewModel) {
    Text(
        text = "Autenticação Cancelada",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.error
    )

    Text(
        text = "A autenticação é necessária para continuar",
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(vertical = 16.dp)
    )

    Button(
        onClick = { viewModel.startAuthentication() },
        modifier = Modifier.padding(top = 24.dp)
    ) {
        Text("TENTAR NOVAMENTE")
    }
}

@Composable
private fun ErrorContent(
    state: AuthState.Error,
    viewModel: AuthenticationViewModel
) {
    Text(
        text = "Erro na Autenticação",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.error
    )

    Text(
        text = state.message,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(vertical = 16.dp)
    )

    if (state.isRecoverable) {
        Button(
            onClick = { viewModel.retryAuthentication() },
            modifier = Modifier.padding(top = 24.dp)
        ) {
            Text("TENTAR NOVAMENTE")
        }

        OutlinedButton(
            onClick = { viewModel.proceedWithPIN() },
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text("USAR PIN")
        }
    } else {
        Text(
            text = "Esta aplicação não pode ser acessada neste dispositivo.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 24.dp)
        )
    }
}
