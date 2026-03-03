package com.psychologist.financial.viewmodel

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.psychologist.financial.data.repositories.DashboardRepository
import com.psychologist.financial.domain.models.MonthlyMetrics
import com.psychologist.financial.domain.usecases.GetDashboardMetricsUseCase
import com.psychologist.financial.domain.usecases.MonthComparisonResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.YearMonth

/**
 * Dashboard ViewModel
 *
 * Manages dashboard state and user interactions for financial metrics display.
 * Handles month selection, metrics loading, and trend analysis.
 *
 * Responsibilities:
 * - Manage metrics loading and caching
 * - Handle month selection and navigation
 * - Calculate trends and comparisons
 * - Manage loading/error states
 * - Coordinate repository and use case calls
 * - Provide reactive state updates via StateFlow
 *
 * Architecture:
 * - Extends BaseViewModel for lifecycle management
 * - Uses GetDashboardMetricsUseCase for business logic
 * - Manages separate StateFlows for metrics, monthly, trends
 * - Combines all states into DashboardState
 * - Error handling via exceptionHandler from base class
 *
 * State Management:
 * - Reactive: Uses Flow for automatic UI updates
 * - Separated concerns: Metrics, Monthly, Trends are independent
 * - Cached: Stores previous month to avoid redundant queries
 * - Coordinated: DashboardState combines all sub-states
 *
 * Usage:
 * ```kotlin
 * val viewModel = DashboardViewModel(repository, useCase)
 *
 * // Observe metrics
 * val state = viewModel.state.collectAsState()
 * when (state.value.metricsState) {
 *     is DashboardViewState.MetricsState.Success -> {
 *         Text("Revenue: ${metrics.getFormattedRevenue()}")
 *     }
 *     // ... other states
 * }
 *
 * // Change month
 * viewModel.goToPreviousMonth()
 * viewModel.goToNextMonth()
 * viewModel.selectMonth(YearMonth.of(2024, 3))
 * ```
 *
 * @property repository DashboardRepository for direct queries
 * @property useCase GetDashboardMetricsUseCase for orchestrated operations
 */
