package com.psychologist.financial.domain.usecases

import android.util.Log
import com.psychologist.financial.data.repositories.DashboardRepository
import com.psychologist.financial.domain.models.DashboardMetrics
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import java.time.YearMonth

/**
 * Use case: Get dashboard metrics for a month
 *
 * Responsibilities:
 * - Retrieve aggregated dashboard metrics
 * - Support month-based filtering
 * - Provide both synchronous and reactive APIs
 * - Handle errors gracefully
 * - Support trend analysis (multiple months)
 *
 * Architecture:
 * - Orchestrates repository calls for aggregations
 * - Provides high-level business operations
 * - ViewModels call this use case for metrics
 * - Handles reactive streams automatically
 *
 * Metrics Provided:
 * - Total Revenue: Sum of PAID payments
 * - Active Patients: Count of ACTIVE patients
 * - Average Fee: Average payment amount
 * - Outstanding Balance: Sum of PENDING payments
 * - Total Transactions: Count of transactions
 *
 * Usage:
 * ```kotlin
 * val useCase = GetDashboardMetricsUseCase(dashboardRepository)
 *
 * // Get current month metrics
 * val metrics = useCase.execute(YearMonth.now())
 * println("Revenue: ${metrics.getFormattedRevenue()}")
 *
 * // Get reactive stream for current month
 * useCase.getMetricsFlow(YearMonth.now()).collect { metrics ->
 *     updateDashboard(metrics)
 * }
 *
 * // Get trend analysis
 * val lastThreeMonths = useCase.getLastMonthsMetrics(3)
 * ```
 *
 * Error Handling:
 * - Returns empty metrics on error (non-blocking)
 * - Logs errors for debugging
 * - Reactive flows handle errors gracefully
 * - Caller decides how to handle missing data
 *
 * @property dashboardRepository DashboardRepository for aggregations
 */
