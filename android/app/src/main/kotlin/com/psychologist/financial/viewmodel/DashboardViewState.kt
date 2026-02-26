package com.psychologist.financial.viewmodel

import com.psychologist.financial.domain.models.DashboardMetrics
import com.psychologist.financial.domain.models.MonthlyMetrics
import java.time.YearMonth

/**
 * Dashboard ViewModel state classes
 *
 * Defines sealed classes and data classes for managing dashboard screen states.
 * Provides type-safe state management for metrics display with loading/error handling.
 *
 * State Hierarchy:
 * - MetricsState: Dashboard metrics view (Loading, Success, Empty, Error)
 * - TrendState: Trend analysis view (trend data, comparisons)
 * - SelectedMonthState: Month selector state
 * - ErrorState: Error handling with retry capability
 *
 * Usage:
 * ```kotlin
 * // Metrics state
 * when (metricsState) {
 *     is DashboardViewState.MetricsState.Loading -> showLoadingIndicator()
 *     is DashboardViewState.MetricsState.Success -> displayMetrics(metrics)
 *     is DashboardViewState.MetricsState.Empty -> showEmptyMessage()
 *     is DashboardViewState.MetricsState.Error -> showErrorMessage(message)
 * }
 *
 * // Monthly state
 * when (monthlyState) {
 *     is DashboardViewState.MonthlyState.Loading -> showLoading()
 *     is DashboardViewState.MonthlyState.Success -> displayWeekly(monthly)
 *     is DashboardViewState.MonthlyState.Error -> showError(message)
 * }
 * ```
 */
object DashboardViewState {

    // ========================================
    // Metrics State
    // ========================================

    /**
     * Dashboard metrics display state
     *
     * Represents different states when displaying metrics.
     */
    sealed class MetricsState {
        /**
         * Loading metrics
         */
        object Loading : MetricsState()

        /**
         * Metrics loaded successfully
         *
         * @param metrics Dashboard metrics for month
         */
        data class Success(
            val metrics: DashboardMetrics
        ) : MetricsState() {
            /**
             * Get revenue as formatted string
             */
            fun getFormattedRevenue(): String = metrics.getFormattedRevenue()

            /**
             * Get outstanding as formatted string
             */
            fun getFormattedOutstanding(): String = metrics.getFormattedOutstanding()

            /**
             * Get average fee as formatted string
             */
            fun getFormattedAverageFee(): String = metrics.getFormattedAverageFee()

            /**
             * Get month name
             */
            fun getMonthName(): String = metrics.getFormattedMonth()

            /**
             * Check if has data
             */
            fun hasData(): Boolean = metrics.totalRevenue > java.math.BigDecimal.ZERO

            /**
             * Get summary line
             */
            fun getSummary(): String = metrics.getSummary()
        }

        /**
         * No metrics to display
         */
        data class Empty(
            val reason: String = "No data for this month"
        ) : MetricsState()

        /**
         * Error loading metrics
         *
         * @param message Error message to display
         * @param canRetry Whether retry is possible
         */
        data class Error(
            val message: String,
            val canRetry: Boolean = true
        ) : MetricsState()
    }

    // ========================================
    // Monthly Metrics State (Weekly Breakdown)
    // ========================================

    /**
     * Monthly metrics view state with weekly breakdown
     *
     * Represents state for detailed monthly view.
     */
    sealed class MonthlyState {
        /**
         * Loading monthly metrics
         */
        object Loading : MonthlyState()

        /**
         * Monthly metrics loaded with weekly breakdown
         *
         * @param metrics Monthly metrics with weekly data
         */
        data class Success(
            val metrics: MonthlyMetrics
        ) : MonthlyState() {
            /**
             * Get total revenue formatted
             */
            fun getFormattedTotal(): String = metrics.getFormattedTotal()

            /**
             * Get week count
             */
            fun getWeekCount(): Int = metrics.weekCount

            /**
             * Get weekly distribution string
             */
            fun getWeeklyDistribution(): String = metrics.getWeeklyDistribution()

            /**
             * Get consistency assessment
             */
            fun getConsistency(): String = metrics.getConsistencyAssessment()

            /**
             * Check if data valid
             */
            fun isValid(): Boolean = metrics.isValid()
        }

        /**
         * No monthly data
         */
        data class Empty(
            val reason: String = "No data for this month"
        ) : MonthlyState()

        /**
         * Error loading monthly metrics
         *
         * @param message Error message
         * @param canRetry Whether retry is possible
         */
        data class Error(
            val message: String,
            val canRetry: Boolean = true
        ) : MonthlyState()
    }

    // ========================================
    // Month Selection State
    // ========================================

    /**
     * Selected month state
     *
     * Represents current month selection.
     *
     * @property selectedMonth Currently selected month
     * @property previousMonth Previous month
     * @property nextMonth Next month
     */
    data class SelectedMonthState(
        val selectedMonth: YearMonth,
        val previousMonth: YearMonth = selectedMonth.minusMonths(1),
        val nextMonth: YearMonth = selectedMonth.plusMonths(1)
    ) {
        /**
         * Check if can go to previous month
         *
         * Prevents going back too far (e.g., before app creation date).
         *
         * @return true if previous month available
         */
        fun canGoToPrevious(): Boolean {
            // Allow going back to any month (or add min date constraint)
            return true
        }

        /**
         * Check if can go to next month
         *
         * Prevents going to future months.
         *
         * @return true if next month is not in future
         */
        fun canGoToNext(): Boolean {
            return nextMonth <= YearMonth.now()
        }

        /**
         * Get current month name
         */
        fun getCurrentMonthName(): String {
            val monthName = when (selectedMonth.monthValue) {
                1 -> "Janeiro"
                2 -> "Fevereiro"
                3 -> "Março"
                4 -> "Abril"
                5 -> "Maio"
                6 -> "Junho"
                7 -> "Julho"
                8 -> "Agosto"
                9 -> "Setembro"
                10 -> "Outubro"
                11 -> "Novembro"
                12 -> "Dezembro"
                else -> "Desconhecido"
            }
            return "$monthName de ${selectedMonth.year}"
        }

        /**
         * Is this month the current month?
         */
        fun isCurrentMonth(): Boolean = selectedMonth == YearMonth.now()
    }

