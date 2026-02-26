package com.psychologist.financial.domain.models

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Monthly metrics with weekly breakdown
 *
 * Represents detailed metrics for a month with revenue breakdown by week.
 * Provides granular insight into revenue patterns and trends.
 *
 * Responsibilities:
 * - Represent monthly metrics with week breakdown
 * - Provide aggregated weekly revenue
 * - Calculate week-level metrics
 * - Support trend analysis within month
 * - Track weekly distribution
 *
 * Metrics Definitions:
 * - Week 1: Days 1-7
 * - Week 2: Days 8-14
 * - Week 3: Days 15-21
 * - Week 4: Days 22-28
 * - Week 5: Days 29+ (if applicable)
 *
 * Usage:
 * ```kotlin
 * val metrics = MonthlyMetrics(
 *     yearMonth = YearMonth.of(2024, 3),
 *     totalRevenue = BigDecimal("4500.00"),
 *     weeklyBreakdown = mapOf(
 *         1 to WeeklyMetrics(weekNumber = 1, startDate = LocalDate.of(2024, 3, 1), revenue = BigDecimal("1100.00")),
 *         2 to WeeklyMetrics(weekNumber = 2, startDate = LocalDate.of(2024, 3, 8), revenue = BigDecimal("1200.00")),
 *         3 to WeeklyMetrics(weekNumber = 3, startDate = LocalDate.of(2024, 3, 15), revenue = BigDecimal("1000.00")),
 *         4 to WeeklyMetrics(weekNumber = 4, startDate = LocalDate.of(2024, 3, 22), revenue = BigDecimal("1200.00"))
 *     )
 * )
 *
 * println("Total: ${metrics.getFormattedTotal()}")        // "R$ 4.500,00"
 * println("Highest week: ${metrics.getHighestRevenueWeek()}")
 * println("Distribution: ${metrics.getWeeklyDistribution()}")  // "1100,1200,1000,1200"
 * ```
 *
 * Display Properties:
 * - formattedTotal: Localized currency format
 * - weeklyRevenue: Revenue per week in order
 * - averageWeeklyRevenue: Mean weekly revenue
 * - highestWeekRevenue: Peak week revenue
 * - lowestWeekRevenue: Trough week revenue
 * - highestWeekNumber: Week with highest revenue
 * - lowestWeekNumber: Week with lowest revenue
 *
 * @property yearMonth Month for which metrics are calculated
 * @property totalRevenue Total revenue for the month
 * @property weeklyBreakdown Map of week number to WeeklyMetrics
 */
