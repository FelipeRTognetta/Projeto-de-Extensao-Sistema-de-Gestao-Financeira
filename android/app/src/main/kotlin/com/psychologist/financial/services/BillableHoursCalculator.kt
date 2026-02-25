package com.psychologist.financial.services

import com.psychologist.financial.domain.models.Appointment
import java.time.LocalDate
import java.time.YearMonth

/**
 * Billable hours calculator service
 *
 * Calculates therapeutic session billable hours for patients.
 * Supports various aggregation levels: total, by period, by month, by week.
 *
 * Responsibilities:
 * - Calculate billable hours from appointments
 * - Aggregate by time period
 * - Track billable metrics per patient
 * - Generate billing summaries
 * - Support different billing rules
 *
 * Billing Rules:
 * - Sessions only count if completed (date < today)
 * - Duration is converted to decimal hours: minutes / 60
 * - Minimum session: 5 minutes
 * - Maximum session: 8 hours (480 minutes)
 * - Partial hours counted (e.g., 45 min = 0.75 hours)
 *
 * Example:
 * ```kotlin
 * val calculator = BillableHoursCalculator()
 *
 * // Calculate total billable hours for patient
 * val totalHours = calculator.calculateTotalBillableHours(appointments)
 * println("Total billable hours: $totalHours")  // 15.5
 *
 * // Calculate billable hours for a month
 * val marchHours = calculator.calculateMonthlyBillableHours(
 *     appointments = appointments,
 *     month = YearMonth.of(2024, 3)
 * )
 *
 * // Get billable hours summary
 * val summary = calculator.calculateBillableHoursSummary(appointments)
 * println("Sessions: ${summary.totalSessions}")      // 20
 * println("Hours: ${summary.totalBillableHours}")    // 15.5
 * println("Average: ${summary.averageSessionHours}") // 0.775
 *
 * // Calculate by billing rate
 * val revenue = calculator.calculateRevenue(
 *     appointments = appointments,
 *     hourlyRate = 150.0  // $150 per hour
 * )
 * println("Billable revenue: $${revenue}")  // $2325.00
 * ```
 *
 * Performance Considerations:
 * - Filters appointments in-memory (should be <1000)
 * - O(n) time complexity for single pass calculations
 * - Suitable for real-time UI updates
 * - Caching recommended for daily/weekly reports
 */
class BillableHoursCalculator {

    // ========================================
    // Total Billable Hours Calculation
    // ========================================

    /**
     * Calculate total billable hours for list of appointments
     *
     * Only counts completed (past) appointments.
     * Formula: SUM(duration_minutes / 60) for each completed appointment
     *
     * @param appointments List of appointments
     * @param beforeDate Cutoff date for "completed" (default: today)
     * @return Total billable hours as decimal
     */
    fun calculateTotalBillableHours(
        appointments: List<Appointment>,
        beforeDate: LocalDate = LocalDate.now()
    ): Double {
        return appointments
            .filter { it.date.isBefore(beforeDate) }
            .sumOf { it.billableHours }
    }

    /**
     * Calculate billable hours for completed and upcoming appointments
     *
     * Split calculation for invoicing and forecasting.
     *
     * @param appointments List of appointments
     * @return Pair of (completed hours, upcoming hours)
     */
    fun calculateBillableHoursSplit(
        appointments: List<Appointment>
    ): Pair<Double, Double> {
        val now = LocalDate.now()
        val completedHours = appointments
            .filter { it.date.isBefore(now) }
            .sumOf { it.billableHours }
        val upcomingHours = appointments
            .filter { !it.date.isBefore(now) }
            .sumOf { it.billableHours }
        return completedHours to upcomingHours
    }

    // ========================================
    // Period-Based Calculations
    // ========================================

