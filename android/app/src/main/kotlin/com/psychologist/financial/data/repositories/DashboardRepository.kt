package com.psychologist.financial.data.repositories

import com.psychologist.financial.data.database.AppDatabase
import com.psychologist.financial.data.database.PatientDao
import com.psychologist.financial.data.database.PaymentDao
import com.psychologist.financial.domain.models.DashboardMetrics
import com.psychologist.financial.domain.models.PatientStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.math.BigDecimal
import java.time.YearMonth

/**
 * Dashboard repository
 *
 * Data access layer for dashboard aggregation queries.
 * Provides aggregated financial metrics for dashboard display.
 *
 * Architecture:
 * - Extends BaseRepository for transaction management
 * - Uses PaymentDao and PatientDao for database operations
 * - Aggregates data from multiple sources
 * - Provides reactive (Flow) APIs for real-time updates
 * - Calculates complex metrics from raw data
 *
 * Responsibilities:
 * - Aggregate revenue data (sum of paid payments)
 * - Count active patients
 * - Calculate average fees
 * - Calculate outstanding balance
 * - Support month-based filtering
 * - Provide reactive streams for dashboard updates
 *
 * Reactive Streams:
 * - All Flow<> methods return cold flows
 * - Automatically update when underlying data changes
 * - Multiple metrics combined using combine()
 *
 * Metrics Calculations:
 * - Total Revenue: SUM(amount) WHERE status = 'PAID' AND payment_date IN month
 * - Active Patients: COUNT(*) WHERE status = 'ACTIVE'
 * - Average Fee: AVG(amount) WHERE status = 'PAID' AND payment_date IN month
 * - Outstanding Balance: SUM(amount) WHERE status = 'PENDING'
 * - Total Transactions: COUNT(*) WHERE payment_date IN month
 *
 * Usage Example:
 * ```kotlin
 * // Get current month metrics
 * val metrics = dashboardRepository.getMetricsForMonth(YearMonth.now())
 *
 * // Get reactive metrics stream
 * dashboardRepository.getMetricsForMonthFlow(YearMonth.now()).collect { metrics ->
 *     updateDashboard(metrics)
 * }
 *
 * // Get multiple months for trend analysis
 * val lastThreeMonths = dashboardRepository.getMetricsForLastMonths(3)
 * ```
 */
