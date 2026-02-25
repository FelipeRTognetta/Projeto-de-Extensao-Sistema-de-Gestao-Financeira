package com.psychologist.financial.domain.models

import java.time.LocalDate
import java.time.YearMonth

/**
 * Billable hours summary domain model
 *
 * Represents aggregated billable metrics for a patient.
 * Used in patient profile and billing views.
 *
 * Metrics Included:
 * - Total sessions completed
 * - Total billable hours (sum of all session durations)
 * - Average session duration
 * - Upcoming sessions and hours (for forecasting)
 * - Session breakdown by length
 *
 * Responsibilities:
 * - Display aggregated appointment metrics
 * - Format hours for user display
 * - Calculate derived metrics (averages, totals)
 * - Support billing and invoicing
 * - Track progress and trends
 *
 * Display Formats:
 * - Hours: "15h 30min"
 * - Average: "45min" or "1h 15min"
 * - Currency: With hourly rate application
 * - Status: "On track", "Behind schedule"
 *
 * Example:
 * ```kotlin
 * val summary = BillableHoursSummary(
 *     totalSessions = 20,
 *     totalBillableHours = 15.5,
 *     averageSessionHours = 0.775,
 *     minSessionHours = 0.25,
 *     maxSessionHours = 1.5,
 *     upcomingSessions = 3,
 *     upcomingBillableHours = 2.25,
 *     lastSessionDate = LocalDate.of(2024, 3, 15),
 *     currentMonthSessions = 4,
 *     currentMonthBillableHours = 3.0
 * )
 *
 * println(summary.getDisplayText())
 * // Output: "20 sessions | 15h 30min | Média: 46 min"
 *
 * println(summary.getFormattedTotalRevenue(150.0))
 * // Output: "$2,325.00"
 * ```
 *
 * Comparison with BillableHoursSummary from Calculator:
 * - Calculator output: Computed metrics (what was calculated)
 * - Domain model: Display data (what to show user)
 * - Domain model has more context (patient info, periods, trends)
 */
