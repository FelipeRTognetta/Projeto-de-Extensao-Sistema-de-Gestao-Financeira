package com.psychologist.financial.domain.usecases

import com.psychologist.financial.data.repositories.PaymentRepository
import com.psychologist.financial.domain.models.Payment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Get patient payments use case
 *
 * Retrieves and presents payments for a patient.
 * Provides multiple views: all, by status (PAID/PENDING), by date range.
 * Supports both sync and reactive (Flow) APIs for UI binding.
 *
 * Responsibilities:
 * - Retrieve payments from repository
 * - Filter by status (PAID/PENDING)
 * - Filter by date range
 * - Calculate balance metrics (amount due, outstanding)
 * - Provide reactive streams for UI binding
 * - Count and statistics
 *
 * Balance Calculation:
 * - Amount Due Now: SUM(amount) WHERE status = 'PAID'
 * - Total Outstanding: SUM(amount) WHERE status = 'PENDING'
 * - Total Received: SUM(amount) WHERE status = 'PAID'
 *
 * Sorting Strategy:
 * - Default: Most recent first (payment_date DESC)
 * - Chronological: Oldest first (payment_date ASC)
 * - By status: Grouped by PAID/PENDING
 *
 * Usage Examples:
 * ```kotlin
 * val useCase = GetPatientPaymentsUseCase(paymentRepository)
 *
 * // Get all payments (sync)
 * val payments = useCase.execute(patientId = 1L)
 *
 * // Get payments as reactive stream
 * useCase.executeFlow(patientId = 1L).collect { payments ->
 *     updatePaymentList(payments)
 * }
 *
 * // Get only paid payments
 * val paidPayments = useCase.getPaidPayments(patientId = 1L)
 *
 * // Get only pending payments (outstanding)
 * val pendingPayments = useCase.getPendingPayments(patientId = 1L)
 *
 * // Get balance metrics
 * val amountDueNow = useCase.getAmountDueNow(patientId = 1L)
 * val totalOutstanding = useCase.getTotalOutstanding(patientId = 1L)
 *
 * // Get payments for specific month
 * val monthPayments = useCase.getByDateRange(
 *     patientId = 1L,
 *     startDate = LocalDate.of(2024, 3, 1),
 *     endDate = LocalDate.of(2024, 3, 31)
 * )
 * ```
 */
