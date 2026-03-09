package com.psychologist.financial.services

import com.psychologist.financial.domain.models.Payment
import com.psychologist.financial.domain.models.PatientBalance
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/**
 * Balance calculator service
 *
 * Calculates financial balance metrics for patients.
 * Supports various aggregation levels: total, by period, by month, by week.
 *
 * Responsibilities:
 * - Calculate balance metrics from payments
 * - Aggregate by status (PAID/PENDING)
 * - Track balance per patient
 * - Generate financial summaries
 * - Support different time periods for reporting
 *
 * Balance Definitions:
 * - Amount Due Now: SUM(amount) WHERE status = 'PAID' (received payments)
 * - Total Outstanding: SUM(amount) WHERE status = 'PENDING' (unpaid invoices)
 * - Total Received: SUM(amount) WHERE status = 'PAID' (same as due now, accounting view)
 * - Collection Rate: (paid_count / total_count) * 100%
 * - Overdue Amount: SUM(amount) WHERE status = 'PENDING' AND payment_date < today
 *
 * Example:
 * ```kotlin
 * val calculator = BalanceCalculator()
 *
 * // Calculate total balance for patient
 * val balance = calculator.calculateBalance(payments)
 * println("Due Now: ${balance.amountDueNow}")           // R$ 1.500,00
 * println("Outstanding: ${balance.totalOutstanding}")   // R$ 750,00
 * println("Received: ${balance.totalReceived}")         // R$ 1.500,00
 *
 * // Calculate balance for a month
 * val monthBalance = calculator.calculateMonthlyBalance(
 *     payments = payments,
 *     month = YearMonth.of(2024, 3)
 * )
 * println("March received: ${monthBalance.totalReceived}")
 *
 * // Get balance summary
 * val summary = calculator.calculateBalanceSummary(payments)
 * println("Paid payments: ${summary.paidCount}")        // 5
 * println("Pending payments: ${summary.pendingCount}")  // 2
 * println("Collection rate: ${summary.collectionRate}%") // 71%
 * println("Average payment: ${summary.averagePayment}") // R$ 214,29
 *
 * // Calculate by payment method
 * val transferTotal = calculator.calculateByMethod(
 *     payments = payments,
 *     method = Payment.METHOD_TRANSFER
 * )
 * println("Total via transfer: $transferTotal")
 * ```
 *
 * Performance Considerations:
 * - Filters payments in-memory (should be <10k)
 * - O(n) time complexity for single pass calculations
 * - Suitable for real-time UI updates
 * - Caching recommended for daily/weekly reports
 *
 * Accounting Notes:
 * - "Amount Due Now" = Total Received (payments received from patient)
 * - "Total Outstanding" = Invoices not yet paid
 * - For accounts receivable: Outstanding = Amount owed by patient
 * - For cash flow: Due Now = Cash received
 */
class BalanceCalculator {

    // ========================================
    // Total Balance Calculation
    // ========================================

    /**
     * Calculate total balance for list of payments
     *
     * Aggregates all payments by status to determine financial position.
     *
     * @param payments List of payments
     * @return PatientBalance with all metrics
     */
    fun calculateBalance(payments: List<Payment>): PatientBalance {
        // All payments are PAID — no pending concept
        val totalReceived = payments.fold(BigDecimal.ZERO) { acc, payment ->
            acc + payment.amount
        }

        return PatientBalance(
            amountDueNow = totalReceived,
            totalOutstanding = BigDecimal.ZERO,
            totalReceived = totalReceived,
            paidPaymentsCount = payments.size,
            pendingPaymentsCount = 0,
            totalPaymentsCount = payments.size
        )
    }

    /**
     * Calculate amount due now (total paid)
     *
     * Sum of all PAID payments.
     *
     * @param payments List of payments
     * @return Amount due now
     */
    fun calculateAmountDueNow(payments: List<Payment>): BigDecimal {
        return payments.fold(BigDecimal.ZERO) { acc, payment ->
            acc + payment.amount
        }
    }