data class BillableHoursSummary(
    // Completed sessions
    val totalSessions: Int,
    val totalBillableHours: Double,
    val averageSessionHours: Double,
    val minSessionHours: Double,
    val maxSessionHours: Double,

    // Upcoming sessions
    val upcomingSessions: Int = 0,
    val upcomingBillableHours: Double = 0.0,

    // Additional context
    val lastSessionDate: LocalDate? = null,
    val firstSessionDate: LocalDate? = null,
    val currentMonthSessions: Int = 0,
    val currentMonthBillableHours: Double = 0.0,
    val currentMonthAverageSessionHours: Double = 0.0,

    // Patient context (optional)
    val patientId: Long? = null,
    val patientName: String? = null
) {

    // ========================================
    // Computed Properties
    // ========================================

    /**
     * Total sessions (completed + upcoming)
     */
    val allSessions: Int
        get() = totalSessions + upcomingSessions

    /**
     * Total billable hours (completed + upcoming)
     */
    val allBillableHours: Double
        get() = totalBillableHours + upcomingBillableHours

    /**
     * Days since first session
     */
    val daysSinceFirstSession: Long?
        get() = firstSessionDate?.let {
            java.time.temporal.ChronoUnit.DAYS.between(it, LocalDate.now())
        }

    /**
     * Days since last session
     */
    val daysSinceLastSession: Long?
        get() = lastSessionDate?.let {
            java.time.temporal.ChronoUnit.DAYS.between(it, LocalDate.now())
        }

    /**
     * Sessions per week (average over lifetime)
     */
    val sessionsPerWeek: Double
        get() {
            val daysSince = daysSinceFirstSession ?: 1L
            val weeks = (daysSince / 7.0).coerceAtLeast(1.0)
            return totalSessions / weeks
        }

    /**
     * Hours per week (average over lifetime)
     */
    val hoursPerWeek: Double
        get() {
            val daysSince = daysSinceFirstSession ?: 1L
            val weeks = (daysSince / 7.0).coerceAtLeast(1.0)
            return totalBillableHours / weeks
        }

    /**
     * Average days between sessions
     */
    val averageDaysBetweenSessions: Double
        get() {
            val daysSince = daysSinceFirstSession ?: 1L
            return if (totalSessions > 1) {
                daysSince.toDouble() / (totalSessions - 1)
            } else {
                0.0
            }
        }

    /**
     * Consistency score (0-100)
     *
     * Based on regularity of sessions.
     * Higher score = more consistent scheduling.
     */
    val consistencyScore: Int
        get() {
            if (totalSessions < 2) return 0

            // Calculate expected vs actual sessions
            val daysSince = daysSinceFirstSession ?: 1L
            val expectedWeeklyRate = 1.0  // One session per week baseline
            val expectedSessions = (daysSince / 7.0) * expectedWeeklyRate
            val actualSessions = totalSessions.toDouble()

            // Score: how close actual is to expected (capped at 100)
            return (actualSessions / expectedSessions * 100).coerceIn(0.0, 100.0).toInt()
        }

    /**
     * Progress status text (Portuguese)
     *
     * Returns: "Em dia", "Atrasado", or "Consistente"
     */
    val progressStatus: String
        get() = when (consistencyScore) {
            in 80..100 -> "Consistente"
            in 60..79 -> "Em dia"
            else -> "Atrasado"
        }

    // ========================================
    // Display Methods - Formatting
    // ========================================

    /**
     * Get total billable hours formatted as string
     *
     * Format: "15h 30min"
     * Returns just "30min" if < 1 hour
     *
     * @return Formatted hours string
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
     * Get upcoming billable hours formatted
     *
     * @return Formatted upcoming hours
     */
    fun getFormattedUpcomingHours(): String {
        val hours = upcomingBillableHours.toInt()
        val minutes = ((upcomingBillableHours - hours) * 60).toInt()

        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}min"
            hours > 0 -> "${hours}h"
            else -> "${minutes}min"
        }
    }

    /**
     * Get average session duration formatted
     *
     * Format: "45min" or "1h 15min"
     *
     * @return Formatted average session duration
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

    /**
     * Get sessions per week formatted
     *
     * @return Formatted sessions/week (e.g., "1.5")
     */
    fun getFormattedSessionsPerWeek(): String {
        return String.format("%.1f", sessionsPerWeek)
    }

    /**
     * Get hours per week formatted
     *
     * @return Formatted hours/week (e.g., "1h 10min")
     */
    fun getFormattedHoursPerWeek(): String {
        val hours = hoursPerWeek.toInt()
        val minutes = ((hoursPerWeek - hours) * 60).toInt()

        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}min"
            hours > 0 -> "${hours}h"
            else -> "${minutes}min"
        }
    }

    /**
     * Get consistent human-readable display text
     *
     * Format: "20 sessões | 15h 30min | Média: 46 min"
     *
     * @return Display text for UI
     */
    fun getDisplayText(): String {
        val sessionText = when (totalSessions) {
            1 -> "1 sessão"
            else -> "$totalSessions sessões"
        }
        val hoursText = getFormattedTotalHours()
        val avgText = getFormattedAverageSessionHours()

        return "$sessionText | $hoursText | Média: $avgText"
    }

    /**
     * Get summary card text for patient profile
     *
     * Format: "20 consultas\n15h 30min\nÚltima: 15 dias"
     *
     * @return Multi-line summary
     */
    fun getCardText(): String {
        val sessionText = when (totalSessions) {
            1 -> "1 consulta"
            else -> "$totalSessions consultas"
        }
        val hoursText = getFormattedTotalHours()
        val lastSessionText = lastSessionDate?.let { date ->
            val daysSince = daysSinceLastSession ?: 0
            when {
                daysSince == 0L -> "Última: Hoje"
                daysSince == 1L -> "Última: Ontem"
                else -> "Última: $daysSince dias"
            }
        } ?: "Nenhuma consulta"

        return "$sessionText\n$hoursText\n$lastSessionText"
    }

    // ========================================
    // Revenue Calculations
    // ========================================

    /**
     * Calculate billable revenue at given hourly rate
     *
     * @param hourlyRate Rate per hour
     * @return Total revenue (hours × rate)
     */
    fun calculateRevenue(hourlyRate: Double): Double {
        return totalBillableHours * hourlyRate
    }

    /**
     * Calculate upcoming revenue at given hourly rate
     *
     * @param hourlyRate Rate per hour
     * @return Upcoming revenue (scheduled hours × rate)
     */
    fun calculateUpcomingRevenue(hourlyRate: Double): Double {
        return upcomingBillableHours * hourlyRate
    }

    /**
     * Get formatted revenue string with currency
     *
     * Format: "$2,325.00"
     *
     * @param hourlyRate Rate per hour
     * @param currency Currency symbol (default: "$")
     * @return Formatted revenue
     */
    fun getFormattedRevenue(hourlyRate: Double, currency: String = "R$"): String {
        val revenue = calculateRevenue(hourlyRate)
        return String.format("%s %.2f", currency, revenue)
    }

    /**
     * Get formatted upcoming revenue
     *
     * @param hourlyRate Rate per hour
     * @param currency Currency symbol
     * @return Formatted upcoming revenue
     */
    fun getFormattedUpcomingRevenue(hourlyRate: Double, currency: String = "R$"): String {
        val revenue = calculateUpcomingRevenue(hourlyRate)
        return String.format("%s %.2f", currency, revenue)
    }

    // ========================================
    // Current Period Metrics
    // ========================================

    /**
     * Get current month summary text
     *
     * @return Text summarizing this month
     */
    fun getCurrentMonthSummary(): String {
        return when (currentMonthSessions) {
            0 -> "Nenhuma consulta este mês"
            1 -> "1 consulta este mês | ${getFormattedMonthlyHours()}"
            else -> "$currentMonthSessions consultas este mês | ${getFormattedMonthlyHours()}"
        }
    }

    /**
     * Get current month hours formatted
     *
     * @return Formatted current month hours
     */
    fun getFormattedMonthlyHours(): String {
        val hours = currentMonthBillableHours.toInt()
        val minutes = ((currentMonthBillableHours - hours) * 60).toInt()

        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}min"
            hours > 0 -> "${hours}h"
            else -> "${minutes}min"
        }
    }

    /**
     * Get monthly average session duration
     *
     * @return Formatted average for current month
     */
    fun getFormattedMonthlyAverageSessionHours(): String {
        if (currentMonthSessions == 0) return "N/A"

        val minutes = (currentMonthAverageSessionHours * 60).toInt()
        return when {
            minutes >= 60 -> {
                val hours = minutes / 60
                val mins = minutes % 60
                if (mins > 0) "${hours}h ${mins}min" else "${hours}h"
            }
            else -> "${minutes}min"
        }
    }

    // ========================================
    // Status Indicators
    // ========================================

    /**
     * Get status indicator emoji (Portuguese context)
     *
     * @return Emoji representing status
     */
    fun getStatusEmoji(): String {
        return when (progressStatus) {
            "Consistente" -> "✅"
            "Em dia" -> "⚠️"
            else -> "❌"
        }
    }

    /**
     * Get progress indicator for UI
     *
     * Returns percentage (0-100) for progress bar.
     *
     * @return Progress percentage
     */
    fun getProgressPercentage(): Int {
        return consistencyScore
    }

    /**
     * Check if patient needs follow-up
     *
     * Returns true if no session in 30+ days.
     *
     * @return true if needs follow-up
     */
    fun needsFollowUp(): Boolean {
        return lastSessionDate?.let {
            daysSinceLastSession?.let { days ->
                days >= 30
            } ?: true
        } ?: true
    }

    // ========================================
    // Companion Object
    // ========================================

    companion object {
        /**
         * Create empty/zero summary
         *
         * @param patientId Patient ID
         * @param patientName Patient name
         * @return Zero summary
         */
        fun empty(patientId: Long? = null, patientName: String? = null): BillableHoursSummary {
            return BillableHoursSummary(
                totalSessions = 0,
                totalBillableHours = 0.0,
                averageSessionHours = 0.0,
                minSessionHours = 0.0,
                maxSessionHours = 0.0,
                patientId = patientId,
                patientName = patientName
            )
        }

        /**
         * Create sample summary for testing
         *
         * @return Sample summary with mock data
         */
        fun sample(): BillableHoursSummary {
            return BillableHoursSummary(
                totalSessions = 20,
                totalBillableHours = 15.5,
                averageSessionHours = 0.775,
                minSessionHours = 0.25,
                maxSessionHours = 1.5,
                upcomingSessions = 3,
                upcomingBillableHours = 2.25,
                lastSessionDate = LocalDate.now().minusDays(7),
                firstSessionDate = LocalDate.now().minusDays(120),
                currentMonthSessions = 4,
                currentMonthBillableHours = 3.0,
                currentMonthAverageSessionHours = 0.75,
                patientName = "João Silva"
            )
        }
    }
}