    // ========================================
    // Trend State
    // ========================================

    /**
     * Trend analysis state
     *
     * Represents comparison between months.
     */
    sealed class TrendState {
        /**
         * Loading trend data
         */
        object Loading : TrendState()

        /**
         * Trend data loaded
         *
         * @param currentMonth Current month metrics
         * @param previousMonth Previous month metrics
         * @param revenueTrend Revenue change percentage
         * @param patientTrend Patient count change
         */
        data class Success(
            val currentMonth: DashboardMetrics,
            val previousMonth: DashboardMetrics,
            val revenueTrend: Int,
            val patientTrend: Int
        ) : TrendState() {
            /**
             * Check if revenue increased
             */
            fun isRevenueUp(): Boolean = revenueTrend > 0

            /**
             * Check if patients increased
             */
            fun isPatientsUp(): Boolean = patientTrend > 0

            /**
             * Get revenue trend description
             */
            fun getRevenueTrendText(): String {
                return when {
                    revenueTrend > 0 -> "↑ $revenueTrend% crescimento"
                    revenueTrend < 0 -> "↓ ${revenueTrend * -1}% queda"
                    else -> "→ Estável"
                }
            }

            /**
             * Get patient trend description
             */
            fun getPatientTrendText(): String {
                return when {
                    patientTrend > 0 -> "↑ +$patientTrend pacientes"
                    patientTrend < 0 -> "↓ $patientTrend pacientes"
                    else -> "→ Estável"
                }
            }
        }

        /**
         * No trend data (not enough months)
         */
        data class NoData(
            val reason: String = "Need data from previous month"
        ) : TrendState()

        /**
         * Error loading trend data
         */
        data class Error(
            val message: String
        ) : TrendState()
    }

    // ========================================
    // Overall Dashboard State
    // ========================================

    /**
     * Complete dashboard UI state
     *
     * Combines all sub-states into one view state.
     *
     * @property metricsState Current metrics display state
     * @property monthlyState Monthly breakdown state
     * @property trendState Trend analysis state
     * @property selectedMonth Selected month info
     * @property isLoading Overall loading state
     * @property error Overall error message
     */
    data class DashboardState(
        val metricsState: MetricsState = MetricsState.Loading,
        val monthlyState: MonthlyState = MonthlyState.Loading,
        val trendState: TrendState = TrendState.Loading,
        val selectedMonth: SelectedMonthState = SelectedMonthState(YearMonth.now()),
        val isLoading: Boolean = true,
        val error: String? = null
    ) {
        /**
         * Check if all data loaded successfully
         */
        fun isFullyLoaded(): Boolean {
            return metricsState is MetricsState.Success &&
                    monthlyState is MonthlyState.Success &&
                    !isLoading
        }

        /**
         * Check if has any error
         */
        fun hasError(): Boolean = error != null

        /**
         * Get all errors as list
         */
        fun getAllErrors(): List<String> {
            return listOfNotNull(
                error,
                if (metricsState is MetricsState.Error) metricsState.message else null,
                if (monthlyState is MonthlyState.Error) monthlyState.message else null,
                if (trendState is TrendState.Error) trendState.message else null
            )
        }

        /**
         * Check if can retry
         */
        fun canRetry(): Boolean {
            return (metricsState is MetricsState.Error && metricsState.canRetry) ||
                    (monthlyState is MonthlyState.Error && monthlyState.canRetry)
        }

        /**
         * Get summary of current state
         */
        fun getSummary(): String {
            return when {
                isLoading -> "Carregando..."
                hasError() -> "Erro: ${error ?: "Desconhecido"}"
                metricsState is MetricsState.Success -> {
                    val metrics = metricsState.metrics
                    "Receita: ${metrics.getFormattedRevenue()} | " +
                            "Pacientes: ${metrics.activePatients} | " +
                            "Pendente: ${metrics.getFormattedOutstanding()}"
                }
                else -> "Sem dados"
            }
        }
    }

    // ========================================
    // Helper State Objects
    // ========================================

    /**
     * Loading state singleton
     */
    val DEFAULT_LOADING = MetricsState.Loading

    /**
     * Default empty state
     */
    fun defaultEmpty(reason: String = "No data for this month"): MetricsState.Empty {
        return MetricsState.Empty(reason)
    }

    /**
     * Default error state
     */
    fun defaultError(message: String, canRetry: Boolean = true): MetricsState.Error {
        return MetricsState.Error(message, canRetry)
    }

    /**
     * Create initial dashboard state
     */
    fun initialState(): DashboardState {
        return DashboardState(
            metricsState = MetricsState.Loading,
            monthlyState = MonthlyState.Loading,
            trendState = TrendState.Loading,
            selectedMonth = SelectedMonthState(YearMonth.now()),
            isLoading = true,
            error = null
        )
    }
}
