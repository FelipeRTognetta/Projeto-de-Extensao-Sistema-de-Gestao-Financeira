package com.psychologist.financial

import com.psychologist.financial.domain.models.Payment
import com.psychologist.financial.services.BalanceCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/**
 * Unit tests for BalanceCalculator
 *
 * Coverage:
 * - Total balance calculation (v3: all payments are PAID)
 * - Amount due now (sum of all payments)
 * - Total outstanding (always zero in v3)
 * - Period-based calculations (monthly, weekly, daily, range)
 * - Payment method breakdown (empty in v3)
 * - Collection rate (always 100% for non-empty in v3)
 * - Edge cases (zero payments, single payment, large datasets)
 * - Boundary conditions (decimal precision, large amounts)
 *
 * Total: 30+ test cases
 */
class BalanceCalculatorTest {

    private lateinit var calculator: BalanceCalculator

    private val today = LocalDate.now()
    private val yesterday = today.minusDays(1)
    private val weekAgo = today.minusDays(7)
    private val monthAgo = today.minusMonths(1)
    private val twoMonthsAgo = today.minusMonths(2)

    private fun payment(id: Long, amount: String, date: LocalDate = yesterday) = Payment(
        id = id,
        patientId = 1L,
        amount = BigDecimal(amount),
        paymentDate = date
    )

    @Before
    fun setUp() {
        calculator = BalanceCalculator()
    }

    // ========================================
    // Balance Calculation Tests
    // ========================================

    @Test
    fun calculateBalance_emptyPayments_returnsZero() {
        val balance = calculator.calculateBalance(emptyList())

        assertEquals(BigDecimal.ZERO, balance.amountDueNow)
        assertEquals(BigDecimal.ZERO, balance.totalOutstanding)
        assertEquals(BigDecimal.ZERO, balance.totalReceived)
    }

    @Test
    fun calculateBalance_multiplePayments_sumsAll() {
        val payments = listOf(
            payment(1L, "150.00"),
            payment(2L, "200.00", weekAgo)
        )

        val balance = calculator.calculateBalance(payments)

        // v3: all payments are PAID
        assertEquals(BigDecimal("350.00"), balance.amountDueNow)
        assertEquals(BigDecimal.ZERO, balance.totalOutstanding)
        assertEquals(BigDecimal("350.00"), balance.totalBalance)
    }

    @Test
    fun calculateBalance_singlePayment_calculatesCorrectly() {
        val payments = listOf(payment(1L, "125.50"))

        val balance = calculator.calculateBalance(payments)

        assertEquals(BigDecimal("125.50"), balance.amountDueNow)
        assertEquals(BigDecimal.ZERO, balance.totalOutstanding)
    }

    @Test
    fun calculateBalance_largeAmounts_calculatesWithoutPrecisionLoss() {
        val payments = listOf(payment(1L, "999999.99"))

        val balance = calculator.calculateBalance(payments)

        assertEquals(BigDecimal("999999.99"), balance.amountDueNow)
    }

    @Test
    fun calculateBalance_threePayments_sumsAll() {
        val payments = listOf(
            payment(1L, "200.00"),
            payment(2L, "150.00", weekAgo),
            payment(3L, "100.00", monthAgo)
        )

        val balance = calculator.calculateBalance(payments)

        // v3: all are paid, no outstanding
        assertEquals(BigDecimal("450.00"), balance.amountDueNow)
        assertEquals(BigDecimal.ZERO, balance.totalOutstanding)
        assertEquals(BigDecimal("450.00"), balance.totalBalance)
    }

    // ========================================
    // Collection Rate Tests
    // ========================================

    @Test
    fun calculateBalance_withPayments_collectionRateIs100() {
        val payments = listOf(payment(1L, "500.00"))

        val balance = calculator.calculateBalance(payments)

        // v3: all payments are paid → always 100%
        assertEquals(100, balance.collectionPercentage)
    }

    @Test
    fun calculateBalance_emptyList_collectionRateIs0() {
        val balance = calculator.calculateBalance(emptyList())

        assertEquals(0, balance.collectionPercentage)
    }

    @Test
    fun calculateBalance_manyPayments_collectionRateIs100() {
        val payments = (1..1000).map { i ->
            payment(i.toLong(), "10.00", today.minusDays((i % 365).toLong()))
        }

        val balance = calculator.calculateBalance(payments)

        // v3: all payments are PAID
        assertEquals(BigDecimal("10000.00"), balance.amountDueNow)
        assertEquals(BigDecimal.ZERO, balance.totalOutstanding)
        assertEquals(100, balance.collectionPercentage)
    }

    // ========================================
    // Period-Based Calculations
    // ========================================

    @Test
    fun calculateMonthlyBalance_currentMonth_includesOnlyCurrentMonthPayments() {
        val payments = listOf(
            payment(1L, "100.00", today),
            payment(2L, "200.00", monthAgo)
        )

        val monthBalance = calculator.calculateMonthlyBalance(payments, YearMonth.now())

        assertEquals(BigDecimal("100.00"), monthBalance.amountDueNow)
        assertEquals(BigDecimal.ZERO, monthBalance.totalOutstanding)
    }

