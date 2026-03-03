package com.psychologist.financial.services

import com.psychologist.financial.domain.models.DashboardMetrics
import com.psychologist.financial.domain.models.MonthlyMetrics
import com.psychologist.financial.domain.models.Payment
import com.psychologist.financial.domain.models.WeeklyMetrics
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * Metrics aggregator service
 *
 * Calculates aggregated financial metrics from raw payment and patient data.
 * Provides computation of revenue, fees, and breakdown by time period.
 *
 * Responsibilities:
 * - Aggregate revenue from payments
 * - Calculate average fees
 * - Count patients and transactions
 * - Generate weekly breakdowns
 * - Calculate metrics for time periods
 * - Generate monthly summaries
 *
 * Architecture:
 * - Processes data in-memory (suitable for <10k records)
 * - Pure functions for calculations
 * - No database dependencies
 * - Testable with sample data
 *
 * Calculation Rules:
 * - Revenue: Sum of PAID payments only
 * - Average Fee: AVG(amount) WHERE status = PAID
 * - Outstanding: Sum of PENDING payments
 * - Transactions: Count of all payments
 * - Week: 7-day period starting day 1, 8, 15, 22, 29 of month
 *
 * Usage:
 * ```kotlin
 * val aggregator = MetricsAggregator()
 *
 * // Calculate metrics for a month
 * val metrics = aggregator.calculateMonthlyMetrics(
 *     yearMonth = YearMonth.of(2024, 3),
 *     payments = allPayments,
 *     activePatients = patientCount
 * )
 * println("Revenue: ${metrics.getFormattedTotal()}")
 *
 * // Calculate with weekly breakdown
 * val detailed = aggregator.calculateMonthlyMetricsWithWeekly(
 *     yearMonth = YearMonth.of(2024, 3),
 *     payments = allPayments,
 *     activePatients = patientCount
 * )
 * println("Distribution: ${detailed.getWeeklyDistribution()}")
 *
 * // Calculate average fee
 * val avgFee = aggregator.calculateAverageFee(payments)
 * ```
 *
 * Performance Considerations:
 * - O(n) complexity for all calculations
 * - Single pass through payment list
 * - Suitable for real-time dashboard updates
 * - Cache results for multiple accesses
 */
class MetricsAggregator {

    // ========================================
    // Monthly Metrics Calculation
    // ========================================

    /**
     * Calculate dashboard metrics for a month
     *
     * Aggregates revenue, patients, fees, outstanding from payment data.
     *
     * @param yearMonth Month to calculate metrics for
     * @param payments All payments (any month)
     * @param activePatients Count of active patients
     * @return DashboardMetrics for the month
     *
     * Example:
     * ```kotlin
     * val metrics = aggregator.calculateMonthlyMetrics(
     *     yearMonth = YearMonth.of(2024, 3),
     *     payments = allPayments,
     *     activePatients = 12
     * )
     * ```
     */
    fun calculateMonthlyMetrics(
        yearMonth: YearMonth,
        payments: List<Payment>,
        activePatients: Int
    ): DashboardMetrics {
        // Filter payments for month
        val monthPayments = filterPaymentsByMonth(payments, yearMonth)

        // Calculate revenue (paid only)
        val revenue = calculateRevenue(monthPayments)

        // Calculate average fee (paid only)
        val avgFee = calculateAverageFee(monthPayments)

        // Calculate outstanding (all pending, not month-specific)
        val outstanding = calculateOutstanding(payments)

        // Count transactions in month
        val transactionCount = monthPayments.size

        return DashboardMetrics(
            yearMonth = yearMonth,
            totalRevenue = revenue,
            activePatients = activePatients,
            averageFee = avgFee,
            outstandingBalance = outstanding,
            totalTransactions = transactionCount
        )
    }

