package com.psychologist.financial.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/**
 * Reusable error dialog component for displaying errors
 *
 * Features:
 * - Consistent error styling across app
 * - Dismissible via button or close icon
 * - Optional retry callback
 * - Material 3 design system
 * - Accessible (proper content descriptions)
 *
 * Architecture:
 * - Used by all screens for error display
 * - Triggered by BaseViewModel error state
 * - Dismisses on user action or retry
 *
 * Usage:
 * ```kotlin
 * val error = viewModel.error.collectAsState().value
 *
 * if (error != null) {
 *     ErrorDialog(
 *         message = error,
 *         onDismiss = { viewModel.clearError() },
 *         onRetry = { viewModel.loadData() }
 *     )
 * }
 * ```
 *
 * @param message Error message to display
 * @param onDismiss Callback when dialog is dismissed
 * @param title Optional custom title (defaults to "Erro")
 * @param onRetry Optional retry callback (shows retry button if provided)
 * @param modifier Modifier for customization
 */
@Composable
fun ErrorDialog(
    message: String,
    onDismiss: () -> Unit,
    title: String = "Erro",
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.error
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("OK")
            }
        },
        dismissButton = {
            if (onRetry != null) {
                Button(
                    onClick = {
                        onRetry()
                        onDismiss()
                    }
                ) {
                    Text("Tentar Novamente")
                }
            }
        },
        // Prevent dismissal by tapping outside dialog
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        ),
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.error,
        textContentColor = MaterialTheme.colorScheme.onSurface
    )
}

/**
 * Alternative error dialog with explicit close button
 *
 * Useful when retry is the primary action (close button less prominent).
 *
 * @param message Error message
 * @param onDismiss Callback on close
 * @param onRetry Retry callback
 * @param title Custom title
 * @param modifier Modifier
 */
@Composable
fun ErrorDialogWithRetry(
    message: String,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    title: String = "Erro",
    modifier: Modifier = Modifier
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.error
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onRetry()
                    onDismiss()
                }
            ) {
                Text("Tentar Novamente")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Fechar")
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    )
}

/**
 * Error banner for displaying non-critical errors inline
 *
 * Alternative to dialog - shows error in-place without blocking UI.
 * Use for validation errors, warnings, non-blocking issues.
 *
 * @param message Error message to display
 * @param onDismiss Callback when banner is dismissed
 * @param modifier Modifier for customization
 *
 * Example:
 * ```kotlin
 * Column {
 *     if (formError != null) {
 *         ErrorBanner(
 *             message = formError,
 *             onDismiss = { formError = null }
 *         )
 *     }
 *     // Form content below
 * }
 * ```
 */
@Composable
fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    ) {
        androidx.compose.material3.ListItem(
            headlineContent = {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall
                )
            },
            trailingContent = {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Descartar",
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        )
    }
}

/**
 * Loading dialog with spinner
 *
 * Shows indeterminate progress indicator while loading.
 * Blocks user interaction while operation is in progress.
 *
 * @param message Loading message (e.g., "Carregando...")
 * @param modifier Modifier
 *
 * Example:
 * ```kotlin
 * if (isLoading) {
 *     LoadingDialog(message = "Salvando...")
 * }
 * ```
 */
@Composable
fun LoadingDialog(
    message: String = "Carregando...",
    modifier: Modifier = Modifier
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = {},  // Don't dismiss while loading
        text = {
            androidx.compose.material3.CircularProgressIndicator()
        },
        confirmButton = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    )
}

// ========================================
// Previews for Design System
// ========================================

/**
 * Preview: Standard error dialog
 */
@Preview(showBackground = true)
@Composable
fun PreviewErrorDialog() {
    MaterialTheme {
        Surface {
            ErrorDialog(
                message = "Falha ao carregar dados da rede. Verifique sua conexão e tente novamente.",
                onDismiss = {},
                onRetry = {},
                title = "Erro de Carregamento"
            )
        }
    }
}

/**
 * Preview: Error dialog with retry
 */
@Preview(showBackground = true)
@Composable
fun PreviewErrorDialogWithRetry() {
    MaterialTheme {
        Surface {
            ErrorDialogWithRetry(
                message = "Falha ao salvar paciente: Email já em uso.",
                onDismiss = {},
                onRetry = {},
                title = "Erro de Validação"
            )
        }
    }
}

/**
 * Preview: Error banner
 */
@Preview(showBackground = true)
@Composable
fun PreviewErrorBanner() {
    MaterialTheme {
        Surface {
            ErrorBanner(
                message = "O campo de telefone deve conter apenas números e caracteres especiais válidos.",
                onDismiss = {}
            )
        }
    }
}

/**
 * Preview: Loading dialog
 */
@Preview(showBackground = true)
@Composable
fun PreviewLoadingDialog() {
    MaterialTheme {
        Surface {
            LoadingDialog(message = "Salvando pagamento...")
        }
    }
}
