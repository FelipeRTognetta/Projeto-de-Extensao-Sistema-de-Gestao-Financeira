package com.psychologist.financial

import com.psychologist.financial.data.repositories.DashboardRepository
import com.psychologist.financial.domain.models.DashboardMetrics
import com.psychologist.financial.domain.usecases.GetDashboardMetricsUseCase
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
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for GetDashboardMetricsUseCase
 *
 * Coverage:
 * - execute() with specific month
 * - getCurrentMonth() / getPreviousMonth()
 * - getLastMonthsMetrics() ordering and count
 * - getMetricsForRange() with valid range
 * - getMetricsFlow() reactive emission
 * - getTotalRevenue() / getOutstandingBalance() / getActivePatientCount()
 * - getAverageFee() / getCollectionRate()
 * - Exception handling returns empty metrics
 * - compareMonths() result
 *
 * Total: 18 test cases
 */
@RunWith(MockitoJUnitRunner::class)
class GetDashboardMetricsUseCaseTest {

    @Mock
    private lateinit var mockRepository: DashboardRepository

    private lateinit var useCase: GetDashboardMetricsUseCase

    private val march2025 = YearMonth.of(2025, 3)
    private val february2025 = YearMonth.of(2025, 2)

    private fun sampleMetrics(
        yearMonth: YearMonth,
        revenue: BigDecimal = BigDecimal("4500.00"),
        patients: Int = 12
    ) = DashboardMetrics.sample(yearMonth = yearMonth, revenue = revenue, patients = patients)

    @Before
    fun setUp() {
        useCase = GetDashboardMetricsUseCase(dashboardRepository = mockRepository)
    }

    // ========================================
    // execute() Tests
    // ========================================

    @Test
    fun `execute returns metrics for specified month`() = runTest {
        val metrics = sampleMetrics(march2025)
        whenever(mockRepository.getMetricsForMonth(march2025)).thenReturn(metrics)

        val result = useCase.execute(yearMonth = march2025)

        assertEquals(march2025, result.yearMonth)
        assertEquals(BigDecimal("4500.00"), result.totalRevenue)
    }

    @Test
    fun `execute returns empty metrics on exception`() = runTest {
        whenever(mockRepository.getMetricsForMonth(any()))
            .thenThrow(RuntimeException("Database error"))

        val result = useCase.execute(yearMonth = march2025)

        assertEquals(BigDecimal.ZERO, result.totalRevenue)
        assertEquals(0, result.activePatients)
    }

    @Test
    fun `execute returns empty metrics when metrics invalid`() = runTest {
        val invalidMetrics = DashboardMetrics(
            yearMonth = march2025,
            totalRevenue = BigDecimal("-1.00"),
            activePatients = -1,
            averageFee = BigDecimal.ZERO,
            outstandingBalance = BigDecimal.ZERO
        )
        whenever(mockRepository.getMetricsForMonth(march2025)).thenReturn(invalidMetrics)

        val result = useCase.execute(yearMonth = march2025)

        // Returns empty metrics when invalid
        assertEquals(BigDecimal.ZERO, result.totalRevenue)
    }

    // ========================================
    // Convenience Methods Tests
    // ========================================

    @Test
    fun `getCurrentMonth delegates to execute with current month`() = runTest {
        val metrics = sampleMetrics(YearMonth.now())
        whenever(mockRepository.getMetricsForMonth(any())).thenReturn(metrics)

        val result = useCase.getCurrentMonth()

        assertNotNull(result)
        assertEquals(metrics.activePatients, result.activePatients)
    }

    @Test
    fun `getPreviousMonth delegates to execute with previous month`() = runTest {
        val prevMonth = YearMonth.now().minusMonths(1)
        val metrics = sampleMetrics(prevMonth)
        whenever(mockRepository.getMetricsForMonth(any())).thenReturn(metrics)

        val result = useCase.getPreviousMonth()

        assertNotNull(result)
    }

    // ========================================
    // Last Months Tests
    // ========================================

    @Test
    fun `getLastMonthsMetrics returns correct number of months`() = runTest {
        val metricsList = listOf(
            sampleMetrics(YearMonth.now().minusMonths(2)),
            sampleMetrics(YearMonth.now().minusMonths(1)),
            sampleMetrics(YearMonth.now())
        )
        whenever(mockRepository.getMetricsForLastMonths(3)).thenReturn(metricsList)

        val result = useCase.getLastMonthsMetrics(months = 3)

        assertEquals(3, result.size)
    }