    /**
     * Calculate monthly metrics with weekly breakdown
     *
     * Provides detailed metrics including revenue per week.
     *
     * @param yearMonth Month to calculate metrics for
     * @param payments All payments (any month)
     * @param activePatients Count of active patients
     * @return MonthlyMetrics with weekly breakdown
     *
     * Example:
     * ```kotlin
     * val monthly = aggregator.calculateMonthlyMetricsWithWeekly(
     *     yearMonth = YearMonth.of(2024, 3),
     *     payments = allPayments,
     *     activePatients = 12
     * )
     * println("Week 1: ${monthly.weeklyBreakdown[1]?.getFormattedRevenue()}")
     * ```
     */
    fun calculateMonthlyMetricsWithWeekly(
        yearMonth: YearMonth,
        payments: List<Payment>,
        activePatients: Int
    ): MonthlyMetrics {
        // Filter payments for month
        val monthPayments = filterPaymentsByMonth(payments, yearMonth)

        // Calculate total revenue
        val totalRevenue = calculateRevenue(monthPayments)

        // Calculate weekly breakdown
        val weeklyBreakdown = calculateWeeklyBreakdown(yearMonth, monthPayments)

        return MonthlyMetrics(
            yearMonth = yearMonth,
            totalRevenue = totalRevenue,
            weeklyBreakdown = weeklyBreakdown
        )
    }

    /**
     * Calculate metrics for multiple months
     *
     * Useful for trend analysis.
     *
     * @param yearMonths List of months
     * @param payments All payments
     * @param activePatients Active patient count
     * @return List of DashboardMetrics
     *
     * Example:
     * ```kotlin
     * val months = listOf(
     *     YearMonth.of(2024, 1),
     *     YearMonth.of(2024, 2),
     *     YearMonth.of(2024, 3)
     * )
     * val metrics = aggregator.calculateMultipleMonthsMetrics(months, payments, 12)
     * ```
     */
    fun calculateMultipleMonthsMetrics(
        yearMonths: List<YearMonth>,
        payments: List<Payment>,
        activePatients: Int
    ): List<DashboardMetrics> {
        return yearMonths.map { month ->
            calculateMonthlyMetrics(month, payments, activePatients)
        }
    }

    // ========================================
    // Revenue Calculations
    // ========================================

    /**
     * Calculate total revenue from payments
     *
     * Sum of all PAID payments only.
     *
     * @param payments Payments to aggregate
     * @return Total revenue
     *
     * Example:
     * ```kotlin
     * val revenue = aggregator.calculateRevenue(payments)
     * ```
     */
    fun calculateRevenue(payments: List<Payment>): BigDecimal {
        return payments
            .filter { it.isPaid }
            .fold(BigDecimal.ZERO) { acc, payment ->
                acc + payment.amount
            }
    }

    /**
     * Calculate outstanding balance
     *
     * Sum of all PENDING payments (not month-specific).
     *
     * @param payments All payments
     * @return Outstanding balance
     */
    fun calculateOutstanding(payments: List<Payment>): BigDecimal {
        return payments
            .filter { it.isPending }
            .fold(BigDecimal.ZERO) { acc, payment ->
                acc + payment.amount
            }
    }

    /**
     * Calculate collection rate
     *
     * Percentage of revenue vs total billed (paid + pending).
     *
     * @param payments All payments
     * @return Collection percentage (0-100)
     */
    fun calculateCollectionRate(payments: List<Payment>): Int {
        val revenue = calculateRevenue(payments)
        val outstanding = calculateOutstanding(payments)
        val totalBilled = revenue + outstanding

        return if (totalBilled > BigDecimal.ZERO) {
            ((revenue * BigDecimal("100")) / totalBilled).toInt()
        } else {
            0
        }
    }

    // ========================================
    // Fee Calculations
    // ========================================