data class MonthlyMetrics(
    /**
     * Year and month for metrics
     *
     * Used to identify the period.
     */
    val yearMonth: YearMonth,

    /**
     * Total revenue for the month
     *
     * Sum of all weekly revenues.
     */
    val totalRevenue: BigDecimal,

    /**
     * Weekly breakdown of revenue
     *
     * Map from week number (1-5) to WeeklyMetrics.
     * Allows analysis of revenue distribution within month.
     */
    val weeklyBreakdown: Map<Int, WeeklyMetrics> = emptyMap()
) {

    // ========================================
    // Computed Properties
    // ========================================

    /**
     * Number of weeks with revenue
     *
     * @return Count of weeks
     */
    val weekCount: Int
        get() = weeklyBreakdown.size

    /**
     * Weekly revenues as list
     *
     * Returns revenues in week order (1, 2, 3, 4, 5).
     *
     * @return List of revenues in week order
     */
    val weeklyRevenuesInOrder: List<BigDecimal>
        get() = (1..5).mapNotNull { weeklyBreakdown[it]?.revenue }

    /**
     * Average weekly revenue
     *
     * Total divided by number of weeks with data.
     *
     * @return Average revenue per week
     */
    val averageWeeklyRevenue: BigDecimal
        get() = if (weekCount > 0) {
            totalRevenue / BigDecimal(weekCount)
        } else {
            BigDecimal.ZERO
        }

    /**
     * Highest weekly revenue
     *
     * @return Max revenue in any week
     */
    val highestWeekRevenue: BigDecimal
        get() = weeklyBreakdown.values.maxOfOrNull { it.revenue } ?: BigDecimal.ZERO

    /**
     * Lowest weekly revenue
     *
     * @return Min revenue in any week
     */
    val lowestWeekRevenue: BigDecimal
        get() = weeklyBreakdown.values.minOfOrNull { it.revenue } ?: BigDecimal.ZERO

    /**
     * Week number with highest revenue
     *
     * @return Week number (1-5) or 0 if no weeks
     */
    val highestWeekNumber: Int
        get() = weeklyBreakdown.maxByOrNull { it.value.revenue }?.key ?: 0

    /**
     * Week number with lowest revenue
     *
     * @return Week number (1-5) or 0 if no weeks
     */
    val lowestWeekNumber: Int
        get() = weeklyBreakdown.minByOrNull { it.value.revenue }?.key ?: 0

    /**
     * Revenue variance (standard deviation approximation)
     *
     * Indicates consistency of revenue across weeks.
     * High variance = inconsistent week-to-week revenue.
     * Low variance = steady revenue weeks.
     *
     * @return Variance value
     */
    val revenueVariance: BigDecimal
        get() {
            if (weekCount < 2) return BigDecimal.ZERO

            val mean = averageWeeklyRevenue
            val variances = weeklyRevenuesInOrder.map { revenue ->
                (revenue - mean).pow(2)
            }
            val sumVariances = variances.fold(BigDecimal.ZERO) { acc, v -> acc + v }
            return sumVariances / BigDecimal(weekCount)
        }

    /**
     * Revenue consistency score (0-100)
     *
     * 100 = perfectly consistent (all weeks equal)
     * 0 = highly variable
     *
     * Calculated from variance.
     *
     * @return Score 0-100
     */
    val consistencyScore: Int
        get() {
            if (weekCount < 2 || totalRevenue == BigDecimal.ZERO) return 100

            // Simple consistency: how close weeks are to average
            val avgWeekRevenue = averageWeeklyRevenue
            val deviations = weeklyRevenuesInOrder.map { revenue ->
                val deviation = (revenue - avgWeekRevenue).abs() / avgWeekRevenue
                (1 - deviation.toDouble()).coerceIn(0.0, 1.0)
            }

            return (deviations.average() * 100).toInt()
        }

    // ========================================
    // Display Methods
    // ========================================

    /**
     * Format total as currency string
     *
     * Format: "R$ 4.500,00"
     *
     * @return Formatted currency string
     */
    fun getFormattedTotal(): String {
        return "R$ ${totalRevenue.setScale(2, java.math.RoundingMode.HALF_UP)}"
    }

    /**
     * Format average weekly revenue as currency
     *
     * Format: "R$ 1.125,00"
     *
     * @return Formatted currency string
     */
    fun getFormattedAverageWeekly(): String {
        return "R$ ${averageWeeklyRevenue.setScale(2, java.math.RoundingMode.HALF_UP)}"
    }

    /**
     * Format highest weekly revenue as currency
     *
     * Format: "R$ 1.200,00"
     *
     * @return Formatted currency string
     */
    fun getFormattedHighestWeekly(): String {
        return "R$ ${highestWeekRevenue.setScale(2, java.math.RoundingMode.HALF_UP)}"
    }

    /**
     * Format lowest weekly revenue as currency
     *
     * Format: "R$ 1.000,00"
     *
     * @return Formatted currency string
     */
    fun getFormattedLowestWeekly(): String {
        return "R$ ${lowestWeekRevenue.setScale(2, java.math.RoundingMode.HALF_UP)}"
    }

    /**
     * Get weekly revenue distribution as string
     *
     * Format: "W1: R$ 1.100 | W2: R$ 1.200 | W3: R$ 1.000 | W4: R$ 1.200"
     *
     * @return Distribution string
     */
    fun getWeeklyDistribution(): String {
        return weeklyBreakdown
            .toSortedMap()
            .entries
            .map { (week, metrics) ->
                "W$week: ${metrics.getFormattedRevenue()}"
            }
            .joinToString(" | ")
    }

    /**
     * Get weekly revenue as comma-separated values
     *
     * Format: "1100.00,1200.00,1000.00,1200.00"
     * Useful for charting.
     *
     * @return CSV string
     */
    fun getWeeklyRevenueCSV(): String {
        return weeklyRevenuesInOrder
            .map { it.setScale(2, java.math.RoundingMode.HALF_UP) }
            .joinToString(",")
    }

    /**
     * Get summary with statistics
     *
     * Format: "Total: R$ 4.500 | Weeks: 4 | Avg: R$ 1.125 | High: R$ 1.200 (W2)"
     *
     * @return Summary string
     */
    fun getSummary(): String {
        return "Total: ${getFormattedTotal()} | " +
                "Weeks: $weekCount | " +
                "Avg: ${getFormattedAverageWeekly()} | " +
                "High: ${getFormattedHighestWeekly()} (W$highestWeekNumber)"
    }

    /**
     * Get consistency assessment (Portuguese)
     *
     * @return Consistency description
     */
    fun getConsistencyAssessment(): String {
        return when {
            consistencyScore >= 90 -> "Muito consistente"
            consistencyScore >= 75 -> "Consistente"
            consistencyScore >= 50 -> "Moderadamente variável"
            else -> "Altamente variável"
        }
    }

    /**
     * Check if metrics are valid
     *
     * @return true if valid
     */
    fun isValid(): Boolean {
        return totalRevenue >= BigDecimal.ZERO &&
                weekCount <= 5 &&
                weeklyBreakdown.values.all { it.isValid() } &&
                weeklyRevenuesInOrder.sum() <= totalRevenue + BigDecimal("0.01")  // Allow for rounding
    }

    // ========================================
    // Companion Object
    // ========================================

    companion object {
        /**
         * Create empty monthly metrics
         *
         * @param yearMonth Month for metrics
         * @return Empty MonthlyMetrics
         */
        fun empty(yearMonth: YearMonth = YearMonth.now()): MonthlyMetrics {
            return MonthlyMetrics(
                yearMonth = yearMonth,
                totalRevenue = BigDecimal.ZERO,
                weeklyBreakdown = emptyMap()
            )
        }

        /**
         * Create sample monthly metrics for testing
         *
         * @param yearMonth Month for metrics
         * @return Sample MonthlyMetrics
         */
        fun sample(yearMonth: YearMonth = YearMonth.now()): MonthlyMetrics {
            val startDate = yearMonth.atDay(1)
            return MonthlyMetrics(
                yearMonth = yearMonth,
                totalRevenue = BigDecimal("4500.00"),
                weeklyBreakdown = mapOf(
                    1 to WeeklyMetrics(
                        weekNumber = 1,
                        startDate = startDate,
                        revenue = BigDecimal("1100.00"),
                        transactionCount = 4
                    ),
                    2 to WeeklyMetrics(
                        weekNumber = 2,
                        startDate = startDate.plusDays(7),
                        revenue = BigDecimal("1200.00"),
                        transactionCount = 5
                    ),
                    3 to WeeklyMetrics(
                        weekNumber = 3,
                        startDate = startDate.plusDays(14),
                        revenue = BigDecimal("1000.00"),
                        transactionCount = 4
                    ),
                    4 to WeeklyMetrics(
                        weekNumber = 4,
                        startDate = startDate.plusDays(21),
                        revenue = BigDecimal("1200.00"),
                        transactionCount = 5
                    )
                )
            )
        }
    }
}

