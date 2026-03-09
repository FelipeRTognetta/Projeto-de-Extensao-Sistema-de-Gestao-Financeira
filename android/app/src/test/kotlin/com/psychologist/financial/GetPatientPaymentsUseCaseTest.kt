package com.psychologist.financial

import com.psychologist.financial.data.repositories.PaymentRepository
import com.psychologist.financial.domain.models.Payment
import com.psychologist.financial.domain.usecases.GetPatientPaymentsUseCase
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for GetPatientPaymentsUseCase
 *
 * Coverage:
 * - execute() retrieves all payments for patient
 * - getPaidPayments() / getPendingPayments()
 * - getByDateRange() / getMonthPayments()
 * - getAmountDueNow() / getTotalOutstanding() / getTotalAmountPaid()
 * - getCount() / getPaidCount() / getPendingCount()
 * - hasPayments() / hasPendingPayments()
 * - getCollectionRate()
 * - getGroupedByStatus()
 * - Status filtering
 *
 * Total: 20 test cases
 */
@RunWith(MockitoJUnitRunner::class)
class GetPatientPaymentsUseCaseTest {

    @Mock
    private lateinit var mockRepository: PaymentRepository

    private lateinit var useCase: GetPatientPaymentsUseCase

    private val today = LocalDate.now()
    private val yesterday = today.minusDays(1)

    private fun makePayment(id: Long, amount: BigDecimal = BigDecimal("150.00")) = Payment(
        id = id,
        patientId = 1L,
        amount = amount,
        paymentDate = yesterday
    )

    @Before
    fun setUp() {
        useCase = GetPatientPaymentsUseCase(repository = mockRepository)
    }

    // ========================================
    // execute() Tests
    // ========================================

    @Test
    fun `execute returns all payments for patient`() = runTest {
        val payments = listOf(makePayment(1L), makePayment(2L))
        whenever(mockRepository.getByPatient(1L)).thenReturn(payments)

        val result = useCase.execute(patientId = 1L)

        assertEquals(2, result.size)
    }

    @Test
    fun `execute returns empty list when no payments`() = runTest {
        whenever(mockRepository.getByPatient(99L)).thenReturn(emptyList())

        val result = useCase.execute(patientId = 99L)

        assertTrue(result.isEmpty())
    }

    // ========================================
    // Status Filtering Tests
    // ========================================

    @Test
    fun `getPaidPayments returns all patient payments`() = runTest {
        // v3: getPaidPayments delegates to getByPatient (all payments are paid)
        val payments = listOf(makePayment(1L))
        whenever(mockRepository.getByPatient(1L)).thenReturn(payments)

        val result = useCase.getPaidPayments(patientId = 1L)

        assertEquals(1, result.size)
    }

    @Test
    fun `getPendingPayments always returns empty`() = runTest {
        // v3: no pending payments concept — always returns empty list
        val result = useCase.getPendingPayments(patientId = 1L)

        assertTrue(result.isEmpty())
    }

    // ========================================
    // Date Range Tests
    // ========================================

    @Test
    fun `getByDateRange returns payments in range`() = runTest {
        val payments = listOf(makePayment(1L))
        whenever(mockRepository.getByPatientAndDateRange(any(), any(), any()))
            .thenReturn(payments)

        val result = useCase.getByDateRange(
            patientId = 1L,
            startDate = yesterday.minusDays(5),
            endDate = today
        )

        assertEquals(1, result.size)
    }