    /**
     * Calculate average fee per transaction
     *
     * Average of all PAID payments.
     *
     * @param payments Payments to analyze
     * @return Average fee amount
     *
     * Example:
     * ```kotlin
     * val avgFee = aggregator.calculateAverageFee(payments)
     * ```
     */
    fun calculateAverageFee(payments: List<Payment>): BigDecimal {
        val paidPayments = payments.filter { it.isPaid }

        if (paidPayments.isEmpty()) {
            return BigDecimal.ZERO
        }

        val totalRevenue = paidPayments.fold(BigDecimal.ZERO) { acc, payment ->
            acc + payment.amount
        }

        return totalRevenue / BigDecimal(paidPayments.size)
    }

    /**
     * Calculate fee statistics
     *
     * Provides min, max, avg, median fees.
     *
     * @param payments Payments to analyze
     * @return FeeStatistics
     */
    fun calculateFeeStatistics(payments: List<Payment>): FeeStatistics {
        val paidPayments = payments.filter { it.isPaid }

        if (paidPayments.isEmpty()) {
            return FeeStatistics(
                minFee = BigDecimal.ZERO,
                maxFee = BigDecimal.ZERO,
                averageFee = BigDecimal.ZERO,
                medianFee = BigDecimal.ZERO,
                count = 0
            )
        }

        val amounts = paidPayments.map { it.amount }.sorted()
        val avg = calculateAverageFee(paidPayments)

        val median = if (amounts.size % 2 == 0) {
            (amounts[amounts.size / 2 - 1] + amounts[amounts.size / 2]) / BigDecimal(2)
        } else {
            amounts[amounts.size / 2]
        }

        return FeeStatistics(
            minFee = amounts.first(),
            maxFee = amounts.last(),
            averageFee = avg,
            medianFee = median,
            count = paidPayments.size
        )
    }

    // ========================================
    // Weekly Breakdown
    // ========================================

    /**
     * Calculate weekly breakdown for a month
     *
     * Distributes revenue across 5 weeks of month.
     * Week boundaries: 1-7, 8-14, 15-21, 22-28, 29+
     *
     * @param yearMonth Month to calculate
     * @param payments Payments for the month
     * @return Map of week number to WeeklyMetrics
     */
    fun calculateWeeklyBreakdown(
        yearMonth: YearMonth,
        payments: List<Payment>
    ): Map<Int, WeeklyMetrics> {
        val startDate = yearMonth.atDay(1)
        val weeklyMetrics = mutableMapOf<Int, WeeklyMetrics>()

        // Create weekly metrics for all 5 possible weeks
        for (week in 1..5) {
            val weekStart = startDate.plusDays((week - 1) * 7L)

            // Check if week is in month
            if (weekStart.month == yearMonth.month) {
                val weekPayments = payments.filter { payment ->
                    val paymentDate = payment.paymentDate
                    val dayOfMonth = paymentDate.dayOfMonth
                    val weekDay = (dayOfMonth - 1) / 7 + 1
                    weekDay == week
                }

                val revenue = weekPayments
                    .filter { it.isPaid }
                    .fold(BigDecimal.ZERO) { acc, p -> acc + p.amount }

                weeklyMetrics[week] = WeeklyMetrics(
                    weekNumber = week,
                    startDate = weekStart,
                    revenue = revenue,
                    transactionCount = weekPayments.size
                )
            }
        }

        return weeklyMetrics
    }

    /**
     * Get highest revenue week in month
     *
     * @param monthlyMetrics Monthly metrics with weekly breakdown
     * @return WeeklyMetrics for highest week or null
     */
    fun getHighestRevenueWeek(monthlyMetrics: MonthlyMetrics): WeeklyMetrics? {
        return monthlyMetrics.weeklyBreakdown.values.maxByOrNull { it.revenue }
    }

    /**
     * Get lowest revenue week in month
     *
     * @param monthlyMetrics Monthly metrics with weekly breakdown
     * @return WeeklyMetrics for lowest week or null
     */
    fun getLowestRevenueWeek(monthlyMetrics: MonthlyMetrics): WeeklyMetrics? {
        return monthlyMetrics.weeklyBreakdown.values.minByOrNull { it.revenue }
    }

