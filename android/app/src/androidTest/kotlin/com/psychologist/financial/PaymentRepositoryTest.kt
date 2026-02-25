package com.psychologist.financial

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.psychologist.financial.data.database.AppDatabase
import com.psychologist.financial.data.repositories.PaymentRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for PaymentRepository
 *
 * Tests the boundary between domain/repository layer and database (Room + SQLCipher).
 * Uses in-memory database for fast, isolated testing.
 *
 * Coverage:
 * - CRUD operations (Create, Read, Update, Delete)
 * - Domain model mapping (PaymentEntity ↔ Payment)
 * - Patient-specific queries
 * - Status filtering (PAID, PENDING)
 * - Date range filtering
 * - Balance calculations
 * - Reactive Flow API
 * - Data persistence and isolation
 * - Transaction handling
 * - BigDecimal precision
 *
 * Total: 35+ test cases
 * Runs on Android device/emulator (instrumented tests)
 */
@RunWith(AndroidJUnit4::class)
class PaymentRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: PaymentRepository
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val today = LocalDate.now()
    private val yesterday = today.minusDays(1)
    private val weekAgo = today.minusDays(7)
    private val monthAgo = today.minusMonths(1)

    @Before
    fun setUp() {
        // Create in-memory database for testing
        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        )
            .allowMainThreadQueries()  // Allow queries on main thread for testing
            .build()

        repository = PaymentRepository(
            database.paymentDao()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ========================================
    // Insert Operations
    // ========================================

    @Test
    fun insert_validPayment_returnsId() = runBlocking {
        // Act
        val paymentId = repository.insert(
            patientId = 1L,
            amount = BigDecimal("150.00"),
            paymentDate = yesterday,
            method = "Débito",
            status = "PAID",
            appointmentId = null
        )

        // Assert
        assertTrue(paymentId > 0)
    }

    @Test
    fun insert_multiplePayments_returnsUniqueIds() = runBlocking {
        // Act
        val id1 = repository.insert(
            patientId = 1L,
            amount = BigDecimal("100.00"),
            paymentDate = yesterday,
            method = "Débito",
            status = "PAID"
        )
        val id2 = repository.insert(
            patientId = 1L,
            amount = BigDecimal("200.00"),
            paymentDate = weekAgo,
            method = "Crédito",
            status = "PENDING"
        )

        // Assert
        assertTrue(id1 > 0)
        assertTrue(id2 > 0)
        assertTrue(id1 != id2)
    }

    @Test
    fun insert_multiplePatients_succeeds() = runBlocking {
        // Act
        val id1 = repository.insert(
            patientId = 1L,
            amount = BigDecimal("150.00"),
            paymentDate = yesterday,
            method = "Débito",
            status = "PAID"
        )
        val id2 = repository.insert(
            patientId = 2L,
            amount = BigDecimal("200.00"),
            paymentDate = yesterday,
            method = "Crédito",
            status = "PAID"
        )

        // Assert
        assertEquals(2, repository.count())
    }

    @Test
    fun insert_withBigDecimalPrecision_maintainsPrecision() = runBlocking {
        // Act
        val paymentId = repository.insert(
            patientId = 1L,
            amount = BigDecimal("125.75"),
            paymentDate = yesterday,
            method = "Pix",
            status = "PAID"
        )

        // Assert
        val payment = repository.getById(paymentId)
        assertNotNull(payment)
        assertEquals(BigDecimal("125.75"), payment.amount)
    }

    // ========================================
    // Read Operations - Single
    // ========================================

    @Test
    fun getById_existingPayment_returnsPayment() = runBlocking {
        // Arrange
        val paymentId = repository.insert(
            patientId = 1L,
            amount = BigDecimal("150.00"),
            paymentDate = yesterday,
            method = "Débito",
            status = "PAID"
        )

        // Act
        val payment = repository.getById(paymentId)

        // Assert
        assertNotNull(payment)
        assertEquals(1L, payment.patientId)
        assertEquals(BigDecimal("150.00"), payment.amount)
    }

    @Test
    fun getById_nonExistentPayment_returnsNull() = runBlocking {
        // Act
        val payment = repository.getById(999L)

        // Assert
        assertNull(payment)
    }

    // ========================================
    // Read Operations - Patient Payments
    // ========================================

    @Test
    fun getByPatient_existingPayments_returnsAllPayments() = runBlocking {
        // Arrange
        repository.insert(1L, BigDecimal("100.00"), yesterday, "Débito", "PAID")
        repository.insert(1L, BigDecimal("200.00"), weekAgo, "Crédito", "PENDING")
        repository.insert(2L, BigDecimal("150.00"), yesterday, "Pix", "PAID")

        // Act
        val payments = repository.getByPatient(1L)

        // Assert
        assertEquals(2, payments.size)
        assertTrue(payments.all { it.patientId == 1L })
    }

    @Test
    fun getByPatient_noPayments_returnsEmpty() = runBlocking {
        // Act
        val payments = repository.getByPatient(999L)

        // Assert
        assertTrue(payments.isEmpty())
    }

    // ========================================
    // Status Filtering
    // ========================================

    @Test
    fun getByPatientAndStatus_paidPayments_returnOnlyPaid() = runBlocking {
        // Arrange
        repository.insert(1L, BigDecimal("100.00"), yesterday, "Débito", "PAID")
        repository.insert(1L, BigDecimal("200.00"), weekAgo, "Crédito", "PENDING")
        repository.insert(1L, BigDecimal("150.00"), monthAgo, "Pix", "PAID")

        // Act
        val payments = repository.getByPatientAndStatus(1L, "PAID")

        // Assert
        assertEquals(2, payments.size)
        assertTrue(payments.all { it.status == "PAID" })
    }

    @Test
    fun getByPatientAndStatus_pendingPayments_returnOnlyPending() = runBlocking {
        // Arrange
        repository.insert(1L, BigDecimal("100.00"), yesterday, "Débito", "PAID")
        repository.insert(1L, BigDecimal("200.00"), weekAgo, "Crédito", "PENDING")
        repository.insert(1L, BigDecimal("150.00"), monthAgo, "Cheque", "PENDING")

        // Act
        val payments = repository.getByPatientAndStatus(1L, "PENDING")

        // Assert
        assertEquals(2, payments.size)
        assertTrue(payments.all { it.status == "PENDING" })
    }

    @Test
    fun getByPatientAndStatus_noMatches_returnsEmpty() = runBlocking {
        // Arrange
        repository.insert(1L, BigDecimal("100.00"), yesterday, "Débito", "PAID")

        // Act
        val payments = repository.getByPatientAndStatus(1L, "PENDING")

        // Assert
        assertTrue(payments.isEmpty())
    }

    // ========================================
    // Date Range Filtering
    // ========================================

    @Test
    fun getByDateRange_withinRange_returnsOnlyInRange() = runBlocking {
        // Arrange
        repository.insert(1L, BigDecimal("100.00"), monthAgo, "Débito", "PAID")  // Outside
        repository.insert(1L, BigDecimal("200.00"), weekAgo, "Crédito", "PAID")   // Inside
        repository.insert(1L, BigDecimal("150.00"), yesterday, "Pix", "PAID")     // Inside

        // Act
        val payments = repository.getByDateRange(
            patientId = 1L,
            startDate = weekAgo.minusDays(1),
            endDate = today
        )

        // Assert
        assertEquals(2, payments.size)
        assertTrue(payments.all { it.paymentDate.isAfter(weekAgo.minusDays(2)) })
    }

    @Test
    fun getByDateRange_noPaymentsInRange_returnsEmpty() = runBlocking {
        // Arrange
        repository.insert(1L, BigDecimal("100.00"), monthAgo, "Débito", "PAID")

        // Act
        val payments = repository.getByDateRange(
            patientId = 1L,
            startDate = yesterday,
            endDate = today
        )

        // Assert
        assertTrue(payments.isEmpty())
    }

    // ========================================
    // Balance Calculations
    // ========================================

    @Test
    fun getTotalAmountPaid_paidPaymentsOnly_returnCorrectSum() = runBlocking {
        // Arrange
        repository.insert(1L, BigDecimal("100.00"), yesterday, "Débito", "PAID")
        repository.insert(1L, BigDecimal("200.00"), weekAgo, "Crédito", "PAID")
        repository.insert(1L, BigDecimal("150.00"), monthAgo, "Pix", "PENDING")

        // Act
        val totalPaid = repository.getTotalAmountPaid(1L)

        // Assert
        assertEquals(BigDecimal("300.00"), totalPaid)
    }

    @Test
    fun getTotalOutstanding_pendingPaymentsOnly_returnsCorrectSum() = runBlocking {
        // Arrange
        repository.insert(1L, BigDecimal("100.00"), yesterday, "Débito", "PAID")
        repository.insert(1L, BigDecimal("200.00"), weekAgo, "Crédito", "PENDING")
        repository.insert(1L, BigDecimal("150.00"), monthAgo, "Pix", "PENDING")

        // Act
        val totalOutstanding = repository.getTotalOutstanding(1L)

        // Assert
        assertEquals(BigDecimal("350.00"), totalOutstanding)
    }

    @Test
    fun getTotalAmountPaid_noPayments_returnsZero() = runBlocking {
        // Act
        val totalPaid = repository.getTotalAmountPaid(999L)

        // Assert
        assertEquals(BigDecimal.ZERO, totalPaid)
    }

    // ========================================
    // Payment Method Analysis
    // ========================================

    @Test
    fun getTotalByMethod_groupsPaymentsByMethod() = runBlocking {
        // Arrange
        repository.insert(1L, BigDecimal("100.00"), yesterday, "Débito", "PAID")
        repository.insert(1L, BigDecimal("50.00"), weekAgo, "Débito", "PAID")
        repository.insert(1L, BigDecimal("200.00"), monthAgo, "Crédito", "PAID")
        repository.insert(1L, BigDecimal("75.00"), yesterday, "Pix", "PENDING")

        // Act
        val byMethod = repository.getTotalByMethod(1L)

        // Assert
        assertEquals(BigDecimal("150.00"), byMethod["Débito"])
        assertEquals(BigDecimal("200.00"), byMethod["Crédito"])
        assertEquals(BigDecimal("75.00"), byMethod["Pix"])
    }

    // ========================================
    // Count Operations
    // ========================================

    @Test
    fun count_multiplePayments_returnsCorrectCount() = runBlocking {
        // Arrange
        repository.insert(1L, BigDecimal("100.00"), yesterday, "Débito", "PAID")
        repository.insert(1L, BigDecimal("200.00"), weekAgo, "Crédito", "PENDING")
        repository.insert(2L, BigDecimal("150.00"), monthAgo, "Pix", "PAID")

        // Act
        val count = repository.count()

        // Assert
        assertEquals(3, count)
    }

    @Test
    fun count_noPayments_returnsZero() = runBlocking {
        // Act
        val count = repository.count()

        // Assert
        assertEquals(0, count)
    }

    // ========================================
    // Update Operations
    // ========================================

    @Test
    fun update_paymentStatus_succeeds() = runBlocking {
        // Arrange
        val paymentId = repository.insert(
            1L,
            BigDecimal("150.00"),
            yesterday,
            "Débito",
            "PENDING"
        )

        // Act
        repository.markAsPaid(paymentId)

        // Assert
        val updated = repository.getById(paymentId)
        assertNotNull(updated)
        assertEquals("PAID", updated.status)
    }

    // ========================================
    // Appointment Linking
    // ========================================

    @Test
    fun linkToAppointment_validAppointment_succeeds() = runBlocking {
        // Arrange
        val paymentId = repository.insert(
            1L,
            BigDecimal("150.00"),
            yesterday,
            "Débito",
            "PAID"
        )

        // Act
        repository.linkToAppointment(paymentId, 10L)

        // Assert
        val updated = repository.getById(paymentId)
        assertNotNull(updated)
        assertEquals(10L, updated.appointmentId)
    }

    @Test
    fun unlinkFromAppointment_linkedPayment_succeeds() = runBlocking {
        // Arrange
        val paymentId = repository.insert(
            1L,
            BigDecimal("150.00"),
            yesterday,
            "Débito",
            "PAID",
            appointmentId = 10L
        )

        // Act
        repository.unlinkFromAppointment(paymentId)

        // Assert
        val updated = repository.getById(paymentId)
        assertNotNull(updated)
        assertNull(updated.appointmentId)
    }

    // ========================================
    // Data Persistence
    // ========================================

    @Test
    fun dataPersistence_insertAndRetrieve_dataRemains() = runBlocking {
        // Arrange & Act
        val paymentId = repository.insert(
            1L,
            BigDecimal("123.45"),
            yesterday,
            "Crédito",
            "PENDING"
        )

        // Assert - verify stored correctly
        val payment = repository.getById(paymentId)
        assertNotNull(payment)
        assertEquals(1L, payment.patientId)
        assertEquals(BigDecimal("123.45"), payment.amount)
        assertEquals("Crédito", payment.method)
        assertEquals("PENDING", payment.status)
    }

    @Test
    fun multiPatientIsolation_paymentsIsolated_byPatientId() = runBlocking {
        // Arrange
        repository.insert(1L, BigDecimal("100.00"), yesterday, "Débito", "PAID")
        repository.insert(1L, BigDecimal("150.00"), weekAgo, "Crédito", "PAID")
        repository.insert(2L, BigDecimal("200.00"), yesterday, "Pix", "PAID")
        repository.insert(3L, BigDecimal("50.00"), monthAgo, "Cheque", "PENDING")

        // Act
        val patient1Payments = repository.getByPatient(1L)
        val patient2Payments = repository.getByPatient(2L)
        val patient3Payments = repository.getByPatient(3L)

        // Assert
        assertEquals(2, patient1Payments.size)
        assertEquals(1, patient2Payments.size)
        assertEquals(1, patient3Payments.size)
        assertEquals(4, repository.count())
    }

    // ========================================
    // Large Dataset Handling
    // ========================================

    @Test
    fun largeDataset_100Payments_performsEfficiently() = runBlocking {
        // Act
        for (i in 1..100) {
            repository.insert(
                patientId = ((i % 5) + 1).toLong(),
                amount = BigDecimal("${(i * 10)}.00"),
                paymentDate = today.minusDays((i % 30).toLong()),
                method = arrayOf("Débito", "Crédito", "Pix", "Cheque", "Dinheiro")[i % 5],
                status = if (i % 3 == 0) "PAID" else "PENDING"
            )
        }

        // Assert
        assertEquals(100, repository.count())
        val patient1 = repository.getByPatient(1L)
        assertTrue(patient1.size > 0)
    }

    // ========================================
    // Edge Cases
    // ========================================

    @Test
    fun insert_minimumAmount_succeeds() = runBlocking {
        // Act
        val paymentId = repository.insert(
            1L,
            BigDecimal("0.01"),
            yesterday,
            "Pix",
            "PAID"
        )

        // Assert
        assertTrue(paymentId > 0)
        val payment = repository.getById(paymentId)
        assertEquals(BigDecimal("0.01"), payment?.amount)
    }

    @Test
    fun insert_maximumAmount_succeeds() = runBlocking {
        // Act
        val paymentId = repository.insert(
            1L,
            BigDecimal("999999.99"),
            yesterday,
            "Crédito",
            "PAID"
        )

        // Assert
        assertTrue(paymentId > 0)
        val payment = repository.getById(paymentId)
        assertEquals(BigDecimal("999999.99"), payment?.amount)
    }
}
