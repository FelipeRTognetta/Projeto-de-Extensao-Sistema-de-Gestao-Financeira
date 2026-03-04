package com.psychologist.financial

import com.psychologist.financial.data.database.PatientDao
import com.psychologist.financial.data.database.PaymentDao
import com.psychologist.financial.data.repositories.DashboardRepository
import com.psychologist.financial.domain.models.PatientStatus
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for DashboardRepository
 *
 * Coverage:
 * - getMetricsForMonth() aggregates from DAO calls
 * - getMetricsForCurrentMonth() uses current month
 * - getMetricsForLastMonths() returns ordered list
 * - getTotalRevenueForMonth() delegates to DAO
 * - getOutstandingBalance() delegates to DAO
 * - getActivePatientCount() delegates to DAO
 * - getAverageFeeForMonth() delegates to DAO
 * - getCollectionRateForMonth() calculates correctly
 * - getOverallCollectionRate() calculates from all payments
 *
 * Total: 12 test cases
 */
@RunWith(MockitoJUnitRunner::class)
class DashboardRepositoryUnitTest {

    @Mock
    private lateinit var mockPaymentDao: PaymentDao

    @Mock
    private lateinit var mockPatientDao: PatientDao

    private lateinit var repository: DashboardRepository

    private val march2025 = YearMonth.of(2025, 3)

    @Before
    fun setUp() {
        repository = DashboardRepository(
            database = mock(),
            paymentDao = mockPaymentDao,
            patientDao = mockPatientDao
        )
    }

    private suspend fun setupDefaultMonthMocks(
        revenue: BigDecimal = BigDecimal("3000.00"),
        patients: Int = 10,
        avgFee: BigDecimal = BigDecimal("250.00"),
        outstanding: BigDecimal = BigDecimal("500.00"),
        transactions: Int = 12
    ) {
        whenever(mockPaymentDao.getSumByStatusAndDateRange(any(), any(), any())).thenReturn(revenue)
        whenever(mockPatientDao.countByStatus(PatientStatus.ACTIVE.name)).thenReturn(patients)
        whenever(mockPaymentDao.getAverageByStatusAndDateRange(any(), any(), any())).thenReturn(avgFee)
        whenever(mockPaymentDao.getSumByStatus("PENDING")).thenReturn(outstanding)
        whenever(mockPaymentDao.countByDateRange(any(), any())).thenReturn(transactions)
    }

    // ========================================
    // getMetricsForMonth() Tests
    // ========================================

    @Test
    fun `getMetricsForMonth aggregates correctly`() = runTest {
        setupDefaultMonthMocks()

        val metrics = repository.getMetricsForMonth(march2025)

        assertEquals(march2025, metrics.yearMonth)
        assertEquals(BigDecimal("3000.00"), metrics.totalRevenue)
        assertEquals(10, metrics.activePatients)
        assertEquals(BigDecimal("250.00"), metrics.averageFee)
        assertEquals(BigDecimal("500.00"), metrics.outstandingBalance)
        assertEquals(12, metrics.totalTransactions)
    }

    @Test
    fun `getMetricsForMonth returns valid DashboardMetrics`() = runTest {
        setupDefaultMonthMocks()

        val metrics = repository.getMetricsForMonth(march2025)

        assertTrue(metrics.isValid())
    }

    @Test
    fun `getMetricsForCurrentMonth uses today month`() = runTest {
        setupDefaultMonthMocks()

        val metrics = repository.getMetricsForCurrentMonth()

        assertNotNull(metrics)
        // Current month yearMonth should match current
        assertEquals(YearMonth.now(), metrics.yearMonth)
    }

    // ========================================
    // getMetricsForLastMonths() Tests
    // ========================================

    @Test
    fun `getMetricsForLastMonths returns ordered list`() = runTest {
        setupDefaultMonthMocks()

        val list = repository.getMetricsForLastMonths(months = 3)

        assertEquals(3, list.size)
        // Oldest first: list[0] should be 2 months ago, list[2] should be current
        val today = YearMonth.now()
        assertEquals(today.minusMonths(2), list[0].yearMonth)
        assertEquals(today, list[2].yearMonth)
    }

    // ========================================
    // Individual Aggregation Tests
    // ========================================

    @Test
    fun `getTotalRevenueForMonth delegates to DAO`() = runTest {
        whenever(mockPaymentDao.getSumByStatusAndDateRange(any(), any(), any()))
            .thenReturn(BigDecimal("4500.00"))

        val revenue = repository.getTotalRevenueForMonth(march2025)

        assertEquals(BigDecimal("4500.00"), revenue)
    }

    @Test
    fun `getOutstandingBalance delegates to DAO`() = runTest {
        whenever(mockPaymentDao.getSumByStatus("PENDING")).thenReturn(BigDecimal("750.00"))

        val balance = repository.getOutstandingBalance()

        assertEquals(BigDecimal("750.00"), balance)
    }

    @Test
    fun `getActivePatientCount delegates to DAO`() = runTest {
        whenever(mockPatientDao.countByStatus(PatientStatus.ACTIVE.name)).thenReturn(18)

        val count = repository.getActivePatientCount()

        assertEquals(18, count)
    }

    @Test
    fun `getAverageFeeForMonth delegates to DAO`() = runTest {
        whenever(mockPaymentDao.getAverageByStatusAndDateRange(any(), any(), any()))
            .thenReturn(BigDecimal("300.00"))

        val avg = repository.getAverageFeeForMonth(march2025)

        assertEquals(BigDecimal("300.00"), avg)
    }

    // ========================================
    // Collection Rate Tests
    // ========================================

    @Test
    fun `getCollectionRateForMonth calculates correctly`() = runTest {
        // Revenue: 3000, Outstanding: 1000, Total: 4000, Rate = 75%
        whenever(mockPaymentDao.getSumByStatusAndDateRange(any(), any(), any()))
            .thenReturn(BigDecimal("3000.00"), BigDecimal("1000.00"))

        val rate = repository.getCollectionRateForMonth(march2025)

        assertEquals(75, rate)
    }

    @Test
    fun `getCollectionRateForMonth returns zero when no revenue`() = runTest {
        whenever(mockPaymentDao.getSumByStatusAndDateRange(any(), any(), any()))
            .thenReturn(BigDecimal.ZERO)

        val rate = repository.getCollectionRateForMonth(march2025)

        assertEquals(0, rate)
    }

    @Test
    fun `getOverallCollectionRate calculates from all time totals`() = runTest {
        // All-time revenue: 6000, outstanding: 2000, total: 8000, rate = 75%
        whenever(mockPaymentDao.getSumByStatus("PAID")).thenReturn(BigDecimal("6000.00"))
        whenever(mockPaymentDao.getSumByStatus("PENDING")).thenReturn(BigDecimal("2000.00"))

        val rate = repository.getOverallCollectionRate()

        assertEquals(75, rate)
    }
}
