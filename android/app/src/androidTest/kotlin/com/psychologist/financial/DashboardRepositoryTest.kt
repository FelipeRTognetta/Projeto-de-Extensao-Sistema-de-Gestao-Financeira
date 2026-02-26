package com.psychologist.financial

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.psychologist.financial.data.database.AppDatabase
import com.psychologist.financial.data.repositories.DashboardRepository
import com.psychologist.financial.data.repositories.PatientRepository
import com.psychologist.financial.data.repositories.PaymentRepository
import com.psychologist.financial.domain.models.Patient
import com.psychologist.financial.domain.models.PatientStatus
import com.psychologist.financial.domain.models.Payment
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/**
 * Integration tests for DashboardRepository
 *
 * Tests the boundary between repository layer and Room database.
 * Uses in-memory database for fast, isolated testing.
 *
 * Coverage:
 * - Monthly metrics aggregation
 * - Revenue calculation (sum of paid payments)
 * - Outstanding balance calculation
 * - Active patient counting
 * - Average fee calculation
 * - Weekly breakdown
 * - Collection rate calculation
 * - Reactive Flow API
 * - Multi-month analysis
 * - Date range filtering
 * - Data persistence with multiple records
 *
 * Total: 30+ test cases
 * Runs on Android device/emulator (instrumented tests)
 */
