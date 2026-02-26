package com.psychologist.financial.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.psychologist.financial.viewmodel.AuthenticationViewModel

/**
 * PIN Fallback Screen
 *
 * Fallback authentication screen when biometric auth is unavailable.
 * Allows user to enter a 4-6 digit PIN to authenticate.
 *
 * Features:
 * - Numeric keypad (0-9)
 * - PIN length validation (4-6 digits)
 * - Visual feedback for entered digits (masked as dots)
 * - Backspace to delete last digit
 * - Submit button enabled only when PIN is valid length
 * - Cancel option to go back
 *
 * @param viewModel AuthenticationViewModel for PIN validation and submission
 * @param onCancel Callback when user cancels PIN entry
 */
@Composable
fun PINFallbackScreen(
    viewModel: AuthenticationViewModel,
    onCancel: () -> Unit = {}
) {
    val enteredPin = remember { mutableStateOf("") }
    val errorMessage = remember { mutableStateOf("") }
    val isSubmitting = remember { mutableStateOf(false) }

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
            Text(
                text = "Autentificação com PIN",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "Biometria não disponível. Digite seu PIN para continuar.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            // PIN Display
            PINDisplay(enteredPin.value)

            // Error message
            if (errorMessage.value.isNotEmpty()) {
                Text(
                    text = errorMessage.value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 16.dp),
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = "PIN deve ter 4-6 dígitos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                    textAlign = TextAlign.Center
                )
            }

            // Numeric Keypad
            NumericKeypad(
                onDigitClick = { digit ->
                    if (enteredPin.value.length < 6) {
                        enteredPin.value += digit
                        errorMessage.value = ""
                    }
                },
                onBackspaceClick = {
                    if (enteredPin.value.isNotEmpty()) {
                        enteredPin.value = enteredPin.value.dropLast(1)
                    }
                },
                modifier = Modifier.padding(vertical = 24.dp)
            )

            // Action Buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (enteredPin.value.length in 4..6 && enteredPin.value.all { it.isDigit() }) {
                            isSubmitting.value = true
                            viewModel.validateAndCompletePIN(enteredPin.value)
                        } else {
                            errorMessage.value = "PIN deve ter entre 4 e 6 dígitos"
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enteredPin.value.length >= 4 && !isSubmitting.value
                ) {
                    Text("CONFIRMAR")
                }

                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSubmitting.value
                ) {
                    Text("CANCELAR")
                }
            }
        }
    }
}

@Composable
private fun PINDisplay(pin: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(6) { index ->
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .size(50.dp),
                color = if (index < pin.length) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                if (index < pin.length) {
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun NumericKeypad(
    onDigitClick: (String) -> Unit,
    onBackspaceClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val digits = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "")
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        digits.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { digit ->
                    if (digit.isEmpty()) {
                        // Empty space
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .size(60.dp),
                            color = MaterialTheme.colorScheme.background
                        ) {}
                    } else {
                        // Digit button
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .size(60.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Button(
                                onClick = { onDigitClick(digit) },
                                modifier = Modifier
                                    .size(60.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(12.dp)
                                    ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = digit,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Backspace button at the bottom
        IconButton(
            onClick = onBackspaceClick,
            modifier = Modifier
                .align(Alignment.End)
                .padding(top = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Backspace,
                contentDescription = "Apagar",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
