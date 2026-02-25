package com.psychologist.financial.domain.models

import java.math.BigDecimal

/**
 * Patient balance domain model
 *
 * Represents financial balance snapshot for a patient.
 * Contains key metrics for financial status and reporting.
 *
 * Responsibilities:
 * - Represent patient's financial position
 * - Provide financial status indicators
 * - Support dashboard and reporting views
 * - Enable financial analysis and alerts
 * - Track collection metrics
 *
 * Balance Definitions:
 * - Amount Due Now: Total amount received from patient (PAID payments)
 * - Total Outstanding: Total amount pending from patient (PENDING payments)
 * - Total Received: Same as Amount Due Now (accounting perspective)
 * - Total Balance: Amount Due Now + Total Outstanding
 *
 * Metrics:
 * - Paid Count: Number of completed payments
 * - Pending Count: Number of unpaid invoices
 * - Total Count: Total payments
 * - Collection Rate: Paid / Total percentage
 *
 * Usage:
 * ```kotlin
 * val balance = PatientBalance(
 *     amountDueNow = BigDecimal("1500.00"),
 *     totalOutstanding = BigDecimal("750.00"),
 *     totalReceived = BigDecimal("1500.00"),
 *     paidPaymentsCount = 5,
 *     pendingPaymentsCount = 2,
 *     totalPaymentsCount = 7
 * )
 *
 * println("Status: ${balance.getStatusLabel()}")      // "Parcialmente Quitado"
 * println("Total: ${balance.formattedTotal}")         // "R$ 2.250,00"
 * println("Outstanding: ${balance.formattedOutstanding}") // "R$ 750,00"
 * println("Collection: ${balance.collectionPercentage}%") // "71%"
 *
 * when (balance.status) {
 *     BalanceStatus.FULLY_PAID -> showGreen()
 *     BalanceStatus.PARTIALLY_PAID -> showYellow()
 *     BalanceStatus.NOT_PAID -> showRed()
 * }
 * ```
 *
 * Display Properties:
 * - formatteAmount: Localized currency format (R$ x.xxx,xx)
 * - status: Financial status (FULLY_PAID, PARTIALLY_PAID, NOT_PAID)
 * - statusLabel: Portuguese label (Quitado, Parcialmente Quitado, Pendente)
 * - collectionPercentage: 0-100 value
 * - hasOutstandingBalance: Boolean indicator
 * - isFullyPaid: True if no pending payments
 *
 * @property amountDueNow Total amount received (PAID payments)
 * @property totalOutstanding Total amount pending (PENDING payments)
 * @property totalReceived Same as amountDueNow (accounting view)
 * @property paidPaymentsCount Number of paid invoices
 * @property pendingPaymentsCount Number of pending invoices
 * @property totalPaymentsCount Total invoices
 */