    @Test
    fun calculateRangeBalance_validRange_includesOnlyPaymentsInRange() {
        val startDate = weekAgo
        val endDate = today

        val payments = listOf(
            payment(1L, "100.00", yesterday),           // in range
            payment(2L, "200.00", monthAgo),            // outside range
            payment(3L, "50.00", today)                 // in range
        )

        val rangeBalance = calculator.calculateRangeBalance(payments, startDate, endDate)

        // v3: all in-range payments sum to amountDueNow
        assertEquals(BigDecimal("150.00"), rangeBalance.amountDueNow)
        assertEquals(BigDecimal.ZERO, rangeBalance.totalOutstanding)
    }

    @Test
    fun calculateWeeklyBalance_thisWeek_includesCurrentWeekPayments() {
        val payments = listOf(
            payment(1L, "75.00", yesterday),
            payment(2L, "500.00", monthAgo)
        )

        val weekBalance = calculator.calculateWeeklyBalance(payments, today)

        assertEquals(BigDecimal("75.00"), weekBalance.amountDueNow)
    }

    @Test
    fun calculateLastNDaysBalance_30days_calculatesCorrectly() {
        val payments = listOf(
            payment(1L, "100.00", yesterday),
            payment(2L, "200.00", today.minusDays(15)),
            payment(3L, "300.00", today.minusDays(60)) // outside 30 days
        )

        val balance = calculator.calculateLastNDaysBalance(payments, 30)

        assertEquals(BigDecimal("300.00"), balance.amountDueNow)
    }

    // ========================================
    // Payment Method Breakdown (v3: always empty)
    // ========================================

    @Test
    fun calculateMethodBreakdown_alwaysReturnsEmpty() {
        val payments = listOf(
            payment(1L, "100.00"),
            payment(2L, "150.00", weekAgo),
            payment(3L, "200.00", monthAgo)
        )

        val breakdown = calculator.calculateMethodBreakdown(payments)

        // v3: paymentMethod field removed — always empty
        assertTrue(breakdown.isEmpty())
    }

    @Test
    fun calculateByMethod_alwaysReturnsZero() {
        val payments = listOf(payment(1L, "100.00"))

        val total = calculator.calculateByMethod(payments, "Débito")

        assertEquals(BigDecimal.ZERO, total)
    }

    // ========================================
    // Edge Cases and Boundary Conditions
    // ========================================

    @Test
    fun calculateBalance_decimalPrecision_maintains2Places() {
        val payments = listOf(
            payment(1L, "10.01"),
            payment(2L, "20.02", weekAgo)
        )

        val balance = calculator.calculateBalance(payments)

        assertEquals(BigDecimal("30.03"), balance.amountDueNow)
    }

    @Test
    fun calculateBalance_minimumAmount_calculatesCorrectly() {
        val payments = listOf(payment(1L, "0.01"))

        val balance = calculator.calculateBalance(payments)

        assertEquals(BigDecimal("0.01"), balance.amountDueNow)
    }

    @Test
    fun calculateAveragePayment_multiplePayments_calculatesCorrectly() {
        val payments = listOf(
            payment(1L, "100.00"),
            payment(2L, "200.00", weekAgo),
            payment(3L, "300.00", monthAgo)
        )

        val average = calculator.calculateAveragePayment(payments)

        // (100 + 200 + 300) / 3 = 200
        assertEquals(BigDecimal("200.00"), average)
    }

    @Test
    fun calculateAveragePayment_emptyList_returnsZero() {
        val average = calculator.calculateAveragePayment(emptyList())

        assertEquals(BigDecimal.ZERO, average)
    }

    @Test
    fun calculateBalance_formattedDisplay_returnsCorrectFormats() {
        val payments = listOf(
            payment(1L, "1500.00"),
            payment(2L, "750.00", weekAgo)
        )

        val balance = calculator.calculateBalance(payments)

        // v3: all PAID, outstanding = 0
        assertEquals(BigDecimal("2250.00"), balance.amountDueNow)
        assertEquals(BigDecimal.ZERO, balance.totalOutstanding)
        assertTrue(balance.getFormattedAmountDue().contains("R$"))
        assertTrue(balance.getFormattedOutstanding().contains("R$"))
        assertTrue(balance.getFormattedTotal().contains("R$"))
    }

    @Test
    fun calculateTotalOutstanding_alwaysReturnsZero() {
        val payments = listOf(
            payment(1L, "300.00"),
            payment(2L, "100.00", weekAgo)
        )

        val outstanding = calculator.calculateTotalOutstanding(payments)

        assertEquals(BigDecimal.ZERO, outstanding)
    }

    @Test
    fun calculateCollectionRate_nonEmptyList_returns100() {
        val payments = listOf(payment(1L, "150.00"))

        val rate = calculator.calculateCollectionRate(payments)

        assertEquals(100, rate)
    }

    @Test
    fun calculateCollectionRate_emptyList_returns0() {
        val rate = calculator.calculateCollectionRate(emptyList())

        assertEquals(0, rate)
    }

    @Test
    fun calculateMonthlyBreakdown_groupsByMonth() {
        val thisMonth = YearMonth.now()
        val lastMonth = thisMonth.minusMonths(1)

        val payments = listOf(
            payment(1L, "100.00", today),
            payment(2L, "200.00", monthAgo)
        )

        val breakdown = calculator.calculateMonthlyBreakdown(payments)

        assertTrue(breakdown.containsKey(thisMonth))
        assertTrue(breakdown.containsKey(lastMonth))
        assertEquals(BigDecimal("100.00"), breakdown[thisMonth]?.amountDueNow)
        assertEquals(BigDecimal("200.00"), breakdown[lastMonth]?.amountDueNow)
    }
}
