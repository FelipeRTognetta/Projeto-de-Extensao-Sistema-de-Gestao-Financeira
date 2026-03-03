package com.psychologist.financial.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psychologist.financial.viewmodel.DashboardViewState
import java.time.YearMonth

/**
 * Month selector component
 *
 * Allows user to navigate between months with Previous/Next buttons.
 * Displays current month name and year with navigation controls.
 *
 * Layout:
 * ```
 * ┌─────────────────────────────────────┐
 * │ ◀  Março de 2024  ▶                 │
 * └─────────────────────────────────────┘
 * ```
 *
 * Features:
 * - Month name in Portuguese
 * - Previous/Next navigation buttons
 * - Disabled state when at boundaries
 * - Clickable month name for calendar picker
 * - Responsive layout
 * - Material 3 styling
 *
 * Example:
 * ```kotlin
 * MonthSelector(
 *     selectedMonth = monthState.value,
 *     onPreviousMonth = { viewModel.goToPreviousMonth() },
 *     onNextMonth = { viewModel.goToNextMonth() },
 *     onSelectMonth = { viewModel.selectMonth(it) }
 * )
 * ```
 *
 * @param selectedMonth Current month selection state
 * @param onPreviousMonth Callback for previous month
 * @param onNextMonth Callback for next month
 * @param onSelectMonth Callback for month selection
 */
@Composable
fun MonthSelector(
    selectedMonth: DashboardViewState.SelectedMonthState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectMonth: (YearMonth) -> Unit = {}
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Previous month button
            IconButton(
                onClick = onPreviousMonth,
                enabled = selectedMonth.canGoToPrevious(),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Mês anterior",
                    tint = if (selectedMonth.canGoToPrevious()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    }
                )
            }

            // Month name (clickable)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = { onSelectMonth(selectedMonth.selectedMonth) })
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = selectedMonth.getCurrentMonthName(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            }

            // Next month button
            IconButton(
                onClick = onNextMonth,
                enabled = selectedMonth.canGoToNext(),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Próximo mês",
                    tint = if (selectedMonth.canGoToNext()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    }
                )
            }
        }
    }
}

/**
 * Compact month selector (alternative design)
 *
 * Horizontal scrollable month picker.
 *
 * @param selectedMonth Current month
 * @param months List of months to display
 * @param onSelectMonth Callback for selection
 */
@Composable
fun CompactMonthSelector(
    selectedMonth: YearMonth,
    months: List<YearMonth>,
    onSelectMonth: (YearMonth) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        months.forEach { month ->
            Box(
                modifier = Modifier
                    .background(
                        color = if (month == selectedMonth) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = CircleShape
                    )
                    .clickable { onSelectMonth(month) }
                    .padding(8.dp)
                    .size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = month.monthValue.toString().padStart(2, '0'),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (month == selectedMonth) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}