class DashboardRepository(
    database: AppDatabase,
    private val paymentDao: PaymentDao,
    private val patientDao: PatientDao
) : BaseRepository(database) {

    // ========================================
    // Metrics by Month - Synchronous
    // ========================================

    /**
     * Get dashboard metrics for a specific month
     *
     * Aggregates all financial data for the month.
     *
     * @param yearMonth Month to calculate metrics for
     * @return DashboardMetrics for the month
     */
    suspend fun getMetricsForMonth(yearMonth: YearMonth): DashboardMetrics {
        val startDate = yearMonth.atDay(1)
        val endDate = yearMonth.atEndOfMonth()

        // Get revenue (sum of all payments — all payments are PAID)
        val totalRevenue = paymentDao.getSumByDateRange(startDate, endDate)

        // Get active patient count
        val activePatients = patientDao.countByStatus(PatientStatus.ACTIVE.name)

        // Get average fee (average of all payments in month)
        val averageFee = paymentDao.getAverageByDateRange(startDate, endDate)

        // No pending payments in new model — outstanding is always zero
        val outstandingBalance = BigDecimal.ZERO

        // Get transaction count for month
        val totalTransactions = paymentDao.countByDateRange(startDate, endDate)

        return DashboardMetrics(
            yearMonth = yearMonth,
            totalRevenue = totalRevenue,
            activePatients = activePatients,
            averageFee = averageFee,
            outstandingBalance = outstandingBalance,
            totalTransactions = totalTransactions
        )
    }

    /**
     * Get dashboard metrics for current month
     *
     * Convenience method for current month.
     *
     * @return DashboardMetrics for current month
     */
    suspend fun getMetricsForCurrentMonth(): DashboardMetrics {
        return getMetricsForMonth(YearMonth.now())
    }

    /**
     * Get dashboard metrics for previous month
     *
     * Useful for month-over-month comparison.
     *
     * @return DashboardMetrics for previous month
     */
    suspend fun getMetricsForPreviousMonth(): DashboardMetrics {
        return getMetricsForMonth(YearMonth.now().minusMonths(1))
    }

    /**
     * Get dashboard metrics for last N months
     *
     * Returns metrics for N months including current month.
     * Sorted from oldest to newest.
     *
     * @param months Number of months (must be >= 1)
     * @return List of DashboardMetrics
     */
    suspend fun getMetricsForLastMonths(months: Int): List<DashboardMetrics> {
        require(months >= 1) { "months must be >= 1" }

        val metrics = mutableListOf<DashboardMetrics>()
        val today = YearMonth.now()

        for (i in (months - 1) downTo 0) {
            val yearMonth = today.minusMonths(i.toLong())
            metrics.add(getMetricsForMonth(yearMonth))
        }

        return metrics
    }

    /**
     * Get dashboard metrics for date range
     *
     * @param startMonth Start month (inclusive)
     * @param endMonth End month (inclusive)
     * @return List of DashboardMetrics
     */
    suspend fun getMetricsForDateRange(
        startMonth: YearMonth,
        endMonth: YearMonth
    ): List<DashboardMetrics> {
        require(startMonth <= endMonth) { "startMonth must be <= endMonth" }

        val metrics = mutableListOf<DashboardMetrics>()
        var current = startMonth

        while (current <= endMonth) {
            metrics.add(getMetricsForMonth(current))
            current = current.plusMonths(1)
        }

        return metrics
    }

    // ========================================
    // Metrics by Month - Reactive
    // ========================================

    /**
     * Get dashboard metrics for month as Flow (reactive)
     *
     * Updates automatically when underlying data changes.
     *
     * @param yearMonth Month to calculate metrics for
     * @return Flow of DashboardMetrics
     */
    fun getMetricsForMonthFlow(yearMonth: YearMonth): Flow<DashboardMetrics> {
        val startDate = yearMonth.atDay(1)
        val endDate = yearMonth.atEndOfMonth()

        // Combine multiple flows for automatic updates
        return combine(
            paymentDao.getSumByDateRangeFlow(startDate, endDate),
            patientDao.countByStatusFlow(PatientStatus.ACTIVE.name),
            paymentDao.getAverageByDateRangeFlow(startDate, endDate),
            paymentDao.countByDateRangeFlow(startDate, endDate)
        ) { revenue, patients, avgFee, transactions ->
            DashboardMetrics(
                yearMonth = yearMonth,
                totalRevenue = revenue,
                activePatients = patients,
                averageFee = avgFee,
                outstandingBalance = BigDecimal.ZERO,
                totalTransactions = transactions
            )
        }
    }

    /**
     * Get dashboard metrics for current month as Flow (reactive)
     *
     * Convenience method for current month with automatic updates.
     *
     * @return Flow of DashboardMetrics
     */
    fun getMetricsForCurrentMonthFlow(): Flow<DashboardMetrics> {
        return getMetricsForMonthFlow(YearMonth.now())
    }

    // ========================================
    // Aggregation Queries - Revenue
    // ========================================

    /**
     * Get total revenue for month
     *
     * Sum of all PAID payments for the month.
     *
     * @param yearMonth Month to calculate for
     * @return Total revenue amount
     */
    suspend fun getTotalRevenueForMonth(yearMonth: YearMonth): BigDecimal {
        val startDate = yearMonth.atDay(1)
        val endDate = yearMonth.atEndOfMonth()

        return paymentDao.getSumByDateRange(startDate, endDate)
    }

    /**
     * Get revenue as Flow (reactive)
     *
     * @param yearMonth Month to calculate for
     * @return Flow of revenue amount
     */
    fun getTotalRevenueForMonthFlow(yearMonth: YearMonth): Flow<BigDecimal> {
        val startDate = yearMonth.atDay(1)
        val endDate = yearMonth.atEndOfMonth()

        return paymentDao.getSumByDateRangeFlow(startDate, endDate)
    }

    /**
     * Get total revenue across all months
     *
     * Sum of all payments (all are PAID).
     *
     * @return Total revenue
     */
    suspend fun getTotalRevenueAllTime(): BigDecimal {
        return paymentDao.getSum()
    }

    // ========================================
    // Aggregation Queries - Outstanding
    // ========================================

    /**
     * Get outstanding balance
     *
     * Always zero — no pending payments in new model.
     *
     * @return BigDecimal.ZERO
     */
    suspend fun getOutstandingBalance(): BigDecimal {
        return BigDecimal.ZERO
    }

    /**
     * Get outstanding balance as Flow (reactive)
     *
     * Always emits zero — no pending payments in new model.
     *
     * @return Flow of outstanding amount (always ZERO)
     */
    fun getOutstandingBalanceFlow(): Flow<BigDecimal> {
        return paymentDao.getSumFlow().let { flow ->
            kotlinx.coroutines.flow.flow { emit(BigDecimal.ZERO) }
        }
    }

    /**
     * Get outstanding balance for month
     *
     * Always zero — no pending payments in new model.
     *
     * @param yearMonth Month to calculate for
     * @return BigDecimal.ZERO
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun getOutstandingForMonth(yearMonth: YearMonth): BigDecimal {
        return BigDecimal.ZERO
    }

    // ========================================
    // Aggregation Queries - Patients
    // ========================================

    /**
     * Get active patient count
     *
     * @return Number of active patients
     */
    suspend fun getActivePatientCount(): Int {
        return patientDao.countByStatus(PatientStatus.ACTIVE.name)
    }

    /**
     * Get active patient count as Flow (reactive)
     *
     * Updates when patient status changes.
     *
     * @return Flow of active patient count
     */
    fun getActivePatientCountFlow(): Flow<Int> {
        return patientDao.countByStatusFlow(PatientStatus.ACTIVE.name)
    }

    /**
     * Get inactive patient count
     *
     * @return Number of inactive patients
     */
    suspend fun getInactivePatientCount(): Int {
        return patientDao.countByStatus(PatientStatus.INACTIVE.name)
    }

    /**
     * Get total patient count
     *
     * @return Total number of patients
     */
    suspend fun getTotalPatientCount(): Int {
        return patientDao.countAllPatients()
    }

    // ========================================
    // Aggregation Queries - Average
    // ========================================

    /**
     * Get average fee for month
     *
     * Average of all PAID payments in month.
     *
     * @param yearMonth Month to calculate for
     * @return Average amount or zero if no paid payments
     */
    suspend fun getAverageFeeForMonth(yearMonth: YearMonth): BigDecimal {
        val startDate = yearMonth.atDay(1)
        val endDate = yearMonth.atEndOfMonth()

        return paymentDao.getAverageByDateRange(startDate, endDate)
    }

    /**
     * Get average fee for month as Flow (reactive)
     *
     * @param yearMonth Month to calculate for
     * @return Flow of average amount
     */
    fun getAverageFeeForMonthFlow(yearMonth: YearMonth): Flow<BigDecimal> {
        val startDate = yearMonth.atDay(1)
        val endDate = yearMonth.atEndOfMonth()

        return paymentDao.getAverageByDateRangeFlow(startDate, endDate)
    }

    /**
     * Get overall average fee
     *
     * Average of all payments.
     *
     * @return Average amount
     */
    suspend fun getAverageFeeAllTime(): BigDecimal {
        return paymentDao.getAverage()
    }

    // ========================================
    // Aggregation Queries - Transactions
    // ========================================

    /**
     * Get transaction count for month
     *
     * Count of all payments (PAID + PENDING) in month.
     *
     * @param yearMonth Month to calculate for
     * @return Transaction count
     */
    suspend fun getTransactionCountForMonth(yearMonth: YearMonth): Int {
        val startDate = yearMonth.atDay(1)
        val endDate = yearMonth.atEndOfMonth()

        return paymentDao.countByDateRange(startDate, endDate)
    }

    /**
     * Get total transaction count
     *
     * Count of all payments ever recorded.
     *
     * @return Transaction count
     */
    suspend fun getTotalTransactionCount(): Int {
        return paymentDao.count()
    }

    /**
     * Get paid transaction count for month
     *
     * All payments are PAID — returns count of all payments in month.
     *
     * @param yearMonth Month to calculate for
     * @return Transaction count
     */
    suspend fun getPaidTransactionCountForMonth(yearMonth: YearMonth): Int {
        val startDate = yearMonth.atDay(1)
        val endDate = yearMonth.atEndOfMonth()
        return paymentDao.countByDateRange(startDate, endDate)
    }

    /**
     * Get pending transaction count for month
     *
     * Always zero — no pending payments in new model.
     *
     * @param yearMonth Month to calculate for
     * @return 0
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun getPendingTransactionCountForMonth(yearMonth: YearMonth): Int {
        return 0
    }

    // ========================================
    // Collection Rate
    // ========================================

    /**
     * Get collection rate for month
     *
     * Percentage of revenue vs total billed.
     *
     * @param yearMonth Month to calculate for
     * @return Collection percentage (0-100)
     */
    suspend fun getCollectionRateForMonth(yearMonth: YearMonth): Int {
        val revenue = getTotalRevenueForMonth(yearMonth)
        val outstanding = getOutstandingForMonth(yearMonth)
        val totalBilled = revenue + outstanding

        return if (totalBilled > BigDecimal.ZERO) {
            ((revenue * BigDecimal("100")) / totalBilled).toInt()
        } else {
            0
        }
    }

    /**
     * Get overall collection rate
     *
     * @return Collection percentage (0-100)
     */
    suspend fun getOverallCollectionRate(): Int {
        val revenue = getTotalRevenueAllTime()
        val outstanding = getOutstandingBalance()
        val totalBilled = revenue + outstanding

        return if (totalBilled > BigDecimal.ZERO) {
            ((revenue * BigDecimal("100")) / totalBilled).toInt()
        } else {
            0
        }
    }
}
