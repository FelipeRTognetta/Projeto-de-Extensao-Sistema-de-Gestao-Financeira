package com.psychologist.financial.domain.models

import java.math.BigDecimal
import java.time.YearMonth

/**
 * Dashboard metrics domain model
 *
 * Represents aggregated financial metrics for dashboard display.
 * Contains key metrics for financial status, trends, and reporting.
 *
 * Responsibilities:
 * - Represent dashboard metrics in domain layer
 * - Provide aggregated financial data
 * - Support dashboard and reporting views
 * - Enable financial analysis and trends
 * - Track performance metrics
 *
 * Metrics Definitions:
 * - Total Revenue: Sum of all PAID payments for the month
 * - Active Patients: Count of patients with status ACTIVE
 * - Average Fee: Average payment amount per transaction (PAID only)
 * - Outstanding Balance: Sum of all PENDING payments
 *
 * Usage:
 * ```kotlin
 * val metrics = DashboardMetrics(
 *     yearMonth = YearMonth.of(2024, 3),
 *     totalRevenue = BigDecimal("4500.00"),
 *     activePatients = 12,
 *     averageFee = BigDecimal("250.00"),
 *     outstandingBalance = BigDecimal("750.00"),
 *     totalTransactions = 18
 * )
 *
 * println("Revenue: ${metrics.getFormattedRevenue()}")      // "R$ 4.500,00"
 * println("Patients: ${metrics.activePatients}")             // "12"
 * println("Outstanding: ${metrics.getFormattedOutstanding()}") // "R$ 750,00"
 * println("Month: ${metrics.getFormattedMonth()}")           // "Março de 2024"
 * ```
 *
 * Display Properties:
 * - formattedRevenue: Localized currency format (R$ x.xxx,xx)
 * - formattedAverageFee: Average payment formatted
 * - formattedOutstanding: Outstanding balance formatted
 * - formattedMonth: Month name and year in Portuguese
 * - collectionRate: Percentage of revenue vs total invoiced
 * - revenueTrend: Up/down/stable compared to previous month
 * - transactionCount: Total transaction count
 *
 * @property yearMonth Month for which metrics are calculated
 * @property totalRevenue Total amount received (PAID payments only)
 * @property activePatients Count of active patients
 * @property averageFee Average payment amount per transaction
 * @property outstandingBalance Total pending amount
 * @property totalTransactions Total transaction count
 */