    /**
     * Calculate billable hours for specific date range
     *
     * @param appointments List of appointments
     * @param startDate Start date (inclusive)
     * @param endDate End date (inclusive)
     * @return Billable hours within range
     */
    fun calculateRangeBillableHours(
        appointments: List<Appointment>,
        startDate: LocalDate,
        endDate: LocalDate
    ): Double {
        return appointments
            .filter { it.date >= startDate && it.date <= endDate }
            .sumOf { it.billableHours }
    }

    /**
     * Calculate billable hours for specific month
     *
     * @param appointments List of appointments
     * @param month Year-month to calculate
     * @return Billable hours for month
     */
    fun calculateMonthlyBillableHours(
        appointments: List<Appointment>,
        month: YearMonth
    ): Double {
        val startDate = month.atDay(1)
        val endDate = month.atEndOfMonth()
        return calculateRangeBillableHours(appointments, startDate, endDate)
    }

    /**
     * Calculate billable hours for specific week
     *
     * Week defined as Monday-Sunday.
     *
     * @param appointments List of appointments
     * @param date Any date within the week
     * @return Billable hours for that week
     */
    fun calculateWeeklyBillableHours(
        appointments: List<Appointment>,
        date: LocalDate = LocalDate.now()
    ): Double {
        // Find Monday of week containing date
        val monday = date.minusDays(date.dayOfWeek.value.toLong() - 1)
        val sunday = monday.plusDays(6)
        return calculateRangeBillableHours(appointments, monday, sunday)
    }

    /**
     * Calculate billable hours for last N days
     *
     * @param appointments List of appointments
     * @param days Number of days to look back
     * @return Billable hours in last N days
     */
    fun calculateLastNDaysBillableHours(
        appointments: List<Appointment>,
        days: Int = 30
    ): Double {
        val startDate = LocalDate.now().minusDays(days.toLong())
        return calculateRangeBillableHours(appointments, startDate, LocalDate.now())
    }

    // ========================================
    // Monthly Breakdown
    // ========================================

    /**
     * Get billable hours breakdown by month
     *
     * Useful for monthly invoicing and reporting.
     *
     * @param appointments List of appointments
     * @return Map of month to billable hours
     */
    fun calculateMonthlyBreakdown(
        appointments: List<Appointment>
    ): Map<YearMonth, Double> {
        return appointments
            .filter { it.isPast }
            .groupBy { YearMonth.from(it.date) }
            .mapValues { (_, apps) ->
                apps.sumOf { it.billableHours }
            }
            .toSortedMap()
    }

    /**
     * Get billable hours breakdown by week
     *
     * Useful for weekly reporting.
     *
     * @param appointments List of appointments
     * @param month Filter to specific month (optional)
     * @return Map of week start date to billable hours
     */
    fun calculateWeeklyBreakdown(
        appointments: List<Appointment>,
        month: YearMonth? = null
    ): Map<LocalDate, Double> {
        var filtered = appointments.filter { it.isPast }

        if (month != null) {
            val startDate = month.atDay(1)
            val endDate = month.atEndOfMonth()
            filtered = filtered.filter { it.date >= startDate && it.date <= endDate }
        }

        // Group by week start (Monday)
        return filtered
            .groupBy { date ->
                date.date.minusDays(date.date.dayOfWeek.value.toLong() - 1)
            }
            .mapValues { (_, apps) ->
                apps.sumOf { it.billableHours }
            }
            .toSortedMap()
    }

    /**
     * Get billable hours breakdown by day
     *
     * @param appointments List of appointments
     * @param month Filter to specific month (optional)
     * @return Map of date to billable hours
     */
    fun calculateDailyBreakdown(
        appointments: List<Appointment>,
        month: YearMonth? = null
    ): Map<LocalDate, Double> {
        var filtered = appointments.filter { it.isPast }

        if (month != null) {
            val startDate = month.atDay(1)
            val endDate = month.atEndOfMonth()
            filtered = filtered.filter { it.date >= startDate && it.date <= endDate }
        }

        return filtered
            .groupBy { it.date }
            .mapValues { (_, apps) ->
                apps.sumOf { it.billableHours }
            }
            .toSortedMap()
    }