/**
 * Weekly metrics for a single week
 *
 * Represents revenue and transaction data for one week of a month.
 *
 * @property weekNumber Week number (1-5)
 * @property startDate First day of week
 * @property revenue Total revenue for week
 * @property transactionCount Number of transactions in week
 */
data class WeeklyMetrics(
    val weekNumber: Int,
    val startDate: LocalDate,
    val revenue: BigDecimal,
    val transactionCount: Int = 0
) {

    /**
     * End date of week (6 days after start)
     *
     * @return Last day of week
     */
    val endDate: LocalDate
        get() = startDate.plusDays(6)

    /**
     * Number of days in week (1-7)
     *
     * @return Day count
     */
    val dayCount: Int
        get() = (ChronoUnit.DAYS.between(startDate, endDate) + 1).toInt()

    /**
     * Average revenue per transaction
     *
     * @return Average amount
     */
    val averageTransactionAmount: BigDecimal
        get() = if (transactionCount > 0) {
            revenue / BigDecimal(transactionCount)
        } else {
            BigDecimal.ZERO
        }

    /**
     * Average revenue per day
     *
     * @return Average daily revenue
     */
    val averageDailyRevenue: BigDecimal
        get() = if (dayCount > 0) {
            revenue / BigDecimal(dayCount)
        } else {
            BigDecimal.ZERO
        }

    /**
     * Format revenue as currency
     *
     * @return Formatted string
     */
    fun getFormattedRevenue(): String {
        return "R$ ${revenue.setScale(2, java.math.RoundingMode.HALF_UP)}"
    }

    /**
     * Format date range
     *
     * @return Range string "01-07 Mar"
     */
    fun getFormattedDateRange(): String {
        val startDay = startDate.dayOfMonth.toString().padStart(2, '0')
        val endDay = endDate.dayOfMonth.toString().padStart(2, '0')
        val monthName = when (startDate.monthValue) {
            1 -> "Jan"
            2 -> "Fev"
            3 -> "Mar"
            4 -> "Abr"
            5 -> "Mai"
            6 -> "Jun"
            7 -> "Jul"
            8 -> "Ago"
            9 -> "Set"
            10 -> "Out"
            11 -> "Nov"
            12 -> "Dez"
            else -> "???"
        }
        return "$startDay-$endDay $monthName"
    }

    /**
     * Get summary
     *
     * @return Summary string
     */
    fun getSummary(): String {
        return "W$weekNumber: ${getFormattedRevenue()} | ${getFormattedDateRange()} | $transactionCount transações"
    }

    /**
     * Check if metrics are valid
     *
     * @return true if valid
     */
    fun isValid(): Boolean {
        return weekNumber in 1..5 &&
                revenue >= BigDecimal.ZERO &&
                transactionCount >= 0 &&
                dayCount in 1..7
    }

    companion object {
        /**
         * Create empty weekly metrics
         *
         * @param weekNumber Week number
         * @param startDate First day of week
         * @return Empty WeeklyMetrics
         */
        fun empty(weekNumber: Int, startDate: LocalDate): WeeklyMetrics {
            return WeeklyMetrics(
                weekNumber = weekNumber,
                startDate = startDate,
                revenue = BigDecimal.ZERO,
                transactionCount = 0
            )
        }
    }
}