    @Test
    fun `getLastMonthsMetrics returns empty list on exception`() = runTest {
        whenever(mockRepository.getMetricsForLastMonths(any()))
            .thenThrow(RuntimeException("DB error"))

        val result = useCase.getLastMonthsMetrics(months = 3)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getLastMonthsMetrics with single month returns list of one`() = runTest {
        val metrics = sampleMetrics(YearMonth.now())
        whenever(mockRepository.getMetricsForLastMonths(1)).thenReturn(listOf(metrics))

        val result = useCase.getLastMonthsMetrics(months = 1)

        assertEquals(1, result.size)
    }

    // ========================================
    // Range Tests
    // ========================================

    @Test
    fun `getMetricsForRange returns metrics for range`() = runTest {
        val metricsList = listOf(
            sampleMetrics(february2025),
            sampleMetrics(march2025)
        )
        whenever(mockRepository.getMetricsForDateRange(february2025, march2025))
            .thenReturn(metricsList)

        val result = useCase.getMetricsForRange(
            startMonth = february2025,
            endMonth = march2025
        )

        assertEquals(2, result.size)
    }

    @Test
    fun `getMetricsForRange returns empty list on exception`() = runTest {
        whenever(mockRepository.getMetricsForDateRange(any(), any()))
            .thenThrow(RuntimeException("Error"))

        val result = useCase.getMetricsForRange(
            startMonth = february2025,
            endMonth = march2025
        )

        assertTrue(result.isEmpty())
    }

    // ========================================
    // Flow Tests
    // ========================================

    @Test
    fun `getMetricsFlow emits metrics reactively`() = runTest {
        val metrics = sampleMetrics(march2025)
        whenever(mockRepository.getMetricsForMonthFlow(march2025))
            .thenReturn(flowOf(metrics))

        val flow = useCase.getMetricsFlow(yearMonth = march2025)
        var emitted: DashboardMetrics? = null
        flow.collect { emitted = it }

        assertNotNull(emitted)
        assertEquals(march2025, emitted?.yearMonth)
    }

    // ========================================
    // Individual Metrics Tests
    // ========================================

    @Test
    fun `getTotalRevenue delegates to repository`() = runTest {
        whenever(mockRepository.getTotalRevenueForMonth(march2025))
            .thenReturn(BigDecimal("3500.00"))

        val revenue = useCase.getTotalRevenue(yearMonth = march2025)

        assertEquals(BigDecimal("3500.00"), revenue)
    }

    @Test
    fun `getTotalRevenue returns zero on exception`() = runTest {
        whenever(mockRepository.getTotalRevenueForMonth(any()))
            .thenThrow(RuntimeException("Error"))

        val revenue = useCase.getTotalRevenue(yearMonth = march2025)

        assertEquals(BigDecimal.ZERO, revenue)
    }

    @Test
    fun `getOutstandingBalance delegates to repository`() = runTest {
        whenever(mockRepository.getOutstandingBalance())
            .thenReturn(BigDecimal("800.00"))

        val balance = useCase.getOutstandingBalance()

        assertEquals(BigDecimal("800.00"), balance)
    }

    @Test
    fun `getActivePatientCount delegates to repository`() = runTest {
        whenever(mockRepository.getActivePatientCount()).thenReturn(15)

        val count = useCase.getActivePatientCount()

        assertEquals(15, count)
    }

    @Test
    fun `getAverageFee delegates to repository`() = runTest {
        whenever(mockRepository.getAverageFeeForMonth(march2025))
            .thenReturn(BigDecimal("275.00"))

        val avg = useCase.getAverageFee(yearMonth = march2025)

        assertEquals(BigDecimal("275.00"), avg)
    }

    @Test
    fun `getCollectionRate delegates to repository`() = runTest {
        whenever(mockRepository.getCollectionRateForMonth(march2025)).thenReturn(85)

        val rate = useCase.getCollectionRate(yearMonth = march2025)

        assertEquals(85, rate)
    }

    // ========================================
    // compareMonths() Tests
    // ========================================

    @Test
    fun `compareMonths returns correct trend direction`() = runTest {
        val currentMetrics = sampleMetrics(march2025, revenue = BigDecimal("5000.00"))
        val previousMetrics = sampleMetrics(february2025, revenue = BigDecimal("4000.00"))
        whenever(mockRepository.getMetricsForMonth(march2025)).thenReturn(currentMetrics)
        whenever(mockRepository.getMetricsForMonth(february2025)).thenReturn(previousMetrics)

        val comparison = useCase.compareMonths(
            currentMonth = march2025,
            previousMonth = february2025
        )

        assertTrue(comparison.isRevenueUp)
        assertEquals(march2025, comparison.currentMonth.yearMonth)
    }
}