    // ========================================
    // Statistics
    // ========================================

    /**
     * Calculate billable hours summary
     *
     * Provides comprehensive metrics for a collection of appointments.
     *
     * @param appointments List of appointments
     * @return BillableHoursSummary with all metrics
     */
    fun calculateBillableHoursSummary(
        appointments: List<Appointment>
    ): BillableHoursSummary {
        val completedAppointments = appointments.filter { it.isPast }

        val totalSessions = completedAppointments.size
        val totalBillableHours = completedAppointments.sumOf { it.billableHours }
        val averageSessionHours = if (totalSessions > 0) {
            totalBillableHours / totalSessions
        } else {
            0.0
        }
        val minSessionHours = completedAppointments.minOfOrNull { it.billableHours } ?: 0.0
        val maxSessionHours = completedAppointments.maxOfOrNull { it.billableHours } ?: 0.0

        return BillableHoursSummary(
            totalSessions = totalSessions,
            totalBillableHours = totalBillableHours,
            averageSessionHours = averageSessionHours,
            minSessionHours = minSessionHours,
            maxSessionHours = maxSessionHours,
            upcomingSessions = appointments.count { !it.isPast },
            upcomingBillableHours = appointments.filter { !it.isPast }.sumOf { it.billableHours }
        )
    }

    /**
     * Get session distribution (hours grouped by ranges)
     *
     * Useful for understanding session length distribution.
     *
     * @param appointments List of appointments
     * @return Map of duration range to count
     */
    fun calculateSessionLengthDistribution(
        appointments: List<Appointment>
    ): Map<String, Int> {
        return appointments
            .filter { it.isPast }
            .groupingBy { app ->
                when {
                    app.durationMinutes < 30 -> "< 30 min"
                    app.durationMinutes < 60 -> "30-60 min"
                    app.durationMinutes < 90 -> "60-90 min"
                    app.durationMinutes < 120 -> "90-120 min"
                    else -> "> 120 min"
                }
            }
            .eachCount()
    }

    // ========================================
    // Revenue Calculations
    // ========================================

    /**
     * Calculate billable revenue for appointments
     *
     * @param appointments List of appointments
     * @param hourlyRate Hourly rate in currency
     * @return Total revenue (billable hours × rate)
     */
    fun calculateRevenue(
        appointments: List<Appointment>,
        hourlyRate: Double
    ): Double {
        val billableHours = calculateTotalBillableHours(appointments)
        return billableHours * hourlyRate
    }

    /**
     * Calculate monthly revenue breakdown
     *
     * @param appointments List of appointments
     * @param hourlyRate Hourly rate in currency
     * @return Map of month to revenue
     */
    fun calculateMonthlyRevenue(
        appointments: List<Appointment>,
        hourlyRate: Double
    ): Map<YearMonth, Double> {
        return calculateMonthlyBreakdown(appointments)
            .mapValues { (_, hours) ->
                hours * hourlyRate
            }
    }

    /**
     * Calculate weekly revenue breakdown
     *
     * @param appointments List of appointments
     * @param hourlyRate Hourly rate in currency
     * @return Map of week start date to revenue
     */
    fun calculateWeeklyRevenue(
        appointments: List<Appointment>,
        hourlyRate: Double
    ): Map<LocalDate, Double> {
        return calculateWeeklyBreakdown(appointments)
            .mapValues { (_, hours) ->
                hours * hourlyRate
            }
    }

    // ========================================
    // Comparative Analysis
    // ========================================