    /**
     * Calculate week-over-week growth
     *
     * Compares consecutive weeks.
     *
     * @param monthlyMetrics Monthly metrics with weekly breakdown
     * @return List of growth percentages (week 2 vs 1, week 3 vs 2, etc.)
     */
    fun calculateWeeklyGrowth(monthlyMetrics: MonthlyMetrics): List<Int> {
        val weeks = (1..4).mapNotNull { monthlyMetrics.weeklyBreakdown[it] }
        val growth = mutableListOf<Int>()

        for (i in 0 until weeks.size - 1) {
            val current = weeks[i].revenue
            val next = weeks[i + 1].revenue

            val percentChange = if (current > BigDecimal.ZERO) {
                (((next - current) * BigDecimal("100")) / current).toInt()
            } else {
                0
            }

            growth.add(percentChange)
        }

        return growth
    }

    // ========================================
    // Filtering Helpers
    // ========================================

    /**
     * Filter payments by month
     *
     * @param payments All payments
     * @param yearMonth Month to filter
     * @return Payments in month
     */
    private fun filterPaymentsByMonth(
        payments: List<Payment>,
        yearMonth: YearMonth
    ): List<Payment> {
        val startDate = yearMonth.atDay(1)
        val endDate = yearMonth.atEndOfMonth()

        return payments.filter { payment ->
            payment.paymentDate >= startDate && payment.paymentDate <= endDate
        }
    }

    /**
     * Filter payments by status and month
     *
     * @param payments All payments
     * @param status Payment status
     * @param yearMonth Month to filter
     * @return Filtered payments
     */
    fun filterPaymentsByStatusAndMonth(
        payments: List<Payment>,
        status: String,
        yearMonth: YearMonth
    ): List<Payment> {
        return filterPaymentsByMonth(payments, yearMonth)
            .filter { it.status == status }
    }

    // ========================================
    // Transaction Analysis
    // ========================================

    /**
     * Get transaction count by status for month
     *
     * @param payments Payments for month
     * @param status Payment status
     * @return Count of transactions
     */
    fun countTransactionsByStatus(
        payments: List<Payment>,
        status: String
    ): Int {
        return payments.count { it.status == status }
    }

    /**
     * Get transaction count by payment method
     *
     * @param payments Payments to analyze
     * @param method Payment method
     * @return Count of transactions
     */
    fun countTransactionsByMethod(
        payments: List<Payment>,
        method: String
    ): Int {
        return payments.count { it.paymentMethod == method }
    }

    /**
     * Get revenue by payment method
     *
     * @param payments Payments to analyze
     * @param method Payment method
     * @return Total for method
     */
    fun getRevenueByMethod(
        payments: List<Payment>,
        method: String
    ): BigDecimal {
        return payments
            .filter { it.paymentMethod == method && it.isPaid }
            .fold(BigDecimal.ZERO) { acc, p -> acc + p.amount }
    }
}

/**
 * Fee statistics result
 *
 * @property minFee Lowest fee amount
 * @property maxFee Highest fee amount
 * @property averageFee Average fee amount
 * @property medianFee Median fee amount
 * @property count Number of transactions
 */
data class FeeStatistics(
    val minFee: BigDecimal,
    val maxFee: BigDecimal,
    val averageFee: BigDecimal,
    val medianFee: BigDecimal,
    val count: Int
) {
    /**
     * Format average fee as currency
     *
     * @return Formatted string
     */
    fun getFormattedAverage(): String {
        return "R$ ${averageFee.setScale(2, java.math.RoundingMode.HALF_UP)}"
    }

    /**
     * Get statistics summary
     *
     * @return Summary string
     */
    fun getSummary(): String {
        return "Min: R$ $minFee | Max: R$ $maxFee | " +
                "Avg: ${getFormattedAverage()} | Mediana: R$ $medianFee | Transações: $count"
    }
}