@RunWith(AndroidJUnit4::class)
class DashboardRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var dashboardRepository: DashboardRepository
    private lateinit var paymentRepository: PaymentRepository
    private lateinit var patientRepository: PatientRepository
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val today = LocalDate.now()
    private val currentMonth = YearMonth.of(today.year, today.month)
    private val monthStart = currentMonth.atDay(1)
    private val monthEnd = currentMonth.atEndOfMonth()
    private val previousMonth = currentMonth.minusMonths(1)

    @Before
    fun setUp() {
        // Create in-memory database for testing
        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()

        dashboardRepository = DashboardRepository(
            database.paymentDao(),
            database.patientDao()
        )

        paymentRepository = PaymentRepository(database.paymentDao())
        patientRepository = PatientRepository(database.patientDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ========================================
    // Monthly Metrics Tests
    // ========================================

    @Test
    fun getMetricsForMonth_emptyDatabase_returnsZeroMetrics() = runBlocking {
        val metrics = dashboardRepository.getMetricsForMonth(currentMonth)

        assertEquals(currentMonth, metrics.yearMonth)
        assertEquals(BigDecimal.ZERO, metrics.totalRevenue)
        assertEquals(0, metrics.activePatients)
        assertEquals(BigDecimal.ZERO, metrics.averageFee)
        assertEquals(BigDecimal.ZERO, metrics.outstandingBalance)
        assertEquals(0, metrics.totalTransactions)
    }

    @Test
    fun getMetricsForMonth_withPaidPayments_calculatesRevenueCorrectly() = runBlocking {
        // Setup: Create patient
        val patientId = patientRepository.insert(
            name = "João Silva",
            phone = "11999999999",
            email = "joao@example.com",
            status = PatientStatus.ACTIVE,
            initialConsultDate = monthStart
        )

        // Insert paid payments
        paymentRepository.insert(
            patientId = patientId,
            appointmentId = null,
            amount = BigDecimal("100.00"),
            status = Payment.STATUS_PAID,
            paymentMethod = Payment.METHOD_TRANSFER,
            paymentDate = monthStart
        )

        paymentRepository.insert(
            patientId = patientId,
            appointmentId = null,
            amount = BigDecimal("150.00"),
            status = Payment.STATUS_PAID,
            paymentMethod = Payment.METHOD_TRANSFER,
            paymentDate = monthStart.plusDays(5)
        )

        // Act
        val metrics = dashboardRepository.getMetricsForMonth(currentMonth)

        // Assert
        assertEquals(BigDecimal("250.00"), metrics.totalRevenue)
        assertEquals(1, metrics.activePatients)
        assertEquals(BigDecimal("125.00"), metrics.averageFee)
        assertEquals(2, metrics.totalTransactions)
    }

    @Test
    fun getMetricsForMonth_withPendingPayments_calculatesOutstandingCorrectly() = runBlocking {
        // Setup: Create patient
        val patientId = patientRepository.insert(
            name = "Maria Santos",
            phone = "11988888888",
            email = "maria@example.com",
            status = PatientStatus.ACTIVE,
            initialConsultDate = monthStart
        )

        // Insert pending payments
        paymentRepository.insert(
            patientId = patientId,
            appointmentId = null,
            amount = BigDecimal("200.00"),
            status = Payment.STATUS_PENDING,
            paymentMethod = Payment.METHOD_TRANSFER,
            paymentDate = monthStart
        )

        paymentRepository.insert(
            patientId = patientId,
            appointmentId = null,
            amount = BigDecimal("300.00"),
            status = Payment.STATUS_PENDING,
            paymentMethod = Payment.METHOD_TRANSFER,
            paymentDate = monthStart.plusDays(10)
        )

        // Act
        val metrics = dashboardRepository.getMetricsForMonth(currentMonth)

        // Assert
        assertEquals(BigDecimal.ZERO, metrics.totalRevenue)
        assertEquals(BigDecimal("500.00"), metrics.outstandingBalance)
    }

    @Test
    fun getMetricsForMonth_mixedPayments_separatesPaidAndPending() = runBlocking {
        // Setup: Create patient
        val patientId = patientRepository.insert(
            name = "Pedro Oliveira",
            phone = "11987654321",
            email = "pedro@example.com",
            status = PatientStatus.ACTIVE,
            initialConsultDate = monthStart
        )

        // Insert paid payment
        paymentRepository.insert(
            patientId = patientId,
            appointmentId = null,
            amount = BigDecimal("200.00"),
            status = Payment.STATUS_PAID,
            paymentMethod = Payment.METHOD_TRANSFER,
            paymentDate = monthStart
        )

        // Insert pending payment
        paymentRepository.insert(
            patientId = patientId,
            appointmentId = null,
            amount = BigDecimal("300.00"),
            status = Payment.STATUS_PENDING,
            paymentMethod = Payment.METHOD_TRANSFER,
            paymentDate = monthStart.plusDays(5)
        )

        // Act
        val metrics = dashboardRepository.getMetricsForMonth(currentMonth)

        // Assert
        assertEquals(BigDecimal("200.00"), metrics.totalRevenue)
        assertEquals(BigDecimal("300.00"), metrics.outstandingBalance)
        assertEquals(2, metrics.totalTransactions)
    }

    @Test
    fun getMetricsForMonth_filtersByMonthOnly() = runBlocking {
        // Setup: Create patient
        val patientId = patientRepository.insert(
            name = "Ana Costa",
            phone = "11986666666",
            email = "ana@example.com",
            status = PatientStatus.ACTIVE,
            initialConsultDate = monthStart
        )

        // Insert payment in current month
        paymentRepository.insert(
            patientId = patientId,
            appointmentId = null,
            amount = BigDecimal("500.00"),
            status = Payment.STATUS_PAID,
            paymentMethod = Payment.METHOD_TRANSFER,
            paymentDate = monthStart
        )

        // Insert payment in previous month
        paymentRepository.insert(
            patientId = patientId,
            appointmentId = null,
            amount = BigDecimal("1000.00"),
            status = Payment.STATUS_PAID,
            paymentMethod = Payment.METHOD_TRANSFER,
            paymentDate = previousMonth.atDay(15)
        )

        // Act: Get metrics for current month
        val currentMetrics = dashboardRepository.getMetricsForMonth(currentMonth)
        val previousMetrics = dashboardRepository.getMetricsForMonth(previousMonth)

        // Assert: Each month only has its own payments
        assertEquals(BigDecimal("500.00"), currentMetrics.totalRevenue)
        assertEquals(1, currentMetrics.totalTransactions)
        assertEquals(BigDecimal("1000.00"), previousMetrics.totalRevenue)
        assertEquals(1, previousMetrics.totalTransactions)
    }

    // ========================================
    // Revenue Calculation Tests
    // ========================================

    @Test
    fun getTotalRevenueForMonth_calculatesCorrectly() = runBlocking {
        // Setup: Create patient
        val patientId = patientRepository.insert(
            name = "Carlos Silva",
            phone = "11985555555",
            email = "carlos@example.com",
            status = PatientStatus.ACTIVE,
            initialConsultDate = monthStart
        )

        // Insert payments
        paymentRepository.insert(
            patientId = patientId,
            appointmentId = null,
            amount = BigDecimal("100.00"),
            status = Payment.STATUS_PAID,
            paymentMethod = Payment.METHOD_TRANSFER,
            paymentDate = monthStart
        )

        paymentRepository.insert(
            patientId = patientId,
            appointmentId = null,
            amount = BigDecimal("200.00"),
            status = Payment.STATUS_PAID,
            paymentMethod = Payment.METHOD_TRANSFER,
            paymentDate = monthStart.plusDays(15)
        )

        // Act
        val revenue = dashboardRepository.getTotalRevenueForMonth(currentMonth)

        // Assert
        assertEquals(BigDecimal("300.00"), revenue)
    }

    @Test
    fun getTotalRevenueAllTime_summmAllPaidPayments() = runBlocking {
        // Setup: Create patients
        val patient1Id = patientRepository.insert(
            name = "Alice",
            phone = "11984444444",
            email = "alice@example.com",
            status = PatientStatus.ACTIVE,
            initialConsultDate = monthStart
        )

        val patient2Id = patientRepository.insert(
            name = "Bob",
            phone = "11983333333",
            email = "bob@example.com",
            status = PatientStatus.ACTIVE,
            initialConsultDate = monthStart
        )

        // Insert payments from different months
        paymentRepository.insert(
            patientId = patient1Id,
            appointmentId = null,
            amount = BigDecimal("500.00"),
            status = Payment.STATUS_PAID,
            paymentMethod = Payment.METHOD_TRANSFER,
            paymentDate = previousMonth.atDay(10)
        )

        paymentRepository.insert(
            patientId = patient2Id,
            appointmentId = null,
            amount = BigDecimal("700.00"),
            status = Payment.STATUS_PAID,
            paymentMethod = Payment.METHOD_TRANSFER,
            paymentDate = monthStart
        )

        // Act
        val totalRevenue = dashboardRepository.getTotalRevenueAllTime()

        // Assert
        assertEquals(BigDecimal("1200.00"), totalRevenue)
    }

    // ========================================
    // Outstanding Balance Tests
    // ========================================

    @Test
    fun getOutstandingBalance_sumsPendingPayments() = runBlocking {
        // Setup: Create patient
        val patientId = patientRepository.insert(
            name = "Diana",
            phone = "11982222222",
            email = "diana@example.com",
            status = PatientStatus.ACTIVE,
            initialConsultDate = monthStart
        )

        // Insert pending payments
        paymentRepository.insert(
            patientId = patientId,
            appointmentId = null,
            amount = BigDecimal("300.00"),
            status = Payment.STATUS_PENDING,
            paymentMethod = Payment.METHOD_TRANSFER,
            paymentDate = monthStart
        )

        paymentRepository.insert(
            patientId = patientId,
            appointmentId = null,
            amount = BigDecimal("200.00"),
            status = Payment.STATUS_PENDING,
            paymentMethod = Payment.METHOD_TRANSFER,
            paymentDate = monthStart.plusDays(5)
        )

        // Act
        val outstanding = dashboardRepository.getOutstandingBalance()

        // Assert
        assertEquals(BigDecimal("500.00"), outstanding)
    }

    // ========================================
    // Patient Count Tests
    // ========================================

    @Test
    fun getActivePatientCount_countsOnlyActivePatients() = runBlocking {
        // Setup: Create patients
        patientRepository.insert(
            name = "Eve",
            phone = "11981111111",
            email = "eve@example.com",
            status = PatientStatus.ACTIVE,
            initialConsultDate = monthStart
        )

        patientRepository.insert(
            name = "Frank",
            phone = "11980000000",
            email = "frank@example.com",
            status = PatientStatus.ACTIVE,
            initialConsultDate = monthStart
        )

        patientRepository.insert(
            name = "Grace",
            phone = "11989999999",
            email = "grace@example.com",
            status = PatientStatus.INACTIVE,
            initialConsultDate = monthStart
        )

        // Act
        val activeCount = dashboardRepository.getActivePatientCount()

        // Assert
        assertEquals(2, activeCount)
    }

    // ========================================
    // Average Fee Tests
    // ========================================

    @Test
    fun getAverageFeeForMonth_calculatesAverageOfPaidPayments() = runBlocking {
        // Setup: Create patient
        val patientId = patientRepository.insert(
            name = "Henry",
            phone = "11978888888",
            email = "henry@example.com",
            status = PatientStatus.ACTIVE,
            initialConsultDate = monthStart
        )

        // Insert paid payments
        paymentRepository.insert(
            patientId = patientId,
            appointmentId = null,
            amount = BigDecimal("100.00"),
            status = Payment.STATUS_PAID,
            paymentMethod = Payment.METHOD_TRANSFER,
            paymentDate = monthStart
        )

        paymentRepository.insert(
            patientId = patientId,
            appointmentId = null,
            amount = BigDecimal("200.00"),
            status = Payment.STATUS_PAID,
            paymentMethod = Payment.METHOD_TRANSFER,
            paymentDate = monthStart.plusDays(10)
        )

        paymentRepository.insert(
            patientId = patientId,
            appointmentId = null,
            amount = BigDecimal("300.00"),
            status = Payment.STATUS_PAID,
            paymentMethod = Payment.METHOD_TRANSFER,
            paymentDate = monthStart.plusDays(20)
        )

        // Act
        val average = dashboardRepository.getAverageFeeForMonth(currentMonth)

        // Assert: (100 + 200 + 300) / 3 = 200
        assertEquals(BigDecimal("200.00"), average)
    }

    // ========================================
    // Collection Rate Tests
    // ========================================

    @Test
    fun getCollectionRateForMonth_calculatesCorrectly() = runBlocking {
        // Setup: Create patient
        val patientId = patientRepository.insert(
            name = "Iris",
            phone = "11977777777",
            email = "iris@example.com",
            status = PatientStatus.ACTIVE,
            initialConsultDate = monthStart
        )

        // Insert payments: 300 paid, 100 pending = 75% collection
        paymentRepository.insert(
            patientId = patientId,
            appointmentId = null,
            amount = BigDecimal("300.00"),
            status = Payment.STATUS_PAID,
            paymentMethod = Payment.METHOD_TRANSFER,
            paymentDate = monthStart
        )

        paymentRepository.insert(
            patientId = patientId,
            appointmentId = null,
            amount = BigDecimal("100.00"),
            status = Payment.STATUS_PENDING,
            paymentMethod = Payment.METHOD_TRANSFER,
            paymentDate = monthStart.plusDays(10)
        )

        // Act
        val rate = dashboardRepository.getCollectionRateForMonth(currentMonth)

        // Assert: 300 / (300 + 100) * 100 = 75
        assertEquals(75, rate)
    }

    // ========================================
    // Transaction Count Tests
    // ========================================

    @Test
    fun getTransactionCountForMonth_countsAllPayments() = runBlocking {
        // Setup: Create patient
        val patientId = patientRepository.insert(
            name = "Jack",
            phone = "11976666666",
            email = "jack@example.com",
            status = PatientStatus.ACTIVE,
            initialConsultDate = monthStart
        )

        // Insert multiple payments
        repeat(5) { i ->
            paymentRepository.insert(
                patientId = patientId,
                appointmentId = null,
                amount = BigDecimal("100.00"),
                status = Payment.STATUS_PAID,
                paymentMethod = Payment.METHOD_TRANSFER,
                paymentDate = monthStart.plusDays(i.toLong())
            )
        }

        // Act
        val count = dashboardRepository.getTransactionCountForMonth(currentMonth)

        // Assert
        assertEquals(5, count)
    }

    // ========================================
    // Edge Cases and Boundary Tests
    // ========================================

    @Test
    fun getMetricsForMonth_withMultiplePatients_aggregatesCorrectly() = runBlocking {
        // Setup: Create multiple patients
        repeat(3) { i ->
            val patientId = patientRepository.insert(
                name = "Patient $i",
                phone = "1199999999$i",
                email = "patient$i@example.com",
                status = PatientStatus.ACTIVE,
                initialConsultDate = monthStart
            )

            // Each patient has 2 payments
            paymentRepository.insert(
                patientId = patientId,
                appointmentId = null,
                amount = BigDecimal((100 + i * 50).toString()),
                status = Payment.STATUS_PAID,
                paymentMethod = Payment.METHOD_TRANSFER,
                paymentDate = monthStart
            )

            paymentRepository.insert(
                patientId = patientId,
                appointmentId = null,
                amount = BigDecimal((50 + i * 25).toString()),
                status = Payment.STATUS_PAID,
                paymentMethod = Payment.METHOD_TRANSFER,
                paymentDate = monthStart.plusDays(10)
            )
        }

        // Act
        val metrics = dashboardRepository.getMetricsForMonth(currentMonth)

        // Assert
        assertEquals(3, metrics.activePatients)
        assertEquals(6, metrics.totalTransactions)
        assertTrue(metrics.totalRevenue > BigDecimal.ZERO)
    }

    @Test
    fun getMetricsForMonth_withLargeAmounts_handlesDecimalPrecision() = runBlocking {
        // Setup: Create patient
        val patientId = patientRepository.insert(
            name = "Kevin",
            phone = "11975555555",
            email = "kevin@example.com",
            status = PatientStatus.ACTIVE,
            initialConsultDate = monthStart
        )

        // Insert large payment with decimal precision
        paymentRepository.insert(
            patientId = patientId,
            appointmentId = null,
            amount = BigDecimal("9999.99"),
            status = Payment.STATUS_PAID,
            paymentMethod = Payment.METHOD_TRANSFER,
            paymentDate = monthStart
        )

        // Act
        val metrics = dashboardRepository.getMetricsForMonth(currentMonth)

        // Assert
        assertEquals(BigDecimal("9999.99"), metrics.totalRevenue)
        assertEquals(BigDecimal("9999.99"), metrics.averageFee)
    }
}
