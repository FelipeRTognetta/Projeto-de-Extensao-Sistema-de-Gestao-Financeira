package com.psychologist.financial.ui.screens

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.psychologist.financial.domain.models.DashboardMetrics
import com.psychologist.financial.domain.models.MonthlyMetrics
import com.psychologist.financial.ui.components.ErrorDialog
import com.psychologist.financial.ui.components.GridMetricsCard
import com.psychologist.financial.ui.components.MetricsCard
import com.psychologist.financial.ui.components.MonthSelector
import com.psychologist.financial.ui.components.WeeklyBreakdown
import com.psychologist.financial.viewmodel.DashboardViewModel
import com.psychologist.financial.viewmodel.DashboardViewState
import java.math.BigDecimal

/**
 * Dashboard screen
 *
 * Main dashboard displaying financial metrics for psychologist:
 * - Revenue, patients, average fee, outstanding balance
 * - Monthly metrics with weekly breakdown
 * - Month selection and navigation
 * - Trend analysis with previous month comparison
 *
 * Layout:
 * ```
 * ┌─────────────────────────────────┐
 * │ Month Navigation (Prev/Next)     │
 * ├─────────────────────────────────┤
 * │ Metrics Grid (4 cards):          │
 * │ • Revenue                        │
 * │ • Active Patients                │
 * │ • Average Fee                    │
 * │ • Outstanding Balance            │
 * ├─────────────────────────────────┤
 * │ Trend Comparison (Month-over-Mo) │
 * ├─────────────────────────────────┤
 * │ Weekly Breakdown Chart           │
 * └─────────────────────────────────┘
 * ```
 *
 * Features:
 * - Reactive updates via ViewModel
 * - Loading state with spinner
 * - Error handling with retry
 * - Empty state messages
 * - Responsive grid layout
 * - Color-coded metrics
 * - Month navigation controls
 * - Trend indicators
 * - Weekly revenue visualization
 * - Portuguese localization
 * - Material 3 styling
 *
 * Example:
 * ```kotlin
 * DashboardScreen(
 *     viewModel = dashboardViewModel
 * )
 * ```
 *
 * @param viewModel DashboardViewModel for state management
 */
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val state = viewModel.state.collectAsState()
    val currentState = state.value

    Log.d("DashboardScreen", "Rendering with state: ${currentState.metricsState::class.simpleName}")

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        when {
            // Loading state
            currentState.isLoading && currentState.metricsState is DashboardViewState.MetricsState.Loading -> {
                LoadingScreen(paddingValues)
            }

            // Error state
            currentState.hasError() -> {
                ErrorScreen(
                    paddingValues = paddingValues,
                    error = currentState.error ?: "Unknown error",
                    onRetry = { viewModel.refresh() },
                    onDismiss = { viewModel.clearError() }
                )
            }

            // Success state
            currentState.metricsState is DashboardViewState.MetricsState.Success -> {
                SuccessScreen(
                    paddingValues = paddingValues,
                    currentState = currentState,
                    viewModel = viewModel
                )
            }

            // Empty state
            else -> {
                EmptyScreen(paddingValues)
            }
        }
    }
}

/**
 * Success screen - Dashboard with metrics
 *
 * @param paddingValues Scaffold padding
 * @param currentState Current dashboard state
 * @param viewModel Dashboard view model
 */
@Composable
private fun SuccessScreen(
    paddingValues: PaddingValues,
    currentState: DashboardViewState.DashboardState,
    viewModel: DashboardViewModel
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Month selector
        item {
            MonthSelector(
                selectedMonth = currentState.selectedMonth,
                onPreviousMonth = { viewModel.goToPreviousMonth() },
                onNextMonth = { viewModel.goToNextMonth() },
                onSelectMonth = { viewModel.selectMonth(it) }
            )
        }

        // Main metrics cards (if available)
        item {
            if (currentState.metricsState is DashboardViewState.MetricsState.Success) {
                MetricsGrid(currentState.metricsState.metrics)
            }
        }

        // Trend section
        item {
            if (currentState.trendState is DashboardViewState.TrendState.Success) {
                TrendSection(currentState.trendState)
            }
        }

        // Weekly breakdown
        item {
            if (currentState.monthlyState is DashboardViewState.MonthlyState.Success) {
                WeeklyBreakdown(currentState.monthlyState.metrics)
            }
        }

        // Footer
        item {
            Text(
                text = "Última atualização: ${java.time.LocalDateTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                )}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Metrics grid - 2x2 card layout
 *
 * @param metrics DashboardMetrics to display
 */
@Composable
private fun MetricsGrid(
    metrics: DashboardMetrics
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Row 1: Revenue and Patients
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GridMetricsCard(
                icon = Icons.Default.AttachMoney,
                label = "Receita",
                value = metrics.getFormattedRevenue(),
                backgroundColor = Color(0xFFE8F5E9),
                modifier = Modifier.weight(1f)
            )
            GridMetricsCard(
                icon = Icons.Default.Group,
                label = "Pacientes",
                value = metrics.activePatients.toString(),
                backgroundColor = Color(0xFFE3F2FD),
                modifier = Modifier.weight(1f)
            )
        }

        // Row 2: Average Fee and Outstanding
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GridMetricsCard(
                icon = Icons.Default.TrendingUp,
                label = "Média",
                value = metrics.getFormattedAverageFee(),
                backgroundColor = Color(0xFFFFF3E0),
                modifier = Modifier.weight(1f)
            )
            GridMetricsCard(
                icon = Icons.Default.Warning,
                label = "Pendente",
                value = metrics.getFormattedOutstanding(),
                backgroundColor = Color(0xFFFFEBEE),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Helper function - GridMetricsCard with modifier
 *
 * Overload to support modifier parameter
 */
@Composable
private fun GridMetricsCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.Card(
        modifier = modifier.padding(4.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Trend section - Month comparison
 *
 * @param trendState Trend data
 */
@Composable
private fun TrendSection(
    trendState: DashboardViewState.TrendState.Success
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "Tendência",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Receita",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = trendState.getRevenueTrendText(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (trendState.isRevenueUp()) Color.Green else Color.Red
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Pacientes",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = trendState.getPatientTrendText(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (trendState.isPatientsUp()) Color.Green else Color.Red
                )
            }
        }
    }
}

/**
 * Loading screen
 *
 * @param paddingValues Scaffold padding
 */
@Composable
private fun LoadingScreen(
    paddingValues: PaddingValues
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Text(
            text = "Carregando...",
            modifier = Modifier.padding(top = 16.dp),
            fontSize = 14.sp
        )
    }
}

/**
 * Empty screen
 *
 * @param paddingValues Scaffold padding
 */
@Composable
private fun EmptyScreen(
    paddingValues: PaddingValues
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Sem dados para este mês",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Error screen
 *
 * @param paddingValues Scaffold padding
 * @param error Error message
 * @param onRetry Retry callback
 * @param onDismiss Dismiss callback
 */
@Composable
private fun ErrorScreen(
    paddingValues: PaddingValues,
    error: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ErrorDialog(
            message = error,
            onDismiss = onDismiss
        )
    }
}