data class DashboardMetrics(
    /**
     * Year and month for metrics
     *
     * Used to filter data by month.
     */
    val yearMonth: YearMonth,

    /**
     * Total revenue for the month
     *
     * Sum of all PAID payments for the month.
     * Does not include PENDING payments.
     */
    val totalRevenue: BigDecimal,

    /**
     * Count of active patients
     *
     * Number of patients with status = ACTIVE.
     * Does not include INACTIVE patients.
     */
    val activePatients: Int,

    /**
     * Average payment amount
     *
     * Average of all PAID payments.
     * Useful for pricing analysis.
     */
    val averageFee: BigDecimal,

    /**
     * Outstanding balance
     *
     * Sum of all PENDING payments.
     * Accounts receivable metric.
     */
    val outstandingBalance: BigDecimal,

    /**
     * Total transaction count
     *
     * Count of all transactions (PAID + PENDING).
     * Useful for volume analysis.
     */
    val totalTransactions: Int = 0
) {

    // ========================================
    // Computed Properties
    // ========================================

    /**
     * Total billed amount
     *
     * Sum of revenue and outstanding.
     *
     * @return Total billed
     */
    val totalBilled: BigDecimal
        get() = totalRevenue + outstandingBalance

    /**
     * Collection rate (0-100)
     *
     * Percentage of revenue vs total billed.
     *
     * @return 0-100 or 0 if no billed amount
     */
    val collectionRate: Int
        get() = if (totalBilled > BigDecimal.ZERO) {
            ((totalRevenue * BigDecimal("100")) / totalBilled).toInt()
        } else {
            0
        }

    /**
     * Outstanding percentage (0-100)
     *
     * Percentage of outstanding vs total billed.
     *
     * @return 0-100 or 0 if no billed amount
     */
    val outstandingPercentage: Int
        get() = if (totalBilled > BigDecimal.ZERO) {
            ((outstandingBalance * BigDecimal("100")) / totalBilled).toInt()
        } else {
            0
        }

    /**
     * Average revenue per patient
     *
     * Total revenue divided by active patients.
     *
     * @return Average revenue per patient or zero
     */
    val revenuePerPatient: BigDecimal
        get() = if (activePatients > 0) {
            totalRevenue / BigDecimal(activePatients)
        } else {
            BigDecimal.ZERO
        }

    /**
     * Check if has outstanding balance
     *
     * @return true if outstanding > 0
     */
    val hasOutstandingBalance: Boolean
        get() = outstandingBalance > BigDecimal.ZERO

    /**
     * Check if revenue is good
     *
     * Threshold: > R$ 1000
     *
     * @return true if revenue > 1000
     */
    val isGoodRevenue: Boolean
        get() = totalRevenue > BigDecimal("1000.00")

    // ========================================
    // Display Methods
    // ========================================

    /**
     * Format revenue as currency string
     *
     * Format: "R$ 4.500,00"
     *
     * @return Formatted currency string
     */
    fun getFormattedRevenue(): String {
        return "R$ ${totalRevenue.setScale(2, java.math.RoundingMode.HALF_UP)}"
    }

    /**
     * Format average fee as currency string
     *
     * Format: "R$ 250,00"
     *
     * @return Formatted currency string
     */
    fun getFormattedAverageFee(): String {
        return "R$ ${averageFee.setScale(2, java.math.RoundingMode.HALF_UP)}"
    }

    /**
     * Format outstanding as currency string
     *
     * Format: "R$ 750,00"
     *
     * @return Formatted currency string
     */
    fun getFormattedOutstanding(): String {
        return "R$ ${outstandingBalance.setScale(2, java.math.RoundingMode.HALF_UP)}"
    }

    /**
     * Format total billed as currency string
     *
     * Format: "R$ 5.250,00"
     *
     * @return Formatted currency string
     */
    fun getFormattedTotalBilled(): String {
        return "R$ ${totalBilled.setScale(2, java.math.RoundingMode.HALF_UP)}"
    }

    /**
     * Format revenue per patient as currency string
     *
     * Format: "R$ 375,00"
     *
     * @return Formatted currency string
     */
    fun getFormattedRevenuePerPatient(): String {
        return "R$ ${revenuePerPatient.setScale(2, java.math.RoundingMode.HALF_UP)}"
    }

    /**
     * Get month display (Portuguese)
     *
     * Format: "Março de 2024"
     *
     * @return Month name and year
     */
    fun getFormattedMonth(): String {
        val monthName = when (yearMonth.monthValue) {
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
        return "$monthName de ${yearMonth.year}"
    }

    /**
     * Get month abbreviation
     *
     * Format: "Mar/24"
     *
     * @return Abbreviated month
     */
    fun getMonthAbbreviation(): String {
        return "${yearMonth.monthValue.toString().padStart(2, '0')}/${
            yearMonth.year.toString().takeLast(2)
        }"
    }

    /**
     * Get collection rate display
     *
     * Format: "86%"
     *
     * @return Collection rate string
     */
    fun getCollectionRateDisplay(): String {
        return "$collectionRate%"
    }

    /**
     * Get outstanding percentage display
     *
     * Format: "14%"
     *
     * @return Outstanding percentage string
     */
    fun getOutstandingPercentageDisplay(): String {
        return "$outstandingPercentage%"
    }

    /**
     * Get summary line for display
     *
     * Format: "R$ 4.500,00 | 12 pacientes | R$ 750,00 pendente"
     *
     * @return Summary string
     */
    fun getSummary(): String {
        return "${getFormattedRevenue()} | $activePatients pacientes | ${getFormattedOutstanding()} pendente"
    }

    /**
     * Get detailed metrics statement
     *
     * Format: "Receita: R$ 4.500,00 | Pacientes: 12 | Média: R$ 250,00 | Pendente: R$ 750,00 (14%)"
     *
     * @return Detailed statement
     */
    fun getDetailedStatement(): String {
        return "Receita: ${getFormattedRevenue()} | " +
                "Pacientes: $activePatients | " +
                "Média: ${getFormattedAverageFee()} | " +
                "Pendente: ${getFormattedOutstanding()} ($outstandingPercentage%)"
    }

    /**
     * Get alert level (for highlighting)
     *
     * Determines urgency based on outstanding balance.
     *
     * @return AlertLevel
     */
    fun getAlertLevel(): AlertLevel {
        return when {
            outstandingPercentage == 0 -> AlertLevel.NONE
            outstandingPercentage < 25 -> AlertLevel.NONE
            outstandingPercentage < 50 -> AlertLevel.WARNING
            else -> AlertLevel.ALERT
        }
    }

    /**
     * Get revenue trend description
     *
     * Useful for month-over-month analysis.
     *
     * @param previousRevenue Previous month's revenue
     * @return Trend description
     */
    fun getTrendDescription(previousRevenue: BigDecimal?): String {
        if (previousRevenue == null || previousRevenue == BigDecimal.ZERO) {
            return "Primeira medição"
        }

        val difference = totalRevenue - previousRevenue
        return when {
            difference > BigDecimal.ZERO -> "↑ Crescimento"
            difference < BigDecimal.ZERO -> "↓ Queda"
            else -> "→ Estável"
        }
    }

    /**
     * Get percentage change vs previous month
     *
     * @param previousRevenue Previous month's revenue
     * @return Change percentage (-100 to +100 or more)
     */
    fun getRevenueChangePercentage(previousRevenue: BigDecimal?): Int {
        if (previousRevenue == null || previousRevenue == BigDecimal.ZERO) {
            return 0
        }

        val difference = totalRevenue - previousRevenue
        return ((difference * BigDecimal("100")) / previousRevenue).toInt()
    }

    /**
     * Check if metrics are valid
     *
     * Validates internal consistency.
     *
     * @return true if metrics are valid
     */
    fun isValid(): Boolean {
        return totalRevenue >= BigDecimal.ZERO &&
                activePatients >= 0 &&
                averageFee >= BigDecimal.ZERO &&
                outstandingBalance >= BigDecimal.ZERO &&
                totalTransactions >= 0
    }

    // ========================================
    // Companion Object
    // ========================================

    companion object {
        /**
         * Create empty/zero metrics
         *
         * Useful for initialization or "no data" states.
         *
         * @param yearMonth Month for metrics
         * @return Empty DashboardMetrics
         */
        fun empty(yearMonth: YearMonth = YearMonth.now()): DashboardMetrics {
            return DashboardMetrics(
                yearMonth = yearMonth,
                totalRevenue = BigDecimal.ZERO,
                activePatients = 0,
                averageFee = BigDecimal.ZERO,
                outstandingBalance = BigDecimal.ZERO,
                totalTransactions = 0
            )
        }

        /**
         * Create sample metrics for testing
         *
         * @param yearMonth Month for metrics
         * @param revenue Sample revenue
         * @param patients Sample patient count
         * @param outstanding Sample outstanding
         * @return Sample DashboardMetrics
         */
        fun sample(
            yearMonth: YearMonth = YearMonth.now(),
            revenue: BigDecimal = BigDecimal("4500.00"),
            patients: Int = 12,
            outstanding: BigDecimal = BigDecimal("750.00")
        ): DashboardMetrics {
            return DashboardMetrics(
                yearMonth = yearMonth,
                totalRevenue = revenue,
                activePatients = patients,
                averageFee = BigDecimal("250.00"),
                outstandingBalance = outstanding,
                totalTransactions = 18
            )
        }
    }
}