    /**
     * Calculate total outstanding (sum of pending)
     *
     * Sum of all PENDING payments.
     *
     * @param payments List of payments
     * @return Total outstanding amount
     */
    fun calculateTotalOutstanding(payments: List<Payment>): BigDecimal {
        return BigDecimal.ZERO
    }

    /**
     * Calculate total amount (paid + pending)
     *
     * @param payments List of payments
     * @return Total amount
     */
    fun calculateTotal(payments: List<Payment>): BigDecimal {
        return payments.fold(BigDecimal.ZERO) { acc, payment ->
            acc + payment.amount
        }
    }

    /**
     * Calculate balance split (paid vs pending)
     *
     * Separates received from outstanding for comparison.
     *
     * @param payments List of payments
     * @return Pair of (amountDueNow, totalOutstanding)
     */
    fun calculateBalanceSplit(payments: List<Payment>): Pair<BigDecimal, BigDecimal> {
        val amountDueNow = calculateAmountDueNow(payments)
        val totalOutstanding = calculateTotalOutstanding(payments)
        return amountDueNow to totalOutstanding
    }

    // ========================================
    // Period-Based Calculations
    // ========================================

    /**
     * Calculate balance for specific date range
     *
     * @param payments List of payments
     * @param startDate Start date (inclusive)
     * @param endDate End date (inclusive)
     * @return PatientBalance within range
     */
    fun calculateRangeBalance(
        payments: List<Payment>,
        startDate: LocalDate,
        endDate: LocalDate
    ): PatientBalance {
        val filtered = payments.filter { it.paymentDate >= startDate && it.paymentDate <= endDate }
        return calculateBalance(filtered)
    }

    /**
     * Calculate balance for specific month
     *
     * @param payments List of payments
     * @param month Year-month to calculate
     * @return PatientBalance for month
     */
    fun calculateMonthlyBalance(
        payments: List<Payment>,
        month: YearMonth
    ): PatientBalance {
        val startDate = month.atDay(1)
        val endDate = month.atEndOfMonth()
        return calculateRangeBalance(payments, startDate, endDate)
    }

    /**
     * Calculate balance for specific week
     *
     * Week defined as Monday-Sunday.
     *
     * @param payments List of payments
     * @param date Any date within the week
     * @return PatientBalance for that week
     */
    fun calculateWeeklyBalance(
        payments: List<Payment>,
        date: LocalDate = LocalDate.now()
    ): PatientBalance {
        // Find Monday of week containing date
        val monday = date.minusDays(date.dayOfWeek.value.toLong() - 1)
        val sunday = monday.plusDays(6)
        return calculateRangeBalance(payments, monday, sunday)
    }

    /**
     * Calculate balance for last N days
     *
     * @param payments List of payments
     * @param days Number of days to look back
     * @return PatientBalance in last N days
     */
    fun calculateLastNDaysBalance(
        payments: List<Payment>,
        days: Int = 30
    ): PatientBalance {
        val startDate = LocalDate.now().minusDays(days.toLong())
        return calculateRangeBalance(payments, startDate, LocalDate.now())
    }

    /**
     * Calculate current month balance
     *
     * @param payments List of payments
     * @return PatientBalance for current month
     */
    fun calculateCurrentMonthBalance(payments: List<Payment>): PatientBalance {
        return calculateMonthlyBalance(payments, YearMonth.now())
    }

    /**
     * Calculate last 30 days balance
     *
     * @param payments List of payments
     * @return PatientBalance for last 30 days
     */
    fun calculateLast30DaysBalance(payments: List<Payment>): PatientBalance {
        return calculateLastNDaysBalance(payments, 30)
    }

    // ========================================
    // Monthly Breakdown
    // ========================================

