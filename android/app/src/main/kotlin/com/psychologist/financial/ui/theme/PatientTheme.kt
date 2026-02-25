package com.psychologist.financial.ui.theme

import androidx.compose.foundation.isSystemInDarkMode
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Color palette for Patient Management screens
 *
 * Material 3 design system with custom colors.
 * Adapts to system dark mode automatically.
 *
 * Color Scheme:
 * - Primary: Purple #6200EE (main actions, headers)
 * - Secondary: Teal #03DAC6 (secondary actions)
 * - Tertiary: Orange #FF6B6B (tertiary elements, success)
 * - Error: Red #FF5252 (errors, destructive actions)
 * - Background: White/Dark Surface
 *
 * Usage:
 * ```kotlin
 * PatientTheme {
 *     // Your composables with automatic theme colors
 * }
 * ```
 */

// ========================================
// Light Mode Colors
// ========================================

private val LightPrimary = Color(0xFF6200EE)
private val LightOnPrimary = Color(0xFFFFFFFF)
private val LightPrimaryContainer = Color(0xFFEADDFF)
private val LightOnPrimaryContainer = Color(0xFF21005E)

private val LightSecondary = Color(0xFF03DAC6)
private val LightOnSecondary = Color(0xFFFFFFFF)
private val LightSecondaryContainer = Color(0xFFB0F0EE)
private val LightOnSecondaryContainer = Color(0xFF00342C)

private val LightTertiary = Color(0xFF7D5260)
private val LightOnTertiary = Color(0xFFFFFFFF)
private val LightTertiaryContainer = Color(0xFFFFD8E4)
private val LightOnTertiaryContainer = Color(0xFF2F121D)

private val LightError = Color(0xFFB3261E)
private val LightOnError = Color(0xFFFFFFFF)
private val LightErrorContainer = Color(0xFFF9DEDC)
private val LightOnErrorContainer = Color(0xFF410E0B)

private val LightBackground = Color(0xFFFFFBFE)
private val LightOnBackground = Color(0xFF1C1B1F)
private val LightSurface = Color(0xFFFFFBFE)
private val LightOnSurface = Color(0xFF1C1B1F)
private val LightSurfaceVariant = Color(0xFFEAE0EB)
private val LightOnSurfaceVariant = Color(0xFF49454E)

// ========================================
// Dark Mode Colors
// ========================================

private val DarkPrimary = Color(0xFFBB86FC)
private val DarkOnPrimary = Color(0xFF3700B3)
private val DarkPrimaryContainer = Color(0xFF3700B3)
private val DarkOnPrimaryContainer = Color(0xFFEADDFF)

private val DarkSecondary = Color(0xFF03DAC6)
private val DarkOnSecondary = Color(0xFF005047)
private val DarkSecondaryContainer = Color(0xFF007071)
private val DarkOnSecondaryContainer = Color(0xFFB0F0EE)

private val DarkTertiary = Color(0xFFFFB4C7)
private val DarkOnTertiary = Color(0xFF5A1E3A)
private val DarkTertiaryContainer = Color(0xFF7D3A50)
private val DarkOnTertiaryContainer = Color(0xFFFFD8E4)

private val DarkError = Color(0xFFF2B8B5)
private val DarkOnError = Color(0xFF601410)
private val DarkErrorContainer = Color(0xFF8C1D18)
private val DarkOnErrorContainer = Color(0xFFF9DEDC)

private val DarkBackground = Color(0xFF1C1B1F)
private val DarkOnBackground = Color(0xFFE7E0EC)
private val DarkSurface = Color(0xFF1C1B1F)
private val DarkOnSurface = Color(0xFFE7E0EC)
private val DarkSurfaceVariant = Color(0xFF49454E)
private val DarkOnSurfaceVariant = Color(0xFFCAC7D0)

// ========================================
// Color Scheme Definitions
// ========================================

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant
)

// ========================================
// Theme Composable
// ========================================

/**
 * Patient Management Theme
 *
 * Applies Material 3 design system with custom colors.
 * Automatically uses dark colors on dark systems.
 *
 * @param darkTheme Whether to use dark color scheme (default: system dark mode)
 * @param content Composable content to apply theme to
 *
 * Usage:
 * ```kotlin
 * PatientTheme {
 *     Surface {
 *         PatientListScreen()
 *     }
 * }
 * ```
 */
@Composable
fun PatientTheme(
    darkTheme: Boolean = isSystemInDarkMode(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PatientTypography,
        shapes = PatientShapes,
        content = content
    )
}

// ========================================
// Typography
// ========================================

/**
 * Typography configuration for Patient Management
 *
 * Follows Material 3 guidelines with Portuguese-friendly font sizing.
 * Uses system default font family (Roboto on Android).
 */
val PatientTypography = androidx.compose.material3.Typography()

// ========================================
// Shapes
// ========================================

/**
 * Shape configuration for Patient Management
 *
 * Rounded corners for modern Material 3 appearance.
 */
val PatientShapes = androidx.compose.material3.Shapes()

// ========================================
// Semantic Colors (Functional)
// ========================================

/**
 * Success color (for positive feedback)
 */
val SuccessColor = Color(0xFF00C853)

/**
 * Warning color (for alerts)
 */
val WarningColor = Color(0xFFFF6D00)

/**
 * Info color (for information)
 */
val InfoColor = Color(0xFF2196F3)

// ========================================
// Helper Functions
// ========================================

/**
 * Get color scheme for current theme
 *
 * @return ColorScheme (light or dark)
 */
@Composable
fun getCurrentColorScheme(): ColorScheme {
    return if (isSystemInDarkMode()) DarkColorScheme else LightColorScheme
}

/**
 * Check if current theme is dark
 *
 * @return true if dark theme is active
 */
@Composable
fun isDarkTheme(): Boolean = isSystemInDarkMode()