class GetDashboardMetricsUseCase(
    private val dashboardRepository: DashboardRepository
) {
    private companion object {
        private const val TAG = "GetDashboardMetricsUseCase"
    }

    /**
     * Get dashboard metrics for a specific month
     *
     * Aggregates financial data for the month.
     *
     * @param yearMonth Month to get metrics for (default: current month)
     * @return DashboardMetrics for the month
     *
     * Example:
     * ```kotlin
     * val metrics = useCase.execute(YearMonth.of(2024, 3))
     * println("Revenue: ${metrics.getFormattedRevenue()}")
     * println("Patients: ${metrics.activePatients}")
     * ```
     */
    suspend fun execute(yearMonth: YearMonth = YearMonth.now()): DashboardMetrics {
        return try {
            Log.d(TAG, "Getting metrics for $yearMonth")
            val metrics = dashboardRepository.getMetricsForMonth(yearMonth)

            if (!metrics.isValid()) {
                Log.w(TAG, "Metrics validation failed for $yearMonth")
                return DashboardMetrics.empty(yearMonth)
            }

            Log.d(TAG, "Metrics retrieved successfully: revenue=${metrics.getFormattedRevenue()}, " +
                    "patients=${metrics.activePatients}")
            metrics
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get metrics for $yearMonth", e)
            DashboardMetrics.empty(yearMonth)
        }
    }

    /**
     * Get dashboard metrics for current month
     *
     * Convenience method for current month.
     *
     * @return DashboardMetrics for current month
     *
     * Example:
     * ```kotlin
     * val metrics = useCase.getCurrentMonth()
     * ```
     */
    suspend fun getCurrentMonth(): DashboardMetrics {
        return execute(YearMonth.now())
    }

    /**
     * Get dashboard metrics for previous month
     *
     * Useful for month-over-month comparison.
     *
     * @return DashboardMetrics for previous month
     *
     * Example:
     * ```kotlin
     * val current = useCase.getCurrentMonth()
     * val previous = useCase.getPreviousMonth()
     * val change = current.getRevenueChangePercentage(previous.totalRevenue)
     * ```
     */
    suspend fun getPreviousMonth(): DashboardMetrics {
        return execute(YearMonth.now().minusMonths(1))
    }

    /**
     * Get dashboard metrics for last N months
     *
     * Returns metrics for N months including current month.
     * Useful for trend analysis and charts.
     *
     * @param months Number of months (must be >= 1, default 3)
     * @return List of DashboardMetrics sorted from oldest to newest
     *
     * Example:
     * ```kotlin
     * val lastThree = useCase.getLastMonthsMetrics(3)
     * for (metrics in lastThree) {
     *     println("${metrics.getFormattedMonth()}: ${metrics.getFormattedRevenue()}")
     * }
     * ```
     */
    suspend fun getLastMonthsMetrics(months: Int = 3): List<DashboardMetrics> {
        require(months >= 1) { "months must be >= 1" }

        return try {
            Log.d(TAG, "Getting metrics for last $months months")
            val metrics = dashboardRepository.getMetricsForLastMonths(months)

            if (metrics.isEmpty()) {
                Log.w(TAG, "No metrics found for last $months months")
                return emptyList()
            }

            Log.d(TAG, "Retrieved $months months of metrics")
            metrics
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get metrics for last $months months", e)
            emptyList()
        }
    }

    /**
     * Get dashboard metrics for date range
     *
     * @param startMonth Start month (inclusive)
     * @param endMonth End month (inclusive)
     * @return List of DashboardMetrics
     *
     * Example:
     * ```kotlin
     * val jan2024 = YearMonth.of(2024, 1)
     * val mar2024 = YearMonth.of(2024, 3)
     * val metrics = useCase.getMetricsForRange(jan2024, mar2024)
     * ```
     */
    suspend fun getMetricsForRange(
        startMonth: YearMonth,
        endMonth: YearMonth
    ): List<DashboardMetrics> {
        require(startMonth <= endMonth) { "startMonth must be <= endMonth" }

        return try {
            Log.d(TAG, "Getting metrics from $startMonth to $endMonth")
            val metrics = dashboardRepository.getMetricsForDateRange(startMonth, endMonth)
            Log.d(TAG, "Retrieved ${metrics.size} months of metrics")
            metrics
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get metrics for range $startMonth-$endMonth", e)
            emptyList()
        }
    }

    // ========================================
    // Reactive Streams
    // ========================================

    /**
     * Get dashboard metrics as Flow (reactive)
     *
     * Updates automatically when underlying data changes.
     * Useful for real-time dashboard updates.
     *
     * @param yearMonth Month to get metrics for
     * @return Flow of DashboardMetrics
     *
     * Example:
     * ```kotlin
     * useCase.getMetricsFlow(YearMonth.now())
     *     .collect { metrics ->
     *         println("Dashboard updated: ${metrics.getFormattedRevenue()}")
     *     }
     * ```
     */
    fun getMetricsFlow(yearMonth: YearMonth = YearMonth.now()): Flow<DashboardMetrics> {
        return dashboardRepository.getMetricsForMonthFlow(yearMonth)
            .catch { e ->
                Log.e(TAG, "Error in metrics flow for $yearMonth", e)
                emit(DashboardMetrics.empty(yearMonth))
            }
    }

    /**
     * Get dashboard metrics for current month as Flow (reactive)
     *
     * Convenience method for current month with automatic updates.
     *
     * @return Flow of DashboardMetrics
     *
     * Example:
     * ```kotlin
     * useCase.getCurrentMonthFlow()
     *     .collect { metrics ->
     *         viewModel.updateMetrics(metrics)
     *     }
     * ```
     */
    fun getCurrentMonthFlow(): Flow<DashboardMetrics> {
        return getMetricsFlow(YearMonth.now())
    }

    // ========================================
    // Individual Metrics
    // ========================================

    /**
     * Get total revenue for month
     *
     * @param yearMonth Month to get revenue for
     * @return Total revenue amount
     *
     * Example:
     * ```kotlin
     * val revenue = useCase.getTotalRevenue(YearMonth.now())
     * println("This month: ${revenue.formatAsCurrency()}")
     * ```
     */
    suspend fun getTotalRevenue(yearMonth: YearMonth = YearMonth.now()): java.math.BigDecimal {
        return try {
            Log.d(TAG, "Getting revenue for $yearMonth")
            dashboardRepository.getTotalRevenueForMonth(yearMonth)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get revenue for $yearMonth", e)
            java.math.BigDecimal.ZERO
        }
    }

    /**
     * Get outstanding balance
     *
     * @return Outstanding balance (not month-specific)
     *
     * Example:
     * ```kotlin
     * val outstanding = useCase.getOutstandingBalance()
     * ```
     */
    suspend fun getOutstandingBalance(): java.math.BigDecimal {
        return try {
            Log.d(TAG, "Getting outstanding balance")
            dashboardRepository.getOutstandingBalance()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get outstanding balance", e)
            java.math.BigDecimal.ZERO
        }
    }

    /**
     * Get active patient count
     *
     * @return Number of active patients
     *
     * Example:
     * ```kotlin
     * val patientCount = useCase.getActivePatientCount()
     * println("Active patients: $patientCount")
     * ```
     */
    suspend fun getActivePatientCount(): Int {
        return try {
            Log.d(TAG, "Getting active patient count")
            dashboardRepository.getActivePatientCount()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get active patient count", e)
            0
        }
    }

    /**
     * Get average fee for month
     *
     * @param yearMonth Month to get average for
     * @return Average payment amount
     *
     * Example:
     * ```kotlin
     * val avgFee = useCase.getAverageFee(YearMonth.now())
     * ```
     */
    suspend fun getAverageFee(yearMonth: YearMonth = YearMonth.now()): java.math.BigDecimal {
        return try {
            Log.d(TAG, "Getting average fee for $yearMonth")
            dashboardRepository.getAverageFeeForMonth(yearMonth)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get average fee for $yearMonth", e)
            java.math.BigDecimal.ZERO
        }
    }

    /**
     * Get collection rate for month
     *
     * @param yearMonth Month to get rate for
     * @return Collection percentage (0-100)
     *
     * Example:
     * ```kotlin
     * val rate = useCase.getCollectionRate(YearMonth.now())
     * println("Collection: $rate%")
     * ```
     */
    suspend fun getCollectionRate(yearMonth: YearMonth = YearMonth.now()): Int {
        return try {
            Log.d(TAG, "Getting collection rate for $yearMonth")
            dashboardRepository.getCollectionRateForMonth(yearMonth)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get collection rate for $yearMonth", e)
            0
        }
    }

    // ========================================
    // Trend Analysis
    // ========================================

    /**
     * Get revenue trend vs previous month
     *
     * Calculates percentage change from previous month.
     *
     * @param yearMonth Month to analyze
     * @return Percentage change (-100 to +100 or more)
     *
     * Example:
     * ```kotlin
     * val trend = useCase.getRevenueTrend(YearMonth.now())
     * when {
     *     trend > 0 -> println("↑ Growth: $trend%")
     *     trend < 0 -> println("↓ Decline: $trend%")
     *     else -> println("→ Stable")
     * }
     * ```
     */
    suspend fun getRevenueTrend(yearMonth: YearMonth = YearMonth.now()): Int {
        return try {
            val current = execute(yearMonth)
            val previous = execute(yearMonth.minusMonths(1))
            current.getRevenueChangePercentage(previous.totalRevenue)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to calculate revenue trend for $yearMonth", e)
            0
        }
    }

    /**
     * Compare metrics for two months
     *
     * Useful for month-over-month analysis.
     *
     * @param currentMonth Current month
     * @param previousMonth Month to compare against
     * @return ComparisonResult with differences
     *
     * Example:
     * ```kotlin
     * val current = YearMonth.now()
     * val previous = current.minusMonths(1)
     * val comparison = useCase.compareMonths(current, previous)
     * println("Revenue change: ${comparison.revenueTrend}")
     * ```
     */
    suspend fun compareMonths(
        currentMonth: YearMonth,
        previousMonth: YearMonth
    ): MonthComparisonResult {
        return try {
            val current = execute(currentMonth)
            val previous = execute(previousMonth)

            MonthComparisonResult(
                currentMonth = current,
                previousMonth = previous,
                revenueTrend = current.getRevenueChangePercentage(previous.totalRevenue),
                patientTrend = current.activePatients - previous.activePatients
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to compare months $currentMonth and $previousMonth", e)
            MonthComparisonResult(
                currentMonth = DashboardMetrics.empty(currentMonth),
                previousMonth = DashboardMetrics.empty(previousMonth),
                revenueTrend = 0,
                patientTrend = 0
            )
        }
    }
}

/**
 * Result of month-to-month comparison
 *
 * @property currentMonth Current month metrics
 * @property previousMonth Previous month metrics
 * @property revenueTrend Revenue change percentage
 * @property patientTrend Change in active patient count
 */
data class MonthComparisonResult(
    val currentMonth: DashboardMetrics,
    val previousMonth: DashboardMetrics,
    val revenueTrend: Int,
    val patientTrend: Int
) {
    /**
     * Check if revenue increased
     *
     * @return true if revenue grew
     */
    val isRevenueUp: Boolean
        get() = revenueTrend > 0

    /**
     * Check if patients increased
     *
     * @return true if patient count grew
     */
    val isPatientCountUp: Boolean
        get() = patientTrend > 0

    /**
     * Get trend description (Portuguese)
     *
     * @return Description of trends
     */
    fun getTrendDescription(): String {
        val revenueTrendText = when {
            revenueTrend > 0 -> "Receita ↑ $revenueTrend%"
            revenueTrend < 0 -> "Receita ↓ ${revenueTrend * -1}%"
            else -> "Receita estável"
        }

        val patientTrendText = when {
            patientTrend > 0 -> "Pacientes ↑ $patientTrend"
            patientTrend < 0 -> "Pacientes ↓ ${patientTrend * -1}"
            else -> "Pacientes estável"
        }

        return "$revenueTrendText | $patientTrendText"
    }
}