data class PatientBalance(
    /**
     * Amount due now (total received from patient)
     *
     * Sum of all PAID payments.
     * Accounting perspective: Cash received.
     */
    val amountDueNow: BigDecimal,

    /**
     * Total outstanding (amount owed by patient)
     *
     * Sum of all PENDING payments.
     * Accounting perspective: Accounts receivable.
     */
    val totalOutstanding: BigDecimal,

    /**
     * Total received (same as amountDueNow)
     *
     * Used interchangeably with amountDueNow.
     * Clarifies accounting intent: this is cash received.
     */
    val totalReceived: BigDecimal,

    /**
     * Number of paid payments
     *
     * Count of PAID status invoices.
     */
    val paidPaymentsCount: Int = 0,

    /**
     * Number of pending payments
     *
     * Count of PENDING status invoices.
     */
    val pendingPaymentsCount: Int = 0,

    /**
     * Total number of payments
     *
     * paidPaymentsCount + pendingPaymentsCount
     */
    val totalPaymentsCount: Int = 0
) {

    // ========================================
    // Computed Properties
    // ========================================

    /**
     * Total balance (due + outstanding)
     *
     * Sum of all amounts (paid and pending).
     *
     * @return Total balance
     */
    val totalBalance: BigDecimal
        get() = amountDueNow + totalOutstanding

    /**
     * Check if fully paid
     *
     * @return true if no pending payments
     */
    val isFullyPaid: Boolean
        get() = totalOutstanding <= BigDecimal.ZERO && pendingPaymentsCount == 0

    /**
     * Check if partially paid
     *
     * @return true if has both paid and pending
     */
    val isPartialllyPaid: Boolean
        get() = paidPaymentsCount > 0 && pendingPaymentsCount > 0

    /**
     * Check if not paid
     *
     * @return true if all payments pending
     */
    val isNotPaid: Boolean
        get() = paidPaymentsCount == 0 && pendingPaymentsCount > 0

    /**
     * Check if has outstanding balance
     *
     * @return true if totalOutstanding > 0
     */
    val hasOutstandingBalance: Boolean
        get() = totalOutstanding > BigDecimal.ZERO

    /**
     * Collection percentage (0-100)
     *
     * Percentage of paid vs total payments.
     *
     * @return 0-100 or 0 if no payments
     */
    val collectionPercentage: Int
        get() = if (totalPaymentsCount > 0) {
            (paidPaymentsCount * 100) / totalPaymentsCount
        } else {
            0
        }

    /**
     * Outstanding percentage (0-100)
     *
     * Percentage of pending vs total payments.
     *
     * @return 0-100 or 0 if no payments
     */
    val outstandingPercentage: Int
        get() = if (totalPaymentsCount > 0) {
            (pendingPaymentsCount * 100) / totalPaymentsCount
        } else {
            0
        }

    /**
     * Get financial status
     *
     * @return BalanceStatus enum
     */
    val status: BalanceStatus
        get() = when {
            isFullyPaid -> BalanceStatus.FULLY_PAID
            isPartialllyPaid -> BalanceStatus.PARTIALLY_PAID
            isNotPaid && totalPaymentsCount > 0 -> BalanceStatus.NOT_PAID
            else -> BalanceStatus.NO_PAYMENTS
        }

    // ========================================
    // Display Methods
    // ========================================

    /**
     * Format amount due as currency string
     *
     * Format: "R$ 1.500,00"
     *
     * @return Formatted currency string
     */
    fun getFormattedAmountDue(): String {
        return "R$ ${amountDueNow.setScale(2, java.math.RoundingMode.HALF_UP)}"
    }

    /**
     * Format outstanding as currency string
     *
     * Format: "R$ 750,00"
     *
     * @return Formatted currency string
     */
    fun getFormattedOutstanding(): String {
        return "R$ ${totalOutstanding.setScale(2, java.math.RoundingMode.HALF_UP)}"
    }

    /**
     * Format total balance as currency string
     *
     * Format: "R$ 2.250,00"
     *
     * @return Formatted currency string
     */
    fun getFormattedTotal(): String {
        return "R$ ${totalBalance.setScale(2, java.math.RoundingMode.HALF_UP)}"
    }

    /**
     * Get status label (Portuguese)
     *
     * @return Status label ("Quitado", "Parcialmente Quitado", "Pendente", "Sem Pagamentos")
     */
    fun getStatusLabel(): String {
        return when (status) {
            BalanceStatus.FULLY_PAID -> "Quitado"
            BalanceStatus.PARTIALLY_PAID -> "Parcialmente Quitado"
            BalanceStatus.NOT_PAID -> "Pendente"
            BalanceStatus.NO_PAYMENTS -> "Sem Pagamentos"
        }
    }

    /**
     * Get status description (Portuguese)
     *
     * More detailed description of financial status.
     *
     * @return Description string
     */
    fun getStatusDescription(): String {
        return when (status) {
            BalanceStatus.FULLY_PAID -> "Todos os pagamentos recebidos"
            BalanceStatus.PARTIALLY_PAID -> "$paidPaymentsCount de $totalPaymentsCount pagamentos recebidos"
            BalanceStatus.NOT_PAID -> "Nenhum pagamento recebido"
            BalanceStatus.NO_PAYMENTS -> "Sem registros de pagamento"
        }
    }

    /**
     * Get balance summary string
     *
     * Format: "Recebido: R$ 1.500,00 | Pendente: R$ 750,00"
     *
     * @return Summary string
     */
    fun getSummary(): String {
        return "Recebido: ${getFormattedAmountDue()} | Pendente: ${getFormattedOutstanding()}"
    }

    /**
     * Get collection rate display
     *
     * Format: "71%"
     *
     * @return Collection rate string
     */
    fun getCollectionRateDisplay(): String {
        return "$collectionPercentage%"
    }

    /**
     * Get outstanding rate display
     *
     * Format: "29%"
     *
     * @return Outstanding rate string
     */
    fun getOutstandingRateDisplay(): String {
        return "$outstandingPercentage%"
    }

    /**
     * Get detailed balance statement
     *
     * Format: "Total: R$ 2.250,00 | Recebido: R$ 1.500,00 (67%) | Pendente: R$ 750,00 (33%)"
     *
     * @return Detailed statement
     */
    fun getDetailedStatement(): String {
        return "Total: ${getFormattedTotal()} | " +
                "Recebido: ${getFormattedAmountDue()} ($collectionPercentage%) | " +
                "Pendente: ${getFormattedOutstanding()} ($outstandingPercentage%)"
    }

    /**
     * Get alert status (for highlighting)
     *
     * @return AlertLevel based on financial status
     */
    fun getAlertLevel(): AlertLevel {
        return when {
            isFullyPaid -> AlertLevel.NONE
            isPartialllyPaid -> AlertLevel.WARNING
            hasOutstandingBalance -> AlertLevel.ALERT
            else -> AlertLevel.NONE
        }
    }

    // ========================================
    // Validation Methods
    // ========================================

    /**
     * Check if balance is valid
     *
     * Validates internal consistency:
     * - Non-negative amounts
     * - Non-negative counts
     * - Count sum equals total
     *
     * @return true if balance is valid
     */
    fun isValid(): Boolean {
        return amountDueNow >= BigDecimal.ZERO &&
                totalOutstanding >= BigDecimal.ZERO &&
                totalReceived >= BigDecimal.ZERO &&
                paidPaymentsCount >= 0 &&
                pendingPaymentsCount >= 0 &&
                totalPaymentsCount >= 0 &&
                (paidPaymentsCount + pendingPaymentsCount == totalPaymentsCount || totalPaymentsCount == 0)
    }

    // ========================================
    // Companion Object
    // ========================================

    companion object {
        /**
         * Create zero/empty balance
         *
         * Useful for initialization or "no data" states.
         *
         * @return Empty PatientBalance
         */
        fun empty(): PatientBalance {
            return PatientBalance(
                amountDueNow = BigDecimal.ZERO,
                totalOutstanding = BigDecimal.ZERO,
                totalReceived = BigDecimal.ZERO,
                paidPaymentsCount = 0,
                pendingPaymentsCount = 0,
                totalPaymentsCount = 0
            )
        }

        /**
         * Create sample balance for testing
         *
         * @param amountDue Sample amount due
         * @param outstanding Sample outstanding
         * @return Sample PatientBalance
         */
        fun sample(
            amountDue: BigDecimal = BigDecimal("1500.00"),
            outstanding: BigDecimal = BigDecimal("750.00")
        ): PatientBalance {
            return PatientBalance(
                amountDueNow = amountDue,
                totalOutstanding = outstanding,
                totalReceived = amountDue,
                paidPaymentsCount = 3,
                pendingPaymentsCount = 1,
                totalPaymentsCount = 4
            )
        }
    }
}