    /**
     * Get balance breakdown by month
     *
     * Useful for monthly financial reporting.
     *
     * @param payments List of payments
     * @return Map of month to balance metrics
     */
    fun calculateMonthlyBreakdown(
        payments: List<Payment>
    ): Map<YearMonth, PatientBalance> {
        return payments
            .groupBy { YearMonth.from(it.paymentDate) }
            .mapValues { (_, monthPayments) ->
                calculateBalance(monthPayments)
            }
            .toSortedMap()
    }

    /**
     * Get amount due by month
     *
     * @param payments List of payments
     * @return Map of month to amount due
     */
    fun calculateMonthlyAmountDueBreakdown(
        payments: List<Payment>
    ): Map<YearMonth, BigDecimal> {
        return payments
            .groupBy { YearMonth.from(it.paymentDate) }
            .mapValues { (_, monthPayments) ->
                monthPayments.fold(BigDecimal.ZERO) { acc, payment ->
                    acc + payment.amount
                }
            }
            .toSortedMap()
    }

    /**
     * Get outstanding by month
     *
     * Always empty — no pending payments in new model.
     *
     * @param payments List of payments
     * @return Empty map
     */
    @Suppress("UNUSED_PARAMETER")
    fun calculateMonthlyOutstandingBreakdown(
        payments: List<Payment>
    ): Map<YearMonth, BigDecimal> {
        return emptyMap()
    }

    // ========================================
    // By Payment Method
    // ========================================

    /**
     * Calculate total by payment method
     *
     * @param payments List of payments
     * @param method Payment method filter
     * @return Total amount for method
     */
    fun calculateByMethod(
        @Suppress("UNUSED_PARAMETER") payments: List<Payment>,
        @Suppress("UNUSED_PARAMETER") method: String
    ): BigDecimal {
        return BigDecimal.ZERO
    }

    /**
     * Get breakdown by payment method
     *
     * paymentMethod field removed in v3 migration — returns empty map.
     *
     * @param payments List of payments
     * @return Empty map
     */
    @Suppress("UNUSED_PARAMETER")
    fun calculateMethodBreakdown(payments: List<Payment>): Map<String, BigDecimal> {
        return emptyMap()
    }

    /**
     * Get breakdown by method and status
     *
     * paymentMethod and status removed in v3 migration — returns empty map.
     *
     * @param payments List of payments
     * @return Empty map
     */
    @Suppress("UNUSED_PARAMETER")
    fun calculateMethodStatusBreakdown(
        payments: List<Payment>
    ): Map<String, Pair<BigDecimal, BigDecimal>> {
        return emptyMap()
    }

    // ========================================
    // Statistics & Metrics
    // ========================================

    /**
     * Calculate balance summary
     *
     * Provides comprehensive metrics for a collection of payments.
     *
     * @param payments List of payments
     * @return BalanceSummary with all metrics
     */
    fun calculateBalanceSummary(payments: List<Payment>): BalanceSummary {
        // All payments are PAID — no pending/overdue concept
        val totalReceived = payments.fold(BigDecimal.ZERO) { acc, payment ->
            acc + payment.amount
        }
        val totalOutstanding = BigDecimal.ZERO
        val totalOverdue = BigDecimal.ZERO

        val paidCount = payments.size
        val pendingCount = 0
        val totalCount = payments.size

        val averagePayment = if (totalCount > 0) {
            (totalReceived + totalOutstanding) / BigDecimal(totalCount)
        } else {
            BigDecimal.ZERO
        }

        val minPayment = payments.minOfOrNull { it.amount } ?: BigDecimal.ZERO
        val maxPayment = payments.maxOfOrNull { it.amount } ?: BigDecimal.ZERO

        val collectionRate = if (totalCount > 0) {
            (paidCount * 100) / totalCount
        } else {
            0
        }

        return BalanceSummary(
            totalReceived = totalReceived,
            totalOutstanding = totalOutstanding,
            totalOverdue = totalOverdue,
            paidCount = paidCount,
            pendingCount = pendingCount,
            overdueCount = 0,
            totalCount = totalCount,
            averagePayment = averagePayment,
            minPayment = minPayment,
            maxPayment = maxPayment,
            collectionRate = collectionRate
        )
    }