/**
 * Extension function to sum monthly metrics
 *
 * @receiver List of monthly metrics
 * @return Total revenue across all months
 */
fun List<MonthlyMetrics>.getTotalRevenue(): BigDecimal {
    return fold(BigDecimal.ZERO) { acc, metrics ->
        acc + metrics.totalRevenue
    }
}

/**
 * Extension function to get average monthly revenue
 *
 * @receiver List of monthly metrics
 * @return Average revenue per month
 */
fun List<MonthlyMetrics>.getAverageRevenue(): BigDecimal {
    if (isEmpty()) return BigDecimal.ZERO
    return getTotalRevenue() / BigDecimal(size)
}

/**
 * Extension function to get highest revenue month
 *
 * @receiver List of monthly metrics
 * @return Month with highest revenue or null
 */
fun List<MonthlyMetrics>.getHighestRevenueMonth(): MonthlyMetrics? {
    return maxByOrNull { it.totalRevenue }
}

/**
 * Extension function to get lowest revenue month
 *
 * @receiver List of monthly metrics
 * @return Month with lowest revenue or null
 */
fun List<MonthlyMetrics>.getLowestRevenueMonth(): MonthlyMetrics? {
    return minByOrNull { it.totalRevenue }
}

/**
 * Extension function to get average consistency
 *
 * @receiver List of monthly metrics
 * @return Average consistency score (0-100)
 */
fun List<MonthlyMetrics>.getAverageConsistency(): Int {
    if (isEmpty()) return 0
    return map { it.consistencyScore }.average().toInt()
}