/**
 * Balance status enum
 *
 * Represents financial status of a patient.
 */
enum class BalanceStatus {
    /**
     * All payments received
     */
    FULLY_PAID,

    /**
     * Some payments received, some pending
     */
    PARTIALLY_PAID,

    /**
     * No payments received, all pending
     */
    NOT_PAID,

    /**
     * No payments recorded
     */
    NO_PAYMENTS
}

/**
 * Alert level for UI highlighting
 *
 * Indicates urgency of financial issue.
 */
enum class AlertLevel {
    /**
     * No issues
     */
    NONE,

    /**
     * Caution - some outstanding
     */
    WARNING,

    /**
     * Alert - significant outstanding or overdue
     */
    ALERT
}

/**
 * Extension function to format balance list
 *
 * @receiver List of balances
 * @return Formatted string
 */
fun List<PatientBalance>.getTotalBalance(): BigDecimal {
    return fold(BigDecimal.ZERO) { acc, balance ->
        acc + balance.totalBalance
    }
}

/**
 * Extension function to get total received
 *
 * @receiver List of balances
 * @return Total received amount
 */
fun List<PatientBalance>.getTotalReceived(): BigDecimal {
    return fold(BigDecimal.ZERO) { acc, balance ->
        acc + balance.amountDueNow
    }
}

/**
 * Extension function to get total outstanding
 *
 * @receiver List of balances
 * @return Total outstanding amount
 */
fun List<PatientBalance>.getTotalOutstanding(): BigDecimal {
    return fold(BigDecimal.ZERO) { acc, balance ->
        acc + balance.totalOutstanding
    }
}

/**
 * Extension function to get average collection rate
 *
 * @receiver List of balances
 * @return Average collection percentage
 */
fun List<PatientBalance>.getAverageCollectionRate(): Int {
    if (isEmpty()) return 0
    return map { it.collectionPercentage }.average().toInt()
}

/**
 * Extension function to filter by status
 *
 * @receiver List of balances
 * @param status Filter status
 * @return Filtered list
 */
fun List<PatientBalance>.filterByStatus(status: BalanceStatus): List<PatientBalance> {
    return filter { it.status == status }
}

/**
 * Extension function to filter with outstanding
 *
 * @receiver List of balances
 * @return Balances with outstanding amount
 */
fun List<PatientBalance>.getWithOutstanding(): List<PatientBalance> {
    return filter { it.hasOutstandingBalance }
}

/**
 * Extension function to filter fully paid
 *
 * @receiver List of balances
 * @return Fully paid balances
 */
fun List<PatientBalance>.getFullyPaid(): List<PatientBalance> {
    return filter { it.isFullyPaid }
}

/**
 * Extension function to filter not paid
 *
 * @receiver List of balances
 * @return Not paid balances
 */
fun List<PatientBalance>.getNotPaid(): List<PatientBalance> {
    return filter { it.isNotPaid }
}