class GetPatientPaymentsUseCase(
    private val repository: PaymentRepository
) {

    // ========================================
    // Main Execution Methods
    // ========================================

    /**
     * Execute payment retrieval (sync)
     *
     * Gets all payments for patient, sorted most recent first.
     *
     * @param patientId Patient ID
     * @return List of payments (most recent first)
     */
    suspend fun execute(patientId: Long): List<Payment> {
        return repository.getByPatient(patientId)
    }

    /**
     * Execute payment retrieval (reactive)
     *
     * Returns Flow that automatically updates when payments change.
     * Use in UI layer for reactive binding.
     *
     * @param patientId Patient ID
     * @return Flow of payment list
     */
    fun executeFlow(patientId: Long): Flow<List<Payment>> {
        return repository.getByPatientFlow(patientId)
    }

    // ========================================
    // Filtered Queries by Status
    // ========================================

    /**
     * Get paid payments
     *
     * Useful for viewing received payments and calculating revenue.
     *
     * @param patientId Patient ID
     * @return Paid payments (most recent first)
     */
    suspend fun getPaidPayments(patientId: Long): List<Payment> {
        return repository.getByPatientAndStatus(patientId, Payment.STATUS_PAID)
    }

    /**
     * Get paid payments as Flow (reactive)
     *
     * @param patientId Patient ID
     * @return Flow of paid payments
     */
    fun getPaidPaymentsFlow(patientId: Long): Flow<List<Payment>> {
        return repository.getByPatientAndStatusFlow(patientId, Payment.STATUS_PAID)
    }

    /**
     * Get pending payments (outstanding)
     *
     * Useful for viewing unpaid invoices and calculating outstanding balance.
     *
     * @param patientId Patient ID
     * @return Pending payments (most recent first)
     */
    suspend fun getPendingPayments(patientId: Long): List<Payment> {
        return repository.getByPatientAndStatus(patientId, Payment.STATUS_PENDING)
    }

    /**
     * Get pending payments as Flow (reactive)
     *
     * @param patientId Patient ID
     * @return Flow of pending payments
     */
    fun getPendingPaymentsFlow(patientId: Long): Flow<List<Payment>> {
        return repository.getByPatientAndStatusFlow(patientId, Payment.STATUS_PENDING)
    }

    /**
     * Get overdue pending payments
     *
     * Payments with status PENDING and payment_date in past.
     * Useful for showing delinquent accounts.
     *
     * @param patientId Patient ID
     * @return Overdue pending payments
     */
    suspend fun getOverduePayments(patientId: Long): List<Payment> {
        return repository.getPastDueByPatient(patientId).filter { it.isPending }
    }

    // ========================================
    // Filtered Queries by Date Range
    // ========================================

    /**
     * Get payments within date range
     *
     * Useful for monthly or weekly views and reporting.
     *
     * @param patientId Patient ID
     * @param startDate Start date (inclusive)
     * @param endDate End date (inclusive)
     * @return Payments in range (most recent first)
     */
    suspend fun getByDateRange(
        patientId: Long,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<Payment> {
        return repository.getByPatientAndDateRange(patientId, startDate, endDate)
    }

    /**
     * Get payments by status and date range
     *
     * Combines status and date filtering.
     *
     * @param patientId Patient ID
     * @param status Payment status
     * @param startDate Start date
     * @param endDate End date
     * @return Filtered payments
     */
    suspend fun getByStatusAndDateRange(
        patientId: Long,
        status: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<Payment> {
        return repository.getByPatientStatusAndDateRange(
            patientId,
            status,
            startDate,
            endDate
        )
    }

    /**
     * Get payments for current month
     *
     * @param patientId Patient ID
     * @return Payments this month
     */
    suspend fun getCurrentMonthPayments(patientId: Long): List<Payment> {
        val now = LocalDate.now()
        val startDate = now.withDayOfMonth(1)
        val endDate = now.withDayOfMonth(now.lengthOfMonth())
        return getByDateRange(patientId, startDate, endDate)
    }

    /**
     * Get payments for specific month
     *
     * @param patientId Patient ID
     * @param year Year
     * @param month Month (1-12)
     * @return Payments for month
     */
    suspend fun getMonthPayments(patientId: Long, year: Int, month: Int): List<Payment> {
        val startDate = LocalDate.of(year, month, 1)
        val endDate = startDate.withDayOfMonth(startDate.lengthOfMonth())
        return getByDateRange(patientId, startDate, endDate)
    }

    /**
     * Get paid payments for current month
     *
     * @param patientId Patient ID
     * @return Paid payments this month
     */
    suspend fun getCurrentMonthPaidPayments(patientId: Long): List<Payment> {
        val now = LocalDate.now()
        val startDate = now.withDayOfMonth(1)
        val endDate = now.withDayOfMonth(now.lengthOfMonth())
        return getByStatusAndDateRange(patientId, Payment.STATUS_PAID, startDate, endDate)
    }

    /**
     * Get paid payments for specific month
     *
     * @param patientId Patient ID
     * @param year Year
     * @param month Month (1-12)
     * @return Paid payments for month
     */
    suspend fun getMonthPaidPayments(patientId: Long, year: Int, month: Int): List<Payment> {
        val startDate = LocalDate.of(year, month, 1)
        val endDate = startDate.withDayOfMonth(startDate.lengthOfMonth())
        return getByStatusAndDateRange(patientId, Payment.STATUS_PAID, startDate, endDate)
    }

    /**
     * Get recent payments for patient
     *
     * @param patientId Patient ID
     * @param limit Number of recent payments (default 10)
     * @return Recent payments
     */
    suspend fun getRecentPayments(patientId: Long, limit: Int = 10): List<Payment> {
        return repository.getRecentByPatient(patientId, limit)
    }

    // ========================================
    // Balance Calculation & Metrics
    // ========================================

    /**
     * Get amount due now
     *
     * Sum of all PAID payments (accounting perspective).
     *
     * @param patientId Patient ID
     * @return Amount due now
     */
    suspend fun getAmountDueNow(patientId: Long): BigDecimal {
        return repository.getAmountDueNow(patientId)
    }

    /**
     * Get amount due now as reactive stream
     *
     * @param patientId Patient ID
     * @return Flow of amount due
     */
    fun getAmountDueNowFlow(patientId: Long): Flow<BigDecimal> {
        return repository.getTotalAmountPaidFlow(patientId)
    }

    /**
     * Get total outstanding
     *
     * Sum of all PENDING payments.
     *
     * @param patientId Patient ID
     * @return Total outstanding amount
     */
    suspend fun getTotalOutstanding(patientId: Long): BigDecimal {
        return repository.getTotalOutstanding(patientId)
    }

    /**
     * Get total outstanding as reactive stream
     *
     * @param patientId Patient ID
     * @return Flow of total outstanding
     */
    fun getTotalOutstandingFlow(patientId: Long): Flow<BigDecimal> {
        return repository.getTotalOutstandingFlow(patientId)
    }

    /**
     * Get total amount paid
     *
     * @param patientId Patient ID
     * @return Total paid amount
     */
    suspend fun getTotalAmountPaid(patientId: Long): BigDecimal {
        return repository.getTotalAmountPaid(patientId)
    }

    /**
     * Get average payment amount
     *
     * @param patientId Patient ID
     * @return Average payment amount
     */
    suspend fun getAveragePaymentAmount(patientId: Long): BigDecimal {
        return repository.getAveragePaymentAmount(patientId)
    }

    /**
     * Get highest payment amount
     *
     * @param patientId Patient ID
     * @return Highest payment amount
     */
    suspend fun getMaxPaymentAmount(patientId: Long): BigDecimal {
        return repository.getMaxPaymentAmount(patientId)
    }

    /**
     * Get lowest payment amount
     *
     * @param patientId Patient ID
     * @return Lowest payment amount
     */
    suspend fun getMinPaymentAmount(patientId: Long): BigDecimal {
        return repository.getMinPaymentAmount(patientId)
    }

    /**
     * Get total revenue for date range
     *
     * @param patientId Patient ID
     * @param startDate Start date
     * @param endDate End date
     * @return Total revenue in range
     */
    suspend fun getTotalByDateRange(
        patientId: Long,
        startDate: LocalDate,
        endDate: LocalDate
    ): BigDecimal {
        return repository.getTotalByDateRange(patientId, startDate, endDate)
    }

    /**
     * Get total paid for current month
     *
     * @param patientId Patient ID
     * @return Total paid this month
     */
    suspend fun getCurrentMonthTotal(patientId: Long): BigDecimal {
        val now = LocalDate.now()
        val startDate = now.withDayOfMonth(1)
        val endDate = now.withDayOfMonth(now.lengthOfMonth())
        return getTotalByDateRange(patientId, startDate, endDate)
    }

    // ========================================
    // Statistics & Counting
    // ========================================

    /**
     * Get total payment count for patient
     *
     * @param patientId Patient ID
     * @return Number of payments
     */
    suspend fun getCount(patientId: Long): Int {
        return repository.countByPatient(patientId)
    }

    /**
     * Get count of paid payments
     *
     * @param patientId Patient ID
     * @return Number of paid payments
     */
    suspend fun getPaidCount(patientId: Long): Int {
        return repository.countByPatientAndStatus(patientId, Payment.STATUS_PAID)
    }

    /**
     * Get count of pending payments
     *
     * @param patientId Patient ID
     * @return Number of pending payments
     */
    suspend fun getPendingCount(patientId: Long): Int {
        return repository.countByPatientAndStatus(patientId, Payment.STATUS_PENDING)
    }

    /**
     * Get count of overdue payments
     *
     * @param patientId Patient ID
     * @return Number of overdue pending payments
     */
    suspend fun getOverdueCount(patientId: Long): Int {
        return getOverduePayments(patientId).size
    }

    /**
     * Get payment collection rate
     *
     * Percentage of paid vs total payments.
     *
     * @param patientId Patient ID
     * @return Percentage (0-100)
     */
    suspend fun getCollectionRate(patientId: Long): Int {
        val total = getCount(patientId)
        val paid = getPaidCount(patientId)
        return if (total > 0) (paid * 100) / total else 0
    }

    // ========================================
    // Existence Checks
    // ========================================

    /**
     * Check if patient has any payments
     *
     * @param patientId Patient ID
     * @return true if patient has payments
     */
    suspend fun hasPayments(patientId: Long): Boolean {
        return getCount(patientId) > 0
    }

    /**
     * Check if patient has pending payments
     *
     * @param patientId Patient ID
     * @return true if patient has outstanding balance
     */
    suspend fun hasPendingPayments(patientId: Long): Boolean {
        return getPendingCount(patientId) > 0
    }

    /**
     * Check if patient has overdue payments
     *
     * @param patientId Patient ID
     * @return true if patient has overdue pending payments
     */
    suspend fun hasOverduePayments(patientId: Long): Boolean {
        return getOverdueCount(patientId) > 0
    }

    // ========================================
    // Grouped/Formatted Results
    // ========================================

    /**
     * Get payments grouped by status
     *
     * Separates paid from pending payments.
     *
     * @param patientId Patient ID
     * @return Object with paid and pending lists
     */
    suspend fun getGroupedByStatus(patientId: Long): PaymentsByStatus {
        val payments = execute(patientId)
        return PaymentsByStatus(
            paid = payments.filter { it.isPaid },
            pending = payments.filter { it.isPending }
        )
    }

    /**
     * Get payments with balance metrics
     *
     * @param patientId Patient ID
     * @return Object with payments and balance information
     */
    suspend fun getWithBalance(patientId: Long): PaymentsWithBalance {
        val payments = execute(patientId)
        return PaymentsWithBalance(
            payments = payments,
            totalAmountPaid = getTotalAmountPaid(patientId),
            totalOutstanding = getTotalOutstanding(patientId),
            amountDueNow = getAmountDueNow(patientId),
            paidCount = getPaidCount(patientId),
            pendingCount = getPendingCount(patientId),
            overdueCount = getOverdueCount(patientId)
        )
    }

    // ========================================
    // Reactive Statistics
    // ========================================

    /**
     * Get count as reactive stream
     *
     * @param patientId Patient ID
     * @return Flow of payment count
     */
    fun getCountFlow(patientId: Long): Flow<Int> {
        return executeFlow(patientId).map { it.size }
    }

    /**
     * Get total amount as reactive stream
     *
     * @param patientId Patient ID
     * @return Flow of total amount
     */
    fun getTotalAmountFlow(patientId: Long): Flow<BigDecimal> {
        return executeFlow(patientId).map { payments ->
            payments.fold(BigDecimal.ZERO) { acc, payment ->
                acc + payment.amount
            }
        }
    }

    /**
     * Get paid payments count as reactive stream
     *
     * @param patientId Patient ID
     * @return Flow of paid count
     */
    fun getPaidCountFlow(patientId: Long): Flow<Int> {
        return executeFlow(patientId).map { it.filter { p -> p.isPaid }.size }
    }

    /**
     * Get pending payments count as reactive stream
     *
     * @param patientId Patient ID
     * @return Flow of pending count
     */
    fun getPendingCountFlow(patientId: Long): Flow<Int> {
        return executeFlow(patientId).map { it.filter { p -> p.isPending }.size }
    }
}

/**
 * Payments grouped by status
 */
data class PaymentsByStatus(
    val paid: List<Payment>,
    val pending: List<Payment>
) {
    val total: Int
        get() = paid.size + pending.size
}

/**
 * Payments with balance information
 */
data class PaymentsWithBalance(
    val payments: List<Payment>,
    val totalAmountPaid: BigDecimal,
    val totalOutstanding: BigDecimal,
    val amountDueNow: BigDecimal,
    val paidCount: Int,
    val pendingCount: Int,
    val overdueCount: Int
) {
    /**
     * Get payment collection percentage
     */
    val collectionPercentage: Int
        get() {
            val total = paidCount + pendingCount
            return if (total > 0) (paidCount * 100) / total else 0
        }

    /**
     * Check if has outstanding balance
     */
    val hasOutstandingBalance: Boolean
        get() = totalOutstanding > BigDecimal.ZERO

    /**
     * Check if has overdue payments
     */
    val hasOverduePayments: Boolean
        get() = overdueCount > 0
}