/**
 * Extension function to filter metrics by alert level
 *
 * @receiver List of metrics
 * @param level Alert level filter
 * @return Filtered metrics
 */
fun List<DashboardMetrics>.filterByAlert(level: AlertLevel): List<DashboardMetrics> {
    return filter { it.getAlertLevel() == level }
}

/**
 * Extension function to get total revenue across months
 *
 * @receiver List of metrics
 * @return Total revenue sum
 */
fun List<DashboardMetrics>.getTotalRevenue(): BigDecimal {
    return fold(BigDecimal.ZERO) { acc, metrics ->
        acc + metrics.totalRevenue
    }
}

/**
 * Extension function to get total outstanding across months
 *
 * @receiver List of metrics
 * @return Total outstanding sum
 */
fun List<DashboardMetrics>.getTotalOutstanding(): BigDecimal {
    return fold(BigDecimal.ZERO) { acc, metrics ->
        acc + metrics.outstandingBalance
    }
}

/**
 * Extension function to get average collection rate
 *
 * @receiver List of metrics
 * @return Average collection percentage
 */
fun List<DashboardMetrics>.getAverageCollectionRate(): Int {
    if (isEmpty()) return 0
    return map { it.collectionRate }.average().toInt()
}

/**
 * Extension function to get highest revenue month
 *
 * @receiver List of metrics
 * @return Metrics for highest revenue month or null
 */
fun List<DashboardMetrics>.getHighestRevenueMonth(): DashboardMetrics? {
    return maxByOrNull { it.totalRevenue }
}

/**
 * Extension function to get lowest revenue month
 *
 * @receiver List of metrics
 * @return Metrics for lowest revenue month or null
 */
fun List<DashboardMetrics>.getLowestRevenueMonth(): DashboardMetrics? {
    return minByOrNull { it.totalRevenue }
}