    /**
     * Calculate average payment amount
     *
     * @param payments List of payments
     * @return Average amount
     */
    fun calculateAveragePayment(payments: List<Payment>): BigDecimal {
        if (payments.isEmpty()) return BigDecimal.ZERO
        return payments.fold(BigDecimal.ZERO) { acc, payment ->
            acc + payment.amount
        } / BigDecimal(payments.size)
    }

    /**
     * Calculate average amount due (PAID payments average)
     *
     * @param payments List of payments
     * @return Average paid amount
     */
    fun calculateAveragePaidAmount(payments: List<Payment>): BigDecimal {
        return calculateAveragePayment(payments)
    }

    /**
     * Calculate average outstanding (PENDING payments average)
     *
     * Always zero — no pending payments in new model.
     *
     * @param payments List of payments
     * @return BigDecimal.ZERO
     */
    @Suppress("UNUSED_PARAMETER")
    fun calculateAveragePendingAmount(payments: List<Payment>): BigDecimal {
        return BigDecimal.ZERO
    }

    /**
     * Calculate overdue amount
     *
     * Always zero — no pending payments in new model.
     *
     * @param payments List of payments
     * @return BigDecimal.ZERO
     */
    @Suppress("UNUSED_PARAMETER")
    fun calculateOverdueAmount(payments: List<Payment>): BigDecimal {
        return BigDecimal.ZERO
    }

    /**
     * Calculate collection rate percentage
     *
     * (paid_count / total_count) * 100
     *
     * @param payments List of payments
     * @return Collection rate (0-100)
     */
    fun calculateCollectionRate(payments: List<Payment>): Int {
        return if (payments.isEmpty()) 0 else 100
    }

    /**
     * Calculate days sales outstanding (DSO)
     *
     * Average days from invoice to payment.
     * Approximation: (Outstanding / Daily Revenue) or similar
     *
     * @param payments List of payments
     * @param daysInPeriod Period to analyze (e.g., 30 for monthly)
     * @return Estimated DSO
     */
    fun calculateDaysSalesOutstanding(
        payments: List<Payment>,
        daysInPeriod: Int = 30
    ): Int {
        val totalOutstanding = calculateTotalOutstanding(payments)
        val totalReceived = calculateAmountDueNow(payments)

        if (totalReceived <= BigDecimal.ZERO) return 0

        val dailyAverage = totalReceived / BigDecimal(daysInPeriod)
        if (dailyAverage <= BigDecimal.ZERO) return 0

        return (totalOutstanding / dailyAverage).toInt()
    }
}

/**
 * Balance summary data class
 *
 * Complete financial metrics for a set of payments.
 */
data class BalanceSummary(
    val totalReceived: BigDecimal,
    val totalOutstanding: BigDecimal,
    val totalOverdue: BigDecimal,
    val paidCount: Int,
    val pendingCount: Int,
    val overdueCount: Int,
    val totalCount: Int,
    val averagePayment: BigDecimal,
    val minPayment: BigDecimal,
    val maxPayment: BigDecimal,
    val collectionRate: Int
) {
    /**
     * Total amount (received + outstanding)
     */
    val totalAmount: BigDecimal
        get() = totalReceived + totalOutstanding

    /**
     * Percentage of outstanding vs total
     */
    val outstandingPercentage: Int
        get() = if (totalCount > 0) (pendingCount * 100) / totalCount else 0

    /**
     * Has outstanding balance
     */
    val hasOutstandingBalance: Boolean
        get() = totalOutstanding > BigDecimal.ZERO

    /**
     * Has overdue payments
     */
    val hasOverduePayments: Boolean
        get() = totalOverdue > BigDecimal.ZERO
}