class DashboardViewModel(
    private val repository: DashboardRepository,
    private val useCase: GetDashboardMetricsUseCase
) : BaseViewModel() {

    private companion object {
        private const val TAG = "DashboardViewModel"
    }

    // ========================================
    // State Management
    // ========================================

    /**
     * Complete dashboard state
     *
     * Combines metrics, monthly, trends, and month selection into one view state.
     */
    private val _state = MutableStateFlow(DashboardViewState.initialState())
    val state: StateFlow<DashboardViewState.DashboardState> = _state.asStateFlow()

    /**
     * Selected month state
     *
     * Tracks current month selection and navigation.
     */
    private val _selectedMonth = MutableStateFlow(
        DashboardViewState.SelectedMonthState(YearMonth.now())
    )
    val selectedMonth: StateFlow<DashboardViewState.SelectedMonthState> = _selectedMonth.asStateFlow()

    /**
     * Metrics state (current month)
     *
     * Dashboard metrics for selected month.
     */
    private val _metricsState = MutableStateFlow<DashboardViewState.MetricsState>(
        DashboardViewState.MetricsState.Loading
    )
    val metricsState: StateFlow<DashboardViewState.MetricsState> = _metricsState.asStateFlow()

    /**
     * Monthly state with weekly breakdown
     *
     * Detailed metrics with revenue per week.
     */
    private val _monthlyState = MutableStateFlow<DashboardViewState.MonthlyState>(
        DashboardViewState.MonthlyState.Loading
    )
    val monthlyState: StateFlow<DashboardViewState.MonthlyState> = _monthlyState.asStateFlow()

    /**
     * Trend state (month-over-month comparison)
     *
     * Compares current and previous months.
     */
    private val _trendState = MutableStateFlow<DashboardViewState.TrendState>(
        DashboardViewState.TrendState.Loading
    )
    val trendState: StateFlow<DashboardViewState.TrendState> = _trendState.asStateFlow()

    /**
     * Cache for previous month metrics
     *
     * Prevents redundant loads when comparing months.
     */
    private var cachedPreviousMonth: MonthlyMetrics? = null

    // ========================================
    // Initialization
    // ========================================

    init {
        Log.d(TAG, "DashboardViewModel initialized")
        loadDashboard()
    }

    // ========================================
    // Public API
    // ========================================

    /**
     * Load complete dashboard for current selected month
     *
     * Loads metrics, monthly breakdown, and trends.
     */
    fun loadDashboard() {
        Log.d(TAG, "Loading dashboard for ${_selectedMonth.value.selectedMonth}")
        setDashboardLoading(true)

        val currentMonth = _selectedMonth.value.selectedMonth

        // Load metrics (current month)
        loadMetrics(currentMonth)

        // Load monthly breakdown (current month)
        loadMonthlyMetrics(currentMonth)

        // Load trends (previous month)
        loadTrendData(currentMonth)

        setDashboardLoading(false)
    }

    /**
     * Refresh current dashboard
     *
     * Reloads all data for currently selected month.
     */
    fun refresh() {
        Log.d(TAG, "Refreshing dashboard")
        loadDashboard()
    }

    // ========================================
    // Month Navigation
    // ========================================

    /**
     * Go to previous month
     *
     * Updates selected month and loads new dashboard.
     */
    fun goToPreviousMonth() {
        val current = _selectedMonth.value
        if (current.canGoToPrevious()) {
            val previousMonth = current.selectedMonth.minusMonths(1)
            selectMonth(previousMonth)
        }
    }

    /**
     * Go to next month
     *
     * Updates selected month and loads new dashboard.
     */
    fun goToNextMonth() {
        val current = _selectedMonth.value
        if (current.canGoToNext()) {
            val nextMonth = current.selectedMonth.plusMonths(1)
            selectMonth(nextMonth)
        }
    }

    /**
     * Select specific month
     *
     * Updates selected month and loads dashboard for that month.
     *
     * @param yearMonth Month to select
     */
    fun selectMonth(yearMonth: YearMonth) {
        Log.d(TAG, "Selecting month: $yearMonth")

        _selectedMonth.value = DashboardViewState.SelectedMonthState(yearMonth)
        loadDashboard()
    }

    /**
     * Go to current month
     *
     * Shortcut to select and load current month.
     */
    fun goToCurrentMonth() {
        selectMonth(YearMonth.now())
    }

    // ========================================
    // Data Loading
    // ========================================

    /**
     * Load metrics for month
     *
     * @param yearMonth Month to load
     */
    private fun loadMetrics(yearMonth: YearMonth) {
        launchDashboard {
            try {
                Log.d(TAG, "Loading metrics for $yearMonth")
                val metrics = useCase.execute(yearMonth)

                if (metrics.isValid()) {
                    _metricsState.value = DashboardViewState.MetricsState.Success(metrics)
                    Log.d(TAG, "Metrics loaded: revenue=${metrics.getFormattedRevenue()}")
                } else {
                    Log.w(TAG, "Metrics validation failed for $yearMonth")
                    _metricsState.value = DashboardViewState.MetricsState.Empty(
                        "No valid metrics for this month"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load metrics for $yearMonth", e)
                _metricsState.value = DashboardViewState.MetricsState.Error(
                    e.message ?: "Failed to load metrics"
                )
                setDashboardError("Failed to load metrics: ${e.message}")
            }
        }
    }

    /**
     * Load monthly metrics with weekly breakdown
     *
     * @param yearMonth Month to load
     */
    private fun loadMonthlyMetrics(yearMonth: YearMonth) {
        launchDashboard {
            try {
                Log.d(TAG, "Loading monthly metrics for $yearMonth")

                // Get metrics with weekly breakdown
                val metrics = useCase.execute(yearMonth)
                val monthlyMetrics = repository.getMetricsForMonth(yearMonth)
                    .let { dashboardMetrics ->
                        // Create MonthlyMetrics from DashboardMetrics
                        val weeklyBreakdown = getWeeklyBreakdown(yearMonth)
                        MonthlyMetrics(
                            yearMonth = yearMonth,
                            totalRevenue = dashboardMetrics.totalRevenue,
                            weeklyBreakdown = weeklyBreakdown
                        )
                    }

                _monthlyState.value = DashboardViewState.MonthlyState.Success(monthlyMetrics)
                Log.d(TAG, "Monthly metrics loaded with ${monthlyMetrics.weekCount} weeks")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load monthly metrics for $yearMonth", e)
                _monthlyState.value = DashboardViewState.MonthlyState.Error(
                    e.message ?: "Failed to load monthly metrics"
                )
            }
        }
    }

    /**
     * Load trend data (comparison with previous month)
     *
     * @param yearMonth Current month
     */
    private fun loadTrendData(yearMonth: YearMonth) {
        launchDashboard {
            try {
                Log.d(TAG, "Loading trend data for $yearMonth")

                val previousMonth = yearMonth.minusMonths(1)
                val comparison = useCase.compareMonths(yearMonth, previousMonth)

                _trendState.value = DashboardViewState.TrendState.Success(
                    currentMonth = comparison.currentMonth,
                    previousMonth = comparison.previousMonth,
                    revenueTrend = comparison.revenueTrend,
                    patientTrend = comparison.patientTrend
                )

                Log.d(TAG, "Trend data loaded: revenue=${comparison.revenueTrend}%, " +
                        "patients=${comparison.patientTrend}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load trend data", e)
                _trendState.value = DashboardViewState.TrendState.Error(
                    e.message ?: "Failed to load trend data"
                )
            }
        }
    }

    // ========================================
    // State Updates
    // ========================================

    /**
     * Update combined dashboard state
     *
     * Combines all sub-states into one coherent dashboard state.
     */
    private fun updateDashboardState() {
        _state.value = DashboardViewState.DashboardState(
            metricsState = _metricsState.value,
            monthlyState = _monthlyState.value,
            trendState = _trendState.value,
            selectedMonth = _selectedMonth.value,
            isLoading = isLoading.value,
            error = error.value
        )
    }

    /**
     * Safe launch for coroutines
     *
     * Wraps launch with error handling.
     *
     * @param block Suspend function to execute
     */
    private fun launchDashboard(block: suspend () -> Unit) {
        viewModelScope.launch(coroutineContext) {
            try {
                block()
            } finally {
                updateDashboardState()
            }
        }
    }

    /**
     * Set loading state
     *
     * @param isLoading True if loading
     */
    private fun setDashboardLoading(isLoading: Boolean) {
        setLoading(isLoading)
        updateDashboardState()
    }

    /**
     * Set error message
     *
     * @param message Error message
     */
    private fun setDashboardError(message: String?) {
        if (message != null) setError(message) else clearError()
        updateDashboardState()
    }

    /**
     * Clear error
     */
    override fun clearError() {
        super.clearError()
        updateDashboardState()
    }

    // ========================================
    // Helper Methods
    // ========================================

    /**
     * Get weekly breakdown for month
     *
     * Creates WeeklyMetrics for each week of month.
     *
     * @param yearMonth Month to analyze
     * @return Map of week number to WeeklyMetrics
     */
    private suspend fun getWeeklyBreakdown(yearMonth: YearMonth): Map<Int, com.psychologist.financial.domain.models.WeeklyMetrics> {
        // This would normally get weekly data from repository or service
        // For now, return empty map (would be populated by MetricsAggregator)
        return emptyMap()
    }

    /**
     * Get high level summary for month
     *
     * @param yearMonth Month to summarize
     * @return Summary string
     */
    suspend fun getMonthSummary(yearMonth: YearMonth): String {
        return try {
            val metrics = useCase.execute(yearMonth)
            metrics.getSummary()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get month summary", e)
            "Error loading summary"
        }
    }

    /**
     * Get list of last N months
     *
     * @param months Number of months
     * @return List of YearMonth
     */
    fun getLastMonths(months: Int): List<YearMonth> {
        val today = YearMonth.now()
        return (0 until months).map { i ->
            today.minusMonths(i.toLong())
        }.reversed()
    }

    /**
     * Check if can go back to previous month
     *
     * @return true if previous month available
     */
    fun canGoToPrevious(): Boolean {
        return _selectedMonth.value.canGoToPrevious()
    }

    /**
     * Check if can go to next month
     *
     * @return true if next month available
     */
    fun canGoToNext(): Boolean {
        return _selectedMonth.value.canGoToNext()
    }

    /**
     * Check if currently viewing current month
     *
     * @return true if selected month is now
     */
    fun isCurrentMonth(): Boolean {
        return _selectedMonth.value.isCurrentMonth()
    }
}