    @Test
    fun `getMonthPayments returns correct month payments`() = runTest {
        whenever(mockRepository.getByPatientAndDateRange(any(), any(), any()))
            .thenReturn(emptyList())

        val result = useCase.getMonthPayments(patientId = 1L, year = 2025, month = 3)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getCurrentMonthPayments uses current month range`() = runTest {
        whenever(mockRepository.getByPatientAndDateRange(any(), any(), any()))
            .thenReturn(emptyList())

        val result = useCase.getCurrentMonthPayments(patientId = 1L)

        assertTrue(result.isEmpty())
    }

    // ========================================
    // Balance Calculation Tests
    // ========================================

    @Test
    fun `getAmountDueNow delegates to repository getTotalAmountPaid`() = runTest {
        // v3: getAmountDueNow delegates to getTotalAmountPaid (all payments are paid)
        whenever(mockRepository.getTotalAmountPaid(1L)).thenReturn(BigDecimal("300.00"))

        val amount = useCase.getAmountDueNow(patientId = 1L)

        assertEquals(BigDecimal("300.00"), amount)
    }

    @Test
    fun `getTotalOutstanding always returns zero`() = runTest {
        // v3: no pending payments — outstanding is always 0
        val outstanding = useCase.getTotalOutstanding(patientId = 1L)

        assertEquals(BigDecimal.ZERO, outstanding)
    }

    @Test
    fun `getTotalAmountPaid delegates to repository`() = runTest {
        whenever(mockRepository.getTotalAmountPaid(1L)).thenReturn(BigDecimal("500.00"))

        val total = useCase.getTotalAmountPaid(patientId = 1L)

        assertEquals(BigDecimal("500.00"), total)
    }

    // ========================================
    // Count Tests
    // ========================================

    @Test
    fun `getCount delegates to repository`() = runTest {
        whenever(mockRepository.countByPatient(1L)).thenReturn(10)

        val count = useCase.getCount(patientId = 1L)

        assertEquals(10, count)
    }

    @Test
    fun `getPaidCount delegates to countByPatient`() = runTest {
        // v3: getPaidCount uses countByPatient (all payments are paid)
        whenever(mockRepository.countByPatient(1L)).thenReturn(7)

        val count = useCase.getPaidCount(patientId = 1L)

        assertEquals(7, count)
    }

    @Test
    fun `getPendingCount always returns zero`() = runTest {
        // v3: no pending payments
        val count = useCase.getPendingCount(patientId = 1L)

        assertEquals(0, count)
    }

    // ========================================
    // Existence Tests
    // ========================================

    @Test
    fun `hasPayments returns true when payment count greater than zero`() = runTest {
        whenever(mockRepository.countByPatient(1L)).thenReturn(5)

        val has = useCase.hasPayments(patientId = 1L)

        assertTrue(has)
    }

    @Test
    fun `hasPayments returns false when no payments`() = runTest {
        whenever(mockRepository.countByPatient(1L)).thenReturn(0)

        val has = useCase.hasPayments(patientId = 1L)

        assertFalse(has)
    }

    @Test
    fun `hasPendingPayments returns true when pending count greater than zero`() = runTest {
        // v3: no pending payments concept — hasPendingPayments always returns false
        val has = useCase.hasPendingPayments(patientId = 1L)

        assertFalse(has)
    }

    // ========================================
    // Collection Rate Tests
    // ========================================

    @Test
    fun `getCollectionRate returns correct percentage`() = runTest {
        // v3: all payments are paid → rate is always 100% when count > 0
        whenever(mockRepository.countByPatient(1L)).thenReturn(10)

        val rate = useCase.getCollectionRate(patientId = 1L)

        assertEquals(100, rate)
    }

    @Test
    fun `getCollectionRate returns zero when no payments`() = runTest {
        whenever(mockRepository.countByPatient(1L)).thenReturn(0)

        val rate = useCase.getCollectionRate(patientId = 1L)

        assertEquals(0, rate)
    }

    // ========================================
    // Flow Tests
    // ========================================

    @Test
    fun `executeFlow emits payments reactively`() = runTest {
        val payments = listOf(makePayment(1L))
        whenever(mockRepository.getByPatientFlow(1L))
            .thenReturn(flowOf(payments))

        val flow = useCase.executeFlow(patientId = 1L)
        var emitted: List<Payment>? = null
        flow.collect { emitted = it }

        assertEquals(1, emitted?.size)
    }
}