    /**
     * Compare billable hours between two periods
     *
     * @param appointments List of appointments
     * @param period1Start Start of first period
     * @param period1End End of first period
     * @param period2Start Start of second period
     * @param period2End End of second period
     * @return Comparison with difference and percentage change
     */
    fun comparePeriods(
        appointments: List<Appointment>,
        period1Start: LocalDate,
        period1End: LocalDate,
        period2Start: LocalDate,
        period2End: LocalDate
    ): PeriodComparison {
        val period1Hours = calculateRangeBillableHours(appointments, period1Start, period1End)
        val period2Hours = calculateRangeBillableHours(appointments, period2Start, period2End)

        val difference = period2Hours - period1Hours
        val percentageChange = if (period1Hours > 0) {
            (difference / period1Hours) * 100
        } else if (period2Hours > 0) {
            100.0  // Went from 0 to something
        } else {
            0.0    // Both are 0
        }

        return PeriodComparison(
            period1Hours = period1Hours,
            period2Hours = period2Hours,
            difference = difference,
            percentageChange = percentageChange
        )
    }

    /**
     * Compare this month to last month
     *
     * @param appointments List of appointments
     * @return Comparison for current vs previous month
     */
    fun compareMonths(
        appointments: List<Appointment>
    ): PeriodComparison {
        val thisMonth = YearMonth.now()
        val lastMonth = thisMonth.minusMonths(1)

        return comparePeriods(
            appointments = appointments,
            period1Start = lastMonth.atDay(1),
            period1End = lastMonth.atEndOfMonth(),
            period2Start = thisMonth.atDay(1),
            period2End = thisMonth.atEndOfMonth()
        )
    }

    /**
     * Get growth trend (compare multiple periods)
     *
     * @param appointments List of appointments
     * @param months Number of months to analyze
     * @return List of monthly hours in chronological order
     */
    fun calculateGrowthTrend(
        appointments: List<Appointment>,
        months: Int = 12
    ): List<Pair<YearMonth, Double>> {
        val startMonth = YearMonth.now().minusMonths((months - 1).toLong())
        return (0 until months)
            .map { startMonth.plusMonths(it.toLong()) }
            .map { month ->
                month to calculateMonthlyBillableHours(appointments, month)
            }
    }
}

/**
 * Billable hours summary data class
 *
 * Contains comprehensive billable metrics.
 */
data class BillableHoursSummary(
    val totalSessions: Int,
    val totalBillableHours: Double,
    val averageSessionHours: Double,
    val minSessionHours: Double,
    val maxSessionHours: Double,
    val upcomingSessions: Int = 0,
    val upcomingBillableHours: Double = 0.0
) {
    /**
     * Get total sessions (completed + upcoming)
     */
    val allSessions: Int
        get() = totalSessions + upcomingSessions

    /**
     * Get total billable hours (completed + upcoming)
     */
    val allBillableHours: Double
        get() = totalBillableHours + upcomingBillableHours

    /**
     * Get formatted total hours string
     *
     * Format: "15h 30min"
     */
    fun getFormattedTotalHours(): String {
        val hours = totalBillableHours.toInt()
        val minutes = ((totalBillableHours - hours) * 60).toInt()

        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}min"
            hours > 0 -> "${hours}h"
            else -> "${minutes}min"
        }
    }

    /**
     * Get formatted average session duration
     *
     * Format: "45min" or "1h 15min"
     */
    fun getFormattedAverageSessionHours(): String {
        val minutes = (averageSessionHours * 60).toInt()
        return when {
            minutes >= 60 -> {
                val hours = minutes / 60
                val mins = minutes % 60
                if (mins > 0) "${hours}h ${mins}min" else "${hours}h"
            }
            else -> "${minutes}min"
        }
    }
}

/**
 * Period comparison data class
 *
 * For comparing billable hours between two time periods.
 */
data class PeriodComparison(
    val period1Hours: Double,
    val period2Hours: Double,
    val difference: Double,
    val percentageChange: Double
) {
    /**
     * Check if period 2 had more hours than period 1
     */
    val isGrowth: Boolean
        get() = difference > 0

    /**
     * Get formatted percentage change string
     *
     * Format: "+15.5%" or "-10.2%"
     */
    fun getFormattedPercentageChange(): String {
        val sign = if (percentageChange >= 0) "+" else ""
        return "$sign${String.format("%.1f", percentageChange)}%"
    }
}
