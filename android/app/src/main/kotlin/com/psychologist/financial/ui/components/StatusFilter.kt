package com.psychologist.financial.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Status Filter Component
 *
 * Provides UI controls to filter patients by status (ACTIVE/INACTIVE).
 * Two variants available:
 * - Toggle: Switch between showing only active or all patients
 * - Chips: Show active and inactive as selectable chips
 *
 * Architecture:
 * - Reusable component for any screen needing status filtering
 * - Supports two layout variants (toggle and chips)
 * - Portuguese localization
 * - Material 3 styling
 * - Stateless (receives state and callbacks from parent)
 *
 * Usage:
 * ```kotlin
 * // Toggle variant (simpler)
 * StatusFilterToggle(
 *     includeInactivePatients = includeInactive,
 *     onToggle = { viewModel.toggleInactiveFilter() }
 * )
 *
 * // Chips variant (more explicit)
 * StatusFilterChips(
 *     includeInactivePatients = includeInactive,
 *     onToggle = { viewModel.toggleInactiveFilter() }
 * )
 * ```
 *
 * @param includeInactivePatients Current filter state (true = show all, false = active only)
 * @param onToggle Callback when filter is toggled
 * @param modifier Optional modifier for styling
 */

/**
 * Status Filter Toggle
 *
 * Simple toggle switch showing "Ativos" or "Todos".
 * Compact layout suitable for app bars or headers.
 *
 * Visual:
 * ┌─────────────────────────────┐
 * │  Filter: Ativos  ▼  Todos   │
 * └─────────────────────────────┘
 *
 * @param includeInactivePatients Current state (true = show Todos, false = show Ativos)
 * @param onToggle Callback when toggled
 * @param modifier Optional modifier
 */
@Composable
fun StatusFilterToggle(
    includeInactivePatients: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onToggle)
            .padding(12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.FilterList,
            contentDescription = "Filtro de status",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = if (includeInactivePatients) "Todos os pacientes" else "Apenas ativos",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Status Filter Chips
 *
 * Two chip buttons: "Ativos" and "Inativos"
 * Shows which status is currently selected with checkmark.
 * More explicit control over filtering.
 *
 * Visual:
 * ┌─────────────┐  ┌─────────────┐
 * │ ✓ Ativos    │  │   Inativos  │
 * └─────────────┘  └─────────────┘
 *
 * @param includeInactivePatients Current state (true = both selected, false = active only)
 * @param onToggle Callback to toggle filter
 * @param modifier Optional modifier
 */
@Composable
fun StatusFilterChips(
    includeInactivePatients: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Active chip (always selected when not showing inactive)
        FilterChip(
            label = "Ativos",
            isSelected = !includeInactivePatients,
            onClick = {
                if (includeInactivePatients) {
                    onToggle()
                }
            },
            modifier = Modifier.weight(1f)
        )

        // All patients chip (selected when showing inactive)
        FilterChip(
            label = "Todos",
            isSelected = includeInactivePatients,
            onClick = {
                if (!includeInactivePatients) {
                    onToggle()
                }
            },
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Status Filter Inline
 *
 * Compact horizontal filter showing current state and toggle icon.
 * Suitable for embedding in list headers.
 *
 * Visual:
 * Mostrar inativos ○ ●
 *
 * @param includeInactivePatients Current state
 * @param onToggle Callback when clicked
 * @param modifier Optional modifier
 */
@Composable
fun StatusFilterInline(
    includeInactivePatients: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (includeInactivePatients) "Mostrar inativos" else "Ocultar inativos",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )

        Icon(
            imageVector = if (includeInactivePatients) {
                Icons.Default.Visibility
            } else {
                Icons.Default.VisibilityOff
            },
            contentDescription = "Toggle filter",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

/**
 * Status Indicator Badge
 *
 * Small badge showing patient status (ATIVO/INATIVO).
 * Used in list items and detail screens.
 * Color-coded for quick visual recognition.
 *
 * Visual:
 * ┌──────────┐
 * │  Ativo   │ (green)
 * └──────────┘
 *
 * or
 *
 * ┌──────────┐
 * │ Inativo  │ (gray)
 * └──────────┘
 *
 * @param isActive True for ATIVO status, false for INATIVO
 * @param modifier Optional modifier
 */
@Composable
fun StatusBadge(
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isActive) {
        Color(0xFFE8F5E9)  // Light green
    } else {
        Color(0xFFF5F5F5)  // Light gray
    }

    val textColor = if (isActive) {
        Color(0xFF2E7D32)  // Dark green
    } else {
        Color(0xFF757575)  // Dark gray
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isActive) "Ativo" else "Inativo",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )
    }
}

/**
 * Internal: Filter Chip
 *
 * Single selectable chip for status filtering.
 * Shows checkmark when selected.
 *
 * @param label Chip text (e.g., "Ativos")
 * @param isSelected Whether chip is currently selected
 * @param onClick Callback when clicked
 * @param modifier Optional modifier
 */
@Composable
private fun FilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = textColor
            )
        }
    }
}
